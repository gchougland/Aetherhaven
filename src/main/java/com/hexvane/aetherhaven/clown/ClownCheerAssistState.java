package com.hexvane.aetherhaven.clown;

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

/** Tracks clown cheer travel and fun-restore sessions during work hours. */
public final class ClownCheerAssistState implements Component<EntityStore> {
    public static final int PHASE_OFF = 0;
    public static final int PHASE_TRAVEL = 1;
    public static final int PHASE_CHEER = 2;

    public static final float CHEER_SECONDS = 20f;
    private static final double CHEER_RANGE_HORIZONTAL_SQ = 2.5 * 2.5;
    private static final double CHEER_RANGE_VERTICAL = 4.0;

    @Nonnull
    public static final BuilderCodec<ClownCheerAssistState> CODEC =
        BuilderCodec.builder(ClownCheerAssistState.class, ClownCheerAssistState::new)
            .append(new KeyedCodec<>("Phase", Codec.INTEGER), (v, x) -> v.phase = x, v -> v.phase)
            .add()
            .append(new KeyedCodec<>("TargetEntityId", Codec.STRING), (v, x) -> v.targetEntityId = x, v -> v.targetEntityId)
            .add()
            .append(new KeyedCodec<>("TargetX", Codec.DOUBLE), (v, x) -> v.targetX = x, v -> v.targetX)
            .add()
            .append(new KeyedCodec<>("TargetY", Codec.DOUBLE), (v, x) -> v.targetY = x, v -> v.targetY)
            .add()
            .append(new KeyedCodec<>("TargetZ", Codec.DOUBLE), (v, x) -> v.targetZ = x, v -> v.targetZ)
            .add()
            .append(new KeyedCodec<>("CheerElapsed", Codec.FLOAT), (v, x) -> v.cheerElapsed = x, v -> v.cheerElapsed)
            .add()
            .append(new KeyedCodec<>("StartFun", Codec.FLOAT), (v, x) -> v.startFun = x, v -> v.startFun)
            .add()
            .append(new KeyedCodec<>("LastEmoteNs", Codec.LONG), (v, x) -> v.lastEmoteNs = x, v -> v.lastEmoteNs)
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, ClownCheerAssistState> componentType;

    private int phase = PHASE_OFF;
    @Nullable
    private String targetEntityId;
    private double targetX;
    private double targetY;
    private double targetZ;
    private float cheerElapsed;
    private float startFun;
    private long lastEmoteNs;
    private transient int ticksSinceTargetRescan;
    private transient int travelStuckTicks;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        if (componentType != null) {
            return;
        }
        componentType =
            registry.registerComponent(
                ClownCheerAssistState.class,
                "AetherhavenClownCheerAssist",
                ClownCheerAssistState.CODEC
            );
    }

    @Nonnull
    public static ComponentType<EntityStore, ClownCheerAssistState> getComponentType() {
        ComponentType<EntityStore, ClownCheerAssistState> t = componentType;
        if (t == null) {
            throw new IllegalStateException("ClownCheerAssistState not registered");
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
        return phase == PHASE_TRAVEL || phase == PHASE_CHEER;
    }

    @Nullable
    public UUID getTargetEntityId() {
        if (targetEntityId == null || targetEntityId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(targetEntityId.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setTarget(@Nullable UUID entityId, double x, double y, double z) {
        UUID prev = getTargetEntityId();
        this.targetEntityId = entityId != null ? entityId.toString() : null;
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        if (entityId == null || !entityId.equals(prev)) {
            cheerElapsed = 0f;
            startFun = 0f;
            ticksSinceTargetRescan = 0;
            travelStuckTicks = 0;
            lastEmoteNs = 0L;
        }
    }

    public void clearTarget() {
        this.targetEntityId = null;
        this.phase = PHASE_OFF;
        cheerElapsed = 0f;
        startFun = 0f;
        ticksSinceTargetRescan = 0;
        travelStuckTicks = 0;
        lastEmoteNs = 0L;
    }

    public float getCheerElapsed() {
        return cheerElapsed;
    }

    public void setCheerElapsed(float cheerElapsed) {
        this.cheerElapsed = cheerElapsed;
    }

    public void addCheerElapsed(float dt) {
        cheerElapsed += dt;
    }

    public float getStartFun() {
        return startFun;
    }

    public void setStartFun(float startFun) {
        this.startFun = startFun;
    }

    public long getLastEmoteNs() {
        return lastEmoteNs;
    }

    public void setLastEmoteNs(long lastEmoteNs) {
        this.lastEmoteNs = lastEmoteNs;
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

    public int getTravelStuckTicks() {
        return travelStuckTicks;
    }

    public void incrementTravelStuckTicks() {
        travelStuckTicks++;
    }

    public void resetTravelStuckTicks() {
        travelStuckTicks = 0;
    }

    public int getTicksSinceTargetRescan() {
        return ticksSinceTargetRescan;
    }

    public void incrementTicksSinceTargetRescan() {
        ticksSinceTargetRescan++;
    }

    public void resetTicksSinceTargetRescan() {
        ticksSinceTargetRescan = 0;
    }

    public boolean isWithinCheerRange(double npcX, double npcY, double npcZ) {
        double dx = npcX - targetX;
        double dy = npcY - targetY;
        double dz = npcZ - targetZ;
        return dx * dx + dz * dz <= CHEER_RANGE_HORIZONTAL_SQ && Math.abs(dy) <= CHEER_RANGE_VERTICAL;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        ClownCheerAssistState c = new ClownCheerAssistState();
        c.phase = phase;
        c.targetEntityId = targetEntityId;
        c.targetX = targetX;
        c.targetY = targetY;
        c.targetZ = targetZ;
        c.cheerElapsed = cheerElapsed;
        c.startFun = startFun;
        c.lastEmoteNs = lastEmoteNs;
        return c;
    }
}
