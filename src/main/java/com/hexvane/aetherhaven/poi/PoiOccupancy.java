package com.hexvane.aetherhaven.poi;

import com.hexvane.aetherhaven.autonomy.VillagerAutonomyState;
import com.hexvane.aetherhaven.tourist.TouristAutonomyState;
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
 * Counts how many town NPCs (villagers and tourists) are already traveling to or using each <strong>world cell</strong>
 * targeted by a POI, so capacity-1 spots are not overfilled.
 * <p>
 * Cached per world tick + town. The returned map is <strong>mutable</strong> so same-tick pickers can soft-claim cells
 * after a pick before TRAVEL is visible in the store.
 */
public final class PoiOccupancy {
    private static final ConcurrentHashMap<String, Map<String, Integer>> CACHE = new ConcurrentHashMap<>();
    private static volatile long cachedWorldTick = -1L;
    private static volatile String cachedWorldName = "";

    private PoiOccupancy() {}

    /**
     * @return mutable map key {@code "x,y,z"} of the target stand cell → number of town NPCs traveling to or using
     *         that cell (plus same-tick soft claims)
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

    /** Stand cell tourists/villagers walk to (interaction target when set, else POI block). */
    @Nonnull
    public static String standCellKey(@Nonnull PoiEntry poi) {
        if (poi.hasInteractionTarget()) {
            Double tx = poi.getInteractionTargetX();
            Double ty = poi.getInteractionTargetY();
            Double tz = poi.getInteractionTargetZ();
            if (tx != null && ty != null && tz != null) {
                return cellKey((int) Math.floor(tx), (int) Math.floor(ty), (int) Math.floor(tz));
            }
        }
        return cellKey(poi);
    }

    @Nonnull
    public static String standCellKey(double x, double y, double z) {
        return cellKey((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    /**
     * Soft-claim a capacity slot for {@code poi} if under capacity. Returns false when full (does not increment).
     * Safe for concurrent same-tick pickers. Uses the POI block cell (matches villager {@code PoiScoring}).
     */
    public static boolean tryClaim(@Nonnull Map<String, Integer> cellOccupancy, @Nonnull PoiEntry poi) {
        return tryClaimCell(cellOccupancy, cellKey(poi), Math.max(1, poi.getCapacity()));
    }

    /** Soft-claim the stand cell tourists actually walk to (interaction target when set). */
    public static boolean tryClaimStand(@Nonnull Map<String, Integer> cellOccupancy, @Nonnull PoiEntry poi) {
        return tryClaimCell(cellOccupancy, standCellKey(poi), Math.max(1, poi.getCapacity()));
    }

    public static boolean tryClaimCell(
        @Nonnull Map<String, Integer> cellOccupancy,
        @Nonnull String cell,
        int capacity
    ) {
        int cap = Math.max(1, capacity);
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

    public static boolean isCellAvailable(
        @Nonnull Map<String, Integer> cellOccupancy,
        @Nonnull String cell,
        int capacity
    ) {
        return cellOccupancy.getOrDefault(cell, 0) < Math.max(1, capacity);
    }

    /** True when another villager may soft-claim or begin travel to {@code poi}'s stand cell. */
    public static boolean hasAvailableCapacity(
        @Nonnull Map<String, Integer> cellOccupancy,
        @Nonnull PoiEntry poi
    ) {
        return isCellAvailable(cellOccupancy, standCellKey(poi), poi.getCapacity());
    }

    /**
     * Live store count of town villagers/tourists targeting {@code poi}'s stand cell, excluding
     * {@code excludeEntityUuid}.
     */
    public static int liveOccupancyExcluding(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID townId,
        @Nonnull PoiRegistry registry,
        @Nonnull PoiEntry poi,
        @Nullable UUID excludeEntityUuid
    ) {
        // Villagers claim the POI block cell; tourists often leash to the interaction-target stand — count both.
        String poiCell = cellKey(poi);
        String standCell = standCellKey(poi);
        int[] count = {0};
        Query<EntityStore> villagerQ =
            Query.and(
                VillagerAutonomyState.getComponentType(),
                TownVillagerBinding.getComponentType(),
                UUIDComponent.getComponentType()
            );
        store.forEachEntityParallel(villagerQ, (index, chunk, commandBuffer) -> {
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
            if (occupiesCell(a.getTargetPoiUuid(), a.getTargetX(), a.getTargetY(), a.getTargetZ(), registry, poiCell, standCell)) {
                count[0]++;
            }
        });
        Query<EntityStore> touristQ =
            Query.and(
                TouristAutonomyState.getComponentType(),
                TownVillagerBinding.getComponentType(),
                UUIDComponent.getComponentType()
            );
        store.forEachEntityParallel(touristQ, (index, chunk, commandBuffer) -> {
            TownVillagerBinding b = chunk.getComponent(index, TownVillagerBinding.getComponentType());
            if (b == null || !townId.equals(b.getTownId())) {
                return;
            }
            UUIDComponent uc = chunk.getComponent(index, UUIDComponent.getComponentType());
            if (uc != null && excludeEntityUuid != null && excludeEntityUuid.equals(uc.getUuid())) {
                return;
            }
            TouristAutonomyState a = chunk.getComponent(index, TouristAutonomyState.getComponentType());
            if (a == null || !isTouristOccupyingPhase(a.getPhase())) {
                return;
            }
            if (occupiesCell(a.getTargetPoiUuid(), a.getTargetX(), a.getTargetY(), a.getTargetZ(), registry, poiCell, standCell)) {
                count[0]++;
            }
        });
        return count[0];
    }

    /**
     * True when this NPC may begin USE at {@code poi}: other NPCs targeting the stand cell stay under capacity.
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

    private static boolean isTouristOccupyingPhase(int phase) {
        return phase == TouristAutonomyState.PHASE_TRAVEL
            || phase == TouristAutonomyState.PHASE_POI
            || phase == TouristAutonomyState.PHASE_VISIT
            || phase == TouristAutonomyState.PHASE_RETURNING;
    }

    private static boolean occupiesCell(
        @Nullable UUID targetId,
        double targetX,
        double targetY,
        double targetZ,
        @Nonnull PoiRegistry registry,
        @Nonnull String poiCell,
        @Nonnull String standCell
    ) {
        if (targetId != null) {
            PoiEntry target = registry.get(targetId);
            if (target != null) {
                String key = standCellKey(target);
                return poiCell.equals(cellKey(target)) || standCell.equals(key) || poiCell.equals(key);
            }
        }
        if (!Double.isFinite(targetX) || !Double.isFinite(targetY) || !Double.isFinite(targetZ)) {
            return false;
        }
        String key = standCellKey(targetX, targetY, targetZ);
        return poiCell.equals(key) || standCell.equals(key);
    }

    @Nonnull
    private static ConcurrentHashMap<String, Integer> buildCellCounts(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID townId,
        @Nonnull PoiRegistry registry
    ) {
        ConcurrentHashMap<String, Integer> counts = new ConcurrentHashMap<>();
        Query<EntityStore> villagerQ =
            Query.and(VillagerAutonomyState.getComponentType(), TownVillagerBinding.getComponentType());
        store.forEachEntityParallel(villagerQ, (index, chunk, commandBuffer) -> {
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
            mergeOccupancy(counts, a.getTargetPoiUuid(), a.getTargetX(), a.getTargetY(), a.getTargetZ(), registry);
        });
        Query<EntityStore> touristQ =
            Query.and(TouristAutonomyState.getComponentType(), TownVillagerBinding.getComponentType());
        store.forEachEntityParallel(touristQ, (index, chunk, commandBuffer) -> {
            TownVillagerBinding b = chunk.getComponent(index, TownVillagerBinding.getComponentType());
            if (b == null || !townId.equals(b.getTownId())) {
                return;
            }
            TouristAutonomyState a = chunk.getComponent(index, TouristAutonomyState.getComponentType());
            if (a == null || !isTouristOccupyingPhase(a.getPhase())) {
                return;
            }
            mergeOccupancy(counts, a.getTargetPoiUuid(), a.getTargetX(), a.getTargetY(), a.getTargetZ(), registry);
        });
        return counts;
    }

    private static void mergeOccupancy(
        @Nonnull ConcurrentHashMap<String, Integer> counts,
        @Nullable UUID targetId,
        double targetX,
        double targetY,
        double targetZ,
        @Nonnull PoiRegistry registry
    ) {
        if (targetId != null) {
            PoiEntry target = registry.get(targetId);
            if (target != null) {
                counts.merge(standCellKey(target), 1, Integer::sum);
                return;
            }
        }
        if (Double.isFinite(targetX) && Double.isFinite(targetY) && Double.isFinite(targetZ)) {
            counts.merge(standCellKey(targetX, targetY, targetZ), 1, Integer::sum);
        }
    }
}
