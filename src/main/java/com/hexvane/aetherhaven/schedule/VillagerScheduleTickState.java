package com.hexvane.aetherhaven.schedule;

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
            .append(
                new KeyedCodec<>("ScheduleUtilityPickPlotId", Codec.STRING),
                (v, x) -> v.scheduleUtilityPickPlotId = x != null ? x : "",
                v -> v.scheduleUtilityPickPlotId
            )
            .add()
            .append(
                new KeyedCodec<>("ScheduleUtilityPickGameplayConstructionId", Codec.STRING),
                (v, x) -> v.scheduleUtilityPickGameplayConstructionId = x != null ? x : "",
                v -> v.scheduleUtilityPickGameplayConstructionId
            )
            .add()
            .append(
                new KeyedCodec<>("ScheduleUtilityPickSegment", Codec.STRING),
                (v, x) -> v.scheduleUtilityPickSegment = x != null ? x : "",
                v -> v.scheduleUtilityPickSegment
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

    /** Last stable random choice among multiple complete plots for schedule utility segments (inn/park/Gaia…). */
    @Nonnull
    private String scheduleUtilityPickPlotId = "";
    @Nonnull
    private String scheduleUtilityPickGameplayConstructionId = "";
    @Nonnull
    private String scheduleUtilityPickSegment = "";

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

    public void clearScheduleUtilityPick() {
        scheduleUtilityPickPlotId = "";
        scheduleUtilityPickGameplayConstructionId = "";
        scheduleUtilityPickSegment = "";
    }

    public void setScheduleUtilityPick(
        @Nonnull String gameplayConstructionId,
        @Nonnull String normalizedScheduleSegment,
        @Nonnull UUID plotId
    ) {
        scheduleUtilityPickGameplayConstructionId = gameplayConstructionId;
        scheduleUtilityPickSegment = normalizedScheduleSegment;
        scheduleUtilityPickPlotId = plotId.toString();
    }

    @Nonnull
    public String getScheduleUtilityPickPlotId() {
        return scheduleUtilityPickPlotId;
    }

    @Nonnull
    public String getScheduleUtilityPickGameplayConstructionId() {
        return scheduleUtilityPickGameplayConstructionId;
    }

    @Nonnull
    public String getScheduleUtilityPickSegment() {
        return scheduleUtilityPickSegment;
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        VillagerScheduleTickState c = new VillagerScheduleTickState();
        c.lastGameEpochMinute = lastGameEpochMinute;
        c.lastAppliedScheduleSegment = lastAppliedScheduleSegment;
        c.lastUnresolvedDebugLogGameEpochHour = lastUnresolvedDebugLogGameEpochHour;
        c.scheduleUtilityPickPlotId = scheduleUtilityPickPlotId;
        c.scheduleUtilityPickGameplayConstructionId = scheduleUtilityPickGameplayConstructionId;
        c.scheduleUtilityPickSegment = scheduleUtilityPickSegment;
        return c;
    }
}
