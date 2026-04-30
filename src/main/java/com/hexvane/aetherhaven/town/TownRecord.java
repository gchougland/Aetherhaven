package com.hexvane.aetherhaven.town;

import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.production.PlotProductionState;
import com.hexvane.aetherhaven.reputation.VillagerReputationEntry;
import com.hypixel.hytale.logger.HytaleLogger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
     * NPC role ids (e.g. {@link com.hexvane.aetherhaven.AetherhavenConstants#NPC_MERCHANT}) that must never be chosen
     * when filling the inn visitor pool (e.g. after promotion to a permanent town role).
     */
    @SerializedName("innVisitorPoolExcludedRoleIds")
    private LinkedHashSet<String> innVisitorPoolExcludedRoleIds = new LinkedHashSet<>();

    /** Shared town treasury balance (gold coins); all treasury blocks in this town read/write this. */
    @SerializedName("treasuryGoldCoinCount")
    private long treasuryGoldCoinCount;

    /** Dawn-aligned game epoch day when daily treasury tithe was last applied ({@link com.hexvane.aetherhaven.reputation.VillagerReputationService#currentGameEpochDay}). */
    @Nullable
    @SerializedName("treasuryLastTaxEpochDay")
    private Long treasuryLastTaxEpochDay;

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

    @SerializedName("pendingInvites")
    private List<TownPendingInvite> pendingInvites = new ArrayList<>();

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

    /**
     * Workplace plot production storage: key plot UUID string, value slot cursors + item amounts
     * (see {@link com.hexvane.aetherhaven.production.ProductionTickSystem}).
     */
    @Nullable
    @SerializedName("plotProductionByPlotId")
    private Map<String, PlotProductionState> plotProductionByPlotId;

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

    @Nonnull
    public PlotProductionState getOrCreatePlotProduction(@Nonnull UUID plotId) {
        migratePlotProductionIfNeeded();
        return plotProductionByPlotId.computeIfAbsent(plotId.toString(), k -> PlotProductionState.empty());
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
        if (pendingInvites == null) {
            pendingInvites = new ArrayList<>();
        }
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
     * Elder, innkeeper, inn visitors, locked visitors, resident registry rows, and house assignments — every NPC entity
     * UUID the town persists. Used when stripping prefab volumes so these are never removed as debris.
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
            if (s == null || s.isBlank()) {
                continue;
            }
            try {
                UUID u = UUID.fromString(s.trim());
                if (!nil.equals(u)) {
                    out.add(u);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (String s : getInnLockedEntityUuids()) {
            if (s == null || s.isBlank()) {
                continue;
            }
            try {
                UUID u = UUID.fromString(s.trim());
                if (!nil.equals(u)) {
                    out.add(u);
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        for (ResidentNpcRecord r : getResidentNpcRecords()) {
            UUID u = r.getLastEntityUuid();
            if (!nil.equals(u)) {
                out.add(u);
            }
        }
        for (PlotInstance p : getPlotInstances()) {
            UUID h = p.getHomeResidentEntityUuid();
            if (h != null && !nil.equals(h)) {
                out.add(h);
            }
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
    }

    /** Initializes tracking entries for objectives that are not {@code journal} kind (future hooks). */
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

    /** True if any plot is COMPLETE with this construction id. */
    public boolean hasCompletePlotWithConstruction(@Nonnull String constructionId) {
        String c = constructionId.trim();
        if (c.isEmpty()) {
            return false;
        }
        for (PlotInstance p : getPlotInstances()) {
            if (p.getState() == PlotInstanceState.COMPLETE && c.equals(p.getConstructionId())) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public PlotInstance findCompletePlotWithConstruction(@Nonnull String constructionId) {
        String c = constructionId.trim();
        if (c.isEmpty()) {
            return null;
        }
        for (PlotInstance p : getPlotInstances()) {
            if (p.getState() == PlotInstanceState.COMPLETE && c.equals(p.getConstructionId())) {
                return p;
            }
        }
        return null;
    }

    /**
     * True if this villager NPC is listed as home resident on any complete residential plot in this town
     * (used for house-quest dialogue completion after assignment).
     */
    public boolean isNpcHomeResidentOnHousePlot(@Nonnull UUID npcEntityUuid) {
        for (PlotInstance p : getPlotInstances()) {
            if (p.getState() != PlotInstanceState.COMPLETE) {
                continue;
            }
            if (!AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE.equals(p.getConstructionId())) {
                continue;
            }
            UUID h = p.getHomeResidentEntityUuid();
            if (h != null && h.equals(npcEntityUuid)) {
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
            UUID h = p.getHomeResidentEntityUuid();
            if (h != null && h.equals(residentUuid)) {
                p.setHomeResidentEntityUuid(null);
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

    @Nullable
    public Long getTreasuryLastTaxEpochDay() {
        return treasuryLastTaxEpochDay;
    }

    public void setTreasuryLastTaxEpochDay(@Nullable Long epochDay) {
        this.treasuryLastTaxEpochDay = epochDay;
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

    /** Owner always true for both; members use {@link TownMemberRole}. */
    public boolean playerHasBuildPermission(@Nonnull UUID playerUuid) {
        if (getOwnerUuid().equals(playerUuid)) {
            return true;
        }
        TownMemberRole r = getMemberRoleOrNull(playerUuid);
        return r != null && r.allowsBuild();
    }

    /** Owner always true for both; members use {@link TownMemberRole}. */
    public boolean playerHasQuestPermission(@Nonnull UUID playerUuid) {
        if (getOwnerUuid().equals(playerUuid)) {
            return true;
        }
        TownMemberRole r = getMemberRoleOrNull(playerUuid);
        return r != null && r.allowsQuest();
    }

    public void putMember(@Nonnull UUID playerUuid, @Nonnull TownMemberRole role) {
        if (getOwnerUuid().equals(playerUuid)) {
            return;
        }
        getMemberRolesRaw().put(playerUuid.toString(), role.name());
    }

    public boolean removeMember(@Nonnull UUID playerUuid) {
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
