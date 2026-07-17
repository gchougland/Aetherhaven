package com.hexvane.aetherhaven.town;

import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.gaiadraught.GaiaDraughtState;
import com.hexvane.aetherhaven.production.PlotProductionState;
import com.hexvane.aetherhaven.restaurant.PlotRestaurantState;
import com.hexvane.aetherhaven.production.ProductionCatalog;
import com.hexvane.aetherhaven.production.ProductionEffectiveCatalog;
import com.hexvane.aetherhaven.production.WorkplaceProductionUpgrades;
import com.hexvane.aetherhaven.production.WorkplaceUnlockCatalog;
import com.hexvane.aetherhaven.reputation.VillagerReputationEntry;
import com.hypixel.hytale.logger.HytaleLogger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class TownRecord {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @SerializedName("townId")
    private String townId;

    @SerializedName("ownerUuid")
    private String ownerUuid;

    @SerializedName("worldName")
    private String worldName;

    @SerializedName("charterX")
    private int charterX;

    @SerializedName("charterY")
    private int charterY;

    @SerializedName("charterZ")
    private int charterZ;

    @SerializedName("tier")
    private int tier;

    @SerializedName("territoryChunkRadius")
    private int territoryChunkRadius;

    @SerializedName("createdTimeEpochMs")
    private long createdTimeEpochMs;

    @SerializedName("elderSpawned")
    private boolean elderSpawned;

    /** Legacy saves only; migrated into {@link #plotInstances} on load. */
    @SerializedName("plotFootprints")
    @Nullable
    private List<PlotFootprintRecord> plotFootprints;

    @SerializedName("plotInstances")
    private List<PlotInstance> plotInstances = new ArrayList<>();

    @SerializedName("wallSegments")
    private List<WallSegmentRecord> wallSegments = new ArrayList<>();

    @SerializedName("activeQuestIds")
    private List<String> activeQuestIds = new ArrayList<>();

    @SerializedName("completedQuestIds")
    private List<String> completedQuestIds = new ArrayList<>();

    /**
     * Per-quest objective completion for non-{@code journal} objectives. Outer key: quest id; inner: objective id.
     */
    @Nullable
    @SerializedName("questObjectiveProgress")
    private Map<String, Map<String, Boolean>> questObjectiveProgress;

    @Nullable
    @SerializedName("elderEntityUuid")
    private String elderEntityUuid;

    @SerializedName("innActive")
    private boolean innActive;

    @Nullable
    @SerializedName("innkeeperEntityUuid")
    private String innkeeperEntityUuid;

    /**
     * Up to two inn visitor NPC entity UUID strings (see {@link #getInnPoolNpcIds}).
     * Cleared when innkeeper first spawns; repopulated by {@link com.hexvane.aetherhaven.inn.InnPoolSystem}.
     */
    @SerializedName("innPoolNpcIds")
    private List<String> innPoolNpcIds = new ArrayList<>();

    /** Entity UUIDs that must not be removed on inn pool refresh (lock-on-accept). */
    @SerializedName("innLockedEntityUuids")
    private List<String> innLockedEntityUuids = new ArrayList<>();

    /** Last inn pool refresh instant in world game time (ISO-8601), not wall clock. Legacy; see {@link #innPoolLastMorningGameDate}. */
    @Nullable
    @SerializedName("innPoolLastRefreshGameTime")
    private String innPoolLastRefreshGameTime;

    /** Calendar game date (UTC, YYYY-MM-DD) when we last ran the morning inn shuffle; kept for saves readability. */
    @Nullable
    @SerializedName("innPoolLastMorningGameDate")
    private String innPoolLastMorningGameDate;

    /**
     * Game calendar epoch day ({@link LocalDate#toEpochDay()}) when we last ran the morning inn shuffle.
     * Compared to current game date so {@code /time} to the next morning on a new day triggers refresh.
     */
    @Nullable
    @SerializedName("innPoolLastMorningEpochDay")
    private Long innPoolLastMorningEpochDay;

    /**
     * Game calendar epoch day when we last ran the morning inn visitor fill (spawn into open slots).
     * Compared to current game date so fill runs at most once per dawn after refresh.
     */
    @Nullable
    @SerializedName("innPoolLastFillEpochDay")
    private Long innPoolLastFillEpochDay;

    /**
     * NPC role ids (e.g. {@link com.hexvane.aetherhaven.AetherhavenConstants#NPC_MERCHANT}) that must never be chosen
     * when filling the inn visitor pool (e.g. after promotion to a permanent town role).
     */
    @SerializedName("innVisitorPoolExcludedRoleIds")
    private LinkedHashSet<String> innVisitorPoolExcludedRoleIds = new LinkedHashSet<>();

    @Nullable
    @SerializedName("guildMasterEntityUuid")
    private String guildMasterEntityUuid;

    @SerializedName("guildHallActive")
    private boolean guildHallActive;

    /** Entity UUID strings for unhired guild hall adventurers (dawn cycled). */
    @SerializedName("guildHallAdventurerNpcIds")
    private List<String> guildHallAdventurerNpcIds = new ArrayList<>();

    @Nullable
    @SerializedName("guildHallLastMorningEpochDay")
    private Long guildHallLastMorningEpochDay;

    /** Slot indices chosen for today's ~50% guild hall adventurer fill. */
    @SerializedName("guildHallAdventurerFilledSlots")
    private List<Integer> guildHallAdventurerFilledSlots = new ArrayList<>();

    /** Maps adventurer entity UUID string to spawn slot index for the current day. */
    @SerializedName("guildHallAdventurerSlotByNpcId")
    private Map<String, Integer> guildHallAdventurerSlotByNpcId = new LinkedHashMap<>();

    /** Hired guards not yet promoted to tax paying citizens. */
    @SerializedName("hiredGuardRecords")
    private List<HiredGuardRecord> hiredGuardRecords = new ArrayList<>();

    /** Portal tourists and invited residents from the tourist portal. */
    @SerializedName("touristRecords")
    private List<com.hexvane.aetherhaven.tourist.TouristRecord> touristRecords = new ArrayList<>();

    /** Game epoch day for which {@link #touristPlannedSpawnEpochMinutes} was generated. */
    @SerializedName("touristSpawnPlannedDayEpochDay")
    private long touristSpawnPlannedDayEpochDay = Long.MIN_VALUE;

    /** Planned tourist spawn times for the town (epoch minutes); shared across all portals in the town. */
    @SerializedName("touristPlannedSpawnEpochMinutes")
    private List<Long> touristPlannedSpawnEpochMinutes = new ArrayList<>();

    @SerializedName("touristExecutedSpawnEpochMinutes")
    private List<Long> touristExecutedSpawnEpochMinutes = new ArrayList<>();

    /** Entity UUID of guard targeted by active {@link com.hexvane.aetherhaven.AetherhavenConstants#QUEST_HOUSE_GUARD}. */
    @Nullable
    @SerializedName("guardHouseQuestTargetEntityUuid")
    private String guardHouseQuestTargetEntityUuid;

    /** Active entity-scoped quests: quest id → target entity UUID string. */
    @SerializedName("questTargetEntityUuidByQuestId")
    private Map<String, String> questTargetEntityUuidByQuestId;

    /** Per-entity quest completions for {@code repeat.mode: per_entity} quests. */
    @SerializedName("questCompletedEntityUuids")
    private Map<String, List<String>> questCompletedEntityUuids;

    /** Shared town treasury balance (gold coins); all treasury blocks in this town read/write this. */
    @SerializedName("treasuryGoldCoinCount")
    private long treasuryGoldCoinCount;

    /** Per player gold stored in player shop safes from their listing sales. Key: player UUID string. */
    @Nullable
    @SerializedName("playerShopSafeGoldByPlayerUuid")
    private Map<String, Long> playerShopSafeGoldByPlayerUuid;

    /** Dawn-aligned game epoch day when daily treasury tithe was last applied ({@link com.hexvane.aetherhaven.reputation.VillagerReputationService#currentGameEpochDay}). */
    @Nullable
    @SerializedName("treasuryLastTaxEpochDay")
    private Long treasuryLastTaxEpochDay;

    /**
     * {@link java.time.LocalDate#toEpochDay()} of {@link com.hypixel.hytale.server.core.modules.time.WorldTimeResource#getGameDateTime}
     * when automatic morning tithe last ran. Prevents double collection when sunrise crosses during the same in-game
     * calendar date while {@code treasuryLastTaxEpochDay} (dawn-aligned) advances.
     */
    @Nullable
    @SerializedName("treasuryLastTaxGameLocalDateEpochDay")
    private Long treasuryLastTaxGameLocalDateEpochDay;

    /**
     * Outer key: player UUID string. Inner key: villager NPC entity UUID string.
     * Values: reputation, daily talk tracking, milestone rewards.
     */
    @Nullable
    @SerializedName("playerVillagerReputation")
    private Map<String, Map<String, VillagerReputationEntry>> playerVillagerReputation;

    /**
     * Key: {@code NPCEntity} role name (e.g. {@code Aetherhaven_Merchant}). Town-wide gift history; use
     * {@link VillagerGiftLogEntry#getGiverPlayerUuid()} to show the viewing player's entries.
     */
    @Nullable
    @SerializedName("villagerGiftLogByRoleId")
    private Map<String, List<VillagerGiftLogEntry>> villagerGiftLogByRoleId;

    /**
     * Last known town resident NPCs (role id, binding kind, job plot, entity UUID) for revival UI and saves
     * when entities are unloaded or missing.
     */
    @SerializedName("residentNpcRecords")
    private List<ResidentNpcRecord> residentNpcRecords = new ArrayList<>();

    /**
     * Player-visible name; unique per world (case-insensitive). Set at charter placement (random default) or charter UI.
     */
    @Nullable
    @SerializedName("displayName")
    private String displayName;

    /** Non-owner members: player UUID string -> {@link TownMemberRole} name. */
    @Nullable
    @SerializedName("memberRoles")
    private Map<String, String> memberRoles;

    /** Owner and members: player UUID string -> granular permissions (migrated from {@link #memberRoles}). */
    @Nullable
    @SerializedName("memberPermissions")
    private Map<String, TownMemberPermissions> memberPermissions;

    @SerializedName("pendingInvites")
    private List<TownPendingInvite> pendingInvites = new ArrayList<>();

    /**
     * Crafting output item ids (same strings as {@link com.hypixel.hytale.builtin.crafting.CraftingPlugin#learnRecipe})
     * unlocked for the whole town via quest rewards with {@code grantTo: town_members}. New members receive pending
     * entries until they are in-world.
     */
    @Nullable
    @SerializedName("townSharedCraftRecipeItemIds")
    private LinkedHashSet<String> townSharedCraftRecipeItemIds;

    /**
     * Player UUID string → recipe item ids to apply with {@code CraftingPlugin.learnRecipe} when that player next has
     * an entity in this world (offline at grant time, or new member after shared unlocks already exist).
     */
    @Nullable
    @SerializedName("pendingCraftRecipeUnlockByPlayerUuid")
    private Map<String, List<String>> pendingCraftRecipeUnlockByPlayerUuid;

    /**
     * Level-1 charter amendment: {@link CharterTaxPolicy#id()}; immutable once set.
     */
    @Nullable
    @SerializedName("charterTaxPolicy")
    private String charterTaxPolicy;

    /**
     * Level-2 specialization: {@link CharterSpecialization#id()}; immutable once set.
     */
    @Nullable
    @SerializedName("charterSpecialization")
    private String charterSpecialization;

    /**
     * Legacy/synced flag: morning tax bonus applies when {@link #founderMonumentCount} is positive. Kept for Gson
     * backward compatibility.
     */
    @SerializedName("founderMonumentActive")
    private boolean founderMonumentActive;

    /** Placed founder monument blocks in this town; bonus does not stack (tax uses {@link #isFounderMonumentActive()} only). */
    @SerializedName("founderMonumentCount")
    private int founderMonumentCount;

    /** Active timed feast id (see {@link com.hexvane.aetherhaven.feast.FeastCatalog}); null if none. */
    @Nullable
    @SerializedName("activeFeastKind")
    private String activeFeastKind;

    /** Exclusive end dawn epoch day for {@link #activeFeastKind} (tax / decay feasts). */
    @Nullable
    @SerializedName("activeFeastEndExclusiveDawnDay")
    private Long activeFeastEndExclusiveDawnDay;

    /** Exclusive end dawn day for Berrycircle Concord cooldown. */
    @Nullable
    @SerializedName("feastBerrycircleCooldownEndExclusiveDawnDay")
    private Long feastBerrycircleCooldownEndExclusiveDawnDay;

    @Nullable
    @SerializedName("feastGatherPoiId")
    private String feastGatherPoiId;

    @SerializedName("feastGatherDeadlineEpochMs")
    private long feastGatherDeadlineEpochMs;

    /** Cumulative XP from completed quest board quests; town rank tier is derived from this. */
    @SerializedName("questBoardRankXp")
    private int questBoardRankXp;

    /** Last online dawn epoch day when unaccepted board slots were refreshed for this town. */
    @Nullable
    @SerializedName("questBoardLastRefreshOnlineDawnDay")
    private Long questBoardLastRefreshOnlineDawnDay;

    /** Up to three procedural quest board slot offers (see {@link com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord}). */
    @SerializedName("questBoardSlots")
    @Nullable
    private List<com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord> questBoardSlots;

    /** Remaining quest entry keys for shuffle bag board generation (roleId:entryId). */
    @SerializedName("questBoardDrawPool")
    @Nullable
    private List<String> questBoardDrawPool;

    /**
     * Workplace plot production storage: key plot UUID string, value slot cursors + item amounts
     * (see {@link com.hexvane.aetherhaven.production.ProductionTickSystem}).
     */
    @Nullable
    @SerializedName("plotProductionByPlotId")
    private Map<String, PlotProductionState> plotProductionByPlotId;

    /** Restaurant plot upgrade tiers: key plot UUID string. */
    @Nullable
    @SerializedName("plotRestaurantByPlotId")
    private Map<String, PlotRestaurantState> plotRestaurantByPlotId;

    /**
     * Per player Gaia's Draught shared progression (charges, capacity, heal tier). Key: player UUID string.
     */
    @Nullable
    @SerializedName("playerGaiaDraughtByPlayerUuid")
    private Map<String, GaiaDraughtState> playerGaiaDraughtByPlayerUuid;

    /**
     * Kill counts for {@code entity_kills} objectives. Outer: quest id, inner: objective id, value: current kills.
     */
    @Nullable
    @SerializedName("questKillProgress")
    private Map<String, Map<String, Integer>> questKillProgress;

    public TownRecord() {}

    public TownRecord(
        @Nonnull UUID townId,
        @Nonnull UUID ownerUuid,
        @Nonnull String worldName,
        int charterX,
        int charterY,
        int charterZ,
        int tier,
        int territoryChunkRadius,
        long createdTimeEpochMs
    ) {
        this.townId = townId.toString();
        this.ownerUuid = ownerUuid.toString();
        this.worldName = worldName;
        this.charterX = charterX;
        this.charterY = charterY;
        this.charterZ = charterZ;
        this.tier = tier;
        this.territoryChunkRadius = territoryChunkRadius;
        this.createdTimeEpochMs = createdTimeEpochMs;
    }

    /** Convert legacy {@code plotFootprints} entries into {@link PlotInstance} rows (COMPLETE, synthetic id). */
    public void migrateLegacyPlotFootprintsIfNeeded() {
        List<PlotFootprintRecord> legacy = plotFootprints;
        if (legacy == null || legacy.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (PlotFootprintRecord fp : legacy) {
            PlotInstance pi = new PlotInstance(
                UUID.randomUUID(),
                "legacy_migrated",
                PlotInstanceState.COMPLETE,
                fp,
                charterX,
                charterY,
                charterZ,
                now
            );
            getPlotInstances().add(pi);
        }
        LOGGER.atInfo().log("Migrated %s legacy plot footprints for town %s", legacy.size(), townId);
        plotFootprints = null;
    }

    public void migrateInnFieldsIfNeeded() {
        if (innPoolNpcIds == null) {
            innPoolNpcIds = new ArrayList<>();
        }
        if (innLockedEntityUuids == null) {
            innLockedEntityUuids = new ArrayList<>();
        }
        if (innPoolLastMorningGameDate == null && innPoolLastRefreshGameTime != null && !innPoolLastRefreshGameTime.isBlank()) {
            try {
                Instant inst = Instant.parse(innPoolLastRefreshGameTime.trim());
                innPoolLastMorningGameDate = inst.atZone(ZoneOffset.UTC).toLocalDate().toString();
            } catch (Exception ignored) {
            }
        }
        if (innPoolLastMorningEpochDay == null && innPoolLastMorningGameDate != null && !innPoolLastMorningGameDate.isBlank()) {
            try {
                innPoolLastMorningEpochDay = LocalDate.parse(innPoolLastMorningGameDate.trim()).toEpochDay();
            } catch (Exception ignored) {
            }
        }
        if (innVisitorPoolExcludedRoleIds == null) {
            innVisitorPoolExcludedRoleIds = new LinkedHashSet<>();
        }
        if (residentNpcRecords == null) {
            residentNpcRecords = new ArrayList<>();
        }
        migrateVillagerGiftLogIfNeeded();
        migrateVillagerReputationIfNeeded();
        migrateTownSocialFieldsIfNeeded();
        migrateFounderMonumentCountIfNeeded();
        migrateFeastFieldsIfNeeded();
        migratePlotProductionIfNeeded();
        migrateSharedRecipeUnlockFieldsIfNeeded();
        migrateQuestBoardFieldsIfNeeded();
    }

    private void migrateQuestBoardFieldsIfNeeded() {
        if (questBoardRankXp < 0) {
            questBoardRankXp = 0;
        }
        if (questBoardSlots == null) {
            questBoardSlots = new ArrayList<>();
        }
        if (questBoardDrawPool == null) {
            questBoardDrawPool = new ArrayList<>();
        }
    }

    private void migrateSharedRecipeUnlockFieldsIfNeeded() {
        if (townSharedCraftRecipeItemIds == null) {
            townSharedCraftRecipeItemIds = new LinkedHashSet<>();
        }
        if (pendingCraftRecipeUnlockByPlayerUuid == null) {
            pendingCraftRecipeUnlockByPlayerUuid = new LinkedHashMap<>();
        }
    }

    private void migrateFeastFieldsIfNeeded() {
        if (feastGatherDeadlineEpochMs < 0L) {
            feastGatherDeadlineEpochMs = 0L;
        }
    }

    public void migratePlotProductionIfNeeded() {
        if (plotProductionByPlotId == null) {
            plotProductionByPlotId = new LinkedHashMap<>();
            return;
        }
        for (PlotProductionState s : plotProductionByPlotId.values()) {
            if (s != null) {
                s.migrateIfNeeded();
            }
        }
    }

    /**
     * Clamps workplace production amounts to each plot construction's per-output {@code maxStorage} from the catalog.
     *
     * @return true if any amount was reduced
     */
    public boolean clampPlotProductionToCatalog(
        @Nonnull ProductionCatalog catalog,
        @Nonnull WorkplaceUnlockCatalog unlockCatalog,
        @Nonnull ConstructionCatalog constructionCatalog
    ) {
        migratePlotProductionIfNeeded();
        if (plotProductionByPlotId == null || plotProductionByPlotId.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (Map.Entry<String, PlotProductionState> row : plotProductionByPlotId.entrySet()) {
            PlotProductionState s = row.getValue();
            if (s == null) {
                continue;
            }
            UUID plotId;
            try {
                plotId = UUID.fromString(row.getKey());
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            PlotInstance plot = findPlotById(plotId);
            if (plot == null) {
                continue;
            }
            String cid = constructionCatalog.resolveGameplayConstructionId(plot.getConstructionId());
            if (!ProductionCatalog.isProductionWorkplaceConstruction(cid)) {
                continue;
            }
            ProductionCatalog.Entry centry = ProductionEffectiveCatalog.effective(catalog, unlockCatalog, cid, s);
            if (centry == null) {
                continue;
            }
            if (s.clampAmountsToCatalogEntry(centry, WorkplaceProductionUpgrades.capacityMultiplier(s))) {
                changed = true;
            }
        }
        return changed;
    }

    @Nonnull
    public PlotProductionState getOrCreatePlotProduction(@Nonnull UUID plotId) {
        migratePlotProductionIfNeeded();
        return plotProductionByPlotId.computeIfAbsent(plotId.toString(), k -> PlotProductionState.empty());
    }

    private void migratePlotRestaurantIfNeeded() {
        if (plotRestaurantByPlotId == null) {
            plotRestaurantByPlotId = new LinkedHashMap<>();
        }
        for (PlotRestaurantState s : plotRestaurantByPlotId.values()) {
            if (s != null) {
                s.migrateIfNeeded();
            }
        }
    }

    @Nonnull
    public PlotRestaurantState getOrCreatePlotRestaurant(@Nonnull UUID plotId) {
        migratePlotRestaurantIfNeeded();
        return plotRestaurantByPlotId.computeIfAbsent(plotId.toString(), k -> PlotRestaurantState.empty());
    }

    @Nonnull
    private Map<String, GaiaDraughtState> gaiaDraughtMap() {
        if (playerGaiaDraughtByPlayerUuid == null) {
            playerGaiaDraughtByPlayerUuid = new LinkedHashMap<>();
        }
        return playerGaiaDraughtByPlayerUuid;
    }

    @Nonnull
    public GaiaDraughtState getOrCreateGaiaDraughtState(@Nonnull UUID playerUuid) {
        String k = playerUuid.toString();
        GaiaDraughtState s = gaiaDraughtMap().computeIfAbsent(k, x -> GaiaDraughtState.createFresh());
        s.ensureLegacyMigrated();
        return s;
    }

    @Nullable
    public GaiaDraughtState findGaiaDraughtState(@Nonnull UUID playerUuid) {
        if (playerGaiaDraughtByPlayerUuid == null) {
            return null;
        }
        GaiaDraughtState s = playerGaiaDraughtByPlayerUuid.get(playerUuid.toString());
        if (s != null) {
            s.ensureLegacyMigrated();
        }
        return s;
    }

    @Nonnull
    private Map<String, Map<String, Integer>> questKillMap() {
        if (questKillProgress == null) {
            questKillProgress = new LinkedHashMap<>();
        }
        return questKillProgress;
    }

    /** Initializes kill counters for {@code entity_kills} objectives to zero. */
    public void initQuestKillProgress(@Nonnull String questId, @Nonnull List<String> entityKillObjectiveIds) {
        if (entityKillObjectiveIds.isEmpty()) {
            return;
        }
        Map<String, Integer> m = questKillMap().computeIfAbsent(questId.trim(), k -> new LinkedHashMap<>());
        for (String oid : entityKillObjectiveIds) {
            if (oid != null && !oid.isBlank()) {
                m.putIfAbsent(oid.trim(), 0);
            }
        }
    }

    public int getQuestKillCount(@Nonnull String questId, @Nonnull String objectiveId) {
        if (questKillProgress == null) {
            return 0;
        }
        Map<String, Integer> m = questKillProgress.get(questId.trim());
        if (m == null) {
            return 0;
        }
        Integer v = m.get(objectiveId.trim());
        return v != null ? v : 0;
    }

    public void setQuestKillCount(@Nonnull String questId, @Nonnull String objectiveId, int count) {
        Map<String, Integer> m = questKillMap().computeIfAbsent(questId.trim(), k -> new LinkedHashMap<>());
        m.put(objectiveId.trim(), Math.max(0, count));
    }

    public void clearQuestKillProgress(@Nonnull String questId) {
        if (questKillProgress != null) {
            questKillProgress.remove(questId.trim());
        }
    }

    /** Reconciles {@link #founderMonumentCount} with legacy {@link #founderMonumentActive} from older saves. */
    public void migrateFounderMonumentCountIfNeeded() {
        if (founderMonumentCount < 0) {
            founderMonumentCount = 0;
        }
        if (founderMonumentCount > 0) {
            founderMonumentActive = true;
            return;
        }
        if (founderMonumentActive) {
            founderMonumentCount = 1;
        }
        founderMonumentActive = founderMonumentCount > 0;
    }

    public void migrateTownSocialFieldsIfNeeded() {
        if (displayName == null || displayName.isBlank()) {
            String hex = townId != null ? townId.replace("-", "") : "town";
            String suffix = hex.length() >= 4 ? hex.substring(0, 4) : hex;
            displayName = "Town " + suffix;
        }
        if (memberRoles == null) {
            memberRoles = new LinkedHashMap<>();
        }
        if (memberPermissions == null) {
            memberPermissions = new LinkedHashMap<>();
        }
        migrateMemberPermissionsFromLegacyRolesIfNeeded();
        if (pendingInvites == null) {
            pendingInvites = new ArrayList<>();
        }
    }

    /** Seeds {@link #memberPermissions} from legacy {@link #memberRoles} once per member. */
    private void migrateMemberPermissionsFromLegacyRolesIfNeeded() {
        if (memberPermissions == null) {
            memberPermissions = new LinkedHashMap<>();
        }
        for (Map.Entry<String, String> e : memberRoles.entrySet()) {
            if (!memberPermissions.containsKey(e.getKey())) {
                memberPermissions.put(e.getKey(), TownMemberPermissions.fromRole(TownMemberRole.fromSerialized(e.getValue())));
            }
        }
    }

    @Nonnull
    private Map<String, TownMemberPermissions> getMemberPermissionsMap() {
        migrateTownSocialFieldsIfNeeded();
        return memberPermissions;
    }

    public void migrateVillagerGiftLogIfNeeded() {
        if (villagerGiftLogByRoleId == null) {
            villagerGiftLogByRoleId = new LinkedHashMap<>();
        }
    }

    public void migrateVillagerReputationIfNeeded() {
        if (playerVillagerReputation == null) {
            playerVillagerReputation = new LinkedHashMap<>();
            return;
        }
        for (Map<String, VillagerReputationEntry> inner : playerVillagerReputation.values()) {
            if (inner == null) {
                continue;
            }
            for (VillagerReputationEntry e : inner.values()) {
                if (e != null) {
                    e.migrateIfNeeded();
                }
            }
        }
    }

    @Nonnull
    public Map<String, Map<String, VillagerReputationEntry>> getPlayerVillagerReputation() {
        if (playerVillagerReputation == null) {
            playerVillagerReputation = new LinkedHashMap<>();
        }
        return playerVillagerReputation;
    }

    @Nonnull
    public Map<String, List<VillagerGiftLogEntry>> getVillagerGiftLogByRoleId() {
        migrateVillagerGiftLogIfNeeded();
        return villagerGiftLogByRoleId;
    }

    /** @param npcRoleId {@link com.hypixel.hytale.server.npc.entities.NPCEntity#getRoleName} */
    public void appendVillagerGiftLog(@Nonnull String npcRoleId, @Nonnull VillagerGiftLogEntry entry) {
        migrateVillagerGiftLogIfNeeded();
        String k = npcRoleId.trim();
        if (k.isEmpty() || villagerGiftLogByRoleId == null) {
            return;
        }
        List<VillagerGiftLogEntry> list = villagerGiftLogByRoleId.computeIfAbsent(k, x -> new ArrayList<>());
        list.add(entry);
        int cap = 500;
        while (list.size() > cap) {
            list.remove(0);
        }
    }

    @Nonnull
    public List<ResidentNpcRecord> getResidentNpcRecords() {
        if (residentNpcRecords == null) {
            residentNpcRecords = new ArrayList<>();
        }
        return residentNpcRecords;
    }

    /**
     * Every NPC entity UUID persisted by this town. Used for dissolution cleanup, building relocation, telemetry, and
     * protecting town NPCs when stripping prefab volumes.
     */
    public void collectTrackedNpcEntityUuids(@Nonnull Set<UUID> out) {
        UUID nil = new UUID(0L, 0L);
        if (getElderEntityUuid() != null && !nil.equals(getElderEntityUuid())) {
            out.add(getElderEntityUuid());
        }
        if (getInnkeeperEntityUuid() != null && !nil.equals(getInnkeeperEntityUuid())) {
            out.add(getInnkeeperEntityUuid());
        }
        for (String s : getInnPoolNpcIds()) {
            addTrackedNpcUuid(out, s, nil);
        }
        for (String s : getInnLockedEntityUuids()) {
            addTrackedNpcUuid(out, s, nil);
        }
        UUID guildMasterUuid = getGuildMasterEntityUuid();
        if (guildMasterUuid != null && !nil.equals(guildMasterUuid)) {
            out.add(guildMasterUuid);
        }
        for (String s : getGuildHallAdventurerNpcIds()) {
            addTrackedNpcUuid(out, s, nil);
        }
        // Include the slot-map keys as a fallback for partially inconsistent legacy saves.
        for (String s : getGuildHallAdventurerSlotByNpcId().keySet()) {
            addTrackedNpcUuid(out, s, nil);
        }
        for (HiredGuardRecord guard : getHiredGuardRecords()) {
            UUID u = guard != null ? guard.getEntityUuid() : null;
            if (u != null && !nil.equals(u)) {
                out.add(u);
            }
        }
        for (com.hexvane.aetherhaven.tourist.TouristRecord tourist : getTouristRecords()) {
            UUID u = tourist != null ? tourist.getEntityUuid() : null;
            if (u != null && !nil.equals(u)) {
                out.add(u);
            }
        }
        for (ResidentNpcRecord r : getResidentNpcRecords()) {
            UUID u = r.getLastEntityUuid();
            if (!nil.equals(u)) {
                out.add(u);
            }
        }
        for (PlotInstance p : getPlotInstances()) {
            for (UUID h : p.getHomeResidentEntityUuids()) {
                if (!nil.equals(h)) {
                    out.add(h);
                }
            }
        }
    }

    private static void addTrackedNpcUuid(@Nonnull Set<UUID> out, @Nullable String raw, @Nonnull UUID nil) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            UUID u = UUID.fromString(raw.trim());
            if (!nil.equals(u)) {
                out.add(u);
            }
        } catch (IllegalArgumentException ignored) {
        }
    }

    @Nonnull
    public Set<String> getInnVisitorPoolExcludedRoleIds() {
        if (innVisitorPoolExcludedRoleIds == null) {
            innVisitorPoolExcludedRoleIds = new LinkedHashSet<>();
        }
        return innVisitorPoolExcludedRoleIds;
    }

    public void addInnVisitorPoolExcludedRoleId(@Nonnull String roleId) {
        if (roleId.isBlank()) {
            return;
        }
        getInnVisitorPoolExcludedRoleIds().add(roleId.trim());
    }

    @Nonnull
    public UUID getTownId() {
        return UUID.fromString(townId);
    }

    public void setTownId(@Nonnull UUID id) {
        this.townId = id.toString();
    }

    @Nonnull
    public UUID getOwnerUuid() {
        return UUID.fromString(ownerUuid);
    }

    @Nonnull
    public String getWorldName() {
        return worldName != null ? worldName : "";
    }

    public int getCharterX() {
        return charterX;
    }

    public int getCharterY() {
        return charterY;
    }

    public int getCharterZ() {
        return charterZ;
    }

    /** Updates the charter block position (used when relocating the physical charter in-world). */
    public void setCharterPosition(int x, int y, int z) {
        this.charterX = x;
        this.charterY = y;
        this.charterZ = z;
    }

    public int getTier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = tier;
    }

    public int getTerritoryChunkRadius() {
        return territoryChunkRadius;
    }

    public void setTerritoryChunkRadius(int territoryChunkRadius) {
        this.territoryChunkRadius = territoryChunkRadius;
    }

    public long getCreatedTimeEpochMs() {
        return createdTimeEpochMs;
    }

    public boolean isElderSpawned() {
        return elderSpawned;
    }

    public void setElderSpawned(boolean elderSpawned) {
        this.elderSpawned = elderSpawned;
    }

    @Nullable
    public UUID getElderEntityUuid() {
        return elderEntityUuid != null && !elderEntityUuid.isEmpty() ? UUID.fromString(elderEntityUuid) : null;
    }

    public void setElderEntityUuid(@Nullable UUID uuid) {
        this.elderEntityUuid = uuid != null ? uuid.toString() : null;
    }

    public boolean isInnActive() {
        return innActive;
    }

    public void setInnActive(boolean innActive) {
        this.innActive = innActive;
    }

    @Nullable
    public UUID getInnkeeperEntityUuid() {
        return innkeeperEntityUuid != null && !innkeeperEntityUuid.isEmpty() ? UUID.fromString(innkeeperEntityUuid) : null;
    }

    public void setInnkeeperEntityUuid(@Nullable UUID uuid) {
        this.innkeeperEntityUuid = uuid != null ? uuid.toString() : null;
    }

    @Nonnull
    public List<String> getInnPoolNpcIds() {
        if (innPoolNpcIds == null) {
            innPoolNpcIds = new ArrayList<>();
        }
        return innPoolNpcIds;
    }

    @Nonnull
    public List<String> getInnLockedEntityUuids() {
        if (innLockedEntityUuids == null) {
            innLockedEntityUuids = new ArrayList<>();
        }
        return innLockedEntityUuids;
    }

    public boolean isInnVisitorLocked(@Nonnull UUID entityUuid) {
        String s = entityUuid.toString();
        for (String x : getInnLockedEntityUuids()) {
            if (s.equalsIgnoreCase(x)) {
                return true;
            }
        }
        return false;
    }

    public void addInnLockedEntity(@Nonnull UUID entityUuid) {
        String s = entityUuid.toString();
        for (String x : getInnLockedEntityUuids()) {
            if (s.equalsIgnoreCase(x)) {
                return;
            }
        }
        getInnLockedEntityUuids().add(s);
    }

    public void removeInnLockedEntity(@Nonnull UUID entityUuid) {
        String s = entityUuid.toString();
        getInnLockedEntityUuids().removeIf(x -> s.equalsIgnoreCase(x));
    }

    @Nullable
    public Instant getInnPoolLastRefreshGameTime() {
        String t = innPoolLastRefreshGameTime;
        if (t == null || t.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(t.trim());
        } catch (Exception e) {
            return null;
        }
    }

    public void setInnPoolLastRefreshGameTime(@Nullable Instant gameTime) {
        this.innPoolLastRefreshGameTime = gameTime != null ? gameTime.toString() : null;
    }

    @Nullable
    public String getInnPoolLastMorningGameDate() {
        String s = innPoolLastMorningGameDate;
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    public void setInnPoolLastMorningGameDate(@Nullable String dateIsoUtc) {
        this.innPoolLastMorningGameDate = dateIsoUtc != null && !dateIsoUtc.isBlank() ? dateIsoUtc.trim() : null;
    }

    @Nullable
    public Long getInnPoolLastMorningEpochDay() {
        return innPoolLastMorningEpochDay;
    }

    public void setInnPoolLastMorningEpochDay(@Nullable Long epochDay) {
        this.innPoolLastMorningEpochDay = epochDay;
    }

    @Nullable
    public Long getInnPoolLastFillEpochDay() {
        return innPoolLastFillEpochDay;
    }

    public void setInnPoolLastFillEpochDay(@Nullable Long epochDay) {
        this.innPoolLastFillEpochDay = epochDay;
    }

    @Nullable
    public UUID getGuildMasterEntityUuid() {
        return guildMasterEntityUuid != null && !guildMasterEntityUuid.isBlank() ? UUID.fromString(guildMasterEntityUuid) : null;
    }

    public void setGuildMasterEntityUuid(@Nullable UUID uuid) {
        this.guildMasterEntityUuid = uuid != null ? uuid.toString() : null;
    }

    public boolean isGuildHallActive() {
        return guildHallActive;
    }

    public void setGuildHallActive(boolean guildHallActive) {
        this.guildHallActive = guildHallActive;
    }

    @Nonnull
    public List<String> getGuildHallAdventurerNpcIds() {
        if (guildHallAdventurerNpcIds == null) {
            guildHallAdventurerNpcIds = new ArrayList<>();
        }
        return guildHallAdventurerNpcIds;
    }

    @Nullable
    public Long getGuildHallLastMorningEpochDay() {
        return guildHallLastMorningEpochDay;
    }

    public void setGuildHallLastMorningEpochDay(@Nullable Long epochDay) {
        this.guildHallLastMorningEpochDay = epochDay;
    }

    @Nonnull
    public List<Integer> getGuildHallAdventurerFilledSlots() {
        if (guildHallAdventurerFilledSlots == null) {
            guildHallAdventurerFilledSlots = new ArrayList<>();
        }
        return guildHallAdventurerFilledSlots;
    }

    @Nonnull
    public Map<String, Integer> getGuildHallAdventurerSlotByNpcId() {
        if (guildHallAdventurerSlotByNpcId == null) {
            guildHallAdventurerSlotByNpcId = new LinkedHashMap<>();
        }
        return guildHallAdventurerSlotByNpcId;
    }

    @Nonnull
    public List<HiredGuardRecord> getHiredGuardRecords() {
        if (hiredGuardRecords == null) {
            hiredGuardRecords = new ArrayList<>();
        }
        return hiredGuardRecords;
    }

    @Nonnull
    public List<com.hexvane.aetherhaven.tourist.TouristRecord> getTouristRecords() {
        if (touristRecords == null) {
            touristRecords = new ArrayList<>();
        }
        return touristRecords;
    }

    public long getTouristSpawnPlannedDayEpochDay() {
        return touristSpawnPlannedDayEpochDay;
    }

    public void setTouristSpawnPlannedDayEpochDay(long touristSpawnPlannedDayEpochDay) {
        this.touristSpawnPlannedDayEpochDay = touristSpawnPlannedDayEpochDay;
    }

    @Nonnull
    public List<Long> getTouristPlannedSpawnEpochMinutes() {
        if (touristPlannedSpawnEpochMinutes == null) {
            touristPlannedSpawnEpochMinutes = new ArrayList<>();
        }
        return touristPlannedSpawnEpochMinutes;
    }

    @Nonnull
    public List<Long> getTouristExecutedSpawnEpochMinutes() {
        if (touristExecutedSpawnEpochMinutes == null) {
            touristExecutedSpawnEpochMinutes = new ArrayList<>();
        }
        return touristExecutedSpawnEpochMinutes;
    }

    public void clearTouristDailySpawnPlan() {
        getTouristPlannedSpawnEpochMinutes().clear();
        getTouristExecutedSpawnEpochMinutes().clear();
    }

    private void migrateLegacyGuardHouseQuestTarget() {
        if (guardHouseQuestTargetEntityUuid == null || guardHouseQuestTargetEntityUuid.isBlank()) {
            return;
        }
        if (questTargetEntityUuidByQuestId == null) {
            questTargetEntityUuidByQuestId = new LinkedHashMap<>();
        }
        String qid = com.hexvane.aetherhaven.AetherhavenConstants.QUEST_HOUSE_GUARD;
        questTargetEntityUuidByQuestId.putIfAbsent(qid, guardHouseQuestTargetEntityUuid.trim());
    }

    @Nullable
    public UUID getQuestTargetEntityUuid(@Nonnull String questId) {
        migrateLegacyGuardHouseQuestTarget();
        String q = questId.trim();
        if (q.isEmpty() || questTargetEntityUuidByQuestId == null) {
            return null;
        }
        String raw = questTargetEntityUuidByQuestId.get(q);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setQuestTargetEntityUuid(@Nonnull String questId, @Nullable UUID uuid) {
        migrateLegacyGuardHouseQuestTarget();
        String q = questId.trim();
        if (q.isEmpty()) {
            return;
        }
        if (questTargetEntityUuidByQuestId == null) {
            questTargetEntityUuidByQuestId = new LinkedHashMap<>();
        }
        if (uuid == null) {
            questTargetEntityUuidByQuestId.remove(q);
            if (com.hexvane.aetherhaven.AetherhavenConstants.QUEST_HOUSE_GUARD.equals(q)) {
                guardHouseQuestTargetEntityUuid = null;
            }
        } else {
            String s = uuid.toString();
            questTargetEntityUuidByQuestId.put(q, s);
            if (com.hexvane.aetherhaven.AetherhavenConstants.QUEST_HOUSE_GUARD.equals(q)) {
                guardHouseQuestTargetEntityUuid = s;
            }
        }
    }

    public void clearQuestTarget(@Nonnull String questId) {
        setQuestTargetEntityUuid(questId, null);
    }

    public boolean hasQuestCompletedForEntity(@Nonnull String questId, @Nonnull UUID entityUuid) {
        String q = questId.trim();
        if (q.isEmpty()) {
            return false;
        }
        if (questCompletedEntityUuids == null) {
            return false;
        }
        List<String> list = questCompletedEntityUuids.get(q);
        if (list == null || list.isEmpty()) {
            return false;
        }
        String want = entityUuid.toString();
        for (String s : list) {
            if (s != null && want.equalsIgnoreCase(s.trim())) {
                return true;
            }
        }
        return false;
    }

    public void markQuestCompletedForEntity(@Nonnull String questId, @Nonnull UUID entityUuid) {
        String q = questId.trim();
        if (q.isEmpty()) {
            return;
        }
        if (questCompletedEntityUuids == null) {
            questCompletedEntityUuids = new LinkedHashMap<>();
        }
        List<String> list = questCompletedEntityUuids.computeIfAbsent(q, k -> new ArrayList<>());
        String s = entityUuid.toString();
        for (String existing : list) {
            if (existing != null && s.equalsIgnoreCase(existing.trim())) {
                return;
            }
        }
        list.add(s);
    }

    @Nullable
    public UUID getGuardHouseQuestTargetEntityUuid() {
        return getQuestTargetEntityUuid(com.hexvane.aetherhaven.AetherhavenConstants.QUEST_HOUSE_GUARD);
    }

    public void setGuardHouseQuestTargetEntityUuid(@Nullable UUID uuid) {
        setQuestTargetEntityUuid(com.hexvane.aetherhaven.AetherhavenConstants.QUEST_HOUSE_GUARD, uuid);
    }

    /**
     * Updates quest target entity uuid references after villager reset or revival.
     *
     * @return true when any reference was updated
     */
    public boolean replaceEntityUuidInQuestTargets(@Nonnull UUID oldUuid, @Nonnull UUID newUuid) {
        if (oldUuid.equals(newUuid)) {
            return false;
        }
        migrateLegacyGuardHouseQuestTarget();
        String oldS = oldUuid.toString();
        String newS = newUuid.toString();
        boolean changed = false;
        if (questTargetEntityUuidByQuestId != null) {
            for (Map.Entry<String, String> e : new ArrayList<>(questTargetEntityUuidByQuestId.entrySet())) {
                String v = e.getValue();
                if (v != null && oldS.equalsIgnoreCase(v.trim())) {
                    questTargetEntityUuidByQuestId.put(e.getKey(), newS);
                    changed = true;
                }
            }
        }
        if (guardHouseQuestTargetEntityUuid != null && oldS.equalsIgnoreCase(guardHouseQuestTargetEntityUuid.trim())) {
            guardHouseQuestTargetEntityUuid = newS;
            changed = true;
        }
        return changed;
    }

    @Nonnull
    public List<PlotInstance> getPlotInstances() {
        if (plotInstances == null) {
            plotInstances = new ArrayList<>();
        }
        return plotInstances;
    }

    public void addPlotInstance(@Nonnull PlotInstance instance) {
        getPlotInstances().add(instance);
    }

    @Nonnull
    public List<WallSegmentRecord> getWallSegments() {
        if (wallSegments == null) {
            wallSegments = new ArrayList<>();
        }
        return wallSegments;
    }

    public void addWallSegment(@Nonnull WallSegmentRecord segment) {
        getWallSegments().add(segment);
    }

    public boolean removeWallSegment(@Nonnull UUID segmentId) {
        return getWallSegments().removeIf(s -> s.getSegmentId().equals(segmentId));
    }

    @Nullable
    public WallSegmentRecord findWallSegmentById(@Nonnull UUID segmentId) {
        for (WallSegmentRecord s : getWallSegments()) {
            if (s.getSegmentId().equals(segmentId)) {
                return s;
            }
        }
        return null;
    }

    @Nullable
    public WallSegmentRecord findWallSegmentAtBlock(int x, int y, int z) {
        for (WallSegmentRecord s : getWallSegments()) {
            if (s.containsBlock(x, y, z)) {
                return s;
            }
        }
        return null;
    }

    @Nullable
    public PlotFootprintRecord findOverlappingNonWallPlot(@Nonnull PlotFootprintRecord candidate, @Nullable UUID excludePlotId) {
        for (PlotInstance p : getPlotInstances()) {
            if (excludePlotId != null && p.getPlotId().equals(excludePlotId)) {
                continue;
            }
            if (p.intersectsFootprint(candidate)) {
                return p.toFootprint();
            }
        }
        return null;
    }

    @Nullable
    public PlotFootprintRecord findOverlappingWallFootprint(
        @Nonnull PlotFootprintRecord candidate,
        @Nullable UUID excludePlotId,
        @Nullable UUID excludeSegmentId
    ) {
        for (PlotInstance p : getPlotInstances()) {
            if (excludePlotId != null && p.getPlotId().equals(excludePlotId)) {
                continue;
            }
            if (p.intersectsFootprint(candidate)) {
                return p.toFootprint();
            }
        }
        for (WallSegmentRecord s : getWallSegments()) {
            if (excludeSegmentId != null && s.getSegmentId().equals(excludeSegmentId)) {
                continue;
            }
            if (s.intersectsFootprint(candidate)) {
                return s.toFootprint();
            }
        }
        return null;
    }

    /** Removes a registered plot (e.g. plot sign picked up). @return true if a row was removed */
    public boolean removePlotInstance(@Nonnull UUID plotId) {
        return getPlotInstances().removeIf(p -> p.getPlotId().equals(plotId));
    }

    @Nullable
    public PlotInstance findPlotById(@Nonnull UUID plotId) {
        for (PlotInstance p : getPlotInstances()) {
            if (p.getPlotId().equals(plotId)) {
                return p;
            }
        }
        return null;
    }

    /** First complete plot whose footprint contains the block (for POI tool placement). */
    @Nullable
    public PlotInstance findCompletePlotContaining(int x, int y, int z) {
        for (PlotInstance p : getPlotInstances()) {
            if (p.containsWorldBlock(x, y, z)) {
                return p;
            }
        }
        return null;
    }

    @Nullable
    public PlotFootprintRecord findOverlappingPlot(@Nonnull PlotFootprintRecord candidate) {
        return findOverlappingPlot(candidate, null);
    }

    /**
     * @param excludePlotId optional plot to ignore (e.g. building being relocated).
     */
    @Nullable
    public PlotFootprintRecord findOverlappingPlot(@Nonnull PlotFootprintRecord candidate, @Nullable UUID excludePlotId) {
        for (PlotInstance p : getPlotInstances()) {
            if (excludePlotId != null && p.getPlotId().equals(excludePlotId)) {
                continue;
            }
            if (p.intersectsFootprint(candidate)) {
                return p.toFootprint();
            }
        }
        return null;
    }

    public boolean hasQuestActive(@Nonnull String questId) {
        return normalizedQuestSet(activeQuestIds).contains(questId);
    }

    public boolean hasQuestCompleted(@Nonnull String questId) {
        return normalizedQuestSet(completedQuestIds).contains(questId);
    }

    /** Removes a quest id from the town wide completed list (used when migrating to per entity repeat). */
    public void clearGlobalQuestCompletion(@Nonnull String questId) {
        String q = questId.trim();
        if (q.isEmpty()) {
            return;
        }
        Set<String> done = normalizedQuestSet(completedQuestIds);
        if (!done.remove(q)) {
            return;
        }
        completedQuestIds = new ArrayList<>(done);
    }

    /**
     * True if the quest is active or was completed. Used when job buildings finish so promotion still runs if the
     * quest was turned in before the construction hook applied (or the hook failed earlier).
     */
    public boolean hasQuestActiveOrCompleted(@Nonnull String questId) {
        String q = questId.trim();
        if (q.isEmpty()) {
            return false;
        }
        return hasQuestActive(q) || hasQuestCompleted(q);
    }

    public void addActiveQuest(@Nonnull String questId) {
        String q = questId.trim();
        if (q.isEmpty()) {
            return;
        }
        Set<String> done = normalizedQuestSet(completedQuestIds);
        if (done.contains(q)) {
            return;
        }
        Set<String> active = normalizedQuestSet(activeQuestIds);
        active.add(q);
        activeQuestIds = new ArrayList<>(active);
    }

    public void completeQuest(@Nonnull String questId) {
        String q = questId.trim();
        if (q.isEmpty()) {
            return;
        }
        Set<String> active = normalizedQuestSet(activeQuestIds);
        active.remove(q);
        activeQuestIds = new ArrayList<>(active);
        Set<String> done = normalizedQuestSet(completedQuestIds);
        done.add(q);
        completedQuestIds = new ArrayList<>(done);
        clearQuestObjectiveProgress(q);
        clearQuestKillProgress(q);
        clearQuestTarget(q);
    }

    public void clearActiveQuest(@Nonnull String questId) {
        String q = questId.trim();
        if (q.isEmpty()) {
            return;
        }
        Set<String> active = normalizedQuestSet(activeQuestIds);
        active.remove(q);
        activeQuestIds = new ArrayList<>(active);
        clearQuestObjectiveProgress(q);
        clearQuestKillProgress(q);
        clearQuestTarget(q);
    }

    /**
     * Completes a {@code per_entity} quest for one NPC without marking the whole town as done for that quest id.
     */
    public void completeQuestForEntity(@Nonnull String questId, @Nonnull UUID entityUuid) {
        String q = questId.trim();
        if (q.isEmpty()) {
            return;
        }
        Set<String> active = normalizedQuestSet(activeQuestIds);
        active.remove(q);
        activeQuestIds = new ArrayList<>(active);
        markQuestCompletedForEntity(q, entityUuid);
        clearQuestObjectiveProgress(q);
        clearQuestKillProgress(q);
        clearQuestTarget(q);
    }

    /** Initializes persisted objective entries without replacing progress from an existing save. */
    public void initQuestObjectiveProgress(@Nonnull String questId, @Nonnull List<String> trackableObjectiveIds) {
        if (trackableObjectiveIds.isEmpty()) {
            return;
        }
        if (questObjectiveProgress == null) {
            questObjectiveProgress = new LinkedHashMap<>();
        }
        Map<String, Boolean> m = questObjectiveProgress.computeIfAbsent(questId.trim(), k -> new LinkedHashMap<>());
        for (String oid : trackableObjectiveIds) {
            if (oid != null && !oid.isBlank()) {
                m.putIfAbsent(oid.trim(), Boolean.FALSE);
            }
        }
    }

    public boolean isQuestObjectiveComplete(@Nonnull String questId, @Nonnull String objectiveId) {
        if (questObjectiveProgress == null) {
            return false;
        }
        Map<String, Boolean> objectives = questObjectiveProgress.get(questId.trim());
        if (objectives == null) {
            return false;
        }
        return Boolean.TRUE.equals(objectives.get(objectiveId.trim()));
    }

    /**
     * Marks an objective complete.
     *
     * @return true only when persisted state changed
     */
    public boolean completeQuestObjective(@Nonnull String questId, @Nonnull String objectiveId) {
        String qid = questId.trim();
        String oid = objectiveId.trim();
        if (qid.isEmpty() || oid.isEmpty()) {
            return false;
        }
        if (questObjectiveProgress == null) {
            questObjectiveProgress = new LinkedHashMap<>();
        }
        Map<String, Boolean> objectives =
            questObjectiveProgress.computeIfAbsent(qid, ignored -> new LinkedHashMap<>());
        return !Boolean.TRUE.equals(objectives.put(oid, Boolean.TRUE));
    }

    @Nonnull
    public Map<String, Boolean> getQuestObjectiveProgressSnapshot(@Nonnull String questId) {
        if (questObjectiveProgress == null) {
            return Map.of();
        }
        Map<String, Boolean> objectives = questObjectiveProgress.get(questId.trim());
        return objectives != null
            ? Collections.unmodifiableMap(new LinkedHashMap<>(objectives))
            : Map.of();
    }

    public void clearQuestObjectiveProgress(@Nonnull String questId) {
        if (questObjectiveProgress != null) {
            questObjectiveProgress.remove(questId.trim());
        }
    }

    @Nonnull
    public List<String> getActiveQuestIdsSnapshot() {
        return List.copyOf(new ArrayList<>(normalizedQuestSet(activeQuestIds)));
    }

    @Nonnull
    public List<String> getCompletedQuestIdsSnapshot() {
        return List.copyOf(new ArrayList<>(normalizedQuestSet(completedQuestIds)));
    }

    @Nonnull
    private static Set<String> normalizedQuestSet(@Nullable List<String> list) {
        Set<String> s = new LinkedHashSet<>();
        if (list != null) {
            for (String x : list) {
                if (x != null && !x.isBlank()) {
                    s.add(x.trim());
                }
            }
        }
        return s;
    }

    /** True if any plot is COMPLETE whose stored construction resolves to this gameplay construction id. */
    public boolean hasCompletePlotWithConstruction(
        @Nonnull ConstructionCatalog constructionCatalog,
        @Nonnull String gameplayConstructionId
    ) {
        String c = gameplayConstructionId.trim();
        if (c.isEmpty()) {
            return false;
        }
        for (PlotInstance p : getPlotInstances()) {
            if (p.getState() == PlotInstanceState.COMPLETE
                && constructionCatalog.matchesGameplayConstruction(p.getConstructionId(), c)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public PlotInstance findCompletePlotWithConstruction(
        @Nonnull ConstructionCatalog constructionCatalog,
        @Nonnull String gameplayConstructionId
    ) {
        String c = gameplayConstructionId.trim();
        if (c.isEmpty()) {
            return null;
        }
        for (PlotInstance p : getPlotInstances()) {
            if (p.getState() == PlotInstanceState.COMPLETE
                && constructionCatalog.matchesGameplayConstruction(p.getConstructionId(), c)) {
                return p;
            }
        }
        return null;
    }

    /**
     * All COMPLETE plots whose stored {@link PlotInstance#getConstructionId()} resolves to {@code gameplayConstructionId}.
     */
    @Nonnull
    public List<PlotInstance> listCompletePlotsWithGameplayConstruction(
        @Nonnull ConstructionCatalog constructionCatalog,
        @Nonnull String gameplayConstructionId
    ) {
        String c = gameplayConstructionId.trim();
        List<PlotInstance> out = new ArrayList<>();
        if (c.isEmpty()) {
            return out;
        }
        for (PlotInstance p : getPlotInstances()) {
            if (p.getState() == PlotInstanceState.COMPLETE
                && constructionCatalog.matchesGameplayConstruction(p.getConstructionId(), c)) {
                out.add(p);
            }
        }
        return out;
    }

    /**
     * True if this villager NPC is listed as home resident on any complete residential plot in this town
     * (used for house-quest dialogue completion after assignment). Uses {@link ConstructionCatalog#matchesGameplayConstruction}
     * so variant houses ({@code countsAsConstructionId} including {@link AetherhavenConstants#CONSTRUCTION_PLOT_HOUSE}) count.
     */
    public boolean isNpcHomeResidentOnHousePlot(@Nonnull UUID npcEntityUuid, @Nonnull ConstructionCatalog constructionCatalog) {
        for (PlotInstance p : getPlotInstances()) {
            if (p.getState() != PlotInstanceState.COMPLETE) {
                continue;
            }
            if (!constructionCatalog.matchesGameplayConstruction(
                p.getConstructionId(),
                AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE
            )) {
                continue;
            }
            if (p.hasHomeResident(npcEntityUuid)) {
                return true;
            }
        }
        return false;
    }

    /** If {@code residentUuid} is home in another plot, clears that assignment (one home per villager). */
    public void clearHomeResidentFromOtherPlots(@Nonnull UUID exceptPlotId, @Nonnull UUID residentUuid) {
        for (PlotInstance p : getPlotInstances()) {
            if (p.getPlotId().equals(exceptPlotId)) {
                continue;
            }
            if (p.hasHomeResident(residentUuid)) {
                p.clearHomeResidentUuid(residentUuid);
            }
        }
    }

    public long getTreasuryGoldCoinCount() {
        return Math.max(0L, treasuryGoldCoinCount);
    }

    public void setTreasuryGoldCoinCount(long count) {
        this.treasuryGoldCoinCount = Math.max(0L, count);
    }

    public void addTreasuryGoldCoins(long delta) {
        if (delta == 0L) {
            return;
        }
        long next = getTreasuryGoldCoinCount() + delta;
        this.treasuryGoldCoinCount = Math.max(0L, next);
    }

    @Nonnull
    private Map<String, Long> playerShopSafeGoldMap() {
        if (playerShopSafeGoldByPlayerUuid == null) {
            playerShopSafeGoldByPlayerUuid = new LinkedHashMap<>();
        }
        return playerShopSafeGoldByPlayerUuid;
    }

    public long getPlayerShopSafeGold(@Nonnull UUID playerUuid) {
        Long v = playerShopSafeGoldMap().get(playerUuid.toString());
        return v != null ? Math.max(0L, v) : 0L;
    }

    public void addPlayerShopSafeGold(@Nonnull UUID playerUuid, long delta) {
        if (delta <= 0L) {
            return;
        }
        String key = playerUuid.toString();
        long next = getPlayerShopSafeGold(playerUuid) + delta;
        playerShopSafeGoldMap().put(key, next);
    }

    /** Removes up to {@code amount} from the player's safe balance; returns amount actually removed. */
    public long withdrawPlayerShopSafeGold(@Nonnull UUID playerUuid, long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        String key = playerUuid.toString();
        long bal = getPlayerShopSafeGold(playerUuid);
        long take = Math.min(bal, amount);
        if (take <= 0L) {
            return 0L;
        }
        long remain = bal - take;
        if (remain <= 0L) {
            playerShopSafeGoldMap().remove(key);
        } else {
            playerShopSafeGoldMap().put(key, remain);
        }
        return take;
    }

    @Nullable
    public Long getTreasuryLastTaxEpochDay() {
        return treasuryLastTaxEpochDay;
    }

    public void setTreasuryLastTaxEpochDay(@Nullable Long epochDay) {
        this.treasuryLastTaxEpochDay = epochDay;
    }

    @Nullable
    public Long getTreasuryLastTaxGameLocalDateEpochDay() {
        return treasuryLastTaxGameLocalDateEpochDay;
    }

    public void setTreasuryLastTaxGameLocalDateEpochDay(@Nullable Long epochDay) {
        this.treasuryLastTaxGameLocalDateEpochDay = epochDay;
    }

    @Nullable
    public String getCharterTaxPolicy() {
        return charterTaxPolicy != null && !charterTaxPolicy.isBlank() ? charterTaxPolicy.trim() : null;
    }

    public void setCharterTaxPolicy(@Nullable String charterTaxPolicy) {
        this.charterTaxPolicy = charterTaxPolicy != null ? charterTaxPolicy.trim() : null;
    }

    @Nullable
    public CharterTaxPolicy getCharterTaxPolicyEnum() {
        return CharterTaxPolicy.fromId(getCharterTaxPolicy());
    }

    @Nullable
    public String getCharterSpecialization() {
        return charterSpecialization != null && !charterSpecialization.isBlank() ? charterSpecialization.trim() : null;
    }

    public void setCharterSpecialization(@Nullable String charterSpecialization) {
        this.charterSpecialization = charterSpecialization != null ? charterSpecialization.trim() : null;
    }

    @Nullable
    public CharterSpecialization getCharterSpecializationEnum() {
        return CharterSpecialization.fromId(getCharterSpecialization());
    }

    /** Morning founder-monument tax bonus: one multiplier regardless of how many monuments are placed. */
    public boolean isFounderMonumentActive() {
        return founderMonumentCount > 0;
    }

    public int getFounderMonumentCount() {
        return founderMonumentCount;
    }

    public void incrementFounderMonumentPlaced() {
        founderMonumentCount++;
        founderMonumentActive = founderMonumentCount > 0;
    }

    public void decrementFounderMonumentPlaced() {
        if (founderMonumentCount > 0) {
            founderMonumentCount--;
        }
        founderMonumentActive = founderMonumentCount > 0;
    }

    @Nullable
    public String getActiveFeastKind() {
        return activeFeastKind != null && !activeFeastKind.isBlank() ? activeFeastKind.trim() : null;
    }

    public void setActiveFeastKind(@Nullable String activeFeastKind) {
        this.activeFeastKind = activeFeastKind != null && !activeFeastKind.isBlank() ? activeFeastKind.trim() : null;
    }

    @Nullable
    public Long getActiveFeastEndExclusiveDawnDay() {
        return activeFeastEndExclusiveDawnDay;
    }

    public void setActiveFeastEndExclusiveDawnDay(@Nullable Long activeFeastEndExclusiveDawnDay) {
        this.activeFeastEndExclusiveDawnDay = activeFeastEndExclusiveDawnDay;
    }

    @Nullable
    public Long getFeastBerrycircleCooldownEndExclusiveDawnDay() {
        return feastBerrycircleCooldownEndExclusiveDawnDay;
    }

    public void setFeastBerrycircleCooldownEndExclusiveDawnDay(@Nullable Long feastBerrycircleCooldownEndExclusiveDawnDay) {
        this.feastBerrycircleCooldownEndExclusiveDawnDay = feastBerrycircleCooldownEndExclusiveDawnDay;
    }

    @Nullable
    public String getFeastGatherPoiId() {
        return feastGatherPoiId != null && !feastGatherPoiId.isBlank() ? feastGatherPoiId.trim() : null;
    }

    public void setFeastGatherPoiId(@Nullable String feastGatherPoiId) {
        this.feastGatherPoiId = feastGatherPoiId != null && !feastGatherPoiId.isBlank() ? feastGatherPoiId.trim() : null;
    }

    public long getFeastGatherDeadlineEpochMs() {
        return feastGatherDeadlineEpochMs;
    }

    public void setFeastGatherDeadlineEpochMs(long feastGatherDeadlineEpochMs) {
        this.feastGatherDeadlineEpochMs = feastGatherDeadlineEpochMs;
    }

    public int getQuestBoardRankXp() {
        migrateQuestBoardFieldsIfNeeded();
        return questBoardRankXp;
    }

    public void setQuestBoardRankXp(int questBoardRankXp) {
        migrateQuestBoardFieldsIfNeeded();
        this.questBoardRankXp = Math.max(0, questBoardRankXp);
    }

    public void addQuestBoardRankXp(int amount) {
        if (amount <= 0) {
            return;
        }
        setQuestBoardRankXp(getQuestBoardRankXp() + amount);
    }

    @Nullable
    public Long getQuestBoardLastRefreshOnlineDawnDay() {
        return questBoardLastRefreshOnlineDawnDay;
    }

    public void setQuestBoardLastRefreshOnlineDawnDay(long epochDay) {
        this.questBoardLastRefreshOnlineDawnDay = epochDay;
    }

    @Nonnull
    public List<com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord> getQuestBoardSlots() {
        migrateQuestBoardFieldsIfNeeded();
        return questBoardSlots;
    }

    public void ensureQuestBoardSlotCount(int count) {
        migrateQuestBoardFieldsIfNeeded();
        while (questBoardSlots.size() < count) {
            questBoardSlots.add(com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord.empty());
        }
        while (questBoardSlots.size() > count) {
            questBoardSlots.remove(questBoardSlots.size() - 1);
        }
    }

    @Nonnull
    public List<String> getQuestBoardDrawPool() {
        migrateQuestBoardFieldsIfNeeded();
        return questBoardDrawPool;
    }

    public void setQuestBoardDrawPool(@Nonnull List<String> keys) {
        migrateQuestBoardFieldsIfNeeded();
        this.questBoardDrawPool = new ArrayList<>(keys);
    }

    @Nullable
    public com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord findAcceptedBoardQuestForGiver(@Nonnull UUID giverEntityUuid) {
        migrateQuestBoardFieldsIfNeeded();
        String id = giverEntityUuid.toString();
        for (com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord slot : questBoardSlots) {
            if (slot.isAccepted() && id.equals(slot.getGiverEntityUuid())) {
                return slot;
            }
        }
        return null;
    }

    @Nonnull
    public List<com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord> findAcceptedBoardQuestsForRole(@Nonnull String giverRoleId) {
        migrateQuestBoardFieldsIfNeeded();
        String role = giverRoleId.trim();
        List<com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord> out = new ArrayList<>();
        for (com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord slot : questBoardSlots) {
            if (slot.isAccepted() && role.equalsIgnoreCase(slot.getGiverRoleId() != null ? slot.getGiverRoleId().trim() : "")) {
                out.add(slot);
            }
        }
        return out;
    }

    @Nullable
    public com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord findBoardSlotByInstanceId(@Nonnull String instanceId) {
        migrateQuestBoardFieldsIfNeeded();
        if (instanceId.isBlank()) {
            return null;
        }
        for (com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord slot : questBoardSlots) {
            if (instanceId.equals(slot.instanceIdOrEmpty())) {
                return slot;
            }
        }
        return null;
    }

    @Nonnull
    public List<com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord> acceptedBoardQuestsSnapshot() {
        migrateQuestBoardFieldsIfNeeded();
        List<com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord> out = new ArrayList<>();
        for (com.hexvane.aetherhaven.questboard.QuestBoardSlotRecord slot : questBoardSlots) {
            if (slot.isAccepted()) {
                out.add(slot);
            }
        }
        return out;
    }

    @Nonnull
    public String getDisplayName() {
        migrateTownSocialFieldsIfNeeded();
        return displayName != null && !displayName.isBlank() ? displayName.trim() : "Town";
    }

    public void setDisplayName(@Nonnull String name) {
        this.displayName = name.trim();
    }

    @Nonnull
    public Map<String, String> getMemberRolesRaw() {
        migrateTownSocialFieldsIfNeeded();
        return memberRoles;
    }

    public boolean isMemberPlayer(@Nonnull UUID playerUuid) {
        if (getOwnerUuid().equals(playerUuid)) {
            return false;
        }
        return getMemberRolesRaw().containsKey(playerUuid.toString());
    }

    public boolean hasMemberOrOwner(@Nonnull UUID playerUuid) {
        if (getOwnerUuid().equals(playerUuid)) {
            return true;
        }
        return getMemberRolesRaw().containsKey(playerUuid.toString());
    }

    /**
     * Owner is not stored in the member map; returns {@link TownMemberRole#BOTH} as a sentinel for "full access".
     * For anyone who is not the owner and not in {@link #getMemberRolesRaw()}, returns null.
     */
    @Nullable
    public TownMemberRole getMemberRoleOrNull(@Nonnull UUID playerUuid) {
        if (getOwnerUuid().equals(playerUuid)) {
            return TownMemberRole.BOTH;
        }
        String s = getMemberRolesRaw().get(playerUuid.toString());
        if (s == null) {
            return null;
        }
        return TownMemberRole.fromSerialized(s);
    }

    @Nonnull
    public TownMemberPermissions getEffectiveMemberPermissions(@Nonnull UUID playerUuid) {
        migrateTownSocialFieldsIfNeeded();
        String key = playerUuid.toString();
        if (getOwnerUuid().equals(playerUuid)) {
            TownMemberPermissions o = getMemberPermissionsMap().get(key);
            return o != null ? o.copy() : TownMemberPermissions.fullMember();
        }
        TownMemberPermissions m = getMemberPermissionsMap().get(key);
        if (m != null) {
            return m.copy();
        }
        if (getMemberRolesRaw().containsKey(key)) {
            return TownMemberPermissions.fromRole(TownMemberRole.fromSerialized(getMemberRolesRaw().get(key))).copy();
        }
        return TownMemberPermissions.fullMember();
    }

    public boolean playerCanPlacePlots(@Nonnull UUID playerUuid) {
        return getEffectiveMemberPermissions(playerUuid).placePlots();
    }

    public boolean playerCanManageConstructions(@Nonnull UUID playerUuid) {
        return getEffectiveMemberPermissions(playerUuid).manageConstructions();
    }

    public boolean playerCanSpendTreasuryGold(@Nonnull UUID playerUuid) {
        return getEffectiveMemberPermissions(playerUuid).spendTreasuryGold();
    }

    public boolean playerCanOpenTreasuryPanel(@Nonnull UUID playerUuid) {
        return getEffectiveMemberPermissions(playerUuid).openTreasuryPanel();
    }

    public boolean playerCanAcceptQuests(@Nonnull UUID playerUuid) {
        return getEffectiveMemberPermissions(playerUuid).acceptQuests();
    }

    public boolean playerCanCompleteQuests(@Nonnull UUID playerUuid) {
        return getEffectiveMemberPermissions(playerUuid).completeQuests();
    }

    public boolean playerCanAbandonQuests(@Nonnull UUID playerUuid) {
        return getEffectiveMemberPermissions(playerUuid).abandonQuests();
    }

    public boolean playerCanReviveVillagers(@Nonnull UUID playerUuid) {
        return getEffectiveMemberPermissions(playerUuid).reviveVillagers();
    }

    /** Town owner always may remove plots from the town journal; members need the explicit permission. */
    public boolean playerCanRemovePlots(@Nonnull UUID playerUuid) {
        if (getOwnerUuid().equals(playerUuid)) {
            return true;
        }
        return getEffectiveMemberPermissions(playerUuid).removePlots();
    }

    /** Town owner always may list on player shop spots; members need the explicit permission. */
    public boolean playerCanUseShopSpots(@Nonnull UUID playerUuid) {
        if (getOwnerUuid().equals(playerUuid)) {
            return true;
        }
        if (!isMemberPlayer(playerUuid)) {
            return false;
        }
        return getEffectiveMemberPermissions(playerUuid).useShopSpots();
    }

    /** Legacy: true if the player may place plots or manage constructions (any former "build" capability). */
    public boolean playerHasBuildPermission(@Nonnull UUID playerUuid) {
        return playerCanPlacePlots(playerUuid) || playerCanManageConstructions(playerUuid);
    }

    /** Legacy: true if the player may accept, complete, or abandon quests. */
    public boolean playerHasQuestPermission(@Nonnull UUID playerUuid) {
        return playerCanAcceptQuests(playerUuid)
            || playerCanCompleteQuests(playerUuid)
            || playerCanAbandonQuests(playerUuid);
    }

    public void putMember(@Nonnull UUID playerUuid, @Nonnull TownMemberRole role) {
        if (getOwnerUuid().equals(playerUuid)) {
            return;
        }
        boolean newlyJoined = !getMemberRolesRaw().containsKey(playerUuid.toString());
        getMemberRolesRaw().put(playerUuid.toString(), role.name());
        getMemberPermissionsMap().put(playerUuid.toString(), TownMemberPermissions.fromRole(role));
        if (newlyJoined) {
            seedSharedCraftRecipeUnlocksForMember(playerUuid);
        }
    }

    /**
     * Queues every town-shared craft recipe for {@code playerUuid} so {@link TownSharedRecipeUnlockService} can apply
     * them when the player is in-world (covers members who join after those quests completed).
     */
    private void seedSharedCraftRecipeUnlocksForMember(@Nonnull UUID playerUuid) {
        migrateSharedRecipeUnlockFieldsIfNeeded();
        for (String rid : townSharedCraftRecipeItemIds) {
            if (rid != null && !rid.isBlank()) {
                queuePendingCraftRecipeUnlock(playerUuid, rid.trim());
            }
        }
    }

    public void addTownSharedCraftRecipeItemId(@Nonnull String recipeItemId) {
        migrateSharedRecipeUnlockFieldsIfNeeded();
        townSharedCraftRecipeItemIds.add(recipeItemId.trim());
    }

    public void queuePendingCraftRecipeUnlock(@Nonnull UUID playerUuid, @Nonnull String recipeItemId) {
        migrateSharedRecipeUnlockFieldsIfNeeded();
        String key = playerUuid.toString();
        String r = recipeItemId.trim();
        List<String> list = pendingCraftRecipeUnlockByPlayerUuid.computeIfAbsent(key, k -> new ArrayList<>());
        for (String existing : list) {
            if (existing.equals(r)) {
                return;
            }
        }
        list.add(r);
    }

    /**
     * @return a copy of pending recipe ids for this player and clears the pending list for them
     */
    @Nonnull
    public List<String> takeAndClearPendingCraftRecipeUnlocks(@Nonnull UUID playerUuid) {
        migrateSharedRecipeUnlockFieldsIfNeeded();
        List<String> removed = pendingCraftRecipeUnlockByPlayerUuid.remove(playerUuid.toString());
        return removed != null ? new ArrayList<>(removed) : new ArrayList<>();
    }

    public boolean hasPendingCraftRecipeUnlocks(@Nonnull UUID playerUuid) {
        migrateSharedRecipeUnlockFieldsIfNeeded();
        List<String> list = pendingCraftRecipeUnlockByPlayerUuid.get(playerUuid.toString());
        return list != null && !list.isEmpty();
    }

    /**
     * Sets granular permissions for a member or the owner (owner overrides are for testing; management UI remains
     * owner-only in code).
     */
    public void putMemberPermissions(@Nonnull UUID playerUuid, @Nonnull TownMemberPermissions permissions) {
        getMemberPermissionsMap().put(playerUuid.toString(), permissions.copy());
        if (!getOwnerUuid().equals(playerUuid)) {
            getMemberRolesRaw().put(playerUuid.toString(), permissions.toCoarseRole().name());
        }
    }

    public boolean removeMember(@Nonnull UUID playerUuid) {
        getMemberPermissionsMap().remove(playerUuid.toString());
        return getMemberRolesRaw().remove(playerUuid.toString()) != null;
    }

    @Nonnull
    public List<TownPendingInvite> getPendingInvites() {
        migrateTownSocialFieldsIfNeeded();
        return pendingInvites;
    }

    public void addPendingInvite(@Nonnull TownPendingInvite invite) {
        UUID invitee = invite.getInviteeUuid();
        getPendingInvites().removeIf(p -> p.getInviteeUuid().equals(invitee));
        getPendingInvites().add(invite);
    }

    public boolean removePendingInviteForInvitee(@Nonnull UUID inviteeUuid) {
        return getPendingInvites().removeIf(p -> p.getInviteeUuid().equals(inviteeUuid));
    }

    @Nullable
    public TownPendingInvite findPendingInvite(@Nonnull UUID inviteeUuid) {
        for (TownPendingInvite p : getPendingInvites()) {
            if (p.getInviteeUuid().equals(inviteeUuid)) {
                return p;
            }
        }
        return null;
    }

    @Nonnull
    public List<UUID> getMemberPlayerUuids() {
        List<UUID> out = new ArrayList<>();
        for (String k : getMemberRolesRaw().keySet()) {
            try {
                out.add(UUID.fromString(k));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return out;
    }
}
