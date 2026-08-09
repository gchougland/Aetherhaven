package com.hexvane.aetherhaven.festival;

import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * One festival from {@link com.hexvane.aetherhaven.asset.AetherhavenAssetPaths#FESTIVALS}. On its calendar day the
 * festival square swaps its prefab to {@link #getPrefabPath()} for the active window, then swaps back.
 *
 * <p>Every festival prefab must match {@link FestivalPrefabSize} exactly so any festival fits the same plot footprint.
 */
public final class FestivalDefinition {
    /** Greeting bucket used for villagers the festival does not name. */
    public static final String GREETING_DEFAULT_KIND = "default";

    @SerializedName("id")
    @Nullable
    private String id;

    @SerializedName("displayName")
    @Nullable
    private String displayName;

    @SerializedName("displayNameLangKey")
    @Nullable
    private String displayNameLangKey;

    @SerializedName("description")
    @Nullable
    private String description;

    /** Prefab key under {@code Server/Prefabs/}, e.g. {@code Festivals/Festival_New_Life.prefab.json}. */
    @SerializedName("prefabPath")
    @Nullable
    private String prefabPath;

    @SerializedName("season")
    @Nullable
    private String season;

    @SerializedName("dayOfSeason")
    private int dayOfSeason = 1;

    @SerializedName("allDay")
    private boolean allDay;

    @SerializedName("startHour")
    private int startHour = 8;

    @SerializedName("startMinute")
    private int startMinute;

    @SerializedName("endHour")
    private int endHour = 20;

    @SerializedName("endMinute")
    private int endMinute;

    /** Common asset path for the calendar day marker; defaults to a flag icon. */
    @SerializedName("calendarIconPath")
    @Nullable
    private String calendarIconPath;

    /** Registered {@link FestivalMechanic} id; blank means the festival is decoration only. */
    @SerializedName("mechanicId")
    @Nullable
    private String mechanicId;

    @SerializedName("spots")
    @Nullable
    private List<SpotRow> spots;

    @SerializedName("npcs")
    @Nullable
    private List<NpcRow> npcs;

    /** Prefab-local cell of the festival centerpiece; when unset the mechanic looks for its prefab placeholder. */
    @SerializedName("centerpieceLocal")
    @Nullable
    private int[] centerpieceLocal;

    /** Item ids a mechanic may hand out, e.g. the New Life seed burst pool. */
    @SerializedName("burstItemIds")
    @Nullable
    private List<String> burstItemIds;

    @SerializedName("tags")
    @Nullable
    private List<String> tags;

    /**
     * What the townsfolk say while the festival is on, keyed by villager kind ({@code farmer}, {@code elder}, and so
     * on, the same names {@link SpotRow#getResidentKind()} uses). {@value #GREETING_DEFAULT_KIND} covers anyone with no
     * lines of their own, so a festival never has to list every villager.
     */
    @SerializedName("greetings")
    @Nullable
    private Map<String, List<String>> greetings;

    @Nonnull
    public String getId() {
        return id != null ? id.trim() : "";
    }

    @Nonnull
    public String getDisplayName() {
        return displayName != null && !displayName.isBlank() ? displayName.trim() : getId();
    }

    @Nullable
    public String getDisplayNameLangKey() {
        return displayNameLangKey != null && !displayNameLangKey.isBlank() ? displayNameLangKey.trim() : null;
    }

    @Nullable
    public String getDescription() {
        return description != null && !description.isBlank() ? description.trim() : null;
    }

    @Nonnull
    public String getPrefabPath() {
        return prefabPath != null ? prefabPath.trim() : "";
    }

    /** Falls back to Spring when the JSON season is missing or unparseable. */
    @Nonnull
    public AetherhavenCalendar.Season getSeason() {
        AetherhavenCalendar.Season parsed = AetherhavenCalendar.parseSeason(season);
        return parsed != null ? parsed : AetherhavenCalendar.Season.SPRING;
    }

    public int getDayOfSeason() {
        return Math.max(1, Math.min(AetherhavenCalendar.DAYS_PER_SEASON, dayOfSeason));
    }

    public boolean isAllDay() {
        return allDay;
    }

    public int getStartHour() {
        return clampHour(startHour);
    }

    public int getStartMinute() {
        return clampMinute(startMinute);
    }

    public int getEndHour() {
        return clampHour(endHour);
    }

    public int getEndMinute() {
        return clampMinute(endMinute);
    }

    @Nonnull
    public String getCalendarIconPath() {
        return calendarIconPath != null && !calendarIconPath.isBlank()
            ? calendarIconPath.trim()
            : FestivalIcons.DEFAULT_CALENDAR_ICON;
    }

    @Nullable
    public String getMechanicId() {
        return mechanicId != null && !mechanicId.isBlank() ? mechanicId.trim() : null;
    }

    @Nonnull
    public List<SpotRow> getSpots() {
        return spots != null ? List.copyOf(spots) : List.of();
    }

    @Nonnull
    public List<NpcRow> getNpcs() {
        return npcs != null ? List.copyOf(npcs) : List.of();
    }

    @Nullable
    public int[] getCenterpieceLocal() {
        if (centerpieceLocal == null || centerpieceLocal.length < 3) {
            return null;
        }
        return new int[] {centerpieceLocal[0], centerpieceLocal[1], centerpieceLocal[2]};
    }

    @Nonnull
    public List<String> getBurstItemIds() {
        if (burstItemIds == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String s : burstItemIds) {
            if (s != null && !s.isBlank()) {
                out.add(s.trim());
            }
        }
        return List.copyOf(out);
    }

    @Nonnull
    public List<String> getTags() {
        if (tags == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String s : tags) {
            if (s != null && !s.isBlank()) {
                out.add(s.trim());
            }
        }
        return List.copyOf(out);
    }

    /** Every greeting bucket the festival defines, keyed by lower case villager kind. */
    @Nonnull
    public Map<String, List<String>> getGreetings() {
        if (greetings == null || greetings.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : greetings.entrySet()) {
            String kind = normalizeKind(entry.getKey());
            List<String> keys = trimmedKeys(entry.getValue());
            if (!kind.isEmpty() && !keys.isEmpty()) {
                out.put(kind, keys);
            }
        }
        return Map.copyOf(out);
    }

    /**
     * Lang keys this villager kind can greet with during the festival, falling back to
     * {@value #GREETING_DEFAULT_KIND} when the festival has no lines written for them.
     */
    @Nonnull
    public List<String> getGreetingLangKeys(@Nullable String residentKind) {
        Map<String, List<String>> all = getGreetings();
        if (all.isEmpty()) {
            return List.of();
        }
        List<String> own = all.get(normalizeKind(residentKind));
        if (own != null && !own.isEmpty()) {
            return own;
        }
        List<String> fallback = all.get(GREETING_DEFAULT_KIND);
        return fallback != null ? fallback : List.of();
    }

    @Nonnull
    private static String normalizeKind(@Nullable String kind) {
        return kind != null ? kind.trim().toLowerCase(Locale.ROOT) : "";
    }

    @Nonnull
    private static List<String> trimmedKeys(@Nullable List<String> raw) {
        if (raw == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String s : raw) {
            if (s != null && !s.isBlank()) {
                out.add(s.trim());
            }
        }
        return List.copyOf(out);
    }

    /** Minutes after midnight when the festival opens, or 0 for an all day festival. */
    public int startMinuteOfDay() {
        return allDay ? 0 : getStartHour() * 60 + getStartMinute();
    }

    /** Minutes after midnight when the festival closes; an all day festival runs to the end of the day. */
    public int endMinuteOfDay() {
        return allDay ? 24 * 60 : getEndHour() * 60 + getEndMinute();
    }

    private static int clampHour(int value) {
        return Math.max(0, Math.min(23, value));
    }

    private static int clampMinute(int value) {
        return Math.max(0, Math.min(59, value));
    }

    /** A place a villager or festival NPC stands for the length of the festival. */
    public static final class SpotRow {
        /** Villager binding kind, e.g. {@code priestess}, {@code farmer}, {@code elder}. */
        @SerializedName("residentKind")
        @Nullable
        private String residentKind;

        @SerializedName("localX")
        private int localX;

        @SerializedName("localY")
        private int localY;

        @SerializedName("localZ")
        private int localZ;

        @SerializedName("yawDegrees")
        private float yawDegrees;

        @SerializedName("required")
        private boolean required = true;

        @Nonnull
        public String getResidentKind() {
            return residentKind != null ? residentKind.trim().toLowerCase(java.util.Locale.ROOT) : "";
        }

        public int getLocalX() {
            return localX;
        }

        public int getLocalY() {
            return localY;
        }

        public int getLocalZ() {
            return localZ;
        }

        public float getYawDegrees() {
            return yawDegrees;
        }

        public boolean isRequired() {
            return required;
        }

        @Nonnull
        public static SpotRow of(@Nonnull String residentKind, int x, int y, int z, float yawDegrees) {
            SpotRow row = new SpotRow();
            row.residentKind = residentKind;
            row.localX = x;
            row.localY = y;
            row.localZ = z;
            row.yawDegrees = yawDegrees;
            return row;
        }
    }

    /** A festival-only NPC spawned for the length of the festival. */
    public static final class NpcRow {
        @SerializedName("npcRoleId")
        @Nullable
        private String npcRoleId;

        @SerializedName("displayName")
        @Nullable
        private String displayName;

        @SerializedName("localX")
        private int localX;

        @SerializedName("localY")
        private int localY;

        @SerializedName("localZ")
        private int localZ;

        @SerializedName("yawDegrees")
        private float yawDegrees;

        @Nonnull
        public String getNpcRoleId() {
            return npcRoleId != null ? npcRoleId.trim() : "";
        }

        @Nullable
        public String getDisplayName() {
            return displayName != null && !displayName.isBlank() ? displayName.trim() : null;
        }

        public int getLocalX() {
            return localX;
        }

        public int getLocalY() {
            return localY;
        }

        public int getLocalZ() {
            return localZ;
        }

        public float getYawDegrees() {
            return yawDegrees;
        }
    }
}
