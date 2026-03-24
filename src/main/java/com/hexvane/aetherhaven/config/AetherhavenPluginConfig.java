package com.hexvane.aetherhaven.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import javax.annotation.Nonnull;

/**
 * Loaded from the plugin data directory as {@code config.json}.
 * On first run, {@code AetherhavenPlugin} writes {@code config.json} with these defaults if the file is absent.
 */
public final class AetherhavenPluginConfig {
    public static final BuilderCodec<AetherhavenPluginConfig> CODEC = BuilderCodec.builder(AetherhavenPluginConfig.class, AetherhavenPluginConfig::new)
        .append(
            new KeyedCodec<>("ConstructionBlocksPerTick", Codec.INTEGER),
            (o, v) -> o.constructionBlocksPerTick = v,
            o -> o.constructionBlocksPerTick
        )
        .add()
        .append(
            new KeyedCodec<>("ConstructionMinIntervalMs", Codec.LONG),
            (o, v) -> o.constructionMinIntervalMs = v,
            o -> o.constructionMinIntervalMs
        )
        .add()
        .append(
            new KeyedCodec<>("IgnoreVillagerRequirement", Codec.BOOLEAN),
            (o, v) -> o.ignoreVillagerRequirement = v,
            o -> o.ignoreVillagerRequirement
        )
        .add()
        .append(
            new KeyedCodec<>("DefaultTerritoryChunkRadius", Codec.INTEGER),
            (o, v) -> o.defaultTerritoryChunkRadius = v,
            o -> o.defaultTerritoryChunkRadius
        )
        .add()
        .build();

    private int constructionBlocksPerTick = 8;
    private long constructionMinIntervalMs = 25L;
    private boolean ignoreVillagerRequirement = false;
    private int defaultTerritoryChunkRadius = 8;

    public int getConstructionBlocksPerTick() {
        return constructionBlocksPerTick;
    }

    public long getConstructionMinIntervalMs() {
        return constructionMinIntervalMs;
    }

    public boolean isIgnoreVillagerRequirement() {
        return ignoreVillagerRequirement;
    }

    public int getDefaultTerritoryChunkRadius() {
        return Math.max(1, defaultTerritoryChunkRadius);
    }

    @Nonnull
    public static AetherhavenPluginConfig defaults() {
        return new AetherhavenPluginConfig();
    }
}
