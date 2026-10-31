package com.hexvane.aetherhaven.plotcreator;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.festival.FestivalDefinition.*;
import com.hexvane.aetherhaven.production.BuildingProductionLayout;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
class PlotCreatorSpotRemovalTest {
    @Test void rayHitsFloatingMarkerAndFullHologramAndReturnsNearestAuthoredCell() {
        var cube=spot(PlotCreatorSubstepType.MINING_NODE,new Vector3i(0,70,4));
        var seated=spot(PlotCreatorSubstepType.SLEEP_POI,new Vector3i(1,69,7));
        var visuals=List.of(new PlotCreatorSpotRay.Visual(seated,new Box(-.5,70,-.5,.5,72,.5).offset(0,0,7)),
            new PlotCreatorSpotRay.Visual(cube,new Box(-.5,70,-.5,.5,71,.5).offset(0,0,4)));
        assertEquals(cube,PlotCreatorSpotRay.pick(new Vector3d(0,70.8,0),new Vector3d(0,0,1),8,visuals));
        // Aim at the upper body, well above the authored/supporting cell and above the nearer cube.
        assertEquals(seated,PlotCreatorSpotRay.pick(new Vector3d(0,71.8,0),new Vector3d(0,0,2),8,visuals));
        assertNull(PlotCreatorSpotRay.pick(new Vector3d(2,71.8,0),new Vector3d(0,0,1),8,visuals));
        assertNull(PlotCreatorSpotRay.pick(new Vector3d(0,71.8,0),new Vector3d(0,0,-1),8,visuals));
        assertNull(PlotCreatorSpotRay.pick(new Vector3d(0,71.8,0),new Vector3d(0,0,1),6,visuals));
        assertNull(PlotCreatorSpotRay.pick(new Vector3d(),new Vector3d(),8,visuals));
    }

    @Test void everySpotTypeRemovesExactlyItsAuthoredDataInCreatorAndEditorAtEveryRotation() {
        for(boolean editor:new boolean[]{false,true})for(String yaw:List.of("None","Ninety","OneEighty","TwoSeventy"))
            for(var type:PlotCreatorSubstepType.values()) {
                var draft=draft(editor,yaw);seed(draft,type);
                var world=type==PlotCreatorSubstepType.ADVENTURER_SPAWN
                    ?PlotCreatorPrefabCoords.standWorldBlockFromPrefabLocal(draft,2,3,4)
                    :PlotCreatorLocalCoords.toWorldBlock(draft,new int[]{2,3,4});
                String role=type==PlotCreatorSubstepType.SPECIALTY_BLOCK?"Bench_Farming":null;
                var target=new PlotCreatorSpotRemoval.Spot(type,role,world);
                assertTrue(PlotCreatorSpotRemoval.remove(draft,target),type+" "+editor+" "+yaw);
                assertFalse(PlotCreatorSpotRemoval.remove(draft,target),type+" was removed twice");
            }
    }

    @Test void terrainHidesMarkersBehindItButNotTheMarkerOnItsOwnBlock() {
        var target=spot(PlotCreatorSubstepType.MANAGEMENT_BLOCK,new Vector3i(0,70,5));
        var visuals=List.of(new PlotCreatorSpotRay.Visual(target,new Box(0,70,5,1,71,6)));
        var origin=new Vector3d(.5,70.5,0);var forward=new Vector3d(0,0,1);
        assertNull(PlotCreatorSpotRay.pick(origin,forward,8,visuals,new Vector3i(0,70,3)));
        assertEquals(target,PlotCreatorSpotRay.pick(origin,forward,8,visuals,new Vector3i(0,70,5)));
    }

    @Test void supportFallbackAcceptsOnlyTheAuthoredCellOrItsImmediateSupport() {
        var target=spot(PlotCreatorSubstepType.VISITOR_SPAWN,new Vector3i(0,70,5));
        assertEquals(target,PlotCreatorSpotRay.supported(List.of(target),new Vector3i(0,70,5)));
        assertEquals(target,PlotCreatorSpotRay.supported(List.of(target),new Vector3i(0,69,5)));
        assertNull(PlotCreatorSpotRay.supported(List.of(target),new Vector3i(0,70,4)));
        assertNull(PlotCreatorSpotRay.supported(List.of(target),new Vector3i(1,69,5)));
    }

    @Test void adjacentPoiAndOtherRolesSurviveExactRemoval() {
        var draft=draft(true,"Ninety");
        var neighbor=poi(1,3,4,List.of("WORK"));neighbor.setWorkResidentKind("farmer");
        var otherRole=poi(2,3,4,List.of("WORK"));otherRole.setWorkResidentKind("blacksmith");
        var untyped=poi(2,3,4,List.of("WORK"));
        var target=poi(2,3,4,List.of("WORK"));target.setWorkResidentKind("farmer");
        draft.getPois().addAll(List.of(neighbor,otherRole,untyped,target));
        assertTrue(PlotCreatorSpotRemoval.remove(draft,new PlotCreatorSpotRemoval.Spot(PlotCreatorSubstepType.WORK_POI,
            "farmer",PlotCreatorLocalCoords.toWorldBlock(draft,new int[]{2,3,4}))));
        assertEquals(List.of(neighbor,otherRole,untyped),draft.getPois());
    }

    @Test void adventurerPreviewAndRemovalUsePrefabRotationAndAnchorOffset() {
        for(String yaw:List.of("None","Ninety","OneEighty","TwoSeventy")) {
            var draft=draft(true,yaw);seed(draft,PlotCreatorSubstepType.ADVENTURER_SPAWN);
            var expected=PlotCreatorPrefabCoords.standWorldBlockFromPrefabLocal(draft,2,3,4);
            var preview=PlotCreatorSpotPreviewCollector.collect(draft,null,null).getFirst();
            assertEquals(expected,new Vector3i(preview.standX(),preview.standY(),preview.standZ()));
            assertTrue(PlotCreatorSpotRemoval.remove(draft,spot(preview.type(),expected)));
        }
    }

    @Test void onlyVisibleAuthoringStagesCanBeRayPicked() {
        var draft=draft(false,"None");
        draft.setStep(PlotCreatorStep.IMPORTANT_SPOTS);assertFalse(PlotCreatorSpotRay.showing(draft));
        draft.setBuildingEditorMode(true);assertTrue(PlotCreatorSpotRay.showing(draft));
        draft.setStep(PlotCreatorStep.SUBSTEP);assertTrue(PlotCreatorSpotRay.showing(draft));
        draft.setStep(PlotCreatorStep.REVIEW);assertTrue(PlotCreatorSpotRay.showing(draft));
        draft.setStep(PlotCreatorStep.BOUNDS);assertFalse(PlotCreatorSpotRay.showing(draft));
    }

    @Test void bothShippedStaffsUseSecondaryThatDoesNotRequireTerrainTarget() throws Exception {
        assertTrue(SimpleInstantInteraction.class.isAssignableFrom(PlotCreatorRemoveSpotInteraction.class));
        for(String file:List.of("src/main/resources/Server/Item/Items/Aetherhaven/Aetherhaven_Building_Editor_Staff.json",
            "subplugin-assets/PlotCreator/Server/Item/Items/Aetherhaven/Aetherhaven_Plot_Creator_Staff.json")) {
            var bindings=JsonParser.parseString(Files.readString(Path.of(file))).getAsJsonObject().getAsJsonObject("Interactions");
            assertEquals("AetherhavenPlotCreatorRemoveSpot",bindings.getAsJsonObject("Secondary").getAsJsonArray("Interactions")
                .get(0).getAsJsonObject().get("Type").getAsString());
            assertTrue(bindings.getAsJsonObject("Primary").getAsJsonArray("Interactions").get(0).getAsJsonObject()
                .get("Type").getAsString().endsWith("Block"));
        }
    }

    private static PlotCreatorSpotRemoval.Spot spot(PlotCreatorSubstepType type,Vector3i cell) {
        return new PlotCreatorSpotRemoval.Spot(type,null,cell);
    }
    private static PlotCreatorDraft draft(boolean editor,String yaw) {
        var draft=new PlotCreatorDraft();draft.setBuildingEditorMode(editor);draft.setRotationYaw(yaw);
        draft.setPlotAnchor(new Vector3i(100,65,200));draft.setPlotAnchorOffset(new int[]{3,1,-2});return draft;
    }
    private static PlotCreatorPoiDraft poi(int x,int y,int z,List<String> tags) {
        var p=new PlotCreatorPoiDraft();p.setLocal(x,y,z);p.setTags(tags);return p;
    }
    private static void seed(PlotCreatorDraft d,PlotCreatorSubstepType type) {
        int[] local={2,3,4};
        switch(type) {
            case SPECIALTY_BLOCK -> {var p=poi(2,3,4,List.of(CoreBuildingSpecialties.TAG));p.setBlockTypeId("Bench_Farming");d.getPois().add(p);}
            case PLANNING_DESK_POI -> {var p=poi(2,3,4,List.of());p.setBlockTypeId("Aetherhaven_Town_Planning_Desk");d.getPois().add(p);}
            case WORK_POI,SLEEP_POI,EAT_POI,FUN_POI,SHOP_POI,TOURIST_VISIT_POI,QUEST_BOARD_POI,BARD_WORK_POI,BARN_PEN,CLERK_STAND -> {
                String tag=switch(type) {case WORK_POI->"WORK";case SLEEP_POI->"SLEEP";case EAT_POI->"EAT";case FUN_POI->"FUN";
                    case SHOP_POI->"SHOP";case TOURIST_VISIT_POI->"TOURIST_VISIT";case QUEST_BOARD_POI->"QUEST_BOARD";
                    case BARD_WORK_POI->"BARD";case BARN_PEN->"BARN_PEN";default->"CLERK_STAND";};
                d.getPois().add(poi(2,3,4,List.of(tag)));
            }
            case MANAGEMENT_BLOCK -> d.setManagementBlockLocalPos(local);
            case PRODUCTION_STORAGE -> d.setProductionStorageLocalPos(local);
            case TREASURY_BLOCK -> d.setTreasuryLocalPos(local);
            case SHOP_SAFE_BLOCK -> d.setShopSafeLocalPos(local);
            case INN_BELL_BLOCK -> d.setInnBellLocalPos(local);
            case GAIA_STATUE_BLOCK -> d.setGaiaStatueLocalPos(local);
            case INNKEEPER_SPAWN -> d.setInnkeeperSpawnLocal(local);
            case GUILD_MASTER_SPAWN -> d.setGuildMasterSpawnLocal(local);
            case SHOP_SPOT,TOURIST_PORTAL_BLOCK -> d.getPlacedSpecialBlocks().add(PlotCreatorLocalCoords.toWorldBlock(d,local));
            case VISITOR_SPAWN -> d.getVisitorSpawnLocals().add(local);
            case ADVENTURER_SPAWN -> d.getAdventurerSpawns().add(new PlotCreatorAdventurerSpawnEntry(2,3,4,0));
            case MINING_NODE -> d.getMiningNodes().add(new BuildingProductionLayout.Node(2,3,4,"Rock_Stone"));
            case FESTIVAL_NPC -> d.getFestivalNpcs().add(NpcRow.of("Merchant",2,3,4,0));
            case FESTIVAL_TOURIST_SPOT -> d.getFestivalTouristSpots().add(TouristSpotRow.of(2,3,4,0));
            case FESTIVAL_CENTERPIECE -> d.setFestivalCenterpieceLocal(local);
            case FESTIVAL_RACE_LANE -> d.getFestivalRaceLanes().add(RaceLaneRow.of("Pig",2,3,4,7,3,4));
            case FESTIVAL_BALLOON_SPAWN -> d.getFestivalBalloonSpawns().add(BalloonSpawnRow.of(2,3,4));
            case FESTIVAL_WHACK_SPAWN -> d.getFestivalWhackSpawns().add(WhackSpawnRow.of(2,3,4));
            case FESTIVAL_WHEEL -> d.setFestivalWheelLocal(WheelLocalRow.of(2,3,4,0));
            case FESTIVAL_TREE_CLIMB_START -> d.getFestivalRaceStartSpots().add(RaceStartSpotRow.of(2,3,4,0));
            case FESTIVAL_TREE_CLIMB_FINISH -> d.setFestivalRaceFinishLocal(RaceFinishLocalRow.of(2,3,4));
            case FESTIVAL_MAZE_START -> d.setFestivalMazeStartLocal(MazeStartLocalRow.of(2,3,4,0));
            case FESTIVAL_MAZE_ORB_SPAWN -> d.getFestivalOrbSpawns().add(OrbSpawnRow.of(2.1,3.2,4.3));
            case FESTIVAL_MARKET_STAND -> d.getFestivalMarketStands().add(RaceStartSpotRow.of(2,3,4,0));
            case FESTIVAL_MARKET_DISPLAY -> d.getFestivalMarketDisplaySlots().add(OrbSpawnRow.of(2.1,3.2,4.3));
            case FESTIVAL_SNOWBALL_PILE -> d.getFestivalSnowballPileSpots().add(OrbSpawnRow.of(2.1,3.2,4.3));
            case FESTIVAL_SNOWBALL_TEAM_A -> d.getFestivalSnowballTeamASpots().add(RaceStartSpotRow.of(2,3,4,0));
            case FESTIVAL_SNOWBALL_TEAM_B -> d.getFestivalSnowballTeamBSpots().add(RaceStartSpotRow.of(2,3,4,0));
            case FESTIVAL_SNOWBALL_OUT -> d.setFestivalSnowballOutLocal(MazeStartLocalRow.of(2,3,4,0));
        }
    }
}
