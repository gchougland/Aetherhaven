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
            "Legacy plot token item roll (disabled by default). Plot blueprint pages are rolled via LootChest.PlotBlueprint."
                + " Tokens are not injected into world loot chests anymore.";
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
