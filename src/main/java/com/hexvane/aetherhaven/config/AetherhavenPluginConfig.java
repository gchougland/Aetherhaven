package com.hexvane.aetherhaven.config;

import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
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
    /** Hunger points (0..100) drained per second at full rate; energy/fun use lower multipliers in {@link VillagerNeedsDecaySystem}. */
    public static final float DEFAULT_VILLAGER_NEEDS_DECAY_PER_SECOND = 0.0525f;

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
            new KeyedCodec<>("AssemblyGameDayLengthMsOverride", Codec.LONG),
            (o, v) -> o.assemblyGameDayLengthMsOverride = v,
            o -> o.assemblyGameDayLengthMsOverride
        )
        .documentation("When > 0, passive assembly uses this many ms per in-game day instead of world day+night length.")
        .add()
        .append(
            new KeyedCodec<>("PassivePlotAssembly", Codec.BOOLEAN),
            (o, v) -> o.passivePlotAssembly = v,
            o -> o.passivePlotAssembly
        )
        .documentation(
            "When true (default), assembling plots place prefab cells on the passive schedule from each building's selfBuildGameDays "
                + "(see PlotAssemblyTickSystem / computeSlotWallMs). When false, only the building staff advances assembly while its "
                + "secondary interaction is active — no automatic blocks after release."
        )
        .add()
        .append(
            new KeyedCodec<>("AssemblySectionChunkSizeBlocks", Codec.INTEGER),
            (o, v) -> o.assemblySectionChunkSizeBlocks = v,
            o -> o.assemblySectionChunkSizeBlocks
        )
        .documentation(
            "Prefabs split into assembly sections along each axis when wider than this many prefab-local blocks (default 15)."
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
        .documentation(
            "Starter town territory is a square of (2×radius) chunks on each side (even width), centered on the charter."
        )
        .add()
        .append(
            new KeyedCodec<>("TerritoryExpansionFirstClaimCostGold", Codec.LONG),
            (o, v) -> o.territoryExpansionFirstClaimCostGold = v,
            o -> o.territoryExpansionFirstClaimCostGold
        )
        .documentation(
            "Gold cost for the first 2×2 chunk land purchase beyond the starter territory (not counting starter radius chunks)."
        )
        .add()
        .append(
            new KeyedCodec<>("TerritoryExpansionClaimCostIncrementGold", Codec.LONG),
            (o, v) -> o.territoryExpansionClaimCostIncrementGold = v,
            o -> o.territoryExpansionClaimCostIncrementGold
        )
        .documentation(
            "Added to the first-claim cost for each 2×2 land purchase already made (second purchase = first + increment, third = first + 2×increment, …)."
        )
        .add()
        .append(
            new KeyedCodec<>("TerritoryExpansionClaimLimitEnabled", Codec.BOOLEAN),
            (o, v) -> o.territoryExpansionClaimLimitEnabled = v,
            o -> o.territoryExpansionClaimLimitEnabled
        )
        .documentation(
            "When false (default), towns may buy unlimited 2×2 expansion land. When true, see MaxTerritoryExpansionClaimBlocks."
        )
        .add()
        .append(
            new KeyedCodec<>("MaxTerritoryExpansionClaimBlocks", Codec.INTEGER),
            (o, v) -> o.maxTerritoryExpansionClaimBlocks = v,
            o -> o.maxTerritoryExpansionClaimBlocks
        )
        .documentation(
            "Max number of 2×2 land purchases allowed per town when TerritoryExpansionClaimLimitEnabled is true. Ignored when the limit toggle is false."
        )
        .add()
        .append(
            new KeyedCodec<>("TownTerritoryBreakProtectionEnabled", Codec.BOOLEAN),
            (o, v) -> o.townTerritoryBreakProtectionEnabled = v != null ? v : true,
            o -> o.townTerritoryBreakProtectionEnabled
        )
        .documentation(
            "When true (default), town claim block-break protection can apply. Each town can also turn break protection off. "
                + "When false, town territory never blocks breaking blocks."
        )
        .add()
        .append(
            new KeyedCodec<>("TownTerritoryUseProtectionEnabled", Codec.BOOLEAN),
            (o, v) -> o.townTerritoryUseProtectionEnabled = v != null ? v : true,
            o -> o.townTerritoryUseProtectionEnabled
        )
        .documentation(
            "When true (default), town claim block-use protection can apply (containers, doors, and other uses). "
                + "Each town can also turn use protection off. When false, town territory never blocks using blocks."
        )
        .add()
        .append(
            new KeyedCodec<>("VillagerNeedsDecayPerSecond", Codec.FLOAT),
            (o, v) -> o.villagerNeedsDecayPerSecond = v,
            o -> o.villagerNeedsDecayPerSecond
        )
        .documentation(
            "Hunger points (0..100 scale) removed per second of game time at full rate; energy/fun use slightly lower "
                + "multipliers. Default 0.0525 (~32 min from 100 to 0 for hunger). Config values below 0.002 are assumed "
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
            new KeyedCodec<>("RaidMarchDebugLog", Codec.BOOLEAN),
            (o, v) -> o.raidMarchDebugLog = v,
            o -> o.raidMarchDebugLog
        )
        .documentation(
            "When true, emits raid quest march diagnostics to the server log at INFO (never to players). "
                + "Status lines are limited to once every 15 seconds per raid mob."
        )
        .add()
        .append(
            new KeyedCodec<>("VillagerAuditLogEnabled", Codec.BOOLEAN),
            (o, v) -> o.villagerAuditLogEnabled = v != null && v,
            o -> o.villagerAuditLogEnabled
        )
        .documentation(
            "When true, appends town villager death and removal events to villager_audit/{world}/audit.jsonl under the plugin data folder."
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
            "Legacy field; per-capita charter tax now uses CharterPerCapitaMinGoldPerResidentPerDay and CharterPerCapitaMaxGoldPerResidentPerDay."
        )
        .add()
        .append(
            new KeyedCodec<>("CharterTaxHappinessExponent", Codec.DOUBLE),
            (o, v) -> o.charterTaxHappinessExponent = v != null ? v : 1.25,
            o -> o.charterTaxHappinessExponent
        )
        .documentation("Legacy field; happiness-weighted tax now uses CharterHappinessTaxMinComfortRatio and CharterHappinessTaxPeakPermille.")
        .add()
        .append(
            new KeyedCodec<>("CharterHappinessTaxMinComfortRatio", Codec.DOUBLE),
            (o, v) -> o.charterHappinessTaxMinComfortRatio = v != null ? v : 0.5,
            o -> o.charterHappinessTaxMinComfortRatio
        )
        .documentation(
            "Happiness-weighted charter tax: average needs ratio (0..1) at or below this pays 0 gold; above it, tax rises on a curve to the peak."
        )
        .add()
        .append(
            new KeyedCodec<>("CharterHappinessTaxPeakPermille", Codec.INTEGER),
            (o, v) -> o.charterHappinessTaxPeakPermille = v != null ? v : 1500,
            o -> o.charterHappinessTaxPeakPermille
        )
        .documentation(
            "Happiness-weighted charter tax: at full needs, per-resident gold is TreasuryMaxGoldTaxPerVillagerPerDay * (permille/1000) "
                + "(e.g. 1500 = 150% of the linear cap)."
        )
        .add()
        .append(
            new KeyedCodec<>("CharterPerCapitaMinGoldPerResidentPerDay", Codec.INTEGER),
            (o, v) -> o.charterPerCapitaMinGoldPerResidentPerDay = v != null ? v : 5,
            o -> o.charterPerCapitaMinGoldPerResidentPerDay
        )
        .documentation("Per-capita charter tax: gold at 0% average needs (floor of the per-resident range).")
        .add()
        .append(
            new KeyedCodec<>("CharterPerCapitaMaxGoldPerResidentPerDay", Codec.INTEGER),
            (o, v) -> o.charterPerCapitaMaxGoldPerResidentPerDay = v != null ? v : 8,
            o -> o.charterPerCapitaMaxGoldPerResidentPerDay
        )
        .documentation("Per-capita charter tax: gold at 100% average needs (ceiling of the per-resident range).")
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
            new KeyedCodec<>("BreakableContainers", BreakableContainersConfig.CODEC),
            (o, v) -> o.breakableContainers = v != null ? v : new BreakableContainersConfig(),
            o -> o.breakableContainers
        )
        .documentation(
            "Bonus gold when players break eligible world crates, barrels, pots, sacks, and coffins. Nested Gold uses weighted 0/1/2 rolls."
        )
        .add()
        .append(
            new KeyedCodec<>("FloatingGift", FloatingGiftConfig.CODEC),
            (o, v) -> o.floatingGift = v != null ? v : new FloatingGiftConfig(),
            o -> o.floatingGift
        )
        .documentation("Floating gift balloon world events: spawn cadence, movement, hit radius, and lifetime.")
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
            new KeyedCodec<>("ProductionTimeMultiplier", Codec.DOUBLE),
            (o, v) -> o.productionTimeMultiplier = v != null ? v : 1.0,
            o -> o.productionTimeMultiplier
        )
        .documentation(
            "Multiplies every workplace production interval (catalog ticks): 1.0 = default, 0.5 = half the time, 2.0 = double. "
                + "Clamped to a safe positive range."
        )
        .add()
        .append(
            new KeyedCodec<>("ProductionZoneMismatchTimeMultiplier", Codec.DOUBLE),
            (o, v) -> o.productionZoneMismatchTimeMultiplier = v != null ? v : 2.0,
            o -> o.productionZoneMismatchTimeMultiplier
        )
        .documentation(
            "When a miners hut or lumbermill produces an ore or wood outside that material's preferred adventure zone, "
                + "multiply the production interval by this value (default 2.0 = twice as long). Preferred zone is unchanged."
        )
        .add()
        .append(
            new KeyedCodec<>("ProductionOfflineMultiplier", Codec.DOUBLE),
            (o, v) -> o.productionOfflineMultiplier = v != null ? v : 0.8,
            o -> o.productionOfflineMultiplier
        )
        .documentation(
            "Multiplier on offline (unloaded worker) schedule catch-up production. 0.8 = 80% of a full work-minute at live tick rate."
        )
        .add()
        .append(
            new KeyedCodec<>("ProductionOfflineCatchUpMaxMinutes", Codec.INTEGER),
            (o, v) -> o.productionOfflineCatchUpMaxMinutes = v != null ? v : 10080,
            o -> o.productionOfflineCatchUpMaxMinutes
        )
        .documentation("Max in-game minutes processed per catch-up pass (default 10080 = 7 days).")
        .add()
        .append(
            new KeyedCodec<>("ShopSpotPlayerListingPricePercent", Codec.INTEGER),
            (o, v) -> o.shopSpotPlayerListingPricePercent = v,
            o -> o.shopSpotPlayerListingPricePercent
        )
        .documentation(
            "Percent of catalog gold price buyers pay at player controlled shop spots (NPC spots use full catalog price). Default 75."
        )
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
            (o, v) -> o.pathNavNodeSpacing = v != null ? v : 3.5,
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
        .append(
            new KeyedCodec<>("GrantPlotCreatorPermissionToEveryone", Codec.BOOLEAN),
            (o, v) -> o.grantPlotCreatorPermissionToEveryone = v != null ? v : true,
            o -> o.grantPlotCreatorPermissionToEveryone
        )
        .documentation(
            "When true (default), every player may use the plot creator staff and related commands without assigning "
                + "aetherhaven.plot.creator. Set false to require that permission (or operator) explicitly."
        )
        .add()
        .append(
            new KeyedCodec<>("PlotCreatorPlayerBuildingTypesOnly", Codec.BOOLEAN),
            (o, v) -> o.plotCreatorPlayerBuildingTypesOnly = v != null ? v : true,
            o -> o.plotCreatorPlayerBuildingTypesOnly
        )
        .documentation(
            "When true (default), the plot creator building type picker only lists Decoration and Variant. "
                + "Set false to expose all building types (homes, shops, town hall, etc.) for pack authors and admins."
        )
        .add()
        .append(
            new KeyedCodec<>("BuildingEditorWriteRoot", Codec.STRING),
            (o, v) -> o.buildingEditorWriteRoot = v != null ? v : "",
            o -> o.buildingEditorWriteRoot
        )
        .documentation(
            "Optional filesystem root for the Creative building editor and festival authoring saves (must contain "
                + "Server/Aetherhaven/Buildings, Server/Aetherhaven/Festivals, and Server/Prefabs). When blank, saves "
                + "prefer a writable asset pack that already has the building/festival file "
                + "(e.g. Gradle build/resources/main), otherwise the plugin data directory."
        )
        .add()
        .append(
            new KeyedCodec<>("DialogueTalkDurationSeconds", Codec.FLOAT),
            (o, v) -> o.dialogueTalkDurationSeconds = v != null ? v : 3f,
            o -> o.dialogueTalkDurationSeconds
        )
        .documentation("Seconds villagers play a talking mouth animation when dialogue opens or a choice is picked.")
        .add()
        .append(
            new KeyedCodec<>("DialogueSpeechEnabled", Codec.BOOLEAN),
            (o, v) -> o.dialogueSpeechEnabled = v != null ? v : true,
            o -> o.dialogueSpeechEnabled
        )
        .documentation("When true (default), play Animal Crossing–style speech blips while the dialogue talk mouth animation runs.")
        .add()
        .append(
            new KeyedCodec<>("NeedsMoodLowThreshold", Codec.FLOAT),
            (o, v) -> o.needsMoodLowThreshold = v != null ? v : 40f,
            o -> o.needsMoodLowThreshold
        )
        .documentation("Lowest hunger, energy, or fun value below which villagers show an unhappy face.")
        .add()
        .append(
            new KeyedCodec<>("NeedsMoodHighThreshold", Codec.FLOAT),
            (o, v) -> o.needsMoodHighThreshold = v != null ? v : 70f,
            o -> o.needsMoodHighThreshold
        )
        .documentation("Lowest hunger, energy, or fun value at or above which villagers show a happy face.")
        .add()
        .append(
            new KeyedCodec<>("ReputationWaveMinReputation", Codec.INTEGER),
            (o, v) -> o.reputationWaveMinReputation = v != null ? v : 75,
            o -> o.reputationWaveMinReputation
        )
        .documentation("Minimum villager reputation toward a player before idle NPCs may wave when nearby.")
        .add()
        .append(
            new KeyedCodec<>("ReputationWaveNearbyRangeBlocks", Codec.FLOAT),
            (o, v) -> o.reputationWaveNearbyRangeBlocks = v != null ? v : 8f,
            o -> o.reputationWaveNearbyRangeBlocks
        )
        .documentation("Horizontal distance (blocks) within which reputation waves are considered.")
        .add()
        .append(
            new KeyedCodec<>("ReputationWaveCheckIntervalSeconds", Codec.FLOAT),
            (o, v) -> o.reputationWaveCheckIntervalSeconds = v != null ? v : 2.5f,
            o -> o.reputationWaveCheckIntervalSeconds
        )
        .documentation("Seconds between proximity/reputation checks per villager.")
        .add()
        .append(
            new KeyedCodec<>("ReputationWaveChancePerCheck", Codec.FLOAT),
            (o, v) -> o.reputationWaveChancePerCheck = v != null ? v : 0.12f,
            o -> o.reputationWaveChancePerCheck
        )
        .documentation("Probability (0..1) of waving when a qualifying player is nearby on each check.")
        .add()
        .append(
            new KeyedCodec<>("ReputationWaveDurationSeconds", Codec.FLOAT),
            (o, v) -> o.reputationWaveDurationSeconds = v != null ? v : 2.5f,
            o -> o.reputationWaveDurationSeconds
        )
        .documentation("Seconds the Wave emote plays before stopping (vanilla emote loops).")
        .add()
        .append(
            new KeyedCodec<>("ReputationWaveCooldownSeconds", Codec.FLOAT),
            (o, v) -> o.reputationWaveCooldownSeconds = v != null ? v : 45f,
            o -> o.reputationWaveCooldownSeconds
        )
        .documentation("Seconds before the same villager may wave again.")
        .add()
        .append(
            new KeyedCodec<>("CommunityMarketplace", CommunityMarketplaceConfig.CODEC),
            (o, v) -> o.communityMarketplace = v != null ? v : new CommunityMarketplaceConfig(),
            o -> o.communityMarketplace
        )
        .documentation("Remote community building browser (manifest browse, on-demand download, optional submit).")
        .add()
        .append(
            new KeyedCodec<>("SupportUpload", SupportUploadConfig.CODEC),
            (o, v) -> o.supportUpload = v != null ? v : new SupportUploadConfig(),
            o -> o.supportUpload
        )
        .documentation("Upload mod save data and logs for remote debugging (/aetherhaven support upload).")
        .add()
        .build();

    private int constructionBlocksPerTick = 8;
    private long constructionMinIntervalMs = 25L;
    private long assemblyGameDayLengthMsOverride = 0L;
    private boolean passivePlotAssembly = true;
    private int assemblySectionChunkSizeBlocks = 15;
    private boolean ignoreVillagerRequirement = false;
    private int defaultTerritoryChunkRadius = 6;
    private long territoryExpansionFirstClaimCostGold = 20L;
    private long territoryExpansionClaimCostIncrementGold = 20L;
    private boolean territoryExpansionClaimLimitEnabled = false;
    private int maxTerritoryExpansionClaimBlocks = 0;
    /** When false, town territory never cancels BreakBlockEvent. Default true. */
    private boolean townTerritoryBreakProtectionEnabled = true;
    /** When false, town territory never cancels UseBlockEvent. Default true. */
    private boolean townTerritoryUseProtectionEnabled = true;
    /** Hunger points (0..100 scale) drained per second of game time; energy/fun use lower multipliers in code. */
    private float villagerNeedsDecayPerSecond = DEFAULT_VILLAGER_NEEDS_DECAY_PER_SECOND;

    /** Inclusive start hour for the daily morning inn refresh (game clock, {@link com.hypixel.hytale.server.core.modules.time.WorldTimeResource}). */
    private int innPoolMorningStartHour = 5;
    /** Exclusive end hour (e.g. 15 means 5:00-14:59). */
    private int innPoolMorningEndHour = 15;

    private boolean villagerScheduleEnabled = true;
    private boolean villagerScheduleDebugLog = false;
    private boolean raidMarchDebugLog = false;
    private boolean villagerAuditLogEnabled = true;

    /** When true, plot creator staff and commands do not require explicit aetherhaven.plot.creator grants. */
    private boolean grantPlotCreatorPermissionToEveryone = true;

    /** When true, plot creator building type picker only offers decoration and variant. */
    private boolean plotCreatorPlayerBuildingTypesOnly = true;

    /** Optional root for building editor staff overwrites (blank = auto pack root / data dir). */
    private String buildingEditorWriteRoot = "";

    /** Max gold coins per resident per morning tax tick (needs-scaled). */
    private int treasuryMaxGoldTaxPerVillagerPerDay = 10;

    /** Chance per ore block break to drop an extra geode item. */
    private double geodeDropChancePerOreBreak = 0.025;

    private boolean geodeOreUseBlocksOresCategory = true;

    /** Comma-separated; default filled in getter if blank. */
    private String geodeOreSubcategories = "";

    private String geodeOreExcludedSubcategories = "";

    private String geodeExtraOreGatherTypes = "";

    private String geodeExtraOreBlockTypeIds = "";

    private double charterTaxPerCapitaFlatFraction = 0.35;
    private double charterTaxHappinessExponent = 1.25;
    private double charterHappinessTaxMinComfortRatio = 0.5;
    private int charterHappinessTaxPeakPermille = 1500;
    private int charterPerCapitaMinGoldPerResidentPerDay = 5;
    private int charterPerCapitaMaxGoldPerResidentPerDay = 8;
    private int founderMonumentTaxPermille = 1100;

    private LootChestConfig lootChest = new LootChestConfig();
    private BreakableContainersConfig breakableContainers = new BreakableContainersConfig();
    private FloatingGiftConfig floatingGift = new FloatingGiftConfig();
    private JewelryConfig jewelry = new JewelryConfig();

    private int feastTaxBonusPermille = 1250;
    private int feastNeedsDecayScalePermille = 650;
    private int feastGatherTimeoutSeconds = 120;

    /** Multiplier on catalog production ticks (workplace outputs). Default 1.0. */
    private double productionTimeMultiplier = 1.0;
    /** Wrong-zone ore/wood production interval multiplier. Default 2.0. */
    private double productionZoneMismatchTimeMultiplier = 2.0;
    /** Offline catch-up rate vs a full work-minute at live entity tick rate. Default 0.8. */
    private double productionOfflineMultiplier = 0.8;
    /** Max in-game minutes per offline catch-up pass. Default 7 days. */
    private int productionOfflineCatchUpMaxMinutes = 10080;
    /** Buyer price at player shop spots as percent of catalog price (1-100). Default 75. */
    private int shopSpotPlayerListingPricePercent = 75;

    private double pathToolNodeBlockYOffset = 1.0;
    private int pathToolSamplesPerBlock = 2;
    private int pathToolHalfWidth = 2;
    private int pathToolRayStartAboveY = 6;
    private int pathToolMaxRayDown = 128;
    private String pathToolReplaceableBlockIds = "";
    private String pathToolReplaceableResourceTypeIds = "";
    private String pathToolStyles = PathToolStyleDefinition.DEFAULT_JSON;
    private boolean pathNavEnabled = true;
    private double pathNavNodeSpacing = 3.5;
    private double pathNavSnapRadius = 8.0;
    private double pathNavJunctionEps = 1.25;
    private double pathNavEndpointGateRadius = 32.0;
    private int pathNavMaxNodesPerTown = 6000;
    private boolean pathNavPreferIfShorterOnly = false;
    private boolean pathNavPathfindingLog = false;

    private float dialogueTalkDurationSeconds = 3f;
    private boolean dialogueSpeechEnabled = true;
    private float needsMoodLowThreshold = 40f;
    private float needsMoodHighThreshold = 70f;

    private int reputationWaveMinReputation = 75;
    private float reputationWaveNearbyRangeBlocks = 8f;
    private float reputationWaveCheckIntervalSeconds = 2.5f;
    private float reputationWaveChancePerCheck = 0.12f;
    private float reputationWaveDurationSeconds = 2.5f;
    private float reputationWaveCooldownSeconds = 45f;

    private CommunityMarketplaceConfig communityMarketplace = new CommunityMarketplaceConfig();
    private SupportUploadConfig supportUpload = new SupportUploadConfig();

    @Nonnull
    public CommunityMarketplaceConfig getCommunityMarketplace() {
        return communityMarketplace != null ? communityMarketplace : new CommunityMarketplaceConfig();
    }

    @Nonnull
    public SupportUploadConfig getSupportUpload() {
        return supportUpload != null ? supportUpload : new SupportUploadConfig();
    }

    public int getConstructionBlocksPerTick() {
        return constructionBlocksPerTick;
    }

    public long getConstructionMinIntervalMs() {
        return constructionMinIntervalMs;
    }

    /** 0 = derive from world day+night duration. */
    public long getAssemblyGameDayLengthMsOverride() {
        return Math.max(0L, assemblyGameDayLengthMsOverride);
    }

    public boolean isPassivePlotAssemblyEnabled() {
        return passivePlotAssembly;
    }

    /** Max prefab-local extent along one axis before that axis gains another assembly section. */
    public int getAssemblySectionChunkSizeBlocks() {
        return Math.max(1, assemblySectionChunkSizeBlocks);
    }

    public boolean isIgnoreVillagerRequirement() {
        return ignoreVillagerRequirement;
    }

    public int getDefaultTerritoryChunkRadius() {
        return Math.max(1, defaultTerritoryChunkRadius);
    }

    public long getTerritoryExpansionFirstClaimCostGold() {
        return Math.max(0L, territoryExpansionFirstClaimCostGold);
    }

    public long getTerritoryExpansionClaimCostIncrementGold() {
        return Math.max(0L, territoryExpansionClaimCostIncrementGold);
    }

    public boolean isTerritoryExpansionClaimLimitEnabled() {
        return territoryExpansionClaimLimitEnabled;
    }

    public boolean isTownTerritoryBreakProtectionEnabled() {
        return townTerritoryBreakProtectionEnabled;
    }

    public boolean isTownTerritoryUseProtectionEnabled() {
        return townTerritoryUseProtectionEnabled;
    }

    public int getMaxTerritoryExpansionClaimBlocks() {
        return Math.max(0, maxTerritoryExpansionClaimBlocks);
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
        float v =
            villagerNeedsDecayPerSecond > 0f ? villagerNeedsDecayPerSecond : DEFAULT_VILLAGER_NEEDS_DECAY_PER_SECOND;
        if (v > 0f && v < 0.002f) {
            v *= 100f;
        }
        if (v >= 20f) {
            return DEFAULT_VILLAGER_NEEDS_DECAY_PER_SECOND;
        }
        return v;
    }

    public float getDialogueTalkDurationSeconds() {
        float v = dialogueTalkDurationSeconds;
        return v > 0f ? v : 3f;
    }

    public boolean isDialogueSpeechEnabled() {
        return dialogueSpeechEnabled;
    }

    public float getNeedsMoodLowThreshold() {
        float v = needsMoodLowThreshold;
        if (v < 0f) {
            return 0f;
        }
        return Math.min(v, VillagerNeeds.MAX);
    }

    public float getNeedsMoodHighThreshold() {
        float v = needsMoodHighThreshold;
        float low = getNeedsMoodLowThreshold();
        if (v <= low) {
            return Math.min(low + 1f, VillagerNeeds.MAX);
        }
        return Math.min(v, VillagerNeeds.MAX);
    }

    public int getReputationWaveMinReputation() {
        int v = reputationWaveMinReputation;
        return Math.max(0, Math.min(VillagerReputationService.MAX_REPUTATION, v));
    }

    public float getReputationWaveNearbyRangeBlocks() {
        float v = reputationWaveNearbyRangeBlocks;
        return v > 0f ? v : 8f;
    }

    public float getReputationWaveCheckIntervalSeconds() {
        float v = reputationWaveCheckIntervalSeconds;
        return v > 0f ? v : 2.5f;
    }

    public float getReputationWaveChancePerCheck() {
        float v = reputationWaveChancePerCheck;
        if (v < 0f) {
            return 0f;
        }
        return Math.min(v, 1f);
    }

    public float getReputationWaveDurationSeconds() {
        float v = reputationWaveDurationSeconds;
        return v > 0f ? v : 2.5f;
    }

    public float getReputationWaveCooldownSeconds() {
        float v = reputationWaveCooldownSeconds;
        return v > 0f ? v : 45f;
    }

    public boolean isVillagerScheduleEnabled() {
        return villagerScheduleEnabled;
    }

    public boolean isVillagerScheduleDebugLog() {
        return villagerScheduleDebugLog;
    }

    public boolean isRaidMarchDebugLog() {
        return raidMarchDebugLog;
    }

    public boolean isVillagerAuditLogEnabled() {
        return villagerAuditLogEnabled;
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

    /** In (0, 1): at or below this average comfort ratio, happiness-weighted tax pays 0 for that resident. */
    public double getCharterHappinessTaxMinComfortRatio() {
        double v = charterHappinessTaxMinComfortRatio;
        if (v <= 0.0) {
            return 0.5;
        }
        if (v >= 0.999) {
            return 0.99;
        }
        return v;
    }

    /** Permille on {@link #getTreasuryMaxGoldTaxPerVillagerPerDay} at full comfort (1000 = same as linear cap). Clamped 1000..3000. */
    public int getCharterHappinessTaxPeakPermille() {
        int v = charterHappinessTaxPeakPermille;
        if (v < 1000) {
            return 1000;
        }
        return Math.min(v, 3000);
    }

    /** Per-capita policy floor per resident per morning (clamped 0..9999). */
    public int getCharterPerCapitaMinGoldPerResidentPerDay() {
        int v = charterPerCapitaMinGoldPerResidentPerDay;
        if (v < 0) {
            return 0;
        }
        return Math.min(v, 9999);
    }

    /** Per-capita policy ceiling per resident per morning; forced to be at least the min getter returns. */
    public int getCharterPerCapitaMaxGoldPerResidentPerDay() {
        int mn = getCharterPerCapitaMinGoldPerResidentPerDay();
        int v = charterPerCapitaMaxGoldPerResidentPerDay;
        if (v < mn) {
            return mn;
        }
        return Math.min(v, 9999);
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
    public BreakableContainersConfig getBreakableContainers() {
        return breakableContainers != null ? breakableContainers : new BreakableContainersConfig();
    }

    @Nonnull
    public JewelryConfig getJewelry() {
        return jewelry != null ? jewelry : new JewelryConfig();
    }

    public boolean isFloatingGiftEnabled() {
        return getFloatingGift().isEnabled();
    }

    @Nonnull
    public FloatingGiftConfig getFloatingGift() {
        return floatingGift != null ? floatingGift : new FloatingGiftConfig();
    }

    public double getFloatingGiftSpawnRadiusBlocks() {
        return getFloatingGift().getSpawnRadiusBlocks();
    }

    public double getFloatingGiftSpawnHeightOffsetBlocks() {
        return getFloatingGift().getSpawnHeightOffsetBlocks();
    }

    public double getFloatingGiftSpawnIntervalDaysMin() {
        return getFloatingGift().getSpawnIntervalDaysMin();
    }

    public double getFloatingGiftSpawnIntervalDaysMax() {
        return getFloatingGift().getSpawnIntervalDaysMax();
    }

    public double getFloatingGiftMoveSpeedBlocksPerSec() {
        return getFloatingGift().getMoveSpeedBlocksPerSec();
    }

    public double getFloatingGiftFallSpeedBlocksPerSec() {
        return getFloatingGift().getFallSpeedBlocksPerSec();
    }

    public double getFloatingGiftMaxLifeSeconds() {
        return getFloatingGift().getMaxLifeSeconds();
    }

    public double getFloatingGiftPopDurationSeconds() {
        return getFloatingGift().getPopDurationSeconds();
    }

    public double getFloatingGiftPopHoldLatchSeconds() {
        return getFloatingGift().getPopHoldLatchSeconds();
    }

    public double getFloatingGiftProjectileHitRadiusBlocks() {
        return getFloatingGift().getProjectileHitRadiusBlocks();
    }

    public int getFloatingGiftMaxActivePerWorld() {
        return getFloatingGift().getMaxActivePerWorld();
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

    /**
     * Multiplier applied to catalog production tick counts (below 1 = faster, above 1 = slower). Clamped to the range
     * 0.05–100; NaN and non-positive stored values fall back to 1.0.
     */
    public boolean isGrantPlotCreatorPermissionToEveryone() {
        return grantPlotCreatorPermissionToEveryone;
    }

    public boolean isPlotCreatorPlayerBuildingTypesOnly() {
        return plotCreatorPlayerBuildingTypesOnly;
    }

    @Nonnull
    public String getBuildingEditorWriteRoot() {
        return buildingEditorWriteRoot != null ? buildingEditorWriteRoot : "";
    }

    public int getShopSpotPlayerListingPricePercent() {
        int p = shopSpotPlayerListingPricePercent;
        if (p < 1) {
            return 1;
        }
        if (p > 100) {
            return 100;
        }
        return p;
    }

    public double getProductionTimeMultiplier() {
        double v = productionTimeMultiplier;
        if (Double.isNaN(v) || v <= 0.0) {
            return 1.0;
        }
        return Math.max(0.05, Math.min(100.0, v));
    }

    public double getProductionZoneMismatchTimeMultiplier() {
        double v = productionZoneMismatchTimeMultiplier;
        if (Double.isNaN(v) || v <= 0.0) {
            return 2.0;
        }
        return Math.max(1.0, Math.min(100.0, v));
    }

    public double getProductionOfflineMultiplier() {
        double v = productionOfflineMultiplier;
        if (Double.isNaN(v)) {
            return 0.8;
        }
        return Math.max(0.0, Math.min(2.0, v));
    }

    public int getProductionOfflineCatchUpMaxMinutes() {
        int v = productionOfflineCatchUpMaxMinutes;
        if (v <= 0) {
            return 10080;
        }
        return Math.min(v, 525600);
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

    public void setPathToolStyleDefinitions(@Nonnull List<PathToolStyleDefinition> definitions) {
        this.pathToolStyles = PathToolStyleDefinition.serializeList(definitions);
    }

    @Nonnull
    public String getPathToolStylesJson() {
        return pathToolStyles != null && !pathToolStyles.isBlank() ? pathToolStyles : PathToolStyleDefinition.DEFAULT_JSON;
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

    public double getLootChestPlotBlueprintChance() {
        double v = getLootChest().getPlotBlueprint().getChance();
        if (v < 0.0) {
            return 0.0;
        }
        return Math.min(v, 1.0);
    }

    public double getLootChestPropChance() {
        double v = getLootChest().getProp().getChance();
        if (v < 0.0) {
            return 0.0;
        }
        return Math.min(v, 1.0);
    }

    public double getLootChestBlockPaletteChance() {
        double v = getLootChest().getBlockPalette().getChance();
        if (v < 0.0) {
            return 0.0;
        }
        return Math.min(v, 1.0);
    }

    public double getLootChestGaiaShardChance() {
        double v = getLootChest().getGaiaDraughtBonuses().getShardChance();
        if (v < 0.0) {
            return 0.0;
        }
        return Math.min(v, 1.0);
    }

    @Nonnull
    public String getLootChestGaiaShardItemId() {
        return getLootChest().getGaiaDraughtBonuses().getShardItemId().trim();
    }

    public double getLootChestGaiaCatalystChance() {
        double v = getLootChest().getGaiaDraughtBonuses().getCatalystChance();
        if (v < 0.0) {
            return 0.0;
        }
        return Math.min(v, 1.0);
    }

    @Nonnull
    public String getLootChestGaiaCatalystItemId() {
        return getLootChest().getGaiaDraughtBonuses().getCatalystItemId().trim();
    }

    public double getLootChestHeartberryChance() {
        double v = getLootChest().getHeartberryBonus().getChance();
        if (v < 0.0) {
            return 0.0;
        }
        return Math.min(v, 1.0);
    }

    @Nonnull
    public String getLootChestHeartberryItemId() {
        return getLootChest().getHeartberryBonus().getItemId().trim();
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

    public double getJewelryShopPriceMultiplier(@Nonnull com.hexvane.aetherhaven.jewelry.JewelryRarity rarity) {
        return getJewelry().getShopPriceMultipliers().forRarity(rarity);
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

    /**
     * Applies gameplay fields edited from the Town Journal Settings UI. Values are clamped to safe ranges consistent
     * with getters. Most reads use live config, so changes take effect without restarting the server.
     */
    public void applyTownJournalGameplayTuning(
        boolean passivePlotAssembly,
        int constructionBlocksPerTickRaw,
        long constructionMinIntervalMsRaw,
        double geodeDropChanceRaw,
        double chestJewelryChanceRaw,
        double goldChestChanceRaw,
        int goldChestMinRaw,
        int goldChestMaxRaw,
        int breakableWeightNoneRaw,
        int breakableWeightOneRaw,
        int breakableWeightTwoRaw,
        boolean floatingGiftEnabled,
        double floatingGiftIntervalDaysMinRaw,
        double floatingGiftIntervalDaysMaxRaw,
        int shopSpotPlayerListingPricePercentRaw
    ) {
        this.passivePlotAssembly = passivePlotAssembly;
        this.constructionBlocksPerTick = Math.max(1, constructionBlocksPerTickRaw);
        this.constructionMinIntervalMs = Math.max(0L, constructionMinIntervalMsRaw);
        double geode = geodeDropChanceRaw;
        if (Double.isNaN(geode) || geode < 0.0) {
            geode = 0.0;
        }
        this.geodeDropChancePerOreBreak = Math.min(1.0, geode);
        if (this.lootChest == null) {
            this.lootChest = new LootChestConfig();
        }
        this.lootChest.applyJournalJewelryChance(chestJewelryChanceRaw);
        this.lootChest.getGold().applyJournalTuning(goldChestChanceRaw, goldChestMinRaw, goldChestMaxRaw);
        if (this.breakableContainers == null) {
            this.breakableContainers = new BreakableContainersConfig();
        }
        this.breakableContainers
            .getGold()
            .applyJournalTuning(breakableWeightNoneRaw, breakableWeightOneRaw, breakableWeightTwoRaw);
        if (this.floatingGift == null) {
            this.floatingGift = new FloatingGiftConfig();
        }
        int shopPct = shopSpotPlayerListingPricePercentRaw;
        if (shopPct < 1) {
            shopPct = 1;
        } else if (shopPct > 100) {
            shopPct = 100;
        }
        this.shopSpotPlayerListingPricePercent = shopPct;
        this.floatingGift.applyJournalSpawnCadence(
            floatingGiftEnabled,
            floatingGiftIntervalDaysMinRaw,
            floatingGiftIntervalDaysMaxRaw
        );
    }

    /**
     * Copies every stored field from {@code source} into this instance. Intended for resetting the live server config
     * in place using {@link #defaults()} or another template (same config object the plugin already holds).
     */
    public void copyStateFrom(@Nonnull AetherhavenPluginConfig o) {
        this.constructionBlocksPerTick = o.constructionBlocksPerTick;
        this.constructionMinIntervalMs = o.constructionMinIntervalMs;
        this.assemblyGameDayLengthMsOverride = o.assemblyGameDayLengthMsOverride;
        this.passivePlotAssembly = o.passivePlotAssembly;
        this.assemblySectionChunkSizeBlocks = o.assemblySectionChunkSizeBlocks;
        this.ignoreVillagerRequirement = o.ignoreVillagerRequirement;
        this.defaultTerritoryChunkRadius = o.defaultTerritoryChunkRadius;
        this.territoryExpansionFirstClaimCostGold = o.territoryExpansionFirstClaimCostGold;
        this.territoryExpansionClaimCostIncrementGold = o.territoryExpansionClaimCostIncrementGold;
        this.territoryExpansionClaimLimitEnabled = o.territoryExpansionClaimLimitEnabled;
        this.maxTerritoryExpansionClaimBlocks = o.maxTerritoryExpansionClaimBlocks;
        this.townTerritoryBreakProtectionEnabled = o.townTerritoryBreakProtectionEnabled;
        this.townTerritoryUseProtectionEnabled = o.townTerritoryUseProtectionEnabled;
        this.villagerNeedsDecayPerSecond = o.villagerNeedsDecayPerSecond;
        this.innPoolMorningStartHour = o.innPoolMorningStartHour;
        this.innPoolMorningEndHour = o.innPoolMorningEndHour;
        this.villagerScheduleEnabled = o.villagerScheduleEnabled;
        this.villagerScheduleDebugLog = o.villagerScheduleDebugLog;
        this.raidMarchDebugLog = o.raidMarchDebugLog;
        this.villagerAuditLogEnabled = o.villagerAuditLogEnabled;
        this.grantPlotCreatorPermissionToEveryone = o.grantPlotCreatorPermissionToEveryone;
        this.plotCreatorPlayerBuildingTypesOnly = o.plotCreatorPlayerBuildingTypesOnly;
        this.buildingEditorWriteRoot = o.buildingEditorWriteRoot != null ? o.buildingEditorWriteRoot : "";
        this.treasuryMaxGoldTaxPerVillagerPerDay = o.treasuryMaxGoldTaxPerVillagerPerDay;
        this.geodeDropChancePerOreBreak = o.geodeDropChancePerOreBreak;
        this.geodeOreUseBlocksOresCategory = o.geodeOreUseBlocksOresCategory;
        this.geodeOreSubcategories = o.geodeOreSubcategories != null ? o.geodeOreSubcategories : "";
        this.geodeOreExcludedSubcategories = o.geodeOreExcludedSubcategories != null ? o.geodeOreExcludedSubcategories : "";
        this.geodeExtraOreGatherTypes = o.geodeExtraOreGatherTypes != null ? o.geodeExtraOreGatherTypes : "";
        this.geodeExtraOreBlockTypeIds = o.geodeExtraOreBlockTypeIds != null ? o.geodeExtraOreBlockTypeIds : "";
        this.charterTaxPerCapitaFlatFraction = o.charterTaxPerCapitaFlatFraction;
        this.charterTaxHappinessExponent = o.charterTaxHappinessExponent;
        this.charterHappinessTaxMinComfortRatio = o.charterHappinessTaxMinComfortRatio;
        this.charterHappinessTaxPeakPermille = o.charterHappinessTaxPeakPermille;
        this.charterPerCapitaMinGoldPerResidentPerDay = o.charterPerCapitaMinGoldPerResidentPerDay;
        this.charterPerCapitaMaxGoldPerResidentPerDay = o.charterPerCapitaMaxGoldPerResidentPerDay;
        this.founderMonumentTaxPermille = o.founderMonumentTaxPermille;
        this.lootChest = o.lootChest != null ? o.lootChest : new LootChestConfig();
        this.breakableContainers = o.breakableContainers != null ? o.breakableContainers : new BreakableContainersConfig();
        this.floatingGift = o.floatingGift != null ? o.floatingGift : new FloatingGiftConfig();
        this.jewelry = o.jewelry != null ? o.jewelry : new JewelryConfig();
        this.feastTaxBonusPermille = o.feastTaxBonusPermille;
        this.feastNeedsDecayScalePermille = o.feastNeedsDecayScalePermille;
        this.feastGatherTimeoutSeconds = o.feastGatherTimeoutSeconds;
        this.productionTimeMultiplier = o.productionTimeMultiplier;
        this.productionZoneMismatchTimeMultiplier = o.productionZoneMismatchTimeMultiplier;
        this.productionOfflineMultiplier = o.productionOfflineMultiplier;
        this.productionOfflineCatchUpMaxMinutes = o.productionOfflineCatchUpMaxMinutes;
        this.shopSpotPlayerListingPricePercent = o.shopSpotPlayerListingPricePercent;
        this.pathToolNodeBlockYOffset = o.pathToolNodeBlockYOffset;
        this.pathToolSamplesPerBlock = o.pathToolSamplesPerBlock;
        this.pathToolHalfWidth = o.pathToolHalfWidth;
        this.pathToolRayStartAboveY = o.pathToolRayStartAboveY;
        this.pathToolMaxRayDown = o.pathToolMaxRayDown;
        this.pathToolReplaceableBlockIds = o.pathToolReplaceableBlockIds != null ? o.pathToolReplaceableBlockIds : "";
        this.pathToolReplaceableResourceTypeIds =
            o.pathToolReplaceableResourceTypeIds != null ? o.pathToolReplaceableResourceTypeIds : "";
        this.pathToolStyles = o.pathToolStyles != null && !o.pathToolStyles.isBlank() ? o.pathToolStyles : PathToolStyleDefinition.DEFAULT_JSON;
        this.pathNavEnabled = o.pathNavEnabled;
        this.pathNavNodeSpacing = o.pathNavNodeSpacing;
        this.pathNavSnapRadius = o.pathNavSnapRadius;
        this.pathNavJunctionEps = o.pathNavJunctionEps;
        this.pathNavEndpointGateRadius = o.pathNavEndpointGateRadius;
        this.pathNavMaxNodesPerTown = o.pathNavMaxNodesPerTown;
        this.pathNavPreferIfShorterOnly = o.pathNavPreferIfShorterOnly;
        this.pathNavPathfindingLog = o.pathNavPathfindingLog;
        this.dialogueTalkDurationSeconds = o.dialogueTalkDurationSeconds;
        this.dialogueSpeechEnabled = o.dialogueSpeechEnabled;
        this.needsMoodLowThreshold = o.needsMoodLowThreshold;
        this.needsMoodHighThreshold = o.needsMoodHighThreshold;
        this.reputationWaveMinReputation = o.reputationWaveMinReputation;
        this.reputationWaveNearbyRangeBlocks = o.reputationWaveNearbyRangeBlocks;
        this.reputationWaveCheckIntervalSeconds = o.reputationWaveCheckIntervalSeconds;
        this.reputationWaveChancePerCheck = o.reputationWaveChancePerCheck;
        this.reputationWaveDurationSeconds = o.reputationWaveDurationSeconds;
        this.reputationWaveCooldownSeconds = o.reputationWaveCooldownSeconds;
        this.communityMarketplace = o.communityMarketplace != null ? o.communityMarketplace : new CommunityMarketplaceConfig();
        this.supportUpload = o.supportUpload != null ? o.supportUpload : new SupportUploadConfig();
    }

    @Nonnull
    public static AetherhavenPluginConfig defaults() {
        return new AetherhavenPluginConfig();
    }
}
