package com.hexvane.aetherhaven.inn;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.economy.TownTaxService;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.AetherhavenVillagerHandle;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Inn visitor pool: up to two NPCs (merchant/blacksmith/farmer). {@link TownRecord#getInnPoolNpcIds()} is the source of
 * truth for which visitors the mod spawned.
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

    private static final List<String> POOL_ROLE_IDS = List.of(
        AetherhavenConstants.NPC_MERCHANT,
        AetherhavenConstants.NPC_BLACKSMITH,
        AetherhavenConstants.NPC_FARMER
    );

    private static final List<String> POOL_KINDS = List.of(
        TownVillagerBinding.KIND_VISITOR_MERCHANT,
        TownVillagerBinding.KIND_VISITOR_BLACKSMITH,
        TownVillagerBinding.KIND_VISITOR_FARMER
    );

    private static final Object TICK_LOCK = new Object();
    private static String lastTickWorld;
    private static long lastTickGameEpochSecond = Long.MIN_VALUE;

    private InnPoolService() {}

    /**
     * Throttled entry from {@link InnPoolTickSystem}: at most once per game-second per world.
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

    public static void tick(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull WorldTimeResource wtr) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        var es = world.getEntityStore();
        Store<EntityStore> store = es != null ? es.getStore() : null;
        if (store == null) {
            return;
        }
        int morningStart = plugin.getConfig().get().getInnPoolMorningStartHour();
        int morningEndEx = plugin.getConfig().get().getInnPoolMorningEndHourExclusive();

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
            PlotInstance innPlot = town.findCompletePlotWithConstruction(AetherhavenConstants.CONSTRUCTION_INN_V1);
            if (innPlot == null) {
                continue;
            }
            ConstructionDefinition innDef = plugin.getConstructionCatalog().get(AetherhavenConstants.CONSTRUCTION_INN_V1);
            if (innDef == null) {
                continue;
            }
            int[][] spawnLocals = innDef.getVisitorSpawnLocals();
            if (spawnLocals == null || spawnLocals.length < 1) {
                continue;
            }

            boolean innLoaded = isInnManagementChunkLoaded(world, innPlot, innDef);

            town.migrateInnFieldsIfNeeded();
            if (dedupeInnPoolIds(town, tm)) {
                // saved
            }
            if (innLoaded) {
                if (pruneDeadVisitors(town, store, tm)) {
                    // saved
                }
                if (trimInnPoolListToMax(town, tm, store)) {
                    // saved
                }
                syncInnPoolWithResidentBindings(town, store, tm);
            }
            if (innLoaded) {
                morningUnlockedRefreshIfDue(town, tm, store, wtr, morningStart, morningEndEx);
                if (pruneDeadVisitors(town, store, tm)) {
                    // after morning removals
                }
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
        TownTaxService.tickMorningTax(world, plugin, wtr, store);
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

    /**
     * Hour window (matches {@code /time} game clock) plus a scaled-day band so {@code /time dawn} still counts as morning
     * when the hour does not fall in the numeric window.
     */
    private static boolean isMorningForInnPool(
        @Nonnull WorldTimeResource wtr,
        int morningStartHour,
        int morningEndExclusive
    ) {
        if (isInMorningHourWindow(wtr, morningStartHour, morningEndExclusive)) {
            return true;
        }
        return wtr.isScaledDayTimeWithinRange(0.18f, 0.42f);
    }

    private static boolean isInMorningHourWindow(
        @Nonnull WorldTimeResource wtr,
        int morningStartHour,
        int morningEndExclusive
    ) {
        int h = wtr.getCurrentHour();
        int start = Math.max(0, Math.min(23, morningStartHour));
        int end = morningEndExclusive;
        if (end <= start) {
            end = Math.min(start + 6, 24);
        }
        return h >= start && h < end;
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
        List<String> order = new ArrayList<>(POOL_ROLE_IDS);
        Collections.shuffle(order, new Random(seed));

        for (String roleId : order) {
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
            int kindIndex = POOL_ROLE_IDS.indexOf(roleId);
            String kind = kindIndex >= 0 ? POOL_KINDS.get(kindIndex) : TownVillagerBinding.KIND_VISITOR_MERCHANT;
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
            presentRoles.add(roleId);
            tm.updateTown(town);
        }
    }

    /**
     * True if this town already has a non-visitor villager with the given NPC role (e.g. promoted merchant at the stall).
     * Prevents spawning a second inn visitor with the same role.
     */
    private static boolean townHasResidentWithNpcRole(
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
}
