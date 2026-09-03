package com.hexvane.aetherhaven.difficulty;

import javax.annotation.Nonnull;

/** Scales loot chances and gold quantities from difficulty multipliers. */
public final class LootRarityDifficulty {
    private LootRarityDifficulty() {}

    public static double scaleChance(double baseChance, double multiplier) {
        if (baseChance <= 0.0) {
            return 0.0;
        }
        if (Double.isNaN(multiplier) || multiplier <= 0.0) {
            return 0.0;
        }
        return Math.min(1.0, baseChance * multiplier);
    }

    public static int scaleQuantity(int quantity, double multiplier) {
        if (quantity <= 0) {
            return 0;
        }
        if (Double.isNaN(multiplier) || multiplier <= 0.0) {
            return 0;
        }
        if (Math.abs(multiplier - 1.0) < 0.0001) {
            return quantity;
        }
        return Math.max(1, (int) Math.round(quantity * multiplier));
    }

    @Nonnull
    public static TownDifficultySettings currentOrNormal() {
        return DifficultyResolver.effectiveForLoot(null, null);
    }
}
