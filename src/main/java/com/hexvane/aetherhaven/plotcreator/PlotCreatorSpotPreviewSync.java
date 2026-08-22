package com.hexvane.aetherhaven.plotcreator;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Diffs desired important-spot villager previews vs tracked entities. Plot creator and POI staff use separate
 * channels so clearing one never removes the other. Tick path uses {@link CommandBuffer} for removes and
 * {@code world.execute} for spawns — never writes to Store directly during a tick system.
 */
public final class PlotCreatorSpotPreviewSync {
    private static final ConcurrentHashMap<UUID, Tracking> TRACKING_PLOT_CREATOR = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Tracking> TRACKING_POI_TOOL = new ConcurrentHashMap<>();

    private static final class Tracking {
        final Map<Long, UUID> keyToPreviewUuid = new HashMap<>();
        final Set<Long> pendingSpawnKeys = ConcurrentHashMap.newKeySet();
        final Map<Long, PlotCreatorSpotPreviewCollector.DesiredSpotPreview> keyToDesired = new HashMap<>();
        /** Pose-applied flags live here so component put/clone cannot reset them each tick. */
        final Set<UUID> poseAppliedPreviewUuids = ConcurrentHashMap.newKeySet();
    }

    private PlotCreatorSpotPreviewSync() {}

    @Nonnull
    private static ConcurrentHashMap<UUID, Tracking> trackingMap(boolean poiToolChannel) {
        return poiToolChannel ? TRACKING_POI_TOOL : TRACKING_PLOT_CREATOR;
    }

    public static void clearAll(
        @Nonnull World world,
        @Nonnull UUID ownerPlayerEntityUuid,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        clearAll(world, ownerPlayerEntityUuid, commandBuffer, false);
    }

    public static void clearAll(
        @Nonnull World world,
        @Nonnull UUID ownerPlayerEntityUuid,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        boolean poiToolChannel
    ) {
        Tracking st = trackingMap(poiToolChannel).remove(ownerPlayerEntityUuid);
        if (st != null) {
            st.keyToPreviewUuid.clear();
            st.pendingSpawnKeys.clear();
            st.keyToDesired.clear();
            st.poseAppliedPreviewUuids.clear();
        }
        PlotCreatorSpotPreviewSpawner.removeAllForOwner(
            world, ownerPlayerEntityUuid, commandBuffer, poiToolChannel
        );
    }

    public static void sync(
        @Nonnull World world,
        @Nonnull UUID ownerPlayerEntityUuid,
        @Nonnull List<PlotCreatorSpotPreviewCollector.DesiredSpotPreview> desired,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        sync(world, ownerPlayerEntityUuid, desired, commandBuffer, false);
    }

    public static void sync(
        @Nonnull World world,
        @Nonnull UUID ownerPlayerEntityUuid,
        @Nonnull List<PlotCreatorSpotPreviewCollector.DesiredSpotPreview> desired,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        boolean poiToolChannel
    ) {
        Tracking st = trackingMap(poiToolChannel).computeIfAbsent(ownerPlayerEntityUuid, u -> new Tracking());
        Map<Long, PlotCreatorSpotPreviewCollector.DesiredSpotPreview> desiredByKey = new HashMap<>(desired.size());
        for (PlotCreatorSpotPreviewCollector.DesiredSpotPreview d : desired) {
            desiredByKey.put(d.key(), d);
        }

        Set<Long> desiredKeys = desiredByKey.keySet();
        Iterator<Map.Entry<Long, UUID>> it = st.keyToPreviewUuid.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, UUID> e = it.next();
            long key = e.getKey();
            if (desiredKeys.contains(key) || st.pendingSpawnKeys.contains(key)) {
                continue;
            }
            UUID previewId = e.getValue();
            it.remove();
            st.keyToDesired.remove(key);
            if (previewId != null) {
                st.poseAppliedPreviewUuids.remove(previewId);
            }
            PlotCreatorSpotPreviewSpawner.removePreviewByUuid(world, previewId, commandBuffer);
        }

        respawnMissing(world, ownerPlayerEntityUuid, st, desiredByKey, poiToolChannel);
    }

    /**
     * Re-spawns tracked previews that vanished without a draft change (ambient NPC despawn, etc.).
     * Safe to call every tick while spot previews should be visible.
     */
    public static void respawnMissing(@Nonnull World world, @Nonnull UUID ownerPlayerEntityUuid) {
        respawnMissing(world, ownerPlayerEntityUuid, false);
    }

    public static void respawnMissing(
        @Nonnull World world,
        @Nonnull UUID ownerPlayerEntityUuid,
        boolean poiToolChannel
    ) {
        Tracking st = trackingMap(poiToolChannel).get(ownerPlayerEntityUuid);
        if (st == null || st.keyToDesired.isEmpty()) {
            return;
        }
        respawnMissing(world, ownerPlayerEntityUuid, st, st.keyToDesired, poiToolChannel);
    }

    private static void respawnMissing(
        @Nonnull World world,
        @Nonnull UUID ownerPlayerEntityUuid,
        @Nonnull Tracking st,
        @Nonnull Map<Long, PlotCreatorSpotPreviewCollector.DesiredSpotPreview> desiredByKey,
        boolean poiToolChannel
    ) {
        for (PlotCreatorSpotPreviewCollector.DesiredSpotPreview d : desiredByKey.values()) {
            long key = d.key();
            st.keyToDesired.put(key, d);
            UUID existingId = st.keyToPreviewUuid.get(key);
            if (existingId == null) {
                scheduleSpawn(world, ownerPlayerEntityUuid, st, d, poiToolChannel);
                continue;
            }
            Ref<EntityStore> previewRef = world.getEntityRef(existingId);
            if (previewRef == null || !previewRef.isValid()) {
                st.keyToPreviewUuid.remove(key);
                st.poseAppliedPreviewUuids.remove(existingId);
                scheduleSpawn(world, ownerPlayerEntityUuid, st, d, poiToolChannel);
            }
        }
    }

    public static boolean isPoseApplied(@Nonnull UUID ownerPlayerEntityUuid, @Nullable UUID previewUuid) {
        return isPoseApplied(ownerPlayerEntityUuid, previewUuid, false);
    }

    public static boolean isPoseApplied(
        @Nonnull UUID ownerPlayerEntityUuid,
        @Nullable UUID previewUuid,
        boolean poiToolChannel
    ) {
        if (previewUuid == null) {
            return false;
        }
        Tracking st = trackingMap(poiToolChannel).get(ownerPlayerEntityUuid);
        return st != null && st.poseAppliedPreviewUuids.contains(previewUuid);
    }

    public static void markPoseApplied(@Nonnull UUID ownerPlayerEntityUuid, @Nullable UUID previewUuid) {
        markPoseApplied(ownerPlayerEntityUuid, previewUuid, false);
    }

    public static void markPoseApplied(
        @Nonnull UUID ownerPlayerEntityUuid,
        @Nullable UUID previewUuid,
        boolean poiToolChannel
    ) {
        if (previewUuid == null) {
            return;
        }
        Tracking st = trackingMap(poiToolChannel).get(ownerPlayerEntityUuid);
        if (st != null) {
            st.poseAppliedPreviewUuids.add(previewUuid);
        }
    }

    @Nullable
    public static PlotCreatorSpotPreviewCollector.DesiredSpotPreview desiredForKey(
        @Nonnull UUID ownerPlayerEntityUuid,
        long previewKey
    ) {
        return desiredForKey(ownerPlayerEntityUuid, previewKey, false);
    }

    @Nullable
    public static PlotCreatorSpotPreviewCollector.DesiredSpotPreview desiredForKey(
        @Nonnull UUID ownerPlayerEntityUuid,
        long previewKey,
        boolean poiToolChannel
    ) {
        Tracking st = trackingMap(poiToolChannel).get(ownerPlayerEntityUuid);
        if (st == null) {
            return null;
        }
        return st.keyToDesired.get(previewKey);
    }

    private static void scheduleSpawn(
        @Nonnull World world,
        @Nonnull UUID ownerPlayerEntityUuid,
        @Nonnull Tracking st,
        @Nonnull PlotCreatorSpotPreviewCollector.DesiredSpotPreview desired,
        boolean poiToolChannel
    ) {
        long key = desired.key();
        if (!st.pendingSpawnKeys.add(key)) {
            return;
        }
        final PlotCreatorSpotPreviewCollector.DesiredSpotPreview copy = desired;
        world.execute(() -> {
            try {
                if (st.keyToPreviewUuid.containsKey(key)) {
                    return;
                }
                UUID spawned =
                    PlotCreatorSpotPreviewSpawner.spawnPreview(
                        world, ownerPlayerEntityUuid, copy, poiToolChannel
                    );
                if (spawned != null) {
                    st.keyToPreviewUuid.put(key, spawned);
                    st.keyToDesired.put(key, copy);
                    st.poseAppliedPreviewUuids.remove(spawned);
                }
            } finally {
                st.pendingSpawnKeys.remove(key);
            }
        });
    }

    @Nonnull
    public static UUID requireOwnerEntityUuid(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerRef) {
        UUIDComponent uc = store.getComponent(playerRef, UUIDComponent.getComponentType());
        if (uc == null) {
            throw new IllegalStateException("Player missing UUIDComponent");
        }
        return uc.getUuid();
    }

    /**
     * Applies initial pose / hold / work beats for tracked previews on one channel owned by this player.
     */
    public static void tickPosesForOwner(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull UUID ownerUuid
    ) {
        tickPosesForOwner(world, store, commandBuffer, ownerUuid, false);
    }

    public static void tickPosesForOwner(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull UUID ownerUuid,
        boolean poiToolChannel
    ) {
        long nowMs = System.currentTimeMillis();
        store.forEachChunk(
            Query.and(PlotCreatorSpotPreview.getComponentType()),
            (chunk, chunkCommandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    PlotCreatorSpotPreview preview = chunk.getComponent(i, PlotCreatorSpotPreview.getComponentType());
                    if (preview == null
                        || !ownerUuid.equals(preview.getOwnerPlayerUuid())
                        || preview.isPoiToolChannel() != poiToolChannel) {
                        continue;
                    }
                    Ref<EntityStore> npcRef = chunk.getReferenceTo(i);
                    if (!npcRef.isValid()) {
                        continue;
                    }
                    UUIDComponent previewUuidComp = store.getComponent(npcRef, UUIDComponent.getComponentType());
                    UUID previewUuid = previewUuidComp != null ? previewUuidComp.getUuid() : null;
                    PlotCreatorSpotPreviewCollector.DesiredSpotPreview desired =
                        desiredForKey(ownerUuid, preview.getPreviewKey(), poiToolChannel);
                    if (desired == null) {
                        continue;
                    }
                    if (!isPoseApplied(ownerUuid, previewUuid, poiToolChannel)) {
                        PlotCreatorSpotPreviewPose.applyInitialPose(npcRef, store, commandBuffer, desired);
                        markPoseApplied(ownerUuid, previewUuid, poiToolChannel);
                        preview.setPoseApplied(true);
                        preview.setLastWorkBeatEpochMs(nowMs);
                        commandBuffer.putComponent(npcRef, PlotCreatorSpotPreview.getComponentType(), preview);
                        continue;
                    }
                    PlotCreatorSpotPreviewPose.holdPosition(npcRef, store, commandBuffer, desired);
                    if (desired.poiBlockX() == null) {
                        continue;
                    }
                    long last = preview.getLastWorkBeatEpochMs();
                    if (PlotCreatorSpotPreviewPose.tickWorkBeat(
                        npcRef,
                        store,
                        commandBuffer,
                        desired,
                        null,
                        nowMs,
                        last
                    )) {
                        preview.setLastWorkBeatEpochMs(nowMs);
                        commandBuffer.putComponent(npcRef, PlotCreatorSpotPreview.getComponentType(), preview);
                    }
                }
            }
        );
    }
}
