package com.hexvane.aetherhaven.festival.pigrace;

import com.hexvane.aetherhaven.bard.BardEnvironmentMusic;
import com.hexvane.aetherhaven.festival.FestivalPrefabSwapService;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.builtin.audio.components.ForcedMusicTracker;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.NonSerialized;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.musiccontainer.config.MusicContainer;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.AudioComponent;
import com.hypixel.hytale.server.core.modules.entity.component.Intangible;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.entity.tracker.NetworkId;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Suspense music, start whistle, continuous hooves, and finish brass for a live pig race. */
public final class PigRaceAudio {
    public static final String SOUND_WHISTLE_START = "Aetherhaven_Festival_Pig_Race_Whistle_Start";
    public static final String SOUND_FINISH = "Aetherhaven_Festival_Pig_Race_Finish";
    public static final String SOUND_HOOVES = "Aetherhaven_Festival_Pig_Race_Hooves";
    public static final String MUSIC_SUSPENSE = "Track_Aetherhaven_Pig_Race_Suspense";

    private static final double AUDIO_RADIUS = 48.0;

    private PigRaceAudio() {}

    /** Suspense music as soon as Start the race is chosen (pigs may still be waiting). */
    public static void onRaceMusicStarted(
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull TownRecord town,
        @Nullable Vector3d squareCenter
    ) {
        applySuspenseMusic(store, commandBuffer, town, squareCenter);
    }

    /** Whistle and looping hooves when the start delay ends and pigs leave the line. */
    public static void onRaceGo(
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PigRaceSession session,
        @Nullable Vector3d squareCenter
    ) {
        playWorldSfx(store, squareCenter, SOUND_WHISTLE_START);
        startHoovesLoop(store, commandBuffer, session, squareCenter);
    }

    /** Keeps suspense music on nearby players while racing or waiting to go. */
    public static void tickWhileRacing(
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PigRaceSession session,
        @Nonnull TownRecord town,
        @Nullable Vector3d squareCenter
    ) {
        applySuspenseMusic(store, commandBuffer, town, squareCenter);
    }

    /** Finish brass, stop hooves, and clear race music. */
    public static void onRaceFinished(
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PigRaceSession session,
        @Nullable Vector3d squareCenter
    ) {
        playWorldSfx(store, squareCenter, SOUND_FINISH);
        stopHoovesLoop(store, commandBuffer, session);
        clearSuspenseMusic(store, commandBuffer, session);
    }

    public static void stopHoovesLoop(
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PigRaceSession session
    ) {
        UUID emitterId = session.takeHoovesEmitterUuid();
        if (emitterId == null) {
            return;
        }
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(emitterId);
        if (ref == null || !ref.isValid()) {
            return;
        }
        if (commandBuffer != null) {
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
        } else {
            store.removeEntity(ref, RemoveReason.REMOVE);
        }
    }

    public static void clearSuspenseMusic(
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PigRaceSession session
    ) {
        Set<UUID> listeners = new HashSet<>(session.raceMusicListenersView());
        if (listeners.isEmpty()) {
            return;
        }
        Query<EntityStore> query = Query.and(
            PlayerRef.getComponentType(),
            UUIDComponent.getComponentType(),
            ForcedMusicTracker.getComponentType()
        );
        store.forEachChunk(query, (chunk, chunkBuffer) -> {
            CommandBuffer<EntityStore> write = commandBuffer != null ? commandBuffer : chunkBuffer;
            for (int i = 0; i < chunk.size(); i++) {
                UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                PlayerRef pr = chunk.getComponent(i, PlayerRef.getComponentType());
                ForcedMusicTracker tracker = chunk.getComponent(i, ForcedMusicTracker.getComponentType());
                Ref<EntityStore> ref = chunk.getReferenceTo(i);
                if (uc == null || pr == null || tracker == null || ref == null || !ref.isValid()) {
                    continue;
                }
                if (!listeners.contains(uc.getUuid())) {
                    continue;
                }
                BardEnvironmentMusic.setForcedMusic(ref, write, store, pr, tracker, 0);
                session.clearRaceMusicListener(uc.getUuid());
            }
        });
        session.clearRaceMusicListeners();
    }

    /** Stops hooves, race music, and the bettor camera when the festival ends mid-race. */
    public static void stopAllRaceAudio(
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PigRaceSession session
    ) {
        stopHoovesLoop(store, commandBuffer, session);
        clearSuspenseMusic(store, commandBuffer, session);
        PigRaceCamera.deactivateAll(store, session);
    }

    @Nullable
    public static Vector3d squareCenter(
        @Nonnull com.hexvane.aetherhaven.AetherhavenPlugin plugin,
        @Nonnull TownRecord town
    ) {
        UUID plotId = town.getActiveFestivalPlotId();
        if (plotId == null) {
            return null;
        }
        PlotInstance square = town.findPlotById(plotId);
        if (square == null) {
            return null;
        }
        return FestivalPrefabSwapService.spotWorldPosition(plugin, square, 0, 6, 0);
    }

    private static void startHoovesLoop(
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PigRaceSession session,
        @Nullable Vector3d squareCenter
    ) {
        stopHoovesLoop(store, commandBuffer, session);
        if (squareCenter == null) {
            return;
        }
        int soundIndex = SoundEvent.getAssetMap().getIndex(SOUND_HOOVES);
        if (soundIndex == SoundEvent.EMPTY_ID || soundIndex == Integer.MIN_VALUE) {
            return;
        }
        UUID emitterId = UUID.randomUUID();
        Holder<EntityStore> holder = EntityStore.REGISTRY.newHolder();
        holder.addComponent(
            TransformComponent.getComponentType(),
            new TransformComponent(new Vector3d(squareCenter), new Rotation3f())
        );
        AudioComponent audio = new AudioComponent();
        audio.addSound(soundIndex);
        holder.addComponent(AudioComponent.getComponentType(), audio);
        holder.addComponent(Intangible.getComponentType(), Intangible.INSTANCE);
        holder.addComponent(NetworkId.getComponentType(), new NetworkId(commandBuffer.getExternalData().takeNextNetworkId()));
        holder.addComponent(UUIDComponent.getComponentType(), new UUIDComponent(emitterId));
        holder.addComponent(EntityStore.REGISTRY.getNonSerializedComponentType(), NonSerialized.get());
        commandBuffer.addEntity(holder, AddReason.SPAWN);
        session.setHoovesEmitterUuid(emitterId);
    }

    private static void playWorldSfx(
        @Nonnull Store<EntityStore> store,
        @Nullable Vector3d squareCenter,
        @Nonnull String soundEventId
    ) {
        if (squareCenter == null) {
            return;
        }
        int idx = SoundEvent.getAssetMap().getIndex(soundEventId);
        if (idx == SoundEvent.EMPTY_ID || idx == Integer.MIN_VALUE) {
            return;
        }
        SoundUtil.playSoundEvent3d(
            idx,
            SoundCategory.SFX,
            squareCenter.x,
            squareCenter.y,
            squareCenter.z,
            1.0F,
            1.0F,
            store
        );
    }

    private static void applySuspenseMusic(
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull TownRecord town,
        @Nullable Vector3d squareCenter
    ) {
        if (squareCenter == null) {
            return;
        }
        int musicIndex = MusicContainer.getAssetMap().getIndex(MUSIC_SUSPENSE);
        if (musicIndex == 0 || musicIndex == Integer.MIN_VALUE) {
            return;
        }
        PigRaceSession session = PigRaceSessionIndex.get(town.getTownId());
        if (session == null) {
            return;
        }
        forEachNearbyPlayer(store, town, squareCenter, (playerEntityRef, playerRef, tracker) -> {
            UUIDComponent uc = store.getComponent(playerEntityRef, UUIDComponent.getComponentType());
            if (uc == null) {
                return;
            }
            BardEnvironmentMusic.setForcedMusic(
                playerEntityRef,
                commandBuffer,
                store,
                playerRef,
                tracker,
                musicIndex
            );
            session.markRaceMusicListener(uc.getUuid());
        });
    }

    private static void forEachNearbyPlayer(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nullable Vector3d squareCenter,
        @Nonnull NearbyPlayerConsumer consumer
    ) {
        Query<EntityStore> query = Query.and(
            Player.getComponentType(),
            PlayerRef.getComponentType(),
            UUIDComponent.getComponentType(),
            TransformComponent.getComponentType(),
            ForcedMusicTracker.getComponentType()
        );
        double radiusSq = AUDIO_RADIUS * AUDIO_RADIUS;
        store.forEachChunk(query, (chunk, commandBuffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
                PlayerRef pr = chunk.getComponent(i, PlayerRef.getComponentType());
                TransformComponent tc = chunk.getComponent(i, TransformComponent.getComponentType());
                ForcedMusicTracker tracker = chunk.getComponent(i, ForcedMusicTracker.getComponentType());
                Ref<EntityStore> ref = chunk.getReferenceTo(i);
                if (uc == null || pr == null || tc == null || tracker == null || ref == null || !ref.isValid()) {
                    continue;
                }
                boolean near = squareCenter != null
                    && tc.getPosition().distanceSquared(squareCenter) <= radiusSq;
                if (!near) {
                    continue;
                }
                consumer.accept(ref, pr, tracker);
            }
        });
    }

    @FunctionalInterface
    private interface NearbyPlayerConsumer {
        void accept(
            @Nonnull Ref<EntityStore> playerEntityRef,
            @Nonnull PlayerRef playerRef,
            @Nonnull ForcedMusicTracker tracker
        );
    }
}
