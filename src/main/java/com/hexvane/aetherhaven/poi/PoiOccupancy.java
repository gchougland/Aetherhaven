package com.hexvane.aetherhaven.poi;

import com.hexvane.aetherhaven.autonomy.VillagerAutonomyState;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;

/**
 * Counts how many town villagers are already traveling to or using each <strong>world cell</strong> targeted by a POI,
 * so {@link com.hexvane.aetherhaven.autonomy.PoiScoring} does not overfill a bed (capacity 1) even when two registry
 * entries accidentally share the same block coordinates.
 * <p>
 * Cached per world tick + town to avoid O(n^2) scans when many villagers decide in the same frame.
 */
public final class PoiOccupancy {
    private static final ConcurrentHashMap<String, Map<String, Integer>> CACHE = new ConcurrentHashMap<>();
    private static volatile long cachedWorldTick = -1L;
    private static volatile String cachedWorldName = "";

    private PoiOccupancy() {}

    /**
     * @return map key {@code "x,y,z"} of the target POI's anchor cell → number of town NPCs traveling to or using a POI
     *         at that cell
     */
    @Nonnull
    public static Map<String, Integer> cellOccupancyForTown(
        @Nonnull com.hypixel.hytale.server.core.universe.world.World world,
        @Nonnull UUID townId,
        @Nonnull Store<EntityStore> store,
        @Nonnull PoiRegistry registry
    ) {
        long t = world.getTick();
        String w = world.getName();
        if (t != cachedWorldTick || !w.equals(cachedWorldName)) {
            CACHE.clear();
            cachedWorldTick = t;
            cachedWorldName = w;
        }
        String key = townId.toString();
        return CACHE.computeIfAbsent(key, k -> buildCellCounts(store, townId, registry));
    }

    @Nonnull
    public static String cellKey(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    @Nonnull
    private static Map<String, Integer> buildCellCounts(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID townId,
        @Nonnull PoiRegistry registry
    ) {
        ConcurrentHashMap<String, Integer> counts = new ConcurrentHashMap<>();
        Query<EntityStore> q = Query.and(VillagerAutonomyState.getComponentType(), TownVillagerBinding.getComponentType());
        store.forEachEntityParallel(q, (index, chunk, commandBuffer) -> {
            TownVillagerBinding b = chunk.getComponent(index, TownVillagerBinding.getComponentType());
            if (b == null || !townId.equals(b.getTownId())) {
                return;
            }
            VillagerAutonomyState a = chunk.getComponent(index, VillagerAutonomyState.getComponentType());
            if (a == null) {
                return;
            }
            int ph = a.getPhase();
            if (ph != VillagerAutonomyState.PHASE_TRAVEL && ph != VillagerAutonomyState.PHASE_USE) {
                return;
            }
            UUID pid = a.getTargetPoiUuid();
            if (pid == null) {
                return;
            }
            PoiEntry target = registry.get(pid);
            if (target == null) {
                return;
            }
            String cell = cellKey(target.getX(), target.getY(), target.getZ());
            counts.merge(cell, 1, Integer::sum);
        });
        return Collections.unmodifiableMap(counts);
    }
}
