package com.hexvane.aetherhaven.workplace;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** N25, N47: new projects reach a Builder's yard by themselves, and only one yard. Saved town state only. */
@Tag("town")
class BuildingProjectAutoQueueTest {
    private static final Gson GSON = new Gson();
    private static final UUID OWNER = UUID.randomUUID();

    /** Yard i sits at x = i * 100. */
    private static TownRecord town(TownsfolkWorkType... types) {
        var sites = new ArrayList<ApprenticeWorkplace>();
        for (int i = 0; i < types.length; i++)
            sites.add(new ApprenticeWorkplace("Yard " + i, types[i], new WorkplaceArea(i * 100, 128, 0, i * 100 + 6, 6)));
        return GSON.fromJson(GSON.toJson(Map.of("ownerUuid", OWNER.toString(), "apprenticeWorkplaces", Map.of("sites", sites))), TownRecord.class);
    }
    private static PlotInstance plot(TownRecord town, PlotInstanceState state, int x) {
        var plot = new PlotInstance(UUID.randomUUID(), "plot_house", state, new PlotFootprintRecord(x, 128, 0, x + 9, 138, 9), x, 128, 0, 0);
        town.addPlotInstance(plot);
        return plot;
    }
    private static ApprenticeWorkplace yard(TownRecord town, int index) { return town.getApprenticeWorkplaces().sites().get(index); }
    private static List<UUID> queued(TownRecord town, int index) {
        var plan = yard(town, index).existingBuilding();
        return plan == null ? List.of() : plan.list().stream().map(BuildingOrders.Order::plot).toList();
    }
    private static long ordersFor(TownRecord town, UUID project) {
        return town.getApprenticeWorkplaces().sites().stream().filter(s -> s.existingBuilding() != null)
            .flatMap(s -> s.existingBuilding().list().stream()).filter(o -> o.plot().equals(project)).count();
    }

    @Test void aPlacedBuildingAppearsOnTheYardWithoutThePlayerAddingIt() {
        var town = town(TownsfolkWorkType.BUILDING);
        var placed = plot(town, PlotInstanceState.BLUEPRINTING, 20);
        assertSame(yard(town, 0), BuildingProjectAutoQueue.offer(town, placed, OWNER));
        assertEquals(List.of(placed.getPlotId()), queued(town, 0));
        assertEquals(OWNER, yard(town, 0).building().list().getFirst().author());
        // Starting the build offers it again; the board still has it once.
        placed.setState(PlotInstanceState.ASSEMBLING);
        assertSame(yard(town, 0), BuildingProjectAutoQueue.offer(town, placed, OWNER));
        assertFalse(BuildingProjectAutoQueue.offerWaiting(town));
        assertEquals(1, ordersFor(town, placed.getPlotId()));
        // The saved town remembers it.
        var reloaded = GSON.fromJson(GSON.toJson(town), TownRecord.class);
        assertTrue(reloaded.findPlotById(placed.getPlotId()).isBuilderYardOffered());
        assertEquals(1, ordersFor(reloaded, placed.getPlotId()));
    }

    @Test void twoYardsShareProjectsAndNeverHoldTheSameOne() {
        var town = town(TownsfolkWorkType.BUILDING, TownsfolkWorkType.COOKING, TownsfolkWorkType.BUILDING);
        var projects = new ArrayList<PlotInstance>();
        for (int i = 0; i < 6; i++) {
            var project = plot(town, PlotInstanceState.ASSEMBLING, 190);
            projects.add(project);
            assertNotNull(BuildingProjectAutoQueue.offer(town, project, OWNER));
            // Every later event for the same project is harmless.
            BuildingProjectAutoQueue.offer(town, project, OWNER);
            BuildingProjectAutoQueue.offerWaiting(town);
        }
        for (var project : projects) assertEquals(1, ordersFor(town, project.getPlotId()), "exactly one order per project");
        assertEquals(3, queued(town, 0).size(), "least busy yard takes the next project");
        assertEquals(3, queued(town, 2).size());
        assertNull(yard(town, 1).existingBuilding(), "a kitchen is never given building work");
        // Both yards empty: the nearer yard (x 200) took the first project, not the one made first.
        assertEquals(projects.getFirst().getPlotId(), queued(town, 2).getFirst());
    }

    @Test void aPausedYardIsUsedOnlyWhenNoYardIsRunning() {
        var town = town(TownsfolkWorkType.BUILDING, TownsfolkWorkType.BUILDING);
        var near = yard(town, 0).building();
        near.pause(true);
        var first = plot(town, PlotInstanceState.ASSEMBLING, 0);
        assertSame(yard(town, 1), BuildingProjectAutoQueue.offer(town, first, OWNER), "running yard wins although it is farther");
        var far = yard(town, 1).building();
        far.pause(true);
        var second = plot(town, PlotInstanceState.ASSEMBLING, 0);
        assertSame(yard(town, 0), BuildingProjectAutoQueue.offer(town, second, OWNER), "all paused: least busy again");
    }

    @Test void anOrderThePlayerRemovedIsNotPutBack() {
        var town = town(TownsfolkWorkType.BUILDING, TownsfolkWorkType.BUILDING);
        var project = plot(town, PlotInstanceState.ASSEMBLING, 0);
        var chosen = BuildingProjectAutoQueue.offer(town, project, OWNER);
        var plan = chosen.building();
        assertTrue(plan.remove(plan.list().getFirst().id()));
        assertNull(BuildingProjectAutoQueue.offer(town, project, OWNER));
        assertFalse(BuildingProjectAutoQueue.offerWaiting(town));
        assertEquals(0, ordersFor(town, project.getPlotId()));
        // Adding it by hand afterwards still works, and stays the only order.
        assertNull(plan.add(project.getPlotId(), OWNER));
        assertSame(chosen, BuildingProjectAutoQueue.offer(town, project, OWNER));
        assertEquals(1, ordersFor(town, project.getPlotId()));
    }

    @Test void aProjectQueuedByHandIsNotQueuedAgainOnAnotherYard() {
        var town = town(TownsfolkWorkType.BUILDING, TownsfolkWorkType.BUILDING);
        var project = plot(town, PlotInstanceState.ASSEMBLING, 0);
        var busy = yard(town, 1).building();
        for (int i = 0; i < 5; i++) assertNull(busy.add(plot(town, PlotInstanceState.ASSEMBLING, 0).getPlotId(), OWNER));
        assertNull(busy.add(project.getPlotId(), OWNER));
        assertSame(yard(town, 1), BuildingProjectAutoQueue.offer(town, project, OWNER));
        assertEquals(1, ordersFor(town, project.getPlotId()));
        assertTrue(queued(town, 0).isEmpty());
    }

    @Test void projectsStartedWhileEveryBoardWasFullAreQueuedWhenRoomAppears() {
        var town = town(TownsfolkWorkType.BUILDING);
        var plan = yard(town, 0).building();
        var filling = new ArrayList<PlotInstance>();
        for (int i = 0; i < BuildingOrders.CAPACITY; i++) {
            var project = plot(town, PlotInstanceState.ASSEMBLING, 0);
            filling.add(project);
            assertNotNull(BuildingProjectAutoQueue.offer(town, project, OWNER));
        }
        var waiting = plot(town, PlotInstanceState.ASSEMBLING, 0);
        waiting.setAssemblyOwnerUuid(OWNER);
        assertNull(BuildingProjectAutoQueue.offer(town, waiting, OWNER));
        assertFalse(waiting.isBuilderYardOffered());
        assertFalse(BuildingProjectAutoQueue.offerWaiting(town));
        // One project finishes; its completion sweep finds the waiting one.
        filling.getFirst().setState(PlotInstanceState.COMPLETE);
        var saves = new int[1];
        BuildingProjectCleanup.afterCompletion(Runnable::run, town, filling.getFirst().getPlotId(), () -> saves[0]++);
        assertEquals(1, saves[0]);
        assertEquals(BuildingOrders.CAPACITY, plan.list().size());
        assertEquals(1, ordersFor(town, waiting.getPlotId()));
        assertEquals(0, ordersFor(town, filling.getFirst().getPlotId()));
    }

    @Test void aBoardFullOfProjectsThatNoLongerExistStillTakesALiveOne() {
        var town = town(TownsfolkWorkType.BUILDING);
        var plan = yard(town, 0).building();
        for (int i = 0; i < BuildingOrders.CAPACITY; i++) assertNull(plan.add(UUID.randomUUID(), OWNER));
        var live = plot(town, PlotInstanceState.ASSEMBLING, 0);
        assertSame(yard(town, 0), BuildingProjectAutoQueue.offer(town, live, OWNER));
        assertEquals(BuildingOrders.CAPACITY, plan.list().size());
        assertEquals(1, ordersFor(town, live.getPlotId()));
    }

    @Test void nothingIsQueuedWithoutAYardOrForSomeoneWhoMayNotManageBuildings() {
        var noYard = town(TownsfolkWorkType.COOKING);
        var project = plot(noYard, PlotInstanceState.ASSEMBLING, 0);
        assertNull(BuildingProjectAutoQueue.offer(noYard, project, OWNER));
        assertFalse(BuildingProjectAutoQueue.offerWaiting(noYard));
        assertFalse(project.isBuilderYardOffered(), "it is offered later, once the town has a yard");

        var town = town(TownsfolkWorkType.BUILDING);
        var placed = plot(town, PlotInstanceState.BLUEPRINTING, 0);
        assertNull(BuildingProjectAutoQueue.offer(town, placed, UUID.randomUUID()), "a stranger's order could never run");
        assertFalse(placed.isBuilderYardOffered());
        assertFalse(BuildingProjectAutoQueue.offerWaiting(town), "plans that were never started are not swept up");
        var done = plot(town, PlotInstanceState.COMPLETE, 0);
        assertNull(BuildingProjectAutoQueue.offer(town, done, OWNER));
        assertTrue(queued(town, 0).isEmpty());
    }

    @Test void aYardTurnedIntoAnotherWorkplaceCannotLeaveASecondOrderBehind() {
        var town = town(TownsfolkWorkType.COOKING, TownsfolkWorkType.BUILDING);
        var project = plot(town, PlotInstanceState.ASSEMBLING, 0);
        // The kitchen used to be a yard and still has its old board.
        var old = yard(town, 0).building();
        assertNull(old.add(project.getPlotId(), OWNER));
        assertSame(yard(town, 1), BuildingProjectAutoQueue.offer(town, project, OWNER));
        assertEquals(1, ordersFor(town, project.getPlotId()));
        assertTrue(old.list().isEmpty());
    }
}
