package com.hexvane.aetherhaven.town;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class TownRecord {
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

    @SerializedName("plotFootprints")
    private List<PlotFootprintRecord> plotFootprints = new ArrayList<>();

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

    @Nonnull
    public List<PlotFootprintRecord> getPlotFootprints() {
        if (plotFootprints == null) {
            plotFootprints = new ArrayList<>();
        }
        return plotFootprints;
    }

    public void addPlotFootprint(@Nonnull PlotFootprintRecord fp) {
        getPlotFootprints().add(fp);
    }

    @Nullable
    public PlotFootprintRecord findOverlappingPlot(@Nonnull PlotFootprintRecord candidate) {
        for (PlotFootprintRecord fp : getPlotFootprints()) {
            if (fp.intersects(candidate)) {
                return fp;
            }
        }
        return null;
    }
}
