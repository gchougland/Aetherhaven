package com.hexvane.aetherhaven.config;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
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
            "Shared in-game morning window start hour (0-23): inn visitor refresh, farm sprinklers, treasury tax "
                + "breakdown 'morning' line. JSON key name is historical; not inn-only."
        )
        .add()
        .append(
            new KeyedCodec<>("InnPoolMorningEndHour", Codec.INTEGER),
            (o, v) -> o.innPoolMorningEndHour = v,
            o -> o.innPoolMorningEndHour
        )
        .documentation(
            "Shared exclusive end hour (0-24) for the same morning window. Default 6-12: 6:00 up to but not 12:00."
        )
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
        .append(
            new KeyedCodec<>("LootChest", LootChestConfig.CODEC),
            (o, v) -> o.lootChest = v != null ? v : new LootChestConfig(),
            o -> o.lootChest
        )
        .documentation(
            "World chest bonus rolls (jewelry, gold, plot token). Use nested keys: JewelryChance, BlockIdSubstrings, Gold, PlotToken."
        )
        .add()
        .append(
            new KeyedCodec<>("Jewelry", JewelryConfig.CODEC),
            (o, v) -> o.jewelry = v != null ? v : new JewelryConfig(),
            o -> o.jewelry
        )
        .documentation("Jewelry trait rolling: RarityWeights, TraitMultipliers, and Stat (per-stat Common/Legendary Min/Max).")
        .add()
        .append(
            new KeyedCodec<>("FeastTaxBonusPermille", Codec.INTEGER),
            (o, v) -> o.feastTaxBonusPermille = v != null ? v : 1250,
            o -> o.feastTaxBonusPermille
        )
        .documentation(
            "When the Steward's Ledger feast is active, morning treasury tax sum is multiplied by this permille "
                + "(1250 = +25% after charter/founder multipliers)."
        )
        .add()
        .append(
            new KeyedCodec<>("FeastNeedsDecayScalePermille", Codec.INTEGER),
            (o, v) -> o.feastNeedsDecayScalePermille = v != null ? v : 650,
            o -> o.feastNeedsDecayScalePermille
        )
        .documentation(
            "During Hearthglass Vigil feast, villager needs decay rate is multiplied by permille/1000 (650 ≈ 65% speed)."
        )
        .add()
        .append(
            new KeyedCodec<>("FeastGatherTimeoutSeconds", Codec.INTEGER),
            (o, v) -> o.feastGatherTimeoutSeconds = v != null ? v : 120,
            o -> o.feastGatherTimeoutSeconds
        )
        .documentation("Wall-time safety timeout for villagers routing to a feast table POI before the POI is cleared.")
        .add()
        .append(
            new KeyedCodec<>("PathToolNodeBlockYOffset", Codec.DOUBLE),
            (o, v) -> o.pathToolNodeBlockYOffset = v != null ? v : 1.0,
            o -> o.pathToolNodeBlockYOffset
        )
        .documentation("Placed control node Y offset (blocks) above the clicked block center (e.g. 1.0 or 1.5).")
        .add()
        .append(
            new KeyedCodec<>("PathToolSamplesPerBlock", Codec.INTEGER),
            (o, v) -> o.pathToolSamplesPerBlock = v != null ? v : 2,
            o -> o.pathToolSamplesPerBlock
        )
        .documentation("Spline samples per block along the curve (higher = denser).")
        .add()
        .append(
            new KeyedCodec<>("PathToolHalfWidth", Codec.INTEGER),
            (o, v) -> o.pathToolHalfWidth = v != null ? v : 2,
            o -> o.pathToolHalfWidth
        )
        .documentation("Half-width in cells each side of center. Width = 2 * half + 1 (default 2 => 5 wide).")
        .add()
        .append(
            new KeyedCodec<>("PathToolRayStartAboveY", Codec.INTEGER),
            (o, v) -> o.pathToolRayStartAboveY = v != null ? v : 6,
            o -> o.pathToolRayStartAboveY
        )
        .documentation("Start ground snap ray this many blocks above each lateral sample point.")
        .add()
        .append(
            new KeyedCodec<>("PathToolMaxRayDown", Codec.INTEGER),
            (o, v) -> o.pathToolMaxRayDown = v != null ? v : 128,
            o -> o.pathToolMaxRayDown
        )
        .documentation("Max downward search steps for column ray.")
        .add()
        .append(
            new KeyedCodec<>("PathToolReplaceableBlockIds", Codec.STRING),
            (o, v) -> o.pathToolReplaceableBlockIds = v != null ? v : "",
            o -> o.pathToolReplaceableBlockIds
        )
        .documentation(
            "Comma-separated block type ids that may be replaced. When this and PathToolReplaceableResourceTypeIds are "
                + "both empty, the path tool may replace any block id starting with Soil_ (all vanilla soils, including "
                + "Soil_Grass_Deep and Aetherhaven path output such as Soil_Pathway) or containing Dirt. Re-placing over a "
                + "previously committed path is allowed."
        )
        .add()
        .append(
            new KeyedCodec<>("PathToolReplaceableResourceTypeIds", Codec.STRING),
            (o, v) -> o.pathToolReplaceableResourceTypeIds = v != null ? v : "",
            o -> o.pathToolReplaceableResourceTypeIds
        )
        .documentation("Comma-separated item resource type ids (block item materials) for replaceable blocks.")
        .add()
        .append(
            new KeyedCodec<>("PathToolStyles", Codec.STRING),
            (o, v) -> o.pathToolStyles = v != null && !v.isBlank() ? v : PathToolStyleDefinition.DEFAULT_JSON,
            o -> o.pathToolStyles != null && !o.pathToolStyles.isBlank() ? o.pathToolStyles : PathToolStyleDefinition.DEFAULT_JSON
        )
        .documentation(
            "JSON array of path styles. Each object: name (label for the player) and centerBlockIds (string array of "
                + "block type ids for the center path strip, chosen at random per cell). Edge strips always use grass. "
                + "If invalid or empty, a built-in default (soil + cobble) is used."
        )
        .add()
        .append(
            new KeyedCodec<>("PathNavEnabled", Codec.BOOLEAN),
            (o, v) -> o.pathNavEnabled = v != null ? v : true,
            o -> o.pathNavEnabled
        )
        .documentation("When true, villagers may route along committed path-tool centerline graphs.")
        .add()
        .append(
            new KeyedCodec<>("PathNavNodeSpacing", Codec.DOUBLE),
            (o, v) -> o.pathNavNodeSpacing = v != null ? v : 1.5,
            o -> o.pathNavNodeSpacing
        )
        .documentation("Equidistant centerline spacing in blocks for nav nodes sampled from the spline on commit.")
        .add()
        .append(
            new KeyedCodec<>("PathNavSnapRadius", Codec.DOUBLE),
            (o, v) -> o.pathNavSnapRadius = v != null ? v : 8.0,
            o -> o.pathNavSnapRadius
        )
        .documentation(
            "Legacy / reserved for future doorway or footprint-based snaps. Segment path nav uses PathNavEndpointGateRadius."
        )
        .add()
        .append(
            new KeyedCodec<>("PathNavJunctionEps", Codec.DOUBLE),
            (o, v) -> o.pathNavJunctionEps = v != null ? v : 1.25,
            o -> o.pathNavJunctionEps
        )
        .documentation(
            "Max horizontal gap in blocks between the **start or end** of one placed path and **any** nav node on "
                + "another path (T-junction: side road into main road midline). Interior nodes never initiate a jump to "
                + "another path."
        )
        .add()
        .append(
            new KeyedCodec<>("PathNavEndpointGateRadius", Codec.DOUBLE),
            (o, v) -> o.pathNavEndpointGateRadius = v != null ? v : 32.0,
            o -> o.pathNavEndpointGateRadius
        )
        .documentation(
            "Max horizontal blocks from the NPC to the committed path (nearest vertex or <b>edge</b> of the centerline "
                + "in that town) to allow path routing. Values below 32 are raised to 32 at runtime (typical home "
                + "standpoints are 20–25+ m from a road centerline; a 20 m gate was always borderline). Set 48+ for "
                + "large plots. See PATHNAV_GATE_NETWORK logs (min= distance, gateR= effective cap)."
        )
        .add()
        .append(
            new KeyedCodec<>("PathNavMaxNodesPerTown", Codec.INTEGER),
            (o, v) -> o.pathNavMaxNodesPerTown = v != null ? v : 6000,
            o -> o.pathNavMaxNodesPerTown
        )
        .documentation("Soft cap on generated path-nav nodes per town graph to bound memory and rebuild cost.")
        .add()
        .append(
            new KeyedCodec<>("PathNavPreferIfShorterOnly", Codec.BOOLEAN),
            (o, v) -> o.pathNavPreferIfShorterOnly = v != null ? v : false,
            o -> o.pathNavPreferIfShorterOnly
        )
        .documentation(
            "When true, villager path routing is used only if total distance (entry snap + path graph + exit snap) is "
                + "less than a straight line from NPC to target. When false, any valid snapped route is used (typical for "
                + "wanting villagers on paths)."
        )
        .add()
        .append(
            new KeyedCodec<>("PathNavPathfindingLog", Codec.BOOLEAN),
            (o, v) -> o.pathNavPathfindingLog = v != null ? v : false,
            o -> o.pathNavPathfindingLog
        )
        .documentation(
            "If true, logs [PathNav] lines when a villager skips placed-path routing (see PathNav findRoute skip codes)."
        )
        .add()
        .build();

    private int constructionBlocksPerTick = 8;
    private long constructionMinIntervalMs = 25L;
    private boolean ignoreVillagerRequirement = false;
    private int defaultTerritoryChunkRadius = 8;
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

    private LootChestConfig lootChest = new LootChestConfig();
    private JewelryConfig jewelry = new JewelryConfig();

    private int feastTaxBonusPermille = 1250;
    private int feastNeedsDecayScalePermille = 650;
    private int feastGatherTimeoutSeconds = 120;

    private double pathToolNodeBlockYOffset = 1.0;
    private int pathToolSamplesPerBlock = 2;
    private int pathToolHalfWidth = 2;
    private int pathToolRayStartAboveY = 6;
    private int pathToolMaxRayDown = 128;
    private String pathToolReplaceableBlockIds = "";
    private String pathToolReplaceableResourceTypeIds = "";
    private String pathToolStyles = PathToolStyleDefinition.DEFAULT_JSON;
    private boolean pathNavEnabled = true;
    private double pathNavNodeSpacing = 1.5;
    private double pathNavSnapRadius = 8.0;
    private double pathNavJunctionEps = 1.25;
    private double pathNavEndpointGateRadius = 32.0;
    private int pathNavMaxNodesPerTown = 6000;
    private boolean pathNavPreferIfShorterOnly = false;
    private boolean pathNavPathfindingLog = false;

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

    /**
     * Shared morning window: inn visitor daily refresh, sprinklers, treasury UI “morning” line. Config file keys stay
     * {@code InnPoolMorningStartHour} / {@code InnPoolMorningEndHour} for existing saves.
     */
    public int getGameMorningStartHour() {
        int h = innPoolMorningStartHour;
        if (h < 0) {
            return 0;
        }
        return Math.min(h, 23);
    }

    /** @see #getGameMorningStartHour */
    public int getGameMorningEndHourExclusive() {
        int start = getGameMorningStartHour();
        int end = innPoolMorningEndHour;
        if (end <= start || end > 24) {
            end = Math.min(start + 6, 24);
        }
        return Math.max(start + 1, end);
    }

    /** Alias for {@link #getGameMorningStartHour} (inn uses this window among other features). */
    public int getInnPoolMorningStartHour() {
        return getGameMorningStartHour();
    }

    /** Alias for {@link #getGameMorningEndHourExclusive}. */
    public int getInnPoolMorningEndHourExclusive() {
        return getGameMorningEndHourExclusive();
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

    @Nonnull
    public LootChestConfig getLootChest() {
        return lootChest != null ? lootChest : new LootChestConfig();
    }

    @Nonnull
    public JewelryConfig getJewelry() {
        return jewelry != null ? jewelry : new JewelryConfig();
    }

    /** Clamped to [1000, 2000]. */
    public int getFeastTaxBonusPermille() {
        int v = feastTaxBonusPermille;
        if (v < 1000) {
            return 1000;
        }
        return Math.min(v, 2000);
    }

    /** Clamped to [100, 1000]; lower = slower decay during feast. */
    public int getFeastNeedsDecayScalePermille() {
        int v = feastNeedsDecayScalePermille;
        if (v < 100) {
            return 100;
        }
        return Math.min(v, 1000);
    }

    public int getFeastGatherTimeoutSeconds() {
        int v = feastGatherTimeoutSeconds;
        return v >= 30 ? v : 120;
    }

    public double getPathToolNodeBlockYOffset() {
        double v = pathToolNodeBlockYOffset;
        if (Double.isNaN(v) || v < 0.0 || v > 32.0) {
            return 1.0;
        }
        return v;
    }

    public int getPathToolSamplesPerBlock() {
        int v = pathToolSamplesPerBlock;
        return v >= 1 && v <= 32 ? v : 2;
    }

    public int getPathToolHalfWidth() {
        int v = pathToolHalfWidth;
        if (v >= 1 && v <= 6) {
            return v;
        }
        if (v == 0) {
            return 2;
        }
        return 2;
    }

    public int getPathToolRayStartAboveY() {
        int v = pathToolRayStartAboveY;
        return v >= 1 && v <= 64 ? v : 6;
    }

    public int getPathToolMaxRayDown() {
        int v = pathToolMaxRayDown;
        return v >= 8 && v <= 512 ? v : 128;
    }

    @Nonnull
    public String getPathToolReplaceableBlockIds() {
        return pathToolReplaceableBlockIds != null ? pathToolReplaceableBlockIds : "";
    }

    @Nonnull
    public String getPathToolReplaceableResourceTypeIds() {
        return pathToolReplaceableResourceTypeIds != null ? pathToolReplaceableResourceTypeIds : "";
    }

    @Nonnull
    public List<PathToolStyleDefinition> getPathToolStyleDefinitions() {
        return PathToolStyleDefinition.parseList(pathToolStyles);
    }

    @Nonnull
    public String getPathToolStyleName(int pathStyleIndex) {
        List<PathToolStyleDefinition> list = getPathToolStyleDefinitions();
        if (list.isEmpty()) {
            return "?";
        }
        return list.get(Math.floorMod(pathStyleIndex, list.size())).getName();
    }

    public boolean isPathNavEnabled() {
        return pathNavEnabled;
    }

    public double getPathNavNodeSpacing() {
        double v = pathNavNodeSpacing;
        if (Double.isNaN(v) || v < 0.25 || v > 64.0) {
            return 1.5;
        }
        return v;
    }

    public double getPathNavSnapRadius() {
        double v = pathNavSnapRadius;
        if (Double.isNaN(v) || v < 0.5 || v > 64.0) {
            return 8.0;
        }
        return v;
    }

    public double getPathNavJunctionEps() {
        double v = pathNavJunctionEps;
        if (Double.isNaN(v) || v < 0.1 || v > 24.0) {
            return 1.25;
        }
        return v;
    }

    public double getPathNavEndpointGateRadius() {
        double v = pathNavEndpointGateRadius;
        if (Double.isNaN(v) || v < 1.0 || v > 128.0) {
            return 32.0;
        }
        // Small configs (8–20) and the previous 20 m floor still produced PATHNAV_GATE_NETWORK for typical POIs 20+ m
        // from the path centerline; 32 m matches the schema default and clears normal town layouts without forcing every
        // server to hand-edit config.
        return Math.max(32.0, v);
    }

    public int getPathNavMaxNodesPerTown() {
        int v = pathNavMaxNodesPerTown;
        return v >= 256 && v <= 250000 ? v : 6000;
    }

    public boolean isPathNavPreferIfShorterOnly() {
        return pathNavPreferIfShorterOnly;
    }

    public boolean isPathNavPathfindingLog() {
        return pathNavPathfindingLog;
    }

    /** Clamped to [0, 1]. Zero disables extra chest jewelry. */
    public double getLootChestJewelryChance() {
        double v = getLootChest().getJewelryChance();
        if (v < 0.0) {
            return 0.0;
        }
        return Math.min(v, 1.0);
    }

    /** Clamped to [0, 1]. */
    public double getLootChestGoldCoinChance() {
        double v = getLootChest().getGold().getChance();
        if (v < 0.0) {
            return 0.0;
        }
        return Math.min(v, 1.0);
    }

    public int getLootChestGoldCoinMin() {
        return Math.max(1, getLootChest().getGold().getMin());
    }

    public int getLootChestGoldCoinMax() {
        return Math.max(getLootChestGoldCoinMin(), getLootChest().getGold().getMax());
    }

    @Nonnull
    public String getLootChestGoldCoinItemId() {
        return getLootChest().getGold().getItemId();
    }

    public double getLootChestPlotTokenChance() {
        double v = getLootChest().getPlotToken().getChance();
        if (v < 0.0) {
            return 0.0;
        }
        return Math.min(v, 1.0);
    }

    /**
     * When true, world-load bonus injection also runs in Creative. Default false (Stash and many chest paths skip
     * Creative for stability).
     */
    public boolean isLootChestApplyInCreative() {
        return getLootChest().isApplyInCreative();
    }

    public boolean isLootChestLootrPerPlayerCompatibilityEnabled() {
        return getLootChest().isLootrPerPlayerCompatibilityEnabled();
    }

    @Nonnull
    public String getLootChestPlotTokenItemId() {
        return getLootChest().getPlotToken().getItemId();
    }

    public double getJewelryRarityWeightCommon() {
        return Math.max(0.0, getJewelry().getRarityWeights().getCommon());
    }

    public double getJewelryRarityWeightUncommon() {
        return Math.max(0.0, getJewelry().getRarityWeights().getUncommon());
    }

    public double getJewelryRarityWeightRare() {
        return Math.max(0.0, getJewelry().getRarityWeights().getRare());
    }

    public double getJewelryRarityWeightMythic() {
        return Math.max(0.0, getJewelry().getRarityWeights().getMythic());
    }

    public double getJewelryRarityWeightLegendary() {
        return Math.max(0.0, getJewelry().getRarityWeights().getLegendary());
    }

    public double getJewelryGoldMetalTraitMultiplier() {
        double v = getJewelry().getTraitMultipliers().getGoldMetal();
        return v > 0.0 ? v : 1.2;
    }

    public double getJewelryNecklaceTraitMultiplier() {
        double v = getJewelry().getTraitMultipliers().getNecklace();
        return v > 0.0 ? v : 1.15;
    }

    public double getJewelryStatHealthCommonMin() {
        return getJewelry().getStat().getHealth().getCommon().getMin();
    }

    public double getJewelryStatHealthCommonMax() {
        return getJewelry().getStat().getHealth().getCommon().getMax();
    }

    public double getJewelryStatHealthLegendaryMin() {
        return getJewelry().getStat().getHealth().getLegendary().getMin();
    }

    public double getJewelryStatHealthLegendaryMax() {
        return getJewelry().getStat().getHealth().getLegendary().getMax();
    }

    public double getJewelryStatStaminaCommonMin() {
        return getJewelry().getStat().getStamina().getCommon().getMin();
    }

    public double getJewelryStatStaminaCommonMax() {
        return getJewelry().getStat().getStamina().getCommon().getMax();
    }

    public double getJewelryStatStaminaLegendaryMin() {
        return getJewelry().getStat().getStamina().getLegendary().getMin();
    }

    public double getJewelryStatStaminaLegendaryMax() {
        return getJewelry().getStat().getStamina().getLegendary().getMax();
    }

    public double getJewelryStatAmmoCommonMin() {
        return getJewelry().getStat().getAmmo().getCommon().getMin();
    }

    public double getJewelryStatAmmoCommonMax() {
        return getJewelry().getStat().getAmmo().getCommon().getMax();
    }

    public double getJewelryStatAmmoLegendaryMin() {
        return getJewelry().getStat().getAmmo().getLegendary().getMin();
    }

    public double getJewelryStatAmmoLegendaryMax() {
        return getJewelry().getStat().getAmmo().getLegendary().getMax();
    }

    public double getJewelryStatManaCommonMin() {
        return getJewelry().getStat().getMana().getCommon().getMin();
    }

    public double getJewelryStatManaCommonMax() {
        return getJewelry().getStat().getMana().getCommon().getMax();
    }

    public double getJewelryStatManaLegendaryMin() {
        return getJewelry().getStat().getMana().getLegendary().getMin();
    }

    public double getJewelryStatManaLegendaryMax() {
        return getJewelry().getStat().getMana().getLegendary().getMax();
    }

    public double getJewelryStatOxygenCommonMin() {
        return getJewelry().getStat().getOxygen().getCommon().getMin();
    }

    public double getJewelryStatOxygenCommonMax() {
        return getJewelry().getStat().getOxygen().getCommon().getMax();
    }

    public double getJewelryStatOxygenLegendaryMin() {
        return getJewelry().getStat().getOxygen().getLegendary().getMin();
    }

    public double getJewelryStatOxygenLegendaryMax() {
        return getJewelry().getStat().getOxygen().getLegendary().getMax();
    }

    public double getJewelryStatSignatureEnergyCommonMin() {
        return getJewelry().getStat().getSignatureEnergy().getCommon().getMin();
    }

    public double getJewelryStatSignatureEnergyCommonMax() {
        return getJewelry().getStat().getSignatureEnergy().getCommon().getMax();
    }

    public double getJewelryStatSignatureEnergyLegendaryMin() {
        return getJewelry().getStat().getSignatureEnergy().getLegendary().getMin();
    }

    public double getJewelryStatSignatureEnergyLegendaryMax() {
        return getJewelry().getStat().getSignatureEnergy().getLegendary().getMax();
    }

    /**
     * When non-empty, block type id must contain one of these (case-sensitive). When empty, every block id passes the
     * include filter (exclude list still applies).
     */
    @Nonnull
    public Set<String> lootChestBlockIdSubstrings() {
        return splitCsv(getLootChest().getBlockIdSubstrings());
    }

    /**
     * If a block type id contains any of these substrings, skip chest bonuses (opt-out for specific crates, etc.).
     */
    @Nonnull
    public Set<String> lootChestExcludeBlockIdSubstrings() {
        return splitCsv(getLootChest().getExcludeBlockIdSubstrings());
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
