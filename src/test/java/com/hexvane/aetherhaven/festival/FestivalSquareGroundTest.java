package com.hexvane.aetherhaven.festival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import com.hexvane.aetherhaven.festival.FestivalPrefabSwapService.SwapPlan;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.PrefabRotation;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.joml.Vector3i;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * P48, as the owner ruled it: building the festival square never asks for the ground under its paving to be dug out;
 * every festival layout keeps its foundation, and the game lays it by itself when that festival starts; ending a
 * festival or removing the square touches nothing under the paving, so no cavity is ever left.
 *
 * <p>Reads the shipped prefabs and runs the code assembly and the swap use to decide which cells they touch
 * ({@link ConstructionPasteOps#withReservedFestivalAirCells} and {@link FestivalPrefabSwapService#plan}). The block
 * writes themselves need a world and are not exercised here.
 */
@Tag("town")
final class FestivalSquareGroundTest {
    private static final Path RESOURCES = Path.of("src", "main", "resources", "Server");
    private static final Path FESTIVAL_PREFABS = RESOURCES.resolve(Path.of("Prefabs", "Festivals"));
    private static final Path BUILDINGS = RESOURCES.resolve(Path.of("Aetherhaven", "Buildings"));
    private static final String EVERYDAY = "Festival_Square.prefab.json";
    private static final int FLOOR = FestivalPrefabSize.FLOOR_LOCAL_Y;
    private static final int CELLS_PER_LAYER = FestivalPrefabSize.WIDTH_X * FestivalPrefabSize.DEPTH_Z;
    private static final int CELLS_UNDER_FLOOR = CELLS_PER_LAYER * (FLOOR - FestivalPrefabSize.LOCAL_MIN_Y);
    private static final Vector3i ORIGIN = new Vector3i(0, 0, 0);
    private static final int[][] SIDES_AND_BELOW = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 }, { 0, -1, 0 } };

    // ---- Building the square ----

    @Test
    void theEverydaySquareHasItsWholeFloorOnTheFloorLayerAndNothingUnderIt() throws IOException {
        Layout square = Layout.read(EVERYDAY);
        assertEquals(CELLS_PER_LAYER, square.blocksOnLayer(FLOOR), "the floor layer of the everyday square is complete");
        assertEquals(0, square.cellsUnderFloor().size(), "the everyday square lists nothing under its floor");
    }

    @Test
    void buildingTheSquareAsksForNothingUnderTheFloorToBeCleared() throws IOException {
        JsonObject definition = readJson(BUILDINGS.resolve("plot_festival_square.json"));
        String prefabKey = definition.get("prefabPath").getAsString();
        assertEquals(CustomFestivalPaths.BASE_PREFAB_PATH, prefabKey, "the square is built from the everyday prefab");
        Layout square = Layout.read(EVERYDAY);
        int layersFromFloorUp = FestivalPrefabSize.LOCAL_MAX_Y - FLOOR + 1;
        for (PrefabRotation rotation : PrefabRotation.VALUES) {
            // The cells assembly must find empty before it places anything: PlotAssemblyService builds its clearing
            // footprint with exactly this call.
            List<PendingBlock> mustBeClear =
                ConstructionPasteOps.withReservedFestivalAirCells(square.cells(rotation), rotation, prefabKey);
            assertEquals(
                0,
                mustBeClear.stream().filter(cell -> cell.y() < FLOOR).count(),
                "building the square at " + rotation + " asks for ground under the paving to be dug out"
            );
            assertEquals(
                (long) CELLS_PER_LAYER * layersFromFloorUp,
                mustBeClear.stream().filter(cell -> cell.y() >= FLOOR).count(),
                "building the square at " + rotation + " still clears the whole square from the paving up"
            );
        }
    }

    @Test
    void theBuildCostOfTheSquareHasNoFoundationInIt() throws IOException {
        JsonObject exact = readJson(BUILDINGS.resolve(Path.of("PrefabMaterials", "plot_festival_square.json")));
        assertEquals(CustomFestivalPaths.BASE_PREFAB_PATH, exact.get("prefabPath").getAsString());
        for (JsonElement element : exact.getAsJsonArray("materials")) {
            String line = element.toString();
            assertFalse(line.contains("Soil"), "the exact build cost of the square still charges for a foundation: " + line);
        }
        // Festival layouts are swapped in by the game, never built or paid for: none of them has a cost list.
        try (Stream<Path> lists = Files.list(BUILDINGS.resolve("PrefabMaterials"))) {
            for (Path list : lists.filter(p -> p.toString().endsWith(".json")).toList()) {
                JsonObject cost = readJson(list);
                String prefab = cost.has("prefabPath") ? cost.get("prefabPath").getAsString() : "";
                assertTrue(
                    !prefab.startsWith("Festivals/") || prefab.equals(CustomFestivalPaths.BASE_PREFAB_PATH),
                    list.getFileName() + " charges materials for the festival layout " + prefab
                );
            }
        }
    }

    // ---- The festival layouts ----

    @Test
    void everyFestivalLayoutKeepsItsFoundation() throws IOException {
        List<Layout> festivals = festivalLayouts();
        assertEquals(8, festivals.size(), "eight festival layouts");
        int outerFaceCells = (2 * FestivalPrefabSize.WIDTH_X + 2 * FestivalPrefabSize.DEPTH_Z - 4) * FLOOR;
        for (Layout layout : festivals) {
            assertEquals(CELLS_PER_LAYER, layout.blocksOnLayer(0), layout.file + " has lost the bottom layer of its foundation");
            long outer = layout.blocks.keySet().stream().filter(c -> c.y < FLOOR && onOuterFace(c)).count();
            assertEquals(outerFaceCells, outer, layout.file + " has holes in the outer faces of its foundation");
            assertTrue(
                layout.cellsUnderFloor().size() >= CELLS_UNDER_FLOOR - 1,
                layout.file + " lists only " + layout.cellsUnderFloor().size() + " of " + CELLS_UNDER_FLOOR + " cells under its floor"
            );
        }
        Layout market = Layout.read("Festival_Market.prefab.json");
        assertTrue(
            market.fluids.keySet().stream().anyMatch(c -> c.y < FLOOR),
            "the Market pond has lost its bowl: no water under the floor"
        );
        assertTrue(
            market.blocks.entrySet().stream().anyMatch(e -> e.getKey().y < FLOOR && e.getValue().startsWith("Soil_Grass")),
            "the Market pond has lost its grass lining"
        );
    }

    @Test
    void pondsAreSealedByTheLayoutsOwnFoundation() throws IOException {
        // Water at or under the paving must be held by the layout itself on four sides and below, so a pond keeps its
        // water wherever the square stands, even where the paving overhangs a slope.
        for (Layout layout : festivalLayouts()) {
            for (Vector3i water : layout.fluids.keySet()) {
                if (water.y > FLOOR) {
                    continue;
                }
                for (int[] d : SIDES_AND_BELOW) {
                    Vector3i next = new Vector3i(water.x + d[0], water.y + d[1], water.z + d[2]);
                    assertTrue(
                        layout.fluids.containsKey(next) || isWatertight(layout.blocks.get(next)),
                        layout.file + ": water at " + water + " can run out through " + next
                    );
                }
            }
        }
    }

    // ---- Swaps and removal ----

    @Test
    void aFestivalStartLaysTheLayoutsFoundationAndEmptiesOnlyFromTheFloorUp() throws IOException {
        Vector3i anchor = new Vector3i(100, 64, -40);
        for (Layout layout : festivalLayouts()) {
            for (PrefabRotation rotation : PrefabRotation.VALUES) {
                Rotation yaw = rotation.getRotation();
                PlotFootprintRecord stored = FestivalPrefabSize.footprintAt(anchor, yaw);
                List<PendingBlock> incoming = layout.cells(rotation);
                SwapPlan plan = FestivalPrefabSwapService.plan(anchor, yaw, stored, incoming);
                String at = layout.file + " at " + rotation;

                assertEquals(anchor.y + FLOOR, plan.emptied().getMinY(), at + ": a swap empties nothing under the floor");
                assertEquals(anchor.y + FestivalPrefabSize.LOCAL_MAX_Y, plan.emptied().getMaxY(), at);
                // Exactly the cells the layout lists under the floor are cleared and rewritten, no more and no fewer.
                Set<Vector3i> listed = new HashSet<>();
                for (PendingBlock cell : incoming) {
                    if (cell.y() < FLOOR) {
                        listed.add(new Vector3i(cell.x(), cell.y(), cell.z()));
                    }
                }
                Set<Vector3i> written = new HashSet<>();
                for (PendingBlock cell : plan.underFloorWritten()) {
                    written.add(new Vector3i(cell.x(), cell.y(), cell.z()));
                }
                assertEquals(layout.cellsUnderFloor().size(), written.size(), at + ": foundation cells laid by the swap");
                assertEquals(listed, written, at + ": the swap lays exactly the foundation the layout lists");
                // Whoever stands where the foundation goes is moved out first: the whole plot box counts as occupied.
                assertEquals(anchor.y, plan.occupied().getMinY(), at + ": villagers and props under the paving are moved out");
                assertEquals(stored.getMinX(), plan.occupied().getMinX(), at);
                assertEquals(stored.getMaxX(), plan.occupied().getMaxX(), at);
                assertEquals(stored.getMinZ(), plan.occupied().getMinZ(), at);
                assertEquals(stored.getMaxZ(), plan.occupied().getMaxZ(), at);
            }
        }
    }

    @Test
    void aFestivalEndAndARemovalTouchNothingUnderTheFloor() throws IOException {
        Vector3i anchor = new Vector3i(100, 64, -40);
        Layout square = Layout.read(EVERYDAY);
        for (PrefabRotation rotation : PrefabRotation.VALUES) {
            Rotation yaw = rotation.getRotation();
            // Plots saved before 4.0 and plots built now both store the whole reserved box, five layers under the floor.
            PlotFootprintRecord stored = FestivalPrefabSize.footprintAt(anchor, yaw);
            assertEquals(anchor.y, stored.getMinY(), "the stored plot box still starts at the anchor");

            SwapPlan back = FestivalPrefabSwapService.plan(anchor, yaw, stored, square.cells(rotation));
            assertTrue(back.underFloorWritten().isEmpty(), "swapping back to the everyday square writes under the floor");
            assertEquals(anchor.y + FLOOR, back.emptied().getMinY(), "swapping back empties ground under the floor");
            assertEquals(anchor.y + FLOOR, back.occupied().getMinY(), "swapping back reaches under the floor");
            assertFalse(back.emptied().containsBlock(anchor.x, anchor.y + FLOOR - 1, anchor.z));
            assertTrue(back.emptied().containsBlock(anchor.x, anchor.y + FLOOR, anchor.z));

            // Removing the square (TownDissolutionService) clears this box and nothing else.
            PlotFootprintRecord removed = FestivalPrefabSize.fromFloorUp(stored, anchor.y);
            assertEquals(anchor.y + FLOOR, removed.getMinY(), "removing the square digs under the floor");
            assertEquals(stored.getMaxY(), removed.getMaxY());
        }
    }

    @Test
    void aSquareOnASlopeLivesThroughItsFestivalsWithoutDiggingOrCavities() throws IOException {
        // Half of the square stands on stone, the other half overhangs open air.
        Site site = Site.withGround(c -> c.x < 0 ? "Rock_Stone" : null);
        Map<Vector3i, String> ground = site.blocksUnderFloor();
        Layout square = Layout.read(EVERYDAY);

        site.build(square);
        assertEquals(ground, site.blocksUnderFloor(), "building the square changed the ground under it");
        assertEquals(CELLS_PER_LAYER, site.blocksOnLayer(FLOOR), "the paving is complete");

        // First festival: the game lays the Market foundation by itself, under the overhang too, and the pond holds.
        Layout market = Layout.read("Festival_Market.prefab.json");
        site.swapTo(market);
        assertEquals(market.blocksUnderFloor(), site.blocksUnderFloor(), "the Market foundation is laid under the square");
        assertEquals(market.fluidsUnderFloor(), site.fluidsUnderFloor(), "the Market pond has its water");
        site.assertNoWaterCanRunOut("during the Market festival");

        // The festival ends: paving back, the fill stays hidden under it, no hole and no water getting out.
        Map<Vector3i, String> afterMarket = site.blocksUnderFloor();
        Map<Vector3i, String> waterAfterMarket = site.fluidsUnderFloor();
        site.swapTo(square);
        assertEquals(afterMarket, site.blocksUnderFloor(), "ending the festival changed blocks under the paving");
        assertEquals(waterAfterMarket, site.fluidsUnderFloor(), "ending the festival changed water under the paving");
        assertEquals(CELLS_PER_LAYER, site.blocksOnLayer(FLOOR), "the paving is back with no hole in it");
        assertEquals(0, site.fluidsOnLayer(FLOOR), "pond water is left lying in the paving");
        site.assertNoWaterCanRunOut("after the Market festival");
        site.assertWaterUnderThePavingIsCovered("after the Market festival");

        // A second, different festival lays its own foundation over what the first one left.
        Layout carnival = Layout.read("Festival_Carnival.prefab.json");
        site.swapTo(carnival);
        assertEquals(carnival.blocksUnderFloor(), site.blocksUnderFloor(), "the Carnival foundation replaces the Market's");
        assertTrue(site.fluidsUnderFloor().isEmpty(), "the Market pond is still under the Carnival");
        site.swapTo(square);
        assertEquals(carnival.blocksUnderFloor(), site.blocksUnderFloor());

        // New Life leaves one cell under its floor unlisted. That cell keeps what was there: it is not dug out.
        Layout newLife = Layout.read("Festival_New_Life.prefab.json");
        site.swapTo(newLife);
        assertEquals(CELLS_UNDER_FLOOR, site.blocksUnderFloor().size(), "a cell the layout does not list was cleared to air");

        // A player made layout without a foundation writes nothing under the floor.
        Map<Vector3i, String> before = site.blocksUnderFloor();
        site.swapTo(square.renamed("player made, no foundation"));
        assertEquals(before, site.blocksUnderFloor(), "a layout without a foundation changed the ground under the paving");

        // A player made layout with its own foundation gets it laid like the shipped ones.
        Layout cobbled = square.withFoundation("player made, cobble foundation", "Rock_Stone_Cobble");
        site.swapTo(cobbled);
        assertEquals(cobbled.blocksUnderFloor(), site.blocksUnderFloor());
        site.swapTo(square);

        // Removing the square takes the paving and everything above it, and leaves the fill as ground.
        site.remove();
        assertEquals(cobbled.blocksUnderFloor(), site.blocksUnderFloor(), "removing the square dug under the paving");
        assertEquals(0, site.blocksOnLayer(FLOOR), "removing the square left the paving behind");
    }

    @Test
    void aSquareBuiltBeforeThisVersionKeepsTheDirtItAlreadyHas() throws IOException {
        // 3.x squares were built with five layers of dirt under the paving.
        Site site = Site.withGround(c -> "Soil_Dirt");
        Layout square = Layout.read(EVERYDAY);
        site.swapTo(square);
        assertEquals(CELLS_UNDER_FLOOR, site.blocksUnderFloor().size(), "the old dirt under the paving was dug out");
        assertTrue(site.blocksUnderFloor().values().stream().allMatch("Soil_Dirt"::equals));

        Layout snowball = Layout.read("Festival_Snowball.prefab.json");
        site.swapTo(snowball);
        assertEquals(snowball.blocksUnderFloor(), site.blocksUnderFloor());
        site.assertNoWaterCanRunOut("during the Snowball festival");
        site.swapTo(square);
        assertEquals(snowball.blocksUnderFloor(), site.blocksUnderFloor(), "ending the festival dug under the paving");
    }

    // ---- A festival square site, as far as cells go ----

    /**
     * The cells of one square site at anchor (0, 0, 0), facing north. Clearing follows the production plan; the paste
     * is modelled as what {@code ConstructionAnimator} does with a forced paste: every cell the layout lists is
     * written, nothing else.
     */
    private static final class Site {
        private final Map<Vector3i, String> blocks = new HashMap<>();
        private final Map<Vector3i, String> fluids = new HashMap<>();
        private final PlotFootprintRecord stored = FestivalPrefabSize.footprintAt(ORIGIN, Rotation.None);

        static Site withGround(java.util.function.Function<Vector3i, String> groundAt) {
            Site site = new Site();
            for (int x = FestivalPrefabSize.LOCAL_MIN_X; x <= FestivalPrefabSize.LOCAL_MAX_X; x++) {
                for (int z = FestivalPrefabSize.LOCAL_MIN_Z; z <= FestivalPrefabSize.LOCAL_MAX_Z; z++) {
                    for (int y = FestivalPrefabSize.LOCAL_MIN_Y; y < FLOOR; y++) {
                        Vector3i cell = new Vector3i(x, y, z);
                        String block = groundAt.apply(cell);
                        if (block != null) {
                            site.blocks.put(cell, block);
                        }
                    }
                }
            }
            return site;
        }

        /** The player builds the square: only the cells assembly lists have to be empty, then the prefab is placed. */
        void build(Layout square) {
            List<PendingBlock> mustBeClear = ConstructionPasteOps.withReservedFestivalAirCells(
                square.cells(PrefabRotation.ROTATION_0), PrefabRotation.ROTATION_0, CustomFestivalPaths.BASE_PREFAB_PATH);
            for (PendingBlock cell : mustBeClear) {
                Vector3i at = new Vector3i(cell.x(), cell.y(), cell.z());
                blocks.remove(at);
                fluids.remove(at);
            }
            paste(square);
        }

        void swapTo(Layout incoming) {
            SwapPlan plan =
                FestivalPrefabSwapService.plan(ORIGIN, Rotation.None, stored, incoming.cells(PrefabRotation.ROTATION_0));
            clear(plan.emptied());
            for (PendingBlock cell : plan.underFloorWritten()) {
                Vector3i at = new Vector3i(cell.x(), cell.y(), cell.z());
                blocks.remove(at);
                fluids.remove(at);
            }
            paste(incoming);
        }

        void remove() {
            clear(FestivalPrefabSize.fromFloorUp(stored, ORIGIN.y));
        }

        private void clear(PlotFootprintRecord box) {
            blocks.keySet().removeIf(c -> box.containsBlock(c.x, c.y, c.z));
            fluids.keySet().removeIf(c -> box.containsBlock(c.x, c.y, c.z));
        }

        private void paste(Layout layout) {
            for (Vector3i cell : layout.allCells()) {
                String block = layout.blocks.get(cell);
                String fluid = layout.fluids.get(cell);
                if (block != null) {
                    blocks.put(cell, block);
                } else {
                    blocks.remove(cell);
                }
                if (fluid != null) {
                    fluids.put(cell, fluid);
                } else {
                    fluids.remove(cell);
                }
            }
        }

        Map<Vector3i, String> blocksUnderFloor() {
            return under(blocks);
        }

        Map<Vector3i, String> fluidsUnderFloor() {
            return under(fluids);
        }

        long blocksOnLayer(int y) {
            return blocks.keySet().stream().filter(c -> c.y == y).count();
        }

        long fluidsOnLayer(int y) {
            return fluids.keySet().stream().filter(c -> c.y == y).count();
        }

        /** Every water cell at or under the paving is held on four sides and below by a block or more water. */
        void assertNoWaterCanRunOut(String when) {
            for (Vector3i water : fluids.keySet()) {
                if (water.y > FLOOR) {
                    continue;
                }
                for (int[] d : SIDES_AND_BELOW) {
                    Vector3i next = new Vector3i(water.x + d[0], water.y + d[1], water.z + d[2]);
                    assertTrue(
                        fluids.containsKey(next) || isWatertight(blocks.get(next)),
                        when + ": water at " + water + " can run out through " + next
                    );
                }
            }
        }

        /** What a festival left under the paving stays shut in: water there has a block or more water on top of it. */
        void assertWaterUnderThePavingIsCovered(String when) {
            for (Vector3i water : fluids.keySet()) {
                if (water.y >= FLOOR) {
                    continue;
                }
                Vector3i above = new Vector3i(water.x, water.y + 1, water.z);
                assertTrue(
                    fluids.containsKey(above) || isWatertight(blocks.get(above)),
                    when + ": water at " + water + " under the paving is open to the square"
                );
            }
        }
    }

    // ---- Prefab files ----

    private record Layout(String file, Map<Vector3i, String> blocks, Map<Vector3i, String> fluids) {
        static Layout read(String file) throws IOException {
            JsonObject root = readJson(FESTIVAL_PREFABS.resolve(file));
            Map<Vector3i, String> blocks = new LinkedHashMap<>();
            for (JsonElement element : root.getAsJsonArray("blocks")) {
                JsonObject row = element.getAsJsonObject();
                blocks.put(at(row), row.get("name").getAsString());
            }
            Map<Vector3i, String> fluids = new LinkedHashMap<>();
            if (root.has("fluids") && root.get("fluids").isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray("fluids")) {
                    JsonObject row = element.getAsJsonObject();
                    fluids.put(at(row), row.get("name").getAsString());
                }
            }
            return new Layout(file, blocks, fluids);
        }

        Layout renamed(String name) {
            return new Layout(name, blocks, fluids);
        }

        /** This layout from the floor up, standing on a solid foundation of {@code block}. */
        Layout withFoundation(String name, String block) {
            Map<Vector3i, String> all = new LinkedHashMap<>(blocks);
            for (int x = FestivalPrefabSize.LOCAL_MIN_X; x <= FestivalPrefabSize.LOCAL_MAX_X; x++) {
                for (int z = FestivalPrefabSize.LOCAL_MIN_Z; z <= FestivalPrefabSize.LOCAL_MAX_Z; z++) {
                    for (int y = FestivalPrefabSize.LOCAL_MIN_Y; y < FLOOR; y++) {
                        all.put(new Vector3i(x, y, z), block);
                    }
                }
            }
            return new Layout(name, all, fluids);
        }

        /** One cell per position, as in the prefab buffer: reeds standing in water are a block and a fluid at once. */
        Set<Vector3i> allCells() {
            Set<Vector3i> cells = new HashSet<>(blocks.keySet());
            cells.addAll(fluids.keySet());
            return cells;
        }

        Set<Vector3i> cellsUnderFloor() {
            Set<Vector3i> cells = allCells();
            cells.removeIf(c -> c.y >= FLOOR);
            return cells;
        }

        Map<Vector3i, String> blocksUnderFloor() {
            return under(blocks);
        }

        Map<Vector3i, String> fluidsUnderFloor() {
            return under(fluids);
        }

        long blocksOnLayer(int y) {
            return blocks.keySet().stream().filter(c -> c.y == y).count();
        }

        /** The layout's cells as assembly and the swap see them, turned the way the paste turns them. */
        List<PendingBlock> cells(PrefabRotation rotation) {
            List<PendingBlock> cells = new ArrayList<>();
            for (Vector3i cell : allCells()) {
                Vector3i turned = new Vector3i(cell);
                rotation.rotate(turned);
                String block = blocks.get(cell);
                int blockId = block == null || "Empty".equals(block) ? 0 : 1;
                int fluidId = fluids.containsKey(cell) ? 1 : 0;
                cells.add(new PendingBlock(turned.x, turned.y, turned.z, blockId, null, 0, 0, 0, fluidId, fluidId));
            }
            return cells;
        }
    }

    private static Map<Vector3i, String> under(Map<Vector3i, String> cells) {
        Map<Vector3i, String> out = new HashMap<>();
        cells.forEach((cell, what) -> {
            if (cell.y < FLOOR) {
                out.put(cell, what);
            }
        });
        return out;
    }

    private static List<Layout> festivalLayouts() throws IOException {
        List<Layout> layouts = new ArrayList<>();
        try (Stream<Path> files = Files.list(FESTIVAL_PREFABS)) {
            for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".prefab.json")).sorted().toList()) {
                if (!EVERYDAY.equals(file.getFileName().toString())) {
                    layouts.add(Layout.read(file.getFileName().toString()));
                }
            }
        }
        return layouts;
    }

    /** Full blocks that hold water: soil, stone, ice and trunks. Plants, crystals and air do not. */
    private static boolean isWatertight(String block) {
        if (block == null) {
            return false;
        }
        String name = block.startsWith("*") ? block.substring(1) : block;
        return name.startsWith("Soil_") || name.startsWith("Rock_Stone") || name.startsWith("Rock_Ice") || name.startsWith("Wood_");
    }

    private static boolean onOuterFace(Vector3i cell) {
        return cell.x == FestivalPrefabSize.LOCAL_MIN_X
            || cell.x == FestivalPrefabSize.LOCAL_MAX_X
            || cell.z == FestivalPrefabSize.LOCAL_MIN_Z
            || cell.z == FestivalPrefabSize.LOCAL_MAX_Z;
    }

    private static Vector3i at(JsonObject row) {
        return new Vector3i(row.get("x").getAsInt(), row.get("y").getAsInt(), row.get("z").getAsInt());
    }

    private static JsonObject readJson(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return new Gson().fromJson(reader, JsonObject.class);
        }
    }
}
