package com.hexvane.aetherhaven.construction.assembly;

import static org.junit.jupiter.api.Assertions.*;

import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import com.hypixel.hytale.math.shape.Box;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Whether a block would be placed into a body, with the sizes the game really gives this mod's villagers. The boxes are
 * the hit boxes of the models in the 0.7.0 pre-release assets: the player model every ordinary villager uses, the
 * Trork, the Klops and the Kweebec Rootling.
 */
@Tag("construction")
class SiteBodiesTest {
    private static final Box VILLAGER = new Box(-0.325, 0, -0.325, 0.325, 1.85, 0.325);
    private static final Box TRORK = new Box(-0.5, 0, -0.5, 0.5, 1.9, 0.5);
    private static final Box KLOPS = new Box(-0.3, 0, -0.3, 0.3, 1.5, 0.3);
    private static final Box ROOTLING = new Box(-0.3, 0, -0.3, 0.3, 2.0, 0.3);
    private static final Box CUBE = Box.UNIT;
    private static final Box SLAB = new Box(0, 0, 0, 1, 0.5, 1);
    /** A door: two cells tall and thin, as the village door's box is. */
    private static final Box DOOR = new Box(0, 0, 0.4, 1, 2, 0.6);

    private static SiteBodies.Body at(Box box, double x, double y, double z) {
        return SiteBodies.Body.at(null, false, new Vector3d(x, y, z), box);
    }

    @Test void aBlockInTheCellAVillagerStandsInOrTheOneAboveIsInTheirBody() {
        var villager = at(VILLAGER, 10.5, 64, 10.5);
        assertTrue(villager.overlapsBlock(10, 64, 10, CUBE), "feet");
        assertTrue(villager.overlapsBlock(10, 65, 10, CUBE), "chest and head");
        assertFalse(villager.overlapsBlock(10, 66, 10, CUBE), "1.85 tall: the third cell is above the head");
        assertFalse(villager.overlapsBlock(10, 63, 10, CUBE), "the block they stand on is not in their way");
        // Standing a little above the cell floor, as the game puts an NPC, changes nothing.
        var settled = at(VILLAGER, 10.5, 64.02, 10.5);
        assertTrue(settled.overlapsBlock(10, 64, 10, CUBE));
        assertFalse(settled.overlapsBlock(10, 63, 10, CUBE));
        assertFalse(settled.overlapsBlock(10, 66, 10, CUBE));
    }

    @Test void neighbouringCellsAreFreeForAnOrdinaryVillagerInTheMiddleOfACell() {
        var villager = at(VILLAGER, 10.5, 64, 10.5);
        for (int[] side : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}}) {
            assertFalse(villager.overlapsBlock(10 + side[0], 64, 10 + side[1], CUBE));
            assertFalse(villager.overlapsBlock(10 + side[0], 65, 10 + side[1], CUBE));
        }
        // Off centre the body reaches into the next cell, and that cell is then theirs too.
        var leaning = at(VILLAGER, 10.8, 64, 10.5);
        assertTrue(leaning.overlapsBlock(11, 64, 10, CUBE));
        assertFalse(leaning.overlapsBlock(9, 64, 10, CUBE));
        // Half the body is 0.325 and the margin 0.05: from 10.6 that reaches 10.975, short of the wall at 11.
        assertFalse(at(VILLAGER, 10.6, 64, 10.5).overlapsBlock(11, 64, 10, CUBE));
        // Touching a face exactly is not overlapping either.
        assertFalse(at(VILLAGER, 10.625, 64, 10.5).overlapsBlock(11, 64, 10, CUBE));
        assertTrue(at(VILLAGER, 10.63, 64, 10.5).overlapsBlock(11, 64, 10, CUBE), "inside the margin kept beside a body");
    }

    @Test void theRealSizeOfEachKindOfTownsfolkDecidesNotOneBlockByTwo() {
        // A Trork is a full block wide: in the middle of a cell the margin puts every neighbour within reach.
        var trork = at(TRORK, 10.5, 64, 10.5);
        assertTrue(trork.overlapsBlock(11, 64, 10, CUBE));
        assertTrue(trork.overlapsBlock(9, 65, 10, CUBE));
        assertTrue(trork.overlapsBlock(10, 64, 11, CUBE));
        assertFalse(trork.overlapsBlock(12, 64, 10, CUBE), "two cells away is clear");
        assertFalse(trork.overlapsBlock(10, 66, 10, CUBE), "1.9 tall");
        // A Klops is 1.5 tall: the cell above their feet is still theirs, the body ends inside it.
        var klops = at(KLOPS, 10.5, 64, 10.5);
        assertTrue(klops.overlapsBlock(10, 65, 10, CUBE));
        assertFalse(klops.overlapsBlock(10, 65, 10, new Box(0, 0.6, 0, 1, 1, 1)), "an upper slab above a short body is clear");
        assertTrue(at(VILLAGER, 10.5, 64, 10.5).overlapsBlock(10, 65, 10, new Box(0, 0.6, 0, 1, 1, 1)), "but not above an ordinary one");
        // A Rootling is 2 tall: with the margin above the head, the third cell is in their way.
        var rootling = at(ROOTLING, 10.5, 64, 10.5);
        assertTrue(rootling.overlapsBlock(10, 66, 10, CUBE));
        assertFalse(rootling.overlapsBlock(10, 67, 10, CUBE));
    }

    @Test void aBlocksOwnShapeDecidesNotItsWholeCell() {
        var onTheFloor = at(VILLAGER, 10.5, 64, 10.5);
        assertTrue(onTheFloor.overlapsBlock(10, 64, 10, SLAB), "a slab at the feet");
        // Standing on a slab: feet half way up its cell.
        var onASlab = at(VILLAGER, 10.5, 64.5, 10.5);
        assertFalse(onASlab.overlapsBlock(10, 64, 10, SLAB), "the slab they stand on");
        assertTrue(onASlab.overlapsBlock(10, 64, 10, CUBE), "a full block in the same cell would bury their feet");
        // In a doorway: the door's piece is its lower cell, its box is two cells tall and thin.
        var inTheDoorway = at(VILLAGER, 20.5, 64, 30.5);
        assertTrue(inTheDoorway.overlapsBlock(20, 64, 30, DOOR));
        assertFalse(at(VILLAGER, 20.5, 64, 31.5).overlapsBlock(20, 64, 30, DOOR), "a step back from the doorway");
        // Standing on the block under the door's upper half, one cell up: the upper half is still in the way.
        assertTrue(at(VILLAGER, 20.5, 65, 30.5).overlapsBlock(20, 64, 30, DOOR));
        assertFalse(at(VILLAGER, 20.5, 66, 30.5).overlapsBlock(20, 64, 30, DOOR), "on top of the door frame");
    }

    @Test void theFirstBodyInTheWayIsNamedAndAFreeCellNamesNone() {
        List<SiteBodies.Body> bodies = new ArrayList<>();
        assertNull(SiteBodies.blocker(bodies, 10, 64, 10, CUBE));
        var player = SiteBodies.Body.at(null, true, new Vector3d(3.5, 64, 3.5), VILLAGER);
        var villager = at(VILLAGER, 10.5, 64, 10.5);
        bodies.add(player);
        bodies.add(villager);
        assertSame(villager, SiteBodies.blocker(bodies, 10, 65, 10, CUBE));
        assertSame(player, SiteBodies.blocker(bodies, 3, 64, 3, CUBE));
        assertTrue(SiteBodies.blocker(bodies, 3, 64, 3, CUBE).player());
        assertNull(SiteBodies.blocker(bodies, 10, 66, 10, CUBE));
        assertNull(SiteBodies.blocker(bodies, 6, 64, 6, CUBE));
        // Negative coordinates: the cell west of the origin.
        var west = at(VILLAGER, -0.5, 64, -0.5);
        assertTrue(west.overlapsBlock(-1, 64, -1, CUBE));
        assertFalse(west.overlapsBlock(0, 64, 0, CUBE));
    }

    @Test void theCellsOfASiteStillToBeFilledAreKnownAndABodyIsHeldAgainstThem() {
        List<PendingBlock> pending = new ArrayList<>();
        // A floor of 3 x 3, a wall piece and a flower on it. Index 9 is the wall, 10 the flower.
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) pending.add(new PendingBlock(x, 0, z, 1, null, 0, 0, 0, 0, 0));
        pending.add(new PendingBlock(0, 1, 0, 1, null, 0, 0, 0, 0, 0));
        pending.add(new PendingBlock(2, 1, 2, 7, null, 0, 0, 0, 0, 0));
        var anchor = new Vector3i(100, 64, -50);
        var placed = new IntOpenHashSet(new int[] {0, 1, 2});
        LongOpenHashSet unbuilt = SiteCells.unbuiltSolid(anchor, pending, placed, index -> pending.get(index).blockId() != 7);
        assertEquals(7, unbuilt.size(), "six floor pieces and the wall; the flower is not solid, three are placed");
        assertFalse(unbuilt.contains(SiteCells.key(100, 64, -50)), "placed");
        assertTrue(unbuilt.contains(SiteCells.key(101, 64, -50)));
        assertTrue(unbuilt.contains(SiteCells.key(100, 65, -50)), "the wall piece");
        assertFalse(unbuilt.contains(SiteCells.key(102, 65, -48)), "the flower");
        assertEquals(10, SiteCells.solidWhenFinished(anchor, pending, index -> pending.get(index).blockId() != 7).size());
        // An ordinary villager on a placed floor piece next to the unbuilt ones is in none of them.
        assertFalse(SiteCells.bodyIn(unbuilt, 100.5, 65, -48.5, VILLAGER), "on placed floor at x 100, z -49: nothing unbuilt at that height");
        // Standing on the ground in an unbuilt floor cell.
        assertTrue(SiteCells.bodyIn(unbuilt, 101.5, 64, -49.5, VILLAGER));
        // Standing on the placed floor where the wall goes.
        assertTrue(SiteCells.bodyIn(unbuilt, 100.5, 65, -49.5, VILLAGER));
        // A Trork beside the wall cell reaches into it; an ordinary villager there does not.
        assertTrue(SiteCells.bodyIn(unbuilt, 100.5, 65, -48.5, TRORK));
        assertFalse(SiteCells.bodyIn(new LongOpenHashSet(), 101.5, 64, -49.5, VILLAGER));
        // Keys of different cells differ, on both sides of zero.
        assertNotEquals(SiteCells.key(-1, 64, 0), SiteCells.key(1, 64, 0));
        assertNotEquals(SiteCells.key(0, 64, -1), SiteCells.key(0, 64, 1));
        assertNotEquals(SiteCells.key(5, 64, 6), SiteCells.key(6, 64, 5));
        assertNotEquals(SiteCells.key(5, 64, 6), SiteCells.key(5, 65, 6));
    }
}
