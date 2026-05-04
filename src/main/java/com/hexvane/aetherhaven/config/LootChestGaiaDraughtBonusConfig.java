package com.hexvane.aetherhaven.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import javax.annotation.Nonnull;

/** Optional extra rolls for Gaia draught upgrade items in world chest injection. */
public final class LootChestGaiaDraughtBonusConfig {
    public static final BuilderCodec<LootChestGaiaDraughtBonusConfig> CODEC =
        BuilderCodec.builder(LootChestGaiaDraughtBonusConfig.class, LootChestGaiaDraughtBonusConfig::new)
            .append(
                new KeyedCodec<>("ShardChance", Codec.DOUBLE),
                (o, v) -> o.shardChance = v != null ? v : 0.04,
                o -> o.shardChance
            )
            .documentation("0..1: roll once per eligible chest to add one Shard of Gaia to a free slot.")
            .add()
            .append(
                new KeyedCodec<>("ShardItemId", Codec.STRING),
                (o, v) -> o.shardItemId = v != null ? v : "Aetherhaven_Shard_Of_Gaia",
                o -> o.shardItemId
            )
            .add()
            .append(
                new KeyedCodec<>("CatalystChance", Codec.DOUBLE),
                (o, v) -> o.catalystChance = v != null ? v : 0.025,
                o -> o.catalystChance
            )
            .documentation("0..1: roll once per eligible chest to add one Verdant Catalyst to a free slot.")
            .add()
            .append(
                new KeyedCodec<>("CatalystItemId", Codec.STRING),
                (o, v) -> o.catalystItemId = v != null ? v : "Aetherhaven_Verdant_Catalyst",
                o -> o.catalystItemId
            )
            .add()
            .build();

    private double shardChance = 0.07;
    @Nonnull
    private String shardItemId = "Aetherhaven_Shard_Of_Gaia";
    private double catalystChance = 0.06;
    @Nonnull
    private String catalystItemId = "Aetherhaven_Verdant_Catalyst";

    public double getShardChance() {
        return shardChance;
    }

    @Nonnull
    public String getShardItemId() {
        return shardItemId != null ? shardItemId : "Aetherhaven_Shard_Of_Gaia";
    }

    public double getCatalystChance() {
        return catalystChance;
    }

    @Nonnull
    public String getCatalystItemId() {
        return catalystItemId != null ? catalystItemId : "Aetherhaven_Verdant_Catalyst";
    }
}
