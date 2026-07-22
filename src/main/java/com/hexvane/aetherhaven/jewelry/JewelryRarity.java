package com.hexvane.aetherhaven.jewelry;

import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.world.WorldZoneIndex;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public enum JewelryRarity {
    COMMON,
    UNCOMMON,
    RARE,
    MYTHIC,
    LEGENDARY;

    /** Loot-chest zone 4: multiply configured rare weight after zone rules. */
    private static final double ZONE_4_RARE_MULTIPLIER = 1.5;
    /** Loot-chest zone 4: multiply configured mythic (epic) weight. */
    private static final double ZONE_4_MYTHIC_MULTIPLIER = 1.75;
    /** Loot-chest zone 4: multiply configured legendary weight. */
    private static final double ZONE_4_LEGENDARY_MULTIPLIER = 2.0;

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
        return rollWithWeights(
            rnd,
            WorldZoneIndex.UNKNOWN_DEFAULT,
            cfg.getJewelryRarityWeightCommon(),
            cfg.getJewelryRarityWeightUncommon(),
            cfg.getJewelryRarityWeightRare(),
            cfg.getJewelryRarityWeightMythic(),
            cfg.getJewelryRarityWeightLegendary()
        );
    }

    /**
     * Rolled gem jewelry in world chests: caps tiers by adventure zone (1 = common–uncommon, 2–3 = through epic/mythic,
     * 4 = no common, higher rare/epic/legendary weight). Does not apply to fixed glow-ring artifact drops.
     */
    @Nonnull
    public static JewelryRarity rollForAdventureZone(
        @Nonnull ThreadLocalRandom rnd,
        @Nonnull AetherhavenPluginConfig cfg,
        int adventureZoneIndex
    ) {
        double[] weights = {
            cfg.getJewelryRarityWeightCommon(),
            cfg.getJewelryRarityWeightUncommon(),
            cfg.getJewelryRarityWeightRare(),
            cfg.getJewelryRarityWeightMythic(),
            cfg.getJewelryRarityWeightLegendary()
        };
        applyAdventureZoneCapsToWeights(adventureZoneIndex, weights);
        return rollWithWeights(
            rnd,
            adventureZoneIndex,
            weights[0],
            weights[1],
            weights[2],
            weights[3],
            weights[4]
        );
    }

    /** Applies zone loot rules to five weights: common, uncommon, rare, mythic, legendary. */
    public static void applyAdventureZoneCapsToWeights(int adventureZoneIndex, @Nonnull double[] weightsCommonToLegendary) {
        int zone = WorldZoneIndex.clamp(adventureZoneIndex);
        if (weightsCommonToLegendary.length < 5) {
            return;
        }
        if (zone <= 1) {
            weightsCommonToLegendary[2] = 0.0;
            weightsCommonToLegendary[3] = 0.0;
            weightsCommonToLegendary[4] = 0.0;
        } else if (zone <= 3) {
            weightsCommonToLegendary[4] = 0.0;
        } else {
            weightsCommonToLegendary[0] = 0.0;
            weightsCommonToLegendary[2] *= ZONE_4_RARE_MULTIPLIER;
            weightsCommonToLegendary[3] *= ZONE_4_MYTHIC_MULTIPLIER;
            weightsCommonToLegendary[4] *= ZONE_4_LEGENDARY_MULTIPLIER;
        }
    }

    @Nonnull
    private static JewelryRarity rollWithWeights(
        @Nonnull ThreadLocalRandom rnd,
        int adventureZoneIndex,
        double c,
        double u,
        double r,
        double m,
        double l
    ) {
        double sum = c + u + r + m + l;
        if (sum <= 0.0) {
            return rollTable100ForAdventureZone(rnd, adventureZoneIndex);
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

    /** Fixed distribution when configured weights sum to zero (misconfig fallback). */
    @Nonnull
    static JewelryRarity rollTable100ForAdventureZone(@Nonnull ThreadLocalRandom rnd, int adventureZoneIndex) {
        int zone = WorldZoneIndex.clamp(adventureZoneIndex);
        if (zone <= 1) {
            return rnd.nextInt(100) < 62 ? COMMON : UNCOMMON;
        }
        if (zone <= 3) {
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
            return MYTHIC;
        }
        int t = rnd.nextInt(100);
        if (t < 40) {
            return UNCOMMON;
        }
        if (t < 68) {
            return RARE;
        }
        if (t < 88) {
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
