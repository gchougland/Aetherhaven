package com.hexvane.aetherhaven.difficulty;

import javax.annotation.Nonnull;

/** Shared scaling for production / restaurant / future building upgrade costs and production unlocks. */
public final class BuildingUpgradeCostScaler {
    private BuildingUpgradeCostScaler() {}

    public static long scaleGold(long baseGold, @Nonnull TownDifficultySettings settings) {
        return scaleGold(baseGold, settings.getBuildingUpgradeGoldMultiplier());
    }

    public static long scaleGold(long baseGold, double multiplier) {
        if (baseGold <= 0L) {
            return 0L;
        }
        if (Double.isNaN(multiplier) || multiplier <= 0.0) {
            return baseGold;
        }
        return Math.max(1L, Math.round(baseGold * multiplier));
    }

    public static int scaleResourceCount(int baseCount, @Nonnull TownDifficultySettings settings) {
        return scaleResourceCount(baseCount, settings.getBuildingUpgradeResourceMultiplier());
    }

    public static int scaleResourceCount(int baseCount, double multiplier) {
        if (baseCount <= 0) {
            return 0;
        }
        if (Double.isNaN(multiplier) || multiplier <= 0.0) {
            return baseCount;
        }
        return Math.max(1, (int) Math.round(baseCount * multiplier));
    }

    public static long scaleProductionUnlockGold(long baseGold, @Nonnull TownDifficultySettings settings) {
        return scaleGold(baseGold, settings.getProductionUnlockGoldMultiplier());
    }

    public static int scaleProductionUnlockResource(int baseCount, @Nonnull TownDifficultySettings settings) {
        return scaleResourceCount(baseCount, settings.getProductionUnlockResourceMultiplier());
    }
}
