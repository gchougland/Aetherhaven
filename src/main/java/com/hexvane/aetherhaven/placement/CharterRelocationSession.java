package com.hexvane.aetherhaven.placement;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;

/** Server-side state while the town owner repositions the charter block. */
public final class CharterRelocationSession {
    @Nonnull
    private final World world;

    @Nonnull
    private final UUID townId;

    @Nonnull
    private Vector3i anchor;

    /** 0 = {@link Rotation#None}, 1 = {@link Rotation#Ninety}, … (same as plot placement). */
    private int rotationSteps;

    private boolean birdsEyeSnapshotValid;
    private double birdsEyeSnapshotX;
    private double birdsEyeSnapshotY;
    private double birdsEyeSnapshotZ;

    private double birdsEyePanX;
    private double birdsEyePanZ;

    @Nonnull
    private final List<Ref<EntityStore>> previewEntityRefs = new ArrayList<>();

    public CharterRelocationSession(@Nonnull World world, @Nonnull Vector3i charterAnchor, @Nonnull UUID townId) {
        this.world = world;
        this.townId = townId;
        this.anchor = charterAnchor.clone();
    }

    @Nonnull
    public World getWorld() {
        return world;
    }

    @Nonnull
    public UUID getTownId() {
        return townId;
    }

    @Nonnull
    public Vector3i getAnchor() {
        return anchor.clone();
    }

    public void setAnchor(@Nonnull Vector3i anchor) {
        this.anchor = anchor.clone();
    }

    public void nudge(int dx, int dy, int dz) {
        anchor = anchor.add(dx, dy, dz);
    }

    public int getRotationSteps() {
        return rotationSteps;
    }

    public void rotateClockwise90() {
        rotationSteps = (rotationSteps + 1) % 4;
    }

    /** Horizontal block rotation (NESW) when placing the charter block. */
    @Nonnull
    public Rotation getBlockHorizontalRotation() {
        return switch (rotationSteps) {
            case 1 -> Rotation.Ninety;
            case 2 -> Rotation.OneEighty;
            case 3 -> Rotation.TwoSeventy;
            default -> Rotation.None;
        };
    }

    public void clearBirdsEyeSnapshot() {
        this.birdsEyeSnapshotValid = false;
    }

    public void setBirdsEyeSnapshot(double x, double y, double z) {
        this.birdsEyeSnapshotX = x;
        this.birdsEyeSnapshotY = y;
        this.birdsEyeSnapshotZ = z;
        this.birdsEyeSnapshotValid = true;
    }

    public boolean hasBirdsEyeSnapshot() {
        return birdsEyeSnapshotValid;
    }

    public double getBirdsEyeSnapshotX() {
        return birdsEyeSnapshotX;
    }

    public double getBirdsEyeSnapshotY() {
        return birdsEyeSnapshotY;
    }

    public double getBirdsEyeSnapshotZ() {
        return birdsEyeSnapshotZ;
    }

    public void resetBirdsEyePan() {
        this.birdsEyePanX = 0.0;
        this.birdsEyePanZ = 0.0;
    }

    public void addBirdsEyePan(double dx, double dz) {
        this.birdsEyePanX += dx;
        this.birdsEyePanZ += dz;
    }

    public double getBirdsEyePanX() {
        return birdsEyePanX;
    }

    public double getBirdsEyePanZ() {
        return birdsEyePanZ;
    }

    /** Transient block-entity markers for the charter preview (see {@link PlotPreviewSpawner#rebuildCharterBlockPreview}). */
    @Nonnull
    public List<Ref<EntityStore>> getPreviewEntityRefs() {
        return previewEntityRefs;
    }
}
