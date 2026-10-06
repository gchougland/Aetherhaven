package com.hexvane.aetherhaven.production;

import com.google.gson.Gson;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.hexvane.aetherhaven.production.TimberHarvestPlan.*;

@Tag("town")
class TimberFreePlantingTest {
    private static final String SEED="Plant_Sapling_Beech",WOOD="Wood_Beech_Trunk";
    private static final Function<Position,TimberProductionState.Ground> CLEAR=p->new TimberProductionState.Ground(
        true,p.y()<100?"Soil_Dirt":"Empty",0,0,p.y()>=100,p.y()<100,p.y()<100,false);
    private TimberPlantingReceipt tree(int x) {
        var root=new Position(x,100,0);
        return new TimberPlantingReceipt(UUID.randomUUID(),SEED,WOOD,root,0,List.of(
            new TimberPlantingReceipt.Stage(0,SEED,List.of(new Cell(root,SEED,0,Kind.FOLIAGE))),
            new TimberPlantingReceipt.Stage(1,"mature",List.of(new Cell(root,WOOD,0,Kind.TIMBER)))));
    }
    private TimberProductionState ready(TimberPlantingReceipt plan) {
        var state=new TimberProductionState();assertNull(state.plan(0,2,0,plan,p->true,CLEAR));
        assertNull(state.activate(1));return state;
    }
    @Test void activationIsExplicitAndNeedsNoSaplings() {
        var state=new TimberProductionState();assertNotNull(state.activate(0));assertFalse(state.adopted());
        var plan=tree(0);assertNull(state.plan(0,1,0,plan,p->true,CLEAR));
        assertNotNull(state.activate(0));assertFalse(state.adopted());
        assertNull(state.activate(1));assertTrue(state.adopted());assertEquals(2,state.layoutRevision());
        assertNotNull(state.activate(2));assertTrue(state.trees().isEmpty());
    }
    @Test void freePlantingAndReplantingProduceOnlyTheHarvest() {
        var plan=tree(0);var p=new PlotProductionState();var state=p.timber();
        assertNull(state.plan(0,1,0,plan,pnt->true,CLEAR));assertNull(state.activate(1));
        for(int cycle=0;cycle<2;cycle++) {
            var prepared=state.prepare(0,1,plan,pnt->true,CLEAR);
            assertNull(state.plant(0,1,prepared,pnt->true,CLEAR,()->true));

            assertTrue(state.fell(0,plan.id(),20,Map.of(SEED,1),()->true));
            p=new Gson().fromJson(new Gson().toJson(p),PlotProductionState.class);state=p.timber();
            while(state.cargo()!=null) {
                assertTrue(state.pickUp(16)>0);assertTrue(state.deliver(p,100,16)>0);
            }
            assertEquals(cycle+1,p.getAmount(SEED));
            assertEquals((cycle+1)*20,p.getAmount(WOOD));
            assertEquals(0,state.deliver(p,100,16));
        }
    }
    @Test void releasingAnEditedTreeLeavesItsPlanButCannotRefundOrHarvestIt() {
        var plan=tree(0);var state=ready(plan);
        var prepared=state.prepare(0,1,plan,p->true,CLEAR);assertNull(state.plant(0,1,prepared,p->true,CLEAR,()->true));
        assertNotNull(state.releaseEdited(0,state.layoutRevision()));
        state.markEdited(plan.root());assertNotNull(state.releaseEdited(0,0));
        assertNull(state.releaseEdited(0,state.layoutRevision()));assertTrue(state.trees().isEmpty());
        assertEquals(plan,state.groves().get(0));assertNull(state.cargo());assertTrue(state.adopted());
        assertFalse(state.fell(0,plan.id(),20,()->fail("Released ownership cannot remove blocks")));
    }
}
