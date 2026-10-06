package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.plot.PlotBlockStamper;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.*;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.*;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.joml.Vector3i;

/** Opt-in native stamping check. Creates no players, registered towns or saved terrain. */
final class AetherhavenProductionStorageTestCommand extends AbstractAsyncCommand {
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final int CELL_SETTINGS = SetBlockSettings.NO_UPDATE_STATE | SetBlockSettings.NO_SET_FILLER | SetBlockSettings.NO_BREAK_FILLER;
    AetherhavenProductionStorageTestCommand() {
        super("storagemultiblock", "Check all production wardrobe cells in a disposable world");
        setPermissionGroups("hytale:WorldEditor");
    }
    @Override protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
        if (!RUNNING.compareAndSet(false, true)) return CompletableFuture.failedFuture(new IllegalStateException("Storage check is already running"));
        String name = "aetherhaven_storage_check_" + UUID.randomUUID().toString().replace("-", "");
        var universe = Universe.get();
        var path = universe.validateWorldPath(name);
        if (java.nio.file.Files.exists(path)) {
            RUNNING.set(false);
            return CompletableFuture.failedFuture(new IllegalStateException("Storage fixture already exists"));
        }
        var config = new WorldConfig();
        com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig.ensureAndGet(config);
        config.setWorldGenProvider(new com.hypixel.hytale.server.core.universe.world.worldgen.provider.VoidWorldGenProvider());
        config.setChunkStorageProvider(com.hypixel.hytale.server.core.universe.world.storage.provider.EmptyChunkStorageProvider.INSTANCE);
        config.setSavingPlayers(false);config.setCanSaveChunks(false);config.setSaveNewChunks(false);config.setSpawningNPC(false);
        config.setGameTimePaused(true);config.setBlockTicking(false);config.setDeleteOnRemove(true);config.setDeleteOnUniverseStart(true);
        return universe.makeWorld(name, path, config).thenCompose(world -> {
            var loaded = new ArrayList<CompletableFuture<?>>();
            for (int x = 0; x <= 1; x++) for (int y = 4; y <= 5; y++) for (int z = 0; z <= 1; z++)
                loaded.add(world.getChunkStore().getChunkSectionReferenceAsync(x, y, z, GetChunkFlags.SET_TICKING));
            return CompletableFuture.allOf(loaded.toArray(CompletableFuture[]::new)).thenRunAsync(() -> exercise(world), world)
                .orTimeout(30, TimeUnit.SECONDS).whenCompleteAsync((unused, error) -> {
                    universe.removeWorld(name);
                    String result = error == null ? "Production wardrobe native check: PASSED. Four rotations, all occupied cells, section boundaries, normal stamping, legacy repair, repeat repair, obstruction preservation and native key prompt."
                        : "Production wardrobe native check: FAILED: " + error;
                    ctx.sendMessage(Message.raw(result));
                    com.hypixel.hytale.logger.HytaleLogger.forEnclosingClass().atInfo().log(result);
                });
        }).whenComplete((unused, error) -> RUNNING.set(false));
    }

    private record Cell(int x, int y, int z, int filler) {}
    private record Voxel(int id, int rotation, int filler) {}
    private static void exercise(World world) {
        var type = BlockType.getAssetMap().getAsset(AetherhavenConstants.BLOCK_PRODUCTION_STORAGE);
        require(type != null, "Storage asset missing");
        require(type.getInteractionHitboxTypeIndex() == type.getHitboxTypeIndex(), "Storage interaction does not cover its native hitbox");
        String use = type.getInteractions().get(InteractionType.Use);
        require(use != null && com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction.getAssetMap().getAsset(use) != null,
            "Native storage Use chain missing");
        var i18n = com.hypixel.hytale.server.core.modules.i18n.I18nModule.get();
        String hint = type.getInteractionHint() == null ? null : i18n.getMessage(
            com.hypixel.hytale.server.core.modules.i18n.I18nModule.DEFAULT_LANGUAGE, type.getInteractionHint());
        require(hint != null && hint.contains("[{key}]"), "Native storage key prompt missing");
        var bounds = BlockBoundingBoxes.getAssetMap().getAsset(type.getHitboxTypeIndex());
        require(bounds != null, "Storage native hitbox missing");
        var base = new Vector3i(32, 159, 32);
        var definition = new com.google.gson.Gson().fromJson(
            "{\"id\":\"plot_lumbermill\",\"productionStorageLocalPos\":[0,0,0]}", ConstructionDefinition.class);
        var town = new TownRecord(UUID.randomUUID(), UUID.randomUUID(), world.getName(), base.x, base.y, base.z, 1, 1, 0);
        var plot = new PlotInstance(UUID.randomUUID(), "plot_lumbermill", PlotInstanceState.COMPLETE,
            new PlotFootprintRecord(29, 158, 29, 35, 162, 35), base.x, base.y, base.z, 0);
        town.getPlotInstances().add(plot);
        for (int x = 29; x <= 35; x++) for (int z = 29; z <= 35; z++)
            require(ChunkSectionBlockUtil.setBlockByKey(world, x, 158, z, "Rock_Stone", CELL_SETTINGS), "Fixture floor failed");
        for (Rotation yaw : Rotation.values()) {
            int rotation = RotationTuple.index(yaw, Rotation.None, Rotation.None);
            var cells = new ArrayList<Cell>();
            FillerBlockUtil.forEachFillerBlock(bounds.get(rotation), (x, y, z) ->
                cells.add(new Cell(base.x + x, base.y + y, base.z + z, FillerBlockUtil.pack(x, y, z))));
            require(ChunkSectionBlockUtil.setBlockByKey(world, base.x, base.y, base.z, type.getId(), rotation, SetBlockSettings.NO_UPDATE_STATE),
                "Native wardrobe placement failed");
            verify(world, base, cells, rotation, type);
            PlotBlockStamper.stampAllLinkedBlocks(world, town, plot, definition, base, yaw);
            verify(world, base, cells, rotation, type);
            var fillers = cells.stream().filter(cell -> cell.filler() != 0).toList();
            for (var cell : fillers) clear(world, cell);
            require(PlotBlockStamper.inspectPlotLinks(world, town, plot, definition, base, yaw).getRelinked() == 1,
                "Origin-only wardrobe was reported intact");
            require(fillers.stream().allMatch(cell -> read(world, cell).id() == BlockType.EMPTY_ID), "Read-only inspection changed terrain");
            require(PlotBlockStamper.verifyAndRepairPlot(world, town, plot, definition, base, yaw).getRelinked() == 1,
                "Existing wardrobe fillers were not repaired");
            verify(world, base, cells, rotation, type);
            require(PlotBlockStamper.verifyAndRepairPlot(world, town, plot, definition, base, yaw).getRelinked() == 0,
                "Repeated repair re-stamped intact storage");
            for (var cell : fillers) clear(world, cell);
            var obstruction = fillers.getLast();
            require(ChunkSectionBlockUtil.setBlockByKey(world, obstruction.x(), obstruction.y(), obstruction.z(), "Rock_Stone", CELL_SETTINGS),
                "Fixture obstruction failed");
            var before = cells.stream().map(cell -> read(world, cell)).toList();
            require(PlotBlockStamper.verifyAndRepairPlot(world, town, plot, definition, base, yaw).getFailed() == 1,
                "Occupied neighbor did not block storage repair");
            require(before.equals(cells.stream().map(cell -> read(world, cell)).toList()), "Blocked repair replaced a neighbor or partially wrote fillers");
            for (var cell : cells) clear(world, cell);
        }
    }
    private static void verify(World world, Vector3i base, List<Cell> cells, int rotation, BlockType type) {
        require(cells.size() == 4, "Unexpected native wardrobe footprint");
        int id = BlockType.getAssetMap().getIndex(type.getId());
        for (var cell : cells) {
            var value = read(world, cell);
            require(value.equals(new Voxel(id, rotation, cell.filler())), "Wardrobe cell missing or incorrect: " + cell);
            require(base.equals(new Vector3i(cell.x() - FillerBlockUtil.unpackX(value.filler()),
                cell.y() - FillerBlockUtil.unpackY(value.filler()), cell.z() - FillerBlockUtil.unpackZ(value.filler()))),
                "Wardrobe cell resolves to a different inventory origin");
        }
    }
    private static Voxel read(World world, Cell cell) {
        return new Voxel(ChunkSectionBlockUtil.blockId(world, cell.x(), cell.y(), cell.z()),
            ChunkSectionBlockUtil.rotationIndex(world, cell.x(), cell.y(), cell.z()), ChunkSectionBlockUtil.filler(world, cell.x(), cell.y(), cell.z()));
    }
    private static void clear(World world, Cell cell) { ChunkSectionBlockUtil.setBlockEmpty(world, cell.x(), cell.y(), cell.z(), CELL_SETTINGS); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
}
