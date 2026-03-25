package com.hexvane.aetherhaven.autonomy;

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

public final class VillagerAutonomyState implements Component<EntityStore> {
    public static final int PHASE_IDLE = 0;
    public static final int PHASE_TRAVEL = 1;
    public static final int PHASE_USE = 2;

    @Nonnull
    public static final BuilderCodec<VillagerAutonomyState> CODEC =
        BuilderCodec.builder(VillagerAutonomyState.class, VillagerAutonomyState::new)
            .append(new KeyedCodec<>("Phase", Codec.INTEGER), (v, x) -> v.phase = x, v -> v.phase)
            .add()
            .append(new KeyedCodec<>("TargetPoiId", Codec.STRING), (v, x) -> v.targetPoiId = x, v -> v.targetPoiId)
            .add()
            .append(new KeyedCodec<>("TargetX", Codec.DOUBLE), (v, x) -> v.targetX = x, v -> v.targetX)
            .add()
            .append(new KeyedCodec<>("TargetY", Codec.DOUBLE), (v, x) -> v.targetY = x, v -> v.targetY)
            .add()
            .append(new KeyedCodec<>("TargetZ", Codec.DOUBLE), (v, x) -> v.targetZ = x, v -> v.targetZ)
            .add()
            .append(new KeyedCodec<>("PhaseEndMs", Codec.LONG), (v, x) -> v.phaseEndEpochMs = x, v -> v.phaseEndEpochMs)
            .add()
            .append(new KeyedCodec<>("NextPickMs", Codec.LONG), (v, x) -> v.nextDecisionEpochMs = x, v -> v.nextDecisionEpochMs)
            .add()
            .append(new KeyedCodec<>("TravelPath", Codec.STRING), (v, x) -> v.travelPath = x != null ? x : "", v -> v.travelPath)
            .add()
            .append(new KeyedCodec<>("TravelPathIdx", Codec.INTEGER), (v, x) -> v.travelPathIndex = x, v -> v.travelPathIndex)
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, VillagerAutonomyState> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType = registry.registerComponent(
            VillagerAutonomyState.class,
            "AetherhavenVillagerAutonomyState",
            VillagerAutonomyState.CODEC
        );
    }

    @Nonnull
    public static ComponentType<EntityStore, VillagerAutonomyState> getComponentType() {
        ComponentType<EntityStore, VillagerAutonomyState> t = componentType;
        if (t == null) {
            throw new IllegalStateException("VillagerAutonomyState not registered");
        }
        return t;
    }

    private int phase = PHASE_IDLE;
    @Nullable
    private String targetPoiId;
    private double targetX;
    private double targetY;
    private double targetZ;
    private long phaseEndEpochMs;
    private long nextDecisionEpochMs;
    @Nonnull
    private String travelPath = "";
    private int travelPathIndex;

    public VillagerAutonomyState() {}

    @Nonnull
    public static VillagerAutonomyState fresh(long nowMs) {
        VillagerAutonomyState s = new VillagerAutonomyState();
        s.nextDecisionEpochMs = nowMs;
        return s;
    }

    public int getPhase() {
        return phase;
    }

    public void setPhase(int phase) {
        this.phase = phase;
    }

    @Nullable
    public UUID getTargetPoiUuid() {
        if (targetPoiId == null || targetPoiId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(targetPoiId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setTargetPoiUuid(@Nullable UUID id) {
        this.targetPoiId = id != null ? id.toString() : null;
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

    public void setTravelTarget(double x, double y, double z, @Nonnull UUID poiId) {
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        this.targetPoiId = poiId.toString();
    }

    public void setTravelPath(@Nonnull String encodedWaypoints) {
        this.travelPath = encodedWaypoints;
        this.travelPathIndex = 0;
    }

    @Nonnull
    public String getTravelPath() {
        return travelPath;
    }

    public int getTravelPathIndex() {
        return travelPathIndex;
    }

    public void setTravelPathIndex(int travelPathIndex) {
        this.travelPathIndex = travelPathIndex;
    }

    public long getPhaseEndEpochMs() {
        return phaseEndEpochMs;
    }

    public void setPhaseEndEpochMs(long phaseEndEpochMs) {
        this.phaseEndEpochMs = phaseEndEpochMs;
    }

    public long getNextDecisionEpochMs() {
        return nextDecisionEpochMs;
    }

    public void setNextDecisionEpochMs(long nextDecisionEpochMs) {
        this.nextDecisionEpochMs = nextDecisionEpochMs;
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        VillagerAutonomyState c = new VillagerAutonomyState();
        c.phase = phase;
        c.targetPoiId = targetPoiId;
        c.targetX = targetX;
        c.targetY = targetY;
        c.targetZ = targetZ;
        c.phaseEndEpochMs = phaseEndEpochMs;
        c.nextDecisionEpochMs = nextDecisionEpochMs;
        c.travelPath = travelPath;
        c.travelPathIndex = travelPathIndex;
        return c;
    }
}
