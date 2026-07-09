package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.construction.ConstructionCompleter;
import com.hexvane.aetherhaven.plot.PlotBlockStamper;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import com.hexvane.aetherhaven.construction.PlotMaterialDepositService;
import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.inventory.BenchAdjacentChestUtil;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.core.prefab.event.PrefabPasteEvent;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.LocalCachedChunkAccessor;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PrefabUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Plot assembly: passive ticks and building staff paint cells on a <strong>growth frontier</strong> — any unplaced block
 * face-adjacent (6-neighbor in prefab space) to an already placed cell, starting from the lowest-Y layer. Passive places
 * one block every {@link #computeSlotWallMs} of {@link com.hypixel.hytale.server.core.modules.time.TimeResource} time
 * (dilated), independent of how many blocks the staff placed; staff only speeds completion by reducing remaining work.
 */
public final class PlotAssemblyService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int BREAK_SIGN_SETTINGS = 10;
    /** Passive assembly: at most one prefab cell per plot per {@link #tickPassive} invocation. */
    private static final int PASSIVE_BLOCKS_PER_WORLD_TICK_PER_JOB = 1;
    /** Passive clearing: base cells per tick; builder boost multiplies burst like the placing phase. */
    private static final int PASSIVE_CLEARING_BLOCKS_PER_WORLD_TICK_PER_JOB = 1;
    private static final long REHYDRATE_SCAN_INTERVAL_MS = 3_000L;
    private static final ConcurrentHashMap<String, Long> LAST_REHYDRATE_SCAN_MS = new ConcurrentHashMap<>();

    /** Clearing advance: {@code progressed} advances the passive burst; {@code brokeBlock} is true only when a solid was removed. */
    public record ClearingAdvanceOutcome(boolean progressed, boolean brokeBlock) {
        @Nonnull
        public static ClearingAdvanceOutcome noProgress() {
            return new ClearingAdvanceOutcome(false, false);
        }

        @Nonnull
        public static ClearingAdvanceOutcome skipped() {
            return new ClearingAdvanceOutcome(true, false);
        }

        @Nonnull
        public static ClearingAdvanceOutcome blockBroken() {
            return new ClearingAdvanceOutcome(true, true);
        }
    }

    /** Placement advance: {@code progressed} advances the passive burst; {@code wroteBlock} is true when world state changed. */
    public record PlacementAdvanceOutcome(boolean progressed, boolean wroteBlock) {
        @Nonnull
        public static PlacementAdvanceOutcome noProgress() {
            return new PlacementAdvanceOutcome(false, false);
        }

        @Nonnull
        public static PlacementAdvanceOutcome skipped() {
            return new PlacementAdvanceOutcome(true, false);
        }

        @Nonnull
        public static PlacementAdvanceOutcome committed() {
            return new PlacementAdvanceOutcome(true, true);
        }
    }

    private PlotAssemblyService() {}

    private static void markAssemblyTownDirty(@Nonnull Map<UUID, TownRecord> dirty, @Nonnull TownRecord town) {
        dirty.put(town.getTownId(), town);
    }

    private static void flushDirtyAssemblyTowns(@Nonnull TownManager tm, @Nonnull Map<UUID, TownRecord> dirty) {
        for (TownRecord town : dirty.values()) {
            tm.updateTown(town);
        }
        dirty.clear();
    }

    /**
     * One full in-game day/night cycle length in wall-clock ms from world settings, or config override when {@code > 0}.
     */
    public static long msPerGameDay(@Nonnull World world, @Nonnull AetherhavenPluginConfig cfg) {
        long o = cfg.getAssemblyGameDayLengthMsOverride();
        if (o > 0L) {
            return o;
        }
        int d = world.getDaytimeDurationSeconds();
        int n = world.getNighttimeDurationSeconds();
        long sec = (long) d + (long) n;
        return Math.max(1L, sec) * 1000L;
    }

    /**
     * Schedules assembly job restoration on the world thread (and retries) after load. In-memory jobs are cleared on
     * unload; {@link PrefabPasteEvent} can also fail if rehydrate runs before the world is ready.
     */
    public static void scheduleRehydrateAfterWorldLoad(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        world.execute(() -> rehydrateOnWorldThread(world, plugin));
        plugin.scheduleOnWorld(world, () -> rehydrateOnWorldThread(world, plugin), 2_000L);
        plugin.scheduleOnWorld(world, () -> rehydrateOnWorldThread(world, plugin), 10_000L);
    }

    /** Restores in-memory assembly jobs for every {@link PlotInstanceState#ASSEMBLING} plot missing from {@link AssemblyWorldRegistry}. */
    public static void rehydrateOnWorldThread(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        Store<EntityStore> entityStore = world.getEntityStore().getStore();
        for (TownRecord town : tm.allTowns()) {
            for (PlotInstance plot : town.getPlotInstances()) {
                rehydratePlotIfNeeded(world, plugin, town, plot, entityStore);
            }
        }
    }

    /**
     * Returns the active job for {@code plot}, registering one from persisted assembly state when missing (e.g. after
     * re-entering a world).
     */
    @Nullable
    public static PlotAssemblyJob ensureAssemblyJob(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull Store<EntityStore> entityStore
    ) {
        PlotAssemblyJob existing = AssemblyWorldRegistry.get(world, plot.getPlotId());
        if (existing != null) {
            return existing;
        }
        return rehydratePlotIfNeeded(world, plugin, town, plot, entityStore);
    }

    @Nullable
    private static PlotAssemblyJob rehydratePlotIfNeeded(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull Store<EntityStore> entityStore
    ) {
        if (plot.getState() != PlotInstanceState.ASSEMBLING) {
            return null;
        }
        UUID plotId = plot.getPlotId();
        PlotAssemblyJob existing = AssemblyWorldRegistry.get(world, plotId);
        if (existing != null) {
            return existing;
        }
        ConstructionDefinition def = plugin.getConstructionCatalog().get(plot.getConstructionId());
        if (def == null) {
            LOGGER.atWarning().log("Rehydrate assembly: unknown construction %s plot %s", plot.getConstructionId(), plotId);
            return null;
        }
        Path prefabPath = PrefabResolveUtil.resolvePrefabPath(def.getPrefabPath());
        if (prefabPath == null) {
            LOGGER.atWarning().log("Rehydrate assembly: missing prefab %s", def.getPrefabPath());
            return null;
        }
        IPrefabBuffer buffer = PrefabBufferUtil.getCached(prefabPath);
        Vector3i anchor = plot.resolvePrefabAnchorWorld(def);
        Rotation yaw = plot.resolvePrefabYaw();
        UUID owner = plot.getAssemblyOwnerUuid() != null ? plot.getAssemblyOwnerUuid() : town.getOwnerUuid();
        if (!tryRegisterJob(world, plugin, town, plot, anchor, yaw, def, buffer, owner, entityStore)) {
            LOGGER.atWarning().log("Rehydrate assembly: could not register job for plot %s (prefab paste start cancelled?)", plotId);
            return null;
        }
        return AssemblyWorldRegistry.get(world, plotId);
    }

    /**
     * Starts assembly on the world thread: paste begin, optional clearing phase, break plot sign, persist ASSEMBLING,
     * register job. Spends treasury gold and clears material deposits after paste is accepted.
     */
    @Nonnull
    public static PlotAssemblyBuildStartResult startFromBuildClick(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull Vector3i physicalSignWorld,
        @Nonnull UUID assemblyOwnerUuid,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw,
        @Nonnull ConstructionDefinition def,
        @Nonnull IPrefabBuffer buffer,
        long goldCost,
        boolean creativeBypassPayment
    ) {
        if (world.getEntityStore() == null) {
            return PlotAssemblyBuildStartResult.BUILDER_UNAVAILABLE;
        }
        Store<EntityStore> entityStore = world.getEntityStore().getStore();
        UUID plotId = plot.getPlotId();
        if (plot.getState() == PlotInstanceState.ASSEMBLING) {
            LOGGER.atWarning().log("Build click while plot already assembling %s", plotId);
            return PlotAssemblyBuildStartResult.ALREADY_ASSEMBLING;
        }
        if (AssemblyWorldRegistry.hasJob(world, plotId)) {
            LOGGER.atWarning().log("Assembly already active for plot %s", plotId);
            return PlotAssemblyBuildStartResult.ASSEMBLY_ALREADY_ACTIVE;
        }
        int prefabId = PrefabUtil.getNextPrefabId();
        PrefabPasteEvent start = new PrefabPasteEvent(prefabId, true);
        entityStore.invoke(start);
        if (start.isCancelled()) {
            LOGGER.atWarning().log("Prefab paste start cancelled for plot %s", plotId);
            return PlotAssemblyBuildStartResult.PASTE_CANCELLED;
        }
        if (!creativeBypassPayment) {
            Ref<EntityStore> builderRef = entityStore.getExternalData().getRefFromUUID(assemblyOwnerUuid);
            if (builderRef == null || !builderRef.isValid()) {
                return PlotAssemblyBuildStartResult.BUILDER_UNAVAILABLE;
            }
            Player player = entityStore.getComponent(builderRef, Player.getComponentType());
            if (player == null) {
                return PlotAssemblyBuildStartResult.BUILDER_UNAVAILABLE;
            }
            CombinedItemContainer inv =
                BenchAdjacentChestUtil.combinedPlayerAndAdjacentChestsForBlock(
                    world,
                    entityStore,
                    builderRef,
                    physicalSignWorld.x,
                    physicalSignWorld.y,
                    physicalSignWorld.z
                );
            if (inv == null) {
                return PlotAssemblyBuildStartResult.PAYMENT_FAILED;
            }
            if (goldCost > 0L) {
                boolean allowTreasury = town.playerCanSpendTreasuryGold(assemblyOwnerUuid);
                if (!GoldCoinPayment.canAfford(town, inv, goldCost, allowTreasury)) {
                    return PlotAssemblyBuildStartResult.PAYMENT_FAILED;
                }
                if (GoldCoinPayment.trySpendReturningBreakdown(town, inv, goldCost, allowTreasury) == null) {
                    return PlotAssemblyBuildStartResult.PAYMENT_FAILED;
                }
            }
            PlotMaterialDepositService.clearDeposits(plot);
        }
        TownManager tmPay = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        tmPay.updateTown(town);

        ConstructionPasteOps.PrefabSequence seq = ConstructionPasteOps.buildSequence(buffer, yaw);
        List<PendingBlock> footprintCells = seq.pendingBlocks();
        List<PendingBlock> nonAirCells = ConstructionPasteOps.withoutPureAirCells(footprintCells);
        ConstructionPasteOps.AssemblyDeferredPartition split =
            ConstructionPasteOps.partitionAssemblyDeferredBlocks(
                nonAirCells,
                BlockType.getAssetMap(),
                def.getAssemblyDeferredBlockIds()
            );
        List<PendingBlock> placementOrder = split.main();
        List<PendingBlock> assemblyDeferredBlocks = split.deferred();

        world.breakBlock(physicalSignWorld.x, physicalSignWorld.y, physicalSignWorld.z, BREAK_SIGN_SETTINGS);

        long wallNow = System.currentTimeMillis();
        plot.setPrefabWorldPlacement(anchor.x, anchor.y, anchor.z, yaw);
        plot.setState(PlotInstanceState.ASSEMBLING);
        plot.setLastStateChangeEpochMs(wallNow);
        plot.resetAssemblyPlacementProgress();
        plot.resetAssemblyPassiveTimers();
        int sectionAxis = def.getAssemblyPrefabSectionsPerAxis();
        if (sectionAxis > 1) {
            plot.setAssemblySectionDivisions(sectionAxis);
            plot.setAssemblyActiveSectionIndex(AssemblySectionMapper.firstOccupiedFlatSection(placementOrder, sectionAxis));
        } else {
            plot.setAssemblySectionDivisions(null);
            plot.setAssemblyActiveSectionIndex(null);
        }
        plot.setAssemblyPrefabId(prefabId);
        plot.setAssemblyOwnerUuid(assemblyOwnerUuid);
        long slot = computeSlotWallMs(world, plugin, def, placementOrder.size());
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        tm.updateTown(town);

        AssemblyFootprintIndex footprintIndex = AssemblyFootprintIndex.build(footprintCells);
        PlotAssemblyJob job =
            new PlotAssemblyJob(
                plotId,
                assemblyOwnerUuid,
                anchor,
                yaw,
                footprintCells,
                footprintIndex,
                placementOrder,
                assemblyDeferredBlocks,
                seq.prefabEntitiesInOrder(),
                buffer,
                seq.prefabRotation(),
                prefabId,
                slot,
                def.getId()
            );
        AssemblySectionMapper clearingMapper =
            sectionAxis > 1 ? AssemblySectionMapper.create(placementOrder, sectionAxis) : null;
        if (clearingMapper != null) {
            AssemblyObstructionUtil.clearSoftSkippedBlocksInFootprint(
                world, job, clearingMapper, plot.getAssemblyActiveSectionIndex()
            );
        } else {
            AssemblyObstructionUtil.clearSoftSkippedBlocksInFootprint(world, job);
        }
        if (AssemblyObstructionUtil.hasObstructionsInLoadedChunks(world, job, clearingMapper)) {
            registerClearingJob(world, tm, town, plot, job);
        } else {
            beginPlacingPhase(world, plugin, entityStore, tm, town, plot, job);
        }
        notifyBuildStarted(world, entityStore, assemblyOwnerUuid, def);
        return PlotAssemblyBuildStartResult.OK;
    }

    private static void notifyBuildStarted(
        @Nonnull World world,
        @Nonnull Store<EntityStore> entityStore,
        @Nonnull UUID builderUuid,
        @Nonnull ConstructionDefinition def
    ) {
        Ref<EntityStore> ref = entityStore.getExternalData().getRefFromUUID(builderUuid);
        if (ref == null || !ref.isValid()) {
            return;
        }
        PlayerRef pr = entityStore.getComponent(ref, PlayerRef.getComponentType());
        if (pr == null) {
            return;
        }
        NotificationUtil.sendNotification(
            pr.getPacketHandler(),
            Message.translation("aetherhaven_misc.aetherhaven.assembly.buildStarted.title"),
            Message.translation("aetherhaven_misc.aetherhaven.assembly.buildStarted.body")
                .param("name", def.getDisplayName()),
            NotificationStyle.Success
        );
    }

    /**
     * Restores missing in-memory jobs for every assembling plot in the world (preview/staff safety).
     */
    public static void ensureAssemblyJobsForAssemblingPlots(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> entityStore
    ) {
        if (!anyAssemblingPlotMissingJob(world, plugin)) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            for (PlotInstance plot : town.getPlotInstances()) {
                if (plot.getState() == PlotInstanceState.ASSEMBLING
                    && !AssemblyWorldRegistry.hasJob(world, plot.getPlotId())) {
                    ensureAssemblyJob(world, plugin, town, plot, entityStore);
                }
            }
        }
    }

    public static boolean anyAssemblingPlotMissingJob(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            for (PlotInstance plot : town.getPlotInstances()) {
                if (plot.getState() == PlotInstanceState.ASSEMBLING
                    && !AssemblyWorldRegistry.hasJob(world, plot.getPlotId())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static void schedulePassiveFromHub(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        if (!plugin.getConfig().get().isPassivePlotAssemblyEnabled()) {
            return;
        }
        if (AssemblyWorldRegistry.jobs(world).isEmpty()) {
            return;
        }
        world.execute(
            () -> {
                if (world.getEntityStore() == null) {
                    return;
                }
                Store<EntityStore> store = world.getEntityStore().getStore();
                tickPassive(world, plugin, store, store);
            }
        );
    }

    /**
     * When the builder villager begins assisting, snap the passive timer so a higher boost applies immediately.
     */
    public static void snapPassiveDueForBoost(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID plotId,
        int newBoost
    ) {
        if (newBoost <= 1) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.findTownOwningPlot(plotId);
        if (town == null) {
            return;
        }
        PlotInstance plot = town.findPlotById(plotId);
        if (plot == null || plot.getState() != PlotInstanceState.ASSEMBLING) {
            return;
        }
        if (world.getEntityStore() == null) {
            return;
        }
        Store<EntityStore> store = world.getEntityStore().getStore();
        Instant simNow = store.getResource(TimeResource.getResourceType()).getNow();
        long simNowMs = simNow.toEpochMilli();
        long due = plot.getAssemblyNextPassiveDueSimMs();
        if (due > simNowMs) {
            plot.setAssemblyNextPassiveDueSimMs(simNowMs);
            tm.updateTown(town);
        }
    }

    private static void registerClearingJob(
        @Nonnull World world,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull PlotAssemblyJob job
    ) {
        AssemblySectionMapper mapper = clearingSectionMapper(job, plot);
        PlotAssemblyClearingRuntime clearingRt;
        if (mapper != null) {
            clearingRt = PlotAssemblyClearingRuntime.empty();
            int active = plot.getAssemblyActiveSectionIndex();
            clearingRt.scanSectionFootprintMerge(world, job, mapper, active);
        } else {
            clearingRt = PlotAssemblyClearingRuntime.scanLoadedFootprint(world, job);
        }
        if (mapper != null && !clearingRt.isEmpty()) {
            ArrayList<Vector3i> obstructed = new ArrayList<>();
            clearingRt.appendObstructedCells(obstructed);
            int active = plot.getAssemblyActiveSectionIndex();
            if (!clearingSectionHasAnyObstructedCell(mapper, job.anchor(), obstructed, active)) {
                plot.setAssemblyActiveSectionIndex(
                    AssemblySectionMapper.firstOccupiedFlatSectionForWorldCells(mapper, job.anchor(), obstructed)
                );
                tm.updateTown(town);
            }
        }
        PlotAssemblyClearingFrontierRuntime clearingFrontierRt = null;
        if (!clearingRt.isEmpty()) {
            LocalCachedChunkAccessor acc = clearingRt.getOrCreateChunkAccessor(world, job);
            int activeSection = plot.getAssemblyActiveSectionIndex();
            clearingFrontierRt =
                PlotAssemblyClearingFrontierRuntime.rebuild(
                    world, job, clearingRt, acc, mapper, activeSection
                );
        }
        AssemblyWorldRegistry.put(
            world, plot.getPlotId(), job, PlotAssemblyPhase.CLEARING, null, clearingRt, clearingFrontierRt
        );
    }

    private static boolean tryRegisterJob(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw,
        @Nonnull ConstructionDefinition def,
        @Nonnull IPrefabBuffer buffer,
        @Nonnull UUID ownerUuid,
        @Nonnull Store<EntityStore> entityStore
    ) {
        PrefabPasteEvent start = new PrefabPasteEvent(PrefabUtil.getNextPrefabId(), true);
        entityStore.invoke(start);
        if (start.isCancelled()) {
            return false;
        }
        int prefabId = start.getPrefabId();
        ConstructionPasteOps.PrefabSequence seq = ConstructionPasteOps.buildSequence(buffer, yaw);
        List<PendingBlock> footprintCells = seq.pendingBlocks();
        List<PendingBlock> nonAirCells = ConstructionPasteOps.withoutPureAirCells(footprintCells);
        ConstructionPasteOps.AssemblyDeferredPartition split =
            ConstructionPasteOps.partitionAssemblyDeferredBlocks(
                nonAirCells,
                BlockType.getAssetMap(),
                def.getAssemblyDeferredBlockIds()
            );
        List<PendingBlock> placementOrder = split.main();
        List<PendingBlock> assemblyDeferredBlocks = split.deferred();
        long slot = computeSlotWallMs(world, plugin, def, placementOrder.size());
        plot.setAssemblyPrefabId(prefabId);
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        tm.updateTown(town);
        AssemblyFootprintIndex footprintIndex = AssemblyFootprintIndex.build(footprintCells);
        PlotAssemblyJob job =
            new PlotAssemblyJob(
                plot.getPlotId(),
                ownerUuid,
                anchor,
                yaw,
                footprintCells,
                footprintIndex,
                placementOrder,
                assemblyDeferredBlocks,
                seq.prefabEntitiesInOrder(),
                buffer,
                seq.prefabRotation(),
                prefabId,
                slot,
                def.getId()
            );
        AssemblySectionMapper clearingMapper = clearingSectionMapper(job, plot);
        // Never clear soft blocks when rehydrating mid-placement: those cells are already in the placed set and
        // would not be written again (wall torches, plants, etc. would stay missing through completion).
        if (!assemblyPastClearingPhase(plot)) {
            if (clearingMapper != null) {
                AssemblyObstructionUtil.clearSoftSkippedBlocksInFootprint(
                    world, job, clearingMapper, plot.getAssemblyActiveSectionIndex()
                );
            } else {
                AssemblyObstructionUtil.clearSoftSkippedBlocksInFootprint(world, job);
            }
        }
        // Placed prefab cells are non-air; treating them as obstructions would restart CLEARING and break progress.
        if (!assemblyPastClearingPhase(plot)
            && AssemblyObstructionUtil.hasObstructionsInLoadedChunks(world, job, clearingMapper)) {
            registerClearingJob(world, tm, town, plot, job);
        } else {
            PlotAssemblyFrontierRuntime assemblyRt =
                PlotAssemblyFrontierRuntime.create(placementOrder, plot, assemblySectionsPerAxisForPlot(plot));
            AssemblyWorldRegistry.put(world, plot.getPlotId(), job, PlotAssemblyPhase.PLACING, assemblyRt, null, null);
            if (plot.getAssemblyStartEpochMs() == 0L) {
                Instant simNow = entityStore.getResource(TimeResource.getResourceType()).getNow();
                plot.setAssemblyStartEpochMs(simNow.toEpochMilli());
                plot.setAssemblyNextPassiveDueSimMs(simNow.toEpochMilli() + slot);
                tm.updateTown(town);
            }
        }
        return true;
    }

    /**
     * {@code true} when persisted assembly state shows the plot already left the manual clearing phase (timer started
     * in {@link #beginPlacingPhase} or at least one frontier block was committed).
     */
    private static boolean assemblyPastClearingPhase(@Nonnull PlotInstance plot) {
        return plot.getAssemblyPlacedBlockCount() > 0 || plot.getAssemblyStartEpochMs() > 0L;
    }

    /**
     * Transitions a job from {@link PlotAssemblyPhase#CLEARING} to {@link PlotAssemblyPhase#PLACING}: clears stray
     * fluids, starts the passive assembly clock, and registers the frontier runtime.
     */
    public static void beginPlacingPhase(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> entityStore,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull PlotAssemblyJob job
    ) {
        if (AssemblyWorldRegistry.phase(world, job.plotId()) == PlotAssemblyPhase.PLACING) {
            return;
        }
        LocalCachedChunkAccessor chunkAccessor =
            ConstructionPasteOps.createAccessor(world, job.anchor(), job.buffer());
        ConstructionPasteOps.prepAssemblySite(
            world,
            job.anchor(),
            job.footprintCells(),
            false,
            job.prefabRotation(),
            job.buffer()
        );
        ConstructionPasteOps.clearNonPrefabFluidsInFootprint(world, job.anchor(), job.footprintCells(), chunkAccessor);
        int ax = assemblySectionsPerAxisForPlot(plot);
        if (ax > 1) {
            plot.setAssemblyActiveSectionIndex(AssemblySectionMapper.firstOccupiedFlatSection(job.pendingBlocks(), ax));
            tm.updateTown(town);
        }
        if (plot.getAssemblyStartEpochMs() == 0L) {
            Instant assemblySimStart = entityStore.getResource(TimeResource.getResourceType()).getNow();
            plot.setAssemblyStartEpochMs(assemblySimStart.toEpochMilli());
            plot.setAssemblyNextPassiveDueSimMs(assemblySimStart.toEpochMilli() + job.slotWallMs());
            tm.updateTown(town);
        }
        PlotAssemblyFrontierRuntime assemblyRt =
            PlotAssemblyFrontierRuntime.create(job.pendingBlocks(), plot, assemblySectionsPerAxisForPlot(plot));
        AssemblyWorldRegistry.transitionToPlacing(world, job.plotId(), assemblyRt);
    }

    private static void maybeBeginPlacingAfterClear(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> entityStore,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull PlotAssemblyJob job
    ) {
        if (AssemblyWorldRegistry.phase(world, job.plotId()) != PlotAssemblyPhase.CLEARING) {
            return;
        }
        PlotAssemblyClearingRuntime clearingRt = AssemblyWorldRegistry.clearingRuntime(world, job.plotId());
        if (clearingRt == null || clearingRt.isEmpty()) {
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            beginPlacingPhase(world, plugin, entityStore, tm, town, plot, job);
        }
    }

    /**
     * Milliseconds of {@link TimeResource} (dilated world dt) between passive frontier placements for one block slot.
     */
    private static long computeSlotWallMs(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull ConstructionDefinition def, int pendingCount) {
        long msDay = msPerGameDay(world, plugin.getConfig().get());
        double days = def.getSelfBuildGameDays();
        long total = Math.max(1L, (long) Math.ceil(days * (double) msDay));
        int n = Math.max(1, pendingCount);
        return Math.max(1L, total / n);
    }

    /**
     * {@link PlotInstance#getAssemblyStartEpochMs()} stores {@link TimeResource#getNow()} millis. Legacy saves may
     * still hold wall-clock ms (always after sim {@code Now}); those are snapped forward once.
     */
    @Nonnull
    private static Instant resolvePassiveAssemblyStart(
        @Nonnull PlotInstance plot,
        @Nonnull Instant simNow,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town
    ) {
        long raw = plot.getAssemblyStartEpochMs();
        Instant start = raw == 0L ? simNow : Instant.ofEpochMilli(raw);
        if (start.isAfter(simNow)) {
            plot.setAssemblyStartEpochMs(simNow.toEpochMilli());
            tm.updateTown(town);
            return simNow;
        }
        return start;
    }

    /**
     * {@link PlotInstance#getAssemblyNextPassiveDueSimMs()} uses sim time; legacy wall clock values far ahead of
     * {@link TimeResource#getNow()} stall passive ticks until snapped once.
     */
    private static long resolvePassiveNextDue(
        @Nonnull PlotInstance plot,
        @Nonnull Instant simNow,
        long slot,
        @Nonnull Instant assemblyStart,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town
    ) {
        long simNowMs = simNow.toEpochMilli();
        long nextDue = plot.getAssemblyNextPassiveDueSimMs();
        if (nextDue == 0L) {
            nextDue = assemblyStart.toEpochMilli() + slot;
            plot.setAssemblyNextPassiveDueSimMs(nextDue);
            tm.updateTown(town);
            return nextDue;
        }
        long wallNow = System.currentTimeMillis();
        if (nextDue > wallNow + 86_400_000L || nextDue > simNowMs + 3_600_000L) {
            plot.setAssemblyNextPassiveDueSimMs(simNowMs);
            tm.updateTown(town);
            return simNowMs;
        }
        return nextDue;
    }

    public static void tickPassive(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> entityStore,
        @Nonnull ComponentAccessor<EntityStore> entityAccessor
    ) {
        if (!plugin.getConfig().get().isPassivePlotAssemblyEnabled()) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        HashMap<UUID, TownRecord> dirtyTowns = new HashMap<>();
        Instant simNow = entityStore.getResource(TimeResource.getResourceType()).getNow();
        long simNowMs = simNow.toEpochMilli();
        for (PlotAssemblyJob job : AssemblyWorldRegistry.jobs(world)) {
            if (AssemblyWorldRegistry.phase(world, job.plotId()) == PlotAssemblyPhase.CLEARING) {
                TownRecord clearingTown = tm.findTownOwningPlot(job.plotId());
                if (clearingTown == null) {
                    AssemblyWorldRegistry.remove(world, job.plotId());
                    continue;
                }
                PlotInstance clearingPlot = clearingTown.findPlotById(job.plotId());
                if (clearingPlot == null || clearingPlot.getState() != PlotInstanceState.ASSEMBLING) {
                    AssemblyWorldRegistry.remove(world, job.plotId());
                    continue;
                }
                PlotAssemblyClearingRuntime clearingRt = AssemblyWorldRegistry.clearingRuntime(world, job.plotId());
                if (clearingRt != null) {
                    clearingRt.pruneStaleIfDue(world, job);
                }
                if (clearingRt != null && !clearingRt.isEmpty()) {
                    int boost = AssemblyPassiveBoostRegistry.boostFor(world, job.plotId());
                    long slot = Math.max(1L, job.slotWallMs() / boost);
                    int maxBlocks = PASSIVE_CLEARING_BLOCKS_PER_WORLD_TICK_PER_JOB * boost;
                    Instant assemblyStart = resolvePassiveAssemblyStart(clearingPlot, simNow, tm, clearingTown);
                    long nextDue = resolvePassiveNextDue(clearingPlot, simNow, slot, assemblyStart, tm, clearingTown);
                    if (simNowMs >= nextDue) {
                        AssemblySectionMapper clearingMapper = clearingSectionMapper(job, clearingPlot);
                        maybeAdvanceClearingSection(
                            tm, clearingTown, clearingPlot, job, clearingRt, world, clearingMapper, dirtyTowns
                        );
                        PlotAssemblyClearingFrontierRuntime clearingFrontierRt =
                            AssemblyWorldRegistry.clearingFrontierRuntime(world, job.plotId());
                        LocalCachedChunkAccessor chunkAccessor = clearingRt.getOrCreateChunkAccessor(world, job);
                        if (clearingFrontierRt == null) {
                            int activeSection = clearingPlot.getAssemblyActiveSectionIndex();
                            clearingFrontierRt =
                                PlotAssemblyClearingFrontierRuntime.rebuild(
                                    world, job, clearingRt, chunkAccessor, clearingMapper, activeSection
                                );
                            AssemblyWorldRegistry.updateClearingFrontierRuntime(
                                world, job.plotId(), clearingFrontierRt
                            );
                        }
                        int burst = 0;
                        int clearingAttempts = 0;
                        int maxClearingAttempts = Math.max(maxBlocks * 32, 32);
                        while (burst < maxBlocks
                            && clearingFrontierRt != null
                            && !clearingFrontierRt.isEmpty()
                            && clearingAttempts < maxClearingAttempts) {
                            clearingAttempts++;
                            Vector3i cell = clearingFrontierRt.firstWorldCell();
                            if (cell == null) {
                                break;
                            }
                            if (!isChunkLoadedForWorldCell(world, cell.x, cell.z)) {
                                break;
                            }
                            ClearingAdvanceOutcome outcome =
                                advanceClearingAtCell(
                                    world,
                                    plugin,
                                    entityAccessor,
                                    entityStore,
                                    clearingTown,
                                    clearingPlot,
                                    job,
                                    cell,
                                    null,
                                    chunkAccessor,
                                    clearingFrontierRt
                                );
                            if (!outcome.progressed()) {
                                continue;
                            }
                            clearingPlot.setAssemblyNextPassiveDueSimMs(simNowMs + slot);
                            markAssemblyTownDirty(dirtyTowns, clearingTown);
                            burst++;
                            maybeAdvanceClearingSection(
                                tm, clearingTown, clearingPlot, job, clearingRt, world, clearingMapper, dirtyTowns
                            );
                            if (clearingRt.isEmpty()) {
                                break;
                            }
                        }
                    }
                }
                maybeBeginPlacingAfterClear(world, plugin, entityStore, clearingTown, clearingPlot, job);
                continue;
            }
            TownRecord town = tm.findTownOwningPlot(job.plotId());
            if (town == null) {
                AssemblyWorldRegistry.remove(world, job.plotId());
                continue;
            }
            PlotInstance plot = town.findPlotById(job.plotId());
            if (plot == null || plot.getState() != PlotInstanceState.ASSEMBLING) {
                AssemblyWorldRegistry.remove(world, job.plotId());
                continue;
            }
            List<PendingBlock> pending = job.pendingBlocks();
            int placedCount = plot.getAssemblyPlacedBlockCount();
            if (placedCount >= pending.size()) {
                scheduleCompleteAssembly(world, plugin, town, plot, job);
                continue;
            }
            Instant assemblyStart = resolvePassiveAssemblyStart(plot, simNow, tm, town);
            int boost = AssemblyPassiveBoostRegistry.boostFor(world, job.plotId());
            long slot = Math.max(1L, job.slotWallMs() / boost);
            int maxBlocks = PASSIVE_BLOCKS_PER_WORLD_TICK_PER_JOB * boost;
            long nextDue = resolvePassiveNextDue(plot, simNow, slot, assemblyStart, tm, town);
            if (simNowMs < nextDue) {
                continue;
            }
            int burst = 0;
            int placementAttempts = 0;
            int maxPlacementAttempts = Math.max(maxBlocks * 32, 32);
            while (burst < maxBlocks
                && placedCount < pending.size()
                && placementAttempts < maxPlacementAttempts) {
                placementAttempts++;
                PlotAssemblyFrontierRuntime rt = frontierRuntimeOrRebuild(world, job, plot, pending);
                int pick = rt.smallestPlacementIndex();
                if (pick < 0) {
                    rt.rebuildFrontierFromPlot(pending, plot);
                    pick = rt.smallestPlacementIndex();
                    if (pick < 0) {
                        LOGGER.atWarning().log(
                            "Assembly frontier empty but plot %s incomplete (%d/%d); finalizing",
                            job.plotId(),
                            placedCount,
                            pending.size()
                        );
                        scheduleCompleteAssembly(world, plugin, town, plot, job);
                        break;
                    }
                }
                if (!isChunkLoadedForBlock(world, job.anchor(), pending.get(pick))) {
                    break;
                }
                PlacementAdvanceOutcome outcome =
                    advancePlacementAtIndex(world, plugin, entityStore, town, plot, job, pick, false, null, true);
                if (!outcome.progressed()) {
                    continue;
                }
                plot.setAssemblyNextPassiveDueSimMs(simNowMs + slot);
                markAssemblyTownDirty(dirtyTowns, town);
                burst++;
                placedCount = plot.getAssemblyPlacedBlockCount();
            }
        }
        flushDirtyAssemblyTowns(tm, dirtyTowns);
        maybeRehydrateAssemblingPlots(world, plugin, entityStore, tm);
    }

    private static void maybeRehydrateAssemblingPlots(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> entityStore,
        @Nonnull TownManager tm
    ) {
        if (!anyAssemblingPlotMissingJob(world, plugin)) {
            return;
        }
        long now = System.currentTimeMillis();
        String worldName = world.getName();
        Long last = LAST_REHYDRATE_SCAN_MS.get(worldName);
        if (last != null && now - last < REHYDRATE_SCAN_INTERVAL_MS) {
            return;
        }
        LAST_REHYDRATE_SCAN_MS.put(worldName, now);
        for (TownRecord town : tm.allTowns()) {
            for (PlotInstance plot : town.getPlotInstances()) {
                if (plot.getState() == PlotInstanceState.ASSEMBLING
                    && AssemblyWorldRegistry.get(world, plot.getPlotId()) == null) {
                    rehydratePlotIfNeeded(world, plugin, town, plot, entityStore);
                }
            }
        }
    }

    /**
     * @param staffActor when non-null, permission is checked against this player for the plot's town.
     * @return whether the assembly queue advanced and whether world state was written.
     */
    @Nonnull
    public static PlacementAdvanceOutcome advancePlacementAtIndex(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> entityStore,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull PlotAssemblyJob job,
        int placementIndex,
        boolean fromStaff,
        @Nullable UUID staffActor,
        boolean deferCompletionWhenFullyPlaced
    ) {
        if (plot.getState() != PlotInstanceState.ASSEMBLING) {
            return PlacementAdvanceOutcome.noProgress();
        }
        if (AssemblyWorldRegistry.phase(world, job.plotId()) != PlotAssemblyPhase.PLACING) {
            return PlacementAdvanceOutcome.noProgress();
        }
        if (fromStaff && staffActor != null && !town.playerCanManageConstructions(staffActor)) {
            return PlacementAdvanceOutcome.noProgress();
        }
        List<PendingBlock> pending = job.pendingBlocks();
        if (placementIndex < 0 || placementIndex >= pending.size()) {
            return PlacementAdvanceOutcome.noProgress();
        }
        IntOpenHashSet placedSet = new IntOpenHashSet();
        plot.fillAssemblyPlacedSet(placedSet, pending.size());
        if (placedSet.contains(placementIndex)) {
            return PlacementAdvanceOutcome.noProgress();
        }
        PlotAssemblyFrontierRuntime rt = frontierRuntimeOrRebuild(world, job, plot, pending);
        if (!rt.frontierContains(placementIndex)) {
            return PlacementAdvanceOutcome.noProgress();
        }
        if (!isChunkLoadedForBlock(world, job.anchor(), pending.get(placementIndex))) {
            return PlacementAdvanceOutcome.noProgress();
        }
        LocalCachedChunkAccessor chunkAccessor = rt.getOrCreateChunkAccessor(world, job.anchor(), job.buffer());
        BlockTypeAssetMap<String, BlockType> blockTypeMap = BlockType.getAssetMap();
        PendingBlock pb = pending.get(placementIndex);
        boolean wroteBlock =
            !ConstructionPasteOps.isAssemblyPlacementNoOp(job.anchor(), pb, chunkAccessor, blockTypeMap);
        if (wroteBlock) {
            if (!ConstructionPasteOps.placeOne(world, job.anchor(), pb, true, chunkAccessor, blockTypeMap)) {
                rt.clearChunkAccessor();
                return PlacementAdvanceOutcome.noProgress();
            }
        }
        plot.addAssemblyPlacedIndex(placementIndex);
        rt.onBlockPlaced(placementIndex, pending, plot);
        AssemblySectionMapper sm = rt.getSectionMapper();
        if (sm != null && plot.getAssemblyPlacedBlockCount() < pending.size()) {
            TownManager tmAdv = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            if (maybeAdvanceAssemblySection(tmAdv, town, plot, pending, sm)) {
                rt.rebuildFrontierFromPlot(pending, plot);
                rt.clearChunkAccessor();
            }
        }
        if (fromStaff && staffActor != null && wroteBlock) {
            PlotAssemblyPreviewSystem.markStaffAssemblyBlockPlaced(staffActor);
        }
        AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).updateTown(town);
        boolean fullyIndexed = plot.getAssemblyPlacedBlockCount() >= pending.size();
        boolean frontierExhausted = !fullyIndexed && rt.smallestPlacementIndex() < 0;
        if (frontierExhausted) {
            rt.rebuildFrontierFromPlot(pending, plot);
            frontierExhausted = rt.smallestPlacementIndex() < 0;
        }
        if (fullyIndexed || frontierExhausted) {
            if (deferCompletionWhenFullyPlaced) {
                scheduleCompleteAssembly(world, plugin, town, plot, job);
            } else {
                completeAssembly(world, plugin, entityStore, town, plot, job);
            }
            return wroteBlock ? PlacementAdvanceOutcome.committed() : PlacementAdvanceOutcome.skipped();
        }
        return wroteBlock ? PlacementAdvanceOutcome.committed() : PlacementAdvanceOutcome.skipped();
    }

    /**
     * {@link ConstructionPasteOps#finishFluidsAndEntities} spawns prefab entities via {@link Store#addEntity}, which
     * cannot run while the entity store is mid-tick (e.g. interaction systems). Defer to the next world task.
     *
     * <p>Resolves the job by plot id at run time so a rehydrated/replaced job still completes when the frontier is
     * fully placed (reference equality on the scheduled job instance is not required).
     */
    private static void scheduleCompleteAssembly(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull PlotAssemblyJob job
    ) {
        UUID plotId = plot.getPlotId();
        world.execute(() -> {
            if (plot.getState() != PlotInstanceState.ASSEMBLING) {
                return;
            }
            if (world.getEntityStore() == null) {
                return;
            }
            Store<EntityStore> store = world.getEntityStore().getStore();
            PlotAssemblyJob toComplete = AssemblyWorldRegistry.get(world, plotId);
            if (toComplete == null) {
                // Prefer the instance that scheduled us (avoids a rehydrate pass that can wipe soft blocks).
                toComplete = job;
            }
            if (toComplete == null) {
                return;
            }
            // Do not require placedCount == pending.size(): empty-frontier leftovers (wall torches, etc.) are
            // committed inside completeAssembly via bruteForcePlaceRemaining + placeMissingAssemblyBlocks.
            completeAssembly(world, plugin, store, town, plot, toComplete);
        });
    }

    public static void completeAssembly(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> entityStore,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull PlotAssemblyJob job
    ) {
        UUID plotId = plot.getPlotId();
        if (plot.getState() != PlotInstanceState.ASSEMBLING) {
            return;
        }
        IPrefabBuffer completionBuffer = acquireCompletionPrefabBuffer(plugin, job);
        boolean borrowedCompletionBuffer = completionBuffer != job.buffer();
        try {
            // PrefabUtil-style force setBlock of every solid from a fresh buffer read (wall torches included).
            ConstructionPasteOps.forcePasteAllSolids(world, job.anchor(), job.yaw(), completionBuffer);
            ConstructionPasteOps.placeInteractiveBlockEntitiesFromPrefab(
                world,
                job.anchor(),
                job.yaw(),
                completionBuffer
            );
            ConstructionPasteOps.finishFluidsAndEntities(
                world,
                job.anchor(),
                job.prefabRotation(),
                job.prefabId(),
                completionBuffer,
                job.prefabEntitiesInOrder(),
                entityStore
            );
        } catch (RuntimeException e) {
            // Still finish town bookkeeping below; missing props can be repaired, but an ASSEMBLING plot with no job
            // would rehydrate and wipe soft blocks again.
            LOGGER.atSevere().withCause(e).log(
                "Assembly completion paste had errors for plot %s; applying town bookkeeping anyway",
                plotId
            );
        } finally {
            if (borrowedCompletionBuffer) {
                AssemblyWorldRegistry.releasePrefabBufferQuietly(completionBuffer);
            }
        }
        try {
            PrefabPasteEvent end = new PrefabPasteEvent(job.prefabId(), false);
            entityStore.invoke(end);
        } catch (RuntimeException e) {
            LOGGER.atWarning().withCause(e).log("PrefabPasteEvent end failed for plot %s", plotId);
        }
        AssemblyWorldRegistry.remove(world, plotId);
        UUID finisher = plot.getAssemblyOwnerUuid() != null ? plot.getAssemblyOwnerUuid() : town.getOwnerUuid();
        UUID notifyUuid = town.playerCanManageConstructions(finisher) ? finisher : town.getOwnerUuid();
        try {
            AssemblyCompletionEffects.tryNotifyFinisher(world, plugin, entityStore, notifyUuid, plot);
        } catch (RuntimeException e) {
            LOGGER.atWarning().withCause(e).log("Assembly completion notify failed for plot %s", plotId);
        }
        ConstructionCompleter.finishBuild(world, plugin, finisher, plotId, job.anchor(), job.yaw());
        verifyPlotBlockLinksAfterComplete(world, plugin, town, plot, job);
    }

    /**
     * When the growth frontier is empty but the plot is still ASSEMBLING, run the same finalize path as
     * {@code /ah plots finishassembly} (deferred via {@link #scheduleCompleteAssembly}).
     */
    public static void tryFinalizeWhenFrontierExhausted(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull PlotAssemblyJob job
    ) {
        if (plot.getState() != PlotInstanceState.ASSEMBLING) {
            return;
        }
        if (AssemblyWorldRegistry.phase(world, job.plotId()) != PlotAssemblyPhase.PLACING) {
            return;
        }
        List<PendingBlock> pending = job.pendingBlocks();
        PlotAssemblyFrontierRuntime rt = frontierRuntimeOrRebuild(world, job, plot, pending);
        if (rt.smallestPlacementIndex() >= 0) {
            return;
        }
        rt.rebuildFrontierFromPlot(pending, plot);
        if (rt.smallestPlacementIndex() >= 0) {
            return;
        }
        scheduleCompleteAssembly(world, plugin, town, plot, job);
    }

    private static void verifyPlotBlockLinksAfterComplete(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull PlotAssemblyJob job
    ) {
        ConstructionDefinition def = plugin.getConstructionCatalog().get(plot.getConstructionId());
        if (def == null) {
            return;
        }
        PlotBlockStamper.PlotBlockRepairResult repair =
            PlotBlockStamper.verifyAndRepairPlot(world, town, plot, def, job.anchor(), job.yaw());
        if (repair.getRelinked() > 0) {
            LOGGER.atInfo().log(
                "Post-assembly link repair for plot %s: relinked=%d",
                plot.getPlotId(),
                repair.getRelinked()
            );
            AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).updateTown(town);
        }
    }

    private static boolean bruteForcePlaceRemaining(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull PlotAssemblyJob job
    ) {
        List<PendingBlock> pending = job.pendingBlocks();
        IntOpenHashSet placedSet = new IntOpenHashSet();
        plot.fillAssemblyPlacedSet(placedSet, pending.size());
        LocalCachedChunkAccessor chunkAccessor = ConstructionPasteOps.createAccessor(world, job.anchor(), job.buffer());
        BlockTypeAssetMap<String, BlockType> blockTypeMap = BlockType.getAssetMap();
        boolean any = false;
        for (int i = 0; i < pending.size(); i++) {
            if (placedSet.contains(i)) {
                continue;
            }
            if (!isChunkLoadedForBlock(world, job.anchor(), pending.get(i))) {
                return false;
            }
            if (!ConstructionPasteOps.placeOne(world, job.anchor(), pending.get(i), true, chunkAccessor, blockTypeMap)) {
                chunkAccessor = ConstructionPasteOps.createAccessor(world, job.anchor(), job.buffer());
                if (!ConstructionPasteOps.placeOne(world, job.anchor(), pending.get(i), true, chunkAccessor, blockTypeMap)) {
                    continue;
                }
            }
            plot.addAssemblyPlacedIndex(i);
            any = true;
        }
        if (any) {
            AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).updateTown(town);
        }
        return plot.getAssemblyPlacedBlockCount() >= pending.size();
    }

    /**
     * Returns a live prefab accessor for the completion pass. The job's buffer may already be released when several
     * assembling plots share a prefab path and an earlier completion or preview released the cached accessor.
     */
    @Nonnull
    private static IPrefabBuffer acquireCompletionPrefabBuffer(@Nonnull AetherhavenPlugin plugin, @Nonnull PlotAssemblyJob job) {
        ConstructionDefinition def = plugin.getConstructionCatalog().get(job.constructionId());
        if (def != null) {
            Path prefabPath = PrefabResolveUtil.resolvePrefabPath(def.getPrefabPath());
            if (prefabPath != null) {
                return PrefabBufferUtil.getCached(prefabPath);
            }
        }
        return job.buffer();
    }

    @Nonnull
    private static PlotAssemblyFrontierRuntime frontierRuntimeOrRebuild(
        @Nonnull World world,
        @Nonnull PlotAssemblyJob job,
        @Nonnull PlotInstance plot,
        @Nonnull List<PendingBlock> pending
    ) {
        PlotAssemblyFrontierRuntime rt = AssemblyWorldRegistry.frontierRuntime(world, job.plotId());
        if (rt == null) {
            int ax = assemblySectionsPerAxisForPlot(plot);
            rt = PlotAssemblyFrontierRuntime.create(pending, plot, ax);
            AssemblyWorldRegistry.transitionToPlacing(world, job.plotId(), rt);
        }
        return rt;
    }

    private static int assemblySectionsPerAxisForPlot(@Nonnull PlotInstance plot) {
        Integer d = plot.getAssemblySectionDivisions();
        return d != null && d > 1 ? AssemblySectionMapper.clampAxisDivisions(d) : 1;
    }

    private static boolean maybeAdvanceAssemblySection(
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull List<PendingBlock> pending,
        @Nonnull AssemblySectionMapper mapper
    ) {
        int active = plot.getAssemblyActiveSectionIndex();
        if (!mapper.isSectionComplete(pending, plot, active)) {
            return false;
        }
        int vol = mapper.sectionCount();
        int next = active + 1;
        while (next < vol && !mapper.sectionHasAnyCell(pending, next)) {
            next++;
        }
        if (next >= vol) {
            return false;
        }
        plot.setAssemblyActiveSectionIndex(next);
        tm.updateTown(town);
        return true;
    }

    @Nullable
    private static AssemblySectionMapper clearingSectionMapper(@Nonnull PlotAssemblyJob job, @Nonnull PlotInstance plot) {
        return AssemblySectionMapper.tryCreate(job.pendingBlocks(), assemblySectionsPerAxisForPlot(plot));
    }

    private static boolean clearingSectionHasAnyObstructedCell(
        @Nonnull AssemblySectionMapper mapper,
        @Nonnull Vector3i anchor,
        @Nonnull ArrayList<Vector3i> obstructedWorldCells,
        int flatSection
    ) {
        for (int i = 0; i < obstructedWorldCells.size(); i++) {
            Vector3i c = obstructedWorldCells.get(i);
            if (mapper.isWorldCellInSection(anchor, c.x, c.y, c.z, flatSection)) {
                return true;
            }
        }
        return false;
    }

    private static void maybeAdvanceClearingSection(
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull PlotAssemblyJob job,
        @Nonnull PlotAssemblyClearingRuntime clearingRt,
        @Nonnull World world,
        @Nullable AssemblySectionMapper mapper,
        @Nonnull Map<UUID, TownRecord> dirtyTowns
    ) {
        if (mapper == null) {
            return;
        }
        ArrayList<Vector3i> obstructed = new ArrayList<>();
        clearingRt.appendObstructedCells(obstructed);
        int active = plot.getAssemblyActiveSectionIndex();
        if (clearingSectionHasAnyObstructedCell(mapper, job.anchor(), obstructed, active)) {
            return;
        }
        int vol = mapper.sectionCount();
        int next = active + 1;
        while (next < vol) {
            clearingRt.scanSectionFootprintMerge(world, job, mapper, next);
            AssemblyObstructionUtil.clearSoftSkippedBlocksInFootprint(world, job, mapper, next);
            obstructed.clear();
            clearingRt.appendObstructedCells(obstructed);
            if (clearingSectionHasAnyObstructedCell(mapper, job.anchor(), obstructed, next)) {
                plot.setAssemblyActiveSectionIndex(next);
                markAssemblyTownDirty(dirtyTowns, town);
                LocalCachedChunkAccessor acc = clearingRt.getOrCreateChunkAccessor(world, job);
                PlotAssemblyClearingFrontierRuntime rebuilt =
                    PlotAssemblyClearingFrontierRuntime.rebuild(world, job, clearingRt, acc, mapper, next);
                AssemblyWorldRegistry.updateClearingFrontierRuntime(world, job.plotId(), rebuilt);
                return;
            }
            next++;
        }
    }

    /** Appends world-space clearing frontier cells for the active section (for previews / ray tests). */
    public static void appendClearingFrontierWorldCells(
        @Nonnull World world,
        @Nonnull PlotAssemblyJob job,
        @Nonnull PlotInstance plot,
        @Nonnull List<Vector3i> out
    ) {
        PlotAssemblyClearingRuntime clearingRt = AssemblyWorldRegistry.clearingRuntime(world, job.plotId());
        if (clearingRt == null || clearingRt.isEmpty()) {
            return;
        }
        PlotAssemblyClearingFrontierRuntime clearingFrontierRt =
            AssemblyWorldRegistry.clearingFrontierRuntime(world, job.plotId());
        if (clearingFrontierRt != null && !clearingFrontierRt.isEmpty()) {
            clearingFrontierRt.appendFrontierWorldCells(out);
            return;
        }
        LocalCachedChunkAccessor chunkAccessor = clearingRt.getOrCreateChunkAccessor(world, job);
        AssemblySectionMapper mapper = clearingSectionMapper(job, plot);
        int activeSection = plot.getAssemblyActiveSectionIndex();
        clearingFrontierRt =
            PlotAssemblyClearingFrontierRuntime.rebuild(
                world, job, clearingRt, chunkAccessor, mapper, activeSection
            );
        AssemblyWorldRegistry.updateClearingFrontierRuntime(world, job.plotId(), clearingFrontierRt);
        clearingFrontierRt.appendFrontierWorldCells(out);
    }

    private static boolean resolveClearingFrontierCell(
        @Nonnull World world,
        @Nonnull PlotAssemblyJob job,
        @Nonnull PlotInstance plot,
        @Nonnull Vector3i cellWorld
    ) {
        PlotAssemblyClearingRuntime clearingRt = AssemblyWorldRegistry.clearingRuntime(world, job.plotId());
        if (clearingRt == null || !clearingRt.containsWorldCell(cellWorld.x, cellWorld.y, cellWorld.z)) {
            return false;
        }
        PlotAssemblyClearingFrontierRuntime clearingFrontierRt =
            AssemblyWorldRegistry.clearingFrontierRuntime(world, job.plotId());
        if (clearingFrontierRt != null) {
            return clearingFrontierRt.containsWorldCell(cellWorld.x, cellWorld.y, cellWorld.z);
        }
        ArrayList<Vector3i> frontierScratch = new ArrayList<>();
        appendClearingFrontierWorldCells(world, job, plot, frontierScratch);
        for (int i = 0; i < frontierScratch.size(); i++) {
            Vector3i c = frontierScratch.get(i);
            if (c.x == cellWorld.x && c.y == cellWorld.y && c.z == cellWorld.z) {
                return true;
            }
        }
        return false;
    }

    private static boolean isChunkLoadedForBlock(@Nonnull World world, @Nonnull Vector3i origin, @Nonnull PendingBlock pb) {
        int bx = origin.x + pb.x();
        int bz = origin.z + pb.z();
        return isChunkLoadedForWorldCell(world, bx, bz);
    }

    private static boolean isChunkLoadedForWorldCell(@Nonnull World world, int wx, int wz) {
        return world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(wx, wz)) != null;
    }

    /** Appends world-space integer cells for every frontier placement (for previews / ray tests). */
    public static void appendFrontierWorldCells(
        @Nonnull World world,
        @Nonnull PlotAssemblyJob job,
        @Nonnull PlotInstance plot,
        @Nonnull List<Vector3i> out
    ) {
        List<PendingBlock> pending = job.pendingBlocks();
        PlotAssemblyFrontierRuntime rt = AssemblyWorldRegistry.frontierRuntime(world, job.plotId());
        if (rt != null) {
            rt.appendFrontierWorldCells(job.anchor(), pending, out);
            return;
        }
        IntOpenHashSet placedSet = new IntOpenHashSet();
        plot.fillAssemblyPlacedSet(placedSet, pending.size());
        int ax = assemblySectionsPerAxisForPlot(plot);
        IntArrayList frontier =
            ax > 1
                ? PlotAssemblyFrontier.frontierIndicesForActiveSection(
                    pending,
                    placedSet,
                    plot.getAssemblyActiveSectionIndex(),
                    AssemblySectionMapper.create(pending, ax)
                )
                : PlotAssemblyFrontier.frontierIndices(pending, placedSet);
        for (int k = 0; k < frontier.size(); k++) {
            PendingBlock pb = pending.get(frontier.getInt(k));
            out.add(new Vector3i(job.anchor().x + pb.x(), job.anchor().y + pb.y(), job.anchor().z + pb.z()));
        }
    }

    /**
     * @return pending sequence index if {@code cellWorld} matches a frontier cell for this plot, else {@code -1}.
     */
    public static int resolveFrontierPlacementIndex(
        @Nonnull World world,
        @Nonnull PlotAssemblyJob job,
        @Nonnull PlotInstance plot,
        @Nonnull Vector3i cellWorld
    ) {
        List<PendingBlock> pending = job.pendingBlocks();
        PlotAssemblyFrontierRuntime rt = AssemblyWorldRegistry.frontierRuntime(world, job.plotId());
        if (rt != null) {
            return rt.resolveFrontierPlacementIndex(job.anchor(), pending, cellWorld);
        }
        IntOpenHashSet placedSet = new IntOpenHashSet();
        plot.fillAssemblyPlacedSet(placedSet, pending.size());
        int ax = assemblySectionsPerAxisForPlot(plot);
        IntArrayList frontier =
            ax > 1
                ? PlotAssemblyFrontier.frontierIndicesForActiveSection(
                    pending,
                    placedSet,
                    plot.getAssemblyActiveSectionIndex(),
                    AssemblySectionMapper.create(pending, ax)
                )
                : PlotAssemblyFrontier.frontierIndices(pending, placedSet);
        for (int k = 0; k < frontier.size(); k++) {
            int pi = frontier.getInt(k);
            PendingBlock pb = pending.get(pi);
            int bx = job.anchor().x + pb.x();
            int by = job.anchor().y + pb.y();
            int bz = job.anchor().z + pb.z();
            if (bx == cellWorld.x && by == cellWorld.y && bz == cellWorld.z) {
                return pi;
            }
        }
        return -1;
    }

    /**
     * Frontier indices whose world block lies within Chebyshev {@code radius} of {@code centerWorld}, ordered by
     * distance from the center ascending then by prefab sequence index (deterministic batch for the staff brush).
     */
    @Nonnull
    public static IntArrayList frontierPlacementIndicesNearChebyshev(
        @Nonnull World world,
        @Nonnull PlotAssemblyJob job,
        @Nonnull PlotInstance plot,
        @Nonnull Vector3i centerWorld,
        int radius
    ) {
        List<PendingBlock> pending = job.pendingBlocks();
        PlotAssemblyFrontierRuntime rt = frontierRuntimeOrRebuild(world, job, plot, pending);
        IntArrayList matches = new IntArrayList();
        int cx = centerWorld.x;
        int cy = centerWorld.y;
        int cz = centerWorld.z;
        Vector3i anchor = job.anchor();
        IntIterator fit = rt.frontierIterator();
        while (fit.hasNext()) {
            int pi = fit.nextInt();
            PendingBlock pb = pending.get(pi);
            int bx = anchor.x + pb.x();
            int by = anchor.y + pb.y();
            int bz = anchor.z + pb.z();
            int dx = Math.abs(bx - cx);
            int dy = Math.abs(by - cy);
            int dz = Math.abs(bz - cz);
            if (Math.max(Math.max(dx, dy), dz) <= radius) {
                matches.add(pi);
            }
        }
        if (matches.size() <= 1) {
            return matches;
        }
        for (int i = 0; i + 1 < matches.size(); i++) {
            int best = i;
            for (int j = i + 1; j < matches.size(); j++) {
                int idxBest = matches.getInt(best);
                int idxJ = matches.getInt(j);
                int dBest = chebyshevDistTo(anchor, pending.get(idxBest), cx, cy, cz);
                int dJ = chebyshevDistTo(anchor, pending.get(idxJ), cx, cy, cz);
                if (dJ < dBest || (dJ == dBest && idxJ < idxBest)) {
                    best = j;
                }
            }
            if (best != i) {
                int tmp = matches.getInt(i);
                matches.set(i, matches.getInt(best));
                matches.set(best, tmp);
            }
        }
        return matches;
    }

    private static int chebyshevDistTo(
        @Nonnull Vector3i anchor,
        @Nonnull PendingBlock pb,
        int cx,
        int cy,
        int cz
    ) {
        int bx = anchor.x + pb.x();
        int by = anchor.y + pb.y();
        int bz = anchor.z + pb.z();
        return Math.max(Math.max(Math.abs(bx - cx), Math.abs(by - cy)), Math.abs(bz - cz));
    }

    @Nullable
    public static PlotAssemblyJob findJobContainingPreview(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Vector3i cellWorld
    ) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        for (PlotAssemblyJob job : AssemblyWorldRegistry.jobs(world)) {
            TownRecord town = tm.findTownOwningPlot(job.plotId());
            if (town == null) {
                continue;
            }
            PlotInstance plot = town.findPlotById(job.plotId());
            if (plot == null || plot.getState() != PlotInstanceState.ASSEMBLING) {
                continue;
            }
            if (AssemblyWorldRegistry.phase(world, job.plotId()) == PlotAssemblyPhase.CLEARING) {
                if (resolveClearingFrontierCell(world, job, plot, cellWorld)) {
                    return job;
                }
                continue;
            }
            if (resolveFrontierPlacementIndex(world, job, plot, cellWorld) >= 0) {
                return job;
            }
        }
        return null;
    }

    @Nullable
    public static PlotAssemblyJob findClearingJobForObstructedCell(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Vector3i cellWorld
    ) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        for (PlotAssemblyJob job : AssemblyWorldRegistry.jobs(world)) {
            if (AssemblyWorldRegistry.phase(world, job.plotId()) != PlotAssemblyPhase.CLEARING) {
                continue;
            }
            TownRecord town = tm.findTownOwningPlot(job.plotId());
            if (town == null) {
                continue;
            }
            PlotInstance plot = town.findPlotById(job.plotId());
            if (plot == null || plot.getState() != PlotInstanceState.ASSEMBLING) {
                continue;
            }
            if (resolveClearingFrontierCell(world, job, plot, cellWorld)) {
                return job;
            }
        }
        return null;
    }

    /**
     * @return whether the clearing queue advanced and whether a solid block was broken.
     */
    @Nonnull
    public static ClearingAdvanceOutcome advanceClearingAtCell(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull ComponentAccessor<EntityStore> entityAccessor,
        @Nonnull Store<EntityStore> entityStore,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull PlotAssemblyJob job,
        @Nonnull Vector3i cellWorld,
        @Nullable UUID staffActor
    ) {
        PlotAssemblyClearingRuntime clearingRt = AssemblyWorldRegistry.clearingRuntime(world, job.plotId());
        LocalCachedChunkAccessor chunkAccessor =
            clearingRt != null
                ? clearingRt.getOrCreateChunkAccessor(world, job)
                : ConstructionPasteOps.createAccessor(world, job.anchor(), job.buffer());
        return advanceClearingAtCell(
            world,
            plugin,
            entityAccessor,
            entityStore,
            town,
            plot,
            job,
            cellWorld,
            staffActor,
            chunkAccessor,
            AssemblyWorldRegistry.clearingFrontierRuntime(world, job.plotId())
        );
    }

    @Nonnull
    public static ClearingAdvanceOutcome advanceClearingAtCell(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull ComponentAccessor<EntityStore> entityAccessor,
        @Nonnull Store<EntityStore> entityStore,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull PlotAssemblyJob job,
        @Nonnull Vector3i cellWorld,
        @Nullable UUID staffActor,
        @Nonnull LocalCachedChunkAccessor chunkAccessor,
        @Nullable PlotAssemblyClearingFrontierRuntime clearingFrontierRt
    ) {
        if (plot.getState() != PlotInstanceState.ASSEMBLING) {
            return ClearingAdvanceOutcome.noProgress();
        }
        if (AssemblyWorldRegistry.phase(world, job.plotId()) != PlotAssemblyPhase.CLEARING) {
            return ClearingAdvanceOutcome.noProgress();
        }
        if (staffActor != null && !town.playerCanManageConstructions(staffActor)) {
            return ClearingAdvanceOutcome.noProgress();
        }
        if (!resolveClearingFrontierCell(world, job, plot, cellWorld)) {
            return ClearingAdvanceOutcome.noProgress();
        }
        PlotAssemblyClearingRuntime clearingRt = AssemblyWorldRegistry.clearingRuntime(world, job.plotId());
        if (clearingRt == null || !clearingRt.containsWorldCell(cellWorld.x, cellWorld.y, cellWorld.z)) {
            maybeBeginPlacingAfterClear(world, plugin, entityStore, town, plot, job);
            return ClearingAdvanceOutcome.noProgress();
        }
        if (!AssemblyObstructionUtil.isObstructedFootprintCell(world, job, cellWorld, chunkAccessor)) {
            noteClearingCellCleared(
                world, job, plot, clearingRt, clearingFrontierRt, chunkAccessor, cellWorld
            );
            maybeBeginPlacingAfterClear(world, plugin, entityStore, town, plot, job);
            return ClearingAdvanceOutcome.skipped();
        }
        if (!AssemblyStaffClearBreak.breakWithLoot(world, cellWorld, entityAccessor)) {
            if (!AssemblyObstructionUtil.isObstructedFootprintCell(world, job, cellWorld, chunkAccessor)) {
                noteClearingCellCleared(
                    world, job, plot, clearingRt, clearingFrontierRt, chunkAccessor, cellWorld
                );
                maybeBeginPlacingAfterClear(world, plugin, entityStore, town, plot, job);
                return ClearingAdvanceOutcome.skipped();
            }
            return ClearingAdvanceOutcome.noProgress();
        }
        noteClearingCellCleared(
            world, job, plot, clearingRt, clearingFrontierRt, chunkAccessor, cellWorld
        );
        if (staffActor != null) {
            PlotAssemblyPreviewSystem.markStaffAssemblyBlockPlaced(staffActor);
        }
        HashMap<UUID, TownRecord> dirtyTowns = new HashMap<>();
        AssemblySectionMapper clearingMapper = clearingSectionMapper(job, plot);
        if (clearingMapper != null) {
            TownManager tmAdv = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            maybeAdvanceClearingSection(tmAdv, town, plot, job, clearingRt, world, clearingMapper, dirtyTowns);
            flushDirtyAssemblyTowns(tmAdv, dirtyTowns);
        }
        maybeBeginPlacingAfterClear(world, plugin, entityStore, town, plot, job);
        return ClearingAdvanceOutcome.blockBroken();
    }

    private static void noteClearingCellCleared(
        @Nonnull World world,
        @Nonnull PlotAssemblyJob job,
        @Nonnull PlotInstance plot,
        @Nonnull PlotAssemblyClearingRuntime clearingRt,
        @Nullable PlotAssemblyClearingFrontierRuntime clearingFrontierRt,
        @Nonnull LocalCachedChunkAccessor chunkAccessor,
        @Nonnull Vector3i cellWorld
    ) {
        clearingRt.removeCell(cellWorld.x, cellWorld.y, cellWorld.z);
        if (clearingFrontierRt != null) {
            AssemblySectionMapper clearingMapper = clearingSectionMapper(job, plot);
            int activeSection = plot.getAssemblyActiveSectionIndex();
            clearingFrontierRt.onCellCleared(
                world,
                job,
                clearingRt,
                chunkAccessor,
                cellWorld.x,
                cellWorld.y,
                cellWorld.z,
                clearingMapper,
                activeSection
            );
        }
    }

    /**
     * Clearing frontier world cells within Chebyshev {@code radius} of {@code centerWorld}, nearest first.
     */
    @Nonnull
    public static ArrayList<Vector3i> obstructionCellsNearChebyshev(
        @Nonnull World world,
        @Nonnull PlotAssemblyJob job,
        @Nonnull PlotInstance plot,
        @Nonnull Vector3i centerWorld,
        int radius
    ) {
        ArrayList<Vector3i> matches = new ArrayList<>();
        if (AssemblyWorldRegistry.phase(world, job.plotId()) != PlotAssemblyPhase.CLEARING) {
            return matches;
        }
        PlotAssemblyClearingRuntime clearingRt = AssemblyWorldRegistry.clearingRuntime(world, job.plotId());
        if (clearingRt == null) {
            return matches;
        }
        ArrayList<Vector3i> frontier = new ArrayList<>();
        appendClearingFrontierWorldCells(world, job, plot, frontier);
        if (frontier.isEmpty()) {
            return matches;
        }
        int cx = centerWorld.x;
        int cy = centerWorld.y;
        int cz = centerWorld.z;
        for (int i = 0; i < frontier.size(); i++) {
            Vector3i cell = frontier.get(i);
            int dx = Math.abs(cell.x - cx);
            int dy = Math.abs(cell.y - cy);
            int dz = Math.abs(cell.z - cz);
            if (Math.max(Math.max(dx, dy), dz) <= radius) {
                matches.add(cell);
            }
        }
        if (matches.size() <= 1) {
            return matches;
        }
        matches.sort(
            (a, b) -> {
                int da = chebyshevDistWorld(a, cx, cy, cz);
                int db = chebyshevDistWorld(b, cx, cy, cz);
                if (da != db) {
                    return Integer.compare(da, db);
                }
                if (a.y != b.y) {
                    return Integer.compare(a.y, b.y);
                }
                if (a.x != b.x) {
                    return Integer.compare(a.x, b.x);
                }
                return Integer.compare(a.z, b.z);
            }
        );
        return matches;
    }

    private static int chebyshevDistWorld(@Nonnull Vector3i cell, int cx, int cy, int cz) {
        return Math.max(
            Math.max(Math.abs(cell.x - cx), Math.abs(cell.y - cy)),
            Math.abs(cell.z - cz)
        );
    }

    public static final class InstantCompleteTownResult {
        private int finished;
        private int failed;
        private int stillAssembling;
        private int linksRepaired;

        public int getFinished() {
            return finished;
        }

        public int getFailed() {
            return failed;
        }

        public int getStillAssembling() {
            return stillAssembling;
        }

        public int getLinksRepaired() {
            return linksRepaired;
        }
    }

    /**
     * Creative/debug: place every remaining assembly block for one job in frontier order, then run
     * {@link #completeAssembly} on the same thread (no deferred task). Caller must be on the world thread.
     *
     * @return true when the job finished (or was already fully placed), false on missing chunk or bad state.
     */
    public static boolean instantCompleteJob(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> entityStore,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull PlotAssemblyJob job
    ) {
        if (plot.getState() != PlotInstanceState.ASSEMBLING) {
            return false;
        }
        PlotAssemblyJob registered = AssemblyWorldRegistry.get(world, plot.getPlotId());
        if (registered != job) {
            return false;
        }
        if (AssemblyWorldRegistry.phase(world, job.plotId()) == PlotAssemblyPhase.CLEARING) {
            ConstructionPasteOps.prepAssemblySite(
                world,
                job.anchor(),
                job.footprintCells(),
                true,
                job.prefabRotation(),
                job.buffer()
            );
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            beginPlacingPhase(world, plugin, entityStore, tm, town, plot, job);
        }
        List<PendingBlock> pending = job.pendingBlocks();
        if (plot.getAssemblyPlacedBlockCount() >= pending.size()) {
            completeAssembly(world, plugin, entityStore, town, plot, job);
            return true;
        }
        int guard = 0;
        int maxLoops = pending.size() * 3 + 16;
        while (plot.getAssemblyPlacedBlockCount() < pending.size() && guard++ < maxLoops) {
            PlotAssemblyFrontierRuntime rt = frontierRuntimeOrRebuild(world, job, plot, pending);
            int pick = rt.smallestPlacementIndex();
            if (pick < 0) {
                rt.rebuildFrontierFromPlot(pending, plot);
                pick = rt.smallestPlacementIndex();
            }
            if (pick < 0) {
                if (bruteForcePlaceRemaining(world, plugin, town, plot, job)) {
                    break;
                }
                LOGGER.atWarning().log(
                    "instantCompleteJob: empty frontier plot %s (%d/%d placed)",
                    plot.getPlotId(),
                    plot.getAssemblyPlacedBlockCount(),
                    pending.size()
                );
                break;
            }
            if (!advancePlacementAtIndex(world, plugin, entityStore, town, plot, job, pick, false, null, false).progressed()) {
                if (!bruteForcePlaceRemaining(world, plugin, town, plot, job)) {
                    break;
                }
                break;
            }
        }
        if (plot.getAssemblyPlacedBlockCount() >= pending.size()) {
            completeAssembly(world, plugin, entityStore, town, plot, job);
            return true;
        }
        return false;
    }

    /**
     * Completes every in-world assembly job owned by {@code town} (same thread as {@link #instantCompleteJob}).
     *
     * @return how many jobs reached completion.
     */
    public static int instantCompleteAllAssemblingJobsForTown(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> entityStore,
        @Nonnull TownRecord town
    ) {
        return instantCompleteAllAssemblingJobsForTownDetailed(world, plugin, entityStore, town).getFinished();
    }

    @Nonnull
    public static InstantCompleteTownResult instantCompleteAllAssemblingJobsForTownDetailed(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> entityStore,
        @Nonnull TownRecord town
    ) {
        InstantCompleteTownResult result = new InstantCompleteTownResult();
        for (PlotInstance plot : town.getPlotInstances()) {
            if (plot.getState() != PlotInstanceState.ASSEMBLING) {
                continue;
            }
            PlotAssemblyJob job = ensureAssemblyJob(world, plugin, town, plot, entityStore);
            if (job == null) {
                result.failed++;
                continue;
            }
            if (instantCompleteJob(world, plugin, entityStore, town, plot, job)) {
                result.finished++;
            } else {
                result.failed++;
            }
        }
        for (PlotInstance plot : town.getPlotInstances()) {
            if (plot.getState() == PlotInstanceState.ASSEMBLING) {
                result.stillAssembling++;
            }
        }
        return result;
    }
}
