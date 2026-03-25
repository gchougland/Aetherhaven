package com.hexvane.aetherhaven.town;

import com.google.gson.annotations.SerializedName;
import com.hypixel.hytale.logger.HytaleLogger;
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
}
