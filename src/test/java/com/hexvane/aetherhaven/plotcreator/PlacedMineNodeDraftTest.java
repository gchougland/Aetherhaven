package com.hexvane.aetherhaven.plotcreator;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hexvane.aetherhaven.production.BuildingProductionLayout;
import com.hexvane.aetherhaven.town.*;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import java.util.*;
import org.joml.Vector3i;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("construction")
class PlacedMineNodeDraftTest {
    private static PlotInstance mine(Rotation yaw) {
        var plot=new PlotInstance(UUID.randomUUID(),"community_mine",PlotInstanceState.COMPLETE,
            new PlotFootprintRecord(50,20,150,150,60,250),100,30,200,0);
        plot.setPrefabWorldPlacement(100,31,200,yaw);return plot;
    }
    @Test void oneClickUsesTheMinesOwnCoordinatesForEveryRotationAndNeverEditsThePlot() {
        var gson=new Gson();
        for(var yaw:Rotation.values()) {
            var plot=mine(yaw);String before=gson.toJson(plot);
            var draft=new PlacedMineNodeDraft(plot,BuildingProductionLayout.LEGACY);
            var point=new Vector3i(100,31,200).add(PrefabLocalOffset.rotate(yaw,4,1,-7));
            assertNull(draft.add(plot,point,"Rock_Stone"));
            var node=draft.snapshot().nodes().getFirst();assertEquals(new BuildingProductionLayout.Node(4,1,-7,"Rock_Stone"),node);
            assertEquals(point,draft.point(node));assertEquals(before,gson.toJson(plot));
            assertTrue(draft.remove(point));assertFalse(draft.remove(point));assertTrue(draft.snapshot().nodes().isEmpty());
            assertEquals(before,gson.toJson(plot));
        }
    }
    @Test void reviewSnapshotsAndTheInstalledLayoutCannotBeChangedByFurtherSelection() {
        var plot=mine(Rotation.None);
        var seed=new BuildingProductionLayout("4.0",BuildingProductionLayout.MINING_NODES,List.of(new BuildingProductionLayout.Node(1,0,0,"Rock_Stone")));
        var draft=new PlacedMineNodeDraft(plot,seed);var reviewed=draft.snapshot();
        assertTrue(draft.remove(new Vector3i(101,31,200)));
        assertNull(draft.add(plot,new Vector3i(102,31,200),"Rock_Marble"));
        assertEquals(seed,reviewed);assertEquals(1,seed.nodes().getFirst().x());assertEquals(2,draft.snapshot().nodes().getFirst().x());
        assertThrows(UnsupportedOperationException.class,()->reviewed.nodes().clear());
    }
    @Test void duplicatesBoundsAndMarkerLimitCannotExpandProductionOrRewriteTheBuilding() {
        var plot=mine(Rotation.None);var draft=new PlacedMineNodeDraft(plot,BuildingProductionLayout.LEGACY);
        assertNotNull(draft.add(plot,new Vector3i(0,0,0),"Rock_Stone"));
        var first=new Vector3i(51,31,151);assertNull(draft.add(plot,first,"Rock_Stone"));
        assertNotNull(draft.add(plot,first,"Rock_Marble"));assertEquals("Rock_Stone",draft.snapshot().nodes().getFirst().expectedBlockId());
        for(int i=1;i<64;i++)assertNull(draft.add(plot,new Vector3i(51+i,31,151),"Rock_Stone"));
        assertNotNull(draft.add(plot,new Vector3i(120,31,151),"Rock_Stone"));assertEquals(64,draft.snapshot().nodes().size());
        assertTrue(plot.getProductionLayout().legacy());
    }
    @Test void changedOrAlreadyConvertedMineInvalidatesTheDraftAndMalformedSeedsStartEmpty() {
        var plot=mine(Rotation.None);var malformed=new BuildingProductionLayout("4.0",BuildingProductionLayout.MINING_NODES,Arrays.asList((BuildingProductionLayout.Node)null));
        var draft=new PlacedMineNodeDraft(plot,malformed);assertTrue(draft.snapshot().nodes().isEmpty());
        assertNull(draft.add(plot,new Vector3i(101,31,200),"Rock_Stone"));
        plot.setPrefabWorldPlacement(101,31,200,Rotation.None);assertFalse(draft.matches(plot));
        assertNotNull(draft.add(plot,new Vector3i(102,31,200),"Rock_Stone"));
        plot.setPrefabWorldPlacement(100,31,200,Rotation.None);assertTrue(draft.matches(plot));
        plot.setState(PlotInstanceState.BLUEPRINTING);assertFalse(draft.matches(plot));
        plot.setState(PlotInstanceState.COMPLETE);assertTrue(plot.adoptMiningLayout(draft.snapshot()));assertFalse(draft.matches(plot));
    }
}
