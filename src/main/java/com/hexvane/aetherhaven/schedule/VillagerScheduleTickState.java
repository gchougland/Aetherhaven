package com.hexvane.aetherhaven.schedule;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Throttles schedule evaluation to once per in-game calendar minute; throttles debug log spam for stuck resolution. */
public final class VillagerScheduleTickState implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<VillagerScheduleTickState> CODEC =
        BuilderCodec.builder(VillagerScheduleTickState.class, VillagerScheduleTickState::new)
            .append(
                new KeyedCodec<>("LastGameEpochMinute", Codec.LONG),
                (v, x) -> v.lastGameEpochMinute = x,
                v -> v.lastGameEpochMinute
            )
            .add()
            .append(
                new KeyedCodec<>("LastAppliedScheduleSegment", Codec.STRING),
                (v, x) -> v.lastAppliedScheduleSegment = x != null ? x : "",
                v -> v.lastAppliedScheduleSegment
            )
            .add()
            .append(
                new KeyedCodec<>("LastUnresolvedDebugLogGameEpochHour", Codec.LONG),
                (v, x) -> v.lastUnresolvedDebugLogGameEpochHour = x,
                v -> v.lastUnresolvedDebugLogGameEpochHour
            )
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, VillagerScheduleTickState> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType = registry.registerComponent(
            VillagerScheduleTickState.class,
            "AetherhavenVillagerScheduleTickState",
            CODEC
        );
    }

    @Nonnull
    public static ComponentType<EntityStore, VillagerScheduleTickState> getComponentType() {
        ComponentType<EntityStore, VillagerScheduleTickState> t = componentType;
        if (t == null) {
            throw new IllegalStateException("VillagerScheduleTickState not registered");
        }
        return t;
    }

    private long lastGameEpochMinute = Long.MIN_VALUE;
    /** Last schedule segment symbol we successfully applied (see {@link VillagerScheduleResolver}); empty if none yet. */
    @Nonnull
    private String lastAppliedScheduleSegment = "";
    /** Game epoch-hour bucket ({@code epochMinute / 60}) when we last logged an unresolved-plot INFO line; {@code -1} = never. */
    private long lastUnresolvedDebugLogGameEpochHour = -1L;

    public VillagerScheduleTickState() {}

    public long getLastGameEpochMinute() {
        return lastGameEpochMinute;
    }

    public void setLastGameEpochMinute(long lastGameEpochMinute) {
        this.lastGameEpochMinute = lastGameEpochMinute;
    }

    @Nonnull
    public String getLastAppliedScheduleSegment() {
        return lastAppliedScheduleSegment;
    }

    public void setLastAppliedScheduleSegment(@Nonnull String lastAppliedScheduleSegment) {
        this.lastAppliedScheduleSegment = lastAppliedScheduleSegment != null ? lastAppliedScheduleSegment : "";
    }

    public long getLastUnresolvedDebugLogGameEpochHour() {
        return lastUnresolvedDebugLogGameEpochHour;
    }

    public void setLastUnresolvedDebugLogGameEpochHour(long lastUnresolvedDebugLogGameEpochHour) {
        this.lastUnresolvedDebugLogGameEpochHour = lastUnresolvedDebugLogGameEpochHour;
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        VillagerScheduleTickState c = new VillagerScheduleTickState();
        c.lastGameEpochMinute = lastGameEpochMinute;
        c.lastAppliedScheduleSegment = lastAppliedScheduleSegment;
        c.lastUnresolvedDebugLogGameEpochHour = lastUnresolvedDebugLogGameEpochHour;
        return c;
    }
}
