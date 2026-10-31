package com.hexvane.aetherhaven.production;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService;
import com.hexvane.aetherhaven.town.TownRecord;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class ProductionAccrualEngineTest {
    private final TownRecord town = new TownRecord(UUID.randomUUID(), UUID.randomUUID(), "test", 0, 100, 0, 1, 1, 0);
    private final ProductionCatalog.Entry copper = ProductionCatalog.Entry.create(List.of("Ore_Copper"), new int[]{2}, new long[]{5});

    private boolean tick(PlotProductionState state, ProductionCatalog.Entry entry, int count) {
        return ProductionAccrualEngine.applyEntityTicks(state, entry, town, "plot_miners_hut",
            ConstructionCatalog.empty(), new AetherhavenPluginConfig(), null, 0, 0, count);
    }

    @Test void onlyDeliveredOutputEarnsXpIncludingPartiallyFullSpecializedStorage() {
        town.setCharterSpecialization("mining");
        var state = new PlotProductionState();
        var mastery = CoreVillagerMasteryService.progress(town, "miner");
        assertTrue(tick(state, copper, 1));
        assertEquals(0, state.getAmount("Ore_Copper"));
        assertEquals(0, mastery.xpInLevel());
        assertTrue(tick(state, copper, 1));
        assertEquals(2, state.getAmount("Ore_Copper"));
        assertEquals(2, mastery.xpInLevel());
        tick(state, copper, 20);
        assertEquals(5, state.getAmount("Ore_Copper"));
        assertEquals(5, mastery.xpInLevel(), "Capacity admits only one item from the final two-item cycle");
        tick(state, copper, 20);
        assertEquals(5, mastery.xpInLevel(), "Full storage earns no XP");
    }

    @Test void savedPartialCycleResumesOnceAndCatalystStillAddsOneLevel() {
        var state = new PlotProductionState();
        tick(state, copper, 1);
        var gson = new Gson();
        state = gson.fromJson(gson.toJson(state), PlotProductionState.class);
        var mastery = CoreVillagerMasteryService.progress(town, "miner");
        mastery.earnXp(99);
        tick(state, copper, 1);
        assertEquals(1, state.getAmount("Ore_Copper"));
        assertEquals(2, mastery.level());
        assertEquals(0, mastery.xpInLevel());
        assertTrue(mastery.train());
        assertEquals(3, mastery.level());
        tick(state, copper, 1);
        assertEquals(0, mastery.xpInLevel());
        tick(state, copper, 1);
        assertEquals(1, mastery.xpInLevel());
    }

    @Test void lockedOreAndInactiveTicksNeverEarnXpOrProduceGoods() {
        var state = new PlotProductionState();
        var iron = ProductionCatalog.Entry.create(List.of("Ore_Iron"), new int[]{1}, new long[]{5});
        assertFalse(tick(state, iron, 100));
        assertFalse(tick(state, copper, 0));
        assertTrue(state.getAmounts().isEmpty());
        assertTrue(town.getCoreVillagerMastery().isEmpty());
    }

    @Test void parallelSlotsShareCapacityWithoutDuplicatingXp() {
        var state = new PlotProductionState();
        state.setIronUpgrade(1);
        tick(state, copper, 20);
        assertEquals(5, state.getAmount("Ore_Copper"));
        assertEquals(5, CoreVillagerMasteryService.progress(town, "miner").xpInLevel());
    }
    @Test void lumbermillsNeverCreateTimerWoodBeforeRootstonesOrAfterReload() {
        var entry=ProductionCatalog.Entry.create(List.of("Wood_Beech_Trunk"),new int[]{1},new long[]{100});
        for(var json:List.of("{}","{\"s0\":0,\"a0\":200,\"timber\":{}}")) {
            var state=new Gson().fromJson(json,PlotProductionState.class);
            state.addAmount("Wood_Beech_Trunk",7,100);state.setCobaltLevel(2);
            var before=new Gson().toJson(state);
            assertFalse(ProductionAccrualEngine.applyEntityTicks(state,entry,town,"plot_lumbermill",ConstructionCatalog.empty(),
                new AetherhavenPluginConfig(),null,0,0,1));
            assertFalse(ProductionAccrualEngine.applyEntityTicks(state,entry,town,"plot_lumbermill",ConstructionCatalog.empty(),
                new AetherhavenPluginConfig(),null,0,0,100_000),"Offline catch-up cannot stand in for chopping a tree");
            assertEquals(before,new Gson().toJson(state),"Old stock, upgrades and pending data remain intact");
            assertEquals(7,state.getAmount("Wood_Beech_Trunk"));
            assertFalse(town.getCoreVillagerMastery().containsKey("logger"));
        }
    }
}
