package com.hexvane.aetherhaven.plotcreator;

import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Texture paths and short nameplate labels for plot creator important-spot markers. */
public final class PlotCreatorSpotMarkerVisuals {
    public static final String TEX_GREEN = "Items/Aetherhaven/Building_Marker/Building_Marker.png";
    public static final String TEX_TEAL = "Items/Aetherhaven/Building_Marker/Building_Marker_Teal.png";
    public static final String TEX_PURPLE = "Items/Aetherhaven/Building_Marker/Building_Marker_Purple.png";
    public static final String TEX_BLUE = "Items/Aetherhaven/Building_Marker/Building_Marker_Blue.png";
    public static final String TEX_ORANGE = "Items/Aetherhaven/Building_Marker/Building_Marker_Orange.png";

    private PlotCreatorSpotMarkerVisuals() {}

    @Nonnull
    public static String textureFor(@Nonnull PlotCreatorSubstepType type) {
        return switch (type) {
            case VISITOR_SPAWN, TOURIST_VISIT_POI, TOURIST_PORTAL_BLOCK, FESTIVAL_TOURIST_SPOT -> TEX_TEAL;
            case ADVENTURER_SPAWN, INNKEEPER_SPAWN, GUILD_MASTER_SPAWN, FESTIVAL_NPC -> TEX_PURPLE;
            case SLEEP_POI, EAT_POI, FUN_POI, FESTIVAL_CENTERPIECE, FESTIVAL_WHEEL, FESTIVAL_TREE_CLIMB_FINISH,
                FESTIVAL_MAZE_START, FESTIVAL_MARKET_STAND ->
                TEX_BLUE;
            case WORK_POI, BARD_WORK_POI, PLANNING_DESK_POI, QUEST_BOARD_POI, SHOP_POI, FESTIVAL_RACE_LANE,
                FESTIVAL_BALLOON_SPAWN, FESTIVAL_WHACK_SPAWN, FESTIVAL_TREE_CLIMB_START, FESTIVAL_MAZE_ORB_SPAWN,
                FESTIVAL_MARKET_DISPLAY ->
                TEX_ORANGE;
            case MANAGEMENT_BLOCK,
                PRODUCTION_STORAGE,
                TREASURY_BLOCK,
                SHOP_SAFE_BLOCK,
                INN_BELL_BLOCK,
                GAIA_STATUE_BLOCK,
                SHOP_SPOT -> TEX_GREEN;
        };
    }

    @Nonnull
    public static String nameplateText(
        @Nonnull PlotCreatorSubstepType type,
        @Nullable String workResidentKind
    ) {
        return nameplateText(type, workResidentKind, null);
    }

    @Nonnull
    public static String nameplateText(
        @Nonnull PlotCreatorSubstepType type,
        @Nullable String workResidentKind,
        @Nullable String workActivityId
    ) {
        String activityLabel = PlotCreatorWorkActivityTags.activityLabel(workActivityId);
        if (type == PlotCreatorSubstepType.FUN_POI && activityLabel != null) {
            return activityLabel;
        }
        if (type == PlotCreatorSubstepType.WORK_POI
            || type == PlotCreatorSubstepType.BARD_WORK_POI
            || type == PlotCreatorSubstepType.PLANNING_DESK_POI) {
            if (workResidentKind != null && !workResidentKind.isBlank()) {
                String role = workRoleLabel(workResidentKind);
                if (activityLabel != null && !"Work spot".equals(role)) {
                    return role;
                }
                if (activityLabel != null) {
                    return activityLabel;
                }
                return role;
            }
            if (activityLabel != null) {
                return activityLabel;
            }
        }
        if (type == PlotCreatorSubstepType.FESTIVAL_NPC) {
            return festivalNpcLabel(workResidentKind);
        }
        return switch (type) {
            case MANAGEMENT_BLOCK -> "Town records shelf";
            case PRODUCTION_STORAGE -> "Production chest";
            case TREASURY_BLOCK -> "Treasury";
            case SHOP_SAFE_BLOCK -> "Shop safe";
            case INN_BELL_BLOCK -> "Inn Bell";
            case GAIA_STATUE_BLOCK -> "Gaia statue";
            case PLANNING_DESK_POI -> "Planning desk";
            case WORK_POI -> "Work spot";
            case SLEEP_POI -> "Sleep spot";
            case EAT_POI -> "Eat spot";
            case FUN_POI -> "Fun spot";
            case SHOP_SPOT -> "Shop stall";
            case TOURIST_PORTAL_BLOCK -> "Tourist portal";
            case TOURIST_VISIT_POI -> "Tourist visit spot";
            case SHOP_POI -> "Shop spot";
            case INNKEEPER_SPAWN -> "Innkeeper stand";
            case VISITOR_SPAWN -> "Inn Visitor Spawn";
            case GUILD_MASTER_SPAWN -> "Guild master stand";
            case ADVENTURER_SPAWN -> "Adventurer posts";
            case BARD_WORK_POI -> "Bard work spot";
            case QUEST_BOARD_POI -> "Quest board";
            case FESTIVAL_NPC -> "Festival merchant";
            case FESTIVAL_TOURIST_SPOT -> "Festival visitor stand";
            case FESTIVAL_CENTERPIECE -> "Festival centerpiece";
            case FESTIVAL_RACE_LANE -> "Pig race lane";
            case FESTIVAL_BALLOON_SPAWN -> "Balloon spawn";
            case FESTIVAL_WHACK_SPAWN -> "Whack hole";
            case FESTIVAL_WHEEL -> "Carnival wheel";
            case FESTIVAL_TREE_CLIMB_START -> "Tree climb start";
            case FESTIVAL_TREE_CLIMB_FINISH -> "Tree climb finish";
            case FESTIVAL_MAZE_START -> "Maze start";
            case FESTIVAL_MAZE_ORB_SPAWN -> "Maze orb spawn";
            case FESTIVAL_MARKET_STAND -> "Market stand";
            case FESTIVAL_MARKET_DISPLAY -> "Market display";
        };
    }

    @Nonnull
    private static String festivalNpcLabel(@Nullable String npcRoleId) {
        if (npcRoleId == null || npcRoleId.isBlank()) {
            return "Festival merchant";
        }
        if (PlotCreatorFestivalNpcRoles.SEED_SELLER.equals(npcRoleId)) {
            return "Seed seller";
        }
        if (PlotCreatorFestivalNpcRoles.PIG_RACE_MERCHANT.equals(npcRoleId)) {
            return "Race merchant";
        }
        if (PlotCreatorFestivalNpcRoles.CARNIVAL_BALLOON.equals(npcRoleId)) {
            return "Balloon attendant";
        }
        if (PlotCreatorFestivalNpcRoles.CARNIVAL_WHEEL.equals(npcRoleId)) {
            return "Wheel attendant";
        }
        if (PlotCreatorFestivalNpcRoles.CARNIVAL_WHACK.equals(npcRoleId)) {
            return "Whack attendant";
        }
        if (PlotCreatorFestivalNpcRoles.TREE_CLIMB_MERCHANT.equals(npcRoleId)) {
            return "Tree climb merchant";
        }
        if (PlotCreatorFestivalNpcRoles.TREE_CLIMB_ATTENDANT.equals(npcRoleId)) {
            return "Tree climb attendant";
        }
        if (PlotCreatorFestivalNpcRoles.HALLOWS_EVE_MERCHANT.equals(npcRoleId)) {
            return "Hallow's Eve merchant";
        }
        return "Festival merchant";
    }

    @Nonnull
    private static String workRoleLabel(@Nonnull String workResidentKind) {
        String key = workResidentKind.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "innkeeper" -> "Innkeeper work spot";
            case "guild_master" -> "Guild master work spot";
            case "bard" -> "Bard work spot";
            case "elder" -> "Elder work spot";
            case "farmer" -> "Farmer work spot";
            case "miner" -> "Miner work spot";
            case "logger" -> "Logger work spot";
            case "rancher" -> "Rancher work spot";
            case "blacksmith" -> "Blacksmith work spot";
            case "merchant" -> "Merchant work spot";
            case "builder" -> "Builder work spot";
            case "florist" -> "Florist work spot";
            case "pyrotechnic" -> "Pyrotechnic work spot";
            case "crystal_keeper" -> "Crystal keeper work spot";
            case "chef" -> "Chef work spot";
            case "priestess" -> "Priestess work spot";
            case "market_shop" -> "Market stall";
            default -> "Work spot";
        };
    }
}
