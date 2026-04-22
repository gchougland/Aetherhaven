package com.hexvane.aetherhaven.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Loaded from the plugin data directory as {@code config.json}.
 * On first run, {@code AetherhavenPlugin} writes {@code config.json} with these defaults if the file is absent.
 * When the file already exists, startup merges in any missing keys from the current plugin defaults so new
 * options appear without discarding existing settings.
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
        .documentation(
            "When true, resident NPCs follow weekly JSON schedules under Server/Aetherhaven/VillagerSchedules/<roleId>.json."
        )
        .add()
        .append(
            new KeyedCodec<>("VillagerScheduleDebugLog", Codec.BOOLEAN),
            (o, v) -> o.villagerScheduleDebugLog = v,
            o -> o.villagerScheduleDebugLog
        )
        .documentation(
            "When true with VillagerScheduleEnabled, emits villager schedule diagnostics to the server log at INFO "
                + "(never to players). Unresolved-plot messages are limited to once per in-game hour per villager."
        )
        .add()
        .append(
            new KeyedCodec<>("TreasuryMaxGoldTaxPerVillagerPerDay", Codec.INTEGER),
            (o, v) -> o.treasuryMaxGoldTaxPerVillagerPerDay = v,
            o -> o.treasuryMaxGoldTaxPerVillagerPerDay
        )
        .documentation(
            "Maximum gold coins collected per resident villager per in-game morning when town hall exists; "
                + "actual tax scales with average villager needs (hunger, energy, fun)."
        )
        .add()
        .append(
            new KeyedCodec<>("GeodeDropChancePerOreBreak", Codec.DOUBLE),
            (o, v) -> o.geodeDropChancePerOreBreak = v,
            o -> o.geodeDropChancePerOreBreak
        )
        .documentation("Probability 0..1 that breaking an ore block also drops one geode (event-driven, not block loot).")
        .add()
        .append(
            new KeyedCodec<>("GeodeOreUseBlocksOresCategory", Codec.BOOLEAN),
            (o, v) -> o.geodeOreUseBlocksOresCategory = v,
            o -> o.geodeOreUseBlocksOresCategory
        )
        .documentation(
            "When true, ore blocks include those whose Item has category Blocks.Ores (excluding excluded subcategories). "
                + "When false, subcategory / gather-type / extra lists only."
        )
        .add()
        .append(
            new KeyedCodec<>("GeodeOreSubcategories", Codec.STRING),
            (o, v) -> o.geodeOreSubcategories = v != null ? v : "",
            o -> o.geodeOreSubcategories
        )
        .documentation("Comma-separated Item subcategories that count as ore (default: Ore,Ores).")
        .add()
        .append(
            new KeyedCodec<>("GeodeOreExcludedSubcategories", Codec.STRING),
            (o, v) -> o.geodeOreExcludedSubcategories = v != null ? v : "",
            o -> o.geodeOreExcludedSubcategories
        )
        .documentation(
            "When using Blocks.Ores category, exclude these subcategories (default: Gem so gem blocks are not 'ore' for geodes)."
        )
        .add()
        .append(
            new KeyedCodec<>("GeodeExtraOreGatherTypes", Codec.STRING),
            (o, v) -> o.geodeExtraOreGatherTypes = v != null ? v : "",
            o -> o.geodeExtraOreGatherTypes
        )
        .documentation("Comma-separated extra block breaking gather types that count (e.g. mod OreFoo).")
        .add()
        .append(
            new KeyedCodec<>("GeodeExtraOreBlockTypeIds", Codec.STRING),
            (o, v) -> o.geodeExtraOreBlockTypeIds = v != null ? v : "",
            o -> o.geodeExtraOreBlockTypeIds
        )
        .documentation("Comma-separated extra block type ids that always count as ore for geode drops.")
        .add()
        .append(
            new KeyedCodec<>("CharterTaxPerCapitaFlatFraction", Codec.DOUBLE),
            (o, v) -> o.charterTaxPerCapitaFlatFraction = v != null ? v : 0.35,
            o -> o.charterTaxPerCapitaFlatFraction
        )
        .documentation(
            "For per-capita charter tax policy: blend maxPer * (flatFraction + (1-flatFraction) * needsRatio). Clamped internally."
        )
        .add()
        .append(
            new KeyedCodec<>("CharterTaxHappinessExponent", Codec.DOUBLE),
            (o, v) -> o.charterTaxHappinessExponent = v != null ? v : 1.25,
            o -> o.charterTaxHappinessExponent
        )
        .documentation("For happiness-weighted charter tax: maxPer * needsRatio^exponent. Exponent >= 1.")
        .add()
        .append(
            new KeyedCodec<>("FounderMonumentTaxPermille", Codec.INTEGER),
            (o, v) -> o.founderMonumentTaxPermille = v != null ? v : 1100,
            o -> o.founderMonumentTaxPermille
        )
        .documentation("Morning tax sum multiplier when founder monument is active, in permille (1100 = +10%).")
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

    /** Max gold coins per resident per morning tax tick (needs-scaled). */
    private int treasuryMaxGoldTaxPerVillagerPerDay = 10;

    /** Chance per ore block break to drop an extra geode item. */
    private double geodeDropChancePerOreBreak = 0.015;

    private boolean geodeOreUseBlocksOresCategory = true;

    /** Comma-separated; default filled in getter if blank. */
    private String geodeOreSubcategories = "";

    private String geodeOreExcludedSubcategories = "";

    private String geodeExtraOreGatherTypes = "";

    private String geodeExtraOreBlockTypeIds = "";

    private double charterTaxPerCapitaFlatFraction = 0.35;
    private double charterTaxHappinessExponent = 1.25;
    private int founderMonumentTaxPermille = 1100;

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

    public int getTreasuryMaxGoldTaxPerVillagerPerDay() {
        int v = treasuryMaxGoldTaxPerVillagerPerDay;
        return v > 0 ? v : 10;
    }

    /** Clamped to [0, 0.95]. */
    public double getCharterTaxPerCapitaFlatFraction() {
        double v = charterTaxPerCapitaFlatFraction;
        if (v < 0.0) {
            return 0.0;
        }
        return Math.min(v, 0.95);
    }

    /** At least 1.0. */
    public double getCharterTaxHappinessExponent() {
        double v = charterTaxHappinessExponent;
        return v >= 1.0 ? v : 1.25;
    }

    /** Clamped to [1000, 2000]. */
    public int getFounderMonumentTaxPermille() {
        int v = founderMonumentTaxPermille;
        if (v < 1000) {
            return 1000;
        }
        return Math.min(v, 2000);
    }

    /** Clamped to [0, 1]. */
    public double getGeodeDropChancePerOreBreak() {
        double v = geodeDropChancePerOreBreak;
        if (v < 0.0) {
            return 0.0;
        }
        return Math.min(v, 1.0);
    }

    public boolean isGeodeOreUseBlocksOresCategory() {
        return geodeOreUseBlocksOresCategory;
    }

    @Nonnull
    public Set<String> geodeOreSubcategorySet() {
        return splitCsv(geodeOreSubcategories.isBlank() ? "Ore,Ores" : geodeOreSubcategories);
    }

    @Nonnull
    public Set<String> geodeOreExcludedSubcategorySet() {
        return splitCsv(geodeOreExcludedSubcategories.isBlank() ? "Gem" : geodeOreExcludedSubcategories);
    }

    @Nonnull
    public Set<String> geodeExtraOreGatherTypeSet() {
        return splitCsv(geodeExtraOreGatherTypes);
    }

    @Nonnull
    public Set<String> geodeExtraOreBlockTypeIdSet() {
        return splitCsv(geodeExtraOreBlockTypeIds);
    }

    @Nonnull
    private static Set<String> splitCsv(@Nonnull String s) {
        if (s.isBlank()) {
            return Collections.emptySet();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (String part : s.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    @Nonnull
    public static AetherhavenPluginConfig defaults() {
        return new AetherhavenPluginConfig();
    }
}
