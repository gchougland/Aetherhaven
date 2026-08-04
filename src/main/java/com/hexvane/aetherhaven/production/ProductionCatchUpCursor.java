package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.town.TownRecord;
import java.time.Instant;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Initializes offline production catch-up cursors on workplace plots. */
public final class ProductionCatchUpCursor {
    private ProductionCatchUpCursor() {}

    public static void initOnWorkerAssign(
        @Nonnull TownRecord town,
        @Nonnull UUID workplacePlotId,
        long epochMinute,
        @Nonnull String npcRoleId,
        @Nonnull Instant gameInstant
    ) {
        PlotProductionState state = town.getOrCreatePlotProduction(workplacePlotId);
        state.migrateIfNeeded();
        state.setAssignedWorkerNpcRoleId(npcRoleId);
        state.setLastCatchUpEpochMinute(epochMinute);
        state.setLastAccrualGameInstant(gameInstant);
    }

    public static void clearOnWorkerUnassign(@Nonnull TownRecord town, @Nonnull UUID workplacePlotId) {
        PlotProductionState state = town.getOrCreatePlotProduction(workplacePlotId);
        state.migrateIfNeeded();
        state.clearAssignedWorker();
    }

    public static boolean isProductionWorkerKind(@Nullable String kind) {
        if (kind == null || kind.isBlank()) {
            return false;
        }
        return switch (kind) {
            case com.hexvane.aetherhaven.villager.TownVillagerBinding.KIND_FARMER,
                com.hexvane.aetherhaven.villager.TownVillagerBinding.KIND_MINER,
                com.hexvane.aetherhaven.villager.TownVillagerBinding.KIND_LOGGER,
                com.hexvane.aetherhaven.villager.TownVillagerBinding.KIND_RANCHER -> true;
            default -> false;
        };
    }
}
