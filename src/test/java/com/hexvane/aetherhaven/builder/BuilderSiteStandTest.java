package com.hexvane.aetherhaven.builder;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Where the core Builder stands at a site: beside the building, never inside its outline. */
@Tag("construction")
class BuilderSiteStandTest {
    /** A building from 10 to 18 east and 20 to 28 south, lowest layer at 64, on flat ground at 64 all round. */
    private static int[] stand(double fromX, double fromY, double fromZ, com.hexvane.aetherhaven.construction.assembly.SiteRefuge.Standable ground) {
        return BuilderSiteStand.outside(10, 20, 18, 28, 64, BuilderSiteStand.ABOVE, BuilderSiteStand.BELOW, BuilderSiteStand.RINGS, fromX, fromY, fromZ, ground);
    }

    private static boolean inside(int[] cell) {
        return cell[0] >= 10 && cell[0] <= 18 && cell[2] >= 20 && cell[2] <= 28;
    }

    @Test void heStandsJustOutsideTheBuildingOnTheSideHeComesFrom() {
        var flat = (com.hexvane.aetherhaven.construction.assembly.SiteRefuge.Standable) (x, y, z) -> y == 64;
        // From the west, level with the middle: the cell touching the west wall.
        assertArrayEquals(new int[] {9, 64, 24}, stand(0.5, 64, 24.5, flat));
        // From the south east: the corner cell outside.
        assertArrayEquals(new int[] {19, 64, 29}, stand(40.5, 64, 50.5, flat));
        // From the north: touching the north wall, as far east as he is.
        assertArrayEquals(new int[] {14, 64, 19}, stand(14.5, 64, 5.5, flat));
        // Standing inside the building when the build starts (the plot sign is often there): he is sent out, never kept in.
        int[] fromInside = stand(14.5, 64, 24.5, flat);
        assertNotNull(fromInside);
        assertFalse(inside(fromInside));
        for (double x = 0; x < 30; x += 3.7) {
            for (double z = 10; z < 40; z += 4.3) {
                int[] cell = stand(x, 64, z, flat);
                assertNotNull(cell);
                assertFalse(inside(cell), "from " + x + ", " + z);
                int out = Math.max(Math.max(10 - cell[0], cell[0] - 18), Math.max(20 - cell[2], cell[2] - 28));
                assertEquals(1, out, "one cell out from the wall when the ground there will do");
            }
        }
    }

    @Test void groundThatWillNotDoIsPassedOverAndTheNextRingIsTried() {
        // Water all along the west wall, one cell wide.
        var wetWest = (com.hexvane.aetherhaven.construction.assembly.SiteRefuge.Standable) (x, y, z) -> y == 64 && x != 9;
        int[] cell = stand(0.5, 64, 24.5, wetWest);
        assertNotNull(cell);
        assertNotEquals(9, cell[0]);
        assertFalse(inside(cell));
        // The whole first ring is no good (a moat): the second ring, still on his side.
        var moat = (com.hexvane.aetherhaven.construction.assembly.SiteRefuge.Standable) (x, y, z) ->
            y == 64 && Math.max(Math.max(10 - x, x - 18), Math.max(20 - z, z - 28)) >= 2;
        assertArrayEquals(new int[] {8, 64, 24}, stand(0.5, 64, 24.5, moat));
        // The building stands in a pit: the ground around is two higher, and that is where he stands.
        var raised = (com.hexvane.aetherhaven.construction.assembly.SiteRefuge.Standable) (x, y, z) -> y == 66;
        assertArrayEquals(new int[] {9, 66, 24}, stand(0.5, 66, 24.5, raised));
        // Or on a mound: the ground around is three lower.
        var sunk = (com.hexvane.aetherhaven.construction.assembly.SiteRefuge.Standable) (x, y, z) -> y == 61;
        assertArrayEquals(new int[] {9, 61, 24}, stand(0.5, 61, 24.5, sunk));
        // Nothing to stand on for four cells around, or only out of his reach in height: no stand, and the caller falls back.
        assertNull(stand(0.5, 64, 24.5, (x, y, z) -> false));
        assertNull(stand(0.5, 64, 24.5, (x, y, z) -> y == 64 && Math.max(Math.max(10 - x, x - 18), Math.max(20 - z, z - 28)) >= 5));
        assertNull(stand(0.5, 64, 24.5, (x, y, z) -> y == 70));
        // A cell inside the outline is never offered, even if it is the only ground there is.
        assertNull(stand(14.5, 64, 24.5, (x, y, z) -> y == 64 && x >= 10 && x <= 18 && z >= 20 && z <= 28));
    }
}
