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

    public PoiEntry(
        @Nonnull UUID id,
        @Nonnull UUID townId,
        int x,
        int y,
        int z,
        @Nonnull Set<String> tags,
        int capacity,
        @Nullable UUID plotId
    ) {
        this.id = id;
        this.townId = townId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.tags = new HashSet<>(tags);
        this.capacity = capacity;
        this.plotId = plotId;
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
}
