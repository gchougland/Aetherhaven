package com.hexvane.aetherhaven.jewelry;

import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public enum JewelryRarity {
    COMMON,
    UNCOMMON,
    RARE,
    MYTHIC,
    LEGENDARY;

    public int traitCount() {
        return switch (this) {
            case COMMON -> 1;
            case UNCOMMON -> 2;
            case RARE, MYTHIC, LEGENDARY -> 3;
        };
    }

    /** Weighted by {@link AetherhavenPluginConfig} rarity weights (relative, normalized to 1.0 on the server). */
    @Nonnull
    public static JewelryRarity roll(@Nonnull ThreadLocalRandom rnd) {
        return roll(rnd, JewelryRolling.config());
    }

    @Nonnull
    public static JewelryRarity roll(@Nonnull ThreadLocalRandom rnd, @Nonnull AetherhavenPluginConfig cfg) {
        double c = cfg.getJewelryRarityWeightCommon();
        double u = cfg.getJewelryRarityWeightUncommon();
        double r = cfg.getJewelryRarityWeightRare();
        double m = cfg.getJewelryRarityWeightMythic();
        double l = cfg.getJewelryRarityWeightLegendary();
        double sum = c + u + r + m + l;
        if (sum <= 0.0) {
            return rollTable100(rnd);
        }
        double p = rnd.nextDouble() * sum;
        if (p < c) {
            return COMMON;
        }
        p -= c;
        if (p < u) {
            return UNCOMMON;
        }
        p -= u;
        if (p < r) {
            return RARE;
        }
        p -= r;
        if (p < m) {
            return MYTHIC;
        }
        return LEGENDARY;
    }

    /** Fixed distribution: 50% / 30% / 15% / 4% / 1% (used when all configured weights are zero). */
    @Nonnull
    public static JewelryRarity rollTable100(@Nonnull ThreadLocalRandom rnd) {
        int t = rnd.nextInt(100);
        if (t < 50) {
            return COMMON;
        }
        if (t < 80) {
            return UNCOMMON;
        }
        if (t < 95) {
            return RARE;
        }
        if (t < 99) {
            return MYTHIC;
        }
        return LEGENDARY;
    }

    @Nonnull
    public String wireName() {
        return name();
    }

    /**
     * Hytale item {@code Quality} id (drives default slot / tooltip tier art). Not the same as every {@link
     * #wireName()} value: mythic maps to {@code Epic} which is the closest vanilla tier.
     */
    @Nonnull
    public String itemQualityId() {
        return switch (this) {
            case COMMON -> "Common";
            case UNCOMMON -> "Uncommon";
            case RARE -> "Rare";
            case MYTHIC -> "Epic";
            case LEGENDARY -> "Legendary";
        };
    }

    @Nonnull
    public static JewelryRarity fromWire(@Nullable String s) {
        if (s == null || s.isBlank()) {
            return COMMON;
        }
        try {
            return JewelryRarity.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return COMMON;
        }
    }
}
