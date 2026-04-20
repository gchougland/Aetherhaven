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
import javax.annotation.Nullable;

/** Server-side state while a player is positioning a plot (preview + UI). */
public final class PlotPlacementSession {
    @Nonnull
    private final World world;

    @Nonnull
    private Vector3i anchor;

    /** 0 = Rotation.None, 1 = Ninety, … */
    private int rotationSteps;

    @Nonnull
    private String constructionId;

    /** When non-null, placement commits relocate this existing plot instead of consuming a token / new plot id. */
    @Nullable
    private final UUID movePlotId;

    @Nonnull
    private final List<Ref<EntityStore>> previewEntityRefs = new ArrayList<>();

    /**
     * World-space point the birds-eye rig looks at when first enabled (footprint center or anchor).
     * Not updated when the preview moves — only captured once per birds-eye session.
     */
    private boolean birdsEyeSnapshotValid;
    private double birdsEyeSnapshotX;
    private double birdsEyeSnapshotY;
    private double birdsEyeSnapshotZ;

    /** Extra pan in world space (blocks), on top of the snapshot. */
    private double birdsEyePanX;
    private double birdsEyePanZ;

    public PlotPlacementSession(@Nonnull World world, @Nonnull Vector3i anchor, int rotationSteps, @Nonnull String constructionId) {
        this(world, anchor, rotationSteps, constructionId, null);
    }

    private PlotPlacementSession(
        @Nonnull World world,
        @Nonnull Vector3i anchor,
        int rotationSteps,
        @Nonnull String constructionId,
        @Nullable UUID movePlotId
    ) {
        this.world = world;
        this.anchor = anchor.clone();
        this.rotationSteps = rotationSteps;
        this.constructionId = constructionId;
        this.movePlotId = movePlotId;
    }

    /**
     * Relocate preview: anchor starts at the plot sign coordinates stored on the {@link com.hexvane.aetherhaven.town.PlotInstance}.
     */
    @Nonnull
    public static PlotPlacementSession forRelocatingPlot(
        @Nonnull World world,
        @Nonnull Vector3i signAnchor,
        int rotationSteps,
        @Nonnull String constructionId,
        @Nonnull UUID plotIdBeingMoved
    ) {
        return new PlotPlacementSession(world, signAnchor, rotationSteps, constructionId, plotIdBeingMoved);
    }

    public static int rotationStepsFromPrefabYaw(@Nonnull Rotation yaw) {
        return switch (yaw) {
            case Ninety -> 1;
            case OneEighty -> 2;
            case TwoSeventy -> 3;
            default -> 0;
        };
    }

    public boolean isMoveMode() {
        return movePlotId != null;
    }

    @Nullable
    public UUID getMovePlotId() {
        return movePlotId;
    }

    @Nonnull
    public World getWorld() {
        return world;
    }

    @Nonnull
    public Vector3i getAnchor() {
        return anchor.clone();
    }

    public void setAnchor(@Nonnull Vector3i anchor) {
        this.anchor = anchor.clone();
    }

    public int getRotationSteps() {
        return rotationSteps;
    }

    public void setRotationSteps(int rotationSteps) {
        this.rotationSteps = (rotationSteps % 4 + 4) % 4;
    }

    public void rotateClockwise90() {
        setRotationSteps(rotationSteps + 1);
    }

    @Nonnull
    public Rotation getPrefabYaw() {
        return switch (rotationSteps) {
            case 1 -> Rotation.Ninety;
            case 2 -> Rotation.OneEighty;
            case 3 -> Rotation.TwoSeventy;
            default -> Rotation.None;
        };
    }

    @Nonnull
    public String getConstructionId() {
        return constructionId;
    }

    public void setConstructionId(@Nonnull String constructionId) {
        if (movePlotId != null) {
            return;
        }
        this.constructionId = constructionId;
    }

    @Nonnull
    public List<Ref<EntityStore>> getPreviewEntityRefs() {
        return previewEntityRefs;
    }

    public void nudge(int dx, int dy, int dz) {
        anchor = anchor.add(dx, dy, dz);
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
}
