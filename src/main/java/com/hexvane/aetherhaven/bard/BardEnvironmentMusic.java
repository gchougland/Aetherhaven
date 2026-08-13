package com.hexvane.aetherhaven.bard;

import com.hexvane.aetherhaven.bard.data.BardSongDefinition;
import com.hypixel.hytale.builtin.audio.components.ForcedMusicTracker;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.packets.world.UpdateForcedMusic;
import com.hypixel.hytale.server.core.asset.type.ambiencefx.config.AmbienceFX;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Per-player bard music via {@link UpdateForcedMusic} and {@link ForcedMusicTracker}, matching
 * {@code ForcedMusicSystems.Tick}. Index {@code 0} clears forced music.
 */
public final class BardEnvironmentMusic {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private BardEnvironmentMusic() {}

    public static int resolveMusicContainerIndex(@Nonnull BardSongDefinition song) {
        String ambienceFxId = song.getAmbienceFxId();
        if (ambienceFxId.isEmpty()) {
            return 0;
        }
        AmbienceFX fx = AmbienceFX.getAssetMap().getAsset(ambienceFxId);
        if (fx == null) {
            LOGGER.atWarning().log("Unknown bard AmbienceFX %s", ambienceFxId);
            return 0;
        }
        int index = fx.getMusicContainerIndex();
        if (index == 0) {
            LOGGER.atWarning().log("No music container for bard AmbienceFX %s", ambienceFxId);
        }
        return index;
    }

    /**
     * Updates the player's forced music tracker and sends the packet immediately. Always pass a
     * {@link CommandBuffer} when the store processing lock may be held (tick systems,
     * {@link Store#forEachChunk} callbacks). Only pass {@code null} when writing outside processing.
     */
    public static void setForcedMusic(
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull ForcedMusicTracker tracker,
        int musicContainerIndex
    ) {
        setForcedMusic(playerEntityRef, commandBuffer, store, playerRef, tracker, musicContainerIndex, false);
    }

    public static void setForcedMusic(
        @Nonnull Ref<EntityStore> playerEntityRef,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull ForcedMusicTracker tracker,
        int musicContainerIndex,
        boolean force
    ) {
        if (!force
            && tracker.getCurrentContainerIndex() == musicContainerIndex
            && tracker.getLastSentContainerIndex() == musicContainerIndex) {
            return;
        }
        ForcedMusicTracker updated = (ForcedMusicTracker) tracker.clone();
        updated.setCurrentContainerIndex(musicContainerIndex);
        updated.setLastSentContainerIndex(musicContainerIndex);
        if (commandBuffer != null) {
            commandBuffer.putComponent(playerEntityRef, ForcedMusicTracker.getComponentType(), updated);
        } else {
            store.putComponent(playerEntityRef, ForcedMusicTracker.getComponentType(), updated);
        }
        UpdateForcedMusic packet = updated.getMusicPacket();
        packet.containerIndex = musicContainerIndex;
        playerRef.getPacketHandler().write(packet);
    }

    /** Clears forced bard music for every player currently tracked as listening. */
    public static void stopAllListeningPlayers(
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        BardMusicProximityState proximityState = store.getResource(BardMusicProximityState.getResourceType());
        store.forEachChunk(
            Archetype.of(
                Player.getComponentType(),
                PlayerRef.getComponentType(),
                UUIDComponent.getComponentType(),
                ForcedMusicTracker.getComponentType()
            ),
            (archetypeChunk, chunkCommandBuffer) -> {
                CommandBuffer<EntityStore> writeBuffer =
                    commandBuffer != null ? commandBuffer : chunkCommandBuffer;
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    Ref<EntityStore> playerEntityRef = archetypeChunk.getReferenceTo(i);
                    PlayerRef playerRef = archetypeChunk.getComponent(i, PlayerRef.getComponentType());
                    UUIDComponent uuidComponent = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                    ForcedMusicTracker tracker =
                        archetypeChunk.getComponent(i, ForcedMusicTracker.getComponentType());
                    if (playerEntityRef == null
                        || !playerEntityRef.isValid()
                        || playerRef == null
                        || uuidComponent == null
                        || tracker == null) {
                        continue;
                    }
                    UUID playerId = uuidComponent.getUuid();
                    if (!proximityState.isListening(playerId)) {
                        continue;
                    }
                    setForcedMusic(playerEntityRef, writeBuffer, store, playerRef, tracker, 0);
                    proximityState.clear(playerId);
                }
            }
        );
    }

    /** Re-sends the current bard track to listeners so a looped song starts again. */
    public static void resendToListeningPlayers(
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        int musicContainerIndex
    ) {
        if (musicContainerIndex == 0) {
            return;
        }
        BardMusicProximityState proximityState = store.getResource(BardMusicProximityState.getResourceType());
        store.forEachChunk(
            Archetype.of(
                Player.getComponentType(),
                PlayerRef.getComponentType(),
                UUIDComponent.getComponentType(),
                ForcedMusicTracker.getComponentType()
            ),
            (archetypeChunk, chunkCommandBuffer) -> {
                CommandBuffer<EntityStore> writeBuffer =
                    commandBuffer != null ? commandBuffer : chunkCommandBuffer;
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    Ref<EntityStore> playerEntityRef = archetypeChunk.getReferenceTo(i);
                    PlayerRef playerRef = archetypeChunk.getComponent(i, PlayerRef.getComponentType());
                    UUIDComponent uuidComponent = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                    ForcedMusicTracker tracker =
                        archetypeChunk.getComponent(i, ForcedMusicTracker.getComponentType());
                    if (playerEntityRef == null
                        || !playerEntityRef.isValid()
                        || playerRef == null
                        || uuidComponent == null
                        || tracker == null) {
                        continue;
                    }
                    UUID playerId = uuidComponent.getUuid();
                    if (!proximityState.isListening(playerId)) {
                        continue;
                    }
                    setForcedMusic(
                        playerEntityRef,
                        writeBuffer,
                        store,
                        playerRef,
                        tracker,
                        musicContainerIndex,
                        true
                    );
                    proximityState.setActive(playerId, musicContainerIndex);
                }
            }
        );
    }
}
