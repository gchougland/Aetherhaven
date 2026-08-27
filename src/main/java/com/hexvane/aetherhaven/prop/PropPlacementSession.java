package com.hexvane.aetherhaven.prop;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Server-side state while a player is positioning a prop (preview + UI), see {@link PropPlacementSessions}. */
public final class PropPlacementSession {
    @Nonnull
    private final UUID playerUuid;

    @Nonnull
    private final World world;

    @Nonnull
    private final String propId;

    @Nonnull
    private Vector3i anchor;

    private int rotationSteps;

    private final List<Ref<EntityStore>> previewEntityRefs = new ArrayList<>();

    private boolean gizmoMoveActive;

    public PropPlacementSession(
        @Nonnull UUID playerUuid,
        @Nonnull World world,
        @Nonnull String propId,
        @Nonnull Vector3i anchor,
        int rotationSteps
    ) {
        this.playerUuid = playerUuid;
        this.world = world;
        this.propId = propId.trim();
        this.anchor = new Vector3i(anchor);
        this.rotationSteps = (rotationSteps % 4 + 4) % 4;
    }

    @Nonnull
    public UUID getPlayerUuid() {
        return playerUuid;
    }

    @Nonnull
    public World getWorld() {
        return world;
    }

    @Nonnull
    public String getPropId() {
        return propId;
    }

    @Nonnull
    public Vector3i getAnchor() {
        return new Vector3i(anchor);
    }

    public void setAnchor(@Nonnull Vector3i anchor) {
        this.anchor = new Vector3i(anchor);
    }

    public void nudge(int dx, int dy, int dz) {
        anchor = anchor.add(dx, dy, dz);
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
    public List<Ref<EntityStore>> getPreviewEntityRefs() {
        return previewEntityRefs;
    }

    @Nonnull
    public Rotation getYaw() {
        return switch (rotationSteps) {
            case 1 -> Rotation.Ninety;
            case 2 -> Rotation.OneEighty;
            case 3 -> Rotation.TwoSeventy;
            default -> Rotation.None;
        };
    }

    public boolean isGizmoMoveActive() {
        return gizmoMoveActive;
    }

    public void setGizmoMoveActive(boolean gizmoMoveActive) {
        this.gizmoMoveActive = gizmoMoveActive;
    }
}
