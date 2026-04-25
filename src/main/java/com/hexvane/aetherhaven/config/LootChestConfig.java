package com.hexvane.aetherhaven.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Extra world-chest rolls (jewelry, gold, plot token) at chunk load. JSON path: top-level key {@code LootChest}.
 */
public final class LootChestConfig {
    public static final BuilderCodec<LootChestConfig> CODEC =
        BuilderCodec.builder(LootChestConfig.class, LootChestConfig::new)
            .append(
                new KeyedCodec<>("Note", Codec.STRING),
                (o, v) -> o.note = v != null ? v : defaultNote(),
                o -> o.note
            )
            .documentation("Explains this section. Safe to delete or replace.")
            .add()
            .append(
                new KeyedCodec<>("JewelryChance", Codec.DOUBLE),
                (o, v) -> o.jewelryChance = v != null ? v : 0.2,
                o -> o.jewelryChance
            )
            .documentation("0..1: add one unidentified jewelry item to a free slot (does not edit droplist JSON). Default 0.2.")
            .add()
            .append(
                new KeyedCodec<>("BlockIdSubstrings", Codec.STRING),
                (o, v) -> o.blockIdSubstrings = v != null ? v : "",
                o -> o.blockIdSubstrings
            )
            .documentation(
                "Optional include filter: if non-blank, block type id must contain one substring. If blank, all item containers get bonuses."
            )
            .add()
            .append(
                new KeyedCodec<>("ExcludeBlockIdSubstrings", Codec.STRING),
                (o, v) -> o.excludeBlockIdSubstrings = v != null ? v : "",
                o -> o.excludeBlockIdSubstrings
            )
            .documentation("Comma-separated substrings; if a block type id contains any, skip (e.g. crate you do not want buffed).")
            .add()
            .append(
                new KeyedCodec<>("ApplyInCreative", Codec.BOOLEAN),
                (o, v) -> o.applyInCreative = v != null && v,
                o -> o.applyInCreative
            )
            .documentation("When true, world bonus injection also runs in Creative. Default false (same as vanilla stash).")
            .add()
            .append(
                new KeyedCodec<>("LootrPerPlayerCompatibilityEnabled", Codec.BOOLEAN),
                (o, v) -> o.lootrPerPlayerCompatibilityEnabled = v == null || v,
                o -> o.lootrPerPlayerCompatibilityEnabled
            )
            .documentation("When true, injects Aetherhaven bonus loot into Lootr per-player containers (once per player per chest).")
            .add()
            .append(
                new KeyedCodec<>("Gold", LootChestGoldConfig.CODEC),
                (o, v) -> o.gold = v != null ? v : new LootChestGoldConfig(),
                o -> o.gold
            )
            .add()
            .append(
                new KeyedCodec<>("PlotToken", LootChestPlotTokenConfig.CODEC),
                (o, v) -> o.plotToken = v != null ? v : new LootChestPlotTokenConfig(),
                o -> o.plotToken
            )
            .add()
            .build();

    @Nullable
    private String note;
    private double jewelryChance = 0.2;
    @Nonnull
    private String blockIdSubstrings = "";
    @Nonnull
    private String excludeBlockIdSubstrings = "";
    private boolean applyInCreative = false;
    private boolean lootrPerPlayerCompatibilityEnabled = true;
    @Nonnull
    private LootChestGoldConfig gold = new LootChestGoldConfig();
    @Nonnull
    private LootChestPlotTokenConfig plotToken = new LootChestPlotTokenConfig();

    public LootChestConfig() {}

    @Nonnull
    private static String defaultNote() {
        return
            "World-load injection: leave BlockIdSubstrings empty to target every item-container block. Gold defaults to chance 1.0 so coins almost always show when there is room."
                + " JewelryChance default 0.2 (20% per eligible chest)."
                + " Use ExcludeBlockIdSubstrings to opt out a few block ids. ApplyInCreative: enable for testing in Creative."
                + " (Built-in /droplist only simulates ItemDropList assets, not this injection.)";
    }

    @Nullable
    public String getNote() {
        return note;
    }

    public double getJewelryChance() {
        return jewelryChance;
    }

    @Nonnull
    public String getBlockIdSubstrings() {
        return blockIdSubstrings != null ? blockIdSubstrings : "";
    }

    @Nonnull
    public String getExcludeBlockIdSubstrings() {
        return excludeBlockIdSubstrings != null ? excludeBlockIdSubstrings : "";
    }

    public boolean isApplyInCreative() {
        return applyInCreative;
    }

    public boolean isLootrPerPlayerCompatibilityEnabled() {
        return lootrPerPlayerCompatibilityEnabled;
    }

    @Nonnull
    public LootChestGoldConfig getGold() {
        return gold != null ? gold : new LootChestGoldConfig();
    }

    @Nonnull
    public LootChestPlotTokenConfig getPlotToken() {
        return plotToken != null ? plotToken : new LootChestPlotTokenConfig();
    }
}
