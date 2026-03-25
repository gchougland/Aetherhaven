package com.hexvane.aetherhaven.town;

import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** One placed building plot: blueprint sign phase or completed prefab. */
public final class PlotInstance {
    @SerializedName("plotId")
    private String plotId;

    @SerializedName("constructionId")
    private String constructionId;

    @SerializedName("state")
    private String state = PlotInstanceState.BLUEPRINTING.name();

    @SerializedName("minX")
    private int minX;

    @SerializedName("minY")
    private int minY;

    @SerializedName("minZ")
    private int minZ;

    @SerializedName("maxX")
    private int maxX;

    @SerializedName("maxY")
    private int maxY;

    @SerializedName("maxZ")
    private int maxZ;

    @SerializedName("signX")
    private int signX;

    @SerializedName("signY")
    private int signY;

    @SerializedName("signZ")
    private int signZ;

    @SerializedName("lastStateChangeEpochMs")
    private long lastStateChangeEpochMs;

    /** World-space prefab anchor after COMPLETE; when null, derived from sign + plotAnchorOffset. */
    @Nullable
    @SerializedName("prefabAnchorX")
    private Integer prefabAnchorX;

    @Nullable
    @SerializedName("prefabAnchorY")
    private Integer prefabAnchorY;

    @Nullable
    @SerializedName("prefabAnchorZ")
    private Integer prefabAnchorZ;

    @Nullable
    @SerializedName("prefabYaw")
    private String prefabYaw;

    public PlotInstance() {}

    public PlotInstance(
        @Nonnull UUID plotId,
        @Nonnull String constructionId,
        @Nonnull PlotInstanceState state,
        @Nonnull PlotFootprintRecord footprint,
        int signX,
        int signY,
        int signZ,
        long lastStateChangeEpochMs
    ) {
        this.plotId = plotId.toString();
        this.constructionId = constructionId != null ? constructionId : "";
        this.state = state.name();
        this.minX = footprint.getMinX();
        this.minY = footprint.getMinY();
        this.minZ = footprint.getMinZ();
        this.maxX = footprint.getMaxX();
        this.maxY = footprint.getMaxY();
        this.maxZ = footprint.getMaxZ();
        this.signX = signX;
        this.signY = signY;
        this.signZ = signZ;
        this.lastStateChangeEpochMs = lastStateChangeEpochMs;
    }

    @Nonnull
    public UUID getPlotId() {
        return UUID.fromString(plotId);
    }

    public void setPlotId(@Nonnull UUID id) {
        this.plotId = id.toString();
    }

    @Nonnull
    public String getConstructionId() {
        return constructionId != null ? constructionId : "";
    }

    public void setConstructionId(@Nonnull String id) {
        this.constructionId = id;
    }

    @Nonnull
    public PlotInstanceState getState() {
        try {
            return PlotInstanceState.valueOf(state != null ? state : PlotInstanceState.BLUEPRINTING.name());
        } catch (IllegalArgumentException e) {
            return PlotInstanceState.BLUEPRINTING;
        }
    }

    public void setState(@Nonnull PlotInstanceState s) {
        this.state = s.name();
    }

    @Nonnull
    public PlotFootprintRecord toFootprint() {
        return new PlotFootprintRecord(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public int getSignX() {
        return signX;
    }

    public int getSignY() {
        return signY;
    }

    public int getSignZ() {
        return signZ;
    }

    public long getLastStateChangeEpochMs() {
        return lastStateChangeEpochMs;
    }

    public void setLastStateChangeEpochMs(long lastStateChangeEpochMs) {
        this.lastStateChangeEpochMs = lastStateChangeEpochMs;
    }

    public void setPrefabWorldPlacement(int anchorX, int anchorY, int anchorZ, @Nonnull Rotation yaw) {
        this.prefabAnchorX = anchorX;
        this.prefabAnchorY = anchorY;
        this.prefabAnchorZ = anchorZ;
        this.prefabYaw = yaw.name();
    }

    @Nonnull
    public Vector3i resolvePrefabAnchorWorld(@Nonnull ConstructionDefinition def) {
        if (prefabAnchorX != null && prefabAnchorY != null && prefabAnchorZ != null) {
            return new Vector3i(prefabAnchorX, prefabAnchorY, prefabAnchorZ);
        }
        int[] o = def.getPlotAnchorOffset();
        return new Vector3i(signX + o[0], signY + o[1], signZ + o[2]);
    }

    @Nonnull
    public Rotation resolvePrefabYaw() {
        if (prefabYaw == null || prefabYaw.isBlank()) {
            return Rotation.None;
        }
        try {
            return Rotation.valueOf(prefabYaw.trim());
        } catch (IllegalArgumentException e) {
            return Rotation.None;
        }
    }

    public boolean footprintIntersects(@Nonnull PlotFootprintRecord other) {
        return toFootprint().intersects(other);
    }

    /** True if this plot's AABB intersects {@code candidate} (same rule as legacy overlap). */
    public boolean intersectsFootprint(@Nonnull PlotFootprintRecord candidate) {
        return toFootprint().intersects(candidate);
    }
}
