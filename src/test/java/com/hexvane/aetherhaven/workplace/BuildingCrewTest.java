package com.hexvane.aetherhaven.workplace;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.townsfolk.TownsfolkMastery;
import com.hexvane.aetherhaven.townsfolk.TownsfolkProgress;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** N27: several builders on one yard finish it faster, and no piece is placed or paid twice. Saved yard state only. */
@Tag("town")
class BuildingCrewTest {
    private static final double DURATION = BuildingOrders.duration(1, 1);
    private static final String ANNA = "anna", BORIS = "boris", CLARA = "clara";

    private static BuildingOrders yard() {
        var plan = new BuildingOrders();
        assertNull(plan.add(UUID.randomUUID(), UUID.randomUUID()));
        return plan;
    }
    private static BuildingOrders.Cell cell(BuildingOrders plan, int index) {
        return new BuildingOrders.Cell(plan.list().getFirst().id(), index, 10 + index, 128, 12, "Rock_Stone", 0);
    }
    /** Stand at the cell for {@code seconds}, in half second frames, as the work tick does. */
    private static long work(BuildingOrders plan, String worker, BuildingOrders.Cell cell, double seconds, long now) {
        for (double done = 0; done < seconds; done += .5) {
            now += 500;
            assertTrue(plan.focus(worker, cell, now));
            plan.attend(worker, cell, .5, true, 1, now);
        }
        return now;
    }
    /** What NativeBuildingWork.place does around the single world placement. */
    private static boolean place(BuildingOrders plan, String worker, BuildingOrders.Cell cell, boolean frontierStillHasIt) {
        if (!plan.ready(worker, cell, DURATION) || !plan.beginPlacement(worker, cell)) return false;
        try {
            return frontierStillHasIt && plan.committed(worker, cell);
        } finally {
            plan.endPlacement();
        }
    }

    @Test void twoBuildersPlaceTwoPiecesInTheTimeOneBuilderPlacesOne() {
        var alone = yard();
        long now = work(alone, ANNA, cell(alone, 0), DURATION, 0);
        assertTrue(place(alone, ANNA, cell(alone, 0), true));
        assertEquals(1, alone.contributions());

        var crew = yard();
        var first = cell(crew, 0);
        var second = cell(crew, 1);
        assertTrue(crew.focus(ANNA, first, 0));
        assertTrue(crew.focus(BORIS, second, 0));
        assertEquals(2, crew.crewSize(0));
        for (long t = 500; t <= now; t += 500) {
            crew.attend(ANNA, first, .5, true, 1, t);
            crew.attend(BORIS, second, .5, true, 1, t);
        }
        assertTrue(place(crew, ANNA, first, true));
        assertTrue(place(crew, BORIS, second, true));
        assertEquals(2, crew.contributions(), "same time, twice the pieces");
        assertEquals(0, crew.crewSize(now));
    }

    @Test void aSecondBuilderCannotStartOnAPieceSomeoneIsAlreadyBuilding() {
        var plan = yard();
        var piece = cell(plan, 0);
        var other = cell(plan, 1);
        assertTrue(plan.focus(ANNA, piece, 1_000));
        assertTrue(plan.heldByAnother(BORIS, piece, 1_200));
        assertFalse(plan.focus(BORIS, piece, 1_200));
        assertNull(plan.cell(BORIS));
        assertFalse(plan.heldByAnother(ANNA, piece, 1_200), "a builder is never blocked by their own piece");
        assertTrue(plan.focus(BORIS, other, 1_200));
        // One builder's effort is not shared with, or reset by, another.
        plan.attend(ANNA, piece, .5, true, 1, 1_500);
        plan.attend(BORIS, piece, .5, true, 1, 1_500);
        assertEquals(.5, plan.work(ANNA));
        assertEquals(0, plan.work(BORIS));
        assertFalse(plan.ready(BORIS, piece, .1));
        assertFalse(plan.beginPlacement(BORIS, piece));
        assertFalse(plan.committed(BORIS, piece));
        assertEquals(0, plan.contributions());
    }

    @Test void onePieceIsPlacedAndPaidOnceHoweverManyBuildersAreReady() {
        var plan = yard();
        var piece = cell(plan, 0);
        long now = work(plan, ANNA, piece, DURATION, 0);
        // Anna walked off; after the idle time Boris may take the same piece and finishes his own effort on it.
        long later = now + BuildingOrders.CREW_IDLE_MS + 1;
        assertFalse(plan.heldByAnother(BORIS, piece, later));
        later = work(plan, BORIS, piece, DURATION, later);
        assertTrue(plan.ready(ANNA, piece, DURATION));
        assertTrue(plan.ready(BORIS, piece, DURATION));

        var anna = new TownsfolkProgress();
        var boris = new TownsfolkProgress();
        boolean first = place(plan, BORIS, piece, true);
        // The world placed it once; the frontier no longer offers that cell to anyone.
        boolean second = place(plan, ANNA, piece, false);
        TownsfolkMastery.completed(boris, TownsfolkMastery.Work.BUILDING_BLOCK, first);
        TownsfolkMastery.completed(anna, TownsfolkMastery.Work.BUILDING_BLOCK, second);
        assertTrue(first);
        assertFalse(second);
        assertEquals(1, plan.contributions());
        assertEquals(TownsfolkMastery.Work.BUILDING_BLOCK.xp, boris.masteryXp());
        assertEquals(0, anna.masteryXp());
        // Anna's effort on the placed piece is gone, so a stale retry cannot be paid either.
        assertFalse(plan.ready(ANNA, piece, DURATION));
        assertFalse(plan.committed(ANNA, piece));
        assertFalse(plan.committed(BORIS, piece));
        assertEquals(1, plan.contributions());
    }

    @Test void twoPlacementsCannotOverlapAndAFailedOneCostsNothing() {
        var plan = yard();
        var first = cell(plan, 0);
        var second = cell(plan, 1);
        work(plan, ANNA, first, DURATION, 0);
        work(plan, BORIS, second, DURATION, 0);
        assertTrue(plan.beginPlacement(ANNA, first));
        assertFalse(plan.beginPlacement(BORIS, second), "the world places one piece at a time");
        assertFalse(plan.endPlacement());
        // Anna's placement failed in the world: nothing credited, her effort is still hers.
        assertEquals(0, plan.contributions());
        assertTrue(plan.ready(ANNA, first, DURATION));
        assertTrue(place(plan, BORIS, second, true));
        assertTrue(place(plan, ANNA, first, true));
        assertEquals(2, plan.contributions());
    }

    @Test void pausingStopsTheWholeCrewAndRemovingAProjectDropsItsEfforts() {
        var plan = yard();
        var piece = cell(plan, 0);
        long now = work(plan, ANNA, piece, DURATION, 0);
        assertTrue(plan.pause(true));
        plan.attend(ANNA, piece, .5, true, 5, now + 500);
        assertFalse(plan.ready(ANNA, piece, DURATION));
        assertFalse(plan.beginPlacement(ANNA, piece));
        assertFalse(plan.committed(ANNA, piece));
        assertTrue(plan.pause(false));
        assertTrue(plan.ready(ANNA, piece, DURATION));
        assertTrue(plan.remove(plan.list().getFirst().id()));
        assertNull(plan.cell(ANNA));
        assertFalse(plan.focus(ANNA, piece, now + 1_000), "the project is gone");
        assertFalse(plan.committed(ANNA, piece));
        assertEquals(0, plan.contributions());
    }

    @Test void aBuilderWhoLeavesFreesTheirPieceAndTheCrewSurvivesASave() {
        long clock = 1_700_000_000_000L; // wall time, as the work tick passes it
        var plan = yard();
        var piece = cell(plan, 0);
        assertTrue(plan.focus(ANNA, piece, clock));
        plan.attend(ANNA, piece, .5, true, 1, clock);
        plan.release(ANNA);
        assertFalse(plan.heldByAnother(BORIS, piece, clock + 1));
        assertEquals(0, plan.work(ANNA), "leaving drops unfinished effort, as changing piece always has");

        assertTrue(plan.focus(BORIS, piece, clock + 1_000));
        plan.attend(BORIS, piece, .5, true, 1, clock + 1_000);
        assertTrue(plan.focus(CLARA, cell(plan, 1), clock + 1_000));
        var reloaded = new Gson().fromJson(new Gson().toJson(plan), BuildingOrders.class);
        assertEquals(piece, reloaded.cell(BORIS));
        assertEquals(.5, reloaded.work(BORIS));
        // Who is standing where is not saved: after a load nobody blocks anybody.
        assertFalse(reloaded.heldByAnother(ANNA, piece, clock + 1_001));
        assertEquals(0, reloaded.crewSize(clock + 1_001));
        // Builders long gone are forgotten the next time someone picks a piece.
        assertTrue(reloaded.focus(ANNA, cell(reloaded, 2), clock + 1_001));
        assertNull(reloaded.cell(BORIS));
        assertNull(reloaded.cell(CLARA));
    }

    @Test void theUnnamedSlotOfOlderSavesStillWorksBesideACrew() {
        var plan = yard();
        var piece = cell(plan, 0);
        assertTrue(plan.focus(piece));
        for (int i = 0; i < 20; i++) plan.attend(piece, .5, true, 1);
        assertTrue(plan.focus(ANNA, cell(plan, 1), 0));
        // A named builder places the piece the old slot was on: the old effort cannot be paid as well.
        long now = work(plan, BORIS, piece, DURATION, 0);
        assertTrue(place(plan, BORIS, piece, true));
        assertNull(plan.cell());
        assertEquals(0, plan.work());
        assertFalse(plan.committed(piece));
        assertEquals(1, plan.contributions());
        assertEquals(cell(plan, 1), plan.cell(ANNA));
        assertEquals(1, plan.crewSize(now));
    }
}
