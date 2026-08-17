package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.festival.NewLifeFestivalMechanic;
import com.hexvane.aetherhaven.festival.carnival.CarnivalIds;
import com.hexvane.aetherhaven.festival.hallowseve.HallowsEveIds;
import com.hexvane.aetherhaven.festival.market.MarketIds;
import com.hexvane.aetherhaven.festival.pigrace.PigRaceLanes;
import com.hexvane.aetherhaven.festival.treeclimb.TreeClimbIds;
import com.hexvane.aetherhaven.festival.wintertide.WintertideIds;
import com.hexvane.aetherhaven.festival.snowball.SnowballIds;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Default important spots and activity labels for festival mechanics. */
public final class PlotCreatorFestivalMechanicDefaults {
    public static final int DEFAULT_PIG_RACE_TOURIST_SPOTS = 8;
    public static final int DEFAULT_PIG_RACE_LANES = 4;
    public static final int DEFAULT_CARNIVAL_BALLOON_SPAWNS = 6;
    public static final int DEFAULT_CARNIVAL_WHACK_SPAWNS = 6;
    public static final int DEFAULT_CARNIVAL_TOURIST_SPOTS = 8;
    public static final int DEFAULT_TREE_CLIMB_TOURIST_SPOTS = TreeClimbIds.DEFAULT_TOURIST_SPOTS;
    public static final int DEFAULT_TREE_CLIMB_STARTS = TreeClimbIds.MAX_RACERS;
    public static final int DEFAULT_HALLOWS_EVE_TOURIST_SPOTS = HallowsEveIds.DEFAULT_TOURIST_SPOTS;
    public static final int DEFAULT_HALLOWS_EVE_ORB_SPAWNS = HallowsEveIds.DEFAULT_ORB_SPAWNS;
    public static final int DEFAULT_MARKET_TOURIST_SPOTS = MarketIds.DEFAULT_TOURIST_SPOTS;
    public static final int DEFAULT_MARKET_STANDS = MarketIds.DEFAULT_STANDS;
    public static final int DEFAULT_MARKET_DISPLAYS = MarketIds.DEFAULT_DISPLAY_SLOTS;
    public static final int DEFAULT_WINTERTIDE_TOURIST_SPOTS = WintertideIds.DEFAULT_TOURIST_SPOTS;
    public static final int DEFAULT_SNOWBALL_TOURIST_SPOTS = SnowballIds.DEFAULT_TOURIST_SPOTS;
    public static final int DEFAULT_SNOWBALL_PILES = SnowballIds.DEFAULT_PILE_SPOTS;
    public static final int DEFAULT_SNOWBALL_TEAM_PADS = SnowballIds.TEAM_SIZE;

    private PlotCreatorFestivalMechanicDefaults() {}

    @Nullable
    public static String normalizeMechanicId(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if ("none".equals(lower) || "off".equals(lower) || "-".equals(lower)) {
            return null;
        }
        if ("new life".equals(lower) || "new_life".equals(lower) || "newlife".equals(lower)) {
            return NewLifeFestivalMechanic.MECHANIC_ID;
        }
        if ("pig racing".equals(lower)
            || "pig race".equals(lower)
            || "pig_race".equals(lower)
            || "pigrace".equals(lower)) {
            return PigRaceLanes.MECHANIC_ID;
        }
        if ("carnival".equals(lower) || "carnival festival".equals(lower)) {
            return CarnivalIds.MECHANIC_ID;
        }
        if ("tree climbing".equals(lower)
            || "tree climb".equals(lower)
            || "tree_climbing".equals(lower)
            || "treeclimb".equals(lower)) {
            return TreeClimbIds.MECHANIC_ID;
        }
        if ("hallow's eve".equals(lower)
            || "hallows eve".equals(lower)
            || "hallows_eve".equals(lower)
            || "hallowseve".equals(lower)
            || "halloween".equals(lower)) {
            return HallowsEveIds.MECHANIC_ID;
        }
        if ("market".equals(lower) || "market festival".equals(lower)) {
            return MarketIds.MECHANIC_ID;
        }
        if ("wintertide".equals(lower)
            || "winter tide".equals(lower)
            || "winter_tide".equals(lower)) {
            return WintertideIds.MECHANIC_ID;
        }
        if ("snowball".equals(lower)
            || "snowball throwing".equals(lower)
            || "snowball fight".equals(lower)
            || "snowball_throwing".equals(lower)) {
            return SnowballIds.MECHANIC_ID;
        }
        return lower;
    }

    @Nonnull
    public static String displayLabel(@Nullable String mechanicId) {
        if (mechanicId == null || mechanicId.isBlank()) {
            return "None";
        }
        String key = mechanicId.trim().toLowerCase(Locale.ROOT);
        if (NewLifeFestivalMechanic.MECHANIC_ID.equals(key)) {
            return "New Life";
        }
        if (PigRaceLanes.MECHANIC_ID.equals(key)) {
            return "Pig Racing";
        }
        if (CarnivalIds.MECHANIC_ID.equals(key)) {
            return "Carnival";
        }
        if (TreeClimbIds.MECHANIC_ID.equals(key)) {
            return "Tree Climbing";
        }
        if (HallowsEveIds.MECHANIC_ID.equals(key)) {
            return "Hallow's Eve";
        }
        if (MarketIds.MECHANIC_ID.equals(key)) {
            return "Market Festival";
        }
        if (WintertideIds.MECHANIC_ID.equals(key)) {
            return "Wintertide";
        }
        if (SnowballIds.MECHANIC_ID.equals(key)) {
            return "Snowball Throwing";
        }
        return mechanicId.trim();
    }

    /**
     * Adds any required selected-spot entries for the current mechanic without clearing placed markers.
     */
    public static void ensureRequiredSelectedSpots(@Nonnull PlotCreatorDraft draft) {
        LinkedHashSet<PlotCreatorSpotEntry> merged = new LinkedHashSet<>(draft.getSelectedSpots());
        for (PlotCreatorSpotEntry req : requiredSpotsForMechanic(draft)) {
            merged.remove(req);
            merged.add(req);
        }
        draft.getSelectedSpots().clear();
        draft.getSelectedSpots().addAll(merged);
    }

    @Nonnull
    public static List<PlotCreatorSpotEntry> requiredSpotsForMechanic(@Nonnull PlotCreatorDraft draft) {
        List<PlotCreatorSpotEntry> out = new ArrayList<>();
        String mechanic = draft.getFestivalMechanicId();
        for (var poi : draft.getPois()) {
            String role = poi.getWorkResidentKind();
            if (role != null && !role.isBlank() && poi.getTags().contains("WORK")) {
                out.add(PlotCreatorSpotEntry.workOrBard(role, 1));
            }
        }
        for (var npc : draft.getFestivalNpcs()) {
            if (!npc.getNpcRoleId().isEmpty()) {
                out.add(PlotCreatorSpotEntry.festivalNpc(npc.getNpcRoleId(), 1));
            }
        }
        String defaultMerchant = PlotCreatorFestivalNpcRoles.defaultMerchantForMechanic(mechanic);
        if (defaultMerchant != null) {
            out.add(PlotCreatorSpotEntry.festivalNpc(defaultMerchant, 1));
        }
        if (!draft.getFestivalTouristSpots().isEmpty()
            || PigRaceLanes.MECHANIC_ID.equals(mechanic)
            || CarnivalIds.MECHANIC_ID.equals(mechanic)
            || TreeClimbIds.MECHANIC_ID.equals(mechanic)
            || HallowsEveIds.MECHANIC_ID.equals(mechanic)
            || MarketIds.MECHANIC_ID.equals(mechanic)
            || WintertideIds.MECHANIC_ID.equals(mechanic)
            || SnowballIds.MECHANIC_ID.equals(mechanic)) {
            int defaultTourists =
                CarnivalIds.MECHANIC_ID.equals(mechanic)
                    ? DEFAULT_CARNIVAL_TOURIST_SPOTS
                    : TreeClimbIds.MECHANIC_ID.equals(mechanic)
                        ? DEFAULT_TREE_CLIMB_TOURIST_SPOTS
                        : HallowsEveIds.MECHANIC_ID.equals(mechanic)
                            ? DEFAULT_HALLOWS_EVE_TOURIST_SPOTS
                            : MarketIds.MECHANIC_ID.equals(mechanic)
                                ? DEFAULT_MARKET_TOURIST_SPOTS
                                : WintertideIds.MECHANIC_ID.equals(mechanic)
                                    ? DEFAULT_WINTERTIDE_TOURIST_SPOTS
                                    : SnowballIds.MECHANIC_ID.equals(mechanic)
                                        ? DEFAULT_SNOWBALL_TOURIST_SPOTS
                                        : DEFAULT_PIG_RACE_TOURIST_SPOTS;
            boolean forceTourists =
                PigRaceLanes.MECHANIC_ID.equals(mechanic)
                    || CarnivalIds.MECHANIC_ID.equals(mechanic)
                    || TreeClimbIds.MECHANIC_ID.equals(mechanic)
                    || HallowsEveIds.MECHANIC_ID.equals(mechanic)
                    || MarketIds.MECHANIC_ID.equals(mechanic)
                    || WintertideIds.MECHANIC_ID.equals(mechanic)
                    || SnowballIds.MECHANIC_ID.equals(mechanic);
            int touristCount =
                Math.max(
                    draft.getFestivalTouristSpots().isEmpty()
                        ? defaultTourists
                        : draft.getFestivalTouristSpots().size(),
                    forceTourists ? defaultTourists : 1
                );
            out.add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_TOURIST_SPOT, touristCount));
        }
        if (draft.getFestivalCenterpieceLocal() != null
            || NewLifeFestivalMechanic.MECHANIC_ID.equals(mechanic)
            || HallowsEveIds.MECHANIC_ID.equals(mechanic)) {
            out.add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_CENTERPIECE, 1));
        }
        if (!draft.getFestivalRaceLanes().isEmpty() || PigRaceLanes.MECHANIC_ID.equals(mechanic)) {
            int laneCount =
                Math.max(
                    draft.getFestivalRaceLanes().isEmpty()
                        ? DEFAULT_PIG_RACE_LANES
                        : draft.getFestivalRaceLanes().size(),
                    PigRaceLanes.MECHANIC_ID.equals(mechanic) ? DEFAULT_PIG_RACE_LANES : 1
                );
            out.add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_RACE_LANE, laneCount));
        }
        if (!draft.getFestivalBalloonSpawns().isEmpty() || CarnivalIds.MECHANIC_ID.equals(mechanic)) {
            int balloonCount =
                Math.max(
                    draft.getFestivalBalloonSpawns().isEmpty()
                        ? DEFAULT_CARNIVAL_BALLOON_SPAWNS
                        : draft.getFestivalBalloonSpawns().size(),
                    CarnivalIds.MECHANIC_ID.equals(mechanic) ? DEFAULT_CARNIVAL_BALLOON_SPAWNS : 1
                );
            out.add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_BALLOON_SPAWN, balloonCount));
        }
        if (!draft.getFestivalWhackSpawns().isEmpty() || CarnivalIds.MECHANIC_ID.equals(mechanic)) {
            int whackCount =
                Math.max(
                    draft.getFestivalWhackSpawns().isEmpty()
                        ? DEFAULT_CARNIVAL_WHACK_SPAWNS
                        : draft.getFestivalWhackSpawns().size(),
                    CarnivalIds.MECHANIC_ID.equals(mechanic) ? DEFAULT_CARNIVAL_WHACK_SPAWNS : 1
                );
            out.add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_WHACK_SPAWN, whackCount));
        }
        if (draft.getFestivalWheelLocal() != null || CarnivalIds.MECHANIC_ID.equals(mechanic)) {
            out.add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_WHEEL, 1));
        }
        if (CarnivalIds.MECHANIC_ID.equals(mechanic)) {
            out.add(PlotCreatorSpotEntry.festivalNpc(CarnivalIds.BALLOON_NPC_ROLE, 1));
            out.add(PlotCreatorSpotEntry.festivalNpc(CarnivalIds.WHEEL_NPC_ROLE, 1));
            out.add(PlotCreatorSpotEntry.festivalNpc(CarnivalIds.WHACK_NPC_ROLE, 1));
        }
        if (!draft.getFestivalRaceStartSpots().isEmpty() || TreeClimbIds.MECHANIC_ID.equals(mechanic)) {
            int startCount =
                Math.max(
                    draft.getFestivalRaceStartSpots().isEmpty()
                        ? DEFAULT_TREE_CLIMB_STARTS
                        : draft.getFestivalRaceStartSpots().size(),
                    TreeClimbIds.MECHANIC_ID.equals(mechanic) ? DEFAULT_TREE_CLIMB_STARTS : 1
                );
            out.add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_TREE_CLIMB_START, startCount));
        }
        if (draft.getFestivalRaceFinishLocal() != null || TreeClimbIds.MECHANIC_ID.equals(mechanic)) {
            out.add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_TREE_CLIMB_FINISH, 1));
        }
        if (TreeClimbIds.MECHANIC_ID.equals(mechanic)) {
            out.add(PlotCreatorSpotEntry.festivalNpc(TreeClimbIds.ATTENDANT_NPC_ROLE, 1));
        }
        if (draft.getFestivalMazeStartLocal() != null || HallowsEveIds.MECHANIC_ID.equals(mechanic)) {
            out.add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_MAZE_START, 1));
        }
        if (!draft.getFestivalOrbSpawns().isEmpty() || HallowsEveIds.MECHANIC_ID.equals(mechanic)) {
            int orbCount =
                Math.max(
                    draft.getFestivalOrbSpawns().isEmpty()
                        ? DEFAULT_HALLOWS_EVE_ORB_SPAWNS
                        : draft.getFestivalOrbSpawns().size(),
                    HallowsEveIds.MECHANIC_ID.equals(mechanic) ? DEFAULT_HALLOWS_EVE_ORB_SPAWNS : 1
                );
            out.add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_MAZE_ORB_SPAWN, orbCount));
        }
        if (MarketIds.MECHANIC_ID.equals(mechanic)) {
            out.add(PlotCreatorSpotEntry.workOrBard(com.hexvane.aetherhaven.villager.TownVillagerBinding.KIND_ELDER, 1));
            out.add(PlotCreatorSpotEntry.workOrBard(MarketIds.KIND_MARKET_SHOP, MarketIds.SHOP_SPOT_COUNT));
        }
        if (MarketIds.MECHANIC_ID.equals(mechanic) || !draft.getFestivalMarketStands().isEmpty()) {
            int standCount =
                MarketIds.MECHANIC_ID.equals(mechanic)
                    ? DEFAULT_MARKET_STANDS
                    : Math.max(1, draft.getFestivalMarketStands().size());
            out.add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_MARKET_STAND, standCount));
        }
        if (MarketIds.MECHANIC_ID.equals(mechanic) || !draft.getFestivalMarketDisplaySlots().isEmpty()) {
            int displayCount =
                MarketIds.MECHANIC_ID.equals(mechanic)
                    ? DEFAULT_MARKET_DISPLAYS
                    : Math.max(1, draft.getFestivalMarketDisplaySlots().size());
            out.add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_MARKET_DISPLAY, displayCount));
        }
        if (!draft.getFestivalSnowballPileSpots().isEmpty() || SnowballIds.MECHANIC_ID.equals(mechanic)) {
            int pileCount =
                Math.max(
                    draft.getFestivalSnowballPileSpots().isEmpty()
                        ? DEFAULT_SNOWBALL_PILES
                        : draft.getFestivalSnowballPileSpots().size(),
                    SnowballIds.MECHANIC_ID.equals(mechanic) ? DEFAULT_SNOWBALL_PILES : 1
                );
            out.add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_SNOWBALL_PILE, pileCount));
        }
        if (!draft.getFestivalSnowballTeamASpots().isEmpty() || SnowballIds.MECHANIC_ID.equals(mechanic)) {
            int teamACount =
                Math.max(
                    draft.getFestivalSnowballTeamASpots().isEmpty()
                        ? DEFAULT_SNOWBALL_TEAM_PADS
                        : draft.getFestivalSnowballTeamASpots().size(),
                    SnowballIds.MECHANIC_ID.equals(mechanic) ? DEFAULT_SNOWBALL_TEAM_PADS : 1
                );
            out.add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_SNOWBALL_TEAM_A, teamACount));
        }
        if (!draft.getFestivalSnowballTeamBSpots().isEmpty() || SnowballIds.MECHANIC_ID.equals(mechanic)) {
            int teamBCount =
                Math.max(
                    draft.getFestivalSnowballTeamBSpots().isEmpty()
                        ? DEFAULT_SNOWBALL_TEAM_PADS
                        : draft.getFestivalSnowballTeamBSpots().size(),
                    SnowballIds.MECHANIC_ID.equals(mechanic) ? DEFAULT_SNOWBALL_TEAM_PADS : 1
                );
            out.add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_SNOWBALL_TEAM_B, teamBCount));
        }
        if (draft.getFestivalSnowballOutLocal() != null || SnowballIds.MECHANIC_ID.equals(mechanic)) {
            out.add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_SNOWBALL_OUT, 1));
        }
        return out;
    }
}
