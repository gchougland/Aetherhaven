package com.hexvane.aetherhaven.guild;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Keeps guild hall display adventurers standing at a fixed spot and facing. */
public final class GuildHallDisplayAnchor implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<GuildHallDisplayAnchor> CODEC =
        BuilderCodec.builder(GuildHallDisplayAnchor.class, GuildHallDisplayAnchor::new)
            .append(new KeyedCodec<>("X", Codec.DOUBLE), (c, v) -> c.x = v, c -> c.x)
            .add()
            .append(new KeyedCodec<>("Y", Codec.DOUBLE), (c, v) -> c.y = v, c -> c.y)
            .add()
            .append(new KeyedCodec<>("Z", Codec.DOUBLE), (c, v) -> c.z = v, c -> c.z)
            .add()
            .append(new KeyedCodec<>("YawRadians", Codec.FLOAT), (c, v) -> c.yawRadians = v, c -> c.yawRadians)
            .add()
            .append(new KeyedCodec<>("MarkerX", Codec.DOUBLE), (c, v) -> c.markerX = v, c -> c.markerX)
            .add()
            .append(new KeyedCodec<>("MarkerY", Codec.DOUBLE), (c, v) -> c.markerY = v, c -> c.markerY)
            .add()
            .append(new KeyedCodec<>("MarkerZ", Codec.DOUBLE), (c, v) -> c.markerZ = v, c -> c.markerZ)
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, GuildHallDisplayAnchor> componentType;

    private double x;
    private double y;
    private double z;
    private float yawRadians;
    /** Stand-cell center used to find a chair below (may differ from {@link #getPosition()} when feet are on the seat). */
    private double markerX = Double.NaN;
    private double markerY = Double.NaN;
    private double markerZ = Double.NaN;
    /** Not serialized; mount attempts this session (chunk/mount can lag after spawn). */
    private transient int chairMountAttempts;
    /** Not serialized; sit fallback applied when block mount never succeeds. */
    private transient boolean sitFallbackApplied;
    /** Not serialized; avoid repeating stand-still setState (resets animations). */
    private transient boolean displayStateApplied;
    /** Not serialized; feet aligned over chair block before first mount attempt. */
    private transient boolean chairAlignedForMount;
    /** Not serialized; Sit status animation already started for this display session. */
    private transient boolean sitAnimationApplied;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType = registry.registerComponent(GuildHallDisplayAnchor.class, "AetherhavenGuildHallDisplayAnchor", CODEC);
    }

    @Nonnull
    public static ComponentType<EntityStore, GuildHallDisplayAnchor> getComponentType() {
        ComponentType<EntityStore, GuildHallDisplayAnchor> t = componentType;
        if (t == null) {
            throw new IllegalStateException("GuildHallDisplayAnchor not registered");
        }
        return t;
    }

    public GuildHallDisplayAnchor() {}

    public GuildHallDisplayAnchor(@Nonnull Vector3d position, float yawRadians) {
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
        this.yawRadians = yawRadians;
    }

    public void setSpawnMarkerPosition(@Nonnull Vector3d spawnMarker) {
        this.markerX = spawnMarker.x;
        this.markerY = spawnMarker.y;
        this.markerZ = spawnMarker.z;
    }

    /** Adventurer spot stand cell (for chair lookup); defaults to {@link #getPosition()} when unset. */
    @Nonnull
    public Vector3d getSpawnMarkerPosition() {
        if (Double.isNaN(markerX)) {
            return getPosition();
        }
        return new Vector3d(markerX, markerY, markerZ);
    }

    @Nonnull
    public Vector3d getPosition() {
        return new Vector3d(x, y, z);
    }

    public float getYawRadians() {
        return yawRadians;
    }

    public int getChairMountAttempts() {
        return chairMountAttempts;
    }

    public void incrementChairMountAttempts() {
        chairMountAttempts++;
    }

    public static final int MAX_CHAIR_MOUNT_ATTEMPTS = 30;

    /** True after a successful mount, sit fallback, or max failed attempts. */
    public boolean isChairMountFinished() {
        return chairMountAttempts >= MAX_CHAIR_MOUNT_ATTEMPTS || sitFallbackApplied;
    }

    public void markChairMountFinished() {
        chairMountAttempts = MAX_CHAIR_MOUNT_ATTEMPTS;
    }

    /** Re-opens chair mount when a seat was missed while the plot chunk was still loading. */
    public void resetChairMountForRetry() {
        chairMountAttempts = 0;
        chairAlignedForMount = false;
        sitFallbackApplied = false;
        sitAnimationApplied = false;
    }

    public boolean isSitFallbackApplied() {
        return sitFallbackApplied;
    }

    public void setSitFallbackApplied(boolean sitFallbackApplied) {
        this.sitFallbackApplied = sitFallbackApplied;
    }

    public boolean isDisplayStateApplied() {
        return displayStateApplied;
    }

    public void setDisplayStateApplied(boolean displayStateApplied) {
        this.displayStateApplied = displayStateApplied;
    }

    public boolean isChairAlignedForMount() {
        return chairAlignedForMount;
    }

    public void setChairAlignedForMount(boolean chairAlignedForMount) {
        this.chairAlignedForMount = chairAlignedForMount;
    }

    public boolean isSitAnimationApplied() {
        return sitAnimationApplied;
    }

    public void setSitAnimationApplied(boolean sitAnimationApplied) {
        this.sitAnimationApplied = sitAnimationApplied;
    }

    public void updatePosition(@Nonnull Vector3d position) {
        this.x = position.x;
        this.y = position.y;
        this.z = position.z;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        GuildHallDisplayAnchor copy = new GuildHallDisplayAnchor(getPosition(), yawRadians);
        copy.chairMountAttempts = chairMountAttempts;
        copy.sitFallbackApplied = sitFallbackApplied;
        copy.displayStateApplied = displayStateApplied;
        copy.chairAlignedForMount = chairAlignedForMount;
        copy.sitAnimationApplied = sitAnimationApplied;
        copy.markerX = markerX;
        copy.markerY = markerY;
        copy.markerZ = markerZ;
        return copy;
    }
}
