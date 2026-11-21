package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import com.hexvane.aetherhaven.construction.DecorationBuild;
import com.hexvane.aetherhaven.construction.assembly.AssemblyWorldRegistry;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyBuildStartResult;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyJob;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyService;
import com.hexvane.aetherhaven.placement.PlotFootprintUtil;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import com.hypixel.hytale.server.core.universe.world.storage.provider.EmptyChunkStorageProvider;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.VoidWorldGenProvider;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

/** Builds real prefabs as decorations in a disposable world and reads back what is standing there. */
final class DecorationBuildWorldCheck extends AbstractAsyncCommand {
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    /** A tavern guild hall: its prefab also carries the town markers that are entities, not blocks. */
    private static final String MARKER_BUILDING = "plot_hytinys_cozy_tavern";

    private record Site(ConstructionDefinition definition, IPrefabBuffer prefab, Vector3i anchor, Rotation yaw, PlotFootprintRecord footprint) {}

    DecorationBuildWorldCheck() {
        super("decorationcheck", "Check that a building built as a decoration leaves only plain blocks");
        setPermissionGroups("hytale:WorldEditor");
    }

    @Override
    protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        if (!RUNNING.compareAndSet(false, true)) {
            report(ctx, "A decoration build check is already running.");
            return CompletableFuture.completedFuture(null);
        }
        String name = "aetherhaven_decoration_check_" + UUID.randomUUID().toString().replace("-", "");
        var universe = Universe.get();
        var path = universe.validateWorldPath(name);
        if (Files.exists(path)) {
            RUNNING.set(false);
            return CompletableFuture.failedFuture(new IllegalStateException("Fixture already exists"));
        }
        var config = new WorldConfig();
        InstanceWorldConfig.ensureAndGet(config);
        config.setWorldGenProvider(new VoidWorldGenProvider());
        config.setChunkStorageProvider(EmptyChunkStorageProvider.INSTANCE);
        config.setSavingPlayers(false);
        config.setCanSaveChunks(false);
        config.setSaveNewChunks(false);
        config.setSpawningNPC(false);
        config.setGameTimePaused(true);
        config.setBlockTicking(false);
        config.setDeleteOnRemove(true);
        config.setDeleteOnUniverseStart(true);
        return universe.makeWorld(name, path, config)
            .thenCompose(world -> exercise(world).orTimeout(150, TimeUnit.SECONDS).whenCompleteAsync((done, error) -> {
                if (error == null) {
                    report(ctx, "Decoration build isolated world check: PASSED. Finished builds, a resumed build, no special blocks, no town markers, no plot kept.");
                } else {
                    Throwable cause = error;
                    while (cause.getCause() != null) {
                        cause = cause.getCause();
                    }
                    report(ctx, "Decoration build isolated world check: FAILED: " + cause.getMessage());
                }
                universe.removeWorld(name);
            }))
            .whenComplete((done, error) -> RUNNING.set(false));
    }

    @Nonnull
    private static CompletableFuture<Void> exercise(@Nonnull World world) {
        List<Site> sites = new ArrayList<>();
        sites.add(site(AetherhavenConstants.CONSTRUCTION_PLOT_TOWN_HALL, new Vector3i(16, 128, 16), Rotation.None));
        sites.add(site(AetherhavenConstants.CONSTRUCTION_PLOT_TOWN_HALL, new Vector3i(400, 128, 16), Rotation.Ninety));
        sites.add(site(MARKER_BUILDING, new Vector3i(16, 128, 400), Rotation.None));
        List<CompletableFuture<?>> sections = new ArrayList<>();
        for (Site site : sites) {
            PlotFootprintRecord fp = site.footprint();
            for (int x = Math.floorDiv(fp.getMinX() - 2, 32); x <= Math.floorDiv(fp.getMaxX() + 2, 32); x++) {
                for (int z = Math.floorDiv(fp.getMinZ() - 2, 32); z <= Math.floorDiv(fp.getMaxZ() + 2, 32); z++) {
                    for (int y = Math.floorDiv(fp.getMinY() - 2, 32); y <= Math.floorDiv(fp.getMaxY() + 2, 32); y++) {
                        sections.add(world.getChunkStore().getChunkSectionReferenceAsync(x, y, z, GetChunkFlags.SET_TICKING));
                    }
                }
            }
        }
        return CompletableFuture.allOf(sections.toArray(CompletableFuture[]::new)).thenRunAsync(() -> {
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            TownManager manager = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            for (int i = 0; i < sites.size(); i++) {
                // The last site is rebuilt from its saved plot first, as after a restart in the middle of a build.
                buildAndCheck(world, plugin, manager, sites.get(i), i == sites.size() - 1);
            }
        }, world);
    }

    @Nonnull
    private static Site site(@Nonnull String id, @Nonnull Vector3i anchor, @Nonnull Rotation yaw) {
        ConstructionDefinition definition = AetherhavenPlugin.get().getConstructionCatalog().get(id);
        require(definition != null, id + " definition missing");
        var path = PrefabResolveUtil.resolvePrefabPath(definition.getPrefabPath());
        require(path != null, id + " prefab missing");
        IPrefabBuffer prefab = PrefabBufferUtil.getCached(path);
        return new Site(definition, prefab, anchor, yaw, PlotFootprintUtil.computeFootprint(anchor, yaw, prefab, definition));
    }

    private static void buildAndCheck(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager manager,
        @Nonnull Site site,
        boolean resume
    ) {
        String id = site.definition().getId();
        UUID owner = UUID.randomUUID();
        var town = new TownRecord(UUID.randomUUID(), owner, world.getName(), site.anchor().x, site.anchor().y, site.anchor().z, 1, 1, 0);
        var plot =
            new PlotInstance(UUID.randomUUID(), id, PlotInstanceState.BLUEPRINTING, site.footprint(), site.anchor().x, site.anchor().y + 40, site.anchor().z, 0);
        plot.setBuildAsDecoration(true);
        town.getPlotInstances().add(plot);
        manager.putTown(town);
        try {
            var normal = ConstructionPasteOps.buildSequence(site.prefab(), site.yaw());
            var stripped = DecorationBuild.sequenceFor(site.prefab(), site.yaw(), plot);
            long functional = normal.pendingBlocks().stream().filter(DecorationBuildWorldCheck::isFunctional).count();
            require(functional > 0, id + " has no special blocks to leave out, so this proves nothing");
            require(stripped.pendingBlocks().stream().noneMatch(DecorationBuildWorldCheck::isFunctional), id + " kept a special block in its build list");
            if (MARKER_BUILDING.equals(id)) {
                require(stripped.prefabEntitiesInOrder().size() < normal.prefabEntitiesInOrder().size(), id + " kept its town marker entities");
            }

            PlotAssemblyBuildStartResult started =
                PlotAssemblyService.startFromBuildClick(
                    plugin, world, town, plot, new Vector3i(site.anchor().x, site.anchor().y + 40, site.anchor().z),
                    owner, site.anchor(), site.yaw(), site.definition(), site.prefab(), 0L, true
                );
            require(started == PlotAssemblyBuildStartResult.OK, id + " did not start: " + started);
            var store = world.getEntityStore().getStore();
            // A clear site has no job until the regular scan registers one from the plot.
            PlotAssemblyJob job = PlotAssemblyService.ensureAssemblyJob(world, plugin, town, plot, store);
            require(job != null, id + " has no job");
            int cells = job.pendingBlocks().size();
            if (resume) {
                AssemblyWorldRegistry.remove(world, plot.getPlotId());
                job = PlotAssemblyService.ensureAssemblyJob(world, plugin, town, plot, store);
                require(job != null, id + " could not resume");
                require(job.pendingBlocks().size() == cells, id + " changed its block list when resumed");
                require(job.pendingBlocks().stream().noneMatch(DecorationBuildWorldCheck::isFunctional), id + " got its special blocks back when resumed");
            }
            require(PlotAssemblyService.instantCompleteJob(world, plugin, store, town, plot, job), id + " did not finish");

            require(town.findPlotById(plot.getPlotId()) == null, id + " kept its plot");
            require(town.getPlotInstances().isEmpty(), id + " left a plot in the town");
            PlotFootprintRecord fp = site.footprint();
            for (int x = fp.getMinX(); x <= fp.getMaxX(); x++) {
                for (int z = fp.getMinZ(); z <= fp.getMaxZ(); z++) {
                    for (int y = fp.getMinY(); y <= fp.getMaxY(); y++) {
                        BlockType type = ChunkSectionBlockUtil.blockType(world, x, y, z);
                        require(
                            type == null || !DecorationBuild.isFunctionalBlock(type.getId()),
                            id + " has " + (type != null ? type.getId() : "") + " standing at " + x + "," + y + "," + z
                        );
                    }
                }
            }
            int expected = 0;
            int standing = 0;
            for (PendingBlock cell : stripped.pendingBlocks()) {
                if (cell.blockId() == 0 || cell.filler() != 0) {
                    continue;
                }
                expected++;
                BlockType want = BlockType.getAssetMap().getAsset(cell.blockId());
                BlockType got = ChunkSectionBlockUtil.blockType(world, site.anchor().x + cell.x(), site.anchor().y + cell.y(), site.anchor().z + cell.z());
                if (want != null && got != null && want.getId().equals(got.getId())) {
                    standing++;
                }
            }
            require(expected > 100 && standing >= expected * 0.98, id + " is missing blocks: " + standing + "/" + expected);
        } finally {
            manager.removeTown(town.getTownId());
        }
    }

    private static boolean isFunctional(@Nonnull PendingBlock cell) {
        if (cell.blockId() == 0) {
            return false;
        }
        BlockType type = BlockType.getAssetMap().getAsset(cell.blockId());
        return type != null && DecorationBuild.isFunctionalBlock(type.getId());
    }

    private static void require(boolean yes, @Nonnull String issue) {
        if (!yes) {
            throw new IllegalStateException(issue);
        }
    }

    private static void report(@Nonnull CommandContext ctx, @Nonnull String message) {
        ctx.sendMessage(Message.raw(message));
        HytaleLogger.forEnclosingClass().atInfo().log(message);
    }
}
