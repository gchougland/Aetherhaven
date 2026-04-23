package com.hexvane.aetherhaven.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Optional plot token drop. JSON: {@code LootChest.PlotToken.*}
 */
public final class LootChestPlotTokenConfig {
    public static final BuilderCodec<LootChestPlotTokenConfig> CODEC =
        BuilderCodec.builder(LootChestPlotTokenConfig.class, LootChestPlotTokenConfig::new)
            .append(
                new KeyedCodec<>("Chance", Codec.DOUBLE),
                (o, v) -> o.chance = v != null ? v : 0.0,
                o -> o.chance
            )
            .add()
            .append(
                new KeyedCodec<>("ItemId", Codec.STRING),
                (o, v) -> o.itemId = v != null ? v : "",
                o -> o.itemId
            )
            .add()
            .append(
                new KeyedCodec<>("Note", Codec.STRING),
                (o, v) -> o.note = v != null ? v : defaultNote(),
                o -> o.note
            )
            .documentation("Human-readable; safe to clear. Leave ItemId empty until the item exists in your pack.")
            .add()
            .build();

    private double chance = 0.0;
    @Nonnull
    private String itemId = "";

    @Nullable
    private String note;

    public LootChestPlotTokenConfig() {}

    @Nonnull
    private static String defaultNote() {
        return
            "0..1 chance to add a single token into a free slot. Disabled when Chance is 0 or ItemId is empty."
                + " Configure the real plot token id when the item is added to your data."
                + " (Independent of jewelry and gold bonus rolls.)";
    }

    public double getChance() {
        return chance;
    }

    @Nonnull
    public String getItemId() {
        return itemId == null ? "" : itemId.trim();
    }

    @Nullable
    public String getNote() {
        return note;
    }
}
