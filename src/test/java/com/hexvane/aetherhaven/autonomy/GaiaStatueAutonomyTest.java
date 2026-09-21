package com.hexvane.aetherhaven.autonomy;

import static org.junit.jupiter.api.Assertions.*;
import com.hexvane.aetherhaven.plot.GaiaStatueAppearance;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiInteractionKind;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("autonomy")
class GaiaStatueAutonomyTest {
    @Test void everyStatueAppearanceIsExcludedEvenWhenItIsTheOnlyScheduledSpot() {
        UUID town = UUID.randomUUID(), plot = UUID.randomUUID();
        var binding = new TownVillagerBinding(town, TownVillagerBinding.KIND_FARMER, plot, plot);
        var needs = VillagerNeeds.full();
        needs.setHunger(60);
        needs.setFun(60);
        for (var appearance : GaiaStatueAppearance.values()) {
            var statue = new PoiEntry(UUID.randomUUID(), town, 0, 65, 0, Set.of(), 1,
                plot, appearance.blockTypeId(), PoiInteractionKind.NONE);
            assertEquals(0, PoiScoring.score(needs, statue));
            assertNull(PoiScoring.pickBest(List.of(statue), needs, binding));
            var seat = new PoiEntry(UUID.randomUUID(), town, 3, 64, 2, Set.of("FUN", "SIT"), 1,
                plot, "Furniture_Temple_Emerald_Stool", PoiInteractionKind.SIT);
            assertEquals(seat, PoiScoring.pickBest(List.of(statue, seat), needs, binding));
        }
    }

    @Test void blockedCenterUsesTheClearColumnsCoordinatesAndHeight() {
        var target = SchedulePlotCommute.findClearColumn(-5, 5, -5, 5,
            (x, z) -> x == 2 && z == -1 ? 64 : Integer.MIN_VALUE);
        assertNotNull(target);
        assertEquals(new org.joml.Vector3i(2, 64, -1), target);
    }

    @Test void noClearGroundDoesNotInventATargetOrProbeOutsideThePlot() {
        assertNull(SchedulePlotCommute.findClearColumn(-4, -2, -7, -5, (x, z) -> {
            assertTrue(x >= -4 && x <= -2 && z >= -7 && z <= -5);
            return Integer.MIN_VALUE;
        }));
    }
    @Test void recoversInsideAboveAndOverlappingStatueWithoutNeedingAPoiTarget() {
        var base = new org.joml.Vector3i(10, 64, 20);
        VillagerBlockUtil.StatueProbe blocks = (x, y, z) ->
            x == 10 && z == 20 && y == 64 ? base : null;
        for (double y : new double[]{64, 65, 66.8, 69.82}) {
            assertEquals(base, VillagerBlockUtil.intersectingGaiaStatue(new org.joml.Vector3d(10.5, y, 20.5), blocks));
        }
        assertEquals(base, VillagerBlockUtil.intersectingGaiaStatue(new org.joml.Vector3d(11.2, 65, 20.5), blocks));
        assertNull(VillagerBlockUtil.intersectingGaiaStatue(new org.joml.Vector3d(11.5, 65, 20.5), blocks));
        assertNull(VillagerBlockUtil.intersectingGaiaStatue(new org.joml.Vector3d(10.5, 62, 20.5), blocks));
    }

    @Test void fillerCellsResolveToTheStatueBase() {
        var base = new org.joml.Vector3i(-2, 64, -3);
        assertEquals(base, VillagerBlockUtil.intersectingGaiaStatue(new org.joml.Vector3d(-1.5, 66.8, -2.5),
            (x, y, z) -> x == -2 && z == -3 && y == 66 ? base : null));
    }

    @Test void separateFloorAboveStatueIsNotTreatedAsBeingStuck() {
        var blocks = new VillagerBlockUtil.StatueProbe() {
            public org.joml.Vector3i statueAt(int x, int y, int z) {
                return x == 0 && z == 0 && y == 64 ? new org.joml.Vector3i(0, 64, 0) : null;
            }
            public boolean blocksBelow(int x, int y, int z) { return y == 68; }
        };
        assertNull(VillagerBlockUtil.intersectingGaiaStatue(new org.joml.Vector3d(.5, 69.02, .5), blocks));
    }

}
