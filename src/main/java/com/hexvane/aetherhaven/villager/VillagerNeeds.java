package com.hexvane.aetherhaven.villager;

import com.hexvane.aetherhaven.restaurant.MealPerk;
import com.hexvane.aetherhaven.restaurant.RestaurantDish;
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
        // Restaurant meal perks. Absent in older saves, which load with no perk.
        .append(new KeyedCodec<>("MealWorkUntil", Codec.LONG), (v, x) -> v.mealPerkUntilMs[MealPerk.WORK.ordinal()] = x, v -> v.mealPerkUntilMs[MealPerk.WORK.ordinal()])
        .add()
        .append(new KeyedCodec<>("MealWorkBonus", Codec.FLOAT), (v, x) -> v.mealPerkBonus[MealPerk.WORK.ordinal()] = x, v -> v.mealPerkBonus[MealPerk.WORK.ordinal()])
        .add()
        .append(new KeyedCodec<>("MealEnergyUntil", Codec.LONG), (v, x) -> v.mealPerkUntilMs[MealPerk.ENERGY.ordinal()] = x, v -> v.mealPerkUntilMs[MealPerk.ENERGY.ordinal()])
        .add()
        .append(new KeyedCodec<>("MealEnergyBonus", Codec.FLOAT), (v, x) -> v.mealPerkBonus[MealPerk.ENERGY.ordinal()] = x, v -> v.mealPerkBonus[MealPerk.ENERGY.ordinal()])
        .add()
        .append(new KeyedCodec<>("MealCheerUntil", Codec.LONG), (v, x) -> v.mealPerkUntilMs[MealPerk.CHEER.ordinal()] = x, v -> v.mealPerkUntilMs[MealPerk.CHEER.ordinal()])
        .add()
        .append(new KeyedCodec<>("MealCheerBonus", Codec.FLOAT), (v, x) -> v.mealPerkBonus[MealPerk.CHEER.ordinal()] = x, v -> v.mealPerkBonus[MealPerk.CHEER.ordinal()])
        .add()
        .build();

    /** No meal perk may outlast one full day on the needs clock; a longer value in a save is treated as none. */
    private static final long MEAL_PERK_MAX_MS = 24L * RestaurantDish.MS_PER_GAME_HOUR;

    @Nullable
    private static volatile ComponentType<EntityStore, VillagerNeeds> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        if (componentType != null) {
            return;
        }
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
    /** Per {@link MealPerk}: when it ends, on the same clock as {@link #lastSimulatedEpochMs}. 0 = none. */
    private final long[] mealPerkUntilMs = new long[MealPerk.values().length];
    /** Per {@link MealPerk}: strength as a fraction (0.1 = 10%). */
    private final float[] mealPerkBonus = new float[MealPerk.values().length];

    public VillagerNeeds() {}

    /**
     * Starts a dish's perk from this villager's own clock. A weaker dish never replaces a stronger perk of the same
     * kind that is still running; an equal or stronger one renews it.
     *
     * @return false when nothing changed (no clock yet, bad values, or a stronger perk is active)
     */
    public boolean grantMealPerk(@Nonnull MealPerk perk, float bonus, long durationMs) {
        if (!(bonus > 0f) || durationMs <= 0L || lastSimulatedEpochMs <= 0L) {
            return false;
        }
        float capped = Math.min(bonus, MealPerk.MAX_BONUS);
        if (mealPerkBonus(perk) > capped) {
            return false;
        }
        int i = perk.ordinal();
        mealPerkBonus[i] = capped;
        mealPerkUntilMs[i] = lastSimulatedEpochMs + Math.min(durationMs, MEAL_PERK_MAX_MS);
        return true;
    }

    /** Time left on a meal perk, 0 when none is running. */
    public long mealPerkRemainingMs(@Nonnull MealPerk perk) {
        long left = mealPerkUntilMs[perk.ordinal()] - lastSimulatedEpochMs;
        return lastSimulatedEpochMs > 0L && left > 0L && left <= MEAL_PERK_MAX_MS ? left : 0L;
    }

    /** Strength of a running meal perk as a fraction, 0 when none is running. */
    public float mealPerkBonus(@Nonnull MealPerk perk) {
        if (mealPerkRemainingMs(perk) <= 0L) {
            return 0f;
        }
        float bonus = mealPerkBonus[perk.ordinal()];
        return bonus > 0f ? Math.min(bonus, MealPerk.MAX_BONUS) : 0f;
    }

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
     * Wall-clock gap with no ticks (e.g. chunk unloaded) does not drain needs; we only resume decay from "now".
     * Loaded NPCs tick often, so legitimate gaps stay tiny.
     */
    private static final long OFFLINE_GAP_SKIP_DECAY_MS = 5L * 60L * 1000L;

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
        long elapsedMs = nowMs - lastSimulatedEpochMs;
        if (elapsedMs <= 0L) {
            return;
        }
        if (elapsedMs > OFFLINE_GAP_SKIP_DECAY_MS) {
            lastSimulatedEpochMs = nowMs;
            return;
        }
        double seconds = elapsedMs / 1000.0;
        seconds = Math.min(seconds, 3600.0);
        lastSimulatedEpochMs = nowMs;
        float step = (float) (seconds * decayPerSecond);
        hunger = clampNeed(hunger - step);
        energy = clampNeed(energy - step * 0.85f * (1f - mealPerkBonus(MealPerk.ENERGY)));
        fun = clampNeed(fun - step * 0.7f * (1f - mealPerkBonus(MealPerk.CHEER)));
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
        System.arraycopy(mealPerkUntilMs, 0, c.mealPerkUntilMs, 0, mealPerkUntilMs.length);
        System.arraycopy(mealPerkBonus, 0, c.mealPerkBonus, 0, mealPerkBonus.length);
        return c;
    }
}
