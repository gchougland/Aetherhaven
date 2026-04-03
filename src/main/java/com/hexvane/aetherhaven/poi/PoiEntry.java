package com.hexvane.aetherhaven.poi;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PoiEntry {
    private final UUID id;
    private final UUID townId;
    private final int x;
    private final int y;
    private final int z;
    private final Set<String> tags;
    private final int capacity;
    @Nullable
    private final UUID plotId;
    @Nullable
    private final String blockTypeId;
    @Nonnull
    private final PoiInteractionKind interactionKind;
    /**
     * Optional world-space stand position for autonomy leash / Seek (not necessarily the furniture cell). When all
     * three are non-null, villagers path here before USE; when null, leash uses the POI block center.
     */
    @Nullable
    private final Double interactionTargetX;
    @Nullable
    private final Double interactionTargetY;
    @Nullable
    private final Double interactionTargetZ;

    public PoiEntry(
        @Nonnull UUID id,
        @Nonnull UUID townId,
        int x,
        int y,
        int z,
        @Nonnull Set<String> tags,
        int capacity,
        @Nullable UUID plotId,
        @Nullable String blockTypeId,
        @Nonnull PoiInteractionKind interactionKind
    ) {
        this(id, townId, x, y, z, tags, capacity, plotId, blockTypeId, interactionKind, null, null, null);
    }

    public PoiEntry(
        @Nonnull UUID id,
        @Nonnull UUID townId,
        int x,
        int y,
        int z,
        @Nonnull Set<String> tags,
        int capacity,
        @Nullable UUID plotId,
        @Nullable String blockTypeId,
        @Nonnull PoiInteractionKind interactionKind,
        @Nullable Double interactionTargetX,
        @Nullable Double interactionTargetY,
        @Nullable Double interactionTargetZ
    ) {
        this.id = id;
        this.townId = townId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.tags = new HashSet<>(tags);
        this.capacity = capacity;
        this.plotId = plotId;
        this.blockTypeId = blockTypeId;
        this.interactionKind = interactionKind;
        this.interactionTargetX = interactionTargetX;
        this.interactionTargetY = interactionTargetY;
        this.interactionTargetZ = interactionTargetZ;
    }

    @Nonnull
    public UUID getId() {
        return id;
    }

    @Nonnull
    public UUID getTownId() {
        return townId;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    @Nonnull
    public Set<String> getTags() {
        return tags;
    }

    public int getCapacity() {
        return capacity;
    }

    @Nullable
    public UUID getPlotId() {
        return plotId;
    }

    @Nullable
    public String getBlockTypeId() {
        return blockTypeId;
    }

    @Nonnull
    public PoiInteractionKind getInteractionKind() {
        return interactionKind;
    }

    public boolean hasInteractionTarget() {
        return interactionTargetX != null && interactionTargetY != null && interactionTargetZ != null;
    }

    /** Set by the POI tool; only valid when {@link #hasInteractionTarget()} is true. */
    @Nullable
    public Double getInteractionTargetX() {
        return interactionTargetX;
    }

    @Nullable
    public Double getInteractionTargetY() {
        return interactionTargetY;
    }

    @Nullable
    public Double getInteractionTargetZ() {
        return interactionTargetZ;
    }

    /** Same POI id and metadata with an updated world cell (e.g. POI tool move). */
    @Nonnull
    public PoiEntry copyWithPosition(int nx, int ny, int nz) {
        return new PoiEntry(
            id,
            townId,
            nx,
            ny,
            nz,
            new HashSet<>(tags),
            capacity,
            plotId,
            blockTypeId,
            interactionKind,
            interactionTargetX,
            interactionTargetY,
            interactionTargetZ
        );
    }

    @Nonnull
    public PoiEntry copyWithInteractionTarget(@Nullable Double tx, @Nullable Double ty, @Nullable Double tz) {
        return new PoiEntry(
            id,
            townId,
            x,
            y,
            z,
            new HashSet<>(tags),
            capacity,
            plotId,
            blockTypeId,
            interactionKind,
            tx,
            ty,
            tz
        );
    }
}
