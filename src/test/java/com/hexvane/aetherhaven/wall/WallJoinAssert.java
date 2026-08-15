package com.hexvane.aetherhaven.wall;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.annotation.Nonnull;
import org.joml.Vector3i;

/** Exact adjacency checks: joined wall pieces touch with no gap and no overlap. */
final class WallJoinAssert {
    private WallJoinAssert() {}

    /**
     * Asserts the two pieces meet exactly: their connection cells are one block apart along {@code joinDir} and level
     * with each other across it, and their footprints touch without overlapping.
     */
    static void assertFlush(
        @Nonnull WallPieceDefinition fromDef,
        @Nonnull Vector3i fromSign,
        int fromRotation,
        @Nonnull WallCardinal joinDir,
        @Nonnull WallPieceDefinition toDef,
        @Nonnull Vector3i toSign,
        int toRotation,
        @Nonnull String label
    ) {
        Vector3i exit = WallPieceGeometry.connectionCellWorld(fromDef, fromSign, fromRotation, joinDir);
        Vector3i enter = WallPieceGeometry.connectionCellWorld(toDef, toSign, toRotation, joinDir.opposite());
        assertNotNull(exit, label + ": piece behind has no connection facing " + joinDir);
        assertNotNull(enter, label + ": new piece has no connection facing " + joinDir.opposite());
        assertEquals(exit.x + joinDir.dx, enter.x, label + ": connection cells not adjacent on X");
        assertEquals(exit.z + joinDir.dz, enter.z, label + ": connection cells not adjacent on Z");

        int[] a = WallPieceGeometry.worldBoundsXZ(fromDef, fromSign, fromRotation);
        int[] b = WallPieceGeometry.worldBoundsXZ(toDef, toSign, toRotation);
        switch (joinDir) {
            case SOUTH -> {
                assertEquals(a[3] + 1, b[1], label + ": gap or overlap going south");
                assertOverlaps(a[0], a[2], b[0], b[2], label + ": footprints do not touch across X");
            }
            case NORTH -> {
                assertEquals(a[1] - 1, b[3], label + ": gap or overlap going north");
                assertOverlaps(a[0], a[2], b[0], b[2], label + ": footprints do not touch across X");
            }
            case EAST -> {
                assertEquals(a[2] + 1, b[0], label + ": gap or overlap going east");
                assertOverlaps(a[1], a[3], b[1], b[3], label + ": footprints do not touch across Z");
            }
            case WEST -> {
                assertEquals(a[0] - 1, b[2], label + ": gap or overlap going west");
                assertOverlaps(a[1], a[3], b[1], b[3], label + ": footprints do not touch across Z");
            }
        }
    }

    private static void assertOverlaps(int aMin, int aMax, int bMin, int bMax, @Nonnull String label) {
        assertTrue(aMin <= bMax && bMin <= aMax, label);
    }
}
