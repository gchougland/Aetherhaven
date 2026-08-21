package com.hexvane.aetherhaven.production;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.entity.EntityPresenceUtil;
import com.hexvane.aetherhaven.schedule.VillagerScheduleDefinition;
import com.hexvane.aetherhaven.schedule.VillagerScheduleWorkMinutes;
import com.hexvane.aetherhaven.time.GameTimeEpochs;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.data.VillagerDefinitionCatalog;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.time.TimeModule;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Schedule-aware production accrual for assigned workplace workers (furnace/crop style game-time jumps).
 *
 * <p>{@code /time set} and other discontinuities simulate every skipped scheduled {@code work} minute at full rate,
 * whether the worker entity is loaded or not. Smooth real-time minutes use the offline multiplier only while the
 * worker is unloaded.
 */
public final class ProductionCatchUpService {
    private ProductionCatchUpService() {}

    public static void onSmoothGameMinuteAdvanced(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        long prevEpochMinute,
        long newEpochMinute
    ) {
        if (newEpochMinute <= prevEpochMinute) {
            return;
        }
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        Instant accrualTo = wtr != null ? wtr.getGameTime() : null;
        processWorld(world, store, plugin, prevEpochMinute, newEpochMinute, false, null, accrualTo);
    }

    public static void onGameTimeDiscontinuity(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Instant from,
        @Nonnull Instant to,
        @Nonnull LocalDateTime toDateTime,
        boolean backward
    ) {
        long epochNow = VillagerScheduleWorkMinutes.currentEpochMinute(toDateTime);
        if (backward) {
            resetAllCursors(world, plugin, epochNow, to);
            return;
        }
        if (!to.isAfter(from)) {
            return;
        }
        processWorld(world, store, plugin, -1L, -1L, true, from, to);
    }

    /** Prefer calendar minute span so {@code /time set} dawn→dawn credits a full in-game day. */
    private static long resolveDiscontinuityEpochTo(
        @Nonnull Instant from,
        @Nonnull Instant to,
        long epochFrom
    ) {
        LocalDateTime dtFrom = LocalDateTime.ofInstant(from, WorldTimeResource.ZONE_OFFSET);
        LocalDateTime dtTo = LocalDateTime.ofInstant(to, WorldTimeResource.ZONE_OFFSET);
        long minuteSpan = ChronoUnit.MINUTES.between(dtFrom, dtTo);
        if (minuteSpan > 0L) {
            return epochFrom + minuteSpan;
        }
        return GameTimeEpochs.gameEpochMinute(to);
    }

    private static void resetAllCursors(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        long epochNow,
        @Nonnull Instant gameInstant
    ) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            boolean any = false;
            for (PlotInstance plot : town.getPlotInstances()) {
                if (plot.getState() != PlotInstanceState.COMPLETE) {
                    continue;
                }
                String gid = plugin.getConstructionCatalog().resolveGameplayConstructionId(plot.getConstructionId());
                if (!ProductionCatalog.isProductionWorkplaceConstruction(gid)) {
                    continue;
                }
                PlotProductionState state = town.getOrCreatePlotProduction(plot.getPlotId());
                state.migrateIfNeeded();
                if (state.getAssignedWorkerNpcRoleId() != null) {
                    state.setLastCatchUpEpochMinute(epochNow);
                    state.setLastAccrualGameInstant(gameInstant);
                    any = true;
                }
            }
            if (any) {
                tm.updateTown(town);
            }
        }
    }

    private static void processWorld(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        long fromExclusive,
        long toInclusive,
        boolean bulkCatchUp,
        @Nullable Instant discontinuityFrom,
        @Nullable Instant discontinuityTo
    ) {
        if (world.getWorldConfig().isGameTimePaused()) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        long nowMs = resolveNowMs(store);
        AetherhavenPluginConfig cfg = plugin.getConfig().get();
        ConstructionCatalog ccat = plugin.getConstructionCatalog();
        ProductionCatalog catalog = plugin.getProductionCatalog();
        VillagerDefinitionCatalog vcat = plugin.getVillagerDefinitionCatalog();
        var scheduleRegistry = plugin.getVillagerScheduleRegistry();

        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            boolean townChanged = false;
            for (PlotInstance plot : town.getPlotInstances()) {
                if (plot.getState() != PlotInstanceState.COMPLETE) {
                    continue;
                }
                String gameplayPlotId = ccat.resolveGameplayConstructionId(plot.getConstructionId());
                if (!ProductionCatalog.isProductionWorkplaceConstruction(gameplayPlotId)) {
                    continue;
                }
                UUID plotId = plot.getPlotId();
                PlotProductionState state = town.getOrCreatePlotProduction(plotId);
                state.migrateIfNeeded();

                ProductionAssignedWorker.WorkerRole worker =
                    ProductionAssignedWorker.resolve(town, plotId, state, plot, ccat, store);
                if (worker == null) {
                    continue;
                }

                ProductionCatalog.Entry entry =
                    ProductionEffectiveCatalog.effective(
                        catalog,
                        plugin.getWorkplaceUnlockCatalog(),
                        gameplayPlotId,
                        state
                    );
                if (entry == null || entry.catalogSize() <= 0) {
                    continue;
                }

                VillagerScheduleDefinition schedule =
                    vcat.effectiveSchedule(worker.npcRoleId(), scheduleRegistry);
                if (schedule == null || schedule.getTransitions().isEmpty()) {
                    continue;
                }

                Instant accrualFromInstant;
                Instant accrualToInstant;
                long windowFromExclusive;
                long windowToInclusive;

                if (bulkCatchUp && discontinuityFrom != null && discontinuityTo != null) {
                    accrualFromInstant = resolveBulkAccrualStart(state, discontinuityFrom);
                    accrualToInstant = discontinuityTo;
                    if (!accrualToInstant.isAfter(accrualFromInstant)) {
                        continue;
                    }
                    long epochFrom = GameTimeEpochs.gameEpochMinute(accrualFromInstant);
                    windowFromExclusive = epochFrom;
                    windowToInclusive = resolveDiscontinuityEpochTo(accrualFromInstant, accrualToInstant, epochFrom);
                } else {
                    if (toInclusive <= fromExclusive) {
                        continue;
                    }
                    accrualToInstant = discontinuityTo;
                    windowFromExclusive = fromExclusive;
                    windowToInclusive = toInclusive;
                    accrualFromInstant = null;
                }

                long cursor = state.getLastCatchUpEpochMinute();
                if (cursor < 0L) {
                    cursor = resolveInitialCursor(windowFromExclusive, windowToInclusive);
                    state.setLastCatchUpEpochMinute(cursor);
                    townChanged = true;
                }

                long windowFrom = resolveWindowFrom(windowFromExclusive, windowToInclusive, cursor);
                if (windowToInclusive <= windowFrom) {
                    continue;
                }

                int maxScan = cfg.getProductionOfflineCatchUpMaxMinutes();
                int workMinutes =
                    VillagerScheduleWorkMinutes.countWorkMinutes(
                        windowFrom,
                        windowToInclusive,
                        schedule,
                        maxScan
                    );
                long newCursor =
                    VillagerScheduleWorkMinutes.cursorAfterScan(windowFrom, windowToInclusive, maxScan);

                if (workMinutes > 0) {
                    double workGameSeconds = VillagerScheduleWorkMinutes.workGameSecondsFromMinuteCount(workMinutes);
                    double multiplier = resolveMultiplier(cfg, bulkCatchUp, store, worker.entityUuid());
                    int creditTicks =
                        ProductionCatchUpMath.creditTicksForWorkGameSeconds(world, workGameSeconds, multiplier);
                    if (creditTicks > 0
                        && ProductionAccrualEngine.applyEntityTicks(
                            state,
                            entry,
                            town,
                            gameplayPlotId,
                            ccat,
                            cfg,
                            world,
                            plot.getSignX(),
                            plot.getSignZ(),
                            creditTicks
                        )) {
                        townChanged = true;
                    }
                }
                if (newCursor > cursor) {
                    state.setLastCatchUpEpochMinute(newCursor);
                    townChanged = true;
                }
                if (accrualToInstant != null) {
                    state.setLastAccrualGameInstant(accrualToInstant);
                    townChanged = true;
                }
            }
            if (townChanged) {
                if (bulkCatchUp) {
                    ProductionTownSaveDebouncer.persistNow(tm, town, world, nowMs);
                } else {
                    ProductionTownSaveDebouncer.maybePersist(tm, town, world, nowMs);
                }
            }
        }
    }

    /** Furnace-style: resume from last accrual instant so loaded {@code /time set} still simulates the full skip. */
    @Nonnull
    private static Instant resolveBulkAccrualStart(
        @Nonnull PlotProductionState state,
        @Nonnull Instant discontinuityFrom
    ) {
        Instant last = state.lastAccrualGameInstant();
        if (last != null && last.isAfter(discontinuityFrom)) {
            return last;
        }
        return discontinuityFrom;
    }

    /**
     * Time jumps always simulate at full rate (like furnaces). Smooth minutes while unloaded use the offline
     * multiplier; smooth minutes while the worker is loaded are handled by {@link ProductionTickSystem} within the
     * open game minute.
     */
    private static double resolveMultiplier(
        @Nonnull AetherhavenPluginConfig cfg,
        boolean bulkCatchUp,
        @Nonnull Store<EntityStore> store,
        @Nullable UUID workerEntityUuid
    ) {
        if (bulkCatchUp) {
            return 1.0;
        }
        if (workerEntityUuid != null
            && EntityPresenceUtil.isLoadedLive(EntityPresenceUtil.resolve(store, workerEntityUuid))) {
            return 1.0;
        }
        return cfg.getProductionOfflineMultiplier();
    }

    private static long resolveWindowFrom(long fromExclusive, long toInclusive, long cursor) {
        if (cursor >= toInclusive) {
            return fromExclusive;
        }
        return Math.max(cursor, fromExclusive);
    }

    private static long resolveInitialCursor(long fromExclusive, long toInclusive) {
        return Math.max(0L, Math.min(fromExclusive, toInclusive - 1L));
    }

    private static long resolveNowMs(@Nonnull Store<EntityStore> store) {
        TimeModule mod = TimeModule.get();
        if (mod != null) {
            TimeResource tr = store.getResource(mod.getTimeResourceType());
            if (tr != null) {
                return tr.getNow().toEpochMilli();
            }
        }
        return System.currentTimeMillis();
    }
}
