package com.hexvane.aetherhaven.plotcreator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Writes a festival JSON from a plot creator draft. Fields the wizard does not expose (mechanic, festival NPCs, calendar
 * icon, centerpiece, burst items, villager greetings) are carried over from the festival being edited so hand authored
 * setups survive a round trip through the wizard.
 */
public final class PlotCreatorFestivalJsonWriter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private PlotCreatorFestivalJsonWriter() {}

    public static void writeFestival(
        @Nonnull Path outputFile,
        @Nonnull PlotCreatorDraft draft,
        @Nullable FestivalDefinition existing
    ) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("id", draft.getFestivalId());
        root.put("displayName", draft.getDisplayName());
        if (existing != null && existing.getDisplayNameLangKey() != null) {
            root.put("displayNameLangKey", existing.getDisplayNameLangKey());
        }
        String description = draft.getDescription();
        if (description != null && !description.isBlank()) {
            root.put("description", description.trim());
        }
        root.put("prefabPath", draft.getPrefabPath());
        root.put("season", seasonName(draft));
        root.put("dayOfSeason", draft.getFestivalDayOfSeason());
        if (draft.isFestivalAllDay()) {
            root.put("allDay", true);
        } else {
            root.put("startHour", draft.getFestivalStartHour());
            root.put("endHour", draft.getFestivalEndHour());
            if (existing != null && existing.getStartMinute() != 0) {
                root.put("startMinute", existing.getStartMinute());
            }
            if (existing != null && existing.getEndMinute() != 0) {
                root.put("endMinute", existing.getEndMinute());
            }
        }
        if (existing != null) {
            root.put("calendarIconPath", existing.getCalendarIconPath());
            if (existing.getMechanicId() != null) {
                root.put("mechanicId", existing.getMechanicId());
            }
        }
        List<FestivalDefinition.SpotRow> spots = PlotCreatorFestivalSpots.fromDraft(draft);
        if (!spots.isEmpty()) {
            root.put("spots", spotMaps(spots));
        }
        if (existing != null) {
            if (!existing.getNpcs().isEmpty()) {
                root.put("npcs", npcMaps(existing.getNpcs()));
            }
            int[] centerpiece = existing.getCenterpieceLocal();
            if (centerpiece != null) {
                root.put("centerpieceLocal", List.of(centerpiece[0], centerpiece[1], centerpiece[2]));
            }
            if (!existing.getBurstItemIds().isEmpty()) {
                root.put("burstItemIds", new ArrayList<>(existing.getBurstItemIds()));
            }
            if (!existing.getTags().isEmpty()) {
                root.put("tags", new ArrayList<>(existing.getTags()));
            }
            if (!existing.getGreetings().isEmpty()) {
                root.put("greetings", new LinkedHashMap<>(existing.getGreetings()));
            }
        }
        Files.createDirectories(outputFile.getParent());
        Files.writeString(outputFile, GSON.toJson(root), StandardCharsets.UTF_8);
    }

    @Nonnull
    private static String seasonName(@Nonnull PlotCreatorDraft draft) {
        AetherhavenCalendar.Season season = AetherhavenCalendar.parseSeason(draft.getFestivalSeason());
        return season != null ? season.name() : AetherhavenCalendar.Season.SPRING.name();
    }

    @Nonnull
    private static List<Map<String, Object>> spotMaps(@Nonnull List<FestivalDefinition.SpotRow> spots) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (FestivalDefinition.SpotRow spot : spots) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("residentKind", spot.getResidentKind());
            row.put("localX", spot.getLocalX());
            row.put("localY", spot.getLocalY());
            row.put("localZ", spot.getLocalZ());
            row.put("yawDegrees", spot.getYawDegrees());
            out.add(row);
        }
        return out;
    }

    @Nonnull
    private static List<Map<String, Object>> npcMaps(@Nonnull List<FestivalDefinition.NpcRow> npcs) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (FestivalDefinition.NpcRow npc : npcs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("npcRoleId", npc.getNpcRoleId());
            if (npc.getDisplayName() != null) {
                row.put("displayName", npc.getDisplayName());
            }
            row.put("localX", npc.getLocalX());
            row.put("localY", npc.getLocalY());
            row.put("localZ", npc.getLocalZ());
            row.put("yawDegrees", npc.getYawDegrees());
            out.add(row);
        }
        return out;
    }
}
