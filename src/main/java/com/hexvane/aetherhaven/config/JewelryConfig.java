package com.hexvane.aetherhaven.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Rarity table, post-roll multipliers, and stat bands for jewelry. JSON: top key {@code Jewelry} with child objects.
 */
public final class JewelryConfig {
    public static final BuilderCodec<JewelryConfig> CODEC =
        BuilderCodec.builder(JewelryConfig.class, JewelryConfig::new)
            .append(
                new KeyedCodec<>("Note", Codec.STRING),
                (o, v) -> o.note = v != null ? v : defaultNote(),
                o -> o.note
            )
            .documentation("Overview of the Jewelry section. Safe to delete.")
            .add()
            .append(
                new KeyedCodec<>("RarityWeights", JewelryRarityWeightConfig.CODEC),
                (o, v) -> o.rarityWeights = v != null ? v : new JewelryRarityWeightConfig(),
                o -> o.rarityWeights
            )
            .add()
            .append(
                new KeyedCodec<>("TraitMultipliers", JewelryTraitMultiplierConfig.CODEC),
                (o, v) -> o.traitMultipliers = v != null ? v : new JewelryTraitMultiplierConfig(),
                o -> o.traitMultipliers
            )
            .add()
            .append(
                new KeyedCodec<>("Stat", JewelryStatBlockConfig.CODEC),
                (o, v) -> o.stat = v != null ? v : new JewelryStatBlockConfig(),
                o -> o.stat
            )
            .add()
            .build();

    @Nullable
    private String note;
    @Nonnull
    private JewelryRarityWeightConfig rarityWeights = new JewelryRarityWeightConfig();
    @Nonnull
    private JewelryTraitMultiplierConfig traitMultipliers = new JewelryTraitMultiplierConfig();
    @Nonnull
    private JewelryStatBlockConfig stat = new JewelryStatBlockConfig();

    public JewelryConfig() {}

    @Nonnull
    private static String defaultNote() {
        return
            "Jewelry rolling runs when a stack first gets rolled traits (e.g. from loot or appraisal)."
                + " RarityWeights: relative tier weights. TraitMultipliers: gold band vs neck slot."
                + " Stat: per-entity-stat min/max for common/legendary bounds (see sub-keys)."
                + " (Chest jewelry injection only adds a random jewelry item; values still use this file.)"
                + " (Not related to the built-in /droplist command, which is for simulating drop list assets on demand.)";
    }

    @Nullable
    public String getNote() {
        return note;
    }

    @Nonnull
    public JewelryRarityWeightConfig getRarityWeights() {
        return rarityWeights != null ? rarityWeights : new JewelryRarityWeightConfig();
    }

    @Nonnull
    public JewelryTraitMultiplierConfig getTraitMultipliers() {
        return traitMultipliers != null ? traitMultipliers : new JewelryTraitMultiplierConfig();
    }

    @Nonnull
    public JewelryStatBlockConfig getStat() {
        return stat != null ? stat : new JewelryStatBlockConfig();
    }
}
