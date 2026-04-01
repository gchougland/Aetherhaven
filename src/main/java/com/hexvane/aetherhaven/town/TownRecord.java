package com.hexvane.aetherhaven.town;

import com.google.gson.annotations.SerializedName;
import com.hypixel.hytale.logger.HytaleLogger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class TownRecord {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @SerializedName("townId")
    private String townId;

    @SerializedName("ownerUuid")
    private String ownerUuid;

    @SerializedName("worldName")
    private String worldName;

    @SerializedName("charterX")
    private int charterX;

    @SerializedName("charterY")
    private int charterY;

    @SerializedName("charterZ")
    private int charterZ;

    @SerializedName("tier")
    private int tier;

    @SerializedName("territoryChunkRadius")
    private int territoryChunkRadius;

    @SerializedName("createdTimeEpochMs")
    private long createdTimeEpochMs;

    @SerializedName("elderSpawned")
    private boolean elderSpawned;

    /** Legacy saves only; migrated into {@link #plotInstances} on load. */
    @SerializedName("plotFootprints")
    @Nullable
    private List<PlotFootprintRecord> plotFootprints;

    @SerializedName("plotInstances")
    private List<PlotInstance> plotInstances = new ArrayList<>();

    @SerializedName("activeQuestIds")
    private List<String> activeQuestIds = new ArrayList<>();

    @SerializedName("completedQuestIds")
    private List<String> completedQuestIds = new ArrayList<>();

    @Nullable
    @SerializedName("elderEntityUuid")
    private String elderEntityUuid;

    @SerializedName("innActive")
    private boolean innActive;

    @Nullable
    @SerializedName("innkeeperEntityUuid")
    private String innkeeperEntityUuid;

    /**
     * Up to two inn visitor NPC entity UUID strings (see {@link #getInnPoolNpcIds}).
     * Cleared when innkeeper first spawns; repopulated by {@link com.hexvane.aetherhaven.inn.InnPoolSystem}.
     */
    @SerializedName("innPoolNpcIds")
    private List<String> innPoolNpcIds = new ArrayList<>();

    /** Entity UUIDs that must not be removed on inn pool refresh (lock-on-accept). */
    @SerializedName("innLockedEntityUuids")
    private List<String> innLockedEntityUuids = new ArrayList<>();

    /** Last inn pool refresh instant in world game time (ISO-8601), not wall clock. Legacy; see {@link #innPoolLastMorningGameDate}. */
    @Nullable
    @SerializedName("innPoolLastRefreshGameTime")
    private String innPoolLastRefreshGameTime;

    /** Calendar game date (UTC, YYYY-MM-DD) when we last ran the morning inn shuffle; kept for saves readability. */
    @Nullable
    @SerializedName("innPoolLastMorningGameDate")
    private String innPoolLastMorningGameDate;

    /**
     * Game calendar epoch day ({@link LocalDate#toEpochDay()}) when we last ran the morning inn shuffle.
     * Compared to current game date so {@code /time} to the next morning on a new day triggers refresh.
     */
    @Nullable
    @SerializedName("innPoolLastMorningEpochDay")
    private Long innPoolLastMorningEpochDay;

    /**
     * NPC role ids (e.g. {@link com.hexvane.aetherhaven.AetherhavenConstants#NPC_MERCHANT}) that must never be chosen
     * when filling the inn visitor pool (e.g. after promotion to a permanent town role).
     */
    @SerializedName("innVisitorPoolExcludedRoleIds")
    private LinkedHashSet<String> innVisitorPoolExcludedRoleIds = new LinkedHashSet<>();

    public TownRecord() {}

    public TownRecord(
        @Nonnull UUID townId,
        @Nonnull UUID ownerUuid,
        @Nonnull String worldName,
        int charterX,
        int charterY,
        int charterZ,
        int tier,
        int territoryChunkRadius,
        long createdTimeEpochMs
    ) {
        this.townId = townId.toString();
        this.ownerUuid = ownerUuid.toString();
        this.worldName = worldName;
        this.charterX = charterX;
        this.charterY = charterY;
        this.charterZ = charterZ;
        this.tier = tier;
        this.territoryChunkRadius = territoryChunkRadius;
        this.createdTimeEpochMs = createdTimeEpochMs;
    }

    /** Convert legacy {@code plotFootprints} entries into {@link PlotInstance} rows (COMPLETE, synthetic id). */
    public void migrateLegacyPlotFootprintsIfNeeded() {
        List<PlotFootprintRecord> legacy = plotFootprints;
        if (legacy == null || legacy.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (PlotFootprintRecord fp : legacy) {
            PlotInstance pi = new PlotInstance(
                UUID.randomUUID(),
                "legacy_migrated",
                PlotInstanceState.COMPLETE,
                fp,
                charterX,
                charterY,
                charterZ,
                now
            );
            getPlotInstances().add(pi);
        }
        LOGGER.atInfo().log("Migrated %s legacy plot footprints for town %s", legacy.size(), townId);
        plotFootprints = null;
    }

    public void migrateInnFieldsIfNeeded() {
        if (innPoolNpcIds == null) {
            innPoolNpcIds = new ArrayList<>();
        }
        if (innLockedEntityUuids == null) {
            innLockedEntityUuids = new ArrayList<>();
        }
        if (innPoolLastMorningGameDate == null && innPoolLastRefreshGameTime != null && !innPoolLastRefreshGameTime.isBlank()) {
            try {
                Instant inst = Instant.parse(innPoolLastRefreshGameTime.trim());
                innPoolLastMorningGameDate = inst.atZone(ZoneOffset.UTC).toLocalDate().toString();
            } catch (Exception ignored) {
            }
        }
        if (innPoolLastMorningEpochDay == null && innPoolLastMorningGameDate != null && !innPoolLastMorningGameDate.isBlank()) {
            try {
                innPoolLastMorningEpochDay = LocalDate.parse(innPoolLastMorningGameDate.trim()).toEpochDay();
            } catch (Exception ignored) {
            }
        }
        if (innVisitorPoolExcludedRoleIds == null) {
            innVisitorPoolExcludedRoleIds = new LinkedHashSet<>();
        }
    }

    @Nonnull
    public Set<String> getInnVisitorPoolExcludedRoleIds() {
        if (innVisitorPoolExcludedRoleIds == null) {
            innVisitorPoolExcludedRoleIds = new LinkedHashSet<>();
        }
        return innVisitorPoolExcludedRoleIds;
    }

    public void addInnVisitorPoolExcludedRoleId(@Nonnull String roleId) {
        if (roleId.isBlank()) {
            return;
        }
        getInnVisitorPoolExcludedRoleIds().add(roleId.trim());
    }

    @Nonnull
    public UUID getTownId() {
        return UUID.fromString(townId);
    }

    public void setTownId(@Nonnull UUID id) {
        this.townId = id.toString();
    }

    @Nonnull
    public UUID getOwnerUuid() {
        return UUID.fromString(ownerUuid);
    }

    @Nonnull
    public String getWorldName() {
        return worldName != null ? worldName : "";
    }

    public int getCharterX() {
        return charterX;
    }

    public int getCharterY() {
        return charterY;
    }

    public int getCharterZ() {
        return charterZ;
    }

    public int getTier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = tier;
    }

    public int getTerritoryChunkRadius() {
        return territoryChunkRadius;
    }

    public void setTerritoryChunkRadius(int territoryChunkRadius) {
        this.territoryChunkRadius = territoryChunkRadius;
    }

    public long getCreatedTimeEpochMs() {
        return createdTimeEpochMs;
    }

    public boolean isElderSpawned() {
        return elderSpawned;
    }

    public void setElderSpawned(boolean elderSpawned) {
        this.elderSpawned = elderSpawned;
    }

    @Nullable
    public UUID getElderEntityUuid() {
        return elderEntityUuid != null && !elderEntityUuid.isEmpty() ? UUID.fromString(elderEntityUuid) : null;
    }

    public void setElderEntityUuid(@Nullable UUID uuid) {
        this.elderEntityUuid = uuid != null ? uuid.toString() : null;
    }

    public boolean isInnActive() {
        return innActive;
    }

    public void setInnActive(boolean innActive) {
        this.innActive = innActive;
    }

    @Nullable
    public UUID getInnkeeperEntityUuid() {
        return innkeeperEntityUuid != null && !innkeeperEntityUuid.isEmpty() ? UUID.fromString(innkeeperEntityUuid) : null;
    }

    public void setInnkeeperEntityUuid(@Nullable UUID uuid) {
        this.innkeeperEntityUuid = uuid != null ? uuid.toString() : null;
    }

    @Nonnull
    public List<String> getInnPoolNpcIds() {
        if (innPoolNpcIds == null) {
            innPoolNpcIds = new ArrayList<>();
        }
        return innPoolNpcIds;
    }

    @Nonnull
    public List<String> getInnLockedEntityUuids() {
        if (innLockedEntityUuids == null) {
            innLockedEntityUuids = new ArrayList<>();
        }
        return innLockedEntityUuids;
    }

    public boolean isInnVisitorLocked(@Nonnull UUID entityUuid) {
        String s = entityUuid.toString();
        for (String x : getInnLockedEntityUuids()) {
            if (s.equalsIgnoreCase(x)) {
                return true;
            }
        }
        return false;
    }

    public void addInnLockedEntity(@Nonnull UUID entityUuid) {
        String s = entityUuid.toString();
        for (String x : getInnLockedEntityUuids()) {
            if (s.equalsIgnoreCase(x)) {
                return;
            }
        }
        getInnLockedEntityUuids().add(s);
    }

    public void removeInnLockedEntity(@Nonnull UUID entityUuid) {
        String s = entityUuid.toString();
        getInnLockedEntityUuids().removeIf(x -> s.equalsIgnoreCase(x));
    }

    @Nullable
    public Instant getInnPoolLastRefreshGameTime() {
        String t = innPoolLastRefreshGameTime;
        if (t == null || t.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(t.trim());
        } catch (Exception e) {
            return null;
        }
    }

    public void setInnPoolLastRefreshGameTime(@Nullable Instant gameTime) {
        this.innPoolLastRefreshGameTime = gameTime != null ? gameTime.toString() : null;
    }

    @Nullable
    public String getInnPoolLastMorningGameDate() {
        String s = innPoolLastMorningGameDate;
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    public void setInnPoolLastMorningGameDate(@Nullable String dateIsoUtc) {
        this.innPoolLastMorningGameDate = dateIsoUtc != null && !dateIsoUtc.isBlank() ? dateIsoUtc.trim() : null;
    }

    @Nullable
    public Long getInnPoolLastMorningEpochDay() {
        return innPoolLastMorningEpochDay;
    }

    public void setInnPoolLastMorningEpochDay(@Nullable Long epochDay) {
        this.innPoolLastMorningEpochDay = epochDay;
    }

    @Nonnull
    public List<PlotInstance> getPlotInstances() {
        if (plotInstances == null) {
            plotInstances = new ArrayList<>();
        }
        return plotInstances;
    }

    public void addPlotInstance(@Nonnull PlotInstance instance) {
        getPlotInstances().add(instance);
    }

    /** Removes a registered plot (e.g. plot sign picked up). @return true if a row was removed */
    public boolean removePlotInstance(@Nonnull UUID plotId) {
        return getPlotInstances().removeIf(p -> p.getPlotId().equals(plotId));
    }

    @Nullable
    public PlotInstance findPlotById(@Nonnull UUID plotId) {
        for (PlotInstance p : getPlotInstances()) {
            if (p.getPlotId().equals(plotId)) {
                return p;
            }
        }
        return null;
    }

    @Nullable
    public PlotFootprintRecord findOverlappingPlot(@Nonnull PlotFootprintRecord candidate) {
        for (PlotInstance p : getPlotInstances()) {
            if (p.intersectsFootprint(candidate)) {
                return p.toFootprint();
            }
        }
        return null;
    }

    public boolean hasQuestActive(@Nonnull String questId) {
        return normalizedQuestSet(activeQuestIds).contains(questId);
    }

    public boolean hasQuestCompleted(@Nonnull String questId) {
        return normalizedQuestSet(completedQuestIds).contains(questId);
    }

    public void addActiveQuest(@Nonnull String questId) {
        String q = questId.trim();
        if (q.isEmpty()) {
            return;
        }
        Set<String> done = normalizedQuestSet(completedQuestIds);
        if (done.contains(q)) {
            return;
        }
        Set<String> active = normalizedQuestSet(activeQuestIds);
        active.add(q);
        activeQuestIds = new ArrayList<>(active);
    }

    public void completeQuest(@Nonnull String questId) {
        String q = questId.trim();
        if (q.isEmpty()) {
            return;
        }
        Set<String> active = normalizedQuestSet(activeQuestIds);
        active.remove(q);
        activeQuestIds = new ArrayList<>(active);
        Set<String> done = normalizedQuestSet(completedQuestIds);
        done.add(q);
        completedQuestIds = new ArrayList<>(done);
    }

    public void clearActiveQuest(@Nonnull String questId) {
        String q = questId.trim();
        if (q.isEmpty()) {
            return;
        }
        Set<String> active = normalizedQuestSet(activeQuestIds);
        active.remove(q);
        activeQuestIds = new ArrayList<>(active);
    }

    @Nonnull
    public List<String> getActiveQuestIdsSnapshot() {
        return List.copyOf(new ArrayList<>(normalizedQuestSet(activeQuestIds)));
    }

    @Nonnull
    public List<String> getCompletedQuestIdsSnapshot() {
        return List.copyOf(new ArrayList<>(normalizedQuestSet(completedQuestIds)));
    }

    @Nonnull
    private static Set<String> normalizedQuestSet(@Nullable List<String> list) {
        Set<String> s = new LinkedHashSet<>();
        if (list != null) {
            for (String x : list) {
                if (x != null && !x.isBlank()) {
                    s.add(x.trim());
                }
            }
        }
        return s;
    }

    /** True if any plot is COMPLETE with this construction id. */
    public boolean hasCompletePlotWithConstruction(@Nonnull String constructionId) {
        String c = constructionId.trim();
        if (c.isEmpty()) {
            return false;
        }
        for (PlotInstance p : getPlotInstances()) {
            if (p.getState() == PlotInstanceState.COMPLETE && c.equals(p.getConstructionId())) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public PlotInstance findCompletePlotWithConstruction(@Nonnull String constructionId) {
        String c = constructionId.trim();
        if (c.isEmpty()) {
            return null;
        }
        for (PlotInstance p : getPlotInstances()) {
            if (p.getState() == PlotInstanceState.COMPLETE && c.equals(p.getConstructionId())) {
                return p;
            }
        }
        return null;
    }
}
