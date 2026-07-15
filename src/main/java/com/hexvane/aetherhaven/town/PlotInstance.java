package com.hexvane.aetherhaven.town;

import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.MaterialRequirement;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

    /**
     * Legacy single home resident. Prefer {@link #homeResidentEntityUuids}; still read for migration when the list is
     * empty.
     */
    @Nullable
    @SerializedName("homeResidentEntityUuid")
    private String homeResidentEntityUuid;

    /** Entity UUIDs of villagers assigned to this home (town records slots); index = slot. */
    @Nullable
    @SerializedName("homeResidentEntityUuids")
    private ArrayList<String> homeResidentEntityUuids;

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

    /**
     * When {@code > 1}, main assembly is split into an {@code N×N×N} grid of prefab-local sections ({@code N} per axis).
     * Persisted for rehydrate; omitted or 1 for normal single-volume growth.
     */
    @Nullable
    @SerializedName("assemblySectionDivisions")
    private Integer assemblySectionDivisions;

    /** Flat index {@code 0..N³-1} (x + y·N + z·N²) of the section currently accepting frontier placements. */
    @Nullable
    @SerializedName("assemblyActiveSectionIndex")
    private Integer assemblyActiveSectionIndex;

    /** Materials deposited at the plot sign while {@link PlotInstanceState#BLUEPRINTING}. */
    @Nullable
    @SerializedName("depositedMaterials")
    private ArrayList<MaterialRequirement> depositedMaterials;

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

    /**
     * Slot 0 resident (legacy convenience). Prefer {@link #getHomeResidentEntityUuids()} /
     * {@link #getHomeResidentAt(int)}.
     */
    @Nullable
    public UUID getHomeResidentEntityUuid() {
        return getHomeResidentAt(0);
    }

    /** Sets slot 0 only; prefer {@link #setHomeResidentAt(int, UUID)}. */
    public void setHomeResidentEntityUuid(@Nullable UUID uuid) {
        setHomeResidentAt(0, uuid);
    }

    /** All assigned home residents in slot order (null/blank slots omitted from the returned list). */
    @Nonnull
    public List<UUID> getHomeResidentEntityUuids() {
        ensureHomeResidentListMigrated();
        if (homeResidentEntityUuids == null || homeResidentEntityUuids.isEmpty()) {
            return List.of();
        }
        List<UUID> out = new ArrayList<>(homeResidentEntityUuids.size());
        for (String raw : homeResidentEntityUuids) {
            UUID u = parseUuidOrNull(raw);
            if (u != null) {
                out.add(u);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** True when {@code entityUuid} is assigned to any home resident slot. */
    public boolean hasHomeResident(@Nonnull UUID entityUuid) {
        ensureHomeResidentListMigrated();
        if (homeResidentEntityUuids == null) {
            return false;
        }
        String want = entityUuid.toString();
        for (String raw : homeResidentEntityUuids) {
            if (raw != null && want.equalsIgnoreCase(raw.trim())) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public UUID getHomeResidentAt(int slot) {
        if (slot < 0) {
            return null;
        }
        ensureHomeResidentListMigrated();
        if (homeResidentEntityUuids == null || slot >= homeResidentEntityUuids.size()) {
            return null;
        }
        return parseUuidOrNull(homeResidentEntityUuids.get(slot));
    }

    /**
     * Sets or clears the resident at {@code slot}. Grows the list with nulls as needed. Syncs legacy
     * {@link #homeResidentEntityUuid} to slot 0.
     */
    public void setHomeResidentAt(int slot, @Nullable UUID uuid) {
        if (slot < 0) {
            return;
        }
        ensureHomeResidentListMigrated();
        if (homeResidentEntityUuids == null) {
            homeResidentEntityUuids = new ArrayList<>();
        }
        while (homeResidentEntityUuids.size() <= slot) {
            homeResidentEntityUuids.add(null);
        }
        homeResidentEntityUuids.set(slot, uuid != null ? uuid.toString() : null);
        trimTrailingEmptyHomeResidentSlots();
        syncLegacyHomeResidentField();
    }

    /** Clears every home resident slot. */
    public void clearHomeResidents() {
        homeResidentEntityUuids = null;
        homeResidentEntityUuid = null;
    }

    /** Removes {@code entityUuid} from any slot on this plot. */
    public void clearHomeResidentUuid(@Nonnull UUID entityUuid) {
        ensureHomeResidentListMigrated();
        if (homeResidentEntityUuids == null) {
            return;
        }
        String want = entityUuid.toString();
        boolean changed = false;
        for (int i = 0; i < homeResidentEntityUuids.size(); i++) {
            String raw = homeResidentEntityUuids.get(i);
            if (raw != null && want.equalsIgnoreCase(raw.trim())) {
                homeResidentEntityUuids.set(i, null);
                changed = true;
            }
        }
        if (changed) {
            trimTrailingEmptyHomeResidentSlots();
            syncLegacyHomeResidentField();
        }
    }

    private void ensureHomeResidentListMigrated() {
        if (homeResidentEntityUuids != null && !homeResidentEntityUuids.isEmpty()) {
            return;
        }
        if (homeResidentEntityUuid != null && !homeResidentEntityUuid.isBlank()) {
            if (homeResidentEntityUuids == null) {
                homeResidentEntityUuids = new ArrayList<>(1);
            }
            homeResidentEntityUuids.clear();
            homeResidentEntityUuids.add(homeResidentEntityUuid.trim());
        }
    }

    private void trimTrailingEmptyHomeResidentSlots() {
        if (homeResidentEntityUuids == null) {
            return;
        }
        while (!homeResidentEntityUuids.isEmpty()) {
            String last = homeResidentEntityUuids.get(homeResidentEntityUuids.size() - 1);
            if (last != null && !last.isBlank()) {
                break;
            }
            homeResidentEntityUuids.remove(homeResidentEntityUuids.size() - 1);
        }
        if (homeResidentEntityUuids.isEmpty()) {
            homeResidentEntityUuids = null;
        }
    }

    private void syncLegacyHomeResidentField() {
        UUID slot0 = null;
        if (homeResidentEntityUuids != null && !homeResidentEntityUuids.isEmpty()) {
            slot0 = parseUuidOrNull(homeResidentEntityUuids.get(0));
        }
        homeResidentEntityUuid = slot0 != null ? slot0.toString() : null;
    }

    @Nullable
    private static UUID parseUuidOrNull(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
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

    /** Clears passive assembly clock fields when a new build starts or assembly is abandoned. */
    public void resetAssemblyPassiveTimers() {
        this.assemblyStartEpochMs = null;
        this.assemblyNextPassiveDueSimMs = null;
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

    @Nullable
    public Integer getAssemblySectionDivisions() {
        return assemblySectionDivisions;
    }

    public void setAssemblySectionDivisions(@Nullable Integer divisionsPerAxis) {
        this.assemblySectionDivisions = divisionsPerAxis;
    }

    public int getAssemblyActiveSectionIndex() {
        return assemblyActiveSectionIndex != null ? assemblyActiveSectionIndex : 0;
    }

    public void setAssemblyActiveSectionIndex(@Nullable Integer flatIndex) {
        this.assemblyActiveSectionIndex = flatIndex;
    }

    /** Clears persisted assembly fields when leaving ASSEMBLING. */
    public void clearAssemblyPersistence() {
        this.assemblyBlockIndex = null;
        this.assemblyPlacedIndices = null;
        this.assemblyStartEpochMs = null;
        this.assemblyNextPassiveDueSimMs = null;
        this.assemblyPrefabId = null;
        this.assemblyOwnerUuid = null;
        this.assemblySectionDivisions = null;
        this.assemblyActiveSectionIndex = null;
    }

    @Nonnull
    public List<MaterialRequirement> getDepositedMaterials() {
        if (depositedMaterials == null || depositedMaterials.isEmpty()) {
            return List.of();
        }
        return Collections.unmodifiableList(depositedMaterials);
    }

    public void setDepositedMaterials(@Nonnull List<MaterialRequirement> materials) {
        if (materials.isEmpty()) {
            this.depositedMaterials = null;
        } else {
            this.depositedMaterials = new ArrayList<>(materials);
        }
    }
}
