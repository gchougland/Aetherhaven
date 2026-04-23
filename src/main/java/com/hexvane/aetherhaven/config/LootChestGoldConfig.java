package com.hexvane.aetherhaven.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Gold coin bonus dropped into eligible chests. JSON path: {@code LootChest.Gold.*}
 */
public final class LootChestGoldConfig {
    public static final BuilderCodec<LootChestGoldConfig> CODEC =
        BuilderCodec.builder(LootChestGoldConfig.class, LootChestGoldConfig::new)
            .append(
                new KeyedCodec<>("Chance", Codec.DOUBLE),
                (o, v) -> o.chance = v != null ? v : 1.0,
                o -> o.chance
            )
            .documentation("0..1. Default 1.0: always add gold when the chest is eligible and space allows (independent of jewelry).")
            .add()
            .append(
                new KeyedCodec<>("Min", Codec.INTEGER),
                (o, v) -> o.min = v != null ? v : 5,
                o -> o.min
            )
            .add()
            .append(
                new KeyedCodec<>("Max", Codec.INTEGER),
                (o, v) -> o.max = v != null ? v : 20,
                o -> o.max
            )
            .add()
            .append(
                new KeyedCodec<>("ItemId", Codec.STRING),
                (o, v) -> o.itemId = v != null ? v : "Aetherhaven_Gold_Coin",
                o -> o.itemId
            )
            .documentation("Registered item id; quantity is random between Min and Max (inclusive).")
            .add()
            .append(
                new KeyedCodec<>("Note", Codec.STRING),
                (o, v) -> o.note = v != null ? v : defaultNote(),
                o -> o.note
            )
            .documentation("Human-readable; safe to clear.")
            .add()
            .build();

    private double chance = 1.0;
    private int min = 5;
    private int max = 20;
    @Nonnull
    private String itemId = "Aetherhaven_Gold_Coin";
    @Nullable
    private String note;

    public LootChestGoldConfig() {}

    @Nonnull
    private static String defaultNote() {
        return
            "Chance 1.0 = always (when the chest is eligible and items fit). Under 1.0: random. Quantity uniform [Min, Max]."
                + " Merges into an existing stack when the API allows.";
    }

    public double getChance() {
        return chance;
    }

    public int getMin() {
        return min;
    }

    public int getMax() {
        return max;
    }

    @Nonnull
    public String getItemId() {
        if (itemId == null || itemId.isBlank()) {
            return "Aetherhaven_Gold_Coin";
        }
        return itemId.trim();
    }

    @Nullable
    public String getNote() {
        return note;
    }
}
