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
 * Counts how many town villagers are already traveling to or using each POI, so {@link com.hexvane.aetherhaven.autonomy.PoiScoring}
 * does not assign more NPCs than {@link PoiEntry#getCapacity()} (bed/chair = 1).
 * <p>
 * Cached per world tick + town to avoid O(n^2) scans when many villagers decide in the same frame.
 */
public final class PoiOccupancy {
    private static final ConcurrentHashMap<String, Map<UUID, Integer>> CACHE = new ConcurrentHashMap<>();
    private static volatile long cachedWorldTick = -1L;
    private static volatile String cachedWorldName = "";

    private PoiOccupancy() {}

    @Nonnull
    public static Map<UUID, Integer> countsForTown(
        @Nonnull com.hypixel.hytale.server.core.universe.world.World world,
        @Nonnull UUID townId,
        @Nonnull Store<EntityStore> store
    ) {
        long t = world.getTick();
        String w = world.getName();
        if (t != cachedWorldTick || !w.equals(cachedWorldName)) {
            CACHE.clear();
            cachedWorldTick = t;
            cachedWorldName = w;
        }
        String key = townId.toString();
        return CACHE.computeIfAbsent(key, k -> buildCounts(store, townId));
    }

    @Nonnull
    private static Map<UUID, Integer> buildCounts(@Nonnull Store<EntityStore> store, @Nonnull UUID townId) {
        ConcurrentHashMap<UUID, Integer> counts = new ConcurrentHashMap<>();
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
            counts.merge(pid, 1, Integer::sum);
        });
        return Collections.unmodifiableMap(counts);
    }
}
