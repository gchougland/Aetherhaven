package com.hexvane.aetherhaven.festival.hallowseve;

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

/** Growing jack o lantern at the maze center. */
public final class HallowsEvePumpkinComponent implements Component<EntityStore> {
    public static final String STATE_IDLE = "IDLE";
    public static final String STATE_GROWING = "GROWING";
    public static final String STATE_READY = "READY";
    public static final String STATE_BURSTING = "BURSTING";

    @Nonnull
    public static final BuilderCodec<HallowsEvePumpkinComponent> CODEC =
        BuilderCodec
            .builder(HallowsEvePumpkinComponent.class, HallowsEvePumpkinComponent::new)
            .append(new KeyedCodec<>("State", Codec.STRING), (o, v) -> o.state = v, o -> o.state)
            .add()
            .append(new KeyedCodec<>("TownId", Codec.STRING), (o, v) -> o.townId = v, o -> o.townId)
            .add()
            .append(new KeyedCodec<>("MinScale", Codec.FLOAT), (o, v) -> o.minScale = v, o -> o.minScale)
            .add()
            .append(new KeyedCodec<>("MaxScale", Codec.FLOAT), (o, v) -> o.maxScale = v, o -> o.maxScale)
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, HallowsEvePumpkinComponent> componentType;

    private String state = STATE_IDLE;
    @Nullable
    private String townId;
    private float minScale = HallowsEveIds.PUMPKIN_MIN_SCALE;
    private float maxScale = HallowsEveIds.PUMPKIN_MAX_SCALE;
    private float appliedModelScale;
    private int burstTicketTarget;
    private int burstCandyTarget;
    private int ticketsThrown;
    private int candyThrown;
    private long burstStartEpochMs;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType =
            registry.registerComponent(HallowsEvePumpkinComponent.class, "AetherhavenHallowsEvePumpkin", CODEC);
    }

    public static boolean isRegistered() {
        return componentType != null;
    }

    @Nonnull
    public static ComponentType<EntityStore, HallowsEvePumpkinComponent> getComponentType() {
        ComponentType<EntityStore, HallowsEvePumpkinComponent> type = componentType;
        if (type == null) {
            throw new IllegalStateException("HallowsEvePumpkinComponent not registered");
        }
        return type;
    }

    @Nonnull
    public String getState() {
        return state;
    }

    public void setState(@Nonnull String state) {
        this.state = state;
    }

    public boolean isIdle() {
        return STATE_IDLE.equals(state);
    }

    public boolean isGrowing() {
        return STATE_GROWING.equals(state);
    }

    public boolean isReady() {
        return STATE_READY.equals(state);
    }

    public boolean isBursting() {
        return STATE_BURSTING.equals(state);
    }

    @Nullable
    public UUID getTownId() {
        if (townId == null || townId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(townId.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setTownId(@Nullable UUID townId) {
        this.townId = townId != null ? townId.toString() : null;
    }

    public float getMinScale() {
        return minScale;
    }

    public void setMinScale(float minScale) {
        this.minScale = minScale;
    }

    public float getMaxScale() {
        return maxScale;
    }

    public void setMaxScale(float maxScale) {
        this.maxScale = maxScale;
    }

    public float getAppliedModelScale() {
        return appliedModelScale > 0.01f ? appliedModelScale : getMinScale();
    }

    public void setAppliedModelScale(float appliedModelScale) {
        this.appliedModelScale = Math.max(0.01f, appliedModelScale);
    }

    public int getBurstTicketTarget() {
        return burstTicketTarget;
    }

    public void setBurstTicketTarget(int burstTicketTarget) {
        this.burstTicketTarget = Math.max(0, burstTicketTarget);
    }

    public int getBurstCandyTarget() {
        return burstCandyTarget;
    }

    public void setBurstCandyTarget(int burstCandyTarget) {
        this.burstCandyTarget = Math.max(0, burstCandyTarget);
    }

    public int getTicketsThrown() {
        return ticketsThrown;
    }

    public void addTicketsThrown(int count) {
        this.ticketsThrown += count;
    }

    public void resetTicketsThrown() {
        this.ticketsThrown = 0;
    }

    public int getCandyThrown() {
        return candyThrown;
    }

    public void addCandyThrown(int count) {
        this.candyThrown += count;
    }

    public void resetCandyThrown() {
        this.candyThrown = 0;
    }

    public long getBurstStartEpochMs() {
        return burstStartEpochMs;
    }

    public void setBurstStartEpochMs(long burstStartEpochMs) {
        this.burstStartEpochMs = burstStartEpochMs;
    }

    public void resetForNextRun() {
        state = STATE_IDLE;
        burstTicketTarget = 0;
        burstCandyTarget = 0;
        ticketsThrown = 0;
        candyThrown = 0;
        burstStartEpochMs = 0L;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        HallowsEvePumpkinComponent copy = new HallowsEvePumpkinComponent();
        copy.state = state;
        copy.townId = townId;
        copy.minScale = minScale;
        copy.maxScale = maxScale;
        return copy;
    }
}
