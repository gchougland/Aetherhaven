package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.CustomFestivalPaths;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Reads and checks the festival only settings on the plot creator settings step. */
public final class PlotCreatorFestivalSettings {
    private PlotCreatorFestivalSettings() {}

    /** Names the festival from its display name and points the prefab at the festival prefab folder. */
    public static void applySuggestedId(@Nonnull PlotCreatorDraft draft, @Nonnull String slug) {
        if (draft.getEditingFestivalId() != null) {
            draft.setFestivalId(draft.getEditingFestivalId());
        } else {
            draft.setFestivalId(slug);
        }
        draft.setConstructionId(draft.getFestivalId());
        syncPrefabFileName(draft);
    }

    /** Festivals always export next to the other festival prefabs, named after the festival. */
    public static void syncPrefabFileName(@Nonnull PlotCreatorDraft draft) {
        String id = draft.getFestivalId();
        if (id == null || id.isBlank()) {
            return;
        }
        draft.setPrefabFileName(CustomFestivalPaths.prefabFileName(id));
    }

    /** Prefab key the festival JSON points at once the shape is saved. */
    @Nonnull
    public static String prefabPathKey(@Nonnull PlotCreatorDraft draft) {
        String id = draft.getFestivalId();
        return CustomFestivalPaths.prefabPathKey(id != null ? id : "festival");
    }

    /**
     * Validates and stores the festival name, day, and hours.
     *
     * @return plot creator error lang suffix, or null when everything is fine
     */
    @Nullable
    public static String applyInput(@Nonnull PlotCreatorDraft draft) {
        if (draft.getDisplayName() == null || draft.getDisplayName().isBlank()) {
            return "id_empty";
        }
        if (draft.getFestivalId() == null || draft.getFestivalId().isBlank()) {
            PlotCreatorService.suggestIdFromDisplayName(draft);
        }
        String id = draft.getFestivalId();
        if (id == null || id.isBlank()) {
            return "id_empty";
        }
        if (CustomFestivalPaths.isReserved(id)) {
            return "festivalIdReserved";
        }
        if (!id.matches("[a-z0-9_]+")) {
            return "festivalIdChars";
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin != null && draft.getEditingFestivalId() == null) {
            FestivalDefinition clash = plugin.getFestivalCatalog().get(id);
            if (clash != null) {
                return "festivalIdTaken";
            }
        }

        if (draft.isFestivalLookMode()) {
            String copied = copyScheduleFromBase(draft, plugin);
            if (copied != null) {
                return copied;
            }
            applyMechanic(draft);
            return null;
        }

        String seasonError = applySeason(draft);
        if (seasonError != null) {
            return seasonError;
        }
        String dayError = applyDay(draft);
        if (dayError != null) {
            return dayError;
        }
        String hoursError = applyHours(draft);
        if (hoursError != null) {
            return hoursError;
        }
        applyMechanic(draft);
        return null;
    }

    @Nullable
    private static String copyScheduleFromBase(@Nonnull PlotCreatorDraft draft, @Nullable AetherhavenPlugin plugin) {
        if (plugin == null) {
            return "needFestival";
        }
        FestivalDefinition base = plugin.getFestivalCatalog().get(draft.getCountsAsFestivalId());
        if (base == null || base.isLook()) {
            return "unknownFestival";
        }
        draft.setFestivalSeason(base.getSeason().name());
        draft.setFestivalSeasonInput(base.getSeason().displayName());
        draft.setFestivalDayOfSeason(base.getDayOfSeason());
        draft.setFestivalDayInput(String.valueOf(base.getDayOfSeason()));
        draft.setFestivalAllDay(base.isAllDay());
        draft.setFestivalStartHour(base.getStartHour());
        draft.setFestivalStartHourInput(String.valueOf(base.getStartHour()));
        draft.setFestivalEndHour(base.getEndHour());
        draft.setFestivalEndHourInput(String.valueOf(base.getEndHour()));
        draft.setFestivalMechanicId(base.getMechanicId());
        draft.setFestivalMechanicInput(PlotCreatorFestivalMechanicDefaults.displayLabel(base.getMechanicId()));
        return null;
    }

    private static void applyMechanic(@Nonnull PlotCreatorDraft draft) {
        String normalized = PlotCreatorFestivalMechanicDefaults.normalizeMechanicId(draft.getFestivalMechanicInput());
        draft.setFestivalMechanicId(normalized);
        draft.setFestivalMechanicInput(PlotCreatorFestivalMechanicDefaults.displayLabel(normalized));
        PlotCreatorFestivalMechanicDefaults.ensureRequiredSelectedSpots(draft);
    }

    @Nullable
    private static String applySeason(@Nonnull PlotCreatorDraft draft) {
        String raw = draft.getFestivalSeasonInput();
        if (raw == null || raw.isBlank()) {
            raw = draft.getFestivalSeason();
        }
        AetherhavenCalendar.Season season = AetherhavenCalendar.parseSeason(raw);
        if (season == null) {
            return "festivalSeason";
        }
        draft.setFestivalSeason(season.name());
        draft.setFestivalSeasonInput(season.displayName());
        return null;
    }

    @Nullable
    private static String applyDay(@Nonnull PlotCreatorDraft draft) {
        String raw = draft.getFestivalDayInput();
        if (raw == null || raw.isBlank()) {
            raw = String.valueOf(draft.getFestivalDayOfSeason());
        }
        int day;
        try {
            day = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return "festivalDay";
        }
        if (day < 1 || day > AetherhavenCalendar.DAYS_PER_SEASON) {
            return "festivalDay";
        }
        draft.setFestivalDayOfSeason(day);
        draft.setFestivalDayInput(String.valueOf(day));
        return null;
    }

    @Nullable
    private static String applyHours(@Nonnull PlotCreatorDraft draft) {
        if (draft.isFestivalAllDay()) {
            return null;
        }
        Integer start = parseHour(draft.getFestivalStartHourInput(), draft.getFestivalStartHour());
        if (start == null) {
            return "festivalHours";
        }
        Integer end = parseHour(draft.getFestivalEndHourInput(), draft.getFestivalEndHour());
        if (end == null) {
            return "festivalHours";
        }
        if (start.intValue() == end.intValue()) {
            return "festivalSameHours";
        }
        draft.setFestivalStartHour(start);
        draft.setFestivalStartHourInput(String.valueOf(start));
        draft.setFestivalEndHour(end);
        draft.setFestivalEndHourInput(String.valueOf(end));
        return null;
    }

    @Nullable
    private static Integer parseHour(@Nullable String raw, int fallback) {
        String value = raw != null && !raw.isBlank() ? raw.trim() : String.valueOf(fallback);
        int hour;
        try {
            hour = Integer.parseInt(value.toLowerCase(Locale.ROOT));
        } catch (NumberFormatException e) {
            return null;
        }
        return hour >= 0 && hour <= 23 ? Integer.valueOf(hour) : null;
    }
}
