package com.hexvane.aetherhaven.gaiadraught;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nonnull;

/**
 * Per player Gaia's Draught progression stored on {@link com.hexvane.aetherhaven.town.TownRecord}. Held flasks mirror
 * {@link #charges} / {@link #capacity} as stack durability (quantity stays 1); the ammo HUD reads the same values from
 * town via {@link GaiaDraughtAmmoHudSupport}.
 */
public final class GaiaDraughtState {
    /** Base flask uses before any shard upgrades. */
    public static final int DEFAULT_CAPACITY = 1;

    /** Max healing tier index (inclusive). Five catalyst upgrades raise tier from 0 to 5. */
    public static final int MAX_HEAL_TIER = 5;

    /** Max sips after five shard upgrades ({@value #DEFAULT_CAPACITY} + {@value #MAX_UPGRADES_PER_TYPE}). */
    public static final int MAX_FLASK_CAPACITY = 6;

    /** Each upgrade type (shard capacity / catalyst heal tier) may be applied at most this many times. */
    public static final int MAX_UPGRADES_PER_TYPE = 5;

    @SerializedName("unlocked")
    private boolean unlocked;

    @SerializedName("capacity")
    private int capacity = DEFAULT_CAPACITY;

    /** 0 .. {@link #MAX_HEAL_TIER} inclusive. */
    @SerializedName("healTier")
    private int healTier;

    @SerializedName("charges")
    private int charges;

    /** Completed shard upgrades (each +1 capacity), capped at {@link #MAX_UPGRADES_PER_TYPE}. */
    @SerializedName("shardUpgradeCount")
    private int shardUpgradeCount;

    /** Completed catalyst upgrades (each +1 heal tier), capped at {@link #MAX_UPGRADES_PER_TYPE}. */
    @SerializedName("catalystUpgradeCount")
    private int catalystUpgradeCount;

    @SerializedName("legacyGaiaDraughtMigrated")
    private boolean legacyGaiaDraughtMigrated;

    /**
     * {@code 0} = not yet converted from old capacity rules (base 3, +3 per shard). {@code 2} = current rules (base 1,
     * +1 per shard, max {@value #MAX_FLASK_CAPACITY}).
     */
    @SerializedName("capacitySchemaV2")
    private int capacitySchemaV2;

    public boolean isUnlocked() {
        return unlocked;
    }

    public void setUnlocked(boolean unlocked) {
        this.unlocked = unlocked;
    }

    public int getCapacity() {
        return Math.min(MAX_FLASK_CAPACITY, Math.max(DEFAULT_CAPACITY, capacity));
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getHealTier() {
        return Math.min(MAX_HEAL_TIER, Math.max(0, healTier));
    }

    public void setHealTier(int healTier) {
        this.healTier = healTier;
    }

    public int getCharges() {
        return Math.max(0, charges);
    }

    public void setCharges(int charges) {
        this.charges = charges;
    }

    public int getShardUpgradeCount() {
        return Math.max(0, Math.min(MAX_UPGRADES_PER_TYPE, shardUpgradeCount));
    }

    public int getCatalystUpgradeCount() {
        return Math.max(0, Math.min(MAX_UPGRADES_PER_TYPE, catalystUpgradeCount));
    }

    public void clampChargesToCapacity() {
        int cap = getCapacity();
        if (charges > cap) {
            charges = cap;
        }
    }

    /**
     * Migrates pre-v2 saves (base 3, +3 capacity per shard) to current rules (base 1, +1 per shard, max
     * {@value #MAX_FLASK_CAPACITY}), and infers catalyst upgrade counts from heal tier when missing.
     */
    public void ensureLegacyMigrated() {
        if (capacitySchemaV2 < 2) {
            migrateCapacitySchemaV2FromLegacyPlusThree();
            capacitySchemaV2 = 2;
            legacyGaiaDraughtMigrated = true;
        }
        if (catalystUpgradeCount <= 0 && healTier > 0) {
            int cat = Math.min(MAX_UPGRADES_PER_TYPE, healTier);
            this.catalystUpgradeCount = cat;
            healTier = Math.min(MAX_HEAL_TIER, Math.max(0, healTier));
        }
        shardUpgradeCount = Math.max(0, Math.min(MAX_UPGRADES_PER_TYPE, shardUpgradeCount));
        catalystUpgradeCount = Math.max(0, Math.min(MAX_UPGRADES_PER_TYPE, catalystUpgradeCount));
        clampChargesToCapacity();
    }

    /** Old rules: default 3 sips, +3 per shard tier, cap 48. */
    private void migrateCapacitySchemaV2FromLegacyPlusThree() {
        int rawCap = this.capacity;
        final int oldDefault = 3;
        final int oldCapCeiling = 48;
        int inferredFromOld = 0;
        if (rawCap > oldDefault) {
            inferredFromOld = Math.min(
                MAX_UPGRADES_PER_TYPE,
                (Math.min(oldCapCeiling, rawCap) - oldDefault + 2) / 3
            );
        }
        int sc = Math.min(MAX_UPGRADES_PER_TYPE, Math.max(this.shardUpgradeCount, inferredFromOld));
        this.shardUpgradeCount = sc;
        this.capacity = Math.min(MAX_FLASK_CAPACITY, DEFAULT_CAPACITY + sc);
    }

    public boolean canApplyShardUpgrade() {
        ensureLegacyMigrated();
        return unlocked && shardUpgradeCount < MAX_UPGRADES_PER_TYPE && getCapacity() < MAX_FLASK_CAPACITY;
    }

    public boolean canApplyCatalystUpgrade() {
        ensureLegacyMigrated();
        return unlocked && catalystUpgradeCount < MAX_UPGRADES_PER_TYPE && getHealTier() < MAX_HEAL_TIER;
    }

    public boolean tryApplyShardCapacityUpgrade() {
        ensureLegacyMigrated();
        if (!canApplyShardUpgrade()) {
            return false;
        }
        capacity = Math.min(MAX_FLASK_CAPACITY, getCapacity() + 1);
        shardUpgradeCount = getShardUpgradeCount() + 1;
        clampChargesToCapacity();
        return true;
    }

    public boolean tryApplyCatalystHealTierUpgrade() {
        ensureLegacyMigrated();
        if (!canApplyCatalystUpgrade()) {
            return false;
        }
        healTier = Math.min(MAX_HEAL_TIER, getHealTier() + 1);
        catalystUpgradeCount = getCatalystUpgradeCount() + 1;
        return true;
    }

    @Nonnull
    public static GaiaDraughtState createFresh() {
        GaiaDraughtState s = new GaiaDraughtState();
        s.unlocked = false;
        s.capacity = DEFAULT_CAPACITY;
        s.healTier = 0;
        s.charges = 0;
        s.shardUpgradeCount = 0;
        s.catalystUpgradeCount = 0;
        s.legacyGaiaDraughtMigrated = true;
        s.capacitySchemaV2 = 2;
        return s;
    }

    @Nonnull
    public static String instantHealEffectId(int tier) {
        return switch (Math.min(MAX_HEAL_TIER, Math.max(0, tier))) {
            case 0 -> "Potion_Health_Instant_Small";
            case 1 -> "Potion_Health_Instant";
            case 2 -> "Potion_Health_Instant_Large";
            default -> "Potion_Health_Instant_Greater";
        };
    }
}
