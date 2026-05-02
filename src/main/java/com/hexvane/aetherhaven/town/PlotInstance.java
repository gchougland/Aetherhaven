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

    /** For residential plots: entity UUID of the villager assigned to this home (house management block). */
    @Nullable
    @SerializedName("homeResidentEntityUuid")
    private String homeResidentEntityUuid;

    /** Next index in the prefab paste sequence while {@link PlotInstanceState#ASSEMBLING}. */
    @Nullable
    @SerializedName("assemblyBlockIndex")
    private Integer assemblyBlockIndex;

    @Nullable
    @SerializedName("assemblyStartEpochMs")
    private Long assemblyStartEpochMs;

    /** Matches {@link com.hypixel.hytale.server.core.prefab.event.PrefabPasteEvent} id for this assembly. */
    @Nullable
    @SerializedName("assemblyPrefabId")
    private Integer assemblyPrefabId;

    /** Player who pressed Build; used for {@link com.hexvane.aetherhaven.construction.ConstructionCompleter#finishBuild} permission. */
    @Nullable
    @SerializedName("assemblyOwnerUuid")
    private String assemblyOwnerUuid;

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

    /** Updates logical sign position and axis-aligned footprint (e.g. building relocation). */
    public void applySignAndFootprint(int signX, int signY, int signZ, @Nonnull PlotFootprintRecord footprint) {
        this.signX = signX;
        this.signY = signY;
        this.signZ = signZ;
        this.minX = footprint.getMinX();
        this.minY = footprint.getMinY();
        this.minZ = footprint.getMinZ();
        this.maxX = footprint.getMaxX();
        this.maxY = footprint.getMaxY();
        this.maxZ = footprint.getMaxZ();
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

    /**
     * When registering a plot from the placement UI, the prefab yaw matches the sign block; needed so
     * {@link #resolvePrefabAnchorWorld(ConstructionDefinition)} can rotate {@code plotAnchorOffset} before the build
     * completes and stores the final anchor.
     */
    public void setPlacementPrefabYaw(@Nonnull Rotation yaw) {
        this.prefabYaw = yaw.name();
    }

    @Nonnull
    public Vector3i resolvePrefabAnchorWorld(@Nonnull ConstructionDefinition def) {
        if (prefabAnchorX != null && prefabAnchorY != null && prefabAnchorZ != null) {
            return new Vector3i(prefabAnchorX, prefabAnchorY, prefabAnchorZ);
        }
        return def.resolvePrefabAnchorWorld(new Vector3i(signX, signY, signZ), resolvePrefabYaw());
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

    /** True when this plot is complete and the block lies inside its inclusive AABB footprint. */
    public boolean containsWorldBlock(int x, int y, int z) {
        if (getState() != PlotInstanceState.COMPLETE) {
            return false;
        }
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    @Nullable
    public UUID getHomeResidentEntityUuid() {
        return homeResidentEntityUuid != null && !homeResidentEntityUuid.isBlank()
            ? UUID.fromString(homeResidentEntityUuid.trim())
            : null;
    }

    public void setHomeResidentEntityUuid(@Nullable UUID uuid) {
        this.homeResidentEntityUuid = uuid != null ? uuid.toString() : null;
    }

    public int getAssemblyBlockIndex() {
        return assemblyBlockIndex != null ? assemblyBlockIndex : 0;
    }

    public void setAssemblyBlockIndex(int index) {
        this.assemblyBlockIndex = index;
    }

    public long getAssemblyStartEpochMs() {
        return assemblyStartEpochMs != null ? assemblyStartEpochMs : 0L;
    }

    public void setAssemblyStartEpochMs(long ms) {
        this.assemblyStartEpochMs = ms;
    }

    public int getAssemblyPrefabId() {
        return assemblyPrefabId != null ? assemblyPrefabId : 0;
    }

    public void setAssemblyPrefabId(int id) {
        this.assemblyPrefabId = id;
    }

    /** Clears persisted assembly fields when leaving ASSEMBLING. */
    @Nullable
    public UUID getAssemblyOwnerUuid() {
        if (assemblyOwnerUuid == null || assemblyOwnerUuid.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(assemblyOwnerUuid.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setAssemblyOwnerUuid(@Nonnull UUID uuid) {
        this.assemblyOwnerUuid = uuid.toString();
    }

    public void clearAssemblyPersistence() {
        this.assemblyBlockIndex = null;
        this.assemblyStartEpochMs = null;
        this.assemblyPrefabId = null;
        this.assemblyOwnerUuid = null;
    }
}
