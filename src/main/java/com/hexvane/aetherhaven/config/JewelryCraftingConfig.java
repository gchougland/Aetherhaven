package com.hexvane.aetherhaven.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import javax.annotation.Nonnull;

/**
 * Life-essence input → jewelry rarity for the jewelry crafting bench. Nested under config.json {@code Jewelry.Crafting}.
 */
public final class JewelryCraftingConfig {
    public static final BuilderCodec<JewelryCraftingConfig> CODEC =
        BuilderCodec.builder(JewelryCraftingConfig.class, JewelryCraftingConfig::new)
            .append(
                new KeyedCodec<>("PointsPerRegular", Codec.INTEGER),
                (o, v) -> o.pointsPerRegular = v != null ? v : 1,
                o -> o.pointsPerRegular
            )
            .add()
            .append(
                new KeyedCodec<>("PointsPerConcentrated", Codec.INTEGER),
                (o, v) -> o.pointsPerConcentrated = v != null ? v : 25,
                o -> o.pointsPerConcentrated
            )
            .add()
            .append(
                new KeyedCodec<>("MinTotalPointsCommon", Codec.INTEGER),
                (o, v) -> o.minTotalPointsCommon = v != null ? v : 1,
                o -> o.minTotalPointsCommon
            )
            .add()
            .append(
                new KeyedCodec<>("MinTotalPointsUncommon", Codec.INTEGER),
                (o, v) -> o.minTotalPointsUncommon = v != null ? v : 5,
                o -> o.minTotalPointsUncommon
            )
            .add()
            .append(
                new KeyedCodec<>("MinTotalPointsRare", Codec.INTEGER),
                (o, v) -> o.minTotalPointsRare = v != null ? v : 12,
                o -> o.minTotalPointsRare
            )
            .add()
            .append(
                new KeyedCodec<>("MinTotalPointsMythic", Codec.INTEGER),
                (o, v) -> o.minTotalPointsMythic = v != null ? v : 30,
                o -> o.minTotalPointsMythic
            )
            .add()
            .append(
                new KeyedCodec<>("MinTotalPointsLegendary", Codec.INTEGER),
                (o, v) -> o.minTotalPointsLegendary = v != null ? v : 55,
                o -> o.minTotalPointsLegendary
            )
            .add()
            .append(
                new KeyedCodec<>("MinConcentratedMythic", Codec.INTEGER),
                (o, v) -> o.minConcentratedMythic = v != null ? v : 1,
                o -> o.minConcentratedMythic
            )
            .add()
            .append(
                new KeyedCodec<>("MinConcentratedLegendary", Codec.INTEGER),
                (o, v) -> o.minConcentratedLegendary = v != null ? v : 2,
                o -> o.minConcentratedLegendary
            )
            .add()
            .build();

    private int pointsPerRegular = 1;
    private int pointsPerConcentrated = 25;
    private int minTotalPointsCommon = 1;
    private int minTotalPointsUncommon = 5;
    private int minTotalPointsRare = 12;
    private int minTotalPointsMythic = 30;
    private int minTotalPointsLegendary = 55;
    private int minConcentratedMythic = 1;
    private int minConcentratedLegendary = 2;

    public int getPointsPerRegular() {
        return Math.max(0, pointsPerRegular);
    }

    public int getPointsPerConcentrated() {
        return Math.max(0, pointsPerConcentrated);
    }

    public int getMinTotalPointsCommon() {
        return Math.max(0, minTotalPointsCommon);
    }

    public int getMinTotalPointsUncommon() {
        return Math.max(0, minTotalPointsUncommon);
    }

    public int getMinTotalPointsRare() {
        return Math.max(0, minTotalPointsRare);
    }

    public int getMinTotalPointsMythic() {
        return Math.max(0, minTotalPointsMythic);
    }

    public int getMinTotalPointsLegendary() {
        return Math.max(0, minTotalPointsLegendary);
    }

    public int getMinConcentratedMythic() {
        return Math.max(0, minConcentratedMythic);
    }

    public int getMinConcentratedLegendary() {
        return Math.max(0, minConcentratedLegendary);
    }

    @Nonnull
    public static JewelryCraftingConfig defaults() {
        return new JewelryCraftingConfig();
    }
}
