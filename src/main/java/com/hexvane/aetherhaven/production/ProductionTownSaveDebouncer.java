package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Debounced {@link TownManager#updateTown} for production accrual paths. */
final class ProductionTownSaveDebouncer {
    private static final ConcurrentHashMap<String, Long> LAST_TOWN_SAVE_MS = new ConcurrentHashMap<>();
    private static final long SAVE_DEBOUNCE_MS = 4000L;

    private ProductionTownSaveDebouncer() {}

    static void maybePersist(
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull World world,
        long nowMs
    ) {
        String key = world.getName() + "|" + town.getTownId();
        Long last = LAST_TOWN_SAVE_MS.get(key);
        if (last != null && nowMs - last < SAVE_DEBOUNCE_MS) {
            return;
        }
        persistNow(tm, town, world, nowMs);
    }

    static void persistNow(
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull World world,
        long nowMs
    ) {
        String key = world.getName() + "|" + town.getTownId();
        LAST_TOWN_SAVE_MS.put(key, nowMs);
        tm.updateTown(town);
    }
}
