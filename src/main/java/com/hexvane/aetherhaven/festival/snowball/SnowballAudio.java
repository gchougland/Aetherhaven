package com.hexvane.aetherhaven.festival.snowball;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.bard.BardEnvironmentMusic;
import com.hexvane.aetherhaven.festival.FestivalPrefabSwapService;
import com.hexvane.aetherhaven.festival.FestivalService;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.builtin.audio.components.ForcedMusicTracker;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.asset.type.musiccontainer.config.MusicContainer;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Battle of Glory forced music for players near an active snowball fight. */
public final class SnowballAudio {
    public static final String MUSIC_BATTLE = "Track_Aetherhaven_Snowball_Battle_Of_Glory";

    private static final double AUDIO_RADIUS = 56.0;

    private SnowballAudio() {}

    /** Keeps fight music on nearby players while the match is live. */
    public static void tickFightMusic(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nullable Vector3d squareCenter,
        @Nonnull SnowballSession session
    ) {
        applyFightMusic(store, town, squareCenter, session);
    }

    /** Clears fight music from everyone this fight marked as listening. */
    public static void stopFightMusic(
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nonnull SnowballSession session
    ) {
        Set<UUID> listeners = new HashSet<>(session.fightMusicListenersView());
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
                session.clearFightMusicListener(uc.getUuid());
            }
        });
        session.clearFightMusicListeners();
    }

    @Nullable
    public static Vector3d squareCenter(@Nonnull AetherhavenPlugin plugin, @Nonnull TownRecord town) {
        PlotInstance square = FestivalService.findFestivalSquare(plugin, town);
        if (square == null) {
            return null;
        }
        return FestivalPrefabSwapService.spotWorldPosition(plugin, square, 0, 6, 0);
    }

    private static void applyFightMusic(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nullable Vector3d squareCenter,
        @Nonnull SnowballSession session
    ) {
        if (squareCenter == null) {
            return;
        }
        int musicIndex = MusicContainer.getAssetMap().getIndex(MUSIC_BATTLE);
        if (musicIndex == 0 || musicIndex == Integer.MIN_VALUE) {
            return;
        }
        double radiusSq = AUDIO_RADIUS * AUDIO_RADIUS;
        Query<EntityStore> query = Query.and(
            Player.getComponentType(),
            PlayerRef.getComponentType(),
            UUIDComponent.getComponentType(),
            TransformComponent.getComponentType(),
            ForcedMusicTracker.getComponentType()
        );
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
                if (tc.getPosition().distanceSquared(squareCenter) > radiusSq) {
                    continue;
                }
                BardEnvironmentMusic.setForcedMusic(ref, commandBuffer, store, pr, tracker, musicIndex);
                session.markFightMusicListener(uc.getUuid());
            }
        });
    }
}
