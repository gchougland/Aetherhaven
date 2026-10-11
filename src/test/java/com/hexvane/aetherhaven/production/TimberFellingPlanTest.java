package com.hexvane.aetherhaven.production;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.hexvane.aetherhaven.production.TimberHarvestPlan.*;

@Tag("town")
class TimberFellingPlanTest {
    private static final Position ROOT=new Position(0,100,0),TOP=new Position(0,101,0),SOIL=new Position(0,99,0),SIDE=new Position(1,100,0);
    private static final String WOOD="Wood_Beech_Trunk";
    private static final Observed AIR=new Observed(true,"Empty",0,false),DIRT=new Observed(true,"Soil_Dirt",0,false),LOG=new Observed(true,WOOD,0,false);
    private final Map<Position,Cell> cells=new LinkedHashMap<>(Map.of(ROOT,new Cell(ROOT,WOOD,0,Kind.TIMBER),TOP,new Cell(TOP,WOOD,0,Kind.TIMBER)));
    private TimberProductionState.Planting planted() {
        var receipt=new TimberPlantingReceipt(UUID.randomUUID(),"Plant_Sapling_Beech",WOOD,ROOT,0,List.of(
            new TimberPlantingReceipt.Stage(0,"seed",List.of(new Cell(ROOT,"Plant_Sapling_Beech",0,Kind.FOLIAGE))),
            new TimberPlantingReceipt.Stage(1,"mature",List.copyOf(cells.values()))),Set.of(SIDE));
        return new TimberProductionState.Planting(receipt,List.of(
            new TimberProductionState.Original(ROOT,"Empty",0),new TimberProductionState.Original(TOP,"Empty",0),
            new TimberProductionState.Original(SIDE,"Empty",0),new TimberProductionState.Original(SOIL,"Soil_Dirt",0)));
    }
    private Map<Position,Observed> world() {return new HashMap<>(Map.of(ROOT,LOG,TOP,LOG,SIDE,AIR,SOIL,DIRT));}
    private TimberFellingPlan.Inspection inspect(Map<Position,Observed> world) {
        return TimberFellingPlan.inspect(planted(),cells,p->true,world::get,(c,o)->c.blockId().equals(o.blockId())&&c.rotation()==o.rotation()&&c.filler()==o.filler());
    }
    @Test void intactOwnedTreeRestoresGroundAndCountsOnlyRealTrunks() {
        var world=world();var plan=inspect(world);assertTrue(plan.ready(),plan.issue());assertEquals(2,plan.plan().yield());
        assertTrue(plan.plan().restore(world::get,(p,b)->{world.put(p,b);return true;}));
        assertEquals(Map.of(ROOT,AIR,TOP,AIR,SIDE,AIR,SOIL,DIRT),world);
        assertFalse(plan.plan().restore(world::get,(p,b)->fail("Cannot fell the same snapshot twice")));
    }
    @Test void playerEditsAreLeftAloneAndOnlyReduceTheHarvest() {
        for(var changed:List.of(AIR,new Observed(true,"Furniture_Chest",0,true),new Observed(true,WOOD,1,false),new Observed(true,"Rock_Stone",0,false))) {
            var world=world();world.put(TOP,changed);var plan=inspect(world);
            assertTrue(plan.ready(),plan.issue());assertEquals(1,plan.plan().yield(),"Only the trunk that is still there is harvested");
            assertTrue(plan.plan().restore(world::get,(p,b)->{world.put(p,b);return true;}));
            assertEquals(AIR,world.get(ROOT));assertEquals(changed,world.get(TOP),"A changed cell is never overwritten");
        }
        var unloaded=world();unloaded.put(TOP,new Observed(false,WOOD,0,false));assertFalse(inspect(unloaded).ready());
        var world=world();world.put(SIDE,LOG);var plan=inspect(world);
        assertTrue(plan.ready());assertEquals(2,plan.plan().yield());
        assertTrue(plan.plan().restore(world::get,(p,b)->{world.put(p,b);return true;}));
        assertEquals(LOG,world.get(SIDE),"Do not remove connected player wood");
        var dug=world();dug.put(SOIL,AIR);plan=inspect(dug);assertTrue(plan.ready());
        assertTrue(plan.plan().restore(dug::get,(p,b)->{dug.put(p,b);return true;}));
        assertEquals(AIR,dug.get(SOIL),"Do not replace excavated soil");
    }
    @Test void aTreeThatIsEntirelyGoneIsReportedSoItsStoneCanGrowAgain() {
        var world=world();world.put(ROOT,AIR);world.put(TOP,AIR);
        var gone=inspect(world);assertFalse(gone.ready());assertEquals(TimberFellingPlan.GONE,gone.issue());
        var built=world();built.put(ROOT,new Observed(true,"Rock_Stone",0,false));built.put(TOP,new Observed(true,"Rock_Stone",0,false));
        assertEquals(TimberFellingPlan.GONE,inspect(built).issue(),"Blocks built where the tree stood are not the tree");
    }
    @Test void leftoverLeavesAreClearedWithoutAHarvest() {
        var leaf=new Position(1,101,0);var foliage=new Observed(true,"Plant_Leaves_Beech",0,false);
        cells.put(leaf,new Cell(leaf,"Plant_Leaves_Beech",0,Kind.FOLIAGE));
        var original=planted();var saved=new ArrayList<>(original.originals());saved.add(new TimberProductionState.Original(leaf,"Empty",0));
        var planted=new TimberProductionState.Planting(original.tree(),saved);
        var world=world();world.put(ROOT,AIR);world.put(TOP,AIR);world.put(leaf,foliage);
        var plan=TimberFellingPlan.inspect(planted,cells,p->true,world::get,(c,o)->c.blockId().equals(o.blockId()));
        assertTrue(plan.ready(),plan.issue());assertEquals(0,plan.plan().yield());
        assertTrue(plan.plan().restore(world::get,(p,b)->{world.put(p,b);return true;}));assertEquals(AIR,world.get(leaf));
    }
    @Test void lateEditsRejectTheWholeTransactionAndFailedWritesRestoreAppliedCells() {
        var world=world();var plan=inspect(world).plan();world.put(SIDE,LOG);
        assertFalse(plan.restore(world::get,(p,b)->fail("Recheck must finish before writing")));
        world.put(SIDE,AIR);var calls=new AtomicInteger();
        assertFalse(plan.restore(world::get,(p,b)->{if(calls.incrementAndGet()==2)return false;world.put(p,b);return true;}));
        assertEquals(world(),world);assertEquals(3,calls.get());
        assertTrue(inspect(world).ready(),"Successful rollback permits a later real retry");
    }
    @Test void missingOriginalsOrBoundsChangesFailBeforeWorldReads() {
        var original=planted();var malformed=new TimberProductionState.Planting(original.tree(),original.originals().subList(0,3));
        assertFalse(TimberFellingPlan.inspect(malformed,cells,p->true,p->fail("Missing ground receipt"),(c,o)->true).ready());
        assertFalse(TimberFellingPlan.inspect(original,cells,p->p.y()<=100,p->fail("Outside town"),(c,o)->true).ready());
    }
    @Test void nativeEarlierRootsCanVacateSoilButNeverRemoveNewBlocks() {
        var original=planted();var oldRoot=new Position(-1,99,0);var tree=original.tree();
        var receipt=new TimberPlantingReceipt(tree.id(),tree.sapling(),tree.output(),tree.root(),0,List.of(
            tree.stages().getFirst(),new TimberPlantingReceipt.Stage(1,"young",List.of(
                new Cell(ROOT,WOOD,0,Kind.TIMBER),new Cell(oldRoot,WOOD,0,Kind.TIMBER))),
            new TimberPlantingReceipt.Stage(2,"mature",tree.mature().cells())),tree.clearance());
        var saved=new ArrayList<>(original.originals());saved.add(new TimberProductionState.Original(oldRoot,"Soil_Dirt",0));
        var planted=new TimberProductionState.Planting(receipt,saved);var world=world();world.put(oldRoot,AIR);
        var inspected=TimberFellingPlan.inspect(planted,cells,p->true,world::get,(c,o)->c.blockId().equals(o.blockId()));
        assertTrue(inspected.ready(),inspected.issue());assertEquals(2,inspected.plan().yield(),"Earlier roots do not award additional wood");
        assertTrue(inspected.plan().restore(world::get,(p,b)->{world.put(p,b);return true;}));assertEquals(DIRT,world.get(oldRoot));
        var edited=world();var chest=new Observed(true,"Furniture_Chest",0,false);edited.put(oldRoot,chest);
        var kept=TimberFellingPlan.inspect(planted,cells,p->true,edited::get,(c,o)->c.blockId().equals(o.blockId()));
        assertTrue(kept.ready(),kept.issue());
        assertTrue(kept.plan().restore(edited::get,(p,b)->{edited.put(p,b);return true;}));
        assertEquals(chest,edited.get(oldRoot),"New blocks where an early root once grew are never removed");
    }
}
