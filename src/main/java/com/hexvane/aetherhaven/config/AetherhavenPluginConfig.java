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
        .append(
            new KeyedCodec<>("InnPoolMorningStartHour", Codec.INTEGER),
            (o, v) -> o.innPoolMorningStartHour = v,
            o -> o.innPoolMorningStartHour
        )
        .documentation(
            "In-game hour (0-23) when the morning inn refresh window starts. With InnPoolMorningEndHour, visitors are "
                + "reshuffled at most once per calendar game day while the hour is in [start, end)."
        )
        .add()
        .append(
            new KeyedCodec<>("InnPoolMorningEndHour", Codec.INTEGER),
            (o, v) -> o.innPoolMorningEndHour = v,
            o -> o.innPoolMorningEndHour
        )
        .documentation("Exclusive end hour (0-24). Default 6-12 is morning (6:00 up to but not including 12:00).")
        .add()
        .append(
            new KeyedCodec<>("VillagerScheduleEnabled", Codec.BOOLEAN),
            (o, v) -> o.villagerScheduleEnabled = v,
            o -> o.villagerScheduleEnabled
        )
        .documentation("When true, resident NPCs follow weekly JSON schedules under Server/VillagerSchedules/<roleId>.json.")
        .add()
        .append(
            new KeyedCodec<>("VillagerScheduleDebugLog", Codec.BOOLEAN),
            (o, v) -> o.villagerScheduleDebugLog = v,
            o -> o.villagerScheduleDebugLog
        )
        .documentation("Logs schedule segment resolution and preferred plot updates.")
        .add()
        .build();

    private int constructionBlocksPerTick = 8;
    private long constructionMinIntervalMs = 25L;
    private boolean ignoreVillagerRequirement = false;
    private int defaultTerritoryChunkRadius = 8;
    private boolean debugCommandsEnabled = false;
    /** Hunger points (0..100 scale) drained per second of game time; energy/fun use lower multipliers in code. */
    private float villagerNeedsDecayPerSecond = 0.04f;

    /** Inclusive start hour for the daily morning inn refresh (game clock, {@link com.hypixel.hytale.server.core.modules.time.WorldTimeResource}). */
    private int innPoolMorningStartHour = 5;
    /** Exclusive end hour (e.g. 15 means 5:00-14:59). */
    private int innPoolMorningEndHour = 15;

    private boolean villagerScheduleEnabled = true;
    private boolean villagerScheduleDebugLog = false;

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

    /** Inclusive start, 0-23. */
    public int getInnPoolMorningStartHour() {
        int h = innPoolMorningStartHour;
        if (h < 0) {
            return 0;
        }
        return Math.min(h, 23);
    }

    /** Exclusive end, 1-24; if invalid, defaults to start+6 capped at 24. */
    public int getInnPoolMorningEndHourExclusive() {
        int start = getInnPoolMorningStartHour();
        int end = innPoolMorningEndHour;
        if (end <= start || end > 24) {
            end = Math.min(start + 6, 24);
        }
        return Math.max(start + 1, end);
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

    public boolean isVillagerScheduleEnabled() {
        return villagerScheduleEnabled;
    }

    public boolean isVillagerScheduleDebugLog() {
        return villagerScheduleDebugLog;
    }

    @Nonnull
    public static AetherhavenPluginConfig defaults() {
        return new AetherhavenPluginConfig();
    }
}
