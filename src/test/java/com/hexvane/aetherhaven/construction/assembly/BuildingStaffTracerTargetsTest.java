package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import java.util.List;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("construction")
class BuildingStaffTracerTargetsTest {
    private static final PlotFootprintRecord CURRENT = new PlotFootprintRecord(-5, 0, -5, 10, 10, 5);
    private static final PlotFootprintRecord NEIGHBOR = new PlotFootprintRecord(11, 0, -5, 30, 10, 5);
    private static final Vector3d FEET = new Vector3d(9.9, 1, 0.5);
    private static final Vector3d TIP = new Vector3d(10.32, 2.32, 0.5);

    @Test void ownPlacingFrontierBeatsCloserClearingAndPlacingTargetsAcrossTheBoundary() {
        var own = target(-4, 1, 0, CURRENT, false);
        var clearing = target(11, 1, 0, NEIGHBOR, true);
        var placing = target(12, 1, 0, NEIGHBOR, false);
        assertSame(own.cell(), select(clearing, placing, own));
        assertSame(own.cell(), select(own, placing, clearing));
    }

    @Test void ownClearingUsesItsNearestValidObstruction() {
        var far = target(-4, 1, 0, CURRENT, true);
        var near = target(8, 1, 0, CURRENT, true);
        assertSame(near.cell(), select(target(11, 1, 0, NEIGHBOR, true), far, near));
    }

    @Test void noEligibleTargetInCurrentPlotFallsBackToNearbyWork() {
        // Permission, difficulty, assembly state and live-frontier checks have already excluded this plot's cells.
        var nearby = target(11, 1, 0, NEIGHBOR, false);
        assertSame(nearby.cell(), select(nearby));
        assertNull(select());
    }

    @Test void outOfRangeCurrentFrontierDoesNotPreventFallbackAndBoundaryIsInclusive() {
        var distant = new PlotFootprintRecord(-5, 0, -5, 200, 10, 5);
        var nearby = target(11, 1, 0, NEIGHBOR, false);
        assertSame(nearby.cell(), select(target(150, 1, 0, distant, false), nearby));
        var boundary = target(96, 0, 0, distant, false);
        assertSame(boundary.cell(), BuildingStaffTracerTargets.select(List.of(boundary), FEET, new Vector3d(0.5, 0.5, 0.5), 96));
        assertNull(BuildingStaffTracerTargets.select(List.of(boundary), FEET, new Vector3d(0.49, 0.5, 0.5), 96));
    }

    @Test void outsideAllPlotsPreservesClearingFirstThenNearestBehavior() {
        var nearPlacing = target(1, 1, 0, NEIGHBOR, false);
        var nearClearing = target(15, 1, 0, NEIGHBOR, true);
        var farClearing = target(25, 1, 0, NEIGHBOR, true);
        assertSame(nearClearing.cell(), BuildingStaffTracerTargets.select(List.of(nearPlacing, farClearing, nearClearing),
            new Vector3d(0, 20, 0), new Vector3d(0, 21.32, 0), 96));
    }

    @Test void containmentUsesFlooredFeetIncludingNegativeCoordinatesAndVerticalBounds() {
        var left = new PlotFootprintRecord(-2, 0, -2, -1, 5, 2);
        var right = new PlotFootprintRecord(0, 0, -2, 2, 5, 2);
        var own = target(-2, 1, 0, left, false);
        var other = target(0, 1, 0, right, true);
        var tip = new Vector3d(0.3, 2.32, 0.5);
        assertSame(own.cell(), BuildingStaffTracerTargets.select(List.of(other, own), new Vector3d(-0.1, 1, 0.5), tip, 96));
        assertSame(other.cell(), BuildingStaffTracerTargets.select(List.of(other, own), new Vector3d(-0.1, 6, 0.5), tip, 96));
    }

    private static BuildingStaffTracerTargets.Candidate target(int x, int y, int z, PlotFootprintRecord footprint, boolean clearing) {
        return new BuildingStaffTracerTargets.Candidate(new Vector3i(x, y, z), footprint, clearing);
    }

    private static Vector3i select(BuildingStaffTracerTargets.Candidate... targets) {
        return BuildingStaffTracerTargets.select(List.of(targets), FEET, TIP, 96);
    }
}
