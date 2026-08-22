package com.hexvane.aetherhaven.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Optional block palette drops in world loot chest injection. */
public final class LootChestBlockPaletteConfig {
    public static final BuilderCodec<LootChestBlockPaletteConfig> CODEC =
        BuilderCodec.builder(LootChestBlockPaletteConfig.class, LootChestBlockPaletteConfig::new)
            .append(
                new KeyedCodec<>("Chance", Codec.DOUBLE),
                (o, v) -> o.chance = v != null ? v : 0.30,
                o -> o.chance
            )
            .documentation("0..1: roll once per eligible chest to add one random block palette. Default 0.30 (same as plot blueprints).")
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

    public LootChestBlockPaletteConfig() {}

    @Nonnull
    private static String defaultNote() {
        return "Independent of prop and blueprint rolls.";
    }

    public double getChance() {
        return chance;
    }

    @Nullable
    public String getNote() {
        return note;
    }
}
