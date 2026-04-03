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

/** Throttles schedule evaluation to once per in-game calendar minute. */
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

    public VillagerScheduleTickState() {}

    public long getLastGameEpochMinute() {
        return lastGameEpochMinute;
    }

    public void setLastGameEpochMinute(long lastGameEpochMinute) {
        this.lastGameEpochMinute = lastGameEpochMinute;
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        VillagerScheduleTickState c = new VillagerScheduleTickState();
        c.lastGameEpochMinute = lastGameEpochMinute;
        return c;
    }
}
