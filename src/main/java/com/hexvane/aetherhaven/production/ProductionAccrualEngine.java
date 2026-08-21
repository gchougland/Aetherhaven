package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.town.CharterSpecializationModifiers;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.server.core.universe.world.World;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Shared per-slot production tick accrual for live entity ticks and offline catch-up. */
public final class ProductionAccrualEngine {
    private ProductionAccrualEngine() {}

    /**
     * Applies {@code entityTicksToApply} entity ticks to every active output slot (parallel slot timers).
     *
     * @param world workplace world for adventure-zone speed; may be null to skip zone penalties
     * @param plotBlockX workplace plot X (typically sign X)
     * @param plotBlockZ workplace plot Z (typically sign Z)
     * @return true if slot accumulators or stored amounts changed
     */
    public static boolean applyEntityTicks(
        @Nonnull PlotProductionState state,
        @Nonnull ProductionCatalog.Entry entry,
        @Nonnull TownRecord town,
        @Nonnull String gameplayPlotId,
        @Nonnull ConstructionCatalog ccat,
        @Nonnull AetherhavenPluginConfig cfg,
        @Nullable World world,
        int plotBlockX,
        int plotBlockZ,
        int entityTicksToApply
    ) {
        if (entityTicksToApply <= 0 || entry.catalogSize() <= 0) {
            return false;
        }
        state.migrateIfNeeded();
        double timeMul = cfg.getProductionTimeMultiplier();
        double speedMul = WorkplaceProductionUpgrades.speedMultiplier(state);
        double mismatchMul = cfg.getProductionZoneMismatchTimeMultiplier();
        Integer adventureZone =
            ProductionResourceZoneAffinity.resolveAdventureZoneOrSkip(world, plotBlockX, plotBlockZ);
        int slotCount = WorkplaceProductionUpgrades.slotCount(state);

        boolean amountsChanged = false;
        boolean accumChanged = false;
        for (int tick = 0; tick < entityTicksToApply; tick++) {
            boolean progressed = false;
            for (int slot = 0; slot < slotCount; slot++) {
                int cursor = state.getSlotCursor(slot);
                String selected = entry.itemAtCursor(cursor);
                if (selected == null || selected.isBlank()) {
                    if (state.getSlotTickAccum(slot) != 0) {
                        state.setSlotTickAccum(slot, 0);
                        accumChanged = true;
                    }
                    continue;
                }
                double zoneMul =
                    ProductionResourceZoneAffinity.timeMultiplierForResolvedZone(
                        adventureZone,
                        selected,
                        mismatchMul
                    );
                int ticksNeeded =
                    ProductionTimeScaling.effectiveTicks(entry.ticksAtCursor(cursor), timeMul / speedMul * zoneMul);
                int acc = state.getSlotTickAccum(slot) + 1;
                if (acc < ticksNeeded) {
                    state.setSlotTickAccum(slot, acc);
                    accumChanged = true;
                    progressed = true;
                    continue;
                }
                state.setSlotTickAccum(slot, 0);
                accumChanged = true;
                long maxForItem = WorkplaceProductionUpgrades.effectiveMaxStorage(state, entry, selected);
                long amount = CharterSpecializationModifiers.productionAmountPerCycle(town, ccat, gameplayPlotId);
                state.addAmount(selected, amount, maxForItem);
                amountsChanged = true;
                progressed = true;
            }
            if (!progressed) {
                break;
            }
        }
        return amountsChanged || accumChanged;
    }
}
