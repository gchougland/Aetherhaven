package com.hexvane.aetherhaven.production;

import static com.hexvane.aetherhaven.production.TimberHarvestPlan.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.hypixel.hytale.math.util.MathUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * P04: the Logger could not plant a tree whose growth had not changed. The engine lists a tree prefab's columns in
 * hash table order, and that order differs between a prefab built from its JSON and the same prefab reloaded from
 * its binary cache, so a saved plan and a fresh prediction held the same cells in a different order.
 */
@Tag("town")
class TimberGrowthOrderTest {
    private static final Position ROOT = new Position(40, 100, -12);
    private static final String SAPLING = "Plant_Sapling_Aspen", WOOD = "Wood_Aspen_Trunk", LEAVES = "Plant_Leaves_Aspen";

    private static List<Integer> order(Int2ObjectMap<String> columns) {
        var keys = new ArrayList<Integer>();
        for (var entry : columns.int2ObjectEntrySet()) keys.add(entry.getIntKey());
        return keys;
    }

    /** As PrefabBuffer.Builder does: columns are added to one hash map, then copied into the buffer's own. */
    private static Int2ObjectOpenHashMap<String> build(List<Integer> added) {
        var builder = new Int2ObjectOpenHashMap<String>();
        for (int key : added) builder.put(key, "column");
        var columns = new Int2ObjectOpenHashMap<String>();
        for (var entry : builder.int2ObjectEntrySet()) columns.put(entry.getIntKey(), entry.getValue());
        return columns;
    }

    /** The column order of a canopy this wide, first as built from the prefab's JSON, then as reloaded from its cache. */
    private static List<List<Integer>> engineColumnOrders(int radius) {
        var grouped = new Int2ObjectOpenHashMap<String>();
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) grouped.put(MathUtil.packInt(x, z), "");
        var fromJson = build(order(grouped));
        var fromCache = build(order(fromJson));
        return List.of(order(fromJson), order(fromCache));
    }

    private static TimberPlantingReceipt aspen(List<Integer> columns) {
        var mature = new ArrayList<Cell>();
        for (int key : columns) {
            int x = MathUtil.unpackLeft(key), z = MathUtil.unpackRight(key);
            if (x == 0 && z == 0) for (int y = 0; y < 6; y++) mature.add(new Cell(new Position(ROOT.x(), ROOT.y() + y, ROOT.z()), WOOD, 0, Kind.TIMBER));
            else mature.add(new Cell(new Position(ROOT.x() + x, ROOT.y() + 6, ROOT.z() + z), LEAVES, 0, Kind.FOLIAGE));
        }
        var clearance = new HashSet<Position>();
        for (Cell cell : mature) clearance.add(cell.position());
        return new TimberPlantingReceipt(UUID.randomUUID(), SAPLING, WOOD, ROOT, 0, List.of(
            new TimberPlantingReceipt.Stage(0, SAPLING, List.of(new Cell(ROOT, SAPLING, 0, 0, Kind.FOLIAGE, true))),
            new TimberPlantingReceipt.Stage(1, "Aspen_Stage3_001.prefab.json", mature)), clearance);
    }

    @Test
    void anUnchangedTreeIsStillTheSameTreeAfterTheEngineReloadsItsPrefab() {
        var orders = engineColumnOrders(4);
        assertEquals(new HashSet<>(orders.get(0)), new HashSet<>(orders.get(1)), "The same columns");
        assertNotEquals(orders.get(0), orders.get(1), "in a different order: this is the engine behaviour behind the bug");

        var savedPlan = aspen(orders.get(0));
        var todaysPrediction = aspen(orders.get(1));
        assertNull(savedPlan.issue(p -> true));
        // Before: this is what the Logger compared, so she walked to the Rootstone and planted nothing, every time.
        assertNotEquals(savedPlan.stages(), todaysPrediction.stages());
        assertTrue(todaysPrediction.sameGrowth(savedPlan));
        assertTrue(savedPlan.sameGrowth(todaysPrediction));

        // A plan saved with the town still matches after it is read back.
        var reloaded = new Gson().fromJson(new Gson().toJson(savedPlan), TimberPlantingReceipt.class);
        assertTrue(todaysPrediction.sameGrowth(reloaded));
    }

    @Test
    void smallTreesWereNeverAffectedWhichIsWhyOnlySomeSpeciesFailed() {
        var orders = engineColumnOrders(2);
        assertEquals(orders.get(0), orders.get(1));
        assertEquals(aspen(orders.get(0)).stages(), aspen(orders.get(1)).stages());
    }

    @Test
    void aTreeThatReallyChangedIsStillRefused() {
        var columns = engineColumnOrders(4).get(0);
        var plan = aspen(columns);
        var stages = plan.stages();
        var mature = new ArrayList<>(plan.mature().cells());

        var missingLeaf = new ArrayList<>(mature);
        missingLeaf.removeLast();
        assertFalse(plan.sameGrowth(with(plan, stages.get(0), new TimberPlantingReceipt.Stage(1, plan.mature().source(), missingLeaf), plan.clearance())));

        var otherBlock = new ArrayList<>(mature);
        Cell first = otherBlock.getFirst();
        otherBlock.set(0, new Cell(first.position(), "Wood_Birch_Trunk", first.rotation(), first.filler(), first.kind(), first.nativeFarming()));
        assertFalse(plan.sameGrowth(with(plan, stages.get(0), new TimberPlantingReceipt.Stage(1, plan.mature().source(), otherBlock), plan.clearance())));

        var turned = new ArrayList<>(mature);
        turned.set(0, new Cell(first.position(), first.blockId(), 3, first.filler(), first.kind(), first.nativeFarming()));
        assertFalse(plan.sameGrowth(with(plan, stages.get(0), new TimberPlantingReceipt.Stage(1, plan.mature().source(), turned), plan.clearance())));

        assertFalse(plan.sameGrowth(with(plan, stages.get(0), new TimberPlantingReceipt.Stage(1, "Aspen_Stage3_002.prefab.json", mature), plan.clearance())),
            "A different prefab was chosen");

        var lessRoom = new HashSet<>(plan.clearance());
        lessRoom.remove(mature.getLast().position());
        assertFalse(plan.sameGrowth(with(plan, stages.get(0), stages.get(1), lessRoom)));

        assertFalse(plan.sameGrowth(new TimberPlantingReceipt(UUID.randomUUID(), SAPLING, WOOD, ROOT, 0, List.of(stages.get(0)), plan.clearance())),
            "A stage is missing");
        assertFalse(plan.sameGrowth(null));
        assertTrue(plan.sameGrowth(plan));
    }

    private static TimberPlantingReceipt with(TimberPlantingReceipt plan, TimberPlantingReceipt.Stage seed,
            TimberPlantingReceipt.Stage mature, Set<Position> clearance) {
        return new TimberPlantingReceipt(UUID.randomUUID(), plan.sapling(), plan.output(), plan.root(), 0, List.of(seed, mature), clearance);
    }

    @Test
    void everyRegisteredSpeciesNamesItsOwnSaplingAndCountsOnlyItsOwnWood() {
        var saplings = new HashSet<String>();
        var species = NativeTimberGrowth.species();
        assertTrue(species.size() >= 32);
        for (var tree : species) {
            assertTrue(tree.sapling().startsWith("Plant_Sapling_"), tree.output());
            assertTrue(saplings.add(tree.sapling()), "Two trees share " + tree.sapling());
            assertSame(tree, NativeTimberGrowth.species(tree.output()));
            assertFalse(tree.timber().isEmpty(), tree.output());
            for (String wood : tree.timber()) {
                assertEquals(Kind.TIMBER, tree.kind(wood));
                assertFalse(tree.other().contains(wood), tree.output());
            }
            for (String part : tree.other()) assertEquals(Kind.FOLIAGE, tree.kind(part));
            assertNull(tree.kind("Rock_Stone"), tree.output());
            assertTrue(NativeTimberProducts.supported(tree.sapling()), tree.sapling());
        }
        var aspen = NativeTimberGrowth.species(WOOD);
        assertNotNull(aspen);
        assertEquals(SAPLING, aspen.sapling());
        assertEquals(Set.of(WOOD, "Wood_Aspen_Trunk_Full"), aspen.timber());
        assertTrue(aspen.other().containsAll(Set.of(LEAVES, "Wood_Aspen_Branch_Short", "Wood_Aspen_Branch_Long",
            "Wood_Aspen_Branch_Corner", "Wood_Aspen_Roots", "Deco_Nest")));
    }
}
