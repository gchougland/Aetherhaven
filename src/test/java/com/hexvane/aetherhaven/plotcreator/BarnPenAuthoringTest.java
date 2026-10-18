package com.hexvane.aetherhaven.plotcreator;

import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
import org.joml.Vector3i;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("crossmod")
class BarnPenAuthoringTest {
    @TempDir Path dir;
    private PlotCreatorDraft barn() {
        var d=new PlotCreatorDraft();d.setConstructionId("plot_barn");d.setDisplayName("Barn");
        d.setPrefabPath("Barn.prefab.json");d.setPrefabFileName("Barn.prefab.json");d.setRotationYaw("None");
        d.setPlotAnchor(new Vector3i(0,100,0));d.setCornerFirst(new Vector3i(0,99,0));d.setCornerSecond(new Vector3i(15,110,15));
        d.setPlotAnchorOffset(new int[]{0,1,0});d.setSelfBuildGameDays(1);d.setProductionStorageLocalPos(new int[]{1,0,1});
        var equipment=new PlotCreatorPoiDraft();equipment.setLocal(2,0,1);equipment.setTags(List.of(CoreBuildingSpecialties.TAG));equipment.setBlockTypeId("Bench_Farming");d.getPois().add(equipment);return d;
    }
    private PlotCreatorPoiDraft pen(int x) {
        var p=new PlotCreatorPoiDraft();p.setLocal(x,0,3);p.setTags(List.of("BARN_PEN"));p.setInteractionTargetYawDegrees(90f);return p;
    }
    @Test void barnVariantsRequireFiveDistinctPensWithinTheirBounds() {
        var d=barn();assertTrue(PlotCreatorProductionRules.requiresBarnPens(d,null));
        assertEquals("barnPensRequired",PlotCreatorProductionRules.validationError(d,null));
        for(int x=1;x<=5;x++)d.getPois().add(pen(x));
        assertNull(PlotCreatorProductionRules.validationError(d,null));
        assertEquals(PlotCreatorSubstepType.BARN_PEN,PlotCreatorSpotMarkerCollector.resolvePoiTypePublic(d.getPois().get(1)));
        d.getPois().set(5,pen(1));assertEquals("barnPensRequired",PlotCreatorProductionRules.validationError(d,null));
        d.getPois().set(5,pen(30));assertEquals("barnPensRequired",PlotCreatorProductionRules.validationError(d,null));
        d.getPois().set(5,pen(5));d.setConstructionId("custom_barn");d.setCountsAsConstructionIds(List.of("plot_barn"));
        assertNull(PlotCreatorProductionRules.validationError(d,null));
    }
    @Test void editorSavePreservesPenOrderAndFacing() throws Exception {
        var d=barn();for(int x=1;x<=5;x++)d.getPois().add(pen(x));
        Path out=dir.resolve("barn.json");BuildingEditorJsonWriter.writeMerged(out,d,new LinkedHashMap<>());
        var pois=JsonParser.parseString(Files.readString(out)).getAsJsonObject().getAsJsonArray("pois").asList().stream().filter(p->p.getAsJsonObject().getAsJsonArray("tags").asList().stream().anyMatch(t->"BARN_PEN".equals(t.getAsString()))).toList();assertEquals(5,pois.size());
        for(int n=0;n<5;n++) {var p=pois.get(n).getAsJsonObject();assertEquals(n+1,p.get("localX").getAsInt());
            assertEquals(90,p.get("interactionTargetYawDegrees").getAsInt());assertEquals("BARN_PEN",p.getAsJsonArray("tags").get(0).getAsString());}
    }
}
