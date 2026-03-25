package com.hexvane.aetherhaven.villager;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Villager survival meters on a 0..{@link #MAX} scale ({@link #MAX} = satisfied, 0 = depleted).
 * Field {@code Schema} in saved data marks the 0..{@link #MAX} format; older saves are migrated once.
 */
public final class VillagerNeeds implements Component<EntityStore> {
    /** Satisfied / full meter value for UIs, balance, and commands. */
    public static final float MAX = 100f;

    /** Written with saved data; {@code 0} = legacy or unknown (may still be 0..100 in memory). */
    private static final int STORAGE_SCHEMA_VERSION = 1;

    @Nonnull
    public static final BuilderCodec<VillagerNeeds> CODEC = BuilderCodec.builder(VillagerNeeds.class, VillagerNeeds::new)
        .append(new KeyedCodec<>("Hunger", Codec.FLOAT), (v, x) -> v.hunger = x, v -> v.hunger)
        .add()
        .append(new KeyedCodec<>("Energy", Codec.FLOAT), (v, x) -> v.energy = x, v -> v.energy)
        .add()
        .append(new KeyedCodec<>("Fun", Codec.FLOAT), (v, x) -> v.fun = x, v -> v.fun)
        .add()
        .append(new KeyedCodec<>("LastSimulatedEpochMs", Codec.LONG), (v, x) -> v.lastSimulatedEpochMs = x, v -> v.lastSimulatedEpochMs)
        .add()
        .append(new KeyedCodec<>("Schema", Codec.INTEGER), (v, x) -> v.schema = x, v -> v.schema)
        .add()
        .build();

    @Nullable
    private static volatile ComponentType<EntityStore, VillagerNeeds> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType = registry.registerComponent(VillagerNeeds.class, "AetherhavenVillagerNeeds", VillagerNeeds.CODEC);
    }

    @Nonnull
    public static ComponentType<EntityStore, VillagerNeeds> getComponentType() {
        ComponentType<EntityStore, VillagerNeeds> t = componentType;
        if (t == null) {
            throw new IllegalStateException("VillagerNeeds not registered");
        }
        return t;
    }

    private float hunger = MAX;
    private float energy = MAX;
    private float fun = MAX;
    private long lastSimulatedEpochMs;
    /** 0 = legacy or unset on disk; 1 = 0..{@link #MAX} storage. */
    private int schema;

    public VillagerNeeds() {}

    @Nonnull
    public static VillagerNeeds full() {
        VillagerNeeds v = new VillagerNeeds();
        v.hunger = MAX;
        v.energy = MAX;
        v.fun = MAX;
        v.lastSimulatedEpochMs = 0L;
        v.schema = STORAGE_SCHEMA_VERSION;
        return v;
    }

    public float getHunger() {
        return hunger;
    }

    public float getEnergy() {
        return energy;
    }

    public float getFun() {
        return fun;
    }

    public long getLastSimulatedEpochMs() {
        return lastSimulatedEpochMs;
    }

    public void setHunger(float hunger) {
        this.hunger = clampNeed(hunger);
    }

    public void setEnergy(float energy) {
        this.energy = clampNeed(energy);
    }

    public void setFun(float fun) {
        this.fun = clampNeed(fun);
    }

    public void setLastSimulatedEpochMs(long lastSimulatedEpochMs) {
        this.lastSimulatedEpochMs = lastSimulatedEpochMs;
    }

    /**
     * One-time migration from legacy 0..1 components, then decay toward 0 using {@code decayPerSecond} hunger points
     * per second of game time (energy/fun use slightly lower multipliers).
     */
    public void applyDecay(long nowMs, float decayPerSecond) {
        migrateStorageIfNeeded();
        if (lastSimulatedEpochMs <= 0L) {
            lastSimulatedEpochMs = nowMs;
            return;
        }
        double seconds = (nowMs - lastSimulatedEpochMs) / 1000.0;
        if (seconds <= 0) {
            return;
        }
        seconds = Math.min(seconds, 3600.0);
        lastSimulatedEpochMs = nowMs;
        float step = (float) (seconds * decayPerSecond);
        hunger = clampNeed(hunger - step);
        energy = clampNeed(energy - step * 0.85f);
        fun = clampNeed(fun - step * 0.7f);
    }

    private void migrateStorageIfNeeded() {
        if (schema >= STORAGE_SCHEMA_VERSION) {
            return;
        }
        float mx = Math.max(hunger, Math.max(energy, fun));
        if (mx <= 1.001f) {
            hunger *= MAX;
            energy *= MAX;
            fun *= MAX;
        }
        hunger = clampNeed(hunger);
        energy = clampNeed(energy);
        fun = clampNeed(fun);
        schema = STORAGE_SCHEMA_VERSION;
    }

    private static float clampNeed(float v) {
        return Math.max(0f, Math.min(MAX, v));
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        VillagerNeeds c = new VillagerNeeds();
        c.hunger = hunger;
        c.energy = energy;
        c.fun = fun;
        c.lastSimulatedEpochMs = lastSimulatedEpochMs;
        c.schema = schema;
        return c;
    }
}
