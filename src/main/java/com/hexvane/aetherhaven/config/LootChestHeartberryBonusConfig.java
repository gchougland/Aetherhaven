package com.hexvane.aetherhaven.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import javax.annotation.Nonnull;

/** Optional Heartberry roll in world loot chest injection. */
public final class LootChestHeartberryBonusConfig {
    public static final BuilderCodec<LootChestHeartberryBonusConfig> CODEC =
        BuilderCodec.builder(LootChestHeartberryBonusConfig.class, LootChestHeartberryBonusConfig::new)
            .append(
                new KeyedCodec<>("Chance", Codec.DOUBLE),
                (o, v) -> o.chance = v != null ? v : 0.035,
                o -> o.chance
            )
            .documentation("0..1: roll once per eligible chest to add one Heartberry to a free slot.")
            .add()
            .append(
                new KeyedCodec<>("ItemId", Codec.STRING),
                (o, v) -> o.itemId = v != null ? v : "Aetherhaven_Heartberry",
                o -> o.itemId
            )
            .add()
            .build();

    private double chance = 0.08;
    @Nonnull
    private String itemId = "Aetherhaven_Heartberry";

    public double getChance() {
        return chance;
    }

    @Nonnull
    public String getItemId() {
        return itemId != null ? itemId : "Aetherhaven_Heartberry";
    }
}
