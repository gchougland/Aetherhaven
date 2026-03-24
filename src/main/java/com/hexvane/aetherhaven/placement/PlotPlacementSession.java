package com.hexvane.aetherhaven.placement;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;

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

    @Nonnull
    private final List<Ref<EntityStore>> previewEntityRefs = new ArrayList<>();

    public PlotPlacementSession(@Nonnull World world, @Nonnull Vector3i anchor, int rotationSteps, @Nonnull String constructionId) {
        this.world = world;
        this.anchor = anchor.clone();
        this.rotationSteps = rotationSteps;
        this.constructionId = constructionId;
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
        this.constructionId = constructionId;
    }

    @Nonnull
    public List<Ref<EntityStore>> getPreviewEntityRefs() {
        return previewEntityRefs;
    }

    public void nudge(int dx, int dy, int dz) {
        anchor = anchor.add(dx, dy, dz);
    }
}
