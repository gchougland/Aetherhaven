package com.hexvane.aetherhaven.production;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class TimberCatalogTest {
    @Test void everyShippedTimberHasCapacityWithoutPurchasingLegacyOutputUnlocks() {
        var loader=getClass().getClassLoader();var base=ProductionCatalog.loadFromClasspath(loader);
        var unlocks=WorkplaceUnlockCatalog.loadFromClasspath(loader);var state=new PlotProductionState();
        var catalog=ProductionEffectiveCatalog.effective(base,unlocks,"plot_lumbermill",state);
        assertNotNull(catalog);assertTrue(catalog.catalogSize()>0);
        int previouslyPaid=0;
        for(var line:unlocks.linesForWorkplace("plot_lumbermill")) {
            assertTrue(catalog.containsItemId(line.itemId()),line.itemId());
            assertEquals(line.maxStorage(),catalog.maxStorageForItem(line.itemId()));
            assertFalse(state.isWorkplaceOutputUnlocked(line.itemId()),"Availability no longer purchases or rewrites unlock state");
            if(!line.defaultUnlocked())previouslyPaid++;
        }
        assertTrue(previouslyPaid>0,"Test must cover outputs formerly gated by an unlock payment");
        var farm=ProductionEffectiveCatalog.effective(base,unlocks,"plot_farm",new PlotProductionState());
        for(var line:unlocks.linesForWorkplace("plot_farm"))if(!line.defaultUnlocked())
            assertFalse(farm!=null&&farm.containsItemId(line.itemId()),"Farm progression is unchanged");
    }
}
