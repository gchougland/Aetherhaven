package com.hexvane.aetherhaven.production;

import com.google.gson.Gson;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.hexvane.aetherhaven.production.TimberHarvestPlan.*;
import static com.hexvane.aetherhaven.production.TimberProductionState.*;

@Tag("town")
class TimberProductionStateTest {
    private static final String WOOD = "Wood_Beech_Trunk", SEED = "Plant_Sapling_Beech";
    private static final Ground AIR = new Ground(true, "Empty", 0, 0, true, false, false, false);
    private static final Ground SOIL = new Ground(true, "Soil_Dirt", 0, 0, false, true, true, false);
    private static final Function<Position, Ground> CLEAR = p -> p.y() < 100 ? SOIL : AIR;
    private TimberPlantingReceipt tree(int x) {
        var root = new Position(x, 100, 10);
        return new TimberPlantingReceipt(UUID.randomUUID(), SEED, WOOD, root, 0, List.of(
            new TimberPlantingReceipt.Stage(0, SEED, List.of(new Cell(root, SEED, 0, Kind.FOLIAGE))),
            new TimberPlantingReceipt.Stage(1, "mature", List.of(new Cell(root, WOOD, 0, Kind.TIMBER),
                new Cell(new Position(x, 98, 10), WOOD, 0, Kind.TIMBER)))),
            Set.of(new Position(x+1, 100, 10)));
    }

    @Test void preparationIncludesUndergroundOriginalsAndFillerClearanceWithoutAdoptingProduction() {
        var state = new TimberProductionState(); var tree = tree(10);
        var prepared = state.prepare(0, 1, tree, p -> true, CLEAR);
        assertTrue(prepared.ready(), prepared.issue());
        assertFalse(state.adopted()); assertTrue(state.trees().isEmpty());
        var originals = prepared.planting().originals();
        assertEquals(4, originals.size());
        assertTrue(originals.contains(new Original(new Position(10, 98, 10), "Soil_Dirt", 0)));
        assertTrue(originals.contains(new Original(new Position(10, 99, 10), "Soil_Dirt", 0)));
        assertTrue(originals.contains(new Original(new Position(11, 100, 10), "Empty", 0)));
        assertThrows(UnsupportedOperationException.class, () -> originals.clear());
    }

    @Test void failedPlacementKeepsLegacyStateAndSuccessfulRetryCannotPlantTwice() {
        var state = new TimberProductionState(); var prepared = state.prepare(0, 1, tree(10), p -> true, CLEAR);
        var writes = new AtomicInteger();
        assertNotNull(state.plant(0, 1, prepared, p -> true, CLEAR, () -> { writes.incrementAndGet(); return false; }));
        assertFalse(state.adopted()); assertTrue(state.trees().isEmpty());
        assertNull(state.plant(0, 1, prepared, p -> true, CLEAR, () -> { writes.incrementAndGet(); return true; }));
        assertTrue(state.adopted()); assertEquals(1, state.trees().size());
        assertNotNull(state.plant(0, 1, prepared, p -> true, CLEAR, () -> { writes.incrementAndGet(); return true; }));
        assertEquals(2, writes.get());
    }

    @Test void staleTerrainChangedPermissionsAndShrunkenAllowanceDoNotInvokePlacement() {
        var state = new TimberProductionState(); var tree = tree(10);
        var prepared = state.prepare(0, 1, tree, p -> true, CLEAR); var writes = new AtomicInteger();
        java.util.function.BooleanSupplier write = () -> { writes.incrementAndGet(); return true; };
        // A natural underground block is still not interchangeable with the saved one.
        Function<Position, Ground> edited = p -> p.y() == 98
            ? new Ground(true, "Rock_Stone", 1, 0, false, false, true, false) : CLEAR.apply(p);
        assertNotNull(state.plant(0, 1, prepared, p -> true, edited, write));
        assertNotNull(state.plant(0, 1, prepared, p -> p.x() <= 10, CLEAR, write));
        assertNotNull(state.plant(0, 0, prepared, p -> true, CLEAR, write));
        assertEquals(0, writes.get()); assertFalse(state.adopted());
    }

    @Test void overlapAndLockedSlotsRejectBeforeReadingTheWorld() {
        var state = new TimberProductionState(); var prepared = state.prepare(0, 2, tree(10), p -> true, CLEAR);
        assertNull(state.plant(0, 2, prepared, p -> true, CLEAR, () -> true));
        var reads = new AtomicInteger(); Function<Position, Ground> read = p -> { reads.incrementAndGet(); return CLEAR.apply(p); };
        assertFalse(state.prepare(1, 2, tree(11), p -> true, read).ready());
        assertFalse(state.prepare(2, 2, tree(30), p -> true, read).ready());
        assertFalse(state.prepare(0, WorkplaceProductionUpgrades.MAX_SLOT_COUNT+1, tree(30), p -> true, read).ready());
        assertEquals(0, reads.get());
        assertTrue(state.prepare(1, 2, tree(30), p -> true, read).ready());
    }

    @Test void unloadedWetProtectedOccupiedAndUnsupportedGroundCannotBePlanted() {
        for (var bad : List.of(new Ground(false, null, 0, 0, false, false, false, false),
                new Ground(true, "Empty", 0, 0, true, false, false, true),
                new Ground(true, "Empty", 0, 1, true, false, false, false),
                new Ground(true, "Wood_Beech_Trunk", 0, 0, false, false, false, false))) {
            var state = new TimberProductionState();
            assertFalse(state.prepare(0, 1, tree(10), p -> true, p -> p.y() == 100 ? bad : CLEAR.apply(p)).ready());
            assertFalse(state.adopted());
        }
        assertFalse(new TimberProductionState().prepare(0, 1, tree(10), p -> true, p -> AIR).ready(), "Soil is required");
        var buried = new Ground(true, "Furniture_Chest", 0, 0, false, false, false, false);
        assertFalse(new TimberProductionState().prepare(0, 1, tree(10), p -> true, p -> p.y() == 98 ? buried : CLEAR.apply(p)).ready());
    }

    @Test void plantedOwnershipAndLegacySuppressionSurviveSavingWithoutChangingStockOrUpgrades() {
        var production = new PlotProductionState(); production.addAmount(WOOD, 7, 100); production.setCobaltLevel(2);
        assertFalse(production.usesPhysicalTimber());
        var state = production.timber(); var prepared = state.prepare(0, 1, tree(10), p -> true, CLEAR);
        assertNull(state.plant(0, 1, prepared, p -> true, CLEAR, () -> true));
        var gson = new Gson(); var restored = gson.fromJson(gson.toJson(production), PlotProductionState.class);
        assertTrue(restored.usesPhysicalTimber()); assertEquals(7, restored.getAmount(WOOD)); assertEquals(2, restored.getCobaltLevel());
        assertEquals(prepared.planting(), restored.timber().trees().get(0));
        assertThrows(UnsupportedOperationException.class, () -> restored.timber().trees().clear());
        assertFalse(gson.fromJson("{}", PlotProductionState.class).usesPhysicalTimber());
        assertFalse(gson.fromJson("{\"timber\":{}}", PlotProductionState.class).usesPhysicalTimber());
    }

    @Test void planningAndMovingLocationsKeepLegacyProductionAndSurviveReload() {
        var production=new PlotProductionState();production.addAmount(WOOD,9,100);
        var state=production.timber();var first=tree(10);
        assertNull(state.plan(0,2,0,first,p->true,CLEAR));
        assertEquals(1,state.layoutRevision());assertEquals(first,state.groves().get(0));
        assertFalse(state.adopted());assertTrue(state.trees().isEmpty());assertFalse(production.usesPhysicalTimber());
        var moved=tree(40);assertNull(state.plan(0,2,1,moved,p->true,CLEAR));
        var gson=new Gson();var restored=gson.fromJson(gson.toJson(production),PlotProductionState.class);
        assertEquals(moved,restored.timber().groves().get(0));assertEquals(2,restored.timber().layoutRevision());
        assertEquals(9,restored.getAmount(WOOD));assertFalse(restored.usesPhysicalTimber());
        assertThrows(UnsupportedOperationException.class,()->restored.timber().groves().clear());
        assertNull(restored.timber().removePlan(0,2));assertTrue(restored.timber().groves().isEmpty());
        assertFalse(restored.usesPhysicalTimber());
    }

    @Test void staleReviewsCannotOverwriteOrRemoveAnotherPlayersGroveEdits() {
        var state=new TimberProductionState();assertNull(state.plan(0,2,0,tree(10),p->true,CLEAR));
        var readCount=new AtomicInteger();Function<Position,Ground> read=p->{readCount.incrementAndGet();return CLEAR.apply(p);};
        assertNotNull(state.plan(1,2,0,tree(30),p->true,read));assertEquals(0,readCount.get());
        assertNotNull(state.removePlan(0,0));assertEquals(1,state.groves().size());
        assertNotNull(state.plan(2,2,1,tree(30),p->true,read));assertEquals(0,readCount.get());
        assertFalse(state.prepare(1,2,tree(11),p->true,read).ready());assertEquals(0,readCount.get());
        assertEquals(1,state.layoutRevision());assertFalse(state.adopted());
    }

    @Test void savedPlansReserveFutureGrowthAcrossLumbermillsWithoutClaimingOwnership() {
        var state=new TimberProductionState();assertNull(state.plan(0,2,0,tree(10),p->true,CLEAR));
        assertTrue(state.conflictsWith(tree(11)),"Hitbox clearance is reserved even before planting");
        assertFalse(state.conflictsWith(tree(30)));assertTrue(state.trees().isEmpty());
        assertNull(state.removePlan(0,1));assertFalse(state.conflictsWith(tree(11)));
        var prepared=state.prepare(0,2,tree(10),p->true,CLEAR);
        assertNull(state.plant(0,2,prepared,p->true,CLEAR,()->true));assertTrue(state.conflictsWith(tree(11)));
        assertNotNull(state.plan(0,2,state.layoutRevision(),tree(40),p->true,CLEAR),"A planted tree cannot be moved by editing its plan");
        assertNotNull(state.removePlan(0,state.layoutRevision()));assertTrue(state.adopted());
    }

    @Test void plantingRechecksSavedPlansAgainstLaterTreesAndTerrain() {
        var state=new TimberProductionState();var first=tree(10);assertNull(state.plan(0,2,0,first,p->true,CLEAR));
        assertNull(state.plan(1,2,1,tree(30),p->true,CLEAR));
        var altered=tree(29);assertFalse(state.prepare(0,2,altered,p->true,CLEAR).ready(),"Changing a planned species or shape must not overlap another tree");
        var prepared=state.prepare(0,2,first,p->true,CLEAR);assertTrue(prepared.ready());
        var attempts=new AtomicInteger();
        assertNotNull(state.plant(0,2,prepared,p->true,p->p.y()==100?SOIL:CLEAR.apply(p),()->{attempts.incrementAndGet();return true;}));
        assertEquals(0,attempts.get());assertFalse(state.adopted());assertEquals(2,state.groves().size());
    }
}
