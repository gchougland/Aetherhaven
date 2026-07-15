package com.hexvane.aetherhaven.poi;

import com.hexvane.aetherhaven.autonomy.VillagerAutonomyState;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Counts how many town villagers are already traveling to or using each <strong>world cell</strong> targeted by a POI,
 * so {@link com.hexvane.aetherhaven.autonomy.PoiScoring} does not overfill a bed (capacity 1) even when two registry
 * entries accidentally share the same block coordinates.
 * <p>
 * Cached per world tick + town. The returned map is <strong>mutable</strong> so same-tick pickers can soft-claim cells
 * after {@code pickBest} before TRAVEL is visible in the store.
 */
public final class PoiOccupancy {
    private static final ConcurrentHashMap<String, Map<String, Integer>> CACHE = new ConcurrentHashMap<>();
    private static volatile long cachedWorldTick = -1L;
    private static volatile String cachedWorldName = "";

    private PoiOccupancy() {}

    /**
     * @return mutable map key {@code "x,y,z"} of the target POI's anchor cell → number of town NPCs traveling to or using
     *         a POI at that cell (plus same-tick soft claims)
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
    public static String cellKey(@Nonnull PoiEntry poi) {
        return cellKey(poi.getX(), poi.getY(), poi.getZ());
    }

    /**
     * Soft-claim a capacity slot for {@code poi} if under capacity. Returns false when full (does not increment).
     * Safe for concurrent same-tick pickers.
     */
    public static boolean tryClaim(@Nonnull Map<String, Integer> cellOccupancy, @Nonnull PoiEntry poi) {
        int cap = Math.max(1, poi.getCapacity());
        String cell = cellKey(poi);
        if (!(cellOccupancy instanceof ConcurrentHashMap)) {
            int used = cellOccupancy.getOrDefault(cell, 0);
            if (used >= cap) {
                return false;
            }
            cellOccupancy.put(cell, used + 1);
            return true;
        }
        @SuppressWarnings("unchecked")
        ConcurrentHashMap<String, Integer> m = (ConcurrentHashMap<String, Integer>) cellOccupancy;
        while (true) {
            Integer cur = m.get(cell);
            int used = cur == null ? 0 : cur;
            if (used >= cap) {
                return false;
            }
            if (cur == null) {
                if (m.putIfAbsent(cell, 1) == null) {
                    return true;
                }
                continue;
            }
            if (m.replace(cell, used, used + 1)) {
                return true;
            }
        }
    }

    /**
     * Live store count of town villagers (TRAVEL/USE) targeting {@code poi}'s cell, excluding {@code excludeEntityUuid}.
     */
    public static int liveOccupancyExcluding(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID townId,
        @Nonnull PoiRegistry registry,
        @Nonnull PoiEntry poi,
        @Nullable UUID excludeEntityUuid
    ) {
        String cell = cellKey(poi);
        int[] count = {0};
        Query<EntityStore> q =
            Query.and(
                VillagerAutonomyState.getComponentType(),
                TownVillagerBinding.getComponentType(),
                UUIDComponent.getComponentType()
            );
        store.forEachEntityParallel(q, (index, chunk, commandBuffer) -> {
            TownVillagerBinding b = chunk.getComponent(index, TownVillagerBinding.getComponentType());
            if (b == null || !townId.equals(b.getTownId())) {
                return;
            }
            UUIDComponent uc = chunk.getComponent(index, UUIDComponent.getComponentType());
            if (uc != null && excludeEntityUuid != null && excludeEntityUuid.equals(uc.getUuid())) {
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
            if (cell.equals(cellKey(target))) {
                count[0]++;
            }
        });
        return count[0];
    }

    /**
     * True when this villager may begin USE at {@code poi}: other NPCs targeting the cell stay under capacity.
     */
    public static boolean canBeginUse(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID townId,
        @Nonnull PoiRegistry registry,
        @Nonnull PoiEntry poi,
        @Nullable UUID selfEntityUuid
    ) {
        int others = liveOccupancyExcluding(store, townId, registry, poi, selfEntityUuid);
        return others < Math.max(1, poi.getCapacity());
    }

    @Nonnull
    private static ConcurrentHashMap<String, Integer> buildCellCounts(
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
        return counts;
    }
}
