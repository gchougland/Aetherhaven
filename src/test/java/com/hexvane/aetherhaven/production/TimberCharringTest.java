package com.hexvane.aetherhaven.production;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.warehouse.TownWarehouse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class TimberCharringTest {
    @Test void emptyUnlitCampfireIsACharringStationWithoutChangingItsFuelOrActiveState() throws Exception {
        var fire = new com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock();
        var definition = new Gson().fromJson("{\"id\":\"Campfire\"}",
            com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.ProcessingBench.class);
        var field = fire.getClass().getDeclaredField("processingBench");
        field.setAccessible(true);field.set(fire, definition);
        assertFalse(fire.isActive());assertEquals(0, fire.getFuelTime());
        assertTrue(TimberCharring.charringStation(fire));
        assertFalse(fire.isActive());assertEquals(0, fire.getFuelTime());
        var p=ready();
        assertTrue(TimberCharring.process(p,ENTRY,new TownWarehouse(),PLOT,WOOD,SOURCE));
        assertEquals(0,p.getAmount(WOOD),"The batch's five logs cover its fuel as well as its output");
        assertEquals(1,p.timber().deliver(p,100,16));
        assertFalse(fire.isActive());assertEquals(0, fire.getFuelTime());
    }

    @Test void missingUninitializedAndOtherProcessingBenchesAreNotCharringStations() throws Exception {
        assertFalse(TimberCharring.charringStation(null));
        var bench = new com.hypixel.hytale.builtin.crafting.component.ProcessingBenchBlock();
        assertFalse(TimberCharring.charringStation(bench));
        var definition = new Gson().fromJson("{\"id\":\"Furnace\"}",
            com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.ProcessingBench.class);
        var field = bench.getClass().getDeclaredField("processingBench");
        field.setAccessible(true);field.set(bench, definition);
        assertFalse(TimberCharring.charringStation(bench));
    }

    private static final String WOOD = "Wood_Beech_Trunk", RARE = "Wood_Crystal_Trunk";
    private static final UUID PLOT = UUID.randomUUID();
    private static final TimberHarvestPlan.Position SOURCE = new TimberHarvestPlan.Position(3, 100, 4);
    private static final ProductionCatalog.Entry ENTRY = ProductionCatalog.Entry.create(
        List.of(WOOD, TimberCharring.OUTPUT, RARE), new int[]{1, 1, 1}, new long[]{100, 100, 100});

    private PlotProductionState ready() {
        var p = new Gson().fromJson("{\"timber\":{\"adopted\":true}}", PlotProductionState.class);
        p.timber().setCharWood(WOOD);
        p.addAmount(WOOD, 5, 100);p.addAmount(RARE, 20, 100);
        return p;
    }

    @Test void costsArePaidOnceAndSavedCargoMustBeDeliveredBeforeAnotherBatch() {
        var p=ready();var stock=new TownWarehouse();
        assertEquals(WOOD, TimberCharring.input(p, ENTRY, stock, PLOT));
        assertTrue(TimberCharring.process(p, ENTRY, stock, PLOT, WOOD, SOURCE));
        assertEquals(0, p.getAmount(WOOD));assertEquals(20, p.getAmount(RARE));
        assertEquals(0, p.getAmount(TimberCharring.OUTPUT));
        assertEquals(1, p.timber().cargo().carried());
        assertFalse(TimberCharring.process(p, ENTRY, stock, PLOT, WOOD, SOURCE));
        p=new Gson().fromJson(new Gson().toJson(p), PlotProductionState.class);
        assertEquals(0,p.timber().deliver(p,0,16));
        assertEquals(1,p.timber().deliver(p,100,16));
        assertEquals(0,p.timber().deliver(p,100,16));
        assertEquals(1,p.getAmount(TimberCharring.OUTPUT));assertEquals(0,p.getAmount(WOOD));
    }

    @Test void switchedOffChangedWoodAndFullOutputSpendNothing() {
        var p=ready();var stock=new TownWarehouse();
        p.timber().setCharWood(null);
        assertFalse(TimberCharring.process(p,ENTRY,stock,PLOT,WOOD,SOURCE));
        p.timber().setCharWood(RARE);
        assertFalse(TimberCharring.process(p,ENTRY,stock,PLOT,WOOD,SOURCE),"A visit begun for one wood cannot spend it after the choice changes");
        p.timber().setCharWood(WOOD);p.addAmount(TimberCharring.OUTPUT,100,100);
        assertFalse(TimberCharring.process(p,ENTRY,stock,PLOT,WOOD,SOURCE));
        assertEquals(5,p.getAmount(WOOD));assertEquals(20,p.getAmount(RARE));assertNull(p.timber().cargo());
    }

    @Test void missingOrUnchosenInputsNeverFallBackToOtherStoredWood() {
        var p=ready();var stock=new TownWarehouse();p.removeAmountUpTo(WOOD,4);
        assertNull(TimberCharring.input(p,ENTRY,stock,PLOT));
        assertFalse(TimberCharring.process(p,ENTRY,stock,PLOT,WOOD,SOURCE));
        assertFalse(TimberCharring.process(p,ENTRY,stock,PLOT,RARE,SOURCE));
        assertFalse(TimberCharring.process(p,ENTRY,stock,PLOT,TimberCharring.OUTPUT,SOURCE));
        assertEquals(1,p.getAmount(WOOD));assertEquals(20,p.getAmount(RARE));
        p.timber().setCharWood(RARE);
        assertEquals(RARE,TimberCharring.input(p,ENTRY,stock,PLOT));
    }

    @Test void legacyModeAndFailedPaymentCannotCreateCargo() {
        var p=new PlotProductionState();p.timber().setCharWood(WOOD);p.addAmount(WOOD,5,100);
        assertFalse(TimberCharring.process(p,ENTRY,new TownWarehouse(),PLOT,WOOD,SOURCE));
        p=ready();assertFalse(p.timber().charLogs(SOURCE,()->false));
        assertFalse(p.timber().charLogs(null,()->fail("Missing work location cannot pay")));
        assertNull(p.timber().cargo());assertEquals(5,p.getAmount(WOOD));
    }
}
