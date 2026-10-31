package com.hexvane.aetherhaven.workplace;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.construction.*;
import com.hexvane.aetherhaven.town.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class CoreWorkplaceNavigationTest {
    @Test void buildersNeverOfferProductionEvenWhenTheirPrefabAlsoCountsAsAProductionBuilding() {
        var combined=List.of("plot_builders_hut","plot_lumbermill","plot_farm");
        assertFalse(WorkplaceUpgradesPage.offersProduction("builder",List.of("plot_builders_hut")));
        assertFalse(WorkplaceUpgradesPage.offersProduction("builder",combined));
        assertTrue(WorkplaceUpgradesPage.offersProduction("logger",combined));
        assertTrue(WorkplaceUpgradesPage.offersProduction("farmer",combined));
        assertFalse(WorkplaceUpgradesPage.offersProduction("miner",combined));
    }

    @Test void aNewOrLegacyMillOpensItsGroveWithoutAnyRootstonesOrProductionState() {
        var gson=new Gson();
        var mill=gson.fromJson("{\"id\":\"plot_lumbermill\"}",ConstructionDefinition.class);
        var variant=gson.fromJson("{\"id\":\"community_mill\",\"countsAsConstructionId\":\"plot_lumbermill\"}",ConstructionDefinition.class);
        var mixed=gson.fromJson("{\"id\":\"mixed_mill\",\"countsAsConstructionId\":[\"plot_builders_hut\",\"plot_lumbermill\"]}",ConstructionDefinition.class);
        var catalog=ConstructionCatalog.forTests(Map.of("plot_lumbermill",mill,"community_mill",variant,"mixed_mill",mixed));
        var town=new TownRecord();town.setTownId(UUID.randomUUID());
        for(String id:List.of("plot_lumbermill","community_mill","mixed_mill")) {
            var plot=new PlotInstance(UUID.randomUUID(),id,PlotInstanceState.COMPLETE,
                new PlotFootprintRecord(0,64,0,10,70,10),0,64,0,0L);
            assertNull(town.peekPlotProduction(plot.getPlotId()));
            assertInstanceOf(GrovePage.class,GrovePage.forMill(catalog,null,town.getTownId(),plot));
            assertNull(town.peekPlotProduction(plot.getPlotId()),"Opening the screen must not invent production or consume a sapling");
        }
        var farm=new PlotInstance(UUID.randomUUID(),"plot_farm",PlotInstanceState.COMPLETE,
            new PlotFootprintRecord(0,64,0,10,70,10),0,64,0,0L);
        assertNull(GrovePage.forMill(catalog,null,town.getTownId(),farm));
    }
}
