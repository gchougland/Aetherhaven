package com.hexvane.aetherhaven.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Optional decoration prop drops in world loot chest injection. */
public final class LootChestPropConfig {
    public static final BuilderCodec<LootChestPropConfig> CODEC =
        BuilderCodec.builder(LootChestPropConfig.class, LootChestPropConfig::new)
            .append(
                new KeyedCodec<>("Chance", Codec.DOUBLE),
                (o, v) -> o.chance = v != null ? v : 0.30,
                o -> o.chance
            )
            .documentation(
                "0..1: roll once per eligible chest to add one random decoration prop."
                    + " Default 0.30 (30% per eligible chest). Festival merchant props are excluded."
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

    public LootChestPropConfig() {}

    @Nonnull
    private static String defaultNote() {
        return "Independent of plot blueprint rolls. Uses prop_loot_exclusions.json for the skip list.";
    }

    public double getChance() {
        return chance;
    }

    @Nullable
    public String getNote() {
        return note;
    }
}
