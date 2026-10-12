package com.hexvane.aetherhaven.workplace;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.*;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class BuildingProjectCleanupTest {
    private static final Gson GSON = new Gson();
    private static final UUID AUTHOR = UUID.randomUUID();

    @Test void synchronousFinalPlacementKeepsContributionAndMasteryCreditBeforeDeferredRetirement() {
        var town = townWithSites(TownsfolkWorkType.BUILDING);
        var plot = plot(PlotInstanceState.ASSEMBLING);town.getPlotInstances().add(plot);
        var plan = town.getApprenticeWorkplaces().sites().getFirst().building();
        var order = add(plan, plot.getPlotId());
        var cell = cell(order);plan.focus(cell);
        for (int i = 0; i < 10; i++) plan.attend(cell, .5, true, 1);
        var worldQueue = new ArrayDeque<Runnable>();
        var saves = new AtomicInteger();
        long before = plan.revision();

        // advancePlacementAtIndex invokes ConstructionCompleter before NativeBuildingWork.place can commit.
        plot.setState(PlotInstanceState.COMPLETE);
        BuildingProjectCleanup.afterCompletion(worldQueue::add, town, plot.getPlotId(), saves::incrementAndGet);
        assertEquals(before, plan.revision());
        assertNotNull(plan.find(order.id()));
        assertTrue(plan.ready(cell, BuildingOrders.duration(1, 1)));
        var progress = new TownsfolkProgress();
        boolean placed = plan.committed(cell);
        assertTrue(placed);
        TownsfolkMastery.completed(progress, TownsfolkMastery.Work.BUILDING_BLOCK, placed);
        assertEquals(1, plan.contributions());
        assertEquals(TownsfolkMastery.Work.BUILDING_BLOCK.xp, progress.masteryXp());

        worldQueue.removeFirst().run();
        assertTrue(plan.list().isEmpty());
        assertNull(plan.cell());assertEquals(0, plan.work());
        assertEquals(before + 1, plan.revision());assertEquals(1, saves.get());
        assertEquals(1, plan.contributions());
        assertEquals(0, TownsfolkMastery.completed(progress, TownsfolkMastery.Work.BUILDING_BLOCK, plan.committed(cell)));
        assertEquals(1, progress.masteryXp());
        BuildingProjectCleanup.afterCompletion(worldQueue::add, town, plot.getPlotId(), saves::incrementAndGet);
        worldQueue.removeFirst().run();
        assertEquals(1, saves.get());assertEquals(before + 1, plan.revision());
    }

    @Test void reentrantTaskDrainAndReconciliationCannotRemoveTheFinalCellBeforeItCommits() {
        var town = townWithSites(TownsfolkWorkType.BUILDING);
        var plot = plot(PlotInstanceState.ASSEMBLING);town.getPlotInstances().add(plot);
        var plan = town.getApprenticeWorkplaces().sites().getFirst().building();
        var order = add(plan, plot.getPlotId());var cell = cell(order);plan.focus(cell);
        var worldQueue = new ArrayDeque<Runnable>();var saves = new AtomicInteger();
        long before = plan.revision();
        assertTrue(plan.beginPlacement(cell));
        assertFalse(plan.beginPlacement(cell));
        plot.setState(PlotInstanceState.COMPLETE);
        BuildingProjectCleanup.afterCompletion(worldQueue::add, town, plot.getPlotId(), saves::incrementAndGet);
        worldQueue.removeFirst().run(); // Native ChunkGrid can consume the queue inside finalization.
        assertFalse(BuildingProjectCleanup.reconcile(town));
        assertEquals(before, plan.revision());assertNotNull(plan.find(order.id()));assertEquals(0, saves.get());
        boolean progressed = plan.committed(cell);
        assertTrue(progressed);assertEquals(1, plan.contributions());
        assertTrue(plan.endPlacement()); // NativeBuildingWork finally persists any delayed retirement.
        var progress = new TownsfolkProgress();
        assertEquals(1, TownsfolkMastery.completed(progress, TownsfolkMastery.Work.BUILDING_BLOCK, progressed));
        assertTrue(plan.list().isEmpty());assertEquals(before + 1, plan.revision());
        assertEquals(1, plan.contributions());assertEquals(1, progress.masteryXp());
        assertFalse(plan.endPlacement());assertFalse(plan.committed(cell));
    }

    @Test void failedPlacementStillReleasesItsGuardWithoutInventingCredit() {
        var town = townWithSites(TownsfolkWorkType.BUILDING);
        var plan = town.getApprenticeWorkplaces().sites().getFirst().building();
        var plot = plot(PlotInstanceState.COMPLETE);town.getPlotInstances().add(plot);
        var order = add(plan, plot.getPlotId());var cell = cell(order);plan.focus(cell);
        assertTrue(plan.beginPlacement(cell));
        assertFalse(BuildingProjectCleanup.reconcile(town));
        assertTrue(plan.endPlacement());
        assertEquals(0, plan.contributions());assertTrue(plan.list().isEmpty());
        var other = add(plan, UUID.randomUUID());plan.focus(cell(other));
        assertTrue(plan.beginPlacement(cell(other)));assertFalse(plan.endPlacement());
    }

    @Test void savedCompletedEntriesDisappearFromEveryYardWithoutChangingUnrelatedProgressOrPauses() {
        var town = townWithSites(TownsfolkWorkType.BUILDING, TownsfolkWorkType.BUILDING, TownsfolkWorkType.MINING);
        var complete = plot(PlotInstanceState.COMPLETE);
        var active = plot(PlotInstanceState.ASSEMBLING);
        var planned = plot(PlotInstanceState.BLUEPRINTING);
        town.getPlotInstances().addAll(List.of(complete, active, planned));
        UUID unavailable = UUID.randomUUID();
        for (var site : town.getApprenticeWorkplaces().sites()) {
            var plan = site.building();
            add(plan, complete.getPlotId());var first = add(plan, active.getPlotId());
            add(plan, unavailable);add(plan, planned.getPlotId());
            plan.focus(cell(first));assertTrue(plan.committed(cell(first)));
            plan.focus(cell(first));plan.attend(cell(first), .5, true, 1);
            plan.pause(true);
        }
        // Restored queues can include inactive yard plans after a workplace is redesignated.
        town = GSON.fromJson(GSON.toJson(town), TownRecord.class);
        assertTrue(BuildingProjectCleanup.reconcile(town));
        for (var site : town.getApprenticeWorkplaces().sites()) {
            var plan = site.building();
            assertEquals(List.of(active.getPlotId(), unavailable, planned.getPlotId()), plan.list().stream().map(BuildingOrders.Order::plot).toList());
            assertTrue(plan.paused());assertEquals(1, plan.contributions());assertEquals(.5, plan.work());
            assertEquals(plan.list().getFirst().id(), plan.cell().order());
            assertEquals(6, plan.revision());
            // The board moved since any page drew it. A press still acts only on the project it names.
            assertFalse(plan.remove(UUID.randomUUID()), "A project that is not on the board cannot be removed");
            assertNotNull(plan.add(active.getPlotId(), AUTHOR), "A project already on the board is not queued twice");
            assertEquals(6, plan.revision(), "A refused press changes nothing");
        }
        String after = GSON.toJson(town);
        assertFalse(BuildingProjectCleanup.reconcile(town));
        assertEquals(after, GSON.toJson(town));
    }

    @Test void clearingCompletedFocusedOrderDiscardsOnlyItsUnfinishedAttendance() {
        var town = townWithSites(TownsfolkWorkType.BUILDING, TownsfolkWorkType.COOKING);
        var plan = town.getApprenticeWorkplaces().sites().getFirst().building();
        var complete = plot(PlotInstanceState.COMPLETE);town.getPlotInstances().add(complete);
        var done = add(plan, complete.getPlotId());var next = add(plan, UUID.randomUUID());
        var cell = cell(done);plan.focus(cell);plan.attend(cell, .5, true, 1);plan.pause(true);
        assertTrue(BuildingProjectCleanup.reconcile(town));
        assertEquals(List.of(next), plan.list());assertTrue(plan.paused());
        assertNull(plan.cell());assertEquals(0, plan.work());assertEquals(0, plan.contributions());
        assertFalse(plan.committed(cell));
        assertNull(town.getApprenticeWorkplaces().sites().getLast().existingBuilding());
    }

    @Test void completedWallRecordsReconcileButUnrecordedDecorationNeedsExplicitCompletionSignal() {
        var town = townWithSites(TownsfolkWorkType.BUILDING);
        var plan = town.getApprenticeWorkplaces().sites().getFirst().building();
        UUID wall = UUID.randomUUID(), decoration = UUID.randomUUID();
        add(plan, wall);var decorationOrder = add(plan, decoration);
        town.addWallSegment(new WallSegmentRecord(wall, "wall", new PlotFootprintRecord(0, 0, 0, 1, 2, 1), 0, 0, 0, Rotation.None, 0));
        assertTrue(BuildingProjectCleanup.reconcile(town));
        assertEquals(List.of(decorationOrder), plan.list());
        var worldQueue = new ArrayDeque<Runnable>();var saves = new AtomicInteger();
        BuildingProjectCleanup.afterCompletion(worldQueue::add, town, decoration, saves::incrementAndGet);
        assertEquals(List.of(decorationOrder), plan.list());
        worldQueue.removeFirst().run();
        assertTrue(plan.list().isEmpty());assertEquals(1, saves.get());
    }

    private static TownRecord townWithSites(TownsfolkWorkType... types) {
        var sites = new ArrayList<ApprenticeWorkplace>();
        for (int i = 0; i < types.length; i++) sites.add(new ApprenticeWorkplace("Yard " + i, types[i], new WorkplaceArea(i * 10, 128, 0, i * 10 + 5, 5)));
        return GSON.fromJson(GSON.toJson(Map.of("apprenticeWorkplaces", Map.of("sites", sites))), TownRecord.class);
    }
    private static PlotInstance plot(PlotInstanceState state) {
        return new PlotInstance(UUID.randomUUID(), "plot_house", state, new PlotFootprintRecord(0, 128, 0, 10, 138, 10), 0, 128, 0, 0);
    }
    private static BuildingOrders.Order add(BuildingOrders plan, UUID plot) {
        assertNull(plan.add(plot, AUTHOR));return plan.list().getLast();
    }
    private static BuildingOrders.Cell cell(BuildingOrders.Order order) {
        return new BuildingOrders.Cell(order.id(), 42, 1, 129, 1, "Rock_Stone", 0);
    }
}
