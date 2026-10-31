package com.hexvane.aetherhaven.plotcreator;

import java.util.List;
import java.util.function.Predicate;
import org.joml.Vector3i;

/** Exact authored-spot removal. A neighboring marker never wins merely because it was saved first. */
final class PlotCreatorSpotRemoval {
    private PlotCreatorSpotRemoval() {}
    record Spot(PlotCreatorSubstepType type,String role,Vector3i cell) {}

    static boolean remove(PlotCreatorDraft draft,Spot spot) {
        var req=new PlotBuildingKindRequirements.SubstepRequirement(spot.type(),0,spot.role());
        return switch(spot.type()) {
            case SPECIALTY_BLOCK,WORK_POI,SLEEP_POI,EAT_POI,FUN_POI,SHOP_POI,TOURIST_VISIT_POI,PLANNING_DESK_POI,BARD_WORK_POI,QUEST_BOARD_POI,BARN_PEN,CLERK_STAND ->
                first(draft.getPois(),p->PlotCreatorValidator.matchesPoiRequirement(p,req)
                    &&(spot.type()!=PlotCreatorSubstepType.WORK_POI||java.util.Objects.equals(p.getWorkResidentKind(),spot.role()))
                    &&at(draft,spot,p.getLocalX(),p.getLocalY(),p.getLocalZ()));
            case MINING_NODE -> first(draft.getMiningNodes(),p->at(draft,spot,p.x(),p.y(),p.z()));
            case VISITOR_SPAWN -> first(draft.getVisitorSpawnLocals(),p->at(draft,spot,p));
            case ADVENTURER_SPAWN -> first(draft.getAdventurerSpawns(),p->spot.cell().equals(
                PlotCreatorPrefabCoords.standWorldBlockFromPrefabLocal(draft,p.getLocalX(),p.getLocalY(),p.getLocalZ())));
            case MANAGEMENT_BLOCK -> clear(draft,spot,draft.getManagementBlockLocalPos(),()->draft.setManagementBlockLocalPos(null));
            case PRODUCTION_STORAGE -> clear(draft,spot,draft.getProductionStorageLocalPos(),()->draft.setProductionStorageLocalPos(null));
            case TREASURY_BLOCK -> clear(draft,spot,draft.getTreasuryLocalPos(),()->draft.setTreasuryLocalPos(null));
            case SHOP_SAFE_BLOCK -> clear(draft,spot,draft.getShopSafeLocalPos(),()->draft.setShopSafeLocalPos(null));
            case INN_BELL_BLOCK -> clear(draft,spot,draft.getInnBellLocalPos(),()->draft.setInnBellLocalPos(null));
            case GAIA_STATUE_BLOCK -> clear(draft,spot,draft.getGaiaStatueLocalPos(),()->PlotCreatorGaiaStatueSupport.clear(draft));
            case INNKEEPER_SPAWN -> clear(draft,spot,draft.getInnkeeperSpawnLocal(),()->draft.setInnkeeperSpawnLocal(null));
            case GUILD_MASTER_SPAWN -> clear(draft,spot,draft.getGuildMasterSpawnLocal(),()->draft.setGuildMasterSpawnLocal(null));
            case SHOP_SPOT,TOURIST_PORTAL_BLOCK -> first(draft.getPlacedSpecialBlocks(),spot.cell()::equals);
            case FESTIVAL_NPC -> first(draft.getFestivalNpcs(),p->(spot.role()==null||spot.role().equals(p.getNpcRoleId()))&&at(draft,spot,p.getLocalX(),p.getLocalY(),p.getLocalZ()));
            case FESTIVAL_TOURIST_SPOT -> first(draft.getFestivalTouristSpots(),p->at(draft,spot,p.getLocalX(),p.getLocalY(),p.getLocalZ()));
            case FESTIVAL_CENTERPIECE -> clear(draft,spot,draft.getFestivalCenterpieceLocal(),()->draft.setFestivalCenterpieceLocal(null));
            case FESTIVAL_RACE_LANE -> first(draft.getFestivalRaceLanes(),p->(spot.role()==null||spot.role().equals(p.getNpcRoleId()))
                &&(at(draft,spot,p.getStartLocalX(),p.getStartLocalY(),p.getStartLocalZ())||at(draft,spot,p.getFinishLocalX(),p.getFinishLocalY(),p.getFinishLocalZ())));
            case FESTIVAL_BALLOON_SPAWN -> first(draft.getFestivalBalloonSpawns(),p->at(draft,spot,p.getLocalX(),p.getLocalY(),p.getLocalZ()));
            case FESTIVAL_WHACK_SPAWN -> first(draft.getFestivalWhackSpawns(),p->at(draft,spot,p.getLocalX(),p.getLocalY(),p.getLocalZ()));
            case FESTIVAL_WHEEL -> {
                var p=draft.getFestivalWheelLocal();yield p!=null&&clear(draft,spot,new int[]{p.getLocalX(),p.getLocalY(),p.getLocalZ()},()->draft.setFestivalWheelLocal(null));
            }
            case FESTIVAL_TREE_CLIMB_START -> first(draft.getFestivalRaceStartSpots(),p->at(draft,spot,p.getLocalX(),p.getLocalY(),p.getLocalZ()));
            case FESTIVAL_TREE_CLIMB_FINISH -> {
                var p=draft.getFestivalRaceFinishLocal();yield p!=null&&clear(draft,spot,new int[]{p.getLocalX(),p.getLocalY(),p.getLocalZ()},()->draft.setFestivalRaceFinishLocal(null));
            }
            case FESTIVAL_MAZE_START -> {
                var p=draft.getFestivalMazeStartLocal();yield p!=null&&clear(draft,spot,new int[]{p.getLocalX(),p.getLocalY(),p.getLocalZ()},()->draft.setFestivalMazeStartLocal(null));
            }
            case FESTIVAL_MAZE_ORB_SPAWN -> first(draft.getFestivalOrbSpawns(),p->atRounded(draft,spot,p.getLocalX(),p.getLocalY(),p.getLocalZ()));
            case FESTIVAL_MARKET_STAND -> first(draft.getFestivalMarketStands(),p->at(draft,spot,p.getLocalX(),p.getLocalY(),p.getLocalZ()));
            case FESTIVAL_MARKET_DISPLAY -> first(draft.getFestivalMarketDisplaySlots(),p->atRounded(draft,spot,p.getLocalX(),p.getLocalY(),p.getLocalZ()));
            case FESTIVAL_SNOWBALL_PILE -> first(draft.getFestivalSnowballPileSpots(),p->atRounded(draft,spot,p.getLocalX(),p.getLocalY(),p.getLocalZ()));
            case FESTIVAL_SNOWBALL_TEAM_A -> first(draft.getFestivalSnowballTeamASpots(),p->at(draft,spot,p.getLocalX(),p.getLocalY(),p.getLocalZ()));
            case FESTIVAL_SNOWBALL_TEAM_B -> first(draft.getFestivalSnowballTeamBSpots(),p->at(draft,spot,p.getLocalX(),p.getLocalY(),p.getLocalZ()));
            case FESTIVAL_SNOWBALL_OUT -> {
                var p=draft.getFestivalSnowballOutLocal();yield p!=null&&clear(draft,spot,new int[]{p.getLocalX(),p.getLocalY(),p.getLocalZ()},()->draft.setFestivalSnowballOutLocal(null));
            }
        };
    }
    private static boolean at(PlotCreatorDraft draft,Spot spot,int... local) {
        return local!=null&&local.length>=3&&spot.cell().equals(PlotCreatorLocalCoords.toWorldBlock(draft,local));
    }
    private static boolean atRounded(PlotCreatorDraft draft,Spot spot,double x,double y,double z) {
        return at(draft,spot,(int)Math.round(x),(int)Math.round(y),(int)Math.round(z));
    }
    private static boolean clear(PlotCreatorDraft draft,Spot spot,int[] local,Runnable clear) {
        if(!at(draft,spot,local))return false;clear.run();return true;
    }
    private static <T> boolean first(List<T> rows,Predicate<T> matches) {
        for(var it=rows.iterator();it.hasNext();)if(matches.test(it.next())){it.remove();return true;}
        return false;
    }
}
