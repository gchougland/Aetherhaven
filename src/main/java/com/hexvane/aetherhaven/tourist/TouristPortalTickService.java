package com.hexvane.aetherhaven.tourist;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.shopspot.ShopSpotOpenService;
import com.hexvane.aetherhaven.time.AetherhavenMorningWindow;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownOnlinePresence;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkAssignmentKinds;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hexvane.aetherhaven.townsfolk.TownsfolkPoolPersistence;
import com.hexvane.aetherhaven.townsfolk.TownsfolkPoolState;
import com.hexvane.aetherhaven.townsfolk.TownsfolkExistenceService;
import com.hexvane.aetherhaven.townsfolk.TownsfolkSpawnService;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterCatalog;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Daily tourist spawn and end of day return scheduling keyed to tourist portal blocks. */
public final class TouristPortalTickService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    /** Last dawn-aligned game day we ran the morning overstays pass for each world. */
    private static final ConcurrentHashMap<String, Long> LAST_DAWN_LEAVE_DAY_BY_WORLD = new ConcurrentHashMap<>();

    private TouristPortalTickService() {}

    public static void scheduleTickFromHub(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull WorldTimeResource wtr
    ) {
        world.execute(() -> tick(world, plugin, wtr));
    }

    public static void tick(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull WorldTimeResource wtr) {
        Store<EntityStore> store = world.getEntityStore() != null ? world.getEntityStore().getStore() : null;
        if (store == null) {
            return;
        }
        LocalDateTime gameTime = wtr.getGameDateTime();
        long epochDay = gameTime.toLocalDate().toEpochDay();
        long epochMinute =
            gameTime.toLocalDate().toEpochDay() * 24L * 60L + gameTime.toLocalTime().toSecondOfDay() / 60L;

        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TouristPortalRegistry registry = AetherhavenWorldRegistries.getOrCreateTouristPortalRegistry(world, plugin);
        AetherhavenPluginConfig cfg = plugin.getConfig().get();

        dedupeTouristRecords(tm, world, plugin, store);
        releaseStaleTouristPoolCheckouts(world, plugin, tm, store);

        processDawnTouristLeave(world, plugin, tm, store, wtr, cfg);
        processTouristLeaveWindow(world, plugin, tm, store, wtr);

        Map<UUID, List<TouristPortalRecord>> portalsByTown = groupPortalsByTown(registry, world.getName());
        boolean portalRegistryDirty = false;
        for (Map.Entry<UUID, List<TouristPortalRecord>> entry : portalsByTown.entrySet()) {
            TownRecord town = tm.getTown(entry.getKey());
            if (town == null) {
                continue;
            }
            List<TouristPortalRecord> townPortals = entry.getValue();
            boolean townPlanChanged = migrateLegacyPortalSpawnPlanIfNeeded(town, townPortals, epochDay);
            if (townPlanChanged) {
                portalRegistryDirty = true;
            }
            if (planTownDayIfNeeded(town, epochDay, cfg)) {
                clearLegacyPortalSpawnPlans(townPortals);
                portalRegistryDirty = true;
                townPlanChanged = true;
            }
            if (townPlanChanged) {
                tm.updateTown(town);
            }
            if (tryExecuteTownSpawn(world, plugin, tm, store, town, townPortals, epochMinute, epochDay)) {
                tm.updateTown(town);
            }
        }

        if (portalRegistryDirty) {
            TouristPortalPersistence.save(world, plugin, registry);
        }
    }

    @Nonnull
    private static Map<UUID, List<TouristPortalRecord>> groupPortalsByTown(
        @Nonnull TouristPortalRegistry registry,
        @Nonnull String worldName
    ) {
        Map<UUID, List<TouristPortalRecord>> byTown = new HashMap<>();
        for (TouristPortalRecord portal : registry.allRecords()) {
            if (!worldName.equals(portal.getWorldName())) {
                continue;
            }
            byTown.computeIfAbsent(portal.getTownId(), ignored -> new ArrayList<>()).add(portal);
        }
        return byTown;
    }

    /** Merges per-portal spawn plans from older saves into the town-level plan for the current day. */
    private static boolean migrateLegacyPortalSpawnPlanIfNeeded(
        @Nonnull TownRecord town,
        @Nonnull List<TouristPortalRecord> townPortals,
        long epochDay
    ) {
        if (town.getTouristSpawnPlannedDayEpochDay() == epochDay
            && !town.getTouristPlannedSpawnEpochMinutes().isEmpty()) {
            return false;
        }
        boolean foundLegacy = false;
        Set<Long> planned = new HashSet<>();
        Set<Long> executed = new HashSet<>();
        for (TouristPortalRecord portal : townPortals) {
            if (portal.getPlannedDayEpochDay() != epochDay) {
                continue;
            }
            foundLegacy = true;
            planned.addAll(portal.getPlannedSpawnEpochMinutes());
            executed.addAll(portal.getExecutedSpawnEpochMinutes());
        }
        if (!foundLegacy) {
            return false;
        }
        town.clearTouristDailySpawnPlan();
        town.setTouristSpawnPlannedDayEpochDay(epochDay);
        town.getTouristPlannedSpawnEpochMinutes().addAll(planned);
        town.getTouristPlannedSpawnEpochMinutes().sort(Long::compare);
        town.getTouristExecutedSpawnEpochMinutes().addAll(executed);
        clearLegacyPortalSpawnPlans(townPortals);
        return true;
    }

    private static void clearLegacyPortalSpawnPlans(@Nonnull List<TouristPortalRecord> townPortals) {
        for (TouristPortalRecord portal : townPortals) {
            if (portal.getPlannedDayEpochDay() != Long.MIN_VALUE
                || !portal.getPlannedSpawnEpochMinutes().isEmpty()
                || !portal.getExecutedSpawnEpochMinutes().isEmpty()) {
                portal.clearDailyPlan();
            }
        }
    }

    /** @return true when a new daily plan was generated */
    private static boolean planTownDayIfNeeded(
        @Nonnull TownRecord town,
        long epochDay,
        @Nonnull AetherhavenPluginConfig cfg
    ) {
        if (town.getTouristSpawnPlannedDayEpochDay() == epochDay
            && !town.getTouristPlannedSpawnEpochMinutes().isEmpty()) {
            return false;
        }
        town.clearTouristDailySpawnPlan();
        town.setTouristSpawnPlannedDayEpochDay(epochDay);
        UUID townId = town.getTownId();
        int count =
            AetherhavenConstants.TOURIST_MIN_DAILY_SPAWNS
                + new Random(townId.getLeastSignificantBits() ^ epochDay * 0x9E3779B97F4A7C15L).nextInt(
                    AetherhavenConstants.TOURIST_MAX_DAILY_SPAWNS
                        - AetherhavenConstants.TOURIST_MIN_DAILY_SPAWNS
                        + 1
                );
        int morningStart = cfg.getGameMorningStartHour();
        int windowStartMinute = Math.max(0, morningStart) * 60;
        int windowEndMinute = AetherhavenConstants.TOURIST_SPAWN_DAY_END_HOUR_EXCLUSIVE * 60;
        if (windowEndMinute <= windowStartMinute) {
            windowEndMinute = windowStartMinute + 360;
        }
        long dayBase = epochDay * 24L * 60L;
        Random random = new Random(townId.getMostSignificantBits() ^ epochDay * 0x517cc1b727220a95L);
        Set<Long> used = new HashSet<>();
        for (int i = 0; i < count; i++) {
            int offset;
            int attempts = 0;
            do {
                offset = windowStartMinute + random.nextInt(windowEndMinute - windowStartMinute);
                attempts++;
            } while (used.contains(dayBase + offset) && attempts < 32);
            used.add(dayBase + offset);
            town.getTouristPlannedSpawnEpochMinutes().add(dayBase + offset);
        }
        town.getTouristPlannedSpawnEpochMinutes().sort(Long::compare);
        return true;
    }

    /** @return true when a spawn was attempted */
    private static boolean tryExecuteTownSpawn(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull List<TouristPortalRecord> townPortals,
        long epochMinute,
        long epochDay
    ) {
        long dayBase = epochDay * 24L * 60L;
        long dayEnd = dayBase + 24L * 60L;
        for (Long planned : town.getTouristPlannedSpawnEpochMinutes()) {
            if (planned == null) {
                continue;
            }
            if (town.getTouristExecutedSpawnEpochMinutes().contains(planned)) {
                continue;
            }
            if (planned < dayBase || planned >= dayEnd) {
                continue;
            }
            if (planned > epochMinute) {
                continue;
            }
            TouristPortalRecord portal = pickSpawnPortal(world, town, townPortals, planned);
            if (portal == null) {
                continue;
            }
            town.getTouristExecutedSpawnEpochMinutes().add(planned);
            spawnOneTourist(world, plugin, tm, store, town, portal, epochDay);
            return true;
        }
        return false;
    }

    @Nullable
    private static TouristPortalRecord pickSpawnPortal(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull List<TouristPortalRecord> townPortals,
        long plannedEpochMinute
    ) {
        List<TouristPortalRecord> loaded = new ArrayList<>();
        for (TouristPortalRecord portal : townPortals) {
            if (isPortalChunkLoaded(world, portal)) {
                loaded.add(portal);
            }
        }
        if (loaded.isEmpty()) {
            return null;
        }
        Random random =
            new Random(
                town.getTownId().getLeastSignificantBits()
                    ^ plannedEpochMinute * 0xD6E8FEB8665FC2B3L
            );
        return loaded.get(random.nextInt(loaded.size()));
    }

    private static void spawnOneTourist(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull TouristPortalRecord portal,
        long epochDay
    ) {
        Set<String> exclude = activeCharacterIdsInTown(town, store);
        String characterId = pickAvailableCharacter(plugin, world, exclude, portal, epochDay);
        if (characterId == null) {
            return;
        }

        Vector3i blockPos = portal.getBlockPosition();
        long spawnSalt =
            town.getTownId().getLeastSignificantBits()
                ^ portal.getPortalId().getLeastSignificantBits()
                ^ characterId.hashCode()
                ^ epochDay;
        Vector3d feet = TouristPortalBlockUtil.spawnFeetPosition(world, blockPos, spawnSalt);
        Random random =
            new Random(
                town.getTownId().getLeastSignificantBits()
                    ^ portal.getPortalId().getLeastSignificantBits()
                    ^ characterId.hashCode()
                    ^ epochDay
            );

        var spawned =
            TownsfolkSpawnService.trySpawn(
                world,
                plugin,
                town,
                store,
                feet,
                TownsfolkAssignmentKinds.TOURIST,
                characterId,
                random,
                new Rotation3f(0.0F, (float) Math.PI, 0.0F),
                null,
                null
            );
        if (spawned.isEmpty()) {
            return;
        }

        UUID entityUuid = spawned.get().entityUuid();
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(entityUuid);
        if (ref == null || !ref.isValid()) {
            TownsfolkSpawnService.release(world, plugin, characterId);
            LOGGER.atWarning().log(
                "Tourist spawn for %s in town %s produced uuid %s but entity ref is missing",
                characterId,
                town.getTownId(),
                entityUuid
            );
            return;
        }

        long spawnDawnDay = VillagerReputationService.currentGameEpochDay(store);
        town.getTouristRecords().add(
            new TouristRecord(
                characterId,
                entityUuid,
                portal.getPortalId(),
                false,
                false,
                spawnDawnDay,
                rollLeaveHour(portal.getPortalId(), characterId, spawnDawnDay)
            )
        );
        tm.updateTown(town);

        TouristAutonomyState autonomy = TouristAutonomyState.fresh(System.currentTimeMillis());
        autonomy.setHomePortalId(portal.getPortalId());
        store.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
        TouristAutonomySystem.kickInitialVisitOnSpawn(ref, store, plugin, autonomy, town, world);

        playPortalBurst(world, store, blockPos);
    }

    @Nullable
    private static String pickAvailableCharacter(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull World world,
        @Nonnull Set<String> exclude,
        @Nonnull TouristPortalRecord portal,
        long epochDay
    ) {
        TownsfolkCharacterCatalog catalog = plugin.getTownsfolkCharacterCatalog();
        TownsfolkPoolState pool = TownsfolkPoolPersistence.getOrLoad(world, plugin);
        List<String> available = pool.availableCharacterIds(catalog, TownsfolkAssignmentKinds.TOURIST);
        List<String> candidates = new ArrayList<>();
        for (String id : available) {
            if (!exclude.contains(id)) {
                candidates.add(id);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        Random random =
            new Random(
                portal.getPortalId().getLeastSignificantBits()
                    ^ epochDay * 0xC2B2AE3D27D4EB4FL
            );
        return candidates.get(random.nextInt(candidates.size()));
    }

    @Nonnull
    private static Set<String> activeCharacterIdsInTown(@Nonnull TownRecord town, @Nonnull Store<EntityStore> store) {
        return TouristReconcileService.liveTouristCharacterIdsInTown(town, store);
    }

    public static void triggerEndOfDayReturn(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store
    ) {
        Set<UUID> onlinePlayers = TownOnlinePresence.collectOnlinePlayerUuids(world);
        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            if (!TownOnlinePresence.hasAffiliatedPlayerOnline(town, onlinePlayers)) {
                continue;
            }
            boolean changed = false;
            Iterator<TouristRecord> it = town.getTouristRecords().iterator();
            while (it.hasNext()) {
                TouristRecord rec = it.next();
                if (rec.isInvitedToStay() || rec.isCitizen()) {
                    continue;
                }
                UUID entityUuid = rec.getEntityUuid();
                if (entityUuid == null) {
                    finalizeTouristRecord(world, plugin, town, tm, rec, store);
                    it.remove();
                    changed = true;
                    continue;
                }
                sendTouristHomeOrFinalize(world, plugin, tm, store, town, rec, entityUuid);
            }
            if (changed) {
                tm.updateTown(town);
            }
        }
    }

    /**
     * Once per dawn-aligned game day during the morning window: send home tourists who overstayed from a prior visit day.
     */
    private static void processDawnTouristLeave(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nonnull WorldTimeResource wtr,
        @Nonnull AetherhavenPluginConfig cfg
    ) {
        long dawnDay = VillagerReputationService.currentGameEpochDay(store);
        Long last = LAST_DAWN_LEAVE_DAY_BY_WORLD.get(world.getName());
        if (last != null && last >= dawnDay) {
            return;
        }
        if (!AetherhavenMorningWindow.isGameMorning(
            wtr,
            cfg.getGameMorningStartHour(),
            cfg.getGameMorningEndHourExclusive()
        )) {
            return;
        }
        LAST_DAWN_LEAVE_DAY_BY_WORLD.put(world.getName(), dawnDay);
        processTouristLeaveWindow(world, plugin, tm, store, wtr);
    }

    /** Once per game minute: send tourists home when their visit window has elapsed (no exact-minute requirement). */
    private static void processTouristLeaveWindow(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nonnull WorldTimeResource wtr
    ) {
        LocalDateTime gameTime = wtr.getGameDateTime();
        long dawnAlignedEpochDay = VillagerReputationService.currentGameEpochDay(store);

        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            boolean changed = false;
            // Snapshot: finalizeTouristDeparture removes from the live list while we may still be visiting rows.
            for (TouristRecord rec : new ArrayList<>(town.getTouristRecords())) {
                if (rec.isInvitedToStay() || rec.isCitizen()) {
                    continue;
                }
                if (!shouldTouristLeaveNow(rec, gameTime, dawnAlignedEpochDay, wtr)) {
                    if (rec.getSpawnEpochDay() == 0L) {
                        rec.setSpawnEpochDay(dawnAlignedEpochDay);
                        changed = true;
                    }
                    continue;
                }
                UUID entityUuid = rec.getEntityUuid();
                if (entityUuid == null) {
                    finalizeTouristRecord(world, plugin, town, tm, rec, store);
                    town.getTouristRecords().remove(rec);
                    changed = true;
                    continue;
                }
                Ref<EntityStore> leaveRef = store.getExternalData().getRefFromUUID(entityUuid);
                if (leaveRef == null || !leaveRef.isValid()) {
                    finalizeTouristRecord(world, plugin, town, tm, rec, store);
                    changed = true;
                    continue;
                }
                if (isTouristOverstay(rec, dawnAlignedEpochDay)) {
                    despawnTourist(world, plugin, town, tm, store, entityUuid, rec.getPortalId());
                    continue;
                }
                sendTouristHomeOrFinalize(world, plugin, tm, store, town, rec, entityUuid);
            }
            if (changed) {
                tm.updateTown(town);
            }
        }
    }

    public static boolean shouldTouristLeaveNow(@Nonnull TouristRecord rec, @Nonnull Store<EntityStore> store) {
        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        if (wtr == null) {
            return false;
        }
        return shouldTouristLeaveNow(
            rec,
            wtr.getGameDateTime(),
            VillagerReputationService.currentGameEpochDay(store),
            wtr
        );
    }

    public static boolean isGameNight(@Nonnull WorldTimeResource wtr) {
        return !ShopSpotOpenService.isGameDay(wtr);
    }

    /** True when the tourist's visit day has passed (dawn-aligned day id advanced past arrival). */
    public static boolean isTouristOverstay(@Nonnull TouristRecord rec, long dawnAlignedEpochDay) {
        long spawnDay = rec.getSpawnEpochDay();
        return spawnDay != 0L && dawnAlignedEpochDay > spawnDay;
    }

    /**
     * @param dawnAlignedEpochDay visit day id from {@link VillagerReputationService#currentGameEpochDay}; a new id
     *     starts at dawn, not calendar midnight
     */
    public static boolean shouldTouristLeaveNow(
        @Nonnull TouristRecord rec,
        @Nonnull LocalDateTime gameTime,
        long dawnAlignedEpochDay,
        @Nonnull WorldTimeResource wtr
    ) {
        if (isTouristOverstay(rec, dawnAlignedEpochDay)) {
            return true;
        }
        long spawnDay = rec.getSpawnEpochDay();
        if (isGameNight(wtr)) {
            // 0 = legacy unset row only. Hytale game calendar epoch days are negative — never treat those as unset.
            return spawnDay == 0L || dawnAlignedEpochDay >= spawnDay;
        }
        // Pre-sunrise morning on a later calendar day (/time set dawn before dawn-aligned day advances).
        long calendarDay = gameTime.toLocalDate().toEpochDay();
        if (spawnDay != 0L && calendarDay > spawnDay && dawnAlignedEpochDay == spawnDay) {
            return true;
        }
        return false;
    }

    @Nullable
    public static TouristRecord findTouristRecord(@Nonnull TownRecord town, @Nonnull String characterId) {
        if (characterId.isBlank()) {
            return null;
        }
        for (TouristRecord rec : town.getTouristRecords()) {
            if (characterId.equals(rec.getCharacterId())) {
                return rec;
            }
        }
        return null;
    }

    public static int rollLeaveHour(@Nonnull UUID portalId, @Nonnull String characterId, long spawnEpochDay) {
        int span = AetherhavenConstants.TOURIST_DESPAWN_HOUR_MAX - AetherhavenConstants.TOURIST_DESPAWN_HOUR_MIN + 1;
        Random random =
            new Random(
                portalId.getLeastSignificantBits()
                    ^ characterId.hashCode()
                    ^ spawnEpochDay * 0x9E3779B97F4A7C15L
            );
        return AetherhavenConstants.TOURIST_DESPAWN_HOUR_MIN + random.nextInt(span);
    }

    /** Assigns a leave hour when missing (legacy rows). */
    public static int ensureLeaveHour(@Nonnull TouristRecord rec) {
        if (rec.getLeaveHour() >= AetherhavenConstants.TOURIST_DESPAWN_HOUR_MIN
            && rec.getLeaveHour() <= AetherhavenConstants.TOURIST_DESPAWN_HOUR_MAX) {
            return rec.getLeaveHour();
        }
        UUID portalId = rec.getPortalId();
        long day = rec.getSpawnEpochDay() != 0L ? rec.getSpawnEpochDay() : 0L;
        int hour =
            rollLeaveHour(
                portalId != null ? portalId : new UUID(0L, 0L),
                rec.getCharacterId(),
                day
            );
        rec.setLeaveHour(hour);
        return hour;
    }

    public static void sendTouristHomeOrFinalize(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull TouristRecord rec,
        @Nonnull UUID entityUuid
    ) {
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(entityUuid);
        if (ref == null || !ref.isValid()) {
            finalizeTouristRecord(world, plugin, town, tm, rec, store);
            return;
        }
        long dawnDay = VillagerReputationService.currentGameEpochDay(store);
        UUID portalId = rec.getPortalId();
        if (isTouristOverstay(rec, dawnDay)) {
            despawnTourist(world, plugin, town, tm, store, entityUuid, portalId);
            return;
        }
        TouristAutonomyState autonomy = store.getComponent(ref, TouristAutonomyState.getComponentType());
        if (autonomy == null) {
            autonomy = TouristAutonomyState.fresh(System.currentTimeMillis());
        }
        if (portalId != null) {
            autonomy.setHomePortalId(portalId);
        }
        if (TouristAutonomySystem.isReturningHome(autonomy)) {
            return;
        }
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc != null && portalId != null) {
            long now = resolveNowMs(store);
            if (TouristAutonomySystem.beginReturnToPortalOnStore(
                ref, store, plugin, npc, autonomy, now, town, world
            )) {
                store.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
                store.putComponent(ref, NPCEntity.getComponentType(), npc);
                TouristAutonomySystem.applyAutonomyRoleStateOnStore(ref, npc, store);
            } else if (shouldTouristLeaveNow(rec, store)) {
                despawnTourist(world, plugin, town, tm, store, entityUuid, portalId);
            } else {
                autonomy.setPhase(TouristAutonomyState.PHASE_IDLE);
                autonomy.clearVisitPlot();
                autonomy.clearTravelWaypoints();
                store.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
            }
        } else if (shouldTouristLeaveNow(rec, store)) {
            despawnTourist(world, plugin, town, tm, store, entityUuid, portalId);
        } else {
            store.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
        }
    }

    private static long resolveNowMs(@Nonnull Store<EntityStore> store) {
        com.hypixel.hytale.server.core.modules.time.TimeResource tr =
            store.getResource(com.hypixel.hytale.server.core.modules.time.TimeResource.getResourceType());
        return tr != null ? tr.getNow().toEpochMilli() : System.currentTimeMillis();
    }

    public static void finalizeTouristRecord(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull TouristRecord rec,
        @Nonnull Store<EntityStore> store
    ) {
        UUID entityUuid = rec.getEntityUuid();
        if (entityUuid != null) {
            finalizeTouristDeparture(world, plugin, town, tm, entityUuid, rec.getPortalId(), store);
            return;
        }
        String characterId = rec.getCharacterId();
        if (!rec.isInvitedToStay() && !rec.isCitizen()) {
            if (characterId != null && !characterId.isBlank()) {
                TownsfolkSpawnService.release(world, plugin, characterId);
            }
        }
        UUID portalId = rec.getPortalId();
        if (portalId != null) {
            TouristPortalRegistry registry = AetherhavenWorldRegistries.getOrCreateTouristPortalRegistry(world, plugin);
            TouristPortalRecord portal = registry.get(portalId);
            if (portal != null) {
                playPortalBurst(world, store, portal.getBlockPosition());
            }
        }
    }

    public static void catchUpLeaveAfterTimeJump(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull WorldTimeResource wtr
    ) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        long dawnDay = VillagerReputationService.currentGameEpochDay(store);
        LAST_DAWN_LEAVE_DAY_BY_WORLD.put(world.getName(), dawnDay - 1L);
        processTouristLeaveWindow(world, plugin, tm, store, wtr);
    }

    public static void despawnTourist(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID entityUuid,
        @Nullable UUID portalId
    ) {
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(entityUuid);
        if (ref != null && ref.isValid()) {
            store.removeEntity(ref, RemoveReason.REMOVE);
        }
        finalizeTouristDeparture(world, plugin, town, tm, entityUuid, portalId, store);
    }

    public static void finalizeTouristDeparture(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull UUID entityUuid,
        @Nullable UUID portalId,
        @Nonnull Store<EntityStore> store
    ) {
        String characterId = null;
        Iterator<TouristRecord> it = town.getTouristRecords().iterator();
        while (it.hasNext()) {
            TouristRecord rec = it.next();
            UUID u = rec.getEntityUuid();
            if (u != null && u.equals(entityUuid)) {
                characterId = rec.getCharacterId();
                if (!rec.isInvitedToStay() && !rec.isCitizen()) {
                    it.remove();
                }
                break;
            }
        }
        if (characterId != null && !characterId.isBlank()) {
            TownsfolkSpawnService.release(world, plugin, characterId);
        }
        tm.updateTown(town);

        if (portalId != null) {
            TouristPortalRegistry registry = AetherhavenWorldRegistries.getOrCreateTouristPortalRegistry(world, plugin);
            TouristPortalRecord portal = registry.get(portalId);
            if (portal != null) {
                playPortalBurst(world, store, portal.getBlockPosition());
            }
        }
    }

    public static void playPortalBurst(@Nonnull World world, @Nonnull Store<EntityStore> store, @Nonnull Vector3i blockPos) {
        Vector3d center = TouristPortalBlockUtil.portalEffectCenter(world, blockPos);
        world.execute(() -> {
            ParticleUtil.spawnParticleEffect(AetherhavenConstants.TOURIST_PORTAL_SPAWN_BURST_PARTICLE, center, store);
        });
    }

    private static boolean isPortalChunkLoaded(@Nonnull World world, @Nonnull TouristPortalRecord portal) {
        Vector3i pos = portal.getBlockPosition();
        return world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(pos.x, pos.z)) != null;
    }

    private static void releaseStaleTouristPoolCheckouts(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store
    ) {
        TownsfolkExistenceService.reclaimAbsentNonGuardCheckouts(world, plugin, store);
        Set<UUID> onlinePlayers = TownOnlinePresence.collectOnlinePlayerUuids(world);
        Map<String, TownsfolkExistenceService.LiveTownsfolkEntity> liveByCharacter =
            TownsfolkExistenceService.buildLiveIndex(store);
        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            if (!TownOnlinePresence.hasAffiliatedPlayerOnline(town, onlinePlayers)) {
                continue;
            }
            boolean changed = false;
            Iterator<TouristRecord> it = town.getTouristRecords().iterator();
            while (it.hasNext()) {
                TouristRecord rec = it.next();
                if (rec.isInvitedToStay() || rec.isCitizen()) {
                    continue;
                }
                if (TouristReconcileService.isLiveTouristEntity(town, store, liveByCharacter, rec)) {
                    continue;
                }
                UUID entityUuid = rec.getEntityUuid();
                if (entityUuid != null) {
                    Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(entityUuid);
                    if (ref == null) {
                        continue;
                    }
                    if (ref.isValid()) {
                        continue;
                    }
                }
                finalizeTouristRecord(world, plugin, town, tm, rec, store);
                if (entityUuid == null) {
                    it.remove();
                }
                changed = true;
            }
            if (changed) {
                tm.updateTown(town);
            }
        }
    }

    private static void dedupeTouristRecords(
        @Nonnull TownManager tm,
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store
    ) {
        Map<String, TownsfolkExistenceService.LiveTownsfolkEntity> liveByCharacter =
            TownsfolkExistenceService.buildLiveIndex(store);
        Set<UUID> onlinePlayers = TownOnlinePresence.collectOnlinePlayerUuids(world);
        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            boolean memberOnline = TownOnlinePresence.hasAffiliatedPlayerOnline(town, onlinePlayers);
            boolean changed = false;
            Set<String> seenChars = new HashSet<>();
            Iterator<TouristRecord> it = town.getTouristRecords().iterator();
            while (it.hasNext()) {
                TouristRecord rec = it.next();
                String cid = rec.getCharacterId();
                if (cid.isBlank()) {
                    it.remove();
                    changed = true;
                    continue;
                }
                if (seenChars.contains(cid)) {
                    it.remove();
                    changed = true;
                    continue;
                }
                seenChars.add(cid);
                if (memberOnline) {
                    TownsfolkExistenceService.LiveTownsfolkEntity live = liveByCharacter.get(cid);
                    if (live != null && live.townId() != null && live.townId().equals(town.getTownId())) {
                        UUID recorded = rec.getEntityUuid();
                        if (recorded == null || !recorded.equals(live.entityUuid())) {
                            rec.setEntityUuid(live.entityUuid());
                            changed = true;
                        }
                    }
                }
            }
            if (changed) {
                tm.updateTown(town);
            }
        }
    }

    public static void promoteTouristToCitizen(
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull UUID entityUuid
    ) {
        for (TouristRecord rec : town.getTouristRecords()) {
            UUID u = rec.getEntityUuid();
            if (u != null && u.equals(entityUuid)) {
                rec.setCitizen(true);
                rec.setInvitedToStay(true);
                break;
            }
        }
        tm.updateTown(town);
    }

    public static void lockTouristForInvite(@Nonnull TownRecord town, @Nonnull TownManager tm, @Nonnull UUID entityUuid) {
        for (TouristRecord rec : town.getTouristRecords()) {
            UUID u = rec.getEntityUuid();
            if (u != null && u.equals(entityUuid)) {
                rec.setInvitedToStay(true);
                break;
            }
        }
        tm.updateTown(town);
    }

    @Nullable
    public static TouristRecord findTouristRecord(@Nonnull TownRecord town, @Nonnull UUID entityUuid) {
        for (TouristRecord rec : town.getTouristRecords()) {
            UUID u = rec.getEntityUuid();
            if (u != null && u.equals(entityUuid)) {
                return rec;
            }
        }
        return null;
    }

    public static boolean isActivePortalTourist(@Nonnull TownRecord town, @Nonnull UUID entityUuid) {
        TouristRecord rec = findTouristRecord(town, entityUuid);
        return rec != null && !rec.isCitizen();
    }

    public static boolean isInvitedUnhousedTourist(
        @Nonnull TownRecord town,
        @Nonnull UUID entityUuid,
        @Nonnull AetherhavenPlugin plugin
    ) {
        TouristRecord rec = findTouristRecord(town, entityUuid);
        if (rec == null || !rec.isInvitedToStay() || rec.isCitizen()) {
            return false;
        }
        return !town.isNpcHomeResidentOnHousePlot(entityUuid, plugin.getConstructionCatalog());
    }
}
