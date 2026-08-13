package com.hexvane.aetherhaven.prop;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** A placed prop instance in the world: which {@link PropDefinition} and where. */
public final class PropInstance {
    @Nonnull
    private final UUID instanceId;

    @Nonnull
    private final String propId;

    private final int anchorX;
    private final int anchorY;
    private final int anchorZ;

    /** Placement yaw, one of {@link Rotation#None}, {@link Rotation#Ninety}, {@link Rotation#OneEighty}, {@link Rotation#TwoSeventy}. */
    @Nonnull
    private final Rotation yaw;

    /** World entity UUIDs spawned with this prop (decorative items, models). Empty for older saves. */
    @Nonnull
    private final List<UUID> linkedEntityIds;

    /**
     * Trigger volume manager ids registered when this prop was pasted. Trigger volumes are not entities after paste, so
     * they cannot carry {@link AetherhavenPlacedInstance}; empty for older saves.
     */
    @Nonnull
    private final List<String> linkedTriggerVolumeIds;

    public PropInstance(
        @Nonnull UUID instanceId,
        @Nonnull String propId,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw
    ) {
        this(instanceId, propId, anchor.x, anchor.y, anchor.z, yaw, List.of(), List.of());
    }

    public PropInstance(
        @Nonnull UUID instanceId,
        @Nonnull String propId,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw,
        @Nonnull List<UUID> linkedEntityIds
    ) {
        this(instanceId, propId, anchor.x, anchor.y, anchor.z, yaw, linkedEntityIds, List.of());
    }

    public PropInstance(
        @Nonnull UUID instanceId,
        @Nonnull String propId,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw,
        @Nonnull List<UUID> linkedEntityIds,
        @Nonnull List<String> linkedTriggerVolumeIds
    ) {
        this(instanceId, propId, anchor.x, anchor.y, anchor.z, yaw, linkedEntityIds, linkedTriggerVolumeIds);
    }

    public PropInstance(
        @Nonnull UUID instanceId,
        @Nonnull String propId,
        int anchorX,
        int anchorY,
        int anchorZ,
        @Nonnull Rotation yaw
    ) {
        this(instanceId, propId, anchorX, anchorY, anchorZ, yaw, List.of(), List.of());
    }

    public PropInstance(
        @Nonnull UUID instanceId,
        @Nonnull String propId,
        int anchorX,
        int anchorY,
        int anchorZ,
        @Nonnull Rotation yaw,
        @Nullable List<UUID> linkedEntityIds
    ) {
        this(instanceId, propId, anchorX, anchorY, anchorZ, yaw, linkedEntityIds, List.of());
    }

    public PropInstance(
        @Nonnull UUID instanceId,
        @Nonnull String propId,
        int anchorX,
        int anchorY,
        int anchorZ,
        @Nonnull Rotation yaw,
        @Nullable List<UUID> linkedEntityIds,
        @Nullable List<String> linkedTriggerVolumeIds
    ) {
        this.instanceId = instanceId;
        this.propId = propId.trim();
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorZ = anchorZ;
        this.yaw = yaw;
        this.linkedEntityIds =
            linkedEntityIds == null || linkedEntityIds.isEmpty()
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(linkedEntityIds));
        this.linkedTriggerVolumeIds =
            linkedTriggerVolumeIds == null || linkedTriggerVolumeIds.isEmpty()
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(linkedTriggerVolumeIds));
    }

    @Nonnull
    public UUID getInstanceId() {
        return instanceId;
    }

    @Nonnull
    public String getPropId() {
        return propId;
    }

    public int getAnchorX() {
        return anchorX;
    }

    public int getAnchorY() {
        return anchorY;
    }

    public int getAnchorZ() {
        return anchorZ;
    }

    @Nonnull
    public Vector3i getAnchor() {
        return new Vector3i(anchorX, anchorY, anchorZ);
    }

    @Nonnull
    public Rotation getYaw() {
        return yaw;
    }

    @Nonnull
    public List<UUID> getLinkedEntityIds() {
        return linkedEntityIds;
    }

    @Nonnull
    public List<String> getLinkedTriggerVolumeIds() {
        return linkedTriggerVolumeIds;
    }
}
