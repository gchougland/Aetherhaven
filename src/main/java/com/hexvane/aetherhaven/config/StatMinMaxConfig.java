package com.hexvane.aetherhaven.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

/**
 * A numeric range for one tier band, e.g. {@code Jewelry.Stat.Mana.Common.Min} in
 * {@code config.json}. JSON keys: {@code Min}, {@code Max}.
 */
public final class StatMinMaxConfig {
    public static final BuilderCodec<StatMinMaxConfig> CODEC =
        BuilderCodec.builder(StatMinMaxConfig.class, StatMinMaxConfig::new)
            .append(
                new KeyedCodec<>("Min", Codec.DOUBLE),
                (o, v) -> o.min = v != null ? v : 0.0,
                o -> o.min
            )
            .add()
            .append(
                new KeyedCodec<>("Max", Codec.DOUBLE),
                (o, v) -> o.max = v != null ? v : 0.0,
                o -> o.max
            )
            .add()
            .build();

    private double min;
    private double max;

    public StatMinMaxConfig() {}

    public StatMinMaxConfig(double min, double max) {
        this.min = min;
        this.max = max;
    }

    public double getMin() {
        return min;
    }

    public double getMax() {
        return max;
    }
}
