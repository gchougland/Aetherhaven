package com.hexvane.aetherhaven.production;

import com.google.gson.Gson;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class MiningCycleTest {
    private MiningCycle ready() {
        var cycle = new MiningCycle(); cycle.select("Ore_Copper", 10);
        for (int i = 0; i < 40; i++) cycle.simulate(.25);
        return cycle;
    }
    @Test void cooldownNeverProducesWithoutPhysicalWorkAndNeverCatchesUp() {
        var c = new MiningCycle(); c.select("Ore_Copper",10);
        c.simulate(10000); assertEquals(9.75,c.remaining());
        c.simulate(Double.NaN); c.simulate(-1); assertEquals(9.75,c.remaining());
        c=ready(); assertFalse(c.extract("Ore_Copper",10,true,()->true));
        for(int i=0;i<100;i++) { c.work(.25,false,true); c.work(.25,true,false); }
        assertEquals(0,c.progress());
    }
    @Test void changingSelectionCannotHarvestProgressOrSkipCooldown() {
        var c=ready(); c.work(.25,true,true); c.select("Ore_Iron",20);
        assertEquals(0,c.progress()); assertEquals(20,c.remaining());
        c.select("Ore_Copper",10); assertEquals(20,c.remaining());
        c.select(null,1); c.select("Ore_Copper",10); assertEquals(20,c.remaining());
    }
    @Test void fullOutputOrEditedNodeRetainsWorkWithoutAwardingAndSuccessOnlyHappensOnce() {
        var c=ready(); for(int i=0;i<16;i++) c.work(.25,true,true);
        assertFalse(c.extract("Ore_Copper",10,false,()->fail("must not edit a full node")));
        assertFalse(c.extract("Ore_Copper",10,true,()->false)); assertEquals(1,c.progress());
        assertTrue(c.extract("Ore_Copper",10,true,()->true));
        assertFalse(c.extract("Ore_Copper",10,true,()->fail("duplicate award")));
        assertEquals(10,c.remaining());
    }
    @Test void reloadPreservesCooldownWorkAndExactCellOwnership() {
        var state=new PlotProductionState(); var cycle=state.mining().cycle(0);
        cycle.select("Ore_Copper",1); for(int i=0;i<4;i++) cycle.simulate(.25);
        cycle.work(.25,true,true); state.mining().own(2,"Aetherhaven_Mining_Node_Ore_Copper");
        state.mining().completed(1);
        var gson=new Gson(); var copy=gson.fromJson(gson.toJson(state),PlotProductionState.class);
        assertEquals(cycle.progress(),copy.mining().cycle(0).progress());
        assertEquals(state.mining().ownedBlock(2),copy.mining().ownedBlock(2));
        assertEquals(2,copy.mining().firstSlot(5)); assertEquals(1,copy.mining().node(3));
    }
}
