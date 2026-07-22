package com.hexvane.aetherhaven.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Optional plot blueprint page drops in world loot chest injection. */
public final class LootChestPlotBlueprintConfig {
    public static final BuilderCodec<LootChestPlotBlueprintConfig> CODEC =
        BuilderCodec.builder(LootChestPlotBlueprintConfig.class, LootChestPlotBlueprintConfig::new)
            .append(
                new KeyedCodec<>("Chance", Codec.DOUBLE),
                (o, v) -> o.chance = v != null ? v : 0.30,
                o -> o.chance
            )
            .documentation(
                "0..1: roll once per eligible chest to add one random lockable plot blueprint page (same item as quests)."
                    + " Default 0.30 (30% per eligible chest)."
            )
            .add()
            .append(
                new KeyedCodec<>("Note", Codec.STRING),
                (o, v) -> o.note = v != null ? v : defaultNote(),
                o -> o.note
            )
            .documentation("Safe to delete.")
            .add()
            .build();

    private double chance = 0.30;

    @Nullable
    private String note;

    public LootChestPlotBlueprintConfig() {}

    @Nonnull
    private static String defaultNote() {
        return
            "Pool: constructions with plotTokenLockedByDefault, plus floatingGiftBlueprint entries."
                + " Independent of legacy PlotToken item id rolls.";
    }

    public double getChance() {
        return chance;
    }

    @Nullable
    public String getNote() {
        return note;
    }
}
