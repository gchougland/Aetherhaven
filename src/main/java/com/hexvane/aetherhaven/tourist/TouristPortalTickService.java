package com.hexvane.aetherhaven.tourist;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.entity.EntityPresenceUtil;
import com.hexvane.aetherhaven.entity.EntityPresenceUtil.EntityPresence;
import com.hexvane.aetherhaven.guild.GuildHallDisplayAnchor;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.rts.RtsGuardDirectory;
import com.hexvane.aetherhaven.shopspot.ShopSpotOpenService;
import com.hexvane.aetherhaven.time.AetherhavenMorningWindow;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownOnlinePresence;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownTerritoryChunkUtil;
import com.hexvane.aetherhaven.townsfolk.TownsfolkAssignmentKinds;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hexvane.aetherhaven.townsfolk.TownsfolkPoolCheckoutRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkPoolPersistence;
import com.hexvane.aetherhaven.townsfolk.TownsfolkPoolState;
import com.hexvane.aetherhaven.townsfolk.TownsfolkExistenceService;
import com.hexvane.aetherhaven.townsfolk.TownsfolkSpawnService;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterCatalog;
import com.hexvane.aetherhaven.villager.NpcSpawnOriginUtil;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
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

        TouristPortalPlotRelocation.purgeFillerPortalRecords(world, plugin, store, registry, tm);
        dedupeTouristRecords(tm, world, plugin, store);
        releaseStaleTouristPoolCheckouts(world, plugin, tm, store);
        purgeOrphanTownsfolkShellsWhenTownLoaded(world, plugin, tm, store);

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

    /** @return true when a planned spawn slot should be marked executed (spawn succeeded). */
    static boolean shouldConsumePlannedSpawnSlot(boolean spawnSucceeded) {
        return spawnSucceeded;
    }

    private static void purgeOrphanTownsfolkShellsWhenTownLoaded(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store
    ) {
        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            if (TownTerritoryChunkUtil.isAnyTownNpcChunkLoaded(world, plugin, town)) {
                purgeOrphanTownsfolkShells(world, plugin, tm, store, new HashSet<>());
                return;
            }
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

    /** @return true when a spawn was attempted and succeeded */
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
            if (shouldConsumePlannedSpawnSlot(
                spawnOneTourist(world, plugin, tm, store, town, portal, epochDay)
            )) {
                town.getTouristExecutedSpawnEpochMinutes().add(planned);
                return true;
            }
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

    /** @return true when a tourist was spawned and recorded successfully */
    private static boolean spawnOneTourist(
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
            return false;
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
            return false;
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
            return false;
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

        NpcSpawnOriginUtil.attach(
            store,
            ref,
            "TOURIST_PORTAL",
            "portalId=" + portal.getPortalId() + ",characterId=" + characterId + ",epochDay=" + epochDay,
            world,
            feet,
            spawnDawnDay
        );

        TouristAutonomyState autonomy = TouristAutonomyState.fresh(System.currentTimeMillis());
        autonomy.setHomePortalId(portal.getPortalId());
        store.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
        TouristAutonomySystem.kickInitialVisitOnSpawn(ref, store, plugin, autonomy, town, world);

        TownsfolkExistenceService.purgeDuplicateEntities(world, store, characterId, entityUuid);
        playPortalBurst(world, store, blockPos);
        return true;
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
                EntityPresence presence = EntityPresenceUtil.resolve(store, entityUuid);
                if (EntityPresenceUtil.isLoadedLive(presence) && leaveRef != null && leaveRef.isValid()) {
                    if (isTouristOverstay(rec, dawnAlignedEpochDay)) {
                        despawnTourist(world, plugin, town, tm, store, entityUuid, rec.getPortalId());
                        continue;
                    }
                    sendTouristHomeOrFinalize(world, plugin, tm, store, town, rec, entityUuid);
                } else if (EntityPresenceUtil.shouldFinalizeTouristLeaveForMissingEntity(presence)) {
                    finalizeTouristRecord(world, plugin, town, tm, rec, store);
                    changed = true;
                }
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
        // Broken identity cannot run autonomy; force-remove instead of leaving a stuck NPC.
        if (!TouristReconcileService.entityHasTouristComponents(store, ref, town)) {
            despawnTourist(world, plugin, town, tm, store, entityUuid, portalId);
            return;
        }
        TouristAutonomyState autonomy = store.getComponent(ref, TouristAutonomyState.getComponentType());
        long now = resolveNowMs(store);
        if (autonomy == null) {
            autonomy = TouristAutonomyState.fresh(now);
        }
        if (portalId != null) {
            autonomy.setHomePortalId(portalId);
        }
        // Returning without autonomy ticks never completes — despawn once the travel budget elapses.
        if (TouristAutonomySystem.isReturningHome(autonomy)) {
            if (now >= autonomy.getNextDecisionEpochMs()) {
                despawnTourist(world, plugin, town, tm, store, entityUuid, portalId);
            }
            return;
        }
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc != null && portalId != null) {
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
        promoteTouristToCitizen(town, tm, entityUuid, null, null, null);
    }

    public static void promoteTouristToCitizen(
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull UUID entityUuid,
        @Nullable World world,
        @Nullable Store<EntityStore> store,
        @Nullable AetherhavenPlugin plugin
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
        // Citizens keep tourist browsing so the town stays lively; they simply never leave via the portal.
        if (world != null && store != null && plugin != null) {
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(entityUuid);
            if (ref != null && ref.isValid()) {
                TouristAutonomyState autonomy = store.getComponent(ref, TouristAutonomyState.getComponentType());
                if (autonomy == null) {
                    autonomy = TouristAutonomyState.fresh(System.currentTimeMillis());
                    store.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
                    NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                    if (npc != null) {
                        TouristAutonomySystem.kickInitialVisitOnSpawn(ref, store, plugin, autonomy, town, world);
                        store.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
                        store.putComponent(ref, NPCEntity.getComponentType(), npc);
                    }
                }
            }
        }
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

    /** {@code skippedGuards} is the count of loaded hired guard entities left untouched in this world. */
    public record TouristPurgeResult(int removed, int skippedProtected, int skippedGuards) {}

    /**
     * True when a tourist row or live tourist entity must not be removed by {@link #purgeActiveTouristsInWorld}.
     * Guard binding is checked separately so callers can count guard skips.
     */
    public static boolean shouldProtectTouristFromPurge(
        @Nonnull TownRecord town,
        @Nullable TouristRecord rec,
        @Nullable UUID entityUuid,
        @Nonnull ConstructionCatalog catalog
    ) {
        if (rec != null && (rec.isInvitedToStay() || rec.isCitizen())) {
            return true;
        }
        return entityUuid != null && town.isNpcHomeResidentOnHousePlot(entityUuid, catalog);
    }

    public static boolean isGuardEntityForPurge(
        @Nullable Ref<EntityStore> entityRef,
        @Nullable Store<EntityStore> store
    ) {
        if (entityRef == null || store == null || !entityRef.isValid()) {
            return false;
        }
        TownVillagerBinding binding = store.getComponent(entityRef, TownVillagerBinding.getComponentType());
        return binding != null && TownVillagerBinding.KIND_GUARD.equals(binding.getKind());
    }

    /**
     * Emergency admin purge: remove active visiting tourists in every town in this world. Invited, housed, and
     * promoted tourist citizens are kept; guard entities are never removed.
     */
    @Nonnull
    public static TouristPurgeResult purgeActiveTouristsInWorld(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store
    ) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        ConstructionCatalog catalog = plugin.getConstructionCatalog();
        int removed = 0;
        int skippedProtected = 0;
        Set<UUID> processedEntityUuids = new HashSet<>();
        Set<String> processedCharacterIds = new HashSet<>();

        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            boolean townChanged = false;
            for (TouristRecord rec : new ArrayList<>(town.getTouristRecords())) {
                UUID entityUuid = rec.getEntityUuid();
                String characterId = rec.getCharacterId();
                Ref<EntityStore> ref =
                    entityUuid != null ? store.getExternalData().getRefFromUUID(entityUuid) : null;
                if (isGuardEntityForPurge(ref, store)) {
                    continue;
                }
                if (shouldProtectTouristFromPurge(town, rec, entityUuid, catalog)) {
                    skippedProtected++;
                    continue;
                }
                if (forcePurgeTourist(world, plugin, town, store, rec, ref)) {
                    removed++;
                    townChanged = true;
                    if (entityUuid != null) {
                        processedEntityUuids.add(entityUuid);
                    }
                    if (characterId != null && !characterId.isBlank()) {
                        processedCharacterIds.add(characterId.toLowerCase());
                    }
                }
            }
            if (townChanged) {
                tm.updateTown(town);
            }
        }

        Map<String, TownsfolkExistenceService.LiveTownsfolkEntity> liveByCharacter =
            TownsfolkExistenceService.buildLiveIndex(store);
        for (TownsfolkExistenceService.LiveTownsfolkEntity live : liveByCharacter.values()) {
            if (!TownsfolkAssignmentKinds.isTourist(live.assignmentKind())) {
                continue;
            }
            String characterId = live.characterId();
            if (characterId.isBlank()) {
                continue;
            }
            if (processedCharacterIds.contains(characterId.toLowerCase())) {
                continue;
            }
            UUID entityUuid = live.entityUuid();
            if (entityUuid != null && processedEntityUuids.contains(entityUuid)) {
                continue;
            }
            TownRecord town;
            if (live.townId() == null) {
                TownRecord resolvedTown = resolveTownForLiveTourist(tm, world, entityUuid, characterId);
                if (resolvedTown == null) {
                    if (isTouristProtectedInAnyTown(tm, world, entityUuid, characterId, catalog)) {
                        skippedProtected++;
                    } else if (live.ref() != null && live.ref().isValid()) {
                        store.removeEntity(live.ref(), RemoveReason.REMOVE);
                        TownsfolkSpawnService.release(world, plugin, characterId);
                        removed++;
                    }
                    continue;
                }
                town = resolvedTown;
            } else {
                town = tm.getTown(live.townId());
            }
            if (town == null || !world.getName().equals(town.getWorldName())) {
                continue;
            }
            if (isGuardEntityForPurge(live.ref(), store)) {
                continue;
            }
            TouristRecord rec =
                entityUuid != null
                    ? findTouristRecord(town, entityUuid)
                    : findTouristRecord(town, characterId);
            if (shouldProtectTouristFromPurge(town, rec, entityUuid, catalog)) {
                skippedProtected++;
                continue;
            }
            boolean townChanged = false;
            if (rec != null) {
                if (forcePurgeTourist(world, plugin, town, store, rec, live.ref())) {
                    removed++;
                    townChanged = true;
                }
            } else if (live.ref() != null && live.ref().isValid()) {
                store.removeEntity(live.ref(), RemoveReason.REMOVE);
                TownsfolkSpawnService.release(world, plugin, characterId);
                removed++;
                townChanged = true;
            }
            if (townChanged) {
                tm.updateTown(town);
                processedCharacterIds.add(characterId.toLowerCase());
                if (entityUuid != null) {
                    processedEntityUuids.add(entityUuid);
                }
            }
        }

        removed += purgeOrphanTownsfolkShells(world, plugin, tm, store, processedEntityUuids);

        int untouchedGuards = countLivingHiredGuardsInWorld(tm, world, store);
        return new TouristPurgeResult(removed, skippedProtected, untouchedGuards);
    }

    private static int countLivingHiredGuardsInWorld(
        @Nonnull TownManager tm,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store
    ) {
        int count = 0;
        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            count += RtsGuardDirectory.livingGuardRefs(town, store).size();
        }
        return count;
    }

    private static boolean forcePurgeTourist(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull TouristRecord rec,
        @Nullable Ref<EntityStore> ref
    ) {
        if (ref != null && ref.isValid()) {
            store.removeEntity(ref, RemoveReason.REMOVE);
        } else if (rec.getEntityUuid() != null) {
            Ref<EntityStore> resolved = store.getExternalData().getRefFromUUID(rec.getEntityUuid());
            if (resolved != null && resolved.isValid()) {
                store.removeEntity(resolved, RemoveReason.REMOVE);
            }
        }
        if (!town.getTouristRecords().remove(rec)) {
            return false;
        }
        String characterId = rec.getCharacterId();
        if (characterId != null && !characterId.isBlank()) {
            TownsfolkSpawnService.release(world, plugin, characterId);
        }
        return true;
    }

    /** Result of clearing visiting tourists for one town (same rules as {@link #purgeActiveTouristsInWorld}). */
    public record TouristTownPurgeResult(int removed, int skippedProtected) {}

    /**
     * Removes active visiting tourists for a single town. Invited, housed, and citizen tourists are kept; guard
     * entities are never removed.
     */
    @Nonnull
    public static TouristTownPurgeResult purgeActiveTouristsInTown(
        @Nonnull TownRecord town,
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store
    ) {
        if (!world.getName().equals(town.getWorldName())) {
            return new TouristTownPurgeResult(0, 0);
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        ConstructionCatalog catalog = plugin.getConstructionCatalog();
        int removed = 0;
        int skippedProtected = 0;
        Set<UUID> processedEntityUuids = new HashSet<>();
        Set<String> processedCharacterIds = new HashSet<>();
        boolean townChanged = false;
        UUID townId = town.getTownId();

        for (TouristRecord rec : new ArrayList<>(town.getTouristRecords())) {
            UUID entityUuid = rec.getEntityUuid();
            String characterId = rec.getCharacterId();
            Ref<EntityStore> entityRef =
                entityUuid != null ? store.getExternalData().getRefFromUUID(entityUuid) : null;
            if (isGuardEntityForPurge(entityRef, store)) {
                continue;
            }
            if (shouldProtectTouristFromPurge(town, rec, entityUuid, catalog)) {
                skippedProtected++;
                continue;
            }
            if (forcePurgeTourist(world, plugin, town, store, rec, entityRef)) {
                removed++;
                townChanged = true;
                if (entityUuid != null) {
                    processedEntityUuids.add(entityUuid);
                }
                if (characterId != null && !characterId.isBlank()) {
                    processedCharacterIds.add(characterId.toLowerCase());
                }
            }
        }

        Map<String, TownsfolkExistenceService.LiveTownsfolkEntity> liveByCharacter =
            TownsfolkExistenceService.buildLiveIndex(store);
        for (TownsfolkExistenceService.LiveTownsfolkEntity live : liveByCharacter.values()) {
            if (!TownsfolkAssignmentKinds.isTourist(live.assignmentKind())) {
                continue;
            }
            String characterId = live.characterId();
            if (characterId.isBlank()) {
                continue;
            }
            if (processedCharacterIds.contains(characterId.toLowerCase())) {
                continue;
            }
            UUID entityUuid = live.entityUuid();
            if (entityUuid != null && processedEntityUuids.contains(entityUuid)) {
                continue;
            }
            TownRecord liveTown;
            if (live.townId() != null && live.townId().equals(townId)) {
                liveTown = town;
            } else if (live.townId() == null) {
                TownRecord resolved = resolveTownForLiveTourist(tm, world, entityUuid, characterId);
                if (resolved == null || !resolved.getTownId().equals(townId)) {
                    continue;
                }
                liveTown = resolved;
            } else {
                continue;
            }
            if (isGuardEntityForPurge(live.ref(), store)) {
                continue;
            }
            TouristRecord rec =
                entityUuid != null
                    ? findTouristRecord(liveTown, entityUuid)
                    : findTouristRecord(liveTown, characterId);
            if (shouldProtectTouristFromPurge(liveTown, rec, entityUuid, catalog)) {
                skippedProtected++;
                continue;
            }
            boolean changed = false;
            if (rec != null) {
                if (forcePurgeTourist(world, plugin, liveTown, store, rec, live.ref())) {
                    removed++;
                    changed = true;
                }
            } else if (live.ref() != null && live.ref().isValid()) {
                store.removeEntity(live.ref(), RemoveReason.REMOVE);
                TownsfolkSpawnService.release(world, plugin, characterId);
                removed++;
                changed = true;
            }
            if (changed) {
                townChanged = true;
                processedCharacterIds.add(characterId.toLowerCase());
                if (entityUuid != null) {
                    processedEntityUuids.add(entityUuid);
                }
            }
        }

        if (townChanged) {
            tm.updateTown(town);
        }
        return new TouristTownPurgeResult(removed, skippedProtected);
    }

    /**
     * Legacy tourist spawns that lost {@link TownVillagerBinding} / {@link TownsfolkCharacterBinding} but kept the
     * townsfolk NPC role — not reachable via tourist save rows or the live townsfolk index.
     */
    public static boolean isOrphanTownsfolkShellForPurge(
        @Nullable String npcRoleName,
        boolean hasVillagerBinding,
        boolean hasTownsfolkBinding,
        boolean hasGuildHallDisplayAnchor,
        @Nonnull UUID entityUuid,
        @Nonnull Set<UUID> trackedNpcUuids
    ) {
        if (!AetherhavenConstants.NPC_TOWNSFOLK.equals(npcRoleName)) {
            return false;
        }
        if (hasVillagerBinding || hasTownsfolkBinding || hasGuildHallDisplayAnchor) {
            return false;
        }
        return !trackedNpcUuids.contains(entityUuid);
    }

    @Nonnull
    private static Set<UUID> buildTrackedNpcUuidsForWorld(@Nonnull TownManager tm, @Nonnull World world) {
        Set<UUID> tracked = new HashSet<>();
        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            town.collectTrackedNpcEntityUuids(tracked);
            for (TouristRecord rec : town.getTouristRecords()) {
                UUID entityUuid = rec.getEntityUuid();
                if (entityUuid != null) {
                    tracked.add(entityUuid);
                }
            }
        }
        return tracked;
    }

    @Nullable
    private static TownRecord resolveTownForLiveTourist(
        @Nonnull TownManager tm,
        @Nonnull World world,
        @Nullable UUID entityUuid,
        @Nonnull String characterId
    ) {
        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            if (entityUuid != null && findTouristRecord(town, entityUuid) != null) {
                return town;
            }
            if (!characterId.isBlank() && findTouristRecord(town, characterId) != null) {
                return town;
            }
        }
        return null;
    }

    private static boolean isTouristProtectedInAnyTown(
        @Nonnull TownManager tm,
        @Nonnull World world,
        @Nullable UUID entityUuid,
        @Nonnull String characterId,
        @Nonnull ConstructionCatalog catalog
    ) {
        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            TouristRecord rec =
                entityUuid != null
                    ? findTouristRecord(town, entityUuid)
                    : findTouristRecord(town, characterId);
            if (shouldProtectTouristFromPurge(town, rec, entityUuid, catalog)) {
                return true;
            }
        }
        return false;
    }

    private static int purgeOrphanTownsfolkShells(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nonnull Set<UUID> processedEntityUuids
    ) {
        Set<UUID> trackedNpcUuids = buildTrackedNpcUuidsForWorld(tm, world);
        TownsfolkPoolState pool = TownsfolkPoolPersistence.getOrLoad(world, plugin);
        int[] removed = {0};
        List<String> releaseCharacterIds = new ArrayList<>();
        store.forEachChunk(
            Query.and(NPCEntity.getComponentType(), UUIDComponent.getComponentType()),
            (chunk, commandBuffer) -> {
                collectOrphanTownsfolkShellsInChunk(
                    chunk,
                    commandBuffer,
                    pool,
                    trackedNpcUuids,
                    processedEntityUuids,
                    releaseCharacterIds,
                    removed
                );
            }
        );
        for (String characterId : releaseCharacterIds) {
            TownsfolkSpawnService.release(world, plugin, characterId);
        }
        return removed[0];
    }

    private static void collectOrphanTownsfolkShellsInChunk(
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull TownsfolkPoolState pool,
        @Nonnull Set<UUID> trackedNpcUuids,
        @Nonnull Set<UUID> processedEntityUuids,
        @Nonnull List<String> releaseCharacterIds,
        @Nonnull int[] removed
    ) {
        for (int i = 0; i < chunk.size(); i++) {
            Ref<EntityStore> ref = chunk.getReferenceTo(i);
            if (ref == null || !ref.isValid()) {
                continue;
            }
            UUIDComponent uc = chunk.getComponent(i, UUIDComponent.getComponentType());
            NPCEntity npc = chunk.getComponent(i, NPCEntity.getComponentType());
            if (uc == null || npc == null) {
                continue;
            }
            UUID entityUuid = uc.getUuid();
            if (processedEntityUuids.contains(entityUuid)) {
                continue;
            }
            boolean hasVillagerBinding = chunk.getComponent(i, TownVillagerBinding.getComponentType()) != null;
            boolean hasTownsfolkBinding = chunk.getComponent(i, TownsfolkCharacterBinding.getComponentType()) != null;
            boolean hasGuildHallAnchor = chunk.getComponent(i, GuildHallDisplayAnchor.getComponentType()) != null;
            if (!isOrphanTownsfolkShellForPurge(
                npc.getRoleName(),
                hasVillagerBinding,
                hasTownsfolkBinding,
                hasGuildHallAnchor,
                entityUuid,
                trackedNpcUuids
            )) {
                continue;
            }
            commandBuffer.removeEntity(ref, RemoveReason.REMOVE);
            TownsfolkPoolCheckoutRecord checkout = pool.checkoutForEntity(entityUuid);
            if (checkout != null && checkout.getCharacterId() != null && !checkout.getCharacterId().isBlank()) {
                releaseCharacterIds.add(checkout.getCharacterId());
            }
            processedEntityUuids.add(entityUuid);
            removed[0]++;
        }
    }
}
