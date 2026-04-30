package com.hexvane.aetherhaven.inn;

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
import com.hexvane.aetherhaven.villager.AetherhavenVillagerHandle;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hexvane.aetherhaven.villager.data.InnPoolEntry;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Inn visitor pool only: up to two NPCs (merchant/blacksmith/farmer/priestess). {@link TownRecord#getInnPoolNpcIds()} is
 * the source of truth for which visitors the mod spawned. Treasury tax is handled by {@link com.hexvane.aetherhaven.economy.TownEconomyTimeService}.
 * <p>
 * <b>Spawning</b> happens only during the morning window and only when the inn's management block chunk is loaded —
 * never to "replace" entries whose entities are still unloaded elsewhere. <b>Pruning</b> never drops unlocked list
 * entries with a missing entity ref (unloaded); morning refresh can despawn and remove only when refs are valid.
 * <p>
 * Unlocked visitors are cleared at most once per calendar game day during the morning window ({@link WorldTimeResource}
 * clock). Locked visitors stay.
 */
public final class InnPoolService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int MAX_VISITORS = 2;

    private static final List<InnPoolEntry> LEGACY_INN_POOL = List.of(
        new InnPoolEntry(AetherhavenConstants.NPC_MERCHANT, TownVillagerBinding.KIND_VISITOR_MERCHANT, 0),
        new InnPoolEntry(AetherhavenConstants.NPC_BLACKSMITH, TownVillagerBinding.KIND_VISITOR_BLACKSMITH, 1),
        new InnPoolEntry(AetherhavenConstants.NPC_FARMER, TownVillagerBinding.KIND_VISITOR_FARMER, 2),
        new InnPoolEntry(AetherhavenConstants.NPC_PRIESTESS, TownVillagerBinding.KIND_VISITOR_PRIESTESS, 3)
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

    private static final Object TICK_LOCK = new Object();
    private static String lastTickWorld;
    private static long lastTickGameEpochSecond = Long.MIN_VALUE;

    private InnPoolService() {}

    public static final class RepairReport {
        private int lockedQuestVisitors;
        private int promotedResidents;
        private int removedPoolEntries;

        public int getLockedQuestVisitors() {
            return lockedQuestVisitors;
        }

        public int getPromotedResidents() {
            return promotedResidents;
        }

        public int getRemovedPoolEntries() {
            return removedPoolEntries;
        }
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
            store.removeEntity(ref, RemoveReason.REMOVE);
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
            PlotInstance innPlot = town.findCompletePlotWithConstruction(AetherhavenConstants.CONSTRUCTION_PLOT_INN);
            if (innPlot == null) {
                continue;
            }
            ConstructionDefinition innDef = plugin.getConstructionCatalog().get(AetherhavenConstants.CONSTRUCTION_PLOT_INN);
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
    private static void syncInnPoolWithResidentBindings(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownManager tm
    ) {
        List<String> ids = town.getInnPoolNpcIds();
        boolean changed = false;
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
                    town.addInnVisitorPoolExcludedRoleId(npc.getRoleName());
                }
                it.remove();
                changed = true;
            }
        }
        if (changed) {
            tm.updateTown(town);
        }
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
            if (ref != null && ref.isValid()) {
                store.removeEntity(ref, RemoveReason.REMOVE);
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
            store.removeEntity(ref, RemoveReason.REMOVE);
        }
        town.setInnPoolLastMorningEpochDay(epochDay);
        town.setInnPoolLastMorningGameDate(wtr.getGameDateTime().toLocalDate().toString());
        tm.updateTown(town);
        return true;
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
        fillEmptySlotsForSpawn(world, plugin, town, tm, store, innPlot, innDef, spawnLocals, wtr);
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
        if (hasPendingUnlockedMissingRef(town, store)) {
            return;
        }

        List<String> presentRoles = new ArrayList<>();
        for (String sid : town.getInnPoolNpcIds()) {
            try {
                UUID u = UUID.fromString(sid.trim());
                Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(u);
                if (ref == null || !ref.isValid()) {
                    continue;
                }
                var npcType = NPCEntity.getComponentType();
                NPCEntity npc = npcType != null ? store.getComponent(ref, npcType) : null;
                if (npc != null && npc.getRoleName() != null) {
                    presentRoles.add(npc.getRoleName());
                }
            } catch (Exception ignored) {
            }
        }

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
            if (rid != null && !rid.isBlank()) {
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
            int[] local = spawnLocals[Math.min(slotIndex, spawnLocals.length - 1)];
            if (local == null || local.length != 3) {
                break;
            }
            UUID spawned = spawnVisitor(world, plugin, town, store, innPlot, innDef, local, roleId, kind);
            if (spawned == null) {
                break;
            }
            town.getInnPoolNpcIds().add(spawned.toString());
            if (isRoleRequiredByActiveInnQuest(town, roleId)) {
                town.addInnLockedEntity(spawned);
            }
            presentRoles.add(roleId);
            tm.updateTown(town);
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
        @Nonnull String villagerKind
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
        var pair = npc.spawnNPC(store, roleId, null, pos, Vector3f.ZERO);
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
        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        return uuidComp != null ? uuidComp.getUuid() : null;
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
        var pair = npc.spawnNPC(store, roleId, null, worldPosition, Vector3f.ZERO);
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
            if (rid != null && !rid.isBlank()) {
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
        List<String> presentRoles = new ArrayList<>();
        for (String sid : town.getInnPoolNpcIds()) {
            try {
                UUID u = UUID.fromString(sid.trim());
                Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(u);
                if (ref == null || !ref.isValid()) {
                    continue;
                }
                var npcType = NPCEntity.getComponentType();
                NPCEntity npc = npcType != null ? store.getComponent(ref, npcType) : null;
                if (npc != null && npc.getRoleName() != null) {
                    presentRoles.add(npc.getRoleName());
                }
            } catch (Exception ignored) {
            }
        }

        List<String> mergedOrder = mergedVisitorRoleOrder(town, plugin, store);
        List<InnPoolEntry> pool = innPoolOrLegacy(plugin);

        int slot = slotOffsetStart;
        for (String roleId : mergedOrder) {
            if (town.getInnPoolNpcIds().size() >= MAX_VISITORS) {
                break;
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
        return false;
    }

    private static boolean hasAnyActiveInnVisitorQuest(@Nonnull TownRecord town) {
        return town.hasQuestActive(AetherhavenConstants.QUEST_BLACKSMITH_SHOP)
            || town.hasQuestActive(AetherhavenConstants.QUEST_MERCHANT_STALL)
            || town.hasQuestActive(AetherhavenConstants.QUEST_FARM_PLOT)
            || town.hasQuestActive(AetherhavenConstants.QUEST_GAIA_ALTAR);
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
        RepairReport report = new RepairReport();
        town.migrateInnFieldsIfNeeded();
        dedupeInnPoolIds(town, tm);
        report.lockedQuestVisitors = repairQuestLocksCount(town, store);
        autoLockQuestCriticalVisitors(town, tm, store);
        report.promotedResidents = promoteEligibleVisitorsToResidents(world, plugin, town, tm, store);
        report.removedPoolEntries = removeNonVisitorPoolEntries(town, store, tm);
        return report;
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

    private static int removeNonVisitorPoolEntries(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownManager tm
    ) {
        int removed = 0;
        Iterator<String> it = town.getInnPoolNpcIds().iterator();
        while (it.hasNext()) {
            UUID u = parseUuid(it.next());
            if (u == null) {
                continue;
            }
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(u);
            if (ref == null || !ref.isValid()) {
                continue;
            }
            TownVillagerBinding b = store.getComponent(ref, TownVillagerBinding.getComponentType());
            if (b != null && b.getTownId().equals(town.getTownId()) && !TownVillagerBinding.isVisitorKind(b.getKind())) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0) {
            tm.updateTown(town);
        }
        return removed;
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
            } else {
                continue;
            }
            PlotInstance residentPlot = town.findCompletePlotWithConstruction(constructionId);
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
            ResidentRegistryService.upsert(town, tm, roleId, residentKind, residentPlot.getPlotId(), uuidComp.getUuid());
            tm.updateTown(town);
            promoted++;
        }
        return promoted;
    }
}
