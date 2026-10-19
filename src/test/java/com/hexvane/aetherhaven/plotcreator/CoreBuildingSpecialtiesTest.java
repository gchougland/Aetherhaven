package com.hexvane.aetherhaven.plotcreator;

import com.google.gson.*;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.hunter.HuntArenaLayout;
import com.hexvane.aetherhaven.villager.data.VillagerDefinitionCatalog;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import java.nio.file.*;
import java.util.*;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CoreBuildingSpecialtiesTest {
    // Keep this historical list fixed: every subsequent core building automatically enters the prefab audit.
    private static final Set<String> PRE_FOUR_BUILDINGS=Set.of("town_hall","blacksmith_shop","clown_tent","farm","restaurant","guild_hall","builders_hut","furniture_shop","crystal_keepers_shop","flower_shop","halloween","inn","lumbermill","market_stall","miners_hut","bomb_shop","gaia_altar","barn");
    private static PlotCreatorDraft draft(String id) {
        var d=new PlotCreatorDraft();d.setConstructionId(id);d.setKinds(List.of(PlotBuildingKind.WORK));
        d.setCornerFirst(new Vector3i(-20,0,-20));d.setCornerSecond(new Vector3i(20,12,20));d.setPlotAnchor(new Vector3i(0,1,0));
        d.setDisplayName("Test");d.setPrefabPath("test.prefab.json");return d;
    }
    @Test void everyCoreVillagerBuildingMustHaveAnExplicitSpecialtyAudit() {
        var villagers=VillagerDefinitionCatalog.loadFromAssetPacksOrClasspath(getClass().getClassLoader());
        for(var v:villagers.allByNpcRoleId().values())if(v.getWorkConstructionId()!=null)
            assertTrue(CoreBuildingSpecialties.auditedBuildings().containsKey(v.getWorkConstructionId()),
                "New core building must be audited and added to the creator/editor requirements: "+v.getWorkConstructionId());
    }
    @Test void everyFourPointZeroBuildingSpecialBlockIsCoveredByAGuidedStep() throws Exception {
        // The smokestack is decorative; every other mod block in these seven buildings is functional equipment.
        var decorative=Set.of("Aetherhaven_Smokestack");
        for(String id:CoreBuildingSpecialties.auditedBuildings().keySet()) {
            String name=id.substring("plot_".length());
            if(PRE_FOUR_BUILDINGS.contains(name))continue;
            var def=JsonParser.parseString(Files.readString(Path.of("src/main/resources/Server/Aetherhaven/Buildings/plot_"+name+".json"))).getAsJsonObject();
            var prefab=JsonParser.parseString(Files.readString(Path.of("src/main/resources/Server/Prefabs",def.get("prefabPath").getAsString()))).getAsJsonObject();
            var actual=new HashSet<String>();
            for(var cell:prefab.getAsJsonArray("blocks"))actual.add(cell.getAsJsonObject().get("name").getAsString());
            var steps=PlotBuildingKindRequirements.defaultRequirements(draft("plot_"+name),null);
            for(String block:actual)if((block.startsWith("Aetherhaven_")||block.startsWith("Bench_"))&&!decorative.contains(block)) {
                assertTrue(PlotBuildingKindRequirements.isSpecialBlockType(block),name+" has unaudited specialty equipment: "+block);
                if("Aetherhaven_Management_Block".equals(block))assertTrue(steps.stream().anyMatch(s->s.type()==PlotCreatorSubstepType.MANAGEMENT_BLOCK));
                else if("Aetherhaven_Production_Storage".equals(block))assertTrue(steps.stream().anyMatch(s->s.type()==PlotCreatorSubstepType.PRODUCTION_STORAGE),name+" must require its output storage");
                else assertTrue(CoreBuildingSpecialties.required(draft("plot_"+name),null).contains(block),name+" must explicitly require "+block);
            }
            for(String block:CoreBuildingSpecialties.auditedBuildings().get("plot_"+name))assertTrue(actual.contains(block),name+" must actually ship "+block);
            var d=draft("plot_"+name);assertRequired(d);
            var edited=draft("plot_"+name);PlotCreatorDraftLoader.loadIntoDraft(edited,new Gson().fromJson(def,ConstructionDefinition.class));assertRequired(edited);
            var variant=draft("plot_community_"+name);variant.setCountsAsConstructionIds(List.of("plot_"+name));assertRequired(variant);
        }
    }
    @Test void productionStorageCannotBeUncheckedForNewOrEditedProductionVariants() {
        var d=draft("plot_community_druid");d.setCountsAsConstructionIds(List.of("plot_druid"));d.setImportantSpotsConfirmed(true);
        d.getSelectedSpots().add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.PRODUCTION_STORAGE,0));
        var steps=PlotBuildingKindRequirements.forDraft(d,null);
        assertEquals(1,steps.stream().filter(r->r.type()==PlotCreatorSubstepType.PRODUCTION_STORAGE).count());
        assertEquals(1,steps.stream().filter(r->r.type()==PlotCreatorSubstepType.PRODUCTION_STORAGE).findFirst().orElseThrow().minCount());
    }
    private static void assertRequired(PlotCreatorDraft d) {
        d.setImportantSpotsConfirmed(true);d.getSelectedSpots().clear();d.getSelectedSpots().add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.MANAGEMENT_BLOCK,1));
        var steps=PlotBuildingKindRequirements.forDraft(d,null);
        for(String block:CoreBuildingSpecialties.required(d,null))assertTrue(steps.stream().anyMatch(r->r.type()==PlotCreatorSubstepType.SPECIALTY_BLOCK&&block.equals(r.workResidentKind())&&r.minCount()==1),"Cannot omit "+block);
    }
    @Test void bothWritersRejectMissingEquipmentAndPreserveRecordedSpecialty(@TempDir Path dir) throws Exception {
        var d=draft("plot_warehouse");
        assertThrows(java.io.IOException.class,()->PlotCreatorJsonWriter.writeBuilding(dir.resolve("new.json"),d));
        assertThrows(java.io.IOException.class,()->BuildingEditorJsonWriter.writeMerged(dir.resolve("edit.json"),d,Map.of()));
        var p=new PlotCreatorPoiDraft();p.setLocal(0,0,0);p.setBlockTypeId("Aetherhaven_Warehouse_Ledger_Desk");p.setTags(List.of(CoreBuildingSpecialties.TAG));d.getPois().add(p);
        assertNull(CoreBuildingSpecialties.validationError(d,null));
        BuildingEditorJsonWriter.writeMerged(dir.resolve("edit.json"),d,Map.of());
        assertTrue(Files.readString(dir.resolve("edit.json")).contains(CoreBuildingSpecialties.TAG));
        p.setLocal(999,0,0);assertEquals("specialtyRequired",CoreBuildingSpecialties.validationError(d,null));
    }
    @Test void arenaPreviewExactlyMatchesLiveHuntAtEveryRotation() {
        for(Rotation yaw:List.of(Rotation.None,Rotation.Ninety,Rotation.OneEighty,Rotation.TwoSeventy)) {
            var d=draft("plot_monster_hunter");d.setRotationYaw(yaw.name());
            var p=new PlotCreatorPoiDraft();p.setLocal(2,1,3);p.setBlockTypeId("Aetherhaven_Hunt_Beacon");p.setTags(List.of(CoreBuildingSpecialties.TAG));d.getPois().add(p);
            var block=PlotCreatorLocalCoords.toWorldBlock(d,new int[]{2,1,3});
            var live=HuntArenaLayout.of(new Vector3d(block.x+.5,block.y+1,block.z+.5),yaw);
            var markers=PlotCreatorSpotMarkerCollector.collect(d,null,null);
            var portal=markers.stream().filter(m->m.nameplateText().equals("Hunt portal opens here")).findFirst().orElseThrow();
            assertEquals((int)Math.floor(live.portal().x),portal.x());assertEquals((int)Math.floor(live.portal().y),portal.y());assertEquals((int)Math.floor(live.portal().z),portal.z());
            assertEquals(live.yawTowardBeacon(),portal.facingYawWorldRadians());
            assertEquals(4,markers.size());
        }
    }
    @Test void authoredArenaFacingSurvivesRefreshSaveAndLoadAndMatchesEveryPlacedRotation(@TempDir Path dir) throws Exception {
        var d=draft("plot_monster_hunter");d.setProductionStorageLocalPos(new int[]{0,0,0});
        var marked=new PlotCreatorPoiDraft();marked.setLocal(2,1,3);marked.setBlockTypeId("Aetherhaven_Hunt_Beacon");
        marked.setTags(List.of(CoreBuildingSpecialties.TAG));marked.setInteractionTargetYawDegrees(37f);
        var rescanned=new PlotCreatorPoiDraft();rescanned.setLocal(2,1,3);rescanned.setBlockTypeId(marked.getBlockTypeId());rescanned.setTags(marked.getTags());
        CoreBuildingSpecialties.preserveFacing(List.of(marked),rescanned);
        assertEquals(37f,rescanned.getInteractionTargetYawDegrees());d.getPois().add(rescanned);
        var gson=new Gson();var file=dir.resolve("lodge.json");
        PlotCreatorJsonWriter.writeBuilding(file,d);
        var def=gson.fromJson(Files.readString(file),ConstructionDefinition.class);
        assertEquals(37f,HuntArenaLayout.savedFacing(def));
        PlotCreatorDraftLoader.loadIntoDraft(d,def);
        BuildingEditorJsonWriter.writeMerged(file,d,gson.fromJson(Files.readString(file),new com.google.gson.reflect.TypeToken<Map<String,Object>>(){}.getType()));
        var edited=gson.fromJson(Files.readString(file),ConstructionDefinition.class);
        assertEquals(37f,HuntArenaLayout.savedFacing(edited));
        for(Rotation turn:List.of(Rotation.None,Rotation.Ninety,Rotation.OneEighty,Rotation.TwoSeventy)) {
            d.setRotationYaw(turn.name());
            var block=PlotCreatorLocalCoords.toWorldBlock(d,new int[]{2,1,3});
            var center=new Vector3d(block.x+.5,block.y+1,block.z+.5);
            var live=HuntArenaLayout.of(center,turn,HuntArenaLayout.savedFacing(edited));
            float yaw=com.hexvane.aetherhaven.construction.PrefabYaw.worldFromPrefabLocal(turn,(float)Math.toRadians(37));
            assertEquals(-Math.sin(yaw)*4,live.portal().x-center.x,1e-6);
            assertEquals(-Math.cos(yaw)*4,live.portal().z-center.z,1e-6);
            var portal=PlotCreatorSpotMarkerCollector.collect(d,null,null).stream()
                .filter(m->m.nameplateText().equals("Hunt portal opens here")).findFirst().orElseThrow();
            assertEquals((int)Math.floor(live.portal().x),portal.x());
            assertEquals((int)Math.floor(live.portal().z),portal.z());
            assertEquals(live.yawTowardBeacon(),portal.facingYawWorldRadians());
        }
    }

}
