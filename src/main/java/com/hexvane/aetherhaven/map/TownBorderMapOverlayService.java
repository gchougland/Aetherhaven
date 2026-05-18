package com.hexvane.aetherhaven.map;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.ui.PlayerTownJournalState;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.NetworkChannel;
import com.hypixel.hytale.protocol.packets.worldmap.MapChunk;
import com.hypixel.hytale.protocol.packets.worldmap.MapImage;
import com.hypixel.hytale.protocol.packets.worldmap.UpdateWorldMap;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.universe.world.worldmap.WorldMapManager;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Sends per-player painted map tiles for town territory borders without touching the shared map cache.
 * When <a href="https://github.com/ninesliced/Hytale-BetterMap">BetterMap</a> is installed, only overlays
 * chunks the player has already received and limits packet volume so exploration streaming is not starved.
 */
public final class TownBorderMapOverlayService {
  private static final boolean BETTERMAP = WorldMapTrackerCompat.isBetterMapPresent();

  private static final long TICK_MS = BETTERMAP ? 500L : 250L;
  /** Re-push painted tiles occasionally so vanilla/BetterMap refreshes do not erase borders. */
  private static final int HEARTBEAT_EVERY_N_TICKS = BETTERMAP ? 4 : 2;
  private static final int MAX_CHUNKS_PER_PLAYER_PER_TICK = BETTERMAP ? 12 : 32;

  private static final ConcurrentHashMap<String, ScheduledFuture<?>> WORLD_TASKS = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<UUID, LongSet> LAST_OVERLAY_CHUNKS = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<UUID, Long2ObjectMap<CachedTile>> PAINT_CACHE = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, WorldBorderState> WORLD_BORDER_STATE = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, AtomicInteger> WORLD_TICK_COUNTER = new ConcurrentHashMap<>();

  private TownBorderMapOverlayService() {}

  /** Painted tile kept across ticks; vanilla map updates replace the base {@link MapImage} instance. */
  private static final class CachedTile {
    final MapImage painted;
    final int baseIdentity;
    final long townsSignature;
    @Nullable
    final UUID viewerTownId;
    int lastSentBaseIdentity;

    CachedTile(MapImage painted, int baseIdentity, long townsSignature, @Nullable UUID viewerTownId) {
      this.painted = painted;
      this.baseIdentity = baseIdentity;
      this.townsSignature = townsSignature;
      this.viewerTownId = viewerTownId;
      this.lastSentBaseIdentity = Integer.MIN_VALUE;
    }

    boolean matches(int baseIdentity, long townsSignature, @Nullable UUID viewerTownId) {
      return this.baseIdentity == baseIdentity
          && this.townsSignature == townsSignature
          && Objects.equals(this.viewerTownId, viewerTownId);
    }

    boolean needsSend(int baseIdentity, boolean heartbeat) {
      return this.lastSentBaseIdentity != baseIdentity || heartbeat;
    }

    void markSent(int baseIdentity) {
      this.lastSentBaseIdentity = baseIdentity;
    }
  }

  private static final class WorldBorderState {
    long townsSignature = Long.MIN_VALUE;
    long[] perimeterChunks = new long[0];
  }

  public static void startWorld(@Nonnull World world) {
    WORLD_TICK_COUNTER.putIfAbsent(world.getName(), new AtomicInteger());
    WORLD_TASKS.computeIfAbsent(
        world.getName(),
        name ->
            HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
                () -> {
                  if (!world.isAlive()) {
                    stopWorld(world);
                    return;
                  }
                  world.execute(() -> tickWorld(world));
                },
                TICK_MS,
                TICK_MS,
                TimeUnit.MILLISECONDS));
  }

  public static void stopWorld(@Nonnull World world) {
    ScheduledFuture<?> task = WORLD_TASKS.remove(world.getName());
    if (task != null) {
      task.cancel(false);
    }
    WORLD_TICK_COUNTER.remove(world.getName());
    for (PlayerRef pref : world.getPlayerRefs()) {
      Ref<EntityStore> ref = pref.getReference();
      if (ref == null || !ref.isValid()) {
        continue;
      }
      UUIDComponent uc = ref.getStore().getComponent(ref, UUIDComponent.getComponentType());
      if (uc != null) {
        UUID uuid = uc.getUuid();
        LAST_OVERLAY_CHUNKS.remove(uuid);
        PAINT_CACHE.remove(uuid);
      }
    }
    WORLD_BORDER_STATE.remove(world.getName());
  }

  public static void refreshPlayer(@Nonnull World world, @Nonnull UUID playerUuid) {
    world.execute(
        () -> {
          Player player = findPlayer(world, playerUuid);
          if (player == null) {
            return;
          }
          Ref<EntityStore> ref = player.getReference();
          if (ref == null || !ref.isValid()) {
            return;
          }
          Store<EntityStore> store = ref.getStore();
          PlayerTownJournalState journal = store.getComponent(ref, PlayerTownJournalState.getComponentType());
          PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
          if (playerRef == null) {
            return;
          }
          if (journal == null || !journal.isShowTownBordersOnMap()) {
            clearOverlays(world, player, playerRef, playerUuid);
          } else {
            PAINT_CACHE.remove(playerUuid);
            updatePlayerOverlays(world, player, playerRef, playerUuid, true);
          }
        });
  }

  private static void tickWorld(@Nonnull World world) {
    AetherhavenPlugin plugin = AetherhavenPlugin.get();
    if (plugin == null) {
      return;
    }
    TownManager townManager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
    if (townsInWorld(townManager, world.getName()).isEmpty()) {
      return;
    }

    AtomicInteger tickCounter = WORLD_TICK_COUNTER.computeIfAbsent(world.getName(), ignored -> new AtomicInteger());
    int tick = tickCounter.incrementAndGet();
    boolean heartbeat = tick % HEARTBEAT_EVERY_N_TICKS == 0;

    for (PlayerRef pref : world.getPlayerRefs()) {
      Ref<EntityStore> ref = pref.getReference();
      if (ref == null || !ref.isValid()) {
        continue;
      }
      Store<EntityStore> store = ref.getStore();
      Player player = store.getComponent(ref, Player.getComponentType());
      PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
      UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
      if (player == null || playerRef == null || uc == null) {
        continue;
      }
      UUID playerUuid = uc.getUuid();
      PlayerTownJournalState journal = store.getComponent(ref, PlayerTownJournalState.getComponentType());
      if (journal == null || !journal.isShowTownBordersOnMap()) {
        if (LAST_OVERLAY_CHUNKS.containsKey(playerUuid)) {
          clearOverlays(world, player, playerRef, playerUuid);
        }
        continue;
      }
      updatePlayerOverlays(world, player, playerRef, playerUuid, heartbeat);
    }
  }

  @Nonnull
  private static List<TownRecord> townsInWorld(@Nonnull TownManager townManager, @Nonnull String worldName) {
    List<TownRecord> all = townManager.allTowns();
    List<TownRecord> filtered = new ArrayList<>();
    for (TownRecord town : all) {
      if (worldName.equals(town.getWorldName())) {
        filtered.add(town);
      }
    }
    return filtered;
  }

  private static void updatePlayerOverlays(
      @Nonnull World world,
      @Nonnull Player player,
      @Nonnull PlayerRef playerRef,
      @Nonnull UUID playerUuid,
      boolean heartbeat) {
    if (!playerRef.getPacketHandler().getChannel(NetworkChannel.WorldMap).isWritable()) {
      return;
    }

    AetherhavenPlugin plugin = AetherhavenPlugin.get();
    if (plugin == null) {
      return;
    }

    TownManager townManager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
    List<TownRecord> towns = townsInWorld(townManager, world.getName());
    if (towns.isEmpty()) {
      return;
    }

    TownRecord viewerTown = townManager.findTownForPlayerInWorld(playerUuid);
    UUID viewerTownId = viewerTown != null ? viewerTown.getTownId() : null;

    WorldMapManager mapManager = world.getWorldMapManager();
    long townsSignature = computeTownsSignature(towns);
    long[] perimeter = perimeterChunksForWorld(world.getName(), towns, townsSignature);
    AtomicInteger worldTick = WORLD_TICK_COUNTER.get(world.getName());
    int scanStart = 0;
    if (worldTick != null && perimeter.length > 0) {
      scanStart = Math.floorMod(worldTick.get() + playerUuid.hashCode(), perimeter.length);
    }

    LongSet previous = LAST_OVERLAY_CHUNKS.get(playerUuid);
    LongSet current = new LongOpenHashSet();
    List<MapChunk> toSend = new ArrayList<>();
    Long2ObjectMap<CachedTile> tileCache =
        PAINT_CACHE.computeIfAbsent(playerUuid, ignored -> new Long2ObjectOpenHashMap<>());

    int sentThisTick = 0;

    for (int pass = 0; pass < perimeter.length && sentThisTick < MAX_CHUNKS_PER_PLAYER_PER_TICK; pass++) {
      long index = perimeter[(scanStart + pass) % perimeter.length];
      if (!shouldOverlayChunkForPlayer(player, index)) {
        tileCache.remove(index);
        continue;
      }

      int mapChunkX = ChunkUtil.xOfChunkIndex(index);
      int mapChunkZ = ChunkUtil.zOfChunkIndex(index);
      MapImage base = resolveMapImage(mapManager, mapChunkX, mapChunkZ);
      if (base == null) {
        tileCache.remove(index);
        continue;
      }
      if (!TownMapImagePixels.hasPixelData(base)) {
        tileCache.remove(index);
        continue;
      }

      current.add(index);
      int baseIdentity = System.identityHashCode(base);
      CachedTile cached = tileCache.get(index);
      if (cached == null || !cached.matches(baseIdentity, townsSignature, viewerTownId)) {
        MapImage painted = paintChunk(base, mapChunkX, mapChunkZ, towns, viewerTownId);
        if (painted == null) {
          tileCache.remove(index);
          continue;
        }
        cached = new CachedTile(painted, baseIdentity, townsSignature, viewerTownId);
        tileCache.put(index, cached);
      }

      if (!cached.needsSend(baseIdentity, heartbeat)) {
        continue;
      }

      MapImage toSendImage = TownMapImagePixels.cloneImage(cached.painted);
      if (toSendImage != null) {
        toSend.add(new MapChunk(mapChunkX, mapChunkZ, toSendImage));
        cached.markSent(baseIdentity);
        sentThisTick++;
      }
    }

    for (long staleIndex : new LongOpenHashSet(tileCache.keySet())) {
      if (!current.contains(staleIndex)) {
        tileCache.remove(staleIndex);
      }
    }

    if (previous != null && sentThisTick < MAX_CHUNKS_PER_PLAYER_PER_TICK) {
      for (long removed : previous) {
        if (sentThisTick >= MAX_CHUNKS_PER_PLAYER_PER_TICK) {
          break;
        }
        if (current.contains(removed)) {
          continue;
        }
        int cx = ChunkUtil.xOfChunkIndex(removed);
        int cz = ChunkUtil.zOfChunkIndex(removed);
        if (!shouldOverlayChunkForPlayer(player, removed)) {
          continue;
        }
        MapImage base = resolveMapImage(mapManager, cx, cz);
        if (base != null) {
          MapImage pristine = TownMapImagePixels.cloneImage(base);
          if (pristine != null) {
            toSend.add(new MapChunk(cx, cz, pristine));
            sentThisTick++;
          }
        }
      }
    }

    if (!toSend.isEmpty()) {
      playerRef.getPacketHandler().writeNoCache(new UpdateWorldMap(toSend.toArray(MapChunk[]::new), null, null));
    }

    if (current.isEmpty()) {
      LAST_OVERLAY_CHUNKS.remove(playerUuid);
    } else {
      LAST_OVERLAY_CHUNKS.put(playerUuid, current);
    }
  }

  /**
   * Only decorate chunks the client already has. Pushing unexplored perimeter tiles fights BetterMap's
   * RestrictedSpiralIterator and can prevent the explored map from loading.
   */
  private static boolean shouldOverlayChunkForPlayer(@Nonnull Player player, long chunkIndex) {
    return WorldMapTrackerCompat.hasChunkLoadedOnClient(player, chunkIndex);
  }

  @Nonnull
  private static long[] perimeterChunksForWorld(
      @Nonnull String worldName, @Nonnull List<TownRecord> towns, long townsSignature) {
    WorldBorderState state = WORLD_BORDER_STATE.computeIfAbsent(worldName, ignored -> new WorldBorderState());
    synchronized (state) {
      if (state.townsSignature != townsSignature) {
        LongSet perimeter = new LongOpenHashSet();
        TownBorderMapRenderer.collectPerimeterChunkIndices(towns, perimeter);
        state.perimeterChunks = perimeter.toLongArray();
        state.townsSignature = townsSignature;
      }
      return state.perimeterChunks;
    }
  }

  private static long computeTownsSignature(@Nonnull List<TownRecord> towns) {
    long hash = 1L;
    for (TownRecord town : towns) {
      hash = 31L * hash + town.getTownId().getLeastSignificantBits();
      hash = 31L * hash + town.getTownId().getMostSignificantBits();
      hash = 31L * hash + town.getCharterX();
      hash = 31L * hash + town.getCharterZ();
      hash = 31L * hash + town.getTerritoryChunkRadius();
    }
    return hash;
  }

  private static void clearOverlays(
      @Nonnull World world,
      @Nonnull Player player,
      @Nonnull PlayerRef playerRef,
      @Nonnull UUID playerUuid) {
    PAINT_CACHE.remove(playerUuid);
    LongSet previous = LAST_OVERLAY_CHUNKS.remove(playerUuid);
    if (previous == null || previous.isEmpty()) {
      return;
    }
    if (!playerRef.getPacketHandler().getChannel(NetworkChannel.WorldMap).isWritable()) {
      return;
    }
    WorldMapManager mapManager = world.getWorldMapManager();
    List<MapChunk> toSend = new ArrayList<>();
    int sent = 0;
    for (long index : previous) {
      if (sent >= MAX_CHUNKS_PER_PLAYER_PER_TICK) {
        break;
      }
      if (!shouldOverlayChunkForPlayer(player, index)) {
        continue;
      }
      int cx = ChunkUtil.xOfChunkIndex(index);
      int cz = ChunkUtil.zOfChunkIndex(index);
      MapImage base = resolveMapImage(mapManager, cx, cz);
      if (base == null) {
        continue;
      }
      MapImage pristine = TownMapImagePixels.cloneImage(base);
      if (pristine != null) {
        toSend.add(new MapChunk(cx, cz, pristine));
        sent++;
      }
    }
    if (!toSend.isEmpty()) {
      playerRef.getPacketHandler().writeNoCache(new UpdateWorldMap(toSend.toArray(MapChunk[]::new), null, null));
    }
  }

  @Nullable
  private static MapImage resolveMapImage(@Nonnull WorldMapManager mapManager, int mapChunkX, int mapChunkZ) {
    MapImage inMemory = mapManager.getImageIfInMemory(mapChunkX, mapChunkZ);
    if (inMemory != null) {
      return inMemory;
    }
    CompletableFuture<MapImage> pending = mapManager.getImageAsync(mapChunkX, mapChunkZ);
    if (pending.isDone()) {
      return pending.getNow(null);
    }
    return null;
  }

  @Nullable
  private static MapImage paintChunk(
      @Nonnull MapImage base,
      int mapChunkX,
      int mapChunkZ,
      @Nonnull List<TownRecord> towns,
      @Nullable UUID viewerTownId) {
    MapImage copy = TownMapImagePixels.cloneImage(base);
    if (copy == null) {
      return null;
    }
    int[] pixels = TownMapImagePixels.unpackToArgb(copy);
    if (pixels == null) {
      return null;
    }
    TownBorderMapRenderer.paintTownBorders(pixels, copy.width, copy.height, mapChunkX, mapChunkZ, towns, viewerTownId);
    TownMapImagePixels.repackFromArgb(copy, pixels);
    return copy;
  }

  @Nullable
  private static Player findPlayer(@Nonnull World world, @Nonnull UUID playerUuid) {
    for (PlayerRef pref : world.getPlayerRefs()) {
      Ref<EntityStore> ref = pref.getReference();
      if (ref == null || !ref.isValid()) {
        continue;
      }
      UUIDComponent uc = ref.getStore().getComponent(ref, UUIDComponent.getComponentType());
      if (uc != null && playerUuid.equals(uc.getUuid())) {
        return ref.getStore().getComponent(ref, Player.getComponentType());
      }
    }
    return null;
  }
}
