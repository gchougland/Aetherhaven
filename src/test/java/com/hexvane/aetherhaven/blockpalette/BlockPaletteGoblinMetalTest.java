package com.hexvane.aetherhaven.blockpalette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownWorldFile;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The Goblin Iron palettes (Hytale 0.7.0): a roof palette beside the other metal roofs, and a brick palette that
 * stands the goblin metal set in for the stone brick shapes. Every swap is checked against the goblin block ids the
 * pre-release server registers ({@code blockpalette/prerelease_goblin_iron_block_ids.txt}, logged from its live block
 * registry by {@code ah test palette worldcheck}).
 */
@Tag("crossmod")
public final class BlockPaletteGoblinMetalTest {
    private static final String ROOFS = BlockPaletteConstants.CATEGORY_ROOFS;
    private static final String BRICKS = BlockPaletteConstants.CATEGORY_BRICKS;
    private static final String ROOF_FAMILY = "metal_roof:Goblin_Iron";
    private static final String BRICK_FAMILY = "metal:Goblin_Iron";
    private static final String STATE = "_State_Definitions_";
    private static final Path PALETTES = Path.of("src/main/resources/Server/Aetherhaven/BlockPalettes/palettes.json");
    private static final Path PREFABS = Path.of("src/main/resources/Server/Prefabs");
    private static final Pattern BLOCK_NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");

    /** Brick shapes the goblin set has no block for. The building keeps its own block there. */
    private static final List<String> BRICK_SHAPES_LEFT_ALONE = List.of("Wall", "Beam", "Pillar_Base", "Pillar_Middle", "Pillar_Top");

    private static final Set<String> GAME_IDS = loadGameIds();
    private static final Predicate<String> IN_GAME = GAME_IDS::contains;

    @Test
    void shippedPalettesPointAtGoblinIronBlocksThatExist() throws IOException {
        Map<String, JsonObject> byId = new LinkedHashMap<>();
        Map<String, String> categoryOf = new LinkedHashMap<>();
        JsonObject root = new Gson().fromJson(Files.readString(PALETTES), JsonObject.class);
        for (JsonElement category : root.getAsJsonArray("categories")) {
            String categoryId = category.getAsJsonObject().get("id").getAsString();
            for (JsonElement palette : category.getAsJsonObject().getAsJsonArray("palettes")) {
                String id = palette.getAsJsonObject().get("id").getAsString();
                assertFalse(byId.containsKey(id), "Palette id used twice: " + id);
                byId.put(id, palette.getAsJsonObject());
                categoryOf.put(id, categoryId);
            }
        }

        JsonObject roofs = byId.get("roofs_goblin_iron_metal");
        assertNotNull(roofs, "Goblin Iron roof palette is missing");
        assertEquals(ROOFS, categoryOf.get("roofs_goblin_iron_metal"));
        assertEquals(ROOF_FAMILY, roofs.get("familyKey").getAsString());
        assertEquals("Goblin Iron", roofs.get("displayName").getAsString());
        assertEquals("Metal_Goblin_Iron_Roof", roofs.get("iconBlockId").getAsString());
        assertTrue(GAME_IDS.contains(roofs.get("iconBlockId").getAsString()));

        JsonObject bricks = byId.get("bricks_goblin_iron");
        assertNotNull(bricks, "Goblin Iron brick palette is missing");
        assertEquals(BRICKS, categoryOf.get("bricks_goblin_iron"));
        assertEquals(BRICK_FAMILY, bricks.get("familyKey").getAsString());
        assertEquals("Goblin Iron", bricks.get("displayName").getAsString());
        assertEquals("Metal_Goblin_Iron", bricks.get("iconBlockId").getAsString());
        assertTrue(GAME_IDS.contains(bricks.get("iconBlockId").getAsString()));

        // The older metal roofs sit in the same list in the same form, so the new one is offered the same way.
        assertEquals("metal_roof:Iron", byId.get("roofs_iron_metal").get("familyKey").getAsString());
        assertEquals(ROOFS, categoryOf.get("roofs_iron_metal"));
    }

    @Test
    void goblinRoofsParseWithTheirTwoWordMetalName() {
        var plain = BlockPaletteRemapper.parse("Metal_Goblin_Iron_Roof");
        assertNotNull(plain);
        assertEquals(ROOFS, plain.category());
        assertEquals(ROOF_FAMILY, plain.familyKey());
        assertEquals("", plain.suffix());

        var corner = BlockPaletteRemapper.parse("*Metal_Goblin_Iron_Roof_Steep_State_Definitions_Inverted_Corner_Left");
        assertNotNull(corner);
        assertEquals(ROOF_FAMILY, corner.familyKey());
        assertEquals("Steep_State_Definitions_Inverted_Corner_Left", corner.suffix());

        // Plain goblin blocks are not roofs and are never mistaken for a palette category of their own.
        assertNull(BlockPaletteRemapper.parse("Metal_Goblin_Iron"));
        assertNull(BlockPaletteRemapper.parse("Metal_Goblin_Iron_Stairs"));
    }

    @Test
    void everyRoofShapeRebuildsToTheSameGoblinShape() {
        String[] sources = {"Wood_Softwood", "Wood_Blackwood", "Rock_Stone_Cobble", "Rock_Sandstone_Red_Brick", "Metal_Iron", "Metal_Orbis_Copper"};
        String[] shapes = {"", "_Flat", "_Shallow", "_Steep"};
        for (String source : sources) {
            for (String shape : shapes) {
                assertRoof(source + "_Roof" + shape, "Metal_Goblin_Iron_Roof" + shape);
            }
        }
    }

    @Test
    void roofCornersAndRidgeKeepTheirConnection() {
        String[] corners = {"Corner_Left", "Corner_Right", "Inverted_Corner_Left", "Inverted_Corner_Right"};
        for (String shape : new String[] {"", "_Shallow", "_Steep"}) {
            for (String corner : corners) {
                String tail = shape + STATE + corner;
                assertRoof("*Wood_Softwood_Roof" + tail, "*Metal_Goblin_Iron_Roof" + tail);
                assertRoof("Wood_Softwood_Roof" + tail, "*Metal_Goblin_Iron_Roof" + tail);
                assertRoof("*Rock_Stone_Brick_Roof" + tail, "*Metal_Goblin_Iron_Roof" + tail);
            }
        }
        assertRoof("*Wood_Softwood_Roof" + STATE + "Topper", "*Metal_Goblin_Iron_Roof" + STATE + "Topper");
    }

    @Test
    void hollowRoofsBecomeTheSolidGoblinSlopeAndKeepTheirCorner() {
        // No metal roof has a hollow twin, so the nearest shape that keeps the outline is the solid slope.
        assertRoof("Wood_Softwood_Roof_Hollow", "Metal_Goblin_Iron_Roof");
        assertRoof("Rock_Basalt_Cobble_Roof_Hollow", "Metal_Goblin_Iron_Roof");
        assertRoof(
            "*Wood_Softwood_Roof_Hollow" + STATE + "Inverted_Corner_Left",
            "*Metal_Goblin_Iron_Roof" + STATE + "Inverted_Corner_Left");
        assertRoof("*Wood_Softwood_Roof_Hollow" + STATE + "Corner_Right", "*Metal_Goblin_Iron_Roof" + STATE + "Corner_Right");
        // Ids from before the roof corner migration still land on a real goblin block.
        assertRoof("Wood_Softwood_Roof_Hollow_Corner", "*Metal_Goblin_Iron_Roof" + STATE + "Corner_Right");
    }

    @Test
    void aRoofStateGoblinLacksFallsBackToTheShapeItself() {
        // Only the plain slope has a ridge topper in the metal sets.
        assertRoof("*Wood_Softwood_Roof_Shallow" + STATE + "Topper", "Metal_Goblin_Iron_Roof_Shallow");
        assertFalse(GAME_IDS.contains("*Metal_Goblin_Iron_Roof_Shallow" + STATE + "Topper"));
    }

    @Test
    void goblinRoofsSwapBackToAnyOtherRoof() {
        assertEquals(
            "Wood_Softwood_Roof_Steep",
            BlockPaletteRemapper.rebuild(BlockPaletteRemapper.parse("Metal_Goblin_Iron_Roof_Steep"), "wood:Softwood"));
        assertEquals(
            "Metal_Iron_Roof_State_Definitions_Corner_Left",
            BlockPaletteRemapper.rebuild(
                BlockPaletteRemapper.parse("*Metal_Goblin_Iron_Roof" + STATE + "Corner_Left"), "metal_roof:Iron"));
        // Already goblin: nothing to do.
        assertEquals(
            "Metal_Goblin_Iron_Roof_Flat",
            BlockPaletteRemapper.remapWithFamily("Metal_Goblin_Iron_Roof_Flat", ROOFS, ROOF_FAMILY, IN_GAME));
    }

    @Test
    void everyBrickShapeRebuildsToTheSameGoblinShape() {
        String[] rocks = {"Stone", "Sandstone_Red", "Magma_Cooled", "Runic_Blue", "Gold"};
        String[] shapes = {"", "_Smooth", "_Ornate", "_Decorative", "_Half", "_Smooth_Half", "_Stairs"};
        for (String rock : rocks) {
            for (String shape : shapes) {
                assertBrick("Rock_" + rock + "_Brick" + shape, "Metal_Goblin_Iron" + shape);
            }
        }
    }

    @Test
    void brickStairCornersAndDoubleSlabsKeepTheirState() {
        for (String corner : new String[] {"Corner_Left", "Corner_Right", "Inverted_Corner_Left", "Inverted_Corner_Right"}) {
            String tail = "_Stairs" + STATE + corner;
            assertBrick("*Rock_Stone_Brick" + tail, "*Metal_Goblin_Iron" + tail);
            assertBrick("Rock_Stone_Brick" + tail, "*Metal_Goblin_Iron" + tail);
        }
        assertBrick("*Rock_Stone_Brick_Half" + STATE + "Block", "*Metal_Goblin_Iron_Half" + STATE + "Block");
        assertBrick("*Rock_Stone_Brick_Smooth_Half" + STATE + "Block", "*Metal_Goblin_Iron_Smooth_Half" + STATE + "Block");
    }

    @Test
    void mossyBricksBecomePlainGoblinIron() {
        assertBrick("Rock_Stone_Brick_Mossy", "Metal_Goblin_Iron");
        assertBrick("Rock_Stone_Brick_Mossy_Half", "Metal_Goblin_Iron_Half");
        assertBrick("Rock_Stone_Brick_Mossy_Stairs", "Metal_Goblin_Iron_Stairs");
    }

    @Test
    void brickShapesGoblinLacksAreLeftAsTheBuildingHasThem() {
        for (String shape : BRICK_SHAPES_LEFT_ALONE) {
            String id = "Rock_Stone_Brick_" + shape;
            assertEquals(id, BlockPaletteRemapper.remapWithFamily(id, BRICKS, BRICK_FAMILY, IN_GAME), id);
        }
        String wallCorner = "*Rock_Stone_Brick_Wall" + STATE + "Corner";
        assertEquals(wallCorner, BlockPaletteRemapper.remapWithFamily(wallCorner, BRICKS, BRICK_FAMILY, IN_GAME));
    }

    @Test
    void eachGoblinPaletteLeavesEveryOtherKindOfBlockAlone() {
        String[] others = {
            "Rock_Stone_Cobble",
            "Rock_Stone_Cobble_Mossy",
            "Rock_Stone_Cobble_Stairs",
            "Rock_Stone",
            "Wood_Softwood_Planks",
            "Wood_Softwood_Stairs",
            "*Wood_Softwood_Fence_Gate_State_Definitions_CloseDoorOut",
            "Wood_Beech_Trunk",
            "*Wood_Village_Wall_Full_State_Definitions_Bottom",
            "Cloth_Roof_Green",
            "Cloth_Block_Wool_Red",
            "Furniture_Village_Window",
            "Bench_Weapon",
            "Deco_Iron_Bars",
            "Metal_Iron_Fence",
            "Metal_Iron",
            "Aetherhaven_Management_Block",
            "Empty"
        };
        for (String id : others) {
            assertEquals(id, BlockPaletteRemapper.remapWithFamily(id, ROOFS, ROOF_FAMILY, IN_GAME), id);
            assertEquals(id, BlockPaletteRemapper.remapWithFamily(id, BRICKS, BRICK_FAMILY, IN_GAME), id);
        }
        // A brick roof is a roof: only the roof palette moves it. A brick wall block is a brick: only the brick palette.
        assertEquals("Rock_Stone_Brick_Roof", BlockPaletteRemapper.remapWithFamily("Rock_Stone_Brick_Roof", BRICKS, BRICK_FAMILY, IN_GAME));
        assertEquals("Rock_Stone_Brick", BlockPaletteRemapper.remapWithFamily("Rock_Stone_Brick", ROOFS, ROOF_FAMILY, IN_GAME));
        // The metal key belongs to bricks only; it does nothing as a cobble or roof family.
        assertEquals("Rock_Stone_Cobble", BlockPaletteRemapper.remapWithFamily("Rock_Stone_Cobble", BlockPaletteConstants.CATEGORY_COBBLE, BRICK_FAMILY, IN_GAME));
        assertEquals("Wood_Softwood_Roof", BlockPaletteRemapper.remapWithFamily("Wood_Softwood_Roof", ROOFS, BRICK_FAMILY, IN_GAME));
    }

    @Test
    void stoneBrickPalettesStillRebuildAsBefore() {
        var brick = BlockPaletteRemapper.parse("Rock_Quartzite_Brick_Stairs");
        assertNotNull(brick);
        assertEquals("Rock_Stone_Brick_Stairs", BlockPaletteRemapper.rebuild(brick, "Stone"));
        assertEquals("Rock_Sandstone_Red_Brick", BlockPaletteRemapper.rebuild(BlockPaletteRemapper.parse("Rock_Stone_Brick"), "Sandstone_Red"));
    }

    @Test
    void blacksmithShopSwapsItsWholeRoofAndItsBrickwork() throws IOException {
        Map<String, Integer> blocks = blockCounts(PREFABS.resolve("Blacksmith_Shop.prefab.json"));
        Map<String, Integer> roofSwaps = new TreeMap<>();
        Map<String, Integer> brickSwaps = new TreeMap<>();
        Map<String, Integer> leftAlone = new TreeMap<>();
        for (var e : blocks.entrySet()) {
            String id = e.getKey();
            String roof = BlockPaletteRemapper.remapWithFamily(id, ROOFS, ROOF_FAMILY, IN_GAME);
            String brick = BlockPaletteRemapper.remapWithFamily(id, BRICKS, BRICK_FAMILY, IN_GAME);
            if (!roof.equals(id)) {
                roofSwaps.merge(bare(id) + " -> " + bare(roof), e.getValue(), Integer::sum);
            } else if (!brick.equals(id)) {
                brickSwaps.merge(bare(id) + " -> " + bare(brick), e.getValue(), Integer::sum);
            } else {
                var parsed = BlockPaletteRemapper.parse(id);
                if (parsed != null && (ROOFS.equals(parsed.category()) || BRICKS.equals(parsed.category()))) {
                    leftAlone.merge(bare(id), e.getValue(), Integer::sum);
                }
            }
        }
        assertEquals(
            Map.of(
                "Wood_Softwood_Roof -> Metal_Goblin_Iron_Roof", 95,
                "Wood_Softwood_Roof_Hollow -> Metal_Goblin_Iron_Roof", 32,
                "Wood_Softwood_Roof_Flat -> Metal_Goblin_Iron_Roof_Flat", 16,
                "Rock_Stone_Brick_Roof_Flat -> Metal_Goblin_Iron_Roof_Flat", 1),
            roofSwaps);
        assertEquals(
            Map.of(
                "Rock_Stone_Brick -> Metal_Goblin_Iron", 30,
                "Rock_Stone_Brick_Stairs -> Metal_Goblin_Iron_Stairs", 3,
                "Rock_Stone_Brick_Stairs_State_Definitions_Corner_Left -> Metal_Goblin_Iron_Stairs_State_Definitions_Corner_Left", 1,
                "Rock_Stone_Brick_Stairs_State_Definitions_Corner_Right -> Metal_Goblin_Iron_Stairs_State_Definitions_Corner_Right", 1,
                "Rock_Magma_Cooled_Brick -> Metal_Goblin_Iron", 2,
                "Rock_Magma_Cooled_Brick_Half -> Metal_Goblin_Iron_Half", 1),
            brickSwaps);
        assertEquals(Map.of("Rock_Stone_Brick_Pillar_Base", 2, "Rock_Stone_Brick_Pillar_Middle", 2), leftAlone);
    }

    /** Every roof and brick block in every shipped building lands on a block the game has, in the same shape. */
    @Test
    void everyGoblinTargetInEveryShippedBuildingExistsInTheGame() throws IOException {
        Set<String> ids = new TreeSet<>();
        try (Stream<Path> walk = Files.walk(PREFABS)) {
            for (Path prefab : walk.filter(p -> p.getFileName().toString().endsWith(".prefab.json")).toList()) {
                ids.addAll(blockCounts(prefab).keySet());
            }
        }
        int roofs = 0;
        int bricks = 0;
        Set<String> untouchedBricks = new TreeSet<>();
        List<String> problems = new ArrayList<>();
        for (String id : ids) {
            var parsed = BlockPaletteRemapper.parse(id);
            if (parsed == null) {
                continue;
            }
            if (ROOFS.equals(parsed.category())) {
                String out = BlockPaletteRemapper.remapWithFamily(id, ROOFS, ROOF_FAMILY, IN_GAME);
                roofs++;
                if (out.equals(id) && !bare(id).startsWith("Metal_Goblin_Iron_Roof")) {
                    problems.add("roof left as it was: " + id);
                } else if (!GAME_IDS.contains(out)) {
                    problems.add("roof target is not a game block: " + id + " -> " + out);
                } else if (!roofTail(parsed.suffix()).equals(bare(out).substring("Metal_Goblin_Iron_Roof".length()))) {
                    problems.add("roof changed shape or lost its corner: " + id + " -> " + out);
                }
            } else if (BRICKS.equals(parsed.category())) {
                String out = BlockPaletteRemapper.remapWithFamily(id, BRICKS, BRICK_FAMILY, IN_GAME);
                bricks++;
                if (out.equals(id)) {
                    untouchedBricks.add(bare(id).substring(bare(id).indexOf("_Brick_") + "_Brick_".length()).split(STATE)[0]);
                } else if (!GAME_IDS.contains(out)) {
                    problems.add("brick target is not a game block: " + id + " -> " + out);
                } else {
                    String wanted = "Metal_Goblin_Iron" + ("".equals(parsed.suffix()) ? "" : "_" + parsed.suffix());
                    wanted = BlockPaletteRemapper.stripMossySegments(wanted);
                    if (!wanted.equals(bare(out))) {
                        problems.add("brick changed shape or lost its corner: " + id + " -> " + out);
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
        assertTrue(roofs > 15 && bricks > 10, "Too few roof (" + roofs + ") or brick (" + bricks + ") ids found in the shipped buildings");
        assertTrue(BRICK_SHAPES_LEFT_ALONE.containsAll(untouchedBricks), "Unexpected brick shapes left alone: " + untouchedBricks);
    }

    /** A town saved with the goblin look unlocked and chosen on a building reads back with both intact. */
    @Test
    void aSavedTownRemembersTheGoblinPalettes(@TempDir Path temp) throws IOException {
        var town = new TownRecord(UUID.randomUUID(), UUID.randomUUID(), "default", 10, 64, 20, 1, 4, 12345L);
        var plot = new PlotInstance(
            UUID.randomUUID(), "plot_blacksmith_shop", PlotInstanceState.COMPLETE, new PlotFootprintRecord(0, 60, 0, 20, 80, 20), 10, 64, 10, 0L);
        plot.setBlockPaletteSelection(ROOFS, "roofs_goblin_iron_metal");
        plot.setBlockPaletteSelection(BRICKS, "bricks_goblin_iron");
        town.getPlotInstances().add(plot);
        assertTrue(town.unlockBlockPalette("roofs_goblin_iron_metal"));
        assertTrue(town.unlockBlockPalette("bricks_goblin_iron"));

        Path file = temp.resolve("towns.json");
        TownWorldFile.writeBytesAtomic(file, TownWorldFile.toJsonBytes(List.of(town)));
        TownRecord restored = TownWorldFile.readOrEmpty(file).getTowns().getFirst();

        assertTrue(restored.hasBlockPaletteUnlocked("roofs_goblin_iron_metal"));
        assertTrue(restored.hasBlockPaletteUnlocked("bricks_goblin_iron"));
        PlotInstance restoredPlot = restored.findPlotById(plot.getPlotId());
        assertNotNull(restoredPlot);
        assertEquals(
            Map.of(ROOFS, "roofs_goblin_iron_metal", BRICKS, "bricks_goblin_iron"), restoredPlot.getBlockPaletteSelections());
    }

    /** Guards the id list itself: when the pre-release game is installed, its goblin set must be the one listed. */
    @Test
    void theIdListMatchesTheInstalledPreReleaseGame() throws IOException {
        String appData = System.getenv("APPDATA");
        assumeTrue(appData != null && !appData.isBlank(), "No APPDATA: not a Windows install");
        Path zip = Path.of(appData, "Hytale", "install", "pre-release", "package", "game", "latest", "Assets.zip");
        assumeTrue(Files.isRegularFile(zip), "Pre-release Assets.zip is not installed here");
        Set<String> inZip = new TreeSet<>();
        String prefix = "Server/Item/Items/Metal/Goblin_Iron/";
        try (ZipFile file = new ZipFile(zip.toFile())) {
            file.stream()
                .map(e -> e.getName())
                .filter(n -> n.startsWith(prefix) && n.endsWith(".json"))
                .forEach(n -> inZip.add(n.substring(prefix.length(), n.length() - ".json".length())));
        }
        Set<String> listed = new TreeSet<>();
        for (String id : GAME_IDS) {
            if (id.startsWith("Metal_Goblin_Iron")) {
                listed.add(id);
            }
        }
        assertEquals(inZip, listed);
    }

    private static void assertRoof(String source, String expected) {
        assertEquals(expected, BlockPaletteRemapper.remapWithFamily(source, ROOFS, ROOF_FAMILY, IN_GAME), source);
        assertTrue(GAME_IDS.contains(expected), "Not a game block: " + expected);
    }

    private static void assertBrick(String source, String expected) {
        assertEquals(expected, BlockPaletteRemapper.remapWithFamily(source, BRICKS, BRICK_FAMILY, IN_GAME), source);
        assertTrue(GAME_IDS.contains(expected), "Not a game block: " + expected);
    }

    /** The roof tail a source shape should land on: hollow has no metal twin, everything else carries over. */
    private static String roofTail(String suffix) {
        String shape = BlockPaletteRemapper.stripHollowRoofShape(suffix);
        return shape.isEmpty() ? "" : "_" + shape;
    }

    private static String bare(String id) {
        return id.startsWith("*") ? id.substring(1) : id;
    }

    private static Map<String, Integer> blockCounts(Path prefab) throws IOException {
        Map<String, Integer> counts = new TreeMap<>();
        try (BufferedReader reader = Files.newBufferedReader(prefab, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.indexOf("\"name\"") < 0) {
                    continue;
                }
                Matcher m = BLOCK_NAME.matcher(line);
                while (m.find()) {
                    counts.merge(m.group(1), 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    private static Set<String> loadGameIds() {
        Set<String> ids = new HashSet<>();
        try (InputStream in = BlockPaletteGoblinMetalTest.class.getResourceAsStream("/blockpalette/prerelease_goblin_iron_block_ids.txt")) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource blockpalette/prerelease_goblin_iron_block_ids.txt");
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                String id = line.trim();
                if (!id.isEmpty() && !id.startsWith("#")) {
                    ids.add(id);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return Set.copyOf(ids);
    }
}
