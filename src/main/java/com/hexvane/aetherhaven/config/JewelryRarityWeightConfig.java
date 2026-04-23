package com.hexvane.aetherhaven.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * When jewelry traits are rolled, a rarity is picked with these relative weights. JSON: {@code Jewelry.RarityWeights.*}
 */
public final class JewelryRarityWeightConfig {
    public static final BuilderCodec<JewelryRarityWeightConfig> CODEC =
        BuilderCodec.builder(JewelryRarityWeightConfig.class, JewelryRarityWeightConfig::new)
            .append(
                new KeyedCodec<>("Note", Codec.STRING),
                (o, v) -> o.note = v != null ? v : defaultNote(),
                o -> o.note
            )
            .documentation("Explains rarities. Safe to delete.")
            .add()
            .append(
                new KeyedCodec<>("Common", Codec.DOUBLE),
                (o, v) -> o.common = v != null ? v : 50.0,
                o -> o.common
            )
            .add()
            .append(
                new KeyedCodec<>("Uncommon", Codec.DOUBLE),
                (o, v) -> o.uncommon = v != null ? v : 30.0,
                o -> o.uncommon
            )
            .add()
            .append(
                new KeyedCodec<>("Rare", Codec.DOUBLE),
                (o, v) -> o.rare = v != null ? v : 12.0,
                o -> o.rare
            )
            .add()
            .append(
                new KeyedCodec<>("Mythic", Codec.DOUBLE),
                (o, v) -> o.mythic = v != null ? v : 4.0,
                o -> o.mythic
            )
            .add()
            .append(
                new KeyedCodec<>("Legendary", Codec.DOUBLE),
                (o, v) -> o.legendary = v != null ? v : 1.0,
                o -> o.legendary
            )
            .documentation("Relative weights, normalized in code. All zeros falls back to a 50/30/15/4/1% table.")
            .add()
            .build();

    @Nullable
    private String note;
    private double common = 50.0;
    private double uncommon = 30.0;
    private double rare = 12.0;
    private double mythic = 4.0;
    private double legendary = 1.0;

    public JewelryRarityWeightConfig() {}

    @Nonnull
    private static String defaultNote() {
        return
            "Five independent non-negative numbers; the server samples rare-by-weight after jewelry base rolls."
                + " Higher number = more of that tier."
                + " Tuning: increase Common/reduce Legendary to flatten drops, or the opposite for more chase items.";
    }

    @Nullable
    public String getNote() {
        return note;
    }

    public double getCommon() {
        return common;
    }

    public double getUncommon() {
        return uncommon;
    }

    public double getRare() {
        return rare;
    }

    public double getMythic() {
        return mythic;
    }

    public double getLegendary() {
        return legendary;
    }
}
