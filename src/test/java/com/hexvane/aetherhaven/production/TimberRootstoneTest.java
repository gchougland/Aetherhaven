package com.hexvane.aetherhaven.production;

import com.google.gson.Gson;
import java.util.*;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.hexvane.aetherhaven.production.TimberHarvestPlan.*;

@Tag("town")
class TimberRootstoneTest {
    private static final String SEED="Plant_Sapling_Beech",WOOD="Wood_Beech_Trunk";
    private static final Function<Position,TimberProductionState.Ground> CLEAR=p->new TimberProductionState.Ground(
        true,p.y()<100?"Soil_Dirt":"Empty",0,0,p.y()>=100,p.y()<100,p.y()<100,false);
    private TimberPlantingReceipt tree(int x) {
        var root=new Position(x,100,0);
        return new TimberPlantingReceipt(UUID.randomUUID(),SEED,WOOD,root,0,List.of(
            new TimberPlantingReceipt.Stage(0,SEED,List.of(new Cell(root,SEED,0,Kind.FOLIAGE))),
            new TimberPlantingReceipt.Stage(1,"mature",List.of(new Cell(root,WOOD,0,Kind.TIMBER)))));
    }
    private static void plant(TimberProductionState state,TimberPlantingReceipt plan) {
        var prepared=state.prepare(0,2,plan,p->true,CLEAR);assertNull(state.plant(0,2,prepared,p->true,CLEAR,()->true));
    }
    @Test void bindingAStoneIsTheOnlyStepNeededToStartTheGrove() {
        var state=new TimberProductionState();assertFalse(state.adopted());assertEquals(0,state.freeStone(2));
        var plan=tree(0);assertEquals(-1,state.stoneAt(new Position(0,99,0)));
        assertNull(state.plan(0,2,0,plan,p->true,CLEAR));
        assertTrue(state.adopted());assertTrue(state.trees().isEmpty());
        assertEquals(0,state.stoneAt(new Position(0,99,0)));assertEquals(-1,state.stoneAt(plan.root()));
        assertEquals(1,state.freeStone(2));assertEquals(-1,state.freeStone(1));
        assertNotNull(state.plan(2,2,state.layoutRevision(),tree(40),p->true,CLEAR),"A stone beyond the lumbermill's allowance stays asleep");
        assertNotNull(state.plan(1,TimberProductionState.MAX_STONES+1,state.layoutRevision(),tree(40),p->true,CLEAR));
    }
    @Test void freePlantingAndReplantingProduceOnlyTheHarvest() {
        var plan=tree(0);var p=new PlotProductionState();var state=p.timber();
        assertNull(state.plan(0,1,0,plan,pnt->true,CLEAR));
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
    @Test void uprootingFreesTheStoneAndLeavesTheTreeToThePlayer() {
        var plan=tree(0);var state=new TimberProductionState();assertNull(state.plan(0,2,0,plan,p->true,CLEAR));
        plant(state,plan);state.work(0,1);long revision=state.layoutRevision();
        assertTrue(state.uproot(0));assertTrue(state.trees().isEmpty());assertTrue(state.groves().isEmpty());
        assertEquals(0,state.chopping(0));assertEquals(-1,state.stoneAt(new Position(0,99,0)));assertTrue(state.layoutRevision()>revision);
        assertFalse(state.fell(0,plan.id(),20,()->fail("An uprooted tree belongs to the player")));
        assertFalse(state.uproot(0));assertTrue(state.adopted());assertEquals(0,state.freeStone(2));
    }
    @Test void aBlessingDoublesTheWoodOfOneHarvestOnly() {
        var plan=tree(0);var state=new TimberProductionState();
        assertNotNull(state.blessingIssue(0));assertFalse(state.bless(0));
        assertNull(state.plan(0,2,0,plan,p->true,CLEAR));
        assertNull(state.blessingIssue(0));assertTrue(state.bless(0));assertTrue(state.blessed(0));
        assertNotNull(state.blessingIssue(0));assertFalse(state.bless(0),"A second blessing would only waste Life Essence");
        var saved=new Gson().fromJson(new Gson().toJson(state),TimberProductionState.class);assertTrue(saved.blessed(0));
        plant(state,plan);
        assertFalse(state.fell(0,plan.id(),20,()->false));assertTrue(state.blessed(0),"A failed felling keeps the blessing");
        assertTrue(state.fell(0,plan.id(),20,Map.of(SEED,1),()->true));
        assertEquals(40,state.cargo().remaining());assertEquals(Map.of(WOOD,40,SEED,1),state.waitingProducts());
        assertFalse(state.blessed(0));
        var p=new PlotProductionState();
        while(state.cargo()!=null){state.pickUp(16);state.deliver(p,100,16);}
        plant(state,plan);assertTrue(state.fell(0,plan.id(),20,()->true));assertEquals(20,state.cargo().remaining());
    }
    @Test void aTreeThatVanishedKeepsItsStoneSoItCanGrowAgain() {
        var plan=tree(0);var state=new TimberProductionState();assertNull(state.plan(0,2,0,plan,p->true,CLEAR));
        plant(state,plan);state.work(0,1);
        assertFalse(state.release(0,UUID.randomUUID()));assertFalse(state.release(1,plan.id()));
        assertTrue(state.release(0,plan.id()));assertTrue(state.trees().isEmpty());assertEquals(0,state.chopping(0));
        assertEquals(plan,state.groves().get(0));assertNull(state.cargo());
        assertTrue(state.prepare(0,2,plan,p->true,CLEAR).ready());
    }
    @Test void millChoicesAreOffByDefaultAndSurviveReload() {
        var p=new PlotProductionState();assertFalse(p.timber().gathersFibre());assertNull(p.timber().charWood());
        p.timber().setCharWood(WOOD);
        p=new Gson().fromJson(new Gson().toJson(p),PlotProductionState.class);
        assertEquals(WOOD,p.timber().charWood());assertFalse(p.timber().gathersFibre());
        assertFalse(TimberCharring.enabled(p),"Charring waits until the lumbermill tends a grove");
        var old=new Gson().fromJson("{\"timber\":{\"adopted\":true}}",PlotProductionState.class);
        assertNull(old.timber().charWood());assertFalse(old.timber().blessed(0));assertEquals(0,old.timber().freeStone(2));
    }
}
