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
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.entity.InteractionChain;
import com.hypixel.hytale.server.core.entity.InteractionManager;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.InteractionModule;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.prefab.event.PrefabPasteEvent;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.LocalCachedChunkAccessor;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.PrefabUtil;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Passive plot assembly: fixed wall-clock schedule from {@code assemblyStartEpochMs + (index+1) * slotWallMs}
 * (see {@link PlotAssemblyJob#slotWallMs()}). Staff advances the same index without shifting future auto slots.
 * Auto-placement runs only while the next cell's chunk is loaded; after long absence, bounded catch-up applies.
 */
public final class PlotAssemblyService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int BREAK_SIGN_SETTINGS = 10;
    /**
     * Passive assembly: at most one prefab cell per plot per world tick. A larger catch-up burst caused blocks to keep
     * appearing for a moment after releasing building-staff secondary (passive resumes in the same tick window).
     */
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

        world.breakBlock(physicalSignWorld.x, physicalSignWorld.y, physicalSignWorld.z, BREAK_SIGN_SETTINGS);

        long now = System.currentTimeMillis();
        plot.setPrefabWorldPlacement(anchor.x, anchor.y, anchor.z, yaw);
        plot.setState(PlotInstanceState.ASSEMBLING);
        plot.setLastStateChangeEpochMs(now);
        plot.setAssemblyStartEpochMs(now);
        plot.setAssemblyBlockIndex(0);
        plot.setAssemblyPrefabId(prefabId);
        plot.setAssemblyOwnerUuid(assemblyOwnerUuid);
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        tm.updateTown(town);

        long slot = computeSlotWallMs(world, plugin, def, seq.pendingBlocks().size());
        PlotAssemblyJob job =
            new PlotAssemblyJob(
                plotId,
                assemblyOwnerUuid,
                anchor,
                yaw,
                seq.pendingBlocks(),
                seq.prefabEntitiesInOrder(),
                buffer,
                seq.prefabRotation(),
                prefabId,
                slot,
                def.getId()
            );
        AssemblyWorldRegistry.put(world, plotId, job);
        refreshPreviewBlock(world, job, plot.getAssemblyBlockIndex());
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
        long slot = computeSlotWallMs(world, plugin, def, seq.pendingBlocks().size());
        plot.setAssemblyPrefabId(prefabId);
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        tm.updateTown(town);
        PlotAssemblyJob job =
            new PlotAssemblyJob(
                plot.getPlotId(),
                ownerUuid,
                anchor,
                yaw,
                seq.pendingBlocks(),
                seq.prefabEntitiesInOrder(),
                buffer,
                seq.prefabRotation(),
                prefabId,
                slot,
                def.getId()
            );
        AssemblyWorldRegistry.put(world, plot.getPlotId(), job);
        refreshPreviewBlock(world, job, plot.getAssemblyBlockIndex());
        return true;
    }

    private static long computeSlotWallMs(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull ConstructionDefinition def, int pendingCount) {
        long msDay = msPerGameDay(world, plugin.getConfig().get());
        double days = def.getSelfBuildGameDays();
        long total = Math.max(1L, (long) Math.ceil(days * (double) msDay));
        int n = Math.max(1, pendingCount);
        return Math.max(1L, total / n);
    }

    public static void tickPassive(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull Store<EntityStore> entityStore) {
        if (!plugin.getConfig().get().isPassivePlotAssemblyEnabled()) {
            return;
        }
        if (anyPlayerChannelingBuildingStaffSecondary(entityStore)) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        long now = System.currentTimeMillis();
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
            int idx = plot.getAssemblyBlockIndex();
            if (idx >= job.pendingBlocks().size()) {
                continue;
            }
            long start = plot.getAssemblyStartEpochMs();
            long slot = job.slotWallMs();
            int placed = 0;
            while (placed < PASSIVE_BLOCKS_PER_WORLD_TICK_PER_JOB && idx < job.pendingBlocks().size()) {
                long due = start + (long) (idx + 1) * slot;
                if (now < due) {
                    break;
                }
                if (!isChunkLoadedForBlock(world, job.anchor(), job.pendingBlocks().get(idx))) {
                    break;
                }
                if (!advanceOneBlock(world, plugin, entityStore, town, plot, job, false, null)) {
                    break;
                }
                placed++;
                idx = plot.getAssemblyBlockIndex();
            }
        }
    }

    /**
     * While any player holds secondary on the building staff (active interaction chain), passive assembly must not
     * advance the same job in parallel — otherwise blocks keep appearing after release.
     */
    private static boolean anyPlayerChannelingBuildingStaffSecondary(@Nonnull Store<EntityStore> store) {
        ComponentType<EntityStore, InteractionManager> imType = InteractionModule.get().getInteractionManagerComponent();
        Query<EntityStore> query = Query.and(Player.getComponentType(), imType);
        return store.forEachChunk(
            query,
            (chunk, commandBuffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    InteractionManager im = chunk.getComponent(i, imType);
                    if (im == null) {
                        continue;
                    }
                    for (InteractionChain chain : im.getChains().values()) {
                        if (chain.getServerState() != InteractionState.NotFinished) {
                            continue;
                        }
                        RootInteraction root = chain.getInitialRootInteraction();
                        if (root != null && AetherhavenConstants.ROOT_INTERACTION_BUILDING_STAFF_SECONDARY.equals(root.getId())) {
                            return true;
                        }
                    }
                }
                return false;
            }
        );
    }

    /**
     * @param staffActor when non-null, permission is checked against this player for the plot's town.
     * @return true if one block was committed (or finish ran).
     */
    public static boolean advanceOneBlock(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> entityStore,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull PlotAssemblyJob job,
        boolean fromStaff,
        @Nullable UUID staffActor
    ) {
        if (plot.getState() != PlotInstanceState.ASSEMBLING) {
            return false;
        }
        if (fromStaff && staffActor != null && !town.playerHasBuildPermission(staffActor)) {
            return false;
        }
        int idx = plot.getAssemblyBlockIndex();
        List<PendingBlock> pending = job.pendingBlocks();
        if (idx >= pending.size()) {
            return false;
        }
        if (!isChunkLoadedForBlock(world, job.anchor(), pending.get(idx))) {
            return false;
        }
        clearPreviewAtIndex(world, job.anchor(), pending, idx);
        LocalCachedChunkAccessor chunkAccessor = ConstructionPasteOps.createAccessor(world, job.anchor(), job.buffer());
        BlockTypeAssetMap<String, BlockType> blockTypeMap = BlockType.getAssetMap();
        ConstructionPasteOps.placeOne(world, job.anchor(), pending.get(idx), true, chunkAccessor, blockTypeMap);
        int next = idx + 1;
        plot.setAssemblyBlockIndex(next);
        AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).updateTown(town);
        if (next >= pending.size()) {
            completeAssembly(world, plugin, entityStore, town, plot, job);
            return true;
        }
        refreshPreviewBlock(world, job, next);
        return true;
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

    /**
     * World integer cell for the next prefab block to place during assembly.
     */
    @Nullable
    public static Vector3i previewCellWorld(@Nonnull PlotAssemblyJob job, @Nonnull PlotInstance plot) {
        int idx = plot.getAssemblyBlockIndex();
        List<PendingBlock> pending = job.pendingBlocks();
        if (idx < 0 || idx >= pending.size()) {
            return null;
        }
        PendingBlock pb = pending.get(idx);
        return new Vector3i(job.anchor().x + pb.x(), job.anchor().y + pb.y(), job.anchor().z + pb.z());
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
            int idx = plot.getAssemblyBlockIndex();
            if (idx < 0 || idx >= job.pendingBlocks().size()) {
                continue;
            }
            PendingBlock pb = job.pendingBlocks().get(idx);
            int bx = job.anchor().x + pb.x();
            int by = job.anchor().y + pb.y();
            int bz = job.anchor().z + pb.z();
            if (bx == cellWorld.x && by == cellWorld.y && bz == cellWorld.z) {
                return job;
            }
        }
        return null;
    }
}
