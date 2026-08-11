package com.hexvane.aetherhaven.poi;

import com.hexvane.aetherhaven.AetherhavenConstants;
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
    private final boolean mountOnUse;
    @Nullable
    private final String equipmentProfileId;
    @Nullable
    private final Double interactionTargetX;
    @Nullable
    private final Double interactionTargetY;
    @Nullable
    private final Double interactionTargetZ;
    @Nullable
    private final Float interactionTargetYawRadians;
    @Nullable
    private final String workResidentKind;

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
        this(id, townId, x, y, z, tags, capacity, plotId, blockTypeId, interactionKind, defaultMountOnUse(interactionKind), null, null, null, null, null, null);
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
        this(
            id,
            townId,
            x,
            y,
            z,
            tags,
            capacity,
            plotId,
            blockTypeId,
            interactionKind,
            defaultMountOnUse(interactionKind),
            null,
            interactionTargetX,
            interactionTargetY,
            interactionTargetZ,
            null,
            null
        );
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
        boolean mountOnUse,
        @Nullable String equipmentProfileId,
        @Nullable Double interactionTargetX,
        @Nullable Double interactionTargetY,
        @Nullable Double interactionTargetZ
    ) {
        this(
            id,
            townId,
            x,
            y,
            z,
            tags,
            capacity,
            plotId,
            blockTypeId,
            interactionKind,
            mountOnUse,
            equipmentProfileId,
            interactionTargetX,
            interactionTargetY,
            interactionTargetZ,
            null,
            null
        );
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
        boolean mountOnUse,
        @Nullable String equipmentProfileId,
        @Nullable Double interactionTargetX,
        @Nullable Double interactionTargetY,
        @Nullable Double interactionTargetZ,
        @Nullable Float interactionTargetYawRadians
    ) {
        this(
            id,
            townId,
            x,
            y,
            z,
            tags,
            capacity,
            plotId,
            blockTypeId,
            interactionKind,
            mountOnUse,
            equipmentProfileId,
            interactionTargetX,
            interactionTargetY,
            interactionTargetZ,
            interactionTargetYawRadians,
            null
        );
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
        boolean mountOnUse,
        @Nullable String equipmentProfileId,
        @Nullable Double interactionTargetX,
        @Nullable Double interactionTargetY,
        @Nullable Double interactionTargetZ,
        @Nullable Float interactionTargetYawRadians,
        @Nullable String workResidentKind
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
        this.mountOnUse = mountOnUse;
        this.equipmentProfileId = equipmentProfileId != null && !equipmentProfileId.isBlank() ? equipmentProfileId.trim() : null;
        this.interactionTargetX = interactionTargetX;
        this.interactionTargetY = interactionTargetY;
        this.interactionTargetZ = interactionTargetZ;
        this.interactionTargetYawRadians = interactionTargetYawRadians;
        this.workResidentKind =
            workResidentKind != null && !workResidentKind.isBlank() ? workResidentKind.trim() : null;
    }

    private static boolean defaultMountOnUse(@Nonnull PoiInteractionKind kind) {
        return kind == PoiInteractionKind.SIT || kind == PoiInteractionKind.SLEEP;
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
        // Tourist stands are single-file: one visitor per spot, including festival tourist stands.
        if (tags.contains(AetherhavenConstants.POI_TAG_TOURIST_VISIT)) {
            return 1;
        }
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

    public boolean isMountOnUse() {
        return mountOnUse;
    }

    @Nullable
    public String getEquipmentProfileId() {
        return equipmentProfileId;
    }

    public boolean hasInteractionTarget() {
        return interactionTargetX != null && interactionTargetY != null && interactionTargetZ != null;
    }

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

    @Nullable
    public Float getInteractionTargetYawRadians() {
        return interactionTargetYawRadians;
    }

    @Nullable
    public String getWorkResidentKind() {
        return workResidentKind;
    }

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
            mountOnUse,
            equipmentProfileId,
            interactionTargetX,
            interactionTargetY,
            interactionTargetZ,
            interactionTargetYawRadians,
            workResidentKind
        );
    }

    @Nonnull
    public PoiEntry copyWithInteractionTarget(@Nullable Double tx, @Nullable Double ty, @Nullable Double tz) {
        return copyWithInteractionTarget(tx, ty, tz, tx == null ? null : interactionTargetYawRadians);
    }

    @Nonnull
    public PoiEntry copyWithInteractionTarget(
        @Nullable Double tx,
        @Nullable Double ty,
        @Nullable Double tz,
        @Nullable Float yawRadians
    ) {
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
            mountOnUse,
            equipmentProfileId,
            tx,
            ty,
            tz,
            yawRadians,
            workResidentKind
        );
    }
}
