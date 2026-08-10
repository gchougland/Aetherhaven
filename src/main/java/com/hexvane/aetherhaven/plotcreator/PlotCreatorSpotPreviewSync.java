package com.hexvane.aetherhaven.plotcreator;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
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
 * Diffs desired important-spot villager previews vs tracked entities. Tick path uses {@link CommandBuffer} for removes
 * and {@code world.execute} for spawns — never writes to Store directly during a tick system.
 */
public final class PlotCreatorSpotPreviewSync {
    private static final ConcurrentHashMap<UUID, Tracking> TRACKING = new ConcurrentHashMap<>();

    private static final class Tracking {
        final Map<Long, UUID> keyToPreviewUuid = new HashMap<>();
        final Set<Long> pendingSpawnKeys = ConcurrentHashMap.newKeySet();
        final Map<Long, PlotCreatorSpotPreviewCollector.DesiredSpotPreview> keyToDesired = new HashMap<>();
        /** Pose-applied flags live here so component put/clone cannot reset them each tick. */
        final Set<UUID> poseAppliedPreviewUuids = ConcurrentHashMap.newKeySet();
    }

    private PlotCreatorSpotPreviewSync() {}

    public static void clearAll(
        @Nonnull World world,
        @Nonnull UUID ownerPlayerEntityUuid,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        Tracking st = TRACKING.remove(ownerPlayerEntityUuid);
        if (st != null) {
            st.keyToPreviewUuid.clear();
            st.pendingSpawnKeys.clear();
            st.keyToDesired.clear();
            st.poseAppliedPreviewUuids.clear();
        }
        PlotCreatorSpotPreviewSpawner.removeAllForOwner(world, ownerPlayerEntityUuid, commandBuffer);
    }

    public static void sync(
        @Nonnull World world,
        @Nonnull UUID ownerPlayerEntityUuid,
        @Nonnull List<PlotCreatorSpotPreviewCollector.DesiredSpotPreview> desired,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        Tracking st = TRACKING.computeIfAbsent(ownerPlayerEntityUuid, u -> new Tracking());
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

        respawnMissing(world, ownerPlayerEntityUuid, st, desiredByKey);
    }

    /**
     * Re-spawns tracked previews that vanished without a draft change (ambient NPC despawn, etc.).
     * Safe to call every tick while spot previews should be visible.
     */
    public static void respawnMissing(@Nonnull World world, @Nonnull UUID ownerPlayerEntityUuid) {
        Tracking st = TRACKING.get(ownerPlayerEntityUuid);
        if (st == null || st.keyToDesired.isEmpty()) {
            return;
        }
        respawnMissing(world, ownerPlayerEntityUuid, st, st.keyToDesired);
    }

    private static void respawnMissing(
        @Nonnull World world,
        @Nonnull UUID ownerPlayerEntityUuid,
        @Nonnull Tracking st,
        @Nonnull Map<Long, PlotCreatorSpotPreviewCollector.DesiredSpotPreview> desiredByKey
    ) {
        for (PlotCreatorSpotPreviewCollector.DesiredSpotPreview d : desiredByKey.values()) {
            long key = d.key();
            st.keyToDesired.put(key, d);
            UUID existingId = st.keyToPreviewUuid.get(key);
            if (existingId == null) {
                scheduleSpawn(world, ownerPlayerEntityUuid, st, d);
                continue;
            }
            Ref<EntityStore> previewRef = world.getEntityRef(existingId);
            if (previewRef == null || !previewRef.isValid()) {
                st.keyToPreviewUuid.remove(key);
                st.poseAppliedPreviewUuids.remove(existingId);
                scheduleSpawn(world, ownerPlayerEntityUuid, st, d);
            }
        }
    }

    public static boolean isPoseApplied(@Nonnull UUID ownerPlayerEntityUuid, @Nullable UUID previewUuid) {
        if (previewUuid == null) {
            return false;
        }
        Tracking st = TRACKING.get(ownerPlayerEntityUuid);
        return st != null && st.poseAppliedPreviewUuids.contains(previewUuid);
    }

    public static void markPoseApplied(@Nonnull UUID ownerPlayerEntityUuid, @Nullable UUID previewUuid) {
        if (previewUuid == null) {
            return;
        }
        Tracking st = TRACKING.get(ownerPlayerEntityUuid);
        if (st != null) {
            st.poseAppliedPreviewUuids.add(previewUuid);
        }
    }

    @Nullable
    public static PlotCreatorSpotPreviewCollector.DesiredSpotPreview desiredForKey(
        @Nonnull UUID ownerPlayerEntityUuid,
        long previewKey
    ) {
        Tracking st = TRACKING.get(ownerPlayerEntityUuid);
        if (st == null) {
            return null;
        }
        return st.keyToDesired.get(previewKey);
    }

    private static void scheduleSpawn(
        @Nonnull World world,
        @Nonnull UUID ownerPlayerEntityUuid,
        @Nonnull Tracking st,
        @Nonnull PlotCreatorSpotPreviewCollector.DesiredSpotPreview desired
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
                UUID spawned = PlotCreatorSpotPreviewSpawner.spawnPreview(world, ownerPlayerEntityUuid, copy);
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
}
