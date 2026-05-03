package com.hexvane.aetherhaven.construction.assembly;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Transient state while holding building staff secondary: locked brush anchor per charge hold, channel timer, freshness.
 * Read by {@link PlotAssemblyPreviewSystem} for growth preview; written by {@link BuildingStaffSecondaryInteraction}.
 */
public final class BuildingStaffAssemblyChannelComponent implements Component<EntityStore> {
    public static final long CHANNEL_DURATION_NS = 500_000_000L;
    /**
     * Without an interaction tick for this long, the brush is cleared. Kept generous so preview does not flicker if
     * interaction cadence is slower than the entity tick.
     */
    public static final long CHANNEL_STALE_NS = 1_000_000_000L;

    @Nonnull
    public static final BuilderCodec<BuildingStaffAssemblyChannelComponent> CODEC =
        BuilderCodec
            .builder(BuildingStaffAssemblyChannelComponent.class, BuildingStaffAssemblyChannelComponent::new)
            .documentation("Transient building-staff assembly channel timing (preview + 0.5s place gate).")
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, BuildingStaffAssemblyChannelComponent> componentType;

    private int channelCellX = Integer.MIN_VALUE;
    private int channelCellY;
    private int channelCellZ;
    private long channelStartNs;
    private long lastChargeTickNs;
    /** World cell locked for this secondary hold; ray aim updates do not retarget until release or invalidation. */
    private int brushLockX = Integer.MIN_VALUE;
    private int brushLockY;
    private int brushLockZ;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType =
            registry.registerComponent(
                BuildingStaffAssemblyChannelComponent.class,
                "AetherhavenBuildingStaffAssemblyChannel",
                BuildingStaffAssemblyChannelComponent.CODEC
            );
    }

    @Nonnull
    public static ComponentType<EntityStore, BuildingStaffAssemblyChannelComponent> getComponentType() {
        ComponentType<EntityStore, BuildingStaffAssemblyChannelComponent> t = componentType;
        if (t == null) {
            throw new IllegalStateException("BuildingStaffAssemblyChannelComponent not registered");
        }
        return t;
    }

    public boolean hasActiveTarget() {
        return channelCellX != Integer.MIN_VALUE;
    }

    public void clearTarget() {
        channelCellX = Integer.MIN_VALUE;
    }

    /** New secondary charge: drop lock and channel anchor. */
    public void resetChargeSession() {
        brushLockX = Integer.MIN_VALUE;
        clearTarget();
    }

    public boolean hasBrushLock() {
        return brushLockX != Integer.MIN_VALUE;
    }

    public void setBrushLock(@Nonnull Vector3i cellWorld) {
        brushLockX = cellWorld.x;
        brushLockY = cellWorld.y;
        brushLockZ = cellWorld.z;
    }

    @Nonnull
    public Vector3i getBrushLockWorld() {
        return new Vector3i(brushLockX, brushLockY, brushLockZ);
    }

    /** After a placement wave while still holding RMB: allow a new ray pick without ending the interaction. */
    public void releaseBrushLockAfterPlacement(long nowNs) {
        brushLockX = Integer.MIN_VALUE;
        clearTarget();
        lastChargeTickNs = nowNs;
    }

    /**
     * @return {@code true} if the fill timer was reset for a new brush center (audio/visual cue hook).
     */
    public boolean beginOrContinueChannel(@Nonnull Vector3i cellWorld, long nowNs) {
        boolean reset =
            !hasActiveTarget()
                || channelCellX != cellWorld.x
                || channelCellY != cellWorld.y
                || channelCellZ != cellWorld.z;
        if (reset) {
            channelCellX = cellWorld.x;
            channelCellY = cellWorld.y;
            channelCellZ = cellWorld.z;
            channelStartNs = nowNs;
        }
        lastChargeTickNs = nowNs;
        return reset;
    }

    public boolean isFresh(long nowNs) {
        return nowNs - lastChargeTickNs <= CHANNEL_STALE_NS;
    }

    /** Brush center in world block coordinates (meaningful only when {@link #hasActiveTarget()}). */
    public int getBrushCenterX() {
        return channelCellX;
    }

    public int getBrushCenterY() {
        return channelCellY;
    }

    public int getBrushCenterZ() {
        return channelCellZ;
    }

    /**
     * Whether {@code (x,y,z)} lies in the brush box around the channel center (Chebyshev radius from
     * {@link AetherhavenConstants#BUILDING_STAFF_ASSEMBLY_BRUSH_CHEBYSHEV_RADIUS}).
     */
    public boolean cellMatchesBrush(int x, int y, int z) {
        if (!hasActiveTarget()) {
            return false;
        }
        int r = AetherhavenConstants.BUILDING_STAFF_ASSEMBLY_BRUSH_CHEBYSHEV_RADIUS;
        int dx = Math.abs(x - channelCellX);
        int dy = Math.abs(y - channelCellY);
        int dz = Math.abs(z - channelCellZ);
        return Math.max(Math.max(dx, dy), dz) <= r;
    }

    /**
     * @return 0..1 linear fill progress for the channel target while fresh; 0 if no target or stale.
     */
    public double channelGrow01(long nowNs) {
        if (!hasActiveTarget() || !isFresh(nowNs)) {
            return 0.0;
        }
        double t = (nowNs - channelStartNs) / (double) CHANNEL_DURATION_NS;
        return Math.min(1.0, Math.max(0.0, t));
    }

    public long getChannelStartNs() {
        return channelStartNs;
    }

    public void resetChannelStart(long nowNs) {
        channelStartNs = nowNs;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        return new BuildingStaffAssemblyChannelComponent();
    }
}
