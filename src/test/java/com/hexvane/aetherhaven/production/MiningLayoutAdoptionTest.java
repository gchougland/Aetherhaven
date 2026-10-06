package com.hexvane.aetherhaven.production;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hexvane.aetherhaven.town.*;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import org.joml.Vector3i;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("construction")
class MiningLayoutAdoptionTest {
    private static final MiningLayoutAdoption.Cell SAFE=new MiningLayoutAdoption.Cell("Rock_Stone",true,true);
    private static BuildingProductionLayout layout() {
        return new BuildingProductionLayout("4.0",BuildingProductionLayout.MINING_NODES,List.of(
            new BuildingProductionLayout.Node(2,1,3,"Rock_Stone"),new BuildingProductionLayout.Node(-3,2,1,"Rock_Stone")));
    }
    private static PlotInstance mine(Rotation yaw) {
        var plot=new PlotInstance(UUID.randomUUID(),"mine",PlotInstanceState.COMPLETE,
            new PlotFootprintRecord(90,30,190,110,40,210),100,30,200,0);
        plot.setPrefabWorldPlacement(100,31,200,yaw);return plot;
    }
    @Test void everyRotationValidatesExactlyTheTransformedCellsWithoutChangingThePlot() {
        var gson=new Gson();
        for(var yaw:Rotation.values()) {
            var plot=mine(yaw);String before=gson.toJson(plot);var visited=new HashSet<Vector3i>();
            assertNull(MiningLayoutAdoption.issue(plot,layout(),p->{visited.add(new Vector3i(p));return SAFE;}));
            var expected=new HashSet<Vector3i>();
            for(var n:layout().nodes())expected.add(new Vector3i(100,31,200).add(PrefabLocalOffset.rotate(yaw,n.x(),n.y(),n.z())));
            assertEquals(expected,visited);assertEquals(before,gson.toJson(plot));
        }
    }
    @Test void unloadedChangedFunctionalAndBlockedCellsCannotPassTheReview() {
        var plot=mine(Rotation.None);var gson=new Gson();String before=gson.toJson(plot);
        for(var cell:Arrays.asList(null,new MiningLayoutAdoption.Cell("Rock_Other",true,true),
                new MiningLayoutAdoption.Cell("Rock_Stone",false,true),new MiningLayoutAdoption.Cell("Rock_Stone",true,false))) {
            assertNotNull(MiningLayoutAdoption.issue(plot,layout(),p->cell));
            assertEquals(before,gson.toJson(plot));
        }
    }
    @Test void everyCellIsRecheckedAndAnOutsideCellNeverRequestsTerrain() {
        var plot=mine(Rotation.None);int[] reads={0};
        assertNotNull(MiningLayoutAdoption.issue(plot,layout(),p->++reads[0]==1?SAFE:null));assertEquals(2,reads[0]);
        var outside=new BuildingProductionLayout("4.0",BuildingProductionLayout.MINING_NODES,List.of(new BuildingProductionLayout.Node(100,0,0,"Rock_Stone")));
        assertNotNull(MiningLayoutAdoption.issue(plot,outside,p->{fail("Out of bounds must be rejected before reading terrain");return SAFE;}));
        plot.setState(PlotInstanceState.BLUEPRINTING);
        assertNotNull(MiningLayoutAdoption.issue(plot,layout(),p->{fail("Incomplete mine must not read terrain");return SAFE;}));
    }
    @Test void explicitAdoptionSurvivesReloadWithoutChangingStoredProductionOrGivingASecondConversion() {
        var gson=new Gson();var town=new TownRecord();var plot=mine(Rotation.None);town.getPlotInstances().add(plot);
        var production=town.getOrCreatePlotProduction(plot.getPlotId());
        production.setIronUpgrade(1);production.setThoriumLevel(2);production.setSlotCursor(0,3);
        production.setSlotTickAccum(0,123);production.addAmount("Ingredient_Bar_Copper",17,100);
        production.migrateIfNeeded();
        String before=gson.toJson(production);
        assertNull(MiningLayoutAdoption.issue(plot,layout(),p->SAFE));assertTrue(plot.adoptMiningLayout(layout()));
        assertFalse(plot.adoptMiningLayout(layout()));assertNotNull(MiningLayoutAdoption.issue(plot,layout(),p->SAFE));
        var restored=gson.fromJson(gson.toJson(town),TownRecord.class);
        assertEquals(layout(),restored.findPlotById(plot.getPlotId()).getProductionLayout());
        assertEquals(before,gson.toJson(restored.getOrCreatePlotProduction(plot.getPlotId())));
    }
}
