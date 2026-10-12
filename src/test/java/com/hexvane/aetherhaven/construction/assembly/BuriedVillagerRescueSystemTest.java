package com.hexvane.aetherhaven.construction.assembly;

import static org.junit.jupiter.api.Assertions.*;

import com.hypixel.hytale.math.shape.Box;
import java.util.HashSet;
import java.util.Set;
import org.joml.Vector3d;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** When a villager counts as buried, and how many looks it takes before they are moved. */
@Tag("construction")
class BuriedVillagerRescueSystemTest {
    private static final Box VILLAGER = new Box(-0.325, 0, -0.325, 0.325, 1.85, 0.325);
    private static final Box ROOTLING = new Box(-0.3, 0, -0.3, 0.3, 2.0, 0.3);

    /** A world of whole solid cells. */
    private static BuriedVillagerRescueSystem.SolidAt cells(int[]... solid) {
        Set<Long> set = new HashSet<>();
        for (int[] cell : solid) set.add(SiteCells.key(cell[0], cell[1], cell[2]));
        return (x, y, z) -> set.contains(SiteCells.key((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)));
    }

    @Test void aBlockThroughTheMiddleOfTheBodyIsBuriedAndNothingElseIs() {
        var feet = new Vector3d(10.5, 64.02, 10.5);
        assertFalse(BuriedVillagerRescueSystem.buried(feet, VILLAGER, cells()), "open air");
        assertFalse(BuriedVillagerRescueSystem.buried(feet, VILLAGER, cells(new int[] {10, 63, 10})), "the block they stand on");
        assertTrue(BuriedVillagerRescueSystem.buried(feet, VILLAGER, cells(new int[] {10, 64, 10})), "a block at the legs");
        assertTrue(BuriedVillagerRescueSystem.buried(feet, VILLAGER, cells(new int[] {10, 65, 10})), "a block at the chest and head");
        assertFalse(BuriedVillagerRescueSystem.buried(feet, VILLAGER, cells(new int[] {10, 66, 10})), "a ceiling right above the head");
        // Walls on every side and a ceiling, as inside a narrow corridor: not buried.
        assertFalse(BuriedVillagerRescueSystem.buried(feet, VILLAGER, cells(
            new int[] {11, 64, 10}, new int[] {9, 64, 10}, new int[] {10, 64, 11}, new int[] {10, 64, 9},
            new int[] {11, 65, 10}, new int[] {9, 65, 10}, new int[] {10, 65, 11}, new int[] {10, 65, 9}, new int[] {10, 66, 10})));
        // Pressed against a wall, even with the body's edge in its cell: the middle of the body is in the open.
        assertFalse(BuriedVillagerRescueSystem.buried(new Vector3d(10.9, 64.02, 10.5), VILLAGER, cells(new int[] {11, 64, 10}, new int[] {11, 65, 10})));
        // A taller body is probed over its own height: a Rootling's head reaches a block an ordinary villager's does not.
        var low = cells(new int[] {10, 65, 10});
        assertTrue(BuriedVillagerRescueSystem.buried(new Vector3d(10.5, 64.0, 10.5), ROOTLING, low));
        var high = (BuriedVillagerRescueSystem.SolidAt) (x, y, z) -> y >= 65.75 && y < 66;
        assertTrue(BuriedVillagerRescueSystem.buried(new Vector3d(10.5, 64.0, 10.5), ROOTLING, high), "nine tenths of 2.0 is 1.8");
        assertFalse(BuriedVillagerRescueSystem.buried(new Vector3d(10.5, 64.0, 10.5), VILLAGER, high), "nine tenths of 1.85 is 1.665");
    }

    @Test void aShapedBlockCountsOnlyWhereItReallyIs() {
        // A slab filling the lower half of a cell.
        BuriedVillagerRescueSystem.SolidAt slab = (x, y, z) -> Math.floor(x) == 10 && Math.floor(z) == 10 && y >= 64 && y < 64.5;
        assertFalse(BuriedVillagerRescueSystem.buried(new Vector3d(10.5, 64.52, 10.5), VILLAGER, slab), "standing on the slab");
        assertTrue(BuriedVillagerRescueSystem.buried(new Vector3d(10.5, 64.02, 10.5), VILLAGER, slab), "standing inside it, up to the knees");
    }

    @Test void twoLooksInARowAreNeededAndTheVillagerIsThenLeftAloneForAWhile() {
        var watch = new BuriedVillagerRescueSystem.Watch();
        long now = 100_000;
        assertFalse(BuriedVillagerRescueSystem.look(watch, true, now), "one look is not enough");
        assertEquals(now + BuriedVillagerRescueSystem.LOOK_EVERY_MS, watch.nextLookMs);
        // Free at the next look: a passing moment, forgotten.
        assertFalse(BuriedVillagerRescueSystem.look(watch, false, now + 1_000));
        assertEquals(0, watch.buriedLooks);
        assertFalse(BuriedVillagerRescueSystem.look(watch, true, now + 2_000));
        assertTrue(BuriedVillagerRescueSystem.look(watch, true, now + 3_000), "twice running: move them");
        assertEquals(now + 3_000 + BuriedVillagerRescueSystem.AFTER_MOVE_MS, watch.nextLookMs, "then leave them alone while the move lands");
        assertEquals(0, watch.buriedLooks);
        // Still buried later (no safe spot was found, say): it takes two looks again, it does not fire every second.
        assertFalse(BuriedVillagerRescueSystem.look(watch, true, now + 20_000));
        assertTrue(BuriedVillagerRescueSystem.look(watch, true, now + 21_000));
        assertEquals(2, BuriedVillagerRescueSystem.LOOKS_NEEDED);
    }
}
