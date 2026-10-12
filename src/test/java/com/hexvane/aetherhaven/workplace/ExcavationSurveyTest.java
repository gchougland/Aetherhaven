package com.hexvane.aetherhaven.workplace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import com.hexvane.aetherhaven.workplace.ExcavationPlan.Cell;
import com.hexvane.aetherhaven.workplace.ExcavationPlan.Found;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Miners dig what is in the ground now, not what the order recorded on the day it was planned.
 *
 * <p>The owner's report from October 11: "Miners stopped mining saying 'The next block changed'. They should keep
 * mining regardless of changes and I wouldn't even know how to review it again." An order used to stop for good when
 * the next block differed from its record, and again when a player placed or broke a block inside the tunnel.
 */
@Tag("town")
class ExcavationSurveyTest {
    private static final WorkplaceArea AREA = new WorkplaceArea(8, 100, 8, 15, 14);
    private static final Point STAND = new Point(11, 100, 10);
    private final Cell first = new Cell(new Point(10, 102, 11), "Rock_Stone", 0, 1);
    private final Cell second = new Cell(new Point(11, 102, 11), "Rock_Stone", 0, 1);
    private final Cell third = new Cell(new Point(12, 102, 11), "Rock_Stone", 0, 1);
    /** The ground as it is now. A cell that is not listed here still holds the rock the order recorded. */
    private final Map<Point, Found> ground = new HashMap<>();

    private Found look(Point p) {
        return ground.getOrDefault(p, Found.rock("Rock_Stone", 0, 1, p, true));
    }

    private ExcavationPlan plan() {
        var plan = new ExcavationPlan();
        assertNull(plan.start(0, UUID.randomUUID(), UUID.randomUUID(), AREA, ExcavationPlan.Entrance.NORTH, List.of(first, second, third)));
        return plan;
    }

    private void dig(ExcavationPlan plan) {
        assertTrue(plan.prepare(Map.of("Rock_Stone_Cobble", 1)));
        for (int i = 0; i < 12; i++) {
            plan.attend(.5, true, 1);
        }
        assertTrue(plan.begin(plan.id(), 6, STAND, () -> true));
        assertTrue(plan.settle(plan.id(), true));
        assertTrue(plan.deposit(plan.id()));
    }

    @Test
    void aSwappedBlockIsDugAsWhatItNowIsWithItsOwnSkillAndDrops() {
        var plan = plan();
        assertTrue(plan.prepare(Map.of("Rock_Stone_Cobble", 1)));
        for (int i = 0; i < 12; i++) {
            plan.attend(.5, true, 1);
        }
        ground.put(first.point(), Found.rock("Ore_Iron_Stone", 3, 3, first.point(), true));

        assertTrue(plan.survey(this::look, 0));
        assertEquals(new Cell(first.point(), "Ore_Iron_Stone", 3, 3), plan.next());
        assertFalse(plan.paused());
        assertFalse(plan.edited());
        // The cobble rolled for the old stone is not paid for iron ore, and the miner starts the new block from nothing.
        assertFalse(plan.dropsPrepared());
        assertEquals(0, plan.work());
        assertTrue(plan.prepare(Map.of("Ore_Iron", 1, "Rock_Stone_Cobble", 1)));
        for (int i = 0; i < 20; i++) {
            plan.attend(.5, true, 1);
        }
        assertTrue(plan.begin(plan.id(), 10, STAND, () -> true));
        assertTrue(plan.settle(plan.id(), true));
        assertEquals(Map.of("Ore_Iron", 1, "Rock_Stone_Cobble", 1), plan.cargo());
        // Looking again changes nothing once the order matches the ground.
        assertFalse(plan.survey(this::look, 0));
        assertEquals(second, plan.next());
    }

    @Test
    void aBlockAlreadyRemovedIsPassedOverAndPaysNothing() {
        var plan = plan();
        assertTrue(plan.prepare(Map.of("Rock_Stone_Cobble", 1)));
        ground.put(first.point(), Found.EMPTY);
        ground.put(second.point(), Found.EMPTY);

        assertTrue(plan.survey(this::look, 0));
        assertEquals(third, plan.next());
        assertEquals(2, plan.completed());
        assertEquals(3, plan.total());
        assertTrue(plan.cargo().isEmpty());
        assertFalse(plan.dropsPrepared());
        dig(plan);
        assertNull(plan.next());
        assertTrue(plan.passageReady());
    }

    @Test
    void somethingThatMustNotBeBrokenIsSetAsideSoTheRestIsDugAndIsFinishedOnceItHasGone() {
        var plan = plan();
        ground.put(first.point(), Found.obstacle("Small Crude Chest"));

        assertTrue(plan.survey(this::look, 0));
        assertEquals(second, plan.next());
        assertEquals(List.of(first), plan.blocked());
        assertEquals(3, plan.total());
        assertTrue(plan.pendingPoints().contains(first.point()));
        dig(plan);
        dig(plan);
        // The rest is dug: branches off this tunnel may start, and nothing is paused.
        assertNull(plan.next());
        assertTrue(plan.passageReady());
        assertEquals(2, plan.completed());
        assertFalse(plan.paused());

        // While the chest stands the block behind it waits, however often anyone looks.
        assertFalse(plan.survey(this::look, 0));
        assertFalse(plan.survey(this::look, 10_000));
        assertEquals(List.of(first), plan.blocked());

        // The chest is moved. A look within two seconds of the last one is skipped, and the next one finds the rock.
        ground.remove(first.point());
        assertFalse(plan.survey(this::look, 10_500));
        assertTrue(plan.survey(this::look, 12_000));
        assertEquals(first, plan.next());
        assertTrue(plan.blocked().isEmpty());
        dig(plan);
        assertNull(plan.next());
        assertEquals(3, plan.completed());
        assertEquals(3, plan.total());
    }

    @Test
    void anObstructionThatLeavesOnlyOpenSpaceBehindIsNothingMoreToDig() {
        var plan = plan();
        ground.put(third.point(), Found.obstacle("Small Crude Chest"));
        plan.survey(this::look, 0);
        dig(plan);
        dig(plan);
        plan.survey(this::look, 0);
        assertEquals(List.of(third), plan.blocked());
        ground.put(third.point(), Found.EMPTY);
        assertTrue(plan.survey(this::look, 5_000));
        assertTrue(plan.blocked().isEmpty());
        assertNull(plan.next());
        assertEquals(2, plan.total());
    }

    @Test
    void groundThatIsNotLoadedIsLeftForLater() {
        var plan = plan();
        ground.put(first.point(), Found.UNLOADED);
        assertFalse(plan.survey(this::look, 0));
        assertEquals(first, plan.next());
        assertTrue(plan.blocked().isEmpty());
    }

    @Test
    void aTunnelStalledInAnOlderSaveResumesByItself() {
        // What an older save holds: stopped for good by the old rule, with none of the fields these rules added.
        var gson = new Gson();
        var saved = gson.toJsonTree(plan()).getAsJsonObject();
        saved.addProperty("edited", true);
        saved.addProperty("paused", true);
        for (String added : List.of("blocked", "sweepArmed", "fittingsSkipped")) {
            assertNotNull(saved.remove(added), added);
        }
        var plan = gson.fromJson(saved, ExcavationPlan.class);
        assertTrue(plan.paused());
        assertTrue(plan.edited());
        assertTrue(plan.blocked().isEmpty());
        assertEquals(3, plan.total());

        assertTrue(plan.resumeAfterEdit());
        assertFalse(plan.paused());
        assertFalse(plan.edited());
        assertFalse(plan.resumeAfterEdit());
        // The block that stalled it had been swapped. It is dug as it is.
        ground.put(first.point(), Found.rock("Soil_Dirt", 0, 1, first.point(), true));
        assertTrue(plan.survey(this::look, 0));
        assertEquals("Soil_Dirt", plan.next().block());
        dig(plan);
        dig(plan);
        dig(plan);
        assertNull(plan.next());
        assertEquals(3, plan.completed());

        // A player's own pause is still a pause.
        var held = plan();
        assertTrue(held.pause(held.revision(), true));
        assertFalse(held.resumeAfterEdit());
        assertTrue(held.paused());
        assertTrue(held.pause(held.revision(), false));
    }

    @Test
    void rockThatArrivedAfterPlanningIsDugBeforeTheTunnelIsCalledFinished() {
        var plan = plan();
        assertFalse(plan.sweepDue());
        dig(plan);
        dig(plan);
        assertFalse(plan.sweepDue());
        dig(plan);
        assertTrue(plan.sweepDue());
        var arrived = new Cell(new Point(11, 101, 11), "Soil_Gravel", 0, 1);
        plan.sweep(List.of(arrived));
        assertEquals(arrived, plan.next());
        assertEquals(4, plan.total());
        assertFalse(plan.passageReady());
        dig(plan);
        // Digging that rock earns one more look, which finds the passage clear.
        assertNull(plan.next());
        assertTrue(plan.sweepDue());
        plan.sweep(List.of());
        assertFalse(plan.sweepDue());
        assertTrue(plan.passageReady());
        // After that the tunnel is finished. What a player builds in it later is never added to the order.
        plan.sweep(List.of(new Cell(new Point(12, 101, 11), "Rock_Stone", 0, 1)));
        assertNull(plan.next());
        assertEquals(4, plan.total());
    }

    @Test
    void anOrderFinishedBeforeTheseRulesIsNeverSweptAgain() {
        var plan = plan();
        dig(plan);
        dig(plan);
        dig(plan);
        var gson = new Gson();
        var saved = gson.toJsonTree(plan).getAsJsonObject();
        saved.remove("sweepArmed");
        var restored = gson.fromJson(saved, ExcavationPlan.class);
        assertNull(restored.next());
        assertFalse(restored.sweepDue());
        assertFalse(restored.survey(this::look, 0));
        assertFalse(restored.sweepDue());
    }

    @Test
    void aBlockThatWillNotBreakIsSetAsideAfterThreeTries() {
        var plan = plan();
        for (int attempt = 0; attempt < 3; attempt++) {
            assertEquals(first, plan.next());
            if (!plan.dropsPrepared()) {
                assertTrue(plan.prepare(Map.of("Rock_Stone_Cobble", 1)));
            }
            for (int i = 0; i < 12; i++) {
                plan.attend(.5, true, 1);
            }
            assertTrue(plan.begin(plan.id(), 6, STAND, () -> true));
            assertTrue(plan.settle(plan.id(), false));
            assertFalse(plan.paused());
            assertTrue(plan.cargo().isEmpty());
        }
        assertEquals(second, plan.next());
        assertEquals(List.of(first), plan.blocked());
    }

    @Test
    void aFittingWithNowhereToGoIsPassedOverAndRockInItsPlaceIsDugFirst() {
        var area = new WorkplaceArea(0, 100, 0, 13, 13);
        var plan = new ExcavationPlan();
        var rock = new Cell(new Point(6, 100, 3), "Rock_Stone", 0, 1);
        assertNull(plan.start(0, UUID.randomUUID(), UUID.randomUUID(), area, ExcavationPlan.Entrance.NORTH, ExcavationPlan.Layout.STRAIGHT,
            List.of(rock), ExcavationFittings.Mode.BOTH));
        int total = plan.fittingsTotal();
        var post = plan.nextFitting();
        assertEquals(ExcavationFittings.BEAM, post.item());
        // Fittings wait for the digging.
        assertFalse(plan.passFitting(post, false));
        plan.survey(p -> Found.EMPTY, 0);
        assertNull(plan.next());

        // Gravel has fallen where the first post belongs: it joins the order, and the post waits for it.
        assertTrue(plan.requeue(new Cell(post.point(), "Soil_Gravel", 0, 1)));
        assertFalse(plan.requeue(new Cell(post.point(), "Soil_Gravel", 0, 1)));
        assertEquals(post.point(), plan.next().point());
        assertFalse(plan.passFitting(post, false));
        assertTrue(plan.prepare(Map.of("Soil_Gravel", 1)));
        for (int i = 0; i < 12; i++) {
            plan.attend(.5, true, 1);
        }
        assertTrue(plan.begin(plan.id(), 6, new Point(post.point().x(), 100, post.point().z()), () -> true));
        assertTrue(plan.settle(plan.id(), true));
        assertTrue(plan.deposit(plan.id()));

        // A post already standing counts as fitted. One with nothing to hold it is left out and counted.
        assertEquals(Map.of(ExcavationFittings.BEAM, 14, ExcavationFittings.TORCH, 2), plan.fittingsNeeded());
        assertTrue(plan.passFitting(post, true));
        assertEquals(0, plan.fittingsSkipped());
        var next = plan.nextFitting();
        assertTrue(plan.passFitting(next, false));
        assertFalse(plan.passFitting(next, false));
        assertEquals(1, plan.fittingsSkipped());
        assertEquals(2, plan.fitted());
        assertEquals(total, plan.fittingsTotal());
        assertEquals(Map.of(ExcavationFittings.BEAM, 12, ExcavationFittings.TORCH, 2), plan.fittingsNeeded());
        // Rock outside the tunnel is never added to its order.
        assertFalse(plan.requeue(new Cell(new Point(40, 100, 40), "Rock_Stone", 0, 1)));
    }
}
