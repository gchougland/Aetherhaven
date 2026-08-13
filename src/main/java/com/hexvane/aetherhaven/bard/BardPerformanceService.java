package com.hexvane.aetherhaven.bard;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.bard.data.BardSongDefinition;
import com.hypixel.hytale.builtin.audio.components.ForcedMusicTracker;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.RoleUtils;
import com.hypixel.hytale.protocol.AnimationSlot;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

public final class BardPerformanceService {
  public static final String PARTICLE_SYSTEM_ID = "Aetherhaven_Bard_Notes";
  public static final String PERFORMANCE_ANIMATION_ID = "PlayLute";
  public static final String LUTE_ITEM_ID = "Aetherhaven_Lute";
  /** Spawn note particles at chest / lute height. */
  public static final double PARTICLE_SPAWN_Y_OFFSET = 1.15;
  /** Horizontal distance in front of the bard along facing. */
  public static final double PARTICLE_SPAWN_FORWARD_OFFSET = 0.72;

  private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

  private BardPerformanceService() {}

  public static boolean isPerforming(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef) {
    return performance(store, npcRef) != null;
  }

  public static boolean isLooping(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef) {
    BardPerformanceComponent perf = performance(store, npcRef);
    return perf != null && perf.isLooping();
  }

  public static boolean isShuffling(@Nonnull Store<EntityStore> store, @Nullable Ref<EntityStore> npcRef) {
    BardPerformanceComponent perf = performance(store, npcRef);
    return perf != null && perf.isShuffling();
  }

  @Nullable
  private static BardPerformanceComponent performance(
      @Nonnull Store<EntityStore> store,
      @Nullable Ref<EntityStore> npcRef
  ) {
    if (npcRef == null || !npcRef.isValid()) {
      return null;
    }
    return store.getComponent(npcRef, BardPerformanceComponent.getComponentType());
  }

  public static void startSong(
      @Nonnull Store<EntityStore> store,
      @Nullable CommandBuffer<EntityStore> commandBuffer,
      @Nonnull Ref<EntityStore> npcRef,
      @Nonnull AetherhavenPlugin plugin,
      @Nonnull String songId
  ) {
    startSong(store, commandBuffer, npcRef, plugin, songId, BardPlaybackMode.ONCE, new String[0]);
  }

  public static void startSong(
      @Nonnull Store<EntityStore> store,
      @Nullable CommandBuffer<EntityStore> commandBuffer,
      @Nonnull Ref<EntityStore> npcRef,
      @Nonnull AetherhavenPlugin plugin,
      @Nonnull String songId,
      @Nonnull BardPlaybackMode mode,
      @Nonnull String[] shuffleRemaining
  ) {
    beginSong(store, commandBuffer, npcRef, plugin, songId, mode, shuffleRemaining, true);
  }

  public static boolean startShuffle(
      @Nonnull Store<EntityStore> store,
      @Nullable CommandBuffer<EntityStore> commandBuffer,
      @Nonnull Ref<EntityStore> npcRef,
      @Nonnull AetherhavenPlugin plugin
  ) {
    List<String> queue =
        BardShufflePlaylist.buildQueue(plugin.getBardSongCatalog().songsOrdered(), null, ThreadLocalRandom.current());
    if (queue.isEmpty()) {
      return false;
    }
    String first = queue.remove(0);
    startSong(
        store,
        commandBuffer,
        npcRef,
        plugin,
        first,
        BardPlaybackMode.SHUFFLE,
        queue.toArray(String[]::new)
    );
    return true;
  }

  public static boolean enableLoop(
      @Nonnull Store<EntityStore> store,
      @Nullable CommandBuffer<EntityStore> commandBuffer,
      @Nonnull Ref<EntityStore> npcRef
  ) {
    BardPerformanceComponent perf = performance(store, npcRef);
    if (perf == null) {
      return false;
    }
    BardPerformanceComponent next =
        new BardPerformanceComponent(
            perf.getSongId(),
            perf.getEndAtEpochMs(),
            perf.getMusicContainerIndex(),
            BardPlaybackMode.LOOP,
            new String[0]
        );
    next.setLastParticleSpawnMs(perf.getLastParticleSpawnMs());
    putPerformanceComponent(npcRef, commandBuffer, store, next);
    return true;
  }

  /** Called from the performance tick when a song's duration elapses. */
  public static void continueOrStop(
      @Nonnull Store<EntityStore> store,
      @Nonnull CommandBuffer<EntityStore> commandBuffer,
      @Nonnull Ref<EntityStore> npcRef,
      @Nonnull AetherhavenPlugin plugin,
      @Nonnull BardPerformanceComponent perf
  ) {
    if (perf.isLooping()) {
      int containerIndex =
          beginSong(
              store,
              commandBuffer,
              npcRef,
              plugin,
              perf.getSongId(),
              BardPlaybackMode.LOOP,
              new String[0],
              false
          );
      if (containerIndex != 0) {
        BardEnvironmentMusic.resendToListeningPlayers(store, commandBuffer, containerIndex);
      } else {
        stopOnStore(store, commandBuffer, npcRef);
      }
      return;
    }
    if (perf.isShuffling()) {
      List<String> remaining = new ArrayList<>(List.of(perf.getShuffleRemaining()));
      if (remaining.isEmpty()) {
        remaining =
            BardShufflePlaylist.buildQueue(
                plugin.getBardSongCatalog().songsOrdered(),
                perf.getSongId(),
                ThreadLocalRandom.current()
            );
      }
      if (remaining.isEmpty()) {
        stopOnStore(store, commandBuffer, npcRef);
        return;
      }
      String nextId = remaining.remove(0);
      int containerIndex =
          beginSong(
              store,
              commandBuffer,
              npcRef,
              plugin,
              nextId,
              BardPlaybackMode.SHUFFLE,
              remaining.toArray(String[]::new),
              false
          );
      if (containerIndex != 0) {
        BardEnvironmentMusic.resendToListeningPlayers(store, commandBuffer, containerIndex);
      } else {
        stopOnStore(store, commandBuffer, npcRef);
      }
      return;
    }
    stopOnStore(store, commandBuffer, npcRef);
  }

  public static void applyForcedMusicForPlayer(
      @Nonnull Ref<EntityStore> playerRef,
      @Nonnull Store<EntityStore> store,
      @Nullable Ref<EntityStore> npcRef
  ) {
    BardPerformanceComponent perf = performance(store, npcRef);
    if (perf == null || perf.getMusicContainerIndex() == 0) {
      return;
    }
    ForcedMusicTracker tracker = store.getComponent(playerRef, ForcedMusicTracker.getComponentType());
    PlayerRef playerRefComponent = store.getComponent(playerRef, PlayerRef.getComponentType());
    UUIDComponent playerUuid = store.getComponent(playerRef, UUIDComponent.getComponentType());
    if (tracker == null || playerRefComponent == null || playerUuid == null) {
      return;
    }
    BardEnvironmentMusic.setForcedMusic(
        playerRef,
        null,
        store,
        playerRefComponent,
        tracker,
        perf.getMusicContainerIndex()
    );
    store.getResource(BardMusicProximityState.getResourceType())
        .setActive(playerUuid.getUuid(), perf.getMusicContainerIndex());
  }

  private static int beginSong(
      @Nonnull Store<EntityStore> store,
      @Nullable CommandBuffer<EntityStore> commandBuffer,
      @Nonnull Ref<EntityStore> npcRef,
      @Nonnull AetherhavenPlugin plugin,
      @Nonnull String songId,
      @Nonnull BardPlaybackMode mode,
      @Nonnull String[] shuffleRemaining,
      boolean stopPrevious
  ) {
    if (!npcRef.isValid()) {
      return 0;
    }
    BardSongDefinition song = plugin.getBardSongCatalog().byId(songId);
    if (song == null) {
      LOGGER.atWarning().log("Unknown bard song id %s", songId);
      return 0;
    }
    if (stopPrevious) {
      stopOnStore(store, commandBuffer, npcRef);
    }
    int musicContainerIndex = BardEnvironmentMusic.resolveMusicContainerIndex(song);
    long endAt = System.currentTimeMillis() + song.getDurationSeconds() * 1000L;
    BardPerformanceComponent perf =
        new BardPerformanceComponent(song.getId(), endAt, musicContainerIndex, mode, shuffleRemaining);
    putPerformanceComponent(npcRef, commandBuffer, store, perf);
    applyPerformanceVisuals(npcRef, store, commandBuffer);
    spawnNoteParticles(npcRef, store, commandBuffer, perf);
    return musicContainerIndex;
  }

  /** Legacy entry for callers that only have a world reference. */
  public static void startSong(
      @Nonnull World world,
      @Nonnull AetherhavenPlugin plugin,
      @Nonnull Ref<EntityStore> npcRef,
      @Nonnull String songId
  ) {
    world.execute(() -> {
      Store<EntityStore> store = world.getEntityStore().getStore();
      if (store != null) {
        startSong(store, null, npcRef, plugin, songId);
      }
    });
  }

  public static void stop(@Nonnull World world, @Nonnull Ref<EntityStore> npcRef) {
    world.execute(() -> {
      Store<EntityStore> store = world.getEntityStore().getStore();
      if (store != null) {
        stopOnStore(store, null, npcRef);
      }
    });
  }

  public static void stopOnStore(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> npcRef) {
    stopOnStore(store, null, npcRef);
  }

  public static void stopOnStore(
      @Nonnull Store<EntityStore> store,
      @Nullable CommandBuffer<EntityStore> commandBuffer,
      @Nonnull Ref<EntityStore> npcRef
  ) {
    if (!npcRef.isValid()) {
      return;
    }
    removePerformanceComponent(npcRef, commandBuffer, store);
    clearPerformanceVisuals(npcRef, store, commandBuffer);
    BardEnvironmentMusic.stopAllListeningPlayers(store, commandBuffer);
  }

  @Nonnull
  public static Vector3d particleSpawnPosition(@Nonnull TransformComponent tc) {
    Vector3d pos = tc.getPosition();
    float yaw = tc.getRotation().yaw();
    double forwardX = -Math.sin(yaw) * PARTICLE_SPAWN_FORWARD_OFFSET;
    double forwardZ = -Math.cos(yaw) * PARTICLE_SPAWN_FORWARD_OFFSET;
    return new Vector3d(pos.x + forwardX, pos.y + PARTICLE_SPAWN_Y_OFFSET, pos.z + forwardZ);
  }

  public static void spawnPerformanceNoteParticles(
      @Nonnull TransformComponent tc,
      @Nonnull Store<EntityStore> store
  ) {
    var rotation = tc.getRotation();
    ParticleUtil.spawnParticleEffect(
        PARTICLE_SYSTEM_ID,
        particleSpawnPosition(tc),
        rotation.yaw(),
        rotation.pitch(),
        rotation.roll(),
        1.0F,
        0.0F,
        store
    );
  }

  public static void maintainPerformanceVisuals(
      @Nonnull Ref<EntityStore> npcRef,
      @Nonnull Store<EntityStore> store,
      @Nullable CommandBuffer<EntityStore> commandBuffer
  ) {
    NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
    if (npc != null) {
      npc.playAnimation(npcRef, AnimationSlot.Status, PERFORMANCE_ANIMATION_ID, store);
      equipLuteInHand(npcRef, npc, store, commandBuffer);
    }
  }

  private static void spawnNoteParticles(
      @Nonnull Ref<EntityStore> npcRef,
      @Nonnull Store<EntityStore> store,
      @Nullable CommandBuffer<EntityStore> commandBuffer,
      @Nonnull BardPerformanceComponent perf
  ) {
    TransformComponent tc = store.getComponent(npcRef, TransformComponent.getComponentType());
    if (tc == null) {
      return;
    }
    spawnPerformanceNoteParticles(tc, store);
    perf.setLastParticleSpawnMs(System.currentTimeMillis());
    putPerformanceComponent(npcRef, commandBuffer, store, perf);
  }

  private static void putPerformanceComponent(
      @Nonnull Ref<EntityStore> npcRef,
      @Nullable CommandBuffer<EntityStore> commandBuffer,
      @Nonnull Store<EntityStore> store,
      @Nonnull BardPerformanceComponent perf
  ) {
    if (commandBuffer != null) {
      commandBuffer.putComponent(npcRef, BardPerformanceComponent.getComponentType(), perf);
    } else {
      store.putComponent(npcRef, BardPerformanceComponent.getComponentType(), perf);
    }
  }

  private static void removePerformanceComponent(
      @Nonnull Ref<EntityStore> npcRef,
      @Nullable CommandBuffer<EntityStore> commandBuffer,
      @Nonnull Store<EntityStore> store
  ) {
    ComponentType<EntityStore, BardPerformanceComponent> type = BardPerformanceComponent.getComponentType();
    if (commandBuffer != null) {
      commandBuffer.tryRemoveComponent(npcRef, type);
    } else {
      store.tryRemoveComponent(npcRef, type);
    }
  }

  private static void applyPerformanceVisuals(
      @Nonnull Ref<EntityStore> npcRef,
      @Nonnull Store<EntityStore> store,
      @Nullable CommandBuffer<EntityStore> commandBuffer
  ) {
    NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
    if (npc != null) {
      equipLuteInHand(npcRef, npc, store, commandBuffer);
      npc.playAnimation(npcRef, AnimationSlot.Status, PERFORMANCE_ANIMATION_ID, store);
    }
  }

  private static void equipLuteInHand(
      @Nonnull Ref<EntityStore> npcRef,
      @Nonnull NPCEntity npc,
      @Nonnull Store<EntityStore> store,
      @Nullable CommandBuffer<EntityStore> commandBuffer
  ) {
    try {
      RoleUtils.setItemInHand(npcRef, npc, LUTE_ITEM_ID, commandBuffer != null ? commandBuffer : store);
    } catch (RuntimeException ex) {
      LOGGER.atWarning().withCause(ex).log("Could not equip lute on bard");
    }
  }

  private static void clearPerformanceVisuals(
      @Nonnull Ref<EntityStore> npcRef,
      @Nonnull Store<EntityStore> store,
      @Nullable CommandBuffer<EntityStore> commandBuffer
  ) {
    NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
    if (npc != null) {
      npc.playAnimation(npcRef, AnimationSlot.Status, null, store);
      try {
        RoleUtils.setItemInHand(npcRef, npc, null, commandBuffer != null ? commandBuffer : store);
      } catch (RuntimeException ex) {
        LOGGER.atWarning().withCause(ex).log("Could not clear lute from bard");
      }
    }
  }
}
