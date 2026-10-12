package com.hexvane.aetherhaven.command;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.blockpalette.BlockPaletteApplyService;
import com.hexvane.aetherhaven.blockpalette.BlockPaletteDefinition;
import com.hexvane.aetherhaven.blockpalette.BlockPaletteIconResolver;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownWorldFile;
import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractAsyncCommand;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.WorldConfig;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import com.hypixel.hytale.server.core.universe.world.storage.provider.EmptyChunkStorageProvider;
import com.hypixel.hytale.server.core.universe.world.worldgen.provider.VoidWorldGenProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.joml.Vector3i;

/**
 * The Goblin Iron block palettes on real blocks. Pastes core buildings in a disposable world, restyles them the way
 * the paintbrush page does, reads every block back, restores the default look, then saves and reloads the plot and
 * builds it fresh with the palette remembered. Also runs every shipped building through the paste remap without
 * touching a world. What the blocks look like is not judged here.
 */
final class AetherhavenPaletteTestCommand extends AbstractCommandCollection {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static final String STATE = "_State_Definitions_";
    private static final String ROOF_PALETTE = "roofs_goblin_iron_metal";
    private static final String BRICK_PALETTE = "bricks_goblin_iron";
    private static final String GOBLIN = "Metal_Goblin_Iron";
    private static final String GOBLIN_ROOF = "Metal_Goblin_Iron_Roof";
    /** Brick shapes the goblin set has no block for; the building keeps its own block. */
    private static final List<String> BRICKS_LEFT_ALONE = List.of("Wall", "Beam", "Pillar_Base", "Pillar_Middle", "Pillar_Top");
    /** Buildings pasted for real: the smithy (roof, brickwork, stair corners) and the town hall (roof corners, shallow roof). */
    private static final List<String> WORLD_BUILDINGS =
        List.of(AetherhavenConstants.CONSTRUCTION_PLOT_BLACKSMITH_SHOP, AetherhavenConstants.CONSTRUCTION_PLOT_TOWN_HALL);
    private static final Vector3i ANCHOR = new Vector3i(64, 128, 64);

    AetherhavenPaletteTestCommand() {
        super("palette", "Check the Goblin Iron block palettes on real buildings");
        setPermissionGroups("hytale:WorldEditor");
        addSubCommand(new Check());
    }

    private static final class Check extends AbstractAsyncCommand {
        Check() {
            super("worldcheck", "Paste, restyle, restore and rebuild core buildings with the Goblin Iron palettes");
            setPermissionGroups("hytale:WorldEditor");
        }

        @Override
        protected CompletableFuture<Void> executeAsync(CommandContext ctx) {
            if (!RUNNING.compareAndSet(false, true)) {
                report(ctx, "A palette check is already running.");
                return CompletableFuture.completedFuture(null);
            }
            String name = "aetherhaven_palette_check_" + UUID.randomUUID().toString().replace("-", "");
            var universe = Universe.get();
            var path = universe.validateWorldPath(name);
            if (Files.exists(path)) {
                RUNNING.set(false);
                return CompletableFuture.failedFuture(new IllegalStateException("Fixture already exists"));
            }
            var config = new WorldConfig();
            com.hypixel.hytale.builtin.instances.config.InstanceWorldConfig.ensureAndGet(config);
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
            Tally tally = new Tally();
            return universe.makeWorld(name, path, config)
                .thenCompose(world -> exercise(world, tally).orTimeout(180, TimeUnit.SECONDS).whenCompleteAsync((done, error) -> {
                    if (error == null) {
                        report(ctx, "Palette isolated world check: PASSED. " + tally.summary());
                    } else {
                        Throwable cause = error;
                        while (cause.getCause() != null) {
                            cause = cause.getCause();
                        }
                        report(ctx, "Palette isolated world check: FAILED: " + cause.getMessage());
                    }
                    universe.removeWorld(name);
                }))
                .whenComplete((done, error) -> RUNNING.set(false));
        }
    }

    /** One placed block as read back from the world. */
    private record Placed(String id, int rotation) {}

    /** One prefab cell: where it lands and what the building design holds there. */
    private record Cell(int x, int y, int z, String prefabId, int prefabRotation) {}

    private static final class Tally {
        int rotations;
        int unknown;
        int allBuildings;
        int allSwapped;
        int allLeftAlone;
        final Map<String, Integer> smithyPairs = new TreeMap<>();
        final Map<String, Integer> smithyLeftAlone = new TreeMap<>();
        final Map<String, Integer> townHallPairs = new TreeMap<>();
        String roofIcon = "";
        String brickIcon = "";

        String summary() {
            int roofs = 0;
            int bricks = 0;
            for (var e : smithyPairs.entrySet()) {
                if (e.getKey().contains(GOBLIN_ROOF)) {
                    roofs += e.getValue();
                } else {
                    bricks += e.getValue();
                }
            }
            int kept = smithyLeftAlone.values().stream().mapToInt(Integer::intValue).sum();
            int hall = townHallPairs.values().stream().mapToInt(Integer::intValue).sum();
            return "Blacksmith shop with Goblin Iron roof and bricks: " + (roofs + bricks) + " blocks swapped (" + roofs + " roof, "
                + bricks + " brick), " + kept + " brick pillars left as stone, town hall " + hall + " swapped, " + rotations
                + " rotations, " + unknown + " unknown blocks; every swapped block exists and keeps its rotation, shape and corner; "
                + "untouched kinds unchanged; default look restored; a saved and reloaded plot rebuilds the same. All "
                + allBuildings + " shipped buildings through the paste remap: " + allSwapped + " swapped, " + allLeftAlone
                + " left alone, no swap made an unknown block.";
        }
    }

    private static CompletableFuture<Void> exercise(World world, Tally tally) {
        var sections = new ArrayList<CompletableFuture<?>>();
        for (int x = -1; x <= 5; x++) {
            for (int z = -1; z <= 5; z++) {
                for (int y = 3; y <= 5; y++) {
                    sections.add(world.getChunkStore().getChunkSectionReferenceAsync(x, y, z, GetChunkFlags.SET_TICKING));
                }
            }
        }
        CompletableFuture<Void> chain = CompletableFuture.allOf(sections.toArray(CompletableFuture[]::new))
            .thenRunAsync(() -> checkCatalogAndGameBlocks(tally), world);
        for (String buildingId : WORLD_BUILDINGS) {
            for (Rotation yaw : Rotation.NORMAL) {
                chain = chain.thenCompose(done -> restyleInWorld(world, buildingId, yaw, tally));
            }
        }
        return chain.thenRunAsync(() -> checkEveryShippedBuilding(tally), world);
    }

    /** The picker's own view: both palettes are in the catalog, in the right rows, with a real icon. */
    private static void checkCatalogAndGameBlocks(Tally tally) {
        var plugin = AetherhavenPlugin.get();
        require(plugin != null, "Plugin missing");
        var catalog = plugin.getBlockPaletteCatalog();
        BlockPaletteDefinition roofs = catalog.get(ROOF_PALETTE);
        BlockPaletteDefinition bricks = catalog.get(BRICK_PALETTE);
        require(roofs != null && "roofs".equals(roofs.getCategory()), "Goblin Iron roof palette is not in the catalog");
        require(bricks != null && "bricks".equals(bricks.getCategory()), "Goblin Iron brick palette is not in the catalog");
        require(catalog.forCategory("roofs").contains(roofs), "Roof row does not list Goblin Iron");
        require(catalog.forCategory("bricks").contains(bricks), "Brick row does not list Goblin Iron");
        BlockPaletteDefinition iron = catalog.get("roofs_iron_metal");
        require(iron != null && catalog.forCategory("roofs").contains(iron), "Iron roof palette went missing");
        require(GOBLIN_ROOF.equals(BlockPaletteIconResolver.resolveIconBlockId(roofs)), "Roof palette icon block does not exist");
        require(GOBLIN.equals(BlockPaletteIconResolver.resolveIconBlockId(bricks)), "Brick palette icon block does not exist");
        tally.roofIcon = BlockPaletteIconResolver.resolveIconAssetPath(roofs);
        tally.brickIcon = BlockPaletteIconResolver.resolveIconAssetPath(bricks);
        require(tally.roofIcon.contains(GOBLIN_ROOF), "Roof palette icon is not the goblin roof: " + tally.roofIcon);
        require(tally.brickIcon.contains(GOBLIN), "Brick palette icon is not goblin iron: " + tally.brickIcon);
        var ids = new TreeSet<String>();
        for (String key : BlockType.getAssetMap().getAssetMap().keySet()) {
            if (key.startsWith(GOBLIN) || key.startsWith("*" + GOBLIN)) {
                ids.add(key);
            }
        }
        LOGGER.atInfo().log("Palette check game ids: %s", new Gson().toJson(ids));
        LOGGER.atInfo().log("Palette check icons: roofs=%s bricks=%s", tally.roofIcon, tally.brickIcon);
    }

    private static CompletableFuture<Void> restyleInWorld(World world, String buildingId, Rotation yaw, Tally tally) {
        var plugin = AetherhavenPlugin.get();
        ConstructionDefinition definition = plugin.getConstructionCatalog().get(buildingId);
        require(definition != null, buildingId + ": definition missing");
        Path prefabPath = PrefabResolveUtil.resolvePrefabPath(definition.getPrefabPath());
        require(prefabPath != null, buildingId + ": prefab missing");
        IPrefabBuffer prefab = PrefabBufferUtil.getCached(prefabPath);
        require(prefab != null, buildingId + ": prefab did not open");
        String tag = buildingId + "/" + yaw;
        var selections = new LinkedHashMap<String, String>();
        selections.put("roofs", ROOF_PALETTE);
        selections.put("bricks", BRICK_PALETTE);
        var cells = new ArrayList<Cell>();
        var baseline = new LinkedHashMap<Cell, Placed>();
        var restyled = new LinkedHashMap<Cell, Placed>();
        var owner = UUID.randomUUID();
        var town = new TownRecord(UUID.randomUUID(), owner, world.getName(), ANCHOR.x, ANCHOR.y, ANCHOR.z, 1, 1, 0);
        var plotHolder = new PlotInstance[1];
        return CompletableFuture.runAsync(() -> {
            var types = BlockType.getAssetMap();
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (var pb : ConstructionPasteOps.withoutPureAirCells(ConstructionPasteOps.buildSequence(prefab, yaw).pendingBlocks())) {
                if (pb.filler() != 0 || pb.blockId() == 0) {
                    continue;
                }
                BlockType type = types.getAsset(pb.blockId());
                require(type != null && !type.isUnknown(), tag + ": the building design itself holds an unknown block");
                var cell = new Cell(ANCHOR.x + pb.x(), ANCHOR.y + pb.y(), ANCHOR.z + pb.z(), type.getId(), pb.blockRotation());
                cells.add(cell);
                minX = Math.min(minX, cell.x());
                minY = Math.min(minY, cell.y());
                minZ = Math.min(minZ, cell.z());
                maxX = Math.max(maxX, cell.x());
                maxY = Math.max(maxY, cell.y());
                maxZ = Math.max(maxZ, cell.z());
            }
            require(cells.size() > 200, tag + ": building too small to be the real one");
            ConstructionPasteOps.forcePasteAllSolids(world, ANCHOR, yaw, false, prefab);
            for (Cell cell : cells) {
                Placed placed = read(world, cell);
                baseline.put(cell, placed);
                if (isRoof(cell.prefabId()) || isBrick(cell.prefabId())) {
                    require(bare(cell.prefabId()).equals(bare(placed.id())) && cell.prefabRotation() == placed.rotation(),
                        tag + ": default paste differs from the design at " + at(cell) + " (" + placed.id() + ")");
                }
            }
            var plot = new PlotInstance(UUID.randomUUID(), definition.getId(), PlotInstanceState.COMPLETE,
                new PlotFootprintRecord(minX, minY, minZ, maxX, maxY, maxZ), ANCHOR.x, ANCHOR.y, ANCHOR.z, 0);
            plot.setPrefabWorldPlacement(ANCHOR.x, ANCHOR.y, ANCHOR.z, yaw);
            town.getPlotInstances().add(plot);
            require(town.unlockBlockPalette(ROOF_PALETTE) && town.unlockBlockPalette(BRICK_PALETTE), tag + ": palettes did not unlock");
            selections.forEach(plot::setBlockPaletteSelection);
            plotHolder[0] = plot;
            // The paintbrush page's Save: restamp the finished building from its saved selections.
            BlockPaletteApplyService.applyToPlot(world, plugin, town, plot, definition);
        }, world).thenRunAsync(() -> {
            var pairs = new TreeMap<String, Integer>();
            var leftAlone = new TreeMap<String, Integer>();
            for (Cell cell : cells) {
                Placed now = read(world, cell);
                Placed before = baseline.get(cell);
                restyled.put(cell, now);
                String design = bare(cell.prefabId());
                String placed = bare(now.id());
                require(now.rotation() == before.rotation(), tag + ": rotation changed at " + at(cell) + " " + design + " -> " + placed);
                if (isRoof(design)) {
                    require(placed.startsWith(GOBLIN_ROOF), tag + ": roof not swapped at " + at(cell) + ": " + design + " -> " + placed);
                    String wanted = roofTail(design);
                    require(wanted.equals(placed.substring(GOBLIN_ROOF.length())),
                        tag + ": roof shape or corner changed at " + at(cell) + ": " + design + " -> " + placed);
                    pairs.merge(design + " -> " + placed, 1, Integer::sum);
                } else if (isBrick(design)) {
                    if (placed.equals(bare(before.id()))) {
                        String shape = design.substring(design.indexOf("_Brick_") + "_Brick_".length()).split(STATE)[0];
                        require(design.contains("_Brick_") && BRICKS_LEFT_ALONE.contains(shape),
                            tag + ": brick shape left alone that goblin iron has a block for: " + design);
                        leftAlone.merge(design, 1, Integer::sum);
                    } else {
                        require(placed.startsWith(GOBLIN) && !placed.startsWith(GOBLIN_ROOF),
                            tag + ": brick swapped to the wrong set at " + at(cell) + ": " + design + " -> " + placed);
                        require(brickTail(design).equals(placed.substring(GOBLIN.length())),
                            tag + ": brick shape or corner changed at " + at(cell) + ": " + design + " -> " + placed);
                        pairs.merge(design + " -> " + placed, 1, Integer::sum);
                    }
                } else {
                    // A row left on Default puts the design's own block back, so either reading is untouched.
                    require(now.equals(before) || placed.equals(design), tag + ": a block the palettes do not cover changed at "
                        + at(cell) + ": " + before.id() + " -> " + now.id());
                }
            }
            require(!pairs.isEmpty(), tag + ": nothing was swapped (the restyle did not run before the check)");
            tally.unknown += countUnknown(world, plotHolder[0].toFootprint());
            require(tally.unknown == 0, tag + ": unknown blocks in the restyled building");
            if (yaw == Rotation.None) {
                boolean smithy = AetherhavenConstants.CONSTRUCTION_PLOT_BLACKSMITH_SHOP.equals(buildingId);
                (smithy ? tally.smithyPairs : tally.townHallPairs).putAll(pairs);
                if (smithy) {
                    tally.smithyLeftAlone.putAll(leftAlone);
                }
                LOGGER.atInfo().log("Palette check swaps %s: %s left alone: %s", tag, new Gson().toJson(pairs), new Gson().toJson(leftAlone));
            }
            // Back to Default in every row: the building returns to its design.
            plotHolder[0].clearBlockPaletteSelections();
            BlockPaletteApplyService.applyToPlot(world, plugin, town, plotHolder[0], definition);
        }, world).thenRunAsync(() -> {
            for (Cell cell : cells) {
                Placed now = read(world, cell);
                require(now.equals(baseline.get(cell)), tag + ": default look not restored at " + at(cell) + ": "
                    + baseline.get(cell).id() + " became " + now.id());
            }
            clear(world, cells);
            // Save the town with the palette chosen, load it again, and build fresh from what was remembered.
            selections.forEach(plotHolder[0]::setBlockPaletteSelection);
            PlotInstance reloaded;
            try {
                Path file = Files.createTempFile("aetherhaven-palette-check", ".json");
                try {
                    TownWorldFile.writeBytesAtomic(file, TownWorldFile.toJsonBytes(List.of(town)));
                    TownRecord loadedTown = TownWorldFile.readOrEmpty(file).getTowns().getFirst();
                    require(loadedTown.hasBlockPaletteUnlocked(ROOF_PALETTE) && loadedTown.hasBlockPaletteUnlocked(BRICK_PALETTE),
                        tag + ": unlocked palettes were not saved");
                    reloaded = loadedTown.findPlotById(plotHolder[0].getPlotId());
                } finally {
                    Files.deleteIfExists(file);
                }
            } catch (java.io.IOException e) {
                throw new IllegalStateException(tag + ": could not save and reload the town: " + e.getMessage());
            }
            require(reloaded != null && selections.equals(reloaded.getBlockPaletteSelections()), tag + ": saved palette choices were lost");
            ConstructionPasteOps.forcePasteAllSolids(world, reloaded.resolvePrefabAnchorWorld(definition), reloaded.resolvePrefabYaw(),
                false, prefab, reloaded.getBlockPaletteSelections());
            for (Cell cell : cells) {
                require(read(world, cell).equals(restyled.get(cell)),
                    tag + ": a fresh build from the saved plot differs from the restyled one at " + at(cell));
            }
            tally.unknown += countUnknown(world, reloaded.toFootprint());
            require(tally.unknown == 0, tag + ": unknown blocks in the fresh build");
            clear(world, cells);
            tally.rotations++;
        }, world);
    }

    /** Every shipped building through the same remap the paste uses, on the live block registry. No world writes. */
    private static void checkEveryShippedBuilding(Tally tally) {
        var plugin = AetherhavenPlugin.get();
        var types = BlockType.getAssetMap();
        var selections = Map.of("roofs", ROOF_PALETTE, "bricks", BRICK_PALETTE);
        var problems = new ArrayList<String>();
        for (ConstructionDefinition definition : plugin.getConstructionCatalog().list()) {
            String prefabKey = definition.getPrefabPath();
            if (prefabKey == null || prefabKey.isBlank()) {
                continue;
            }
            Path prefabPath = PrefabResolveUtil.resolvePrefabPath(prefabKey);
            if (prefabPath == null) {
                continue;
            }
            IPrefabBuffer prefab = PrefabBufferUtil.getCached(prefabPath);
            var plain = ConstructionPasteOps.buildSequence(prefab, Rotation.None).pendingBlocks();
            var styled = ConstructionPasteOps.buildSequence(prefab, Rotation.None, selections).pendingBlocks();
            if (plain.size() != styled.size()) {
                problems.add(definition.getId() + ": the palette changed the number of cells");
                continue;
            }
            tally.allBuildings++;
            for (int i = 0; i < plain.size(); i++) {
                var a = plain.get(i);
                var b = styled.get(i);
                if (a.x() != b.x() || a.y() != b.y() || a.z() != b.z() || a.blockRotation() != b.blockRotation()
                    || a.filler() != b.filler() || a.supportValue() != b.supportValue()) {
                    problems.add(definition.getId() + ": a cell moved, turned or lost its support at " + a.x() + "," + a.y() + "," + a.z());
                    continue;
                }
                if (a.blockId() == 0) {
                    continue;
                }
                BlockType from = types.getAsset(a.blockId());
                BlockType to = types.getAsset(b.blockId());
                if (from == null || to == null || (to.isUnknown() && !from.isUnknown())) {
                    problems.add(definition.getId() + ": unknown block after the swap at " + a.x() + "," + a.y() + "," + a.z());
                    continue;
                }
                String design = bare(from.getId());
                String placed = bare(to.getId());
                boolean roof = isRoof(design);
                boolean brick = isBrick(design);
                if (a.blockId() == b.blockId()) {
                    if (roof && !design.startsWith(GOBLIN_ROOF)) {
                        problems.add(definition.getId() + ": roof not swapped: " + design);
                    } else if (brick) {
                        String shape = design.contains("_Brick_")
                            ? design.substring(design.indexOf("_Brick_") + "_Brick_".length()).split(STATE)[0] : "";
                        if (!BRICKS_LEFT_ALONE.contains(shape)) {
                            problems.add(definition.getId() + ": brick not swapped: " + design);
                        }
                        tally.allLeftAlone++;
                    }
                    continue;
                }
                tally.allSwapped++;
                if (roof) {
                    if (!placed.startsWith(GOBLIN_ROOF) || !roofTail(design).equals(placed.substring(GOBLIN_ROOF.length()))) {
                        problems.add(definition.getId() + ": roof shape or corner changed: " + design + " -> " + placed);
                    }
                } else if (brick) {
                    if (!placed.startsWith(GOBLIN) || placed.startsWith(GOBLIN_ROOF) || !brickTail(design).equals(placed.substring(GOBLIN.length()))) {
                        problems.add(definition.getId() + ": brick shape or corner changed: " + design + " -> " + placed);
                    }
                } else {
                    problems.add(definition.getId() + ": a block the palettes do not cover changed: " + design + " -> " + placed);
                }
            }
        }
        problems.stream().distinct().limit(60).forEach(p -> LOGGER.atWarning().log("Palette check: %s", p));
        require(problems.isEmpty(), problems.size() + " problems across the shipped buildings, first: " + (problems.isEmpty() ? "" : problems.getFirst()));
        require(tally.allBuildings > 30, "Only " + tally.allBuildings + " shipped buildings were found");
    }

    private static Placed read(World world, Cell cell) {
        BlockType type = ChunkSectionBlockUtil.blockType(world, cell.x(), cell.y(), cell.z());
        String id = type == null || type.getId() == null ? "" : type.getId();
        return new Placed(id, ChunkSectionBlockUtil.rotationIndex(world, cell.x(), cell.y(), cell.z()));
    }

    private static void clear(World world, List<Cell> cells) {
        for (Cell cell : cells) {
            ChunkSectionBlockUtil.setBlockEmpty(world, cell.x(), cell.y(), cell.z(), ConstructionPasteOps.SET_BLOCK_SETTINGS_CLEAR);
        }
    }

    private static int countUnknown(World world, PlotFootprintRecord footprint) {
        int unknown = 0;
        for (int x = footprint.getMinX(); x <= footprint.getMaxX(); x++) {
            for (int y = footprint.getMinY(); y <= footprint.getMaxY(); y++) {
                for (int z = footprint.getMinZ(); z <= footprint.getMaxZ(); z++) {
                    BlockType type = ChunkSectionBlockUtil.blockType(world, x, y, z);
                    if (type == null || type.isUnknown()) {
                        unknown++;
                    }
                }
            }
        }
        return unknown;
    }

    private static boolean isRoof(String id) {
        String bare = bare(id);
        return bare.contains("_Roof") && (bare.startsWith("Wood_") || bare.startsWith("Rock_") || bare.startsWith("Metal_"));
    }

    private static boolean isBrick(String id) {
        String bare = bare(id);
        return bare.startsWith("Rock_") && bare.contains("_Brick") && !bare.contains("_Brick_Roof");
    }

    /** What follows the roof name on the goblin side: the same shape and corner, with hollow dropped (metal has none). */
    private static String roofTail(String design) {
        String tail = design.substring(design.indexOf("_Roof") + "_Roof".length());
        if (tail.equals("_Hollow")) {
            return "";
        }
        return tail.startsWith("_Hollow_") ? tail.substring("_Hollow".length()) : tail;
    }

    /** What follows the block name on the goblin side: the same shape and corner, with moss dropped (metal has none). */
    private static String brickTail(String design) {
        String tail = design.substring(design.indexOf("_Brick") + "_Brick".length());
        if (tail.equals("_Mossy")) {
            return "";
        }
        return tail.startsWith("_Mossy_") ? tail.substring("_Mossy".length()) : tail;
    }

    private static String bare(String id) {
        return id.startsWith("*") ? id.substring(1) : id;
    }

    private static String at(Cell cell) {
        return cell.x() + "," + cell.y() + "," + cell.z();
    }

    private static void require(boolean yes, String issue) {
        if (!yes) {
            throw new IllegalStateException(issue);
        }
    }

    private static void report(CommandContext ctx, String message) {
        ctx.sendMessage(Message.raw(message));
        LOGGER.atInfo().log(message);
    }
}
