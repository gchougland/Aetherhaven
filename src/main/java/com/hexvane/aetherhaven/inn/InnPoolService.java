package com.hexvane.aetherhaven.inn;

import com.hypixel.hytale.math.vector.Rotation3f;

import com.hypixel.hytale.math.vector.Vector3fUtil;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.ResidentRegistryService;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.time.AetherhavenMorningWindow;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.PendingEntityRemovalService;
import com.hexvane.aetherhaven.villager.AetherhavenVillagerHandle;
import com.hexvane.aetherhaven.villager.NpcSpawnOriginUtil;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hexvane.aetherhaven.villager.audit.VillagerAuditContext;
import com.hexvane.aetherhaven.villager.data.InnPoolEntry;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.time.TimeModule;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.NPCPlugin;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Inn visitor pool only: up to two NPCs (merchant/blacksmith/farmer/priestess/miner/logger/rancher).
 * {@link TownRecord#getInnPoolNpcIds()} is
 * the source of truth for which visitors the mod spawned. Treasury tax is handled by {@link com.hexvane.aetherhaven.economy.TownEconomyTimeService}.
 * <p>
 * <b>Spawning</b> happens only during the morning window, at most once per calendar game day after the daily
 * refresh, and only when the inn's management block chunk is loaded — never to "replace" entries whose entities are
 * still unloaded elsewhere. <b>Pruning</b> never drops unlocked list
 * entries with a missing entity ref (unloaded); morning refresh can despawn and remove only when refs are valid.
 * <p>
 * Unlocked visitors are cleared at most once per calendar game day during the morning window ({@link WorldTimeResource}
 * clock). Locked visitors stay.
 */
public final class InnPoolService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    public static final int MAX_VISITORS = 2;

    private static final List<InnPoolEntry> LEGACY_INN_POOL = List.of(
        new InnPoolEntry(AetherhavenConstants.NPC_MERCHANT, TownVillagerBinding.KIND_VISITOR_MERCHANT, 0),
        new InnPoolEntry(AetherhavenConstants.NPC_BLACKSMITH, TownVillagerBinding.KIND_VISITOR_BLACKSMITH, 1),
        new InnPoolEntry(AetherhavenConstants.NPC_FARMER, TownVillagerBinding.KIND_VISITOR_FARMER, 2),
        new InnPoolEntry(AetherhavenConstants.NPC_PRIESTESS, TownVillagerBinding.KIND_VISITOR_PRIESTESS, 3),
        new InnPoolEntry(AetherhavenConstants.NPC_MINER, TownVillagerBinding.KIND_VISITOR_MINER, 4),
        new InnPoolEntry(AetherhavenConstants.NPC_LOGGER, TownVillagerBinding.KIND_VISITOR_LOGGER, 5),
        new InnPoolEntry(AetherhavenConstants.NPC_RANCHER, TownVillagerBinding.KIND_VISITOR_RANCHER, 6)
    );

    @Nonnull
    private static List<InnPoolEntry> innPoolOrLegacy(@Nonnull AetherhavenPlugin plugin) {
        var list = plugin.getVillagerDefinitionCatalog().innPoolEntriesSorted();
        return !list.isEmpty() ? list : LEGACY_INN_POOL;
    }

    @Nullable
    private static String visitorKindForRole(@Nonnull List<InnPoolEntry> pool, @Nonnull String roleId) {
        for (InnPoolEntry e : pool) {
            if (roleId.equals(e.npcRoleId())) {
                return e.visitorBindingKind();
            }
        }
        return null;
    }

    private static boolean isRoleEligibleForInnPool(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull List<InnPoolEntry> pool,
        @Nonnull String roleId
    ) {
        for (InnPoolEntry e : pool) {
            if (roleId.equals(e.npcRoleId())) {
                return e.requires().isEmpty() || e.requires().satisfiedBy(town, plugin);
            }
        }
        return true;
    }

    private static boolean isRoleInInnPoolCatalog(@Nonnull List<InnPoolEntry> pool, @Nonnull String roleId) {
        for (InnPoolEntry e : pool) {
            if (roleId.equals(e.npcRoleId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Full visitor-slot eligibility: catalog role, {@link InnPoolRequires}, no resident duplicate, not excluded after
     * promotion.
     */
    public static boolean isVisitorRoleEligible(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull String roleId
    ) {
        return isVisitorRoleEligible(plugin, town, store, innPoolOrLegacy(plugin), roleId.trim());
    }

    private static boolean isVisitorRoleEligible(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull List<InnPoolEntry> pool,
        @Nonnull String roleId
    ) {
        if (!isRoleInInnPoolCatalog(pool, roleId)) {
            return false;
        }
        if (!isRoleEligibleForInnPool(plugin, town, pool, roleId)) {
            return false;
        }
        if (town.getInnVisitorPoolExcludedRoleIds().contains(roleId)) {
            return false;
        }
        if (townHasResidentWithNpcRole(store, town, roleId)) {
            return false;
        }
        return true;
    }

    @Nullable
    private static int[] resolveSpawnLocal(
        @Nonnull List<InnPoolEntry> pool,
        @Nonnull String roleId,
        @Nonnull int[][] visitorSpawnLocals,
        int slotIndex
    ) {
        for (InnPoolEntry e : pool) {
            if (roleId.equals(e.npcRoleId()) && e.spawnLocal() != null) {
                return e.spawnLocal();
            }
        }
        if (visitorSpawnLocals.length == 0) {
            return null;
        }
        return visitorSpawnLocals[Math.min(slotIndex, visitorSpawnLocals.length - 1)];
    }

    private static final Object TICK_LOCK = new Object();
    private static String lastTickWorld;
    private static long lastTickGameEpochSecond = Long.MIN_VALUE;

    private InnPoolService() {}

    public static final class RepairReport {
        private int lockedQuestVisitors;
        private int promotedResidents;
        private int removedPoolEntries;
        private int removedDuplicateVisitors;
        private int removedOrphanVisitors;
        private int poolEntriesFixed;

        public int getLockedQuestVisitors() {
            return lockedQuestVisitors;
        }

        public int getPromotedResidents() {
            return promotedResidents;
        }

        public int getRemovedPoolEntries() {
            return removedPoolEntries;
        }

        public int getRemovedDuplicateVisitors() {
            return removedDuplicateVisitors;
        }

        public int getRemovedOrphanVisitors() {
            return removedOrphanVisitors;
        }

        public int getPoolEntriesFixed() {
            return poolEntriesFixed;
        }
    }

    public static final class ReconcileReport {
        private int removedDuplicates;
        private int removedOrphans;
        private int poolEntriesFixed;

        public int getRemovedDuplicates() {
            return removedDuplicates;
        }

        public int getRemovedOrphans() {
            return removedOrphans;
        }

        public int getPoolEntriesFixed() {
            return poolEntriesFixed;
        }
    }

    private record LoadedInnVisitor(@Nonnull UUID uuid, @Nonnull String roleId, boolean inPool, boolean questLocked) {}

    /** True when {@code binding} is an inn visitor for {@code town} (never matches other towns). */
    public static boolean isInnPoolVisitorForTown(@Nonnull TownRecord town, @Nullable TownVillagerBinding binding) {
        return binding != null
            && town.getTownId().equals(binding.getTownId())
            && TownVillagerBinding.isVisitorKind(binding.getKind());
    }

    /** True when {@code binding} points at a live town other than {@code town}. */
    public static boolean isVisitorBoundToLiveOtherTown(
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nullable TownVillagerBinding binding
    ) {
        if (binding == null) {
            return false;
        }
        try {
            UUID bindingTownId = binding.getTownId();
            if (town.getTownId().equals(bindingTownId)) {
                return false;
            }
            return tm.getTown(bindingTownId) != null;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** Visitor binding whose town id is missing from {@link TownManager} (deleted / stale save). */
    public static boolean isOrphanInnVisitorBinding(@Nonnull TownManager tm, @Nullable TownVillagerBinding binding) {
        if (binding == null || !TownVillagerBinding.isVisitorKind(binding.getKind())) {
            return false;
        }
        try {
            return tm.getTown(binding.getTownId()) == null;
        } catch (RuntimeException ex) {
            return true;
        }
    }

    /**
     * Loaded inn visitors for this town only ({@link TownVillagerBinding#isVisitorKind} + matching {@code townId}).
     */
    public static int countTownInnVisitorsInStore(@Nonnull Store<EntityStore> store, @Nonnull TownRecord town) {
        AtomicInteger count = new AtomicInteger();
        store.forEachEntityParallel(TownVillagerBinding.getComponentType(), (index, archetypeChunk, commandBuffer) -> {
            TownVillagerBinding b = archetypeChunk.getComponent(index, TownVillagerBinding.getComponentType());
            if (!isInnPoolVisitorForTown(town, b)) {
                return;
            }
            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            if (ref != null && ref.isValid()) {
                count.incrementAndGet();
            }
        });
        return count.get();
    }

  private static boolean townHasInnVisitorDrift(@Nonnull Store<EntityStore> store, @Nonnull TownRecord town) {
        if (countTownInnVisitorsInStore(store, town) > town.getInnPoolNpcIds().size()) {
            return true;
        }
        Map<String, Integer> roleCounts = new HashMap<>();
        store.forEachEntityParallel(TownVillagerBinding.getComponentType(), (index, archetypeChunk, commandBuffer) -> {
            TownVillagerBinding b = archetypeChunk.getComponent(index, TownVillagerBinding.getComponentType());
            if (!isInnPoolVisitorForTown(town, b)) {
                return;
            }
            var npcType = NPCEntity.getComponentType();
            NPCEntity npc = npcType != null ? archetypeChunk.getComponent(index, npcType) : null;
            String roleName = npc != null ? npc.getRoleName() : null;
            if (roleName == null || roleName.isBlank()) {
                return;
            }
            roleCounts.merge(roleName.trim(), 1, Integer::sum);
        });
        for (int c : roleCounts.values()) {
            if (c > 1) {
                return true;
            }
        }
        return countTownInnVisitorsInStore(store, town) > MAX_VISITORS;
    }

    private static int keeperPriority(@Nonnull LoadedInnVisitor v) {
        int p = 0;
        if (v.inPool()) {
            p -= 100;
        }
        if (v.questLocked()) {
            p -= 10;
        }
        return p;
    }

    private static int compareKeepers(@Nonnull LoadedInnVisitor a, @Nonnull LoadedInnVisitor b) {
        int pa = keeperPriority(a);
        int pb = keeperPriority(b);
        if (pa != pb) {
            return Integer.compare(pa, pb);
        }
        return a.uuid().toString().compareTo(b.uuid().toString());
    }

    @Nonnull
    private static Set<String> innPoolIdSet(@Nonnull TownRecord town) {
        Set<String> seen = new LinkedHashSet<>();
        for (String sid : town.getInnPoolNpcIds()) {
            if (sid != null && !sid.isBlank()) {
                seen.add(sid.trim().toLowerCase());
            }
        }
        return seen;
    }

    @Nonnull
    private static List<LoadedInnVisitor> collectLoadedInnVisitorsForTown(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town
    ) {
        Set<String> poolIds = innPoolIdSet(town);
        java.util.Queue<LoadedInnVisitor> out = new java.util.concurrent.ConcurrentLinkedQueue<>();
        store.forEachEntityParallel(TownVillagerBinding.getComponentType(), (index, archetypeChunk, commandBuffer) -> {
            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            if (ref == null || !ref.isValid()) {
                return;
            }
            TownVillagerBinding b = archetypeChunk.getComponent(index, TownVillagerBinding.getComponentType());
            if (!isInnPoolVisitorForTown(town, b)) {
                return;
            }
            var uuidType = UUIDComponent.getComponentType();
            UUIDComponent uc = uuidType != null ? archetypeChunk.getComponent(index, uuidType) : null;
            if (uc == null) {
                return;
            }
            var npcType = NPCEntity.getComponentType();
            NPCEntity npc = npcType != null ? archetypeChunk.getComponent(index, npcType) : null;
            String roleName = npc != null ? npc.getRoleName() : null;
            if (roleName == null || roleName.isBlank()) {
                return;
            }
            UUID uuid = uc.getUuid();
            boolean inPool = poolIds.contains(uuid.toString().toLowerCase());
            boolean questLocked = town.isInnVisitorLocked(uuid);
            out.add(new LoadedInnVisitor(uuid, roleName.trim(), inPool, questLocked));
        });
        return new ArrayList<>(out);
    }

    @Nonnull
    private static List<UUID> collectOrphanInnVisitorUuids(
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store
    ) {
        java.util.Queue<UUID> orphans = new java.util.concurrent.ConcurrentLinkedQueue<>();
        store.forEachEntityParallel(TownVillagerBinding.getComponentType(), (index, archetypeChunk, commandBuffer) -> {
            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            if (ref == null || !ref.isValid()) {
                return;
            }
            TownVillagerBinding b = archetypeChunk.getComponent(index, TownVillagerBinding.getComponentType());
            if (b == null || !TownVillagerBinding.isVisitorKind(b.getKind())) {
                return;
            }
            if (isVisitorBoundToLiveOtherTown(town, tm, b)) {
                return;
            }
            if (!isOrphanInnVisitorBinding(tm, b)) {
                return;
            }
            var uuidType = UUIDComponent.getComponentType();
            UUIDComponent uc = uuidType != null ? archetypeChunk.getComponent(index, uuidType) : null;
            if (uc != null) {
                orphans.add(uc.getUuid());
            }
        });
        return new ArrayList<>(orphans);
    }

    private static void syncInnPoolListFromKeepers(
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Set<UUID> keepUuids,
        @Nonnull List<LoadedInnVisitor> keepersInOrder
    ) {
        List<String> previous = new ArrayList<>(town.getInnPoolNpcIds());
        List<String> rebuilt = new ArrayList<>();
        for (String sid : previous) {
            UUID u = parseUuid(sid);
            if (u != null && keepUuids.contains(u)) {
                rebuilt.add(u.toString());
            }
        }
        for (LoadedInnVisitor keeper : keepersInOrder) {
            String sid = keeper.uuid().toString();
            boolean already = rebuilt.stream().anyMatch(s -> sid.equalsIgnoreCase(s));
            if (!already) {
                rebuilt.add(sid);
            }
        }
        if (rebuilt.size() > MAX_VISITORS) {
            rebuilt = new ArrayList<>(rebuilt.subList(0, MAX_VISITORS));
        }
        boolean changed =
            rebuilt.size() != town.getInnPoolNpcIds().size()
                || !new ArrayList<>(town.getInnPoolNpcIds()).equals(rebuilt);
        town.getInnPoolNpcIds().clear();
        town.getInnPoolNpcIds().addAll(rebuilt);
        dedupeInnPoolIds(town, tm);
        if (changed) {
            tm.updateTown(town);
        }
    }

    /**
     * Per-town inn pool reconcile: dedupe same-role visitors, cap at {@link #MAX_VISITORS}, sync {@link
     * TownRecord#getInnPoolNpcIds()}, optionally remove orphan visitor-kind NPCs with no live owning town.
     */
    @Nonnull
    public static ReconcileReport reconcileInnVisitorEntities(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        boolean purgeOrphans
    ) {
        ReconcileReport report = new ReconcileReport();
        List<LoadedInnVisitor> loaded = collectLoadedInnVisitorsForTown(store, town);
        if (loaded.isEmpty() && !purgeOrphans) {
            return report;
        }

        Map<String, LoadedInnVisitor> bestByRole = new LinkedHashMap<>();
        for (LoadedInnVisitor v : loaded) {
            LoadedInnVisitor existing = bestByRole.get(v.roleId());
            if (existing == null || compareKeepers(v, existing) < 0) {
                bestByRole.put(v.roleId(), v);
            }
        }

        List<LoadedInnVisitor> roleKeepers = new ArrayList<>(bestByRole.values());
        roleKeepers.sort(InnPoolService::compareKeepers);

        List<LoadedInnVisitor> finalKeepers = new ArrayList<>();
        for (int i = 0; i < roleKeepers.size() && finalKeepers.size() < MAX_VISITORS; i++) {
            finalKeepers.add(roleKeepers.get(i));
        }

        Set<UUID> keepUuids = new LinkedHashSet<>();
        for (LoadedInnVisitor k : finalKeepers) {
            keepUuids.add(k.uuid());
        }

        List<UUID> toRemove = new ArrayList<>();
        for (LoadedInnVisitor v : loaded) {
            if (!keepUuids.contains(v.uuid())) {
                toRemove.add(v.uuid());
            }
        }
        report.removedDuplicates = toRemove.size();

        if (purgeOrphans) {
            for (UUID orphan : collectOrphanInnVisitorUuids(town, tm, store)) {
                if (!toRemove.contains(orphan)) {
                    toRemove.add(orphan);
                    report.removedOrphans++;
                }
            }
        }

        if (!toRemove.isEmpty()) {
            PendingEntityRemovalService.scheduleAll(world, toRemove, "inn_visitor_despawn");
            LOGGER.atInfo().log(
                "Inn reconcile for town %s: scheduling removal of %s visitor(s) (%s duplicate(s), %s orphan(s))",
                town.getTownId(),
                toRemove.size(),
                report.removedDuplicates,
                report.removedOrphans
            );
        }

        int poolSizeBefore = town.getInnPoolNpcIds().size();
        if (!finalKeepers.isEmpty()) {
            syncInnPoolListFromKeepers(town, tm, keepUuids, finalKeepers);
        } else if (!toRemove.isEmpty()) {
            for (UUID u : toRemove) {
                String sid = u.toString();
                town.getInnPoolNpcIds().removeIf(s -> sid.equalsIgnoreCase(s != null ? s.trim() : ""));
                town.removeInnLockedEntity(u);
            }
        }
        if (town.getInnPoolNpcIds().size() != poolSizeBefore) {
            report.poolEntriesFixed++;
        }

        if (!finalKeepers.isEmpty()) {
            for (String sid : new ArrayList<>(town.getInnLockedEntityUuids())) {
                UUID u = parseUuid(sid);
                if (u != null && !keepUuids.contains(u)) {
                    town.removeInnLockedEntity(u);
                }
            }
        }
        if (report.poolEntriesFixed > 0 || !toRemove.isEmpty()) {
            tm.updateTown(town);
        }

        return report;
    }

    /**
     * Legacy throttled entry (at most once per game-second per world); prefer {@link #scheduleTickFromHub}.
     * <p>
     * Work is queued with {@link World#execute} so spawn/despawn does not run during {@link Store} tick processing.
     */
    public static void tickThrottled(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull WorldTimeResource wtr) {
        long sec = wtr.getGameTime().getEpochSecond();
        synchronized (TICK_LOCK) {
            String w = world.getName();
            if (w.equals(lastTickWorld) && sec == lastTickGameEpochSecond) {
                return;
            }
            lastTickWorld = w;
            lastTickGameEpochSecond = sec;
        }
        world.execute(() -> tick(world, plugin, wtr));
    }

    /**
     * {@link com.hexvane.aetherhaven.time.AetherhavenGameTimeCoordinatorSystem} calls this once per in-game minute
     * (smooth) or after a time discontinuity (along with {@link #catchUpAfterTimeJump}); no per-player tick spam.
     */
    public static void scheduleTickFromHub(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull WorldTimeResource wtr) {
        world.execute(() -> tick(world, plugin, wtr));
    }

    /**
     * When game time jumps forward (e.g. midnight to midday), run inn morning unlock refresh for each calendar day whose
     * configured morning hour fell strictly inside {@code (from, to]} and was not yet recorded on the town.
     */
    public static void catchUpAfterTimeJump(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull WorldTimeResource wtr,
        @Nonnull Instant from,
        @Nonnull Instant to
    ) {
        int morningStart = plugin.getConfig().get().getGameMorningStartHour();
        LinkedHashSet<Long> days = new LinkedHashSet<>();
        com.hexvane.aetherhaven.time.GameTimeEpochs.collectEpochDaysWhereMorningStartOccurred(
            from, to, morningStart, WorldTimeResource.ZONE_OFFSET, days
        );
        if (days.isEmpty()) {
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            town.migrateInnFieldsIfNeeded();
            syncInnPoolWithResidentBindings(town, store, tm);
            for (long epochDay : days) {
                Long last = town.getInnPoolLastMorningEpochDay();
                if (last != null && last >= epochDay) {
                    continue;
                }
                refreshUnlockedPoolForEpochMorning(town, tm, store, epochDay);
            }
        }
    }

    /**
     * Same visitor removals as {@link #morningUnlockedRefreshIfDue} when the inn morning timestamp for {@code epochDay}
     * was skipped by a time jump.
     */
    private static void refreshUnlockedPoolForEpochMorning(
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        long epochDay
    ) {
        for (String sid : new ArrayList<>(town.getInnPoolNpcIds())) {
            UUID u = parseUuid(sid);
            if (u == null) {
                town.getInnPoolNpcIds().remove(sid);
                continue;
            }
            if (town.isInnVisitorLocked(u)) {
                continue;
            }
            if (shouldPreserveInnVisitorFromQuestState(town, store, u)) {
                town.addInnLockedEntity(u);
                continue;
            }
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(u);
            if (ref == null) {
                continue;
            }
            if (!ref.isValid()) {
                town.getInnPoolNpcIds().remove(sid);
                continue;
            }
            town.getInnPoolNpcIds().remove(sid);
            if (isInnPoolListedEntityVisitorToDespawn(town, store, ref, u)) {
                VillagerAuditContext.removeEntity(store, ref, "inn_visitor_despawn");
            }
        }
        town.setInnPoolLastMorningEpochDay(epochDay);
        town.setInnPoolLastMorningGameDate(LocalDate.ofEpochDay(epochDay).toString());
        tm.updateTown(town);
    }

    public static void tick(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull WorldTimeResource wtr) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        var es = world.getEntityStore();
        Store<EntityStore> store = es != null ? es.getStore() : null;
        if (store == null) {
            return;
        }
        int morningStart = plugin.getConfig().get().getGameMorningStartHour();
        int morningEndEx = plugin.getConfig().get().getGameMorningEndHourExclusive();

        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            if (!town.isInnActive()) {
                continue;
            }
            if (!town.hasQuestCompleted(AetherhavenConstants.QUEST_BUILD_INN)) {
                continue;
            }
            if (town.getInnkeeperEntityUuid() == null) {
                continue;
            }
            PlotInstance innPlot =
                InnPlotResolver.resolveInnPlotForVisitors(town, plugin.getConstructionCatalog(), store);
            if (innPlot == null) {
                continue;
            }
            ConstructionDefinition innDef = InnPlotResolver.resolveInnDefinition(plugin, innPlot);
            if (innDef == null) {
                continue;
            }
            int[][] spawnLocals = innDef.getVisitorSpawnLocals();
            if (spawnLocals == null || spawnLocals.length < 1) {
                continue;
            }

            boolean innLoaded = isInnManagementChunkLoaded(world, innPlot, innDef);

            town.migrateInnFieldsIfNeeded();
            dedupeInnPoolIds(town, tm);
            if (innLoaded) {
                autoLockQuestCriticalVisitors(town, tm, store);
                pruneDeadVisitors(town, store, tm);
                trimInnPoolListToMax(town, tm, store);
                syncInnPoolWithResidentBindings(town, store, tm);
            }
            if (innLoaded) {
                morningUnlockedRefreshIfDue(town, tm, store, wtr, morningStart, morningEndEx);
                pruneDeadVisitors(town, store, tm);
                fillVisitorsAtDawnIfEligible(
                    world,
                    plugin,
                    town,
                    tm,
                    store,
                    innPlot,
                    innDef,
                    spawnLocals,
                    wtr,
                    morningStart,
                    morningEndEx
                );
            }
        }
    }

    /**
     * True when the chunk containing the inn management block (or plot sign as fallback) is in memory — i.e. the inn
     * prefab is present in the simulation, not only in {@link TownRecord} data.
     */
    private static boolean isInnManagementChunkLoaded(
        @Nonnull World world,
        @Nonnull PlotInstance innPlot,
        @Nonnull ConstructionDefinition innDef
    ) {
        Vector3i pos = managementBlockWorldPos(innPlot, innDef);
        if (pos == null) {
            pos = new Vector3i(innPlot.getSignX(), innPlot.getSignY(), innPlot.getSignZ());
        }
        long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z);
        return world.getChunkIfInMemory(chunkIndex) != null;
    }

    @Nullable
    private static Vector3i managementBlockWorldPos(@Nonnull PlotInstance innPlot, @Nonnull ConstructionDefinition innDef) {
        int[] m = innDef.getManagementBlockLocalPos();
        if (m == null || m.length != 3) {
            return null;
        }
        Vector3i anchor = innPlot.resolvePrefabAnchorWorld(innDef);
        Rotation yaw = innPlot.resolvePrefabYaw();
        Vector3i d = PrefabLocalOffset.rotate(yaw, m[0], m[1], m[2]);
        return new Vector3i(anchor.x + d.x, anchor.y + d.y, anchor.z + d.z);
    }

    /**
     * Remove pool UUIDs for NPCs that are no longer inn visitors (e.g. merchant promoted to {@link
     * TownVillagerBinding#KIND_MERCHANT}).
     */
    private static int syncInnPoolWithResidentBindings(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownManager tm
    ) {
        List<String> ids = town.getInnPoolNpcIds();
        int removed = 0;
        Iterator<String> it = ids.iterator();
        while (it.hasNext()) {
            String s = it.next();
            UUID u = parseUuid(s);
            if (u == null) {
                continue;
            }
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(u);
            if (ref == null || !ref.isValid()) {
                continue;
            }
            TownVillagerBinding b = store.getComponent(ref, TownVillagerBinding.getComponentType());
            if (b != null && b.getTownId().equals(town.getTownId()) && !TownVillagerBinding.isVisitorKind(b.getKind())) {
                var npcType = NPCEntity.getComponentType();
                NPCEntity npc = npcType != null ? store.getComponent(ref, npcType) : null;
                if (npc != null && npc.getRoleName() != null) {
                    town.addInnVisitorPoolExcludedRoleId(npc.getRoleName().trim());
                }
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            tm.updateTown(town);
        }
        return removed;
    }

    /** Drop duplicate UUID strings; order preserved. */
    private static boolean dedupeInnPoolIds(@Nonnull TownRecord town, @Nonnull TownManager tm) {
        List<String> ids = town.getInnPoolNpcIds();
        Set<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        boolean dup = false;
        for (String s : ids) {
            if (s == null || s.isBlank()) {
                dup = true;
                continue;
            }
            String t = s.trim();
            if (!seen.add(t.toLowerCase())) {
                dup = true;
                continue;
            }
            out.add(t);
        }
        if (!dup && out.size() == ids.size()) {
            return false;
        }
        ids.clear();
        ids.addAll(out);
        tm.updateTown(town);
        return true;
    }

    /**
     * If the saved list grew past two (should not happen), keep locked UUIDs first then trim to {@link #MAX_VISITORS}.
     * Drops despawn tracked visitors we are no longer keeping (no full-world scan).
     */
    private static boolean trimInnPoolListToMax(
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store
    ) {
        List<String> ids = town.getInnPoolNpcIds();
        if (ids.size() <= MAX_VISITORS) {
            return false;
        }
        List<String> sorted = new ArrayList<>(ids);
        sorted.sort(
            Comparator.comparing((String sid) -> {
                UUID u = parseUuid(sid);
                return u != null && town.isInnVisitorLocked(u) ? 0 : 1;
            }).thenComparing(s -> s)
        );
        List<String> keep = new ArrayList<>(sorted.subList(0, MAX_VISITORS));
        Set<String> keepSet = new HashSet<>(keep);
        for (String sid : sorted) {
            if (keepSet.contains(sid)) {
                continue;
            }
            UUID u = parseUuid(sid);
            if (u == null) {
                continue;
            }
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(u);
            if (ref != null && ref.isValid() && isInnPoolListedEntityVisitorToDespawn(town, store, ref, u)) {
                VillagerAuditContext.removeEntity(store, ref, "inn_visitor_despawn");
            }
        }
        ids.clear();
        ids.addAll(keep);
        tm.updateTown(town);
        return true;
    }

    /**
     * Remove list entries only when we can prove the entity is gone: unlocked entries with a missing ref are kept
     * (entity may live in an unloaded chunk). Locked entries with missing refs stay until resolved.
     */
    private static boolean pruneDeadVisitors(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownManager tm
    ) {
        Iterator<String> it = town.getInnPoolNpcIds().iterator();
        boolean changed = false;
        while (it.hasNext()) {
            String s = it.next();
            UUID u = parseUuid(s);
            if (u == null) {
                it.remove();
                changed = true;
                continue;
            }
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(u);
            if (ref != null && ref.isValid()) {
                continue;
            }
            if (town.isInnVisitorLocked(u)) {
                continue;
            }
            if (ref == null) {
                continue;
            }
            it.remove();
            changed = true;
        }
        if (changed) {
            tm.updateTown(town);
        }
        return changed;
    }

    @Nullable
    private static UUID parseUuid(@Nullable String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * {@link TownRecord#getInnPoolNpcIds()} should only list inn visitors. When a UUID is stale (e.g. promoted
     * resident) we still drop it from the list, but we must not call {@code removeEntity} on non-visitors.
     */
    private static boolean isInnPoolListedEntityVisitorToDespawn(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull UUID poolEntryUuid
    ) {
        TownVillagerBinding b = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
        if (b == null || !b.getTownId().equals(town.getTownId())) {
            return false;
        }
        if (!TownVillagerBinding.isVisitorKind(b.getKind())) {
            return false;
        }
        UUIDComponent uuidComp = store.getComponent(npcRef, UUIDComponent.getComponentType());
        return uuidComp != null && poolEntryUuid.equals(uuidComp.getUuid());
    }

    /**
     * Once per calendar game day, during the morning window: remove unlocked pool NPCs (so fill can spawn new roles).
     * Locked quest NPCs stay.
     */
    private static boolean morningUnlockedRefreshIfDue(
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nonnull WorldTimeResource wtr,
        int morningStartHour,
        int morningEndExclusive
    ) {
        long epochDay = wtr.getGameDateTime().toLocalDate().toEpochDay();
        Long lastDay = town.getInnPoolLastMorningEpochDay();
        if (lastDay != null && lastDay >= epochDay) {
            return false;
        }
        if (!isMorningForInnPool(wtr, morningStartHour, morningEndExclusive)) {
            return false;
        }

        removeUnlockedInnVisitors(town, store);
        town.setInnPoolLastMorningEpochDay(epochDay);
        town.setInnPoolLastMorningGameDate(wtr.getGameDateTime().toLocalDate().toString());
        tm.updateTown(town);
        return true;
    }

    /** Removes unlocked inn pool visitors and despawns them. Quest locked visitors stay. */
    private static void removeUnlockedInnVisitors(@Nonnull TownRecord town, @Nonnull Store<EntityStore> store) {
        for (String sid : new ArrayList<>(town.getInnPoolNpcIds())) {
            UUID u = parseUuid(sid);
            if (u == null) {
                town.getInnPoolNpcIds().remove(sid);
                continue;
            }
            if (town.isInnVisitorLocked(u)) {
                continue;
            }
            if (shouldPreserveInnVisitorFromQuestState(town, store, u)) {
                town.addInnLockedEntity(u);
                continue;
            }
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(u);
            if (ref == null) {
                continue;
            }
            if (!ref.isValid()) {
                town.getInnPoolNpcIds().remove(sid);
                continue;
            }
            town.getInnPoolNpcIds().remove(sid);
            if (isInnPoolListedEntityVisitorToDespawn(town, store, ref, u)) {
                VillagerAuditContext.removeEntity(store, ref, "inn_visitor_despawn");
            }
        }
    }

    public enum RerollOutcome {
        OK,
        INN_NOT_READY,
        INN_NOT_LOADED
    }

    @Nonnull
    @SuppressWarnings("deprecation") // Store.isProcessing() is the only way to detect mid-tick writes
    public static RerollOutcome rerollUnlockedVisitorsForTown(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store
    ) {
        if (store.isProcessing()) {
            UUID townId = town.getTownId();
            world.execute(
                () -> {
                    Store<EntityStore> liveStore =
                        world.getEntityStore() != null ? world.getEntityStore().getStore() : null;
                    if (liveStore == null) {
                        return;
                    }
                    TownManager liveTm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
                    TownRecord liveTown = liveTm.getTown(townId);
                    if (liveTown == null) {
                        return;
                    }
                    rerollUnlockedVisitorsForTown(world, plugin, liveTown, liveTm, liveStore);
                }
            );
            return RerollOutcome.OK;
        }
        if (!town.isInnActive()
            || !town.hasQuestCompleted(AetherhavenConstants.QUEST_BUILD_INN)
            || town.getInnkeeperEntityUuid() == null) {
            return RerollOutcome.INN_NOT_READY;
        }
        PlotInstance innPlot =
            InnPlotResolver.resolveInnPlotForVisitors(town, plugin.getConstructionCatalog(), store);
        if (innPlot == null) {
            return RerollOutcome.INN_NOT_READY;
        }
        ConstructionDefinition innDef = InnPlotResolver.resolveInnDefinition(plugin, innPlot);
        if (innDef == null) {
            return RerollOutcome.INN_NOT_READY;
        }
        int[][] spawnLocals = innDef.getVisitorSpawnLocals();
        if (spawnLocals == null || spawnLocals.length < 1) {
            return RerollOutcome.INN_NOT_READY;
        }
        if (!isInnManagementChunkLoaded(world, innPlot, innDef)) {
            return RerollOutcome.INN_NOT_LOADED;
        }
        town.migrateInnFieldsIfNeeded();
        dedupeInnPoolIds(town, tm);
        autoLockQuestCriticalVisitors(town, tm, store);
        removeUnlockedInnVisitors(town, store);
        tm.updateTown(town);
        fillEmptyInnVisitorSlotsAtSpawns(world, plugin, town, tm, store, innPlot, innDef);
        reconcileInnVisitorEntities(world, town, tm, store, false);
        tm.updateTown(town);
        return RerollOutcome.OK;
    }

    private static boolean isMorningForInnPool(
        @Nonnull WorldTimeResource wtr,
        int morningStartHour,
        int morningEndExclusive
    ) {
        return AetherhavenMorningWindow.isGameMorning(wtr, morningStartHour, morningEndExclusive);
    }

    /**
     * Runs after world bootstrap. Must not touch {@link World#getEntityStore()} synchronously from {@code AddWorldEvent}
     * — {@code EntityStore#getStore()} is often still null there; we defer to the world execution queue like
     * {@link InnkeeperSpawnService#reconcileAfterWorldLoad}.
     */
    public static void reconcileAfterWorldLoad(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        world.execute(() -> reconcileAfterWorldLoadOnWorldThread(world, plugin));
    }

    private static void reconcileAfterWorldLoadOnWorldThread(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        var entityStore = world.getEntityStore();
        Store<EntityStore> store = entityStore != null ? entityStore.getStore() : null;
        if (store == null) {
            LOGGER.atWarning().log("Inn pool reconcile skipped: entity store not ready for world %s", world.getName());
            return;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TimeModule mod = TimeModule.get();
        java.time.Instant now = java.time.Instant.now();
        if (mod != null) {
            WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
            if (wtr != null) {
                now = wtr.getGameTime();
            }
        }
        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            town.migrateInnFieldsIfNeeded();
            dedupeInnPoolIds(town, tm);
            trimInnPoolListToMax(town, tm, store);
            reconcileInnVisitorEntities(world, town, tm, store, true);
        }
    }

    /**
     * Spawns visitors only during the morning window, after the daily refresh for this calendar day has run (so we
     * never fill on generic ticks or server load).
     */
    private static void fillVisitorsAtDawnIfEligible(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotInstance innPlot,
        @Nonnull ConstructionDefinition innDef,
        @Nonnull int[][] spawnLocals,
        @Nonnull WorldTimeResource wtr,
        int morningStartHour,
        int morningEndExclusive
    ) {
        if (!isMorningForInnPool(wtr, morningStartHour, morningEndExclusive)) {
            return;
        }
        long epochDay = wtr.getGameDateTime().toLocalDate().toEpochDay();
        Long lastDay = town.getInnPoolLastMorningEpochDay();
        if (lastDay == null || lastDay != epochDay) {
            return;
        }
        Long lastFillDay = town.getInnPoolLastFillEpochDay();
        if (lastFillDay != null && lastFillDay >= epochDay) {
            return;
        }
        fillEmptySlotsForSpawn(world, plugin, town, tm, store, innPlot, innDef, spawnLocals, wtr);
        town.setInnPoolLastFillEpochDay(epochDay);
        tm.updateTown(town);
    }

    /**
     * Eligibility changes (e.g. town hall complete) take effect at the next dawn fill; vacant slots are not filled
     * mid-day.
     */
    public static void tryFillOpenSlotsAfterTownStateChange(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town
    ) {
        // Dawn-only fill: see fillVisitorsAtDawnIfEligible.
    }

    private static void fillEmptySlotsForSpawn(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotInstance innPlot,
        @Nonnull ConstructionDefinition innDef,
        @Nonnull int[][] spawnLocals,
        @Nonnull WorldTimeResource wtr
    ) {
        fillEmptySlotsForSpawn(
            world, plugin, town, tm, store, innPlot, innDef, spawnLocals, wtr, false
        );
    }

    /**
     * Fills open inn visitor slots at {@code visitorSpawnLocals}. When {@code ignorePendingMissingRef} is true, skips the
     * guard that blocks fill while unlocked pool UUIDs lack entity refs (inn bell handles those explicitly first).
     */
    public static void fillEmptyInnVisitorSlotsAtSpawns(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotInstance innPlot,
        @Nonnull ConstructionDefinition innDef
    ) {
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        if (wtr == null) {
            return;
        }
        int[][] spawnLocals = innDef.getVisitorSpawnLocals();
        if (spawnLocals == null || spawnLocals.length == 0) {
            return;
        }
        fillEmptySlotsForSpawn(
            world, plugin, town, tm, store, innPlot, innDef, spawnLocals, wtr, true
        );
    }

    /**
     * True if at least one inn pool role could still fill an open visitor slot (same eligibility as morning fill).
     */
    public static boolean hasEligibleInnPoolRoleForFill(
        @Nonnull TownRecord town,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store
    ) {
        if (town.getInnPoolNpcIds().size() >= MAX_VISITORS) {
            return false;
        }
        Set<String> presentRoles = new LinkedHashSet<>(collectTownVisitorNpcRolesFromStore(store, town));
        mergeQuestCriticalRolesWhenLockedVisitorsUnresolved(town, store, presentRoles);
        List<InnPoolEntry> pool = innPoolOrLegacy(plugin);
        List<String> mergedOrder = mergedVisitorRoleOrder(town, plugin, store);
        for (String roleId : mergedOrder) {
            if (!isRoleEligibleForInnPool(plugin, town, pool, roleId)) {
                continue;
            }
            if (town.getInnVisitorPoolExcludedRoleIds().contains(roleId)) {
                continue;
            }
            if (townHasResidentWithNpcRole(store, town, roleId)) {
                continue;
            }
            if (presentRoles.contains(roleId)) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static void fillEmptySlotsForSpawn(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotInstance innPlot,
        @Nonnull ConstructionDefinition innDef,
        @Nonnull int[][] spawnLocals,
        @Nonnull WorldTimeResource wtr,
        boolean ignorePendingMissingRef
    ) {
        if (!ignorePendingMissingRef && hasPendingUnlockedMissingRef(town, store)) {
            return;
        }

        if (townHasInnVisitorDrift(store, town)) {
            reconcileInnVisitorEntities(world, town, tm, store, true);
        }
        if (countTownInnVisitorsInStore(store, town) >= MAX_VISITORS) {
            return;
        }

        Set<String> presentRoles = new LinkedHashSet<>(collectTownVisitorNpcRolesFromStore(store, town));
        mergeQuestCriticalRolesWhenLockedVisitorsUnresolved(town, store, presentRoles);

        long epochDay = wtr.getGameDateTime().toLocalDate().toEpochDay();
        long seed =
            town.getTownId().getLeastSignificantBits()
                ^ (long) world.getName().hashCode() << 1
                ^ epochDay * 0x9E3779B97F4A7C15L
                ^ wtr.getGameTime().toEpochMilli();
        List<InnPoolEntry> pool = innPoolOrLegacy(plugin);
        List<String> order = prioritizedInnRoleOrder(town);
        List<String> shuffledPoolOrder = new ArrayList<>();
        for (InnPoolEntry e : pool) {
            String rid = e.npcRoleId();
            if (rid != null && !rid.isBlank() && isRoleEligibleForInnPool(plugin, town, pool, rid)) {
                shuffledPoolOrder.add(rid);
            }
        }
        Collections.shuffle(shuffledPoolOrder, new Random(seed));
        Set<String> seen = new LinkedHashSet<>();
        List<String> mergedOrder = new ArrayList<>();
        for (String roleId : order) {
            if (roleId != null && !roleId.isBlank() && seen.add(roleId)) {
                mergedOrder.add(roleId);
            }
        }
        for (String rid : shuffledPoolOrder) {
            if (seen.add(rid)) {
                mergedOrder.add(rid);
            }
        }

        for (String roleId : mergedOrder) {
            if (town.getInnPoolNpcIds().size() >= MAX_VISITORS) {
                break;
            }
            if (countTownInnVisitorsInStore(store, town) >= MAX_VISITORS) {
                break;
            }
            if (!isRoleEligibleForInnPool(plugin, town, pool, roleId)) {
                continue;
            }
            if (town.getInnVisitorPoolExcludedRoleIds().contains(roleId)) {
                continue;
            }
            if (townHasResidentWithNpcRole(store, town, roleId)) {
                continue;
            }
            if (presentRoles.contains(roleId)) {
                continue;
            }
            String kind = visitorKindForRole(pool, roleId);
            if (kind == null) {
                kind = TownVillagerBinding.KIND_VISITOR_MERCHANT;
            }
            int slotIndex = town.getInnPoolNpcIds().size();
            int[] local = resolveSpawnLocal(pool, roleId, spawnLocals, slotIndex);
            if (local == null || local.length != 3) {
                break;
            }
            UUID spawned = spawnVisitor(world, plugin, town, store, innPlot, innDef, local, roleId, kind, "INN_MORNING_FILL", slotIndex);
            if (spawned == null) {
                break;
            }
            town.getInnPoolNpcIds().add(spawned.toString());
            if (isRoleRequiredByActiveInnQuest(town, roleId)) {
                town.addInnLockedEntity(spawned);
            }
            presentRoles.add(roleId);
            tm.updateTown(town);
            InnVisitorShopPromotion.tryPromoteReadyWorkplaces(world, plugin, town, tm);
        }
    }

    /**
     * True if this town already has a non-visitor villager with the given NPC role (e.g. promoted merchant at the stall).
     * Prevents spawning a second inn visitor with the same role.
     */
    public static boolean townHasResidentWithNpcRole(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull String roleId
    ) {
        AtomicBoolean found = new AtomicBoolean(false);
        // Parallel callbacks run on ForkJoin workers — must use chunk.getComponent, not Store.getComponent (world thread only).
        store.forEachEntityParallel(TownVillagerBinding.getComponentType(), (index, archetypeChunk, commandBuffer) -> {
            if (found.get()) {
                return;
            }
            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            if (ref == null || !ref.isValid()) {
                return;
            }
            TownVillagerBinding b = archetypeChunk.getComponent(index, TownVillagerBinding.getComponentType());
            if (b == null || !b.getTownId().equals(town.getTownId()) || TownVillagerBinding.isVisitorKind(b.getKind())) {
                return;
            }
            var npcType = NPCEntity.getComponentType();
            NPCEntity npc = npcType != null ? archetypeChunk.getComponent(index, npcType) : null;
            if (npc != null && roleId.equals(npc.getRoleName())) {
                found.set(true);
            }
        });
        return found.get();
    }

    /**
     * Inn visitor roles currently present in the entity store for this town. Uses a parallel archetype walk so we still
     * see visitors even when UUID-to-ref lookup ({@code getRefFromUUID}) lags registration (same world-thread caveat as
     * {@link #townHasResidentWithNpcRole}).
     */
    @Nonnull
    private static Set<String> collectTownVisitorNpcRolesFromStore(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town
    ) {
        Set<String> roles = ConcurrentHashMap.newKeySet();
        store.forEachEntityParallel(TownVillagerBinding.getComponentType(), (index, archetypeChunk, commandBuffer) -> {
            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            if (ref == null || !ref.isValid()) {
                return;
            }
            TownVillagerBinding b = archetypeChunk.getComponent(index, TownVillagerBinding.getComponentType());
            if (b == null || !b.getTownId().equals(town.getTownId()) || !TownVillagerBinding.isVisitorKind(b.getKind())) {
                return;
            }
            var npcType = NPCEntity.getComponentType();
            NPCEntity npc = npcType != null ? archetypeChunk.getComponent(index, npcType) : null;
            String roleName = npc != null ? npc.getRoleName() : null;
            if (roleName != null && !roleName.isBlank()) {
                roles.add(roleName.trim());
            }
        });
        return roles;
    }

    /**
     * When a quest-locked pool UUID has no valid ref (chunk unloaded or UUID index lag), {@link
     * #collectTownVisitorNpcRolesFromStore} may miss that visitor. Treat active inn-quest roles as taken so we do not
     * spawn another copy of the same role.
     */
    private static void mergeQuestCriticalRolesWhenLockedVisitorsUnresolved(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull Set<String> presentRoles
    ) {
        boolean anyLockedMissingRef = false;
        for (String sid : town.getInnPoolNpcIds()) {
            UUID u = parseUuid(sid);
            if (u == null || !town.isInnVisitorLocked(u)) {
                continue;
            }
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(u);
            if (ref != null && ref.isValid()) {
                continue;
            }
            anyLockedMissingRef = true;
            break;
        }
        if (!anyLockedMissingRef) {
            return;
        }
        if (town.hasQuestActive(AetherhavenConstants.QUEST_BLACKSMITH_SHOP)) {
            presentRoles.add(AetherhavenConstants.NPC_BLACKSMITH);
        }
        if (town.hasQuestActive(AetherhavenConstants.QUEST_MERCHANT_STALL)) {
            presentRoles.add(AetherhavenConstants.NPC_MERCHANT);
        }
        if (town.hasQuestActive(AetherhavenConstants.QUEST_FARM_PLOT)) {
            presentRoles.add(AetherhavenConstants.NPC_FARMER);
        }
        if (town.hasQuestActive(AetherhavenConstants.QUEST_GAIA_ALTAR)) {
            presentRoles.add(AetherhavenConstants.NPC_PRIESTESS);
        }
        if (town.hasQuestActive(AetherhavenConstants.QUEST_MINERS_HUT)) {
            presentRoles.add(AetherhavenConstants.NPC_MINER);
        }
        if (town.hasQuestActive(AetherhavenConstants.QUEST_LUMBERMILL)) {
            presentRoles.add(AetherhavenConstants.NPC_LOGGER);
        }
        if (town.hasQuestActive(AetherhavenConstants.QUEST_BARN)) {
            presentRoles.add(AetherhavenConstants.NPC_RANCHER);
        }
        if (town.hasQuestActive(AetherhavenConstants.QUEST_BUILD_GUILD_HALL)) {
            presentRoles.add(AetherhavenConstants.GUILD_MASTER_NPC_ROLE_ID);
        }
    }

    /** Unlocked pool UUIDs still without a store ref (e.g. still loading after restart). */
    private static boolean hasPendingUnlockedMissingRef(@Nonnull TownRecord town, @Nonnull Store<EntityStore> store) {
        for (String sid : town.getInnPoolNpcIds()) {
            UUID u = parseUuid(sid);
            if (u == null) {
                continue;
            }
            if (town.isInnVisitorLocked(u)) {
                continue;
            }
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(u);
            if (ref == null || !ref.isValid()) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static UUID spawnVisitor(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotInstance innPlot,
        @Nonnull ConstructionDefinition innDef,
        @Nonnull int[] local,
        @Nonnull String roleId,
        @Nonnull String villagerKind,
        @Nonnull String spawnSource,
        int slotIndex
    ) {
        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            return null;
        }
        Vector3i anchor = innPlot.resolvePrefabAnchorWorld(innDef);
        var yaw = innPlot.resolvePrefabYaw();
        Vector3i d = PrefabLocalOffset.rotate(yaw, local[0], local[1], local[2]);
        int wx = anchor.x + d.x;
        int wy = anchor.y + d.y;
        int wz = anchor.z + d.z;
        Vector3d pos = new Vector3d(wx + 0.5, wy, wz + 0.5);
        pos = com.hexvane.aetherhaven.autonomy.VillagerBlockUtil.snapNpcFeetToStand(world, pos);
        var pair = npc.spawnNPC(store, roleId, null, pos, Rotation3f.ZERO);
        if (pair == null) {
            LOGGER.atWarning().log("Failed to spawn inn visitor %s for town %s", roleId, town.getTownId());
            return null;
        }
        Ref<EntityStore> ref = pair.first();
        store.putComponent(ref, VillagerNeeds.getComponentType(), VillagerNeeds.full());
        String handle = "Villager_" + villagerKind + "_" + shortHex(town.getTownId());
        store.putComponent(ref, AetherhavenVillagerHandle.getComponentType(), new AetherhavenVillagerHandle(handle));
        store.putComponent(
            ref,
            TownVillagerBinding.getComponentType(),
            new TownVillagerBinding(town.getTownId(), villagerKind, innPlot.getPlotId())
        );
        NpcSpawnOriginUtil.attach(
            store,
            ref,
            spawnSource,
            "roleId=" + roleId + ",kind=" + villagerKind + ",slot=" + slotIndex,
            world,
            pos
        );
        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        return uuidComp != null ? uuidComp.getUuid() : null;
    }

    /**
     * World position for an inn visitor spawn slot (center of block column, feet at local Y).
     */
    @Nullable
    public static Vector3d resolveVisitorSpawnWorldPosition(
        @Nonnull World world,
        @Nonnull PlotInstance innPlot,
        @Nonnull ConstructionDefinition innDef,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull String roleId,
        int slotIndex
    ) {
        int[][] spawnLocals = innDef.getVisitorSpawnLocals();
        if (spawnLocals == null || spawnLocals.length == 0) {
            return null;
        }
        List<InnPoolEntry> pool = innPoolOrLegacy(plugin);
        int[] local = resolveSpawnLocal(pool, roleId, spawnLocals, slotIndex);
        if (local == null || local.length != 3) {
            return null;
        }
        Vector3i anchor = innPlot.resolvePrefabAnchorWorld(innDef);
        var yaw = innPlot.resolvePrefabYaw();
        Vector3i d = PrefabLocalOffset.rotate(yaw, local[0], local[1], local[2]);
        int wx = anchor.x + d.x;
        int wy = anchor.y + d.y;
        int wz = anchor.z + d.z;
        return com.hexvane.aetherhaven.autonomy.VillagerBlockUtil.snapNpcFeetToStand(
            world,
            new Vector3d(wx + 0.5, wy, wz + 0.5)
        );
    }

    /**
     * Spawns an inn visitor at the spawn local for {@code slotIndex} (same placement as morning fill).
     */
    @Nullable
    public static UUID spawnInnVisitorAtSlot(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotInstance innPlot,
        @Nonnull ConstructionDefinition innDef,
        @Nonnull String roleId,
        @Nonnull String villagerKind,
        int slotIndex
    ) {
        int[][] spawnLocals = innDef.getVisitorSpawnLocals();
        if (spawnLocals == null || spawnLocals.length == 0) {
            return null;
        }
        List<InnPoolEntry> pool = innPoolOrLegacy(plugin);
        int[] local = resolveSpawnLocal(pool, roleId, spawnLocals, slotIndex);
        if (local == null || local.length != 3) {
            return null;
        }
        return spawnVisitor(world, plugin, town, store, innPlot, innDef, local, roleId, villagerKind, "INN_MORNING_FILL", slotIndex);
    }

    /**
     * Spawns an inn visitor at the spawn local for {@code slotIndex} with an explicit spawn-origin tag.
     */
    @Nullable
    public static UUID spawnInnVisitorAtSlot(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotInstance innPlot,
        @Nonnull ConstructionDefinition innDef,
        @Nonnull String roleId,
        @Nonnull String villagerKind,
        int slotIndex,
        @Nonnull String spawnSource
    ) {
        int[][] spawnLocals = innDef.getVisitorSpawnLocals();
        if (spawnLocals == null || spawnLocals.length == 0) {
            return null;
        }
        List<InnPoolEntry> pool = innPoolOrLegacy(plugin);
        int[] local = resolveSpawnLocal(pool, roleId, spawnLocals, slotIndex);
        if (local == null || local.length != 3) {
            return null;
        }
        return spawnVisitor(world, plugin, town, store, innPlot, innDef, local, roleId, villagerKind, spawnSource, slotIndex);
    }

    @Nonnull
    private static String shortHex(@Nonnull UUID townId) {
        String hex = townId.toString().replace("-", "");
        return hex.length() >= 8 ? hex.substring(0, 8) : hex;
    }

    /**
     * Spawns an inn visitor at an explicit world position (e.g. debug villager reset). {@code innPlot} supplies the
     * preferred plot id for {@link TownVillagerBinding} when non-null.
     */
    @Nullable
    public static UUID spawnVisitorAtWorldPosition(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull String roleId,
        @Nonnull String villagerKind,
        @Nonnull Vector3d worldPosition,
        @Nullable PlotInstance innPlot
    ) {
        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            return null;
        }
        var pair = npc.spawnNPC(store, roleId, null, worldPosition, Rotation3f.ZERO);
        if (pair == null) {
            LOGGER.atWarning().log("Failed to spawn inn visitor %s for town %s at reset position", roleId, town.getTownId());
            return null;
        }
        Ref<EntityStore> ref = pair.first();
        store.putComponent(ref, VillagerNeeds.getComponentType(), VillagerNeeds.full());
        String handle = "Villager_" + villagerKind + "_" + shortHex(town.getTownId());
        store.putComponent(ref, AetherhavenVillagerHandle.getComponentType(), new AetherhavenVillagerHandle(handle));
        UUID preferred = innPlot != null ? innPlot.getPlotId() : null;
        store.putComponent(
            ref,
            TownVillagerBinding.getComponentType(),
            new TownVillagerBinding(town.getTownId(), villagerKind, preferred)
        );
        World world = store.getExternalData().getWorld();
        NpcSpawnOriginUtil.attach(
            store,
            ref,
            "INN_DEBUG_SPAWN",
            "roleId=" + roleId + ",kind=" + villagerKind + ",caller=spawnVisitorAtWorldPosition",
            world,
            worldPosition
        );
        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        return uuidComp != null ? uuidComp.getUuid() : null;
    }

    /**
     * Same visitor role ordering as morning inn fill: quest-priority roles first, then shuffled catalog order.
     */
    @Nonnull
    public static List<String> mergedVisitorRoleOrder(
        @Nonnull TownRecord town,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store
    ) {
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        if (wtr == null) {
            return new ArrayList<>();
        }
        World world = store.getExternalData().getWorld();
        long epochDay = wtr.getGameDateTime().toLocalDate().toEpochDay();
        long seed =
            town.getTownId().getLeastSignificantBits()
                ^ (long) world.getName().hashCode() << 1
                ^ epochDay * 0x9E3779B97F4A7C15L
                ^ wtr.getGameTime().toEpochMilli();
        List<InnPoolEntry> pool = innPoolOrLegacy(plugin);
        List<String> order = prioritizedInnRoleOrder(town);
        List<String> shuffledPoolOrder = new ArrayList<>();
        for (InnPoolEntry e : pool) {
            String rid = e.npcRoleId();
            if (rid != null && !rid.isBlank() && isRoleEligibleForInnPool(plugin, town, pool, rid)) {
                shuffledPoolOrder.add(rid);
            }
        }
        Collections.shuffle(shuffledPoolOrder, new Random(seed));
        Set<String> seen = new LinkedHashSet<>();
        List<String> mergedOrder = new ArrayList<>();
        for (String roleId : order) {
            if (roleId != null && !roleId.isBlank() && seen.add(roleId)) {
                mergedOrder.add(roleId);
            }
        }
        for (String rid : shuffledPoolOrder) {
            if (seen.add(rid)) {
                mergedOrder.add(rid);
            }
        }
        return mergedOrder;
    }

    @Nonnull
    public static String visitorBindingKindForRole(@Nonnull AetherhavenPlugin plugin, @Nonnull String roleId) {
        String k = visitorKindForRole(innPoolOrLegacy(plugin), roleId.trim());
        return k != null ? k : TownVillagerBinding.KIND_VISITOR_MERCHANT;
    }

    /**
     * Fills inn visitor pool slots up to {@link #MAX_VISITORS} with roles that are not already town residents,
     * respecting exclusions and active-inn-quest priority (same ordering as morning fill). Spawns near {@code basePos}
     * with X offsets starting at {@code slotOffsetStart}.
     */
    public static void fillRemainingInnVisitorSlotsNear(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nullable PlotInstance innPlot,
        @Nonnull Vector3d basePos,
        int slotOffsetStart
    ) {
        if (innPlot == null) {
            return;
        }
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        if (wtr == null) {
            return;
        }
        if (townHasInnVisitorDrift(store, town)) {
            reconcileInnVisitorEntities(world, town, tm, store, true);
        }
        if (countTownInnVisitorsInStore(store, town) >= MAX_VISITORS) {
            return;
        }
        Set<String> presentRoles = new LinkedHashSet<>(collectTownVisitorNpcRolesFromStore(store, town));
        mergeQuestCriticalRolesWhenLockedVisitorsUnresolved(town, store, presentRoles);

        List<String> mergedOrder = mergedVisitorRoleOrder(town, plugin, store);
        List<InnPoolEntry> pool = innPoolOrLegacy(plugin);

        int slot = slotOffsetStart;
        for (String roleId : mergedOrder) {
            if (town.getInnPoolNpcIds().size() >= MAX_VISITORS) {
                break;
            }
            if (countTownInnVisitorsInStore(store, town) >= MAX_VISITORS) {
                break;
            }
            if (!isRoleEligibleForInnPool(plugin, town, pool, roleId)) {
                continue;
            }
            if (town.getInnVisitorPoolExcludedRoleIds().contains(roleId)) {
                continue;
            }
            if (townHasResidentWithNpcRole(store, town, roleId)) {
                continue;
            }
            if (presentRoles.contains(roleId)) {
                continue;
            }
            String kind = visitorKindForRole(pool, roleId);
            if (kind == null) {
                kind = TownVillagerBinding.KIND_VISITOR_MERCHANT;
            }
            Vector3d pos = new Vector3d(basePos.x + slot * 1.25, basePos.y, basePos.z);
            slot++;
            UUID spawned = spawnVisitorAtWorldPosition(store, town, roleId, kind, pos, innPlot);
            if (spawned == null) {
                break;
            }
            town.getInnPoolNpcIds().add(spawned.toString());
            if (isRoleRequiredByActiveInnQuest(town, roleId)) {
                town.addInnLockedEntity(spawned);
            }
            presentRoles.add(roleId);
            tm.updateTown(town);
            InnVisitorShopPromotion.tryPromoteReadyWorkplaces(world, plugin, town, tm);
        }
    }

    public static boolean innQuestLocksVisitorRole(@Nonnull TownRecord town, @Nonnull String roleId) {
        return isRoleRequiredByActiveInnQuest(town, roleId.trim());
    }

    private static boolean isRoleRequiredByActiveInnQuest(@Nonnull TownRecord town, @Nonnull String roleId) {
        if (AetherhavenConstants.NPC_BLACKSMITH.equals(roleId)) {
            return town.hasQuestActive(AetherhavenConstants.QUEST_BLACKSMITH_SHOP);
        }
        if (AetherhavenConstants.NPC_MERCHANT.equals(roleId)) {
            return town.hasQuestActive(AetherhavenConstants.QUEST_MERCHANT_STALL);
        }
        if (AetherhavenConstants.NPC_FARMER.equals(roleId)) {
            return town.hasQuestActive(AetherhavenConstants.QUEST_FARM_PLOT);
        }
        if (AetherhavenConstants.NPC_PRIESTESS.equals(roleId)) {
            return town.hasQuestActive(AetherhavenConstants.QUEST_GAIA_ALTAR);
        }
        if (AetherhavenConstants.NPC_MINER.equals(roleId)) {
            return town.hasQuestActive(AetherhavenConstants.QUEST_MINERS_HUT);
        }
        if (AetherhavenConstants.NPC_LOGGER.equals(roleId)) {
            return town.hasQuestActive(AetherhavenConstants.QUEST_LUMBERMILL);
        }
        if (AetherhavenConstants.NPC_RANCHER.equals(roleId)) {
            return town.hasQuestActive(AetherhavenConstants.QUEST_BARN);
        }
        if (AetherhavenConstants.GUILD_MASTER_NPC_ROLE_ID.equals(roleId)) {
            return town.hasQuestActive(AetherhavenConstants.QUEST_BUILD_GUILD_HALL);
        }
        if (AetherhavenConstants.NPC_CRYSTAL_KEEPER.equals(roleId)) {
            return town.hasQuestActive(AetherhavenConstants.QUEST_CRYSTAL_KEEPERS_SHOP);
        }
        if (AetherhavenConstants.NPC_PYROTECHNIC.equals(roleId)) {
            return town.hasQuestActive(AetherhavenConstants.QUEST_PYROTECHNIC_SHOP);
        }
        if (AetherhavenConstants.NPC_FLORIST.equals(roleId)) {
            return town.hasQuestActive(AetherhavenConstants.QUEST_FLORIST_SHOP);
        }
        if (AetherhavenConstants.NPC_CHEF.equals(roleId)) {
            return town.hasQuestActive(AetherhavenConstants.QUEST_CHEF_RESTAURANT);
        }
        if (AetherhavenConstants.NPC_BUILDER.equals(roleId)) {
            return town.hasQuestActive(AetherhavenConstants.QUEST_BUILDERS_HUT);
        }
        return false;
    }

    private static boolean hasAnyActiveInnVisitorQuest(@Nonnull TownRecord town) {
        return town.hasQuestActive(AetherhavenConstants.QUEST_BLACKSMITH_SHOP)
            || town.hasQuestActive(AetherhavenConstants.QUEST_MERCHANT_STALL)
            || town.hasQuestActive(AetherhavenConstants.QUEST_FARM_PLOT)
            || town.hasQuestActive(AetherhavenConstants.QUEST_GAIA_ALTAR)
            || town.hasQuestActive(AetherhavenConstants.QUEST_MINERS_HUT)
            || town.hasQuestActive(AetherhavenConstants.QUEST_LUMBERMILL)
            || town.hasQuestActive(AetherhavenConstants.QUEST_BARN)
            || town.hasQuestActive(AetherhavenConstants.QUEST_CRYSTAL_KEEPERS_SHOP)
            || town.hasQuestActive(AetherhavenConstants.QUEST_PYROTECHNIC_SHOP)
            || town.hasQuestActive(AetherhavenConstants.QUEST_FLORIST_SHOP)
            || town.hasQuestActive(AetherhavenConstants.QUEST_CHEF_RESTAURANT)
            || town.hasQuestActive(AetherhavenConstants.QUEST_BUILDERS_HUT)
            || town.hasQuestActive(AetherhavenConstants.QUEST_BUILD_GUILD_HALL);
    }

    private static boolean shouldPreserveInnVisitorFromQuestState(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID entityUuid
    ) {
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(entityUuid);
        if (ref == null || !ref.isValid()) {
            return hasAnyActiveInnVisitorQuest(town);
        }
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        String roleId = npc != null ? npc.getRoleName() : null;
        if (roleId == null || roleId.isBlank()) {
            return hasAnyActiveInnVisitorQuest(town);
        }
        return isRoleRequiredByActiveInnQuest(town, roleId);
    }

    private static void autoLockQuestCriticalVisitors(
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store
    ) {
        boolean changed = false;
        for (String sid : town.getInnPoolNpcIds()) {
            UUID u = parseUuid(sid);
            if (u == null || town.isInnVisitorLocked(u)) {
                continue;
            }
            if (shouldPreserveInnVisitorFromQuestState(town, store, u)) {
                town.addInnLockedEntity(u);
                changed = true;
            }
        }
        if (changed) {
            tm.updateTown(town);
        }
    }

    @Nonnull
    private static List<String> prioritizedInnRoleOrder(@Nonnull TownRecord town) {
        List<String> out = new ArrayList<>();
        if (town.hasQuestActive(AetherhavenConstants.QUEST_BLACKSMITH_SHOP)) {
            out.add(AetherhavenConstants.NPC_BLACKSMITH);
        }
        if (town.hasQuestActive(AetherhavenConstants.QUEST_MERCHANT_STALL)) {
            out.add(AetherhavenConstants.NPC_MERCHANT);
        }
        if (town.hasQuestActive(AetherhavenConstants.QUEST_FARM_PLOT)) {
            out.add(AetherhavenConstants.NPC_FARMER);
        }
        if (town.hasQuestActive(AetherhavenConstants.QUEST_GAIA_ALTAR)) {
            out.add(AetherhavenConstants.NPC_PRIESTESS);
        }
        if (town.hasQuestActive(AetherhavenConstants.QUEST_MINERS_HUT)) {
            out.add(AetherhavenConstants.NPC_MINER);
        }
        if (town.hasQuestActive(AetherhavenConstants.QUEST_LUMBERMILL)) {
            out.add(AetherhavenConstants.NPC_LOGGER);
        }
        if (town.hasQuestActive(AetherhavenConstants.QUEST_BARN)) {
            out.add(AetherhavenConstants.NPC_RANCHER);
        }
        if (town.hasQuestActive(AetherhavenConstants.QUEST_BUILD_GUILD_HALL)) {
            out.add(AetherhavenConstants.GUILD_MASTER_NPC_ROLE_ID);
        }
        if (town.hasQuestActive(AetherhavenConstants.QUEST_CRYSTAL_KEEPERS_SHOP)) {
            out.add(AetherhavenConstants.NPC_CRYSTAL_KEEPER);
        }
        if (town.hasQuestActive(AetherhavenConstants.QUEST_PYROTECHNIC_SHOP)) {
            out.add(AetherhavenConstants.NPC_PYROTECHNIC);
        }
        if (town.hasQuestActive(AetherhavenConstants.QUEST_FLORIST_SHOP)) {
            out.add(AetherhavenConstants.NPC_FLORIST);
        }
        if (town.hasQuestActive(AetherhavenConstants.QUEST_CHEF_RESTAURANT)) {
            out.add(AetherhavenConstants.NPC_CHEF);
        }
        if (town.hasQuestActive(AetherhavenConstants.QUEST_BUILDERS_HUT)) {
            out.add(AetherhavenConstants.NPC_BUILDER);
        }
        if (town.hasQuestCompleted(AetherhavenConstants.QUEST_BUILD_TOWN_HALL)
            && !town.isGuildHallActive()
            && !town.getInnVisitorPoolExcludedRoleIds().contains(AetherhavenConstants.GUILD_MASTER_NPC_ROLE_ID)) {
            out.add(AetherhavenConstants.GUILD_MASTER_NPC_ROLE_ID);
        }
        return out;
    }

    @Nonnull
    public static RepairReport repairInnPoolForTown(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store
    ) {
        return repairInnPoolForTown(world, plugin, town, tm, store, false);
    }

    @Nonnull
    @SuppressWarnings("deprecation") // Store.isProcessing() is the only way to detect mid-tick writes
    public static RepairReport repairInnPoolForTown(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        boolean fillOpenSlots
    ) {
        // putComponent is illegal while EntityTickingSystem holds the store (e.g. plot-creator Use interaction).
        if (store.isProcessing()) {
            UUID townId = town.getTownId();
            world.execute(
                () -> {
                    Store<EntityStore> liveStore =
                        world.getEntityStore() != null ? world.getEntityStore().getStore() : null;
                    if (liveStore == null) {
                        return;
                    }
                    TownManager liveTm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
                    TownRecord liveTown = liveTm.getTown(townId);
                    if (liveTown == null) {
                        return;
                    }
                    repairInnPoolForTown(world, plugin, liveTown, liveTm, liveStore, fillOpenSlots);
                }
            );
            return new RepairReport();
        }
        RepairReport report = new RepairReport();
        List<InnPoolEntry> pool = innPoolOrLegacy(plugin);
        town.migrateInnFieldsIfNeeded();
        dedupeInnPoolIds(town, tm);
        ReconcileReport reconcile = reconcileInnVisitorEntities(world, town, tm, store, true);
        report.removedDuplicateVisitors = reconcile.getRemovedDuplicates();
        report.removedOrphanVisitors = reconcile.getRemovedOrphans();
        report.poolEntriesFixed = reconcile.getPoolEntriesFixed();
        syncExcludedRolesFromResidents(town, store, tm, pool);
        report.lockedQuestVisitors = repairQuestLocksCount(town, store);
        autoLockQuestCriticalVisitors(town, tm, store);
        report.promotedResidents = promoteEligibleVisitorsToResidents(world, plugin, town, tm, store);
        report.removedPoolEntries = syncInnPoolWithResidentBindings(town, store, tm);
        report.removedPoolEntries += removeIneligiblePoolVisitors(town, plugin, tm, store, pool);
        trimInnPoolListToMax(town, tm, store);
        if (fillOpenSlots
            && town.getInnPoolNpcIds().size() < MAX_VISITORS
            && countTownInnVisitorsInStore(store, town) < MAX_VISITORS) {
            PlotInstance innPlot =
                InnPlotResolver.resolveInnPlotForVisitors(town, plugin.getConstructionCatalog(), store);
            if (innPlot != null) {
                ConstructionDefinition innDef = InnPlotResolver.resolveInnDefinition(plugin, innPlot);
                if (innDef != null) {
                    fillEmptyInnVisitorSlotsAtSpawns(world, plugin, town, tm, store, innPlot, innDef);
                }
            }
        }
        if (fillOpenSlots) {
            ReconcileReport afterFill = reconcileInnVisitorEntities(world, town, tm, store, false);
            report.removedDuplicateVisitors += afterFill.getRemovedDuplicates();
            report.poolEntriesFixed += afterFill.getPoolEntriesFixed();
        }
        return report;
    }

    /**
     * Removes every inn visitor NPC for this town ({@link TownVillagerBinding#isVisitorKind}). Used before the inn bell
     * respawns guests so listed, orphaned, or stale visitors cannot duplicate pool slots.
     */
    public static void despawnAllTownInnVisitors(@Nonnull TownRecord town, @Nonnull Store<EntityStore> store) {
        java.util.Queue<Ref<EntityStore>> refs = new java.util.concurrent.ConcurrentLinkedQueue<>();
        store.forEachEntityParallel(TownVillagerBinding.getComponentType(), (index, archetypeChunk, commandBuffer) -> {
            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            if (ref == null || !ref.isValid()) {
                return;
            }
            TownVillagerBinding b = archetypeChunk.getComponent(index, TownVillagerBinding.getComponentType());
            if (!isInnPoolVisitorForTown(town, b)) {
                return;
            }
            refs.add(ref);
        });
        for (Ref<EntityStore> ref : refs) {
            if (ref.isValid()) {
                VillagerAuditContext.removeEntity(store, ref, "inn_visitor_despawn");
            }
        }
    }

    /** Mark inn-pool roles that already have a non-visitor town member so fill logic does not spawn duplicates. */
    private static void syncExcludedRolesFromResidents(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownManager tm,
        @Nonnull List<InnPoolEntry> pool
    ) {
        Set<String> poolRoleIds = new LinkedHashSet<>();
        for (InnPoolEntry e : pool) {
            String rid = e.npcRoleId();
            if (rid != null && !rid.isBlank()) {
                poolRoleIds.add(rid.trim());
            }
        }
        if (poolRoleIds.isEmpty()) {
            return;
        }
        Set<String> residentPoolRoles = ConcurrentHashMap.newKeySet();
        store.forEachEntityParallel(TownVillagerBinding.getComponentType(), (index, archetypeChunk, commandBuffer) -> {
            TownVillagerBinding b = archetypeChunk.getComponent(index, TownVillagerBinding.getComponentType());
            if (b == null || !b.getTownId().equals(town.getTownId()) || TownVillagerBinding.isVisitorKind(b.getKind())) {
                return;
            }
            var npcType = NPCEntity.getComponentType();
            NPCEntity npc = npcType != null ? archetypeChunk.getComponent(index, npcType) : null;
            String roleName = npc != null ? npc.getRoleName() : null;
            if (roleName == null || roleName.isBlank()) {
                return;
            }
            String rid = roleName.trim();
            if (poolRoleIds.contains(rid)) {
                residentPoolRoles.add(rid);
            }
        });
        boolean changed = false;
        for (String rid : residentPoolRoles) {
            if (!town.getInnVisitorPoolExcludedRoleIds().contains(rid)) {
                town.addInnVisitorPoolExcludedRoleId(rid);
                changed = true;
            }
        }
        if (changed) {
            tm.updateTown(town);
        }
    }

    /**
     * Drop unlocked pool visitors whose role is no longer eligible (requirements, excluded after promotion, or a
     * resident already holds that role). Quest-locked visitors stay until their quest no longer needs them.
     */
    private static int removeIneligiblePoolVisitors(
        @Nonnull TownRecord town,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nonnull List<InnPoolEntry> pool
    ) {
        int removed = 0;
        for (String sid : new ArrayList<>(town.getInnPoolNpcIds())) {
            UUID u = parseUuid(sid);
            if (u == null) {
                town.getInnPoolNpcIds().remove(sid);
                removed++;
                continue;
            }
            if (town.isInnVisitorLocked(u) || shouldPreserveInnVisitorFromQuestState(town, store, u)) {
                continue;
            }
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(u);
            if (ref == null || !ref.isValid()) {
                continue;
            }
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            String roleId = npc != null ? npc.getRoleName() : null;
            if (roleId == null || roleId.isBlank()) {
                continue;
            }
            roleId = roleId.trim();
            if (isVisitorRoleEligible(plugin, town, store, pool, roleId)) {
                continue;
            }
            town.getInnPoolNpcIds().removeIf(s -> sid.equalsIgnoreCase(s != null ? s.trim() : ""));
            town.removeInnLockedEntity(u);
            removed++;
            if (isInnPoolListedEntityVisitorToDespawn(town, store, ref, u)) {
                VillagerAuditContext.removeEntity(store, ref, "inn_visitor_despawn");
            }
        }
        if (removed > 0) {
            tm.updateTown(town);
        }
        return removed;
    }

    private static int repairQuestLocksCount(@Nonnull TownRecord town, @Nonnull Store<EntityStore> store) {
        int count = 0;
        for (String sid : town.getInnPoolNpcIds()) {
            UUID u = parseUuid(sid);
            if (u == null || town.isInnVisitorLocked(u)) {
                continue;
            }
            if (shouldPreserveInnVisitorFromQuestState(town, store, u)) {
                count++;
            }
        }
        return count;
    }

    /**
     * When an inn visitor NPC dies, drop it from the active pool. Replacement waits until the next dawn fill.
     */
    public static void onVisitorEntityDeath(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull UUID entityUuid
    ) {
        boolean changed = removeVisitorFromPool(town, entityUuid);
        if (changed) {
            tm.updateTown(town);
        }
    }

    /** @return true if the UUID was listed in {@link TownRecord#getInnPoolNpcIds()} */
    public static boolean removeVisitorFromPool(@Nonnull TownRecord town, @Nonnull UUID entityUuid) {
        String sid = entityUuid.toString();
        boolean removed = town.getInnPoolNpcIds().removeIf(s -> sid.equalsIgnoreCase(s != null ? s.trim() : ""));
        town.removeInnLockedEntity(entityUuid);
        return removed;
    }

    /**
     * When a visitor is quest-locked but missing from {@link TownRecord#getInnPoolNpcIds()}, add them so vacant-slot
     * fill logic does not treat their slot as open.
     */
    public static void ensureVisitorListedInInnPool(@Nonnull TownRecord town, @Nonnull UUID entityUuid) {
        String sid = entityUuid.toString();
        for (String existing : town.getInnPoolNpcIds()) {
            if (sid.equalsIgnoreCase(existing != null ? existing.trim() : "")) {
                return;
            }
        }
        if (town.getInnPoolNpcIds().size() < MAX_VISITORS) {
            town.getInnPoolNpcIds().add(sid);
        }
    }

    private static int promoteEligibleVisitorsToResidents(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store
    ) {
        int promoted = 0;
        for (String sid : new ArrayList<>(town.getInnPoolNpcIds())) {
            UUID u = parseUuid(sid);
            if (u == null) {
                continue;
            }
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(u);
            if (ref == null || !ref.isValid()) {
                continue;
            }
            TownVillagerBinding b = store.getComponent(ref, TownVillagerBinding.getComponentType());
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
            if (b == null || npc == null || uuidComp == null || !b.getTownId().equals(town.getTownId())
                || !TownVillagerBinding.isVisitorKind(b.getKind()) || npc.getRoleName() == null || npc.getRoleName().isBlank()) {
                continue;
            }
            String roleId = npc.getRoleName().trim();
            String constructionId;
            String residentKind;
            if (AetherhavenConstants.NPC_BLACKSMITH.equals(roleId)) {
                constructionId = AetherhavenConstants.CONSTRUCTION_PLOT_BLACKSMITH_SHOP;
                residentKind = TownVillagerBinding.KIND_BLACKSMITH;
            } else if (AetherhavenConstants.NPC_MERCHANT.equals(roleId)) {
                constructionId = AetherhavenConstants.CONSTRUCTION_PLOT_MARKET_STALL;
                residentKind = TownVillagerBinding.KIND_MERCHANT;
            } else if (AetherhavenConstants.NPC_FARMER.equals(roleId)) {
                constructionId = AetherhavenConstants.CONSTRUCTION_PLOT_FARM;
                residentKind = TownVillagerBinding.KIND_FARMER;
            } else if (AetherhavenConstants.NPC_PRIESTESS.equals(roleId)) {
                constructionId = AetherhavenConstants.CONSTRUCTION_PLOT_GAIA_ALTAR;
                residentKind = TownVillagerBinding.KIND_PRIESTESS;
            } else if (AetherhavenConstants.NPC_MINER.equals(roleId)) {
                constructionId = AetherhavenConstants.CONSTRUCTION_PLOT_MINERS_HUT;
                residentKind = TownVillagerBinding.KIND_MINER;
            } else if (AetherhavenConstants.NPC_LOGGER.equals(roleId)) {
                constructionId = AetherhavenConstants.CONSTRUCTION_PLOT_LUMBERMILL;
                residentKind = TownVillagerBinding.KIND_LOGGER;
            } else if (AetherhavenConstants.NPC_RANCHER.equals(roleId)) {
                constructionId = AetherhavenConstants.CONSTRUCTION_PLOT_BARN;
                residentKind = TownVillagerBinding.KIND_RANCHER;
            } else if (AetherhavenConstants.GUILD_MASTER_NPC_ROLE_ID.equals(roleId)) {
                constructionId = AetherhavenConstants.CONSTRUCTION_PLOT_GUILD_HALL;
                residentKind = TownVillagerBinding.KIND_GUILD_MASTER;
            } else if (AetherhavenConstants.BARD_NPC_ROLE_ID.equals(roleId)) {
                constructionId = AetherhavenConstants.CONSTRUCTION_PLOT_GUILD_HALL;
                residentKind = TownVillagerBinding.KIND_BARD;
            } else if (AetherhavenConstants.NPC_CRYSTAL_KEEPER.equals(roleId)) {
                constructionId = AetherhavenConstants.CONSTRUCTION_PLOT_CRYSTAL_KEEPERS_SHOP;
                residentKind = TownVillagerBinding.KIND_CRYSTAL_KEEPER;
            } else if (AetherhavenConstants.NPC_PYROTECHNIC.equals(roleId)) {
                constructionId = AetherhavenConstants.CONSTRUCTION_PLOT_BOMB_SHOP;
                residentKind = TownVillagerBinding.KIND_PYROTECHNIC;
            } else if (AetherhavenConstants.NPC_FLORIST.equals(roleId)) {
                constructionId = AetherhavenConstants.CONSTRUCTION_PLOT_FLOWER_SHOP;
                residentKind = TownVillagerBinding.KIND_FLORIST;
            } else if (AetherhavenConstants.NPC_CHEF.equals(roleId)) {
                constructionId = AetherhavenConstants.CONSTRUCTION_PLOT_RESTAURANT;
                residentKind = TownVillagerBinding.KIND_CHEF;
            } else if (AetherhavenConstants.NPC_BUILDER.equals(roleId)) {
                constructionId = AetherhavenConstants.CONSTRUCTION_PLOT_BUILDERS_HUT;
                residentKind = TownVillagerBinding.KIND_BUILDER;
            } else {
                continue;
            }
            PlotInstance residentPlot = town.findCompletePlotWithConstruction(plugin.getConstructionCatalog(), constructionId);
            if (residentPlot == null) {
                continue;
            }
            store.putComponent(
                ref,
                TownVillagerBinding.getComponentType(),
                new TownVillagerBinding(town.getTownId(), residentKind, residentPlot.getPlotId(), residentPlot.getPlotId())
            );
            town.getInnPoolNpcIds().removeIf(x -> u.toString().equalsIgnoreCase(x));
            town.removeInnLockedEntity(u);
            town.addInnVisitorPoolExcludedRoleId(roleId);
            if (AetherhavenConstants.GUILD_MASTER_NPC_ROLE_ID.equals(roleId)) {
                town.setGuildHallActive(true);
            }
            ResidentRegistryService.upsert(town, tm, roleId, residentKind, residentPlot.getPlotId(), uuidComp.getUuid());
            tm.updateTown(town);
            promoted++;
        }
        return promoted;
    }
}
