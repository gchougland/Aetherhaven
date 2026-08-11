package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.festival.NewLifeFestivalMechanic;
import com.hexvane.aetherhaven.festival.carnival.CarnivalIds;
import com.hexvane.aetherhaven.festival.pigrace.PigRaceLanes;
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
        return mechanicId.trim();
    }

    /**
     * Adds any required selected-spot entries for the current mechanic without clearing placed markers.
     */
    public static void ensureRequiredSelectedSpots(@Nonnull PlotCreatorDraft draft) {
        LinkedHashSet<PlotCreatorSpotEntry> merged = new LinkedHashSet<>(draft.getSelectedSpots());
        for (PlotCreatorSpotEntry req : requiredSpotsForMechanic(draft)) {
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
            || CarnivalIds.MECHANIC_ID.equals(mechanic)) {
            int defaultTourists =
                CarnivalIds.MECHANIC_ID.equals(mechanic)
                    ? DEFAULT_CARNIVAL_TOURIST_SPOTS
                    : DEFAULT_PIG_RACE_TOURIST_SPOTS;
            int touristCount =
                Math.max(
                    draft.getFestivalTouristSpots().isEmpty()
                        ? defaultTourists
                        : draft.getFestivalTouristSpots().size(),
                    (PigRaceLanes.MECHANIC_ID.equals(mechanic) || CarnivalIds.MECHANIC_ID.equals(mechanic))
                        ? defaultTourists
                        : 1
                );
            out.add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.FESTIVAL_TOURIST_SPOT, touristCount));
        }
        if (draft.getFestivalCenterpieceLocal() != null || NewLifeFestivalMechanic.MECHANIC_ID.equals(mechanic)) {
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
        return out;
    }
}
