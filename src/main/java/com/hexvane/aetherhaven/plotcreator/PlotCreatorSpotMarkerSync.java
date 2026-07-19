package com.hexvane.aetherhaven.plotcreator;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Diffs desired important-spot markers vs tracked entities. Tick path uses {@link CommandBuffer} for removes and
 * {@code world.execute} for spawns — never writes to Store directly during a tick system.
 */
public final class PlotCreatorSpotMarkerSync {
    private static final ConcurrentHashMap<UUID, Tracking> TRACKING = new ConcurrentHashMap<>();

    private static final class Tracking {
        final Map<Long, UUID> keyToMarkerUuid = new HashMap<>();
        final Set<Long> pendingSpawnKeys = ConcurrentHashMap.newKeySet();
        final Map<Long, String> keyToTexture = new HashMap<>();
        final Map<Long, String> keyToLabel = new HashMap<>();
    }

    private PlotCreatorSpotMarkerSync() {}

    public static void clearAll(
        @Nonnull World world,
        @Nonnull UUID ownerPlayerEntityUuid,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        Tracking st = TRACKING.remove(ownerPlayerEntityUuid);
        if (st != null) {
            st.keyToMarkerUuid.clear();
            st.pendingSpawnKeys.clear();
            st.keyToTexture.clear();
            st.keyToLabel.clear();
        }
        // Single remove path — do not also remove-by-uuid (double CommandBuffer.removeEntity crashes).
        PlotCreatorSpotMarkerSpawner.removeAllForOwner(world, ownerPlayerEntityUuid, commandBuffer);
    }

    public static void sync(
        @Nonnull World world,
        @Nonnull UUID ownerPlayerEntityUuid,
        @Nonnull List<PlotCreatorSpotMarkerCollector.DesiredSpotMarker> desired,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        Tracking st = TRACKING.computeIfAbsent(ownerPlayerEntityUuid, u -> new Tracking());
        Map<Long, PlotCreatorSpotMarkerCollector.DesiredSpotMarker> desiredByKey = new HashMap<>(desired.size());
        for (PlotCreatorSpotMarkerCollector.DesiredSpotMarker d : desired) {
            desiredByKey.put(d.key(), d);
        }

        Set<Long> desiredKeys = desiredByKey.keySet();
        Iterator<Map.Entry<Long, UUID>> it = st.keyToMarkerUuid.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, UUID> e = it.next();
            long key = e.getKey();
            if (desiredKeys.contains(key) || st.pendingSpawnKeys.contains(key)) {
                continue;
            }
            UUID markerId = e.getValue();
            it.remove();
            st.keyToTexture.remove(key);
            st.keyToLabel.remove(key);
            PlotCreatorSpotMarkerSpawner.removeMarkerByUuid(world, markerId, commandBuffer);
        }

        for (PlotCreatorSpotMarkerCollector.DesiredSpotMarker d : desiredByKey.values()) {
            long key = d.key();
            UUID existingId = st.keyToMarkerUuid.get(key);
            if (existingId == null) {
                scheduleSpawn(world, ownerPlayerEntityUuid, st, d);
                continue;
            }
            Ref<EntityStore> markerRef = world.getEntityRef(existingId);
            if (markerRef == null || !markerRef.isValid()) {
                st.keyToMarkerUuid.remove(key);
                st.keyToTexture.remove(key);
                st.keyToLabel.remove(key);
                scheduleSpawn(world, ownerPlayerEntityUuid, st, d);
                continue;
            }
            String prevTex = st.keyToTexture.get(key);
            String prevLabel = st.keyToLabel.get(key);
            if (d.texturePath().equals(prevTex) && d.nameplateText().equals(prevLabel)) {
                continue;
            }
            // Texture/label change: respawn (nameplate updates via Store are awkward mid-tick).
            st.keyToMarkerUuid.remove(key);
            st.keyToTexture.remove(key);
            st.keyToLabel.remove(key);
            PlotCreatorSpotMarkerSpawner.removeMarkerByUuid(world, existingId, commandBuffer);
            scheduleSpawn(world, ownerPlayerEntityUuid, st, d);
        }
    }

    private static void scheduleSpawn(
        @Nonnull World world,
        @Nonnull UUID ownerPlayerEntityUuid,
        @Nonnull Tracking st,
        @Nonnull PlotCreatorSpotMarkerCollector.DesiredSpotMarker desired
    ) {
        long key = desired.key();
        if (!st.pendingSpawnKeys.add(key)) {
            return;
        }
        final PlotCreatorSpotMarkerCollector.DesiredSpotMarker copy = desired;
        world.execute(() -> {
            try {
                if (st.keyToMarkerUuid.containsKey(key)) {
                    return;
                }
                UUID spawned = PlotCreatorSpotMarkerSpawner.spawnMarker(world, ownerPlayerEntityUuid, copy);
                if (spawned != null) {
                    st.keyToMarkerUuid.put(key, spawned);
                    st.keyToTexture.put(key, copy.texturePath());
                    st.keyToLabel.put(key, copy.nameplateText());
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
