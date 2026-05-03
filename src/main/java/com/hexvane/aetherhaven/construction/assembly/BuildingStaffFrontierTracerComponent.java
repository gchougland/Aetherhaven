package com.hexvane.aetherhaven.construction.assembly;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Server-driven motion toward an assembly frontier cell; entity has no block collision. */
public final class BuildingStaffFrontierTracerComponent implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<BuildingStaffFrontierTracerComponent> CODEC =
        BuilderCodec
            .builder(BuildingStaffFrontierTracerComponent.class, BuildingStaffFrontierTracerComponent::new)
            .documentation("Transient homing bolt from building staff primary toward a frontier preview cell.")
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, BuildingStaffFrontierTracerComponent> componentType;

    private double targetX;
    private double targetY;
    private double targetZ;
    /** Distance from spawn (after pullback) to target; used for progress and arrival guards. */
    private double pathLength;
    private float speedBlocksPerSec;
    private int ticksAlive;
    @Nullable
    private UUID ownerPlayerUuid;

    public BuildingStaffFrontierTracerComponent() {
        this.speedBlocksPerSec = 9.0F;
        this.pathLength = 1.0;
    }

    public BuildingStaffFrontierTracerComponent(
        double targetX,
        double targetY,
        double targetZ,
        double pathLength,
        float speedBlocksPerSec,
        @Nullable UUID ownerPlayerUuid
    ) {
        this.targetX = targetX;
        this.targetY = targetY;
        this.targetZ = targetZ;
        this.pathLength = Math.max(0.05, pathLength);
        this.speedBlocksPerSec = speedBlocksPerSec;
        this.ownerPlayerUuid = ownerPlayerUuid;
    }

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType =
            registry.registerComponent(
                BuildingStaffFrontierTracerComponent.class,
                "AetherhavenBuildingStaffFrontierTracerMotion",
                BuildingStaffFrontierTracerComponent.CODEC
            );
    }

    @Nonnull
    public static ComponentType<EntityStore, BuildingStaffFrontierTracerComponent> getComponentType() {
        ComponentType<EntityStore, BuildingStaffFrontierTracerComponent> t = componentType;
        if (t == null) {
            throw new IllegalStateException("BuildingStaffFrontierTracerComponent not registered");
        }
        return t;
    }

    public double getTargetX() {
        return targetX;
    }

    public double getTargetY() {
        return targetY;
    }

    public double getTargetZ() {
        return targetZ;
    }

    public float getSpeedBlocksPerSec() {
        return speedBlocksPerSec;
    }

    public double getPathLength() {
        return pathLength;
    }

    public int getTicksAlive() {
        return ticksAlive;
    }

    public void incrementTicksAlive() {
        ticksAlive++;
    }

    @Nullable
    public UUID getOwnerPlayerUuid() {
        return ownerPlayerUuid;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        BuildingStaffFrontierTracerComponent c =
            new BuildingStaffFrontierTracerComponent(targetX, targetY, targetZ, pathLength, speedBlocksPerSec, ownerPlayerUuid);
        c.ticksAlive = ticksAlive;
        return c;
    }
}
