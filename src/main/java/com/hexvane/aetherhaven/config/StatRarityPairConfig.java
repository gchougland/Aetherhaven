package com.hexvane.aetherhaven.config;

import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Per-stat common vs legendary roll bounds. JSON: {@code Common: { Min, Max }}, {@code Legendary: { Min, Max }}.
 * Rarity in between is interpolated in code.
 */
public final class StatRarityPairConfig {
    public static final BuilderCodec<StatRarityPairConfig> CODEC =
        BuilderCodec.builder(StatRarityPairConfig.class, StatRarityPairConfig::new)
            .append(
                new KeyedCodec<>("Common", StatMinMaxConfig.CODEC),
                (o, v) -> o.common = v != null ? v : new StatMinMaxConfig(),
                o -> o.common
            )
            .add()
            .append(
                new KeyedCodec<>("Legendary", StatMinMaxConfig.CODEC),
                (o, v) -> o.legendary = v != null ? v : new StatMinMaxConfig(),
                o -> o.legendary
            )
            .add()
            .build();

    @Nullable
    private StatMinMaxConfig common;
    @Nullable
    private StatMinMaxConfig legendary;

    public StatRarityPairConfig() {}

    public StatRarityPairConfig(@Nonnull StatMinMaxConfig common, @Nonnull StatMinMaxConfig legendary) {
        this.common = common;
        this.legendary = legendary;
    }

    @Nonnull
    public StatMinMaxConfig getCommon() {
        return common != null ? common : new StatMinMaxConfig();
    }

    @Nonnull
    public StatMinMaxConfig getLegendary() {
        return legendary != null ? legendary : new StatMinMaxConfig();
    }
}
