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
        .append(
            new KeyedCodec<>("DebugCommandsEnabled", Codec.BOOLEAN),
            (o, v) -> o.debugCommandsEnabled = v,
            o -> o.debugCommandsEnabled
        )
        .documentation(
            "When true, enables /aetherhaven poi, plots, needs, and quest debug subcommands. "
                + "Trusted operators only; commands mutate town data and villager components."
        )
        .add()
        .append(
            new KeyedCodec<>("VillagerNeedsDecayPerSecond", Codec.FLOAT),
            (o, v) -> o.villagerNeedsDecayPerSecond = v,
            o -> o.villagerNeedsDecayPerSecond
        )
        .documentation(
            "Hunger points (0..100 scale) removed per second of game time at full rate; energy/fun use slightly lower "
                + "multipliers. Default 0.04 (~42 min from 100 to 0 for hunger). Config values below 0.002 are assumed "
                + "to be legacy 0..1-scale rates and are multiplied by 100. Values >= 20 are capped to the default."
        )
        .add()
        .build();

    private int constructionBlocksPerTick = 8;
    private long constructionMinIntervalMs = 25L;
    private boolean ignoreVillagerRequirement = false;
    private int defaultTerritoryChunkRadius = 8;
    private boolean debugCommandsEnabled = false;
    /** Hunger points (0..100 scale) drained per second of game time; energy/fun use lower multipliers in code. */
    private float villagerNeedsDecayPerSecond = 0.04f;

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

    public boolean isDebugCommandsEnabled() {
        return debugCommandsEnabled;
    }

    public float getVillagerNeedsDecayPerSecond() {
        float v = villagerNeedsDecayPerSecond > 0f ? villagerNeedsDecayPerSecond : 0.04f;
        if (v > 0f && v < 0.002f) {
            v *= 100f;
        }
        if (v >= 20f) {
            return 0.04f;
        }
        return v;
    }

    @Nonnull
    public static AetherhavenPluginConfig defaults() {
        return new AetherhavenPluginConfig();
    }
}
