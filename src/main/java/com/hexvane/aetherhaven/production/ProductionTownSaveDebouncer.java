package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownSaveCoordinator;
import com.hypixel.hytale.server.core.universe.world.World;
import javax.annotation.Nonnull;

/** Debounced {@link TownManager} persistence for production accrual paths. */
final class ProductionTownSaveDebouncer {
    private ProductionTownSaveDebouncer() {}

    static void maybePersist(
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull World world,
        long nowMs
    ) {
        tm.markDirty(town);
        TownSaveCoordinator.requestSave(tm);
    }

    static void persistNow(
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull World world,
        long nowMs
    ) {
        tm.markDirty(town);
        TownSaveCoordinator.requestImmediateSave(tm);
    }
}
