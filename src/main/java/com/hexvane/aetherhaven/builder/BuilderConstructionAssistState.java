package com.hexvane.aetherhaven.builder;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Tracks builder construction assist travel and work at assembling plots. */
public final class BuilderConstructionAssistState implements Component<EntityStore> {
    public static final int PHASE_OFF = 0;
    public static final int PHASE_TRAVEL = 1;
    public static final int PHASE_ASSIST = 2;

    public static final int BUILDER_PASSIVE_BOOST = 4;

    private static final double ASSIST_RANGE_HORIZONTAL_SQ = 5.0 * 5.0;
    private static final double ASSIST_RANGE_VERTICAL = 16.0;

    @Nonnull
    public static final BuilderCodec<BuilderConstructionAssistState> CODEC =
        BuilderCodec.builder(BuilderConstructionAssistState.class, BuilderConstructionAssistState::new)
            .append(new KeyedCodec<>("Phase", Codec.INTEGER), (v, x) -> v.phase = x, v -> v.phase)
            .add()
            .append(new KeyedCodec<>("TargetPlotId", Codec.STRING), (v, x) -> v.targetPlotId = x, v -> v.targetPlotId)
            .add()
            .append(new KeyedCodec<>("TargetX", Codec.DOUBLE), (v, x) -> v.targetX = x, v -> v.targetX)
            .add()
            .append(new KeyedCodec<>("TargetY", Codec.DOUBLE), (v, x) -> v.targetY = x, v -> v.targetY)
            .add()
            .append(new KeyedCodec<>("TargetZ", Codec.DOUBLE), (v, x) -> v.targetZ = x, v -> v.targetZ)
            .add()
            .append(new KeyedCodec<>("LastSwingNs", Codec.LONG), (v, x) -> v.lastSwingNs = x, v -> v.lastSwingNs)
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, BuilderConstructionAssistState> componentType;

    private int phase = PHASE_OFF;
    @Nullable
    private String targetPlotId;
    private double targetX;
    private double targetY;
    private double targetZ;
    private long lastSwingNs;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType =
            registry.registerComponent(
                BuilderConstructionAssistState.class,
                "AetherhavenBuilderConstructionAssist",
                BuilderConstructionAssistState.CODEC
            );
    }

    @Nonnull
    public static ComponentType<EntityStore, BuilderConstructionAssistState> getComponentType() {
        ComponentType<EntityStore, BuilderConstructionAssistState> t = componentType;
        if (t == null) {
            throw new IllegalStateException("BuilderConstructionAssistState not registered");
        }
        return t;
    }

    public int getPhase() {
        return phase;
    }

    public void setPhase(int phase) {
        this.phase = phase;
    }

    public boolean isActive() {
        return phase == PHASE_TRAVEL || phase == PHASE_ASSIST;
    }

    @Nullable
    public UUID getTargetPlotId() {
        if (targetPlotId == null || targetPlotId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(targetPlotId.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setTargetPlot(@Nullable UUID plotId, double x, double y, double z) {
        this.targetPlotId = plotId != null ? plotId.toString() : null;
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
    }

    public void clearTarget() {
        this.targetPlotId = null;
        this.phase = PHASE_OFF;
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

    public long getLastSwingNs() {
        return lastSwingNs;
    }

    public void setLastSwingNs(long lastSwingNs) {
        this.lastSwingNs = lastSwingNs;
    }

    public boolean isWithinAssistRange(double npcX, double npcY, double npcZ) {
        double dx = npcX - targetX;
        double dy = npcY - targetY;
        double dz = npcZ - targetZ;
        return dx * dx + dz * dz <= ASSIST_RANGE_HORIZONTAL_SQ && Math.abs(dy) <= ASSIST_RANGE_VERTICAL;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        BuilderConstructionAssistState c = new BuilderConstructionAssistState();
        c.phase = phase;
        c.targetPlotId = targetPlotId;
        c.targetX = targetX;
        c.targetY = targetY;
        c.targetZ = targetZ;
        c.lastSwingNs = lastSwingNs;
        return c;
    }
}
