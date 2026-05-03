package com.hexvane.aetherhaven.town;

import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
import java.util.Collections;
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

    /**
     * Legacy linear assembly: value was the next sequence index to place (equal to count of blocks already placed).
     * Superseded by {@link #assemblyPlacedIndices} when non-empty.
     */
    @Nullable
    @SerializedName("assemblyBlockIndex")
    private Integer assemblyBlockIndex;

    /** Explicit indices placed during frontier assembly; sorted unique. When null/empty, {@link #assemblyBlockIndex} applies. */
    @Nullable
    @SerializedName("assemblyPlacedIndices")
    private ArrayList<Integer> assemblyPlacedIndices;

    /**
     * Millis of {@link com.hypixel.hytale.server.core.modules.time.TimeResource#getNow()} when assembly pacing began
     * (dilated world clock; scales with time dilation). Not wall-clock epoch.
     */
    @Nullable
    @SerializedName("assemblyStartEpochMs")
    private Long assemblyStartEpochMs;

    /**
     * {@link com.hypixel.hytale.server.core.modules.time.TimeResource#getNow()} millis when passive assembly may place
     * the next block. Cadence is a fixed sim-time interval between passive placements (see assembly slot in
     * {@code PlotAssemblyService}); staff placements do not push this forward.
     * staff placements do not push this forward.
     */
    @Nullable
    @SerializedName("assemblyNextPassiveDueSimMs")
    private Long assemblyNextPassiveDueSimMs;

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

    /**
     * @deprecated Prefer {@link #getAssemblyPlacedBlockCount()}. Historically the next linear index (= placed count).
     */
    @Deprecated
    public int getAssemblyBlockIndex() {
        return getAssemblyPlacedBlockCount();
    }

    @Deprecated
    public void setAssemblyBlockIndex(int index) {
        this.assemblyBlockIndex = index;
        this.assemblyPlacedIndices = null;
    }

    /** Blocks already committed during ASSEMBLING (frontier growth). */
    public int getAssemblyPlacedBlockCount() {
        if (assemblyPlacedIndices != null && !assemblyPlacedIndices.isEmpty()) {
            return assemblyPlacedIndices.size();
        }
        return assemblyBlockIndex != null ? assemblyBlockIndex : 0;
    }

    public void resetAssemblyPlacementProgress() {
        this.assemblyPlacedIndices = new ArrayList<>();
        this.assemblyBlockIndex = null;
    }

    public void fillAssemblyPlacedSet(@Nonnull IntOpenHashSet out, int pendingSize) {
        out.clear();
        if (assemblyPlacedIndices != null && !assemblyPlacedIndices.isEmpty()) {
            for (Integer i : assemblyPlacedIndices) {
                if (i != null && i >= 0 && i < pendingSize) {
                    out.add(i.intValue());
                }
            }
            return;
        }
        int nextLinear = assemblyBlockIndex != null ? assemblyBlockIndex : 0;
        for (int i = 0; i < nextLinear && i < pendingSize; i++) {
            out.add(i);
        }
    }

    public void addAssemblyPlacedIndex(int index) {
        ArrayList<Integer> list = ensureAssemblyPlacedIndicesMutable();
        int pos = Collections.binarySearch(list, index);
        if (pos >= 0) {
            return;
        }
        list.add(-pos - 1, index);
        this.assemblyBlockIndex = null;
    }

    @Nonnull
    private ArrayList<Integer> ensureAssemblyPlacedIndicesMutable() {
        if (assemblyPlacedIndices == null) {
            assemblyPlacedIndices = new ArrayList<>();
        } else if (assemblyPlacedIndices.isEmpty()
            && assemblyBlockIndex != null
            && assemblyBlockIndex > 0) {
            for (int i = 0; i < assemblyBlockIndex; i++) {
                assemblyPlacedIndices.add(i);
            }
            assemblyBlockIndex = null;
        }
        return assemblyPlacedIndices;
    }

    public long getAssemblyStartEpochMs() {
        return assemblyStartEpochMs != null ? assemblyStartEpochMs : 0L;
    }

    public void setAssemblyStartEpochMs(long ms) {
        this.assemblyStartEpochMs = ms;
    }

    public long getAssemblyNextPassiveDueSimMs() {
        return assemblyNextPassiveDueSimMs != null ? assemblyNextPassiveDueSimMs : 0L;
    }

    public void setAssemblyNextPassiveDueSimMs(long ms) {
        this.assemblyNextPassiveDueSimMs = ms;
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
        this.assemblyPlacedIndices = null;
        this.assemblyStartEpochMs = null;
        this.assemblyNextPassiveDueSimMs = null;
        this.assemblyPrefabId = null;
        this.assemblyOwnerUuid = null;
    }
}
