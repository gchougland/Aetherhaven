package com.hexvane.aetherhaven.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * After a trait amount is rolled from Stat ranges, these multiply the final value. JSON: {@code Jewelry.TraitMultipliers.*}
 */
public final class JewelryTraitMultiplierConfig {
    public static final BuilderCodec<JewelryTraitMultiplierConfig> CODEC =
        BuilderCodec.builder(JewelryTraitMultiplierConfig.class, JewelryTraitMultiplierConfig::new)
            .append(
                new KeyedCodec<>("Note", Codec.STRING),
                (o, v) -> o.note = v != null ? v : defaultNote(),
                o -> o.note
            )
            .documentation("Explains metal and slot. Safe to delete.")
            .add()
            .append(
                new KeyedCodec<>("GoldMetal", Codec.DOUBLE),
                (o, v) -> o.goldMetal = v != null ? v : 1.2,
                o -> o.goldMetal
            )
            .documentation("Applied to gold (_Gold_) items vs silver. Default 1.2 = 20% higher than silver at same rarity.")
            .add()
            .append(
                new KeyedCodec<>("Necklace", Codec.DOUBLE),
                (o, v) -> o.necklace = v != null ? v : 1.15,
                o -> o.necklace
            )
            .documentation("Slots that use a necklace are multiplied by this after metal (rings use 1.0 here).")
            .add()
            .build();

    @Nullable
    private String note;
    private double goldMetal = 1.2;
    private double necklace = 1.15;

    public JewelryTraitMultiplierConfig() {}

    @Nonnull
    private static String defaultNote() {
        return
            "Both multiply the already-rolled per-trait value (after common/legendary band interpolation is applied)."
                + " Gold and necklace bonuses stack multiplicatively."
                + " Set to 1.0 to disable a dimension.";
    }

    @Nullable
    public String getNote() {
        return note;
    }

    public double getGoldMetal() {
        return goldMetal;
    }

    public double getNecklace() {
        return necklace;
    }
}
