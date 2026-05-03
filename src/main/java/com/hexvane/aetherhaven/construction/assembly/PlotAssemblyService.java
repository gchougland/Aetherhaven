package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.construction.ConstructionCompleter;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.prefab.event.PrefabPasteEvent;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.LocalCachedChunkAccessor;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PrefabUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

    private PlotAssemblyService() {}

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

    public static void rehydrate(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        Store<EntityStore> entityStore = world.getEntityStore().getStore();
        for (TownRecord town : tm.allTowns()) {
            for (PlotInstance plot : town.getPlotInstances()) {
                if (plot.getState() != PlotInstanceState.ASSEMBLING) {
                    continue;
                }
                if (AssemblyWorldRegistry.get(world, plot.getPlotId()) != null) {
                    continue;
                }
                ConstructionDefinition def = plugin.getConstructionCatalog().get(plot.getConstructionId());
                if (def == null) {
                    LOGGER.atWarning().log("Rehydrate assembly: unknown construction %s plot %s", plot.getConstructionId(), plot.getPlotId());
                    continue;
                }
                Path prefabPath = PrefabResolveUtil.resolvePrefabPath(def.getPrefabPath());
                if (prefabPath == null) {
                    LOGGER.atWarning().log("Rehydrate assembly: missing prefab %s", def.getPrefabPath());
                    continue;
                }
                IPrefabBuffer buffer = PrefabBufferUtil.getCached(prefabPath);
                Vector3i anchor = plot.resolvePrefabAnchorWorld(def);
                Rotation yaw = plot.resolvePrefabYaw();
                UUID owner = plot.getAssemblyOwnerUuid() != null ? plot.getAssemblyOwnerUuid() : town.getOwnerUuid();
                if (!tryRegisterJob(world, plugin, town, plot, anchor, yaw, def, buffer, owner, entityStore)) {
                    buffer.release();
                }
            }
        }
    }

    /**
     * Starts assembly on the world thread: paste begin, prep footprint, break plot sign, persist ASSEMBLING, register job, preview.
     * Caller must have consumed materials/treasury already.
     */
    public static void startFromBuildClick(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull Store<EntityStore> entityStore,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull Vector3i physicalSignWorld,
        @Nonnull UUID assemblyOwnerUuid,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw,
        @Nonnull ConstructionDefinition def,
        @Nonnull IPrefabBuffer buffer
    ) {
        UUID plotId = plot.getPlotId();
        if (AssemblyWorldRegistry.get(world, plotId) != null) {
            LOGGER.atWarning().log("Assembly already active for plot %s", plotId);
            return;
        }
        int prefabId = PrefabUtil.getNextPrefabId();
        PrefabPasteEvent start = new PrefabPasteEvent(prefabId, true);
        entityStore.invoke(start);
        if (start.isCancelled()) {
            buffer.release();
            LOGGER.atWarning().log("Prefab paste start cancelled for plot %s", plotId);
            return;
        }
        ConstructionPasteOps.PrefabSequence seq = ConstructionPasteOps.buildSequence(buffer, yaw);
        ConstructionPasteOps.prepAssemblySite(world, anchor, seq.pendingBlocks(), true, seq.prefabRotation(), buffer);
        List<PendingBlock> placementOrder = ConstructionPasteOps.withoutPureAirCells(seq.pendingBlocks());

        world.breakBlock(physicalSignWorld.x, physicalSignWorld.y, physicalSignWorld.z, BREAK_SIGN_SETTINGS);

        long wallNow = System.currentTimeMillis();
        plot.setPrefabWorldPlacement(anchor.x, anchor.y, anchor.z, yaw);
        plot.setState(PlotInstanceState.ASSEMBLING);
        plot.setLastStateChangeEpochMs(wallNow);
        Instant assemblySimStart = entityStore.getResource(TimeResource.getResourceType()).getNow();
        plot.setAssemblyStartEpochMs(assemblySimStart.toEpochMilli());
        plot.resetAssemblyPlacementProgress();
        plot.setAssemblyPrefabId(prefabId);
        plot.setAssemblyOwnerUuid(assemblyOwnerUuid);
        long slot = computeSlotWallMs(world, plugin, def, placementOrder.size());
        plot.setAssemblyNextPassiveDueSimMs(assemblySimStart.toEpochMilli() + slot);
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        tm.updateTown(town);

        PlotAssemblyJob job =
            new PlotAssemblyJob(
                plotId,
                assemblyOwnerUuid,
                anchor,
                yaw,
                placementOrder,
                seq.prefabEntitiesInOrder(),
                buffer,
                seq.prefabRotation(),
                prefabId,
                slot,
                def.getId()
            );
        AssemblyWorldRegistry.put(world, plotId, job);
        refreshPreviewBlock(world, job, 0);
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
        List<PendingBlock> placementOrder = ConstructionPasteOps.withoutPureAirCells(seq.pendingBlocks());
        long slot = computeSlotWallMs(world, plugin, def, placementOrder.size());
        plot.setAssemblyPrefabId(prefabId);
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        tm.updateTown(town);
        PlotAssemblyJob job =
            new PlotAssemblyJob(
                plot.getPlotId(),
                ownerUuid,
                anchor,
                yaw,
                placementOrder,
                seq.prefabEntitiesInOrder(),
                buffer,
                seq.prefabRotation(),
                prefabId,
                slot,
                def.getId()
            );
        AssemblyWorldRegistry.put(world, plot.getPlotId(), job);
        refreshPreviewBlock(world, job, 0);
        return true;
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

    public static void tickPassive(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull Store<EntityStore> entityStore) {
        if (!plugin.getConfig().get().isPassivePlotAssemblyEnabled()) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        Instant simNow = entityStore.getResource(TimeResource.getResourceType()).getNow();
        for (PlotAssemblyJob job : AssemblyWorldRegistry.jobs(world)) {
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
                continue;
            }
            Instant assemblyStart = resolvePassiveAssemblyStart(plot, simNow, tm, town);
            long slot = job.slotWallMs();
            long simNowMs = simNow.toEpochMilli();
            long nextDue = plot.getAssemblyNextPassiveDueSimMs();
            if (nextDue == 0L) {
                nextDue = assemblyStart.toEpochMilli() + slot;
                plot.setAssemblyNextPassiveDueSimMs(nextDue);
                tm.updateTown(town);
            }
            if (simNowMs < nextDue) {
                continue;
            }
            int burst = 0;
            while (burst < PASSIVE_BLOCKS_PER_WORLD_TICK_PER_JOB && placedCount < pending.size()) {
                IntOpenHashSet placedSet = new IntOpenHashSet();
                plot.fillAssemblyPlacedSet(placedSet, pending.size());
                IntArrayList frontier = PlotAssemblyFrontier.frontierIndices(pending, placedSet);
                int pick = PlotAssemblyFrontier.smallestPlacementIndex(frontier);
                if (pick < 0) {
                    break;
                }
                if (!isChunkLoadedForBlock(world, job.anchor(), pending.get(pick))) {
                    break;
                }
                if (!advancePlacementAtIndex(world, plugin, entityStore, town, plot, job, pick, false, null, true)) {
                    break;
                }
                plot.setAssemblyNextPassiveDueSimMs(simNowMs + slot);
                tm.updateTown(town);
                burst++;
                placedCount = plot.getAssemblyPlacedBlockCount();
            }
        }
    }

    /**
     * @param staffActor when non-null, permission is checked against this player for the plot's town.
     * @return true if one block was committed (or finish was scheduled).
     */
    public static boolean advancePlacementAtIndex(
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
            return false;
        }
        if (fromStaff && staffActor != null && !town.playerCanManageConstructions(staffActor)) {
            return false;
        }
        List<PendingBlock> pending = job.pendingBlocks();
        if (placementIndex < 0 || placementIndex >= pending.size()) {
            return false;
        }
        IntOpenHashSet placedSet = new IntOpenHashSet();
        plot.fillAssemblyPlacedSet(placedSet, pending.size());
        if (placedSet.contains(placementIndex)) {
            return false;
        }
        IntArrayList frontier = PlotAssemblyFrontier.frontierIndices(pending, placedSet);
        if (!PlotAssemblyFrontier.frontierContains(frontier, placementIndex)) {
            return false;
        }
        if (!isChunkLoadedForBlock(world, job.anchor(), pending.get(placementIndex))) {
            return false;
        }
        clearPreviewAtIndex(world, job.anchor(), pending, placementIndex);
        LocalCachedChunkAccessor chunkAccessor = ConstructionPasteOps.createAccessor(world, job.anchor(), job.buffer());
        BlockTypeAssetMap<String, BlockType> blockTypeMap = BlockType.getAssetMap();
        ConstructionPasteOps.placeOne(world, job.anchor(), pending.get(placementIndex), true, chunkAccessor, blockTypeMap);
        plot.addAssemblyPlacedIndex(placementIndex);
        AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).updateTown(town);
        if (plot.getAssemblyPlacedBlockCount() >= pending.size()) {
            if (deferCompletionWhenFullyPlaced) {
                scheduleCompleteAssembly(world, plugin, town, plot, job);
            } else {
                completeAssembly(world, plugin, entityStore, town, plot, job);
            }
            return true;
        }
        refreshPreviewBlock(world, job, placementIndex);
        return true;
    }

    /**
     * {@link ConstructionPasteOps#finishFluidsAndEntities} spawns prefab entities via {@link Store#addEntity}, which
     * cannot run while the entity store is mid-tick (e.g. interaction systems). Defer to the next world task.
     */
    private static void scheduleCompleteAssembly(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull PlotAssemblyJob job
    ) {
        world.execute(() -> {
            PlotAssemblyJob registered = AssemblyWorldRegistry.get(world, plot.getPlotId());
            if (registered != job) {
                return;
            }
            if (plot.getAssemblyPlacedBlockCount() < job.pendingBlocks().size()) {
                return;
            }
            Store<EntityStore> store = world.getEntityStore().getStore();
            completeAssembly(world, plugin, store, town, plot, job);
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
        ConstructionPasteOps.finishFluidsAndEntities(
            world,
            job.anchor(),
            job.prefabRotation(),
            job.prefabId(),
            job.buffer(),
            job.prefabEntitiesInOrder(),
            entityStore
        );
        PrefabPasteEvent end = new PrefabPasteEvent(job.prefabId(), false);
        entityStore.invoke(end);
        AssemblyWorldRegistry.remove(world, plotId);
        UUID finisher = plot.getAssemblyOwnerUuid() != null ? plot.getAssemblyOwnerUuid() : town.getOwnerUuid();
        AssemblyCompletionEffects.tryNotifyFinisher(world, plugin, entityStore, finisher, plot);
        ConstructionCompleter.finishBuild(world, plugin, finisher, plotId, job.anchor(), job.yaw());
    }

    private static boolean isChunkLoadedForBlock(@Nonnull World world, @Nonnull Vector3i origin, @Nonnull PendingBlock pb) {
        int bx = origin.x + pb.x();
        int bz = origin.z + pb.z();
        return world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(bx, bz)) != null;
    }

    /**
     * Next assembly cell is shown only via client {@code DisplayDebug} (see {@link PlotAssemblyPreviewSystem}), not
     * world blocks.
     */
    private static void refreshPreviewBlock(@Nonnull World world, @Nonnull PlotAssemblyJob job, int index) {}

    /** Appends world-space integer cells for every frontier placement (for previews / ray tests). */
    public static void appendFrontierWorldCells(
        @Nonnull PlotAssemblyJob job,
        @Nonnull PlotInstance plot,
        @Nonnull List<Vector3i> out
    ) {
        List<PendingBlock> pending = job.pendingBlocks();
        IntOpenHashSet placedSet = new IntOpenHashSet();
        plot.fillAssemblyPlacedSet(placedSet, pending.size());
        IntArrayList frontier = PlotAssemblyFrontier.frontierIndices(pending, placedSet);
        for (int k = 0; k < frontier.size(); k++) {
            PendingBlock pb = pending.get(frontier.getInt(k));
            out.add(new Vector3i(job.anchor().x + pb.x(), job.anchor().y + pb.y(), job.anchor().z + pb.z()));
        }
    }

    /**
     * @return pending sequence index if {@code cellWorld} matches a frontier cell for this plot, else {@code -1}.
     */
    public static int resolveFrontierPlacementIndex(
        @Nonnull PlotAssemblyJob job,
        @Nonnull PlotInstance plot,
        @Nonnull Vector3i cellWorld
    ) {
        List<PendingBlock> pending = job.pendingBlocks();
        IntOpenHashSet placedSet = new IntOpenHashSet();
        plot.fillAssemblyPlacedSet(placedSet, pending.size());
        IntArrayList frontier = PlotAssemblyFrontier.frontierIndices(pending, placedSet);
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
        @Nonnull PlotAssemblyJob job,
        @Nonnull PlotInstance plot,
        @Nonnull Vector3i centerWorld,
        int radius
    ) {
        List<PendingBlock> pending = job.pendingBlocks();
        IntOpenHashSet placedSet = new IntOpenHashSet();
        plot.fillAssemblyPlacedSet(placedSet, pending.size());
        IntArrayList frontier = PlotAssemblyFrontier.frontierIndices(pending, placedSet);
        IntArrayList matches = new IntArrayList();
        int cx = centerWorld.x;
        int cy = centerWorld.y;
        int cz = centerWorld.z;
        Vector3i anchor = job.anchor();
        for (int k = 0; k < frontier.size(); k++) {
            int pi = frontier.getInt(k);
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

    private static void clearPreviewAtIndex(
        @Nonnull World world,
        @Nonnull Vector3i origin,
        @Nonnull List<PendingBlock> pending,
        int index
    ) {
        if (index < 0 || index >= pending.size()) {
            return;
        }
        PendingBlock pb = pending.get(index);
        int bx = origin.x + pb.x();
        int by = origin.y + pb.y();
        int bz = origin.z + pb.z();
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(bx, bz));
        if (chunk == null) {
            return;
        }
        int blockId = chunk.getBlock(bx, by, bz);
        BlockType bt = BlockType.getAssetMap().getAsset(blockId);
        if (bt != null && AetherhavenConstants.CONSTRUCTION_PREVIEW_BLOCK_TYPE_ID.equals(bt.getId())) {
            chunk.breakBlock(bx, by, bz, ConstructionPasteOps.SET_BLOCK_SETTINGS_CLEAR);
        }
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
            if (resolveFrontierPlacementIndex(job, plot, cellWorld) >= 0) {
                return job;
            }
        }
        return null;
    }

    /**
     * Creative/debug: place every remaining assembly block for one job in frontier order, then run
     * {@link #completeAssembly} on the same thread (no deferred task). Caller must be on the world thread.
     *
     * @return true when the job finished (or was already fully placed), false on missing chunk, bad state, or empty
     *     frontier.
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
        List<PendingBlock> pending = job.pendingBlocks();
        if (plot.getAssemblyPlacedBlockCount() >= pending.size()) {
            completeAssembly(world, plugin, entityStore, town, plot, job);
            return true;
        }
        while (plot.getAssemblyPlacedBlockCount() < pending.size()) {
            IntOpenHashSet placedSet = new IntOpenHashSet();
            plot.fillAssemblyPlacedSet(placedSet, pending.size());
            IntArrayList frontier = PlotAssemblyFrontier.frontierIndices(pending, placedSet);
            int pick = PlotAssemblyFrontier.smallestPlacementIndex(frontier);
            if (pick < 0) {
                LOGGER.atWarning().log("instantCompleteJob: empty frontier plot %s", plot.getPlotId());
                return false;
            }
            if (!advancePlacementAtIndex(world, plugin, entityStore, town, plot, job, pick, false, null, false)) {
                return false;
            }
        }
        return true;
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
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        UUID townId = town.getTownId();
        List<PlotAssemblyJob> jobs = new ArrayList<>();
        for (PlotAssemblyJob job : AssemblyWorldRegistry.jobs(world)) {
            TownRecord ownerTown = tm.findTownOwningPlot(job.plotId());
            if (ownerTown != null && ownerTown.getTownId().equals(townId)) {
                jobs.add(job);
            }
        }
        int finished = 0;
        for (PlotAssemblyJob job : jobs) {
            PlotInstance plot = town.findPlotById(job.plotId());
            if (plot == null || plot.getState() != PlotInstanceState.ASSEMBLING) {
                continue;
            }
            if (instantCompleteJob(world, plugin, entityStore, town, plot, job)) {
                finished++;
            }
        }
        return finished;
    }
}
