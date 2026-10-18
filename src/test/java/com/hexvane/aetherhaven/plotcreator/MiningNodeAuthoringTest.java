package com.hexvane.aetherhaven.plotcreator;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.production.BuildingProductionLayout;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import org.joml.Vector3i;
import static org.junit.jupiter.api.Assertions.*;

@Tag("construction")
class MiningNodeAuthoringTest {
    @TempDir Path temp;
    private final Gson gson=new Gson();
    private PlotCreatorDraft draft() {
        var d=new PlotCreatorDraft(); d.setConstructionId("plot_miners_hut"); d.setDisplayName("Mine");
        d.setPrefabPath("mine.prefab.json"); d.setPrefabFileName("mine.prefab.json");
        d.setPlotAnchor(new Vector3i(100,120,200));
        d.setCornerFirst(new Vector3i(60,100,160)); d.setCornerSecond(new Vector3i(140,150,240));
        d.setProductionStorageLocalPos(new int[]{1,0,1});
        return d;
    }
    @Test void oneCellRoundTripsWithoutCreatingAWorkerStandingPoi() throws Exception {
        var d=draft(); d.getMiningNodes().add(new BuildingProductionLayout.Node(2,0,13,"Rock_Stone"));
        var output=temp.resolve("mine.json"); PlotCreatorJsonWriter.writeBuilding(output,d);
        var def=gson.fromJson(Files.readString(output),ConstructionDefinition.class);
        assertEquals(d.miningLayout(),def.getProductionLayout()); assertTrue(def.getPois().isEmpty());
        var loaded=new PlotCreatorDraft(); PlotCreatorDraftLoader.loadIntoDraft(loaded,def);
        assertTrue(loaded.isLoadedPhysicalMining()); assertEquals(d.getMiningNodes(),loaded.getMiningNodes());
        assertEquals(1,PlotCreatorValidator.countForRequirement(loaded,new PlotBuildingKindRequirements.SubstepRequirement(PlotCreatorSubstepType.MINING_NODE,1)));
        assertTrue(loaded.getSelectedSpots().contains(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.MINING_NODE,1)));
    }
    @Test void legacyEditorRequiresNodesInsteadOfInferringThemFromOldWorkSpots() throws Exception {
        var def=gson.fromJson("""
            {"id":"plot_miners_hut","displayName":"Mine","prefabPath":"mine.prefab.json","version":98,
             "pois":[{"localX":2,"localY":0,"localZ":3,"tags":["WORK"],"workResidentKind":"miner"}]}
            """,ConstructionDefinition.class);
        var loaded=draft(); PlotCreatorDraftLoader.loadIntoDraft(loaded,def);
        loaded.setProductionStorageLocalPos(new int[]{1,0,1});
        assertFalse(loaded.isLoadedPhysicalMining()); assertTrue(loaded.getMiningNodes().isEmpty());
        assertEquals("miningUpgradeRequired",PlotCreatorProductionRules.validationError(loaded,null));
        assertTrue(loaded.getSelectedSpots().stream().anyMatch(s->s.type()==PlotCreatorSubstepType.MINING_NODE));
        assertFalse(loaded.getSelectedSpots().stream().anyMatch(s->"miner".equals(s.workResidentKind())));
        var out=temp.resolve("legacy.json");
        assertThrows(java.io.IOException.class,()->BuildingEditorJsonWriter.writeMerged(out,loaded,new LinkedHashMap<>(Map.of("version",98))));
        assertFalse(Files.exists(out));
        loaded.getMiningNodes().add(new BuildingProductionLayout.Node(2,0,13,"Rock_Stone"));
        BuildingEditorJsonWriter.writeMerged(out,loaded,new LinkedHashMap<>(Map.of("version",98)));
        assertEquals("4.0",gson.fromJson(Files.readString(out),ConstructionDefinition.class).getProductionLayout().modRules());
    }
    @Test void editorAddsLayoutWithoutDiscardingMarketplaceOrIntegrationMetadata() throws Exception {
        var d=draft(); d.getMiningNodes().add(new BuildingProductionLayout.Node(-2,1,7,"Rock_Stone"));
        var out=temp.resolve("updated.json");
        BuildingEditorJsonWriter.writeMerged(out,d,new LinkedHashMap<>(Map.of("version",17,"integration",Map.of("value","keep"))));
        var root=gson.fromJson(Files.readString(out),com.google.gson.JsonObject.class);
        assertEquals(17,root.get("version").getAsInt());
        assertEquals("keep",root.getAsJsonObject("integration").get("value").getAsString());
        assertEquals(-2,root.getAsJsonObject("productionLayout").getAsJsonArray("nodes").get(0).getAsJsonObject().get("x").getAsInt());
    }
    @Test void marketplaceVariantCannotBypassUpgradeByUncheckingNodes() throws Exception {
        var d=draft(); d.setConstructionId("plot_community_mine"); d.setEditingConstructionId("plot_community_mine");
        d.setCountsAsConstructionIds(List.of("plot_miners_hut"));
        d.setImportantSpotsConfirmed(true);
        d.getSelectedSpots().add(PlotCreatorSpotEntry.work("miner",0));
        var steps=PlotBuildingKindRequirements.forSelectedSpots(d);
        assertTrue(steps.stream().anyMatch(s->s.type()==PlotCreatorSubstepType.MINING_NODE && s.minCount()==1));
        assertFalse(steps.stream().anyMatch(s->"miner".equals(s.workResidentKind())));
        assertEquals("miningUpgradeRequired",PlotCreatorProductionRules.validationError(d,null));
        assertThrows(java.io.IOException.class,()->PlotCreatorJsonWriter.writeBuilding(temp.resolve("bypass.json"),d));
    }

    @Test void unrelatedLegacyBuildingsDoNotAcquireMiningRequirements() throws Exception {
        var d=draft(); d.setConstructionId("plot_house"); d.setEditingConstructionId("plot_house");
        assertNull(PlotCreatorProductionRules.validationError(d,null));
        var out=temp.resolve("house.json"); BuildingEditorJsonWriter.writeMerged(out,d,new LinkedHashMap<>());
        assertFalse(gson.fromJson(Files.readString(out),com.google.gson.JsonObject.class).has("productionLayout"));
    }

    @Test void markerIsAtTheSelectedBlockNotAboveItAndDoesNotBecomeAnNpcPreview() {
        var d=draft(); d.getMiningNodes().add(new BuildingProductionLayout.Node(2,0,13,"Rock_Stone"));
        var markers=PlotCreatorSpotMarkerCollector.collect(d,null,new PlotBuildingKindRequirements.SubstepRequirement(PlotCreatorSubstepType.MINING_NODE,1));
        assertEquals(1,markers.size()); var expected=PlotCreatorLocalCoords.toWorldBlock(d,new int[]{2,0,13});
        assertEquals(expected.x,markers.getFirst().x()); assertEquals(expected.y,markers.getFirst().y());
        assertEquals(expected.z,markers.getFirst().z()); assertEquals(PlotCreatorSubstepType.MINING_NODE,markers.getFirst().type());
        assertTrue(d.getPois().isEmpty());
    }

    @Test void rotatedEditorNodesStayOnTheirBlocksThroughSignMoveAndSave() throws Exception {
        var def=gson.fromJson("""
            {"id":"plot_miners_hut","displayName":"Mine","prefabPath":"mine.prefab.json",
             "plotAnchorOffset":[3,2,-4]}
            """,ConstructionDefinition.class);
        for (var yaw : com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation.values()) {
            var d=draft();
            var anchor=new Vector3i(101,122,199);
            d.setPlotAnchor(def.resolvePreviewSignAnchorWorld(anchor,yaw));
            d.getMiningNodes().add(new BuildingProductionLayout.Node(2,0,13,"Rock_Stone"));
            var expected=new Vector3i(anchor).add(com.hexvane.aetherhaven.construction.PrefabLocalOffset.rotate(yaw,2,0,13));
            PlotCreatorMiningNodes.fromPrefabSpace(d,anchor,yaw);
            var oldSign=new Vector3i(d.getPlotAnchor());
            var newSign=new Vector3i(oldSign).add(4,-1,6);
            BuildingEditorSessionStarter.rebaseLocalsForNewPlotSign(d,oldSign,newSign);
            d.setPlotAnchor(newSign);
            var node=d.getMiningNodes().getFirst();
            assertEquals(expected,PlotCreatorLocalCoords.toWorldBlock(d,new int[]{node.x(),node.y(),node.z()}));
            // Export writes blocks relative to the new sign and resets the anchor offset accordingly.
            PlotCreatorLocalCoords.recomputeAnchorOffset(d);
            var out=temp.resolve("rotated-"+yaw+".json");
            PlotCreatorJsonWriter.writeBuilding(out,d);
            var saved=gson.fromJson(Files.readString(out),ConstructionDefinition.class);
            var savedNode=saved.getProductionLayout().nodes().getFirst();
            var placed=saved.resolvePrefabAnchorWorld(newSign,com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation.None);
            assertEquals(expected,placed.add(savedNode.x(),savedNode.y(),savedNode.z()));
            assertEquals("Rock_Stone",savedNode.expectedBlockId());
        }
    }
}
