package com.hexvane.aetherhaven.tourist;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.entity.EntityPresenceUtil;
import com.hexvane.aetherhaven.entity.EntityPresenceUtil.EntityPresence;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownOnlinePresence;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownTerritoryChunkUtil;
import com.hexvane.aetherhaven.townsfolk.TownsfolkAssignmentKinds;
import com.hexvane.aetherhaven.townsfolk.TownsfolkExistenceService;
import com.hexvane.aetherhaven.townsfolk.TownsfolkPoolCheckoutRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkPoolPersistence;
import com.hexvane.aetherhaven.townsfolk.TownsfolkPoolState;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hexvane.aetherhaven.townsfolk.TownsfolkSpawnService;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterDefinition;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.PersistentDisplayName;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.time.TimeResource;
import com.hypixel.hytale.server.core.modules.time.WorldTimeResource;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** Aligns persisted tourist rows with live entities; releases stale rows back to the spawn pool. */
public final class TouristReconcileService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private TouristReconcileService() {}

    public static void scheduleAfterWorldLoad(@Nonnull World world, @Nonnull AetherhavenPlugin plugin) {
        world.execute(() -> reconcileOnWorldThread(world, plugin, false));
        plugin.scheduleOnWorld(world, () -> reconcileOnWorldThread(world, plugin, false), 2_000L);
        plugin.scheduleOnWorld(world, () -> reconcileOnWorldThread(world, plugin, true), 10_000L);
    }

    /** Reconcile tourists once a town member is online so unloaded chunks are not mistaken for missing NPCs. */
    public static void onTownMemberPlayerReady(@Nonnull World world, @Nonnull AetherhavenPlugin plugin, @Nonnull UUID playerUuid) {
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.findTownForOwnerInWorld(playerUuid);
        if (town == null) {
            return;
        }
        reconcileOnWorldThread(world, plugin, true);
    }

    public static void reconcileOnWorldThread(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        boolean releaseMissing
    ) {
        Store<EntityStore> store = world.getEntityStore() != null ? world.getEntityStore().getStore() : null;
        if (store == null) {
            LOGGER.atWarning().log("Tourist reconcile skipped: entity store not ready for world %s", world.getName());
            return;
        }

        WorldTimeResource wtr = store.getResource(WorldTimeResource.getResourceType());
        long currentDawnEpochDay = wtr != null ? VillagerReputationService.currentGameEpochDay(store) : Long.MIN_VALUE;

        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        Set<UUID> onlinePlayers = TownOnlinePresence.collectOnlinePlayerUuids(world);
        Map<String, TownsfolkExistenceService.LiveTownsfolkEntity> liveByCharacter =
            TownsfolkExistenceService.buildLiveIndex(store);
        TownsfolkPoolState pool = TownsfolkPoolPersistence.getOrLoad(world, plugin);
        boolean townChanged = false;
        int synced = 0;
        int released = 0;

        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            boolean memberOnline = TownOnlinePresence.hasAffiliatedPlayerOnline(town, onlinePlayers);
            boolean townNpcChunksLoaded =
                TownTerritoryChunkUtil.isAnyTownNpcChunkLoaded(world, plugin, town);
            boolean changed = false;
            Set<String> seenCharacters = new HashSet<>();
            Iterator<TouristRecord> it = town.getTouristRecords().iterator();
            while (it.hasNext()) {
                TouristRecord rec = it.next();
                String characterId = rec.getCharacterId();
                if (characterId.isBlank()) {
                    it.remove();
                    changed = true;
                    continue;
                }
                if (seenCharacters.contains(characterId)) {
                    it.remove();
                    changed = true;
                    continue;
                }
                seenCharacters.add(characterId);

                if (!memberOnline) {
                    continue;
                }

                if (rec.isCitizen()) {
                    syncPoolCheckout(pool, town, rec, liveByCharacter.get(characterId));
                    continue;
                }

                if (rec.getSpawnEpochDay() == 0L && currentDawnEpochDay != Long.MIN_VALUE) {
                    rec.setSpawnEpochDay(currentDawnEpochDay);
                    changed = true;
                }
                TouristPortalTickService.ensureLeaveHour(rec);

                if (TouristPortalTickService.shouldTouristLeaveNow(rec, store)) {
                    UUID entityUuid = rec.getEntityUuid();
                    Ref<EntityStore> leaveRef = refForRecord(town, store, liveByCharacter, rec);
                    if (leaveRef != null && leaveRef.isValid() && entityUuid != null) {
                        if (!repairTouristIdentity(leaveRef, store, plugin, town, rec)) {
                            purgeBrokenTouristEntity(world, plugin, store, town, rec);
                            it.remove();
                            changed = true;
                            released++;
                        } else {
                            TouristPortalTickService.sendTouristHomeOrFinalize(
                                world, plugin, tm, store, town, rec, entityUuid
                            );
                        }
                    } else if (!rec.isInvitedToStay()) {
                        EntityPresence presence = EntityPresenceUtil.resolve(store, entityUuid);
                        if (EntityPresenceUtil.shouldFinalizeTouristLeaveForMissingEntity(presence)) {
                            releaseStaleTouristRecord(world, plugin, town, rec);
                            it.remove();
                            changed = true;
                            released++;
                        }
                    }
                    continue;
                }

                Ref<EntityStore> ref = refForRecord(town, store, liveByCharacter, rec);
                if (ref != null && ref.isValid()) {
                    if (!repairTouristIdentity(ref, store, plugin, town, rec)) {
                        purgeBrokenTouristEntity(world, plugin, store, town, rec);
                        it.remove();
                        changed = true;
                        released++;
                        continue;
                    }
                    UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
                    UUID liveUuid = uc != null ? uc.getUuid() : rec.getEntityUuid();
                    if (liveUuid != null) {
                        UUID recorded = rec.getEntityUuid();
                        if (recorded == null || !recorded.equals(liveUuid)) {
                            rec.setEntityUuid(liveUuid);
                            changed = true;
                            synced++;
                        }
                    }
                    ensureAutonomyAfterBind(ref, store, plugin, town, world, rec);
                    syncPoolCheckout(pool, town, rec, liveByCharacter.get(characterId));
                    continue;
                }

                if (rec.isInvitedToStay()) {
                    continue;
                }

                EntityPresence presence = EntityPresenceUtil.resolve(store, rec.getEntityUuid());
                if (EntityPresenceUtil.shouldReleaseMissingTouristRecord(
                    presence,
                    releaseMissing,
                    townNpcChunksLoaded
                )) {
                    releaseStaleTouristRecord(world, plugin, town, rec);
                    it.remove();
                    changed = true;
                    released++;
                }
            }
            if (changed) {
                tm.updateTown(town);
                townChanged = true;
            }
            if (memberOnline && townNpcChunksLoaded) {
                if (repairOrphanLiveTourists(world, plugin, tm, town, store, liveByCharacter, currentDawnEpochDay)) {
                    townChanged = true;
                }
            }
        }

        if (townChanged) {
            TownsfolkPoolPersistence.save(world, plugin, pool);
            LOGGER.atInfo().log(
                "Tourist reconcile in world %s (releaseMissing=%s): synced %s, released %s",
                world.getName(),
                releaseMissing,
                synced,
                released
            );
        }
    }

    /** True while a tourist row still exists for this character (pool reclaim should not steal the checkout yet). */
    public static boolean isActiveTouristCharacter(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull String characterId
    ) {
        if (characterId.isBlank()) {
            return false;
        }
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        for (TownRecord town : tm.allTowns()) {
            if (!world.getName().equals(town.getWorldName())) {
                continue;
            }
            for (TouristRecord rec : town.getTouristRecords()) {
                if (characterId.equals(rec.getCharacterId())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isLiveTouristEntity(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull Map<String, TownsfolkExistenceService.LiveTownsfolkEntity> liveByCharacter,
        @Nonnull TouristRecord rec
    ) {
        String characterId = rec.getCharacterId();
        if (characterId.isBlank()) {
            return false;
        }
        TownsfolkExistenceService.LiveTownsfolkEntity live = liveByCharacter.get(characterId);
        if (live != null
            && town.getTownId().equals(live.townId())
            && TownsfolkAssignmentKinds.isTourist(live.assignmentKind())) {
            return true;
        }
        UUID recorded = rec.getEntityUuid();
        if (recorded == null) {
            return false;
        }
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(recorded);
        return entityHasTouristComponents(store, ref, town);
    }

    /** True when the entity can run tourist autonomy and dialogue identity resolution. */
    public static boolean entityHasTouristComponents(
        @Nonnull Store<EntityStore> store,
        @Nullable Ref<EntityStore> ref,
        @Nonnull TownRecord town
    ) {
        if (ref == null || !ref.isValid()) {
            return false;
        }
        NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc == null) {
            return false;
        }
        TownsfolkCharacterBinding tb = store.getComponent(ref, TownsfolkCharacterBinding.getComponentType());
        if (tb == null || !TownsfolkAssignmentKinds.isTourist(tb.getAssignmentKind())) {
            return false;
        }
        TownVillagerBinding vb = store.getComponent(ref, TownVillagerBinding.getComponentType());
        return vb != null && town.getTownId().equals(vb.getTownId());
    }

    @Nonnull
    public static Set<String> liveTouristCharacterIdsInTown(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store
    ) {
        Set<String> out = new HashSet<>();
        Map<String, TownsfolkExistenceService.LiveTownsfolkEntity> liveByCharacter =
            TownsfolkExistenceService.buildLiveIndex(store);
        for (TouristRecord rec : town.getTouristRecords()) {
            if (isLiveTouristEntity(town, store, liveByCharacter, rec)) {
                out.add(rec.getCharacterId());
            }
        }
        for (TownsfolkExistenceService.LiveTownsfolkEntity live : liveByCharacter.values()) {
            if (town.getTownId().equals(live.townId()) && TownsfolkAssignmentKinds.isTourist(live.assignmentKind())) {
                out.add(live.characterId());
            }
        }
        return out;
    }

    private static void releaseStaleTouristRecord(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TouristRecord rec
    ) {
        UUID entityUuid = rec.getEntityUuid();
        if (entityUuid != null) {
            TownsfolkExistenceService.releaseByEntity(world, plugin, entityUuid);
            return;
        }
        String characterId = rec.getCharacterId();
        if (!characterId.isBlank()) {
            TownsfolkSpawnService.release(world, plugin, town.getTownId(), characterId);
        }
    }

    /**
     * Rebuilds missing or incomplete tourist identity components from the catalog.
     *
     * @return false when the character id is unknown and the entity cannot be repaired
     */
    private static boolean repairTouristIdentity(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TouristRecord rec
    ) {
        String characterId = rec.getCharacterId();
        if (characterId.isBlank()) {
            return false;
        }
        TownsfolkCharacterDefinition def = plugin.getTownsfolkCharacterCatalog().byId(characterId);
        if (def == null) {
            return false;
        }

        TownsfolkCharacterBinding binding = store.getComponent(ref, TownsfolkCharacterBinding.getComponentType());
        boolean needsBinding =
            binding == null
                || !characterId.equals(binding.getCharacterId())
                || !TownsfolkAssignmentKinds.isTourist(binding.getAssignmentKind())
                || binding.getModelAssetId().isBlank()
                || binding.getPersonalityIds().isEmpty();
        if (needsBinding) {
            String modelId =
                binding != null && !binding.getModelAssetId().isBlank()
                    ? binding.getModelAssetId()
                    : def.getModelAssetId();
            var personalities =
                binding != null && !binding.getPersonalityIds().isEmpty()
                    ? binding.getPersonalityIds()
                    : def.getPersonalityIds();
            String activePersonality =
                binding != null ? binding.getActivePersonalityId() : "";
            store.putComponent(
                ref,
                TownsfolkCharacterBinding.getComponentType(),
                new TownsfolkCharacterBinding(
                    characterId,
                    activePersonality,
                    TownsfolkAssignmentKinds.TOURIST,
                    modelId,
                    personalities
                )
            );
        }

        TownVillagerBinding villagerBinding = store.getComponent(ref, TownVillagerBinding.getComponentType());
        if (villagerBinding == null || !town.getTownId().equals(villagerBinding.getTownId())) {
            store.putComponent(
                ref,
                TownVillagerBinding.getComponentType(),
                new TownVillagerBinding(town.getTownId(), TownVillagerBinding.KIND_TOWNSFOLK, null)
            );
        }

        ensureDisplayName(ref, store, plugin, characterId);
        return store.getComponent(ref, NPCEntity.getComponentType()) != null;
    }

    private static void purgeBrokenTouristEntity(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull TouristRecord rec
    ) {
        UUID entityUuid = rec.getEntityUuid();
        if (entityUuid != null) {
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(entityUuid);
            if (ref != null && ref.isValid()) {
                store.removeEntity(ref, RemoveReason.REMOVE);
            }
        }
        releaseStaleTouristRecord(world, plugin, town, rec);
    }

    private static void ensureAutonomyAfterBind(
        @Nullable Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull World world,
        @Nonnull TouristRecord rec
    ) {
        if (ref == null || !ref.isValid()) {
            return;
        }
        TownsfolkCharacterBinding binding = store.getComponent(ref, TownsfolkCharacterBinding.getComponentType());
        if (binding != null) {
            ensureDisplayName(ref, store, plugin, binding.getCharacterId());
        }

        long now = resolveNowMs(store);
        TouristAutonomyState autonomy = store.getComponent(ref, TouristAutonomyState.getComponentType());
        if (autonomy == null) {
            autonomy = TouristAutonomyState.fresh(now);
        }
        UUID portalId = rec.getPortalId();
        if (portalId != null && autonomy.getHomePortalId() == null) {
            autonomy.setHomePortalId(portalId);
        }
        store.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);

        // Citizens also browse destinations; only skip the near-portal kick for invitees who may be settling.
        if (autonomy.getPhase() == TouristAutonomyState.PHASE_IDLE) {
            boolean nearPortalWithoutVisit =
                autonomy.getVisitPlotUuid() == null && isIdleNearPortal(world, plugin, store, ref, rec, autonomy);
            boolean staleIdle =
                autonomy.getVisitPlotUuid() == null
                    && now >= autonomy.getNextDecisionEpochMs() + AetherhavenConstants.TOURIST_PORTAL_IDLE_KICK_MS;
            boolean citizenBrowseKick =
                (rec.isCitizen() || rec.isInvitedToStay())
                    && autonomy.getVisitPlotUuid() == null
                    && now >= autonomy.getNextDecisionEpochMs();
            boolean visitingTouristKick =
                !rec.isInvitedToStay() && (nearPortalWithoutVisit || staleIdle || autonomy.getVisitPlotUuid() == null);
            if (visitingTouristKick || citizenBrowseKick) {
                NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                if (npc != null) {
                    TouristAutonomySystem.kickInitialVisitOnSpawn(ref, store, plugin, autonomy, town, world);
                    store.putComponent(ref, TouristAutonomyState.getComponentType(), autonomy);
                }
            }
        }
    }

    private static boolean repairOrphanLiveTourists(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownManager tm,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull Map<String, TownsfolkExistenceService.LiveTownsfolkEntity> liveByCharacter,
        long currentDawnEpochDay
    ) {
        boolean changed = false;
        TouristPortalRegistry registry = AetherhavenWorldRegistries.getOrCreateTouristPortalRegistry(world, plugin);
        List<TouristPortalRecord> portals = registry.recordsForTown(town.getTownId());
        for (TownsfolkExistenceService.LiveTownsfolkEntity live : liveByCharacter.values()) {
            if (!town.getTownId().equals(live.townId())) {
                continue;
            }
            if (!TownsfolkAssignmentKinds.isTourist(live.assignmentKind())) {
                continue;
            }
            String characterId = live.characterId();
            if (characterId.isBlank()) {
                continue;
            }
            if (TouristPortalTickService.findTouristRecord(town, characterId) != null) {
                TouristRecord existing = TouristPortalTickService.findTouristRecord(town, characterId);
                if (existing != null
                    && live.entityUuid() != null
                    && !live.entityUuid().equals(existing.getEntityUuid())) {
                    existing.setEntityUuid(live.entityUuid());
                    if (repairTouristIdentity(live.ref(), store, plugin, town, existing)) {
                        ensureAutonomyAfterBind(live.ref(), store, plugin, town, world, existing);
                    }
                    changed = true;
                }
                continue;
            }
            if (!live.ref().isValid()) {
                continue;
            }
            if (portals.isEmpty()) {
                UUID entityUuid = live.entityUuid();
                if (entityUuid != null) {
                    TouristPortalTickService.despawnTourist(world, plugin, town, tm, store, entityUuid, null);
                }
                TownsfolkSpawnService.release(world, plugin, town.getTownId(), characterId);
                changed = true;
                continue;
            }
            TouristPortalRecord portal = pickNearestPortal(world, store, live.ref(), portals);
            TouristRecord rec =
                new TouristRecord(
                    characterId,
                    live.entityUuid(),
                    portal.getPortalId(),
                    false,
                    false,
                    currentDawnEpochDay != Long.MIN_VALUE ? currentDawnEpochDay : 0L,
                    TouristPortalTickService.rollLeaveHour(
                        portal.getPortalId(),
                        characterId,
                        currentDawnEpochDay != Long.MIN_VALUE ? currentDawnEpochDay : 0L
                    )
                );
            town.getTouristRecords().add(rec);
            if (repairTouristIdentity(live.ref(), store, plugin, town, rec)) {
                ensureAutonomyAfterBind(live.ref(), store, plugin, town, world, rec);
            }
            changed = true;
        }
        if (changed) {
            tm.updateTown(town);
        }
        return changed;
    }

    private static void ensureDisplayName(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull String characterId
    ) {
        if (store.getComponent(ref, PersistentDisplayName.getComponentType()) != null) {
            return;
        }
        TownsfolkCharacterDefinition def = plugin.getTownsfolkCharacterCatalog().byId(characterId);
        if (def == null) {
            return;
        }
        String displayName = def.getDisplayName();
        if (displayName == null || displayName.isBlank()) {
            return;
        }
        store.putComponent(
            ref,
            PersistentDisplayName.getComponentType(),
            new PersistentDisplayName(Message.raw(displayName))
        );
    }

    private static boolean isIdleNearPortal(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull TouristRecord rec,
        @Nonnull TouristAutonomyState autonomy
    ) {
        if (autonomy.getPhase() != TouristAutonomyState.PHASE_IDLE || autonomy.getVisitPlotUuid() != null) {
            return false;
        }
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) {
            return false;
        }
        UUID portalId = rec.getPortalId();
        if (portalId == null) {
            portalId = autonomy.getHomePortalId();
        }
        if (portalId == null) {
            return false;
        }
        TouristPortalRecord portal =
            AetherhavenWorldRegistries.getOrCreateTouristPortalRegistry(world, plugin).get(portalId);
        if (portal == null) {
            return false;
        }
        return TouristPortalBlockUtil.isNearPortalDespawn(world, portal.getBlockPosition(), tc.getPosition());
    }

    @Nonnull
    private static TouristPortalRecord pickNearestPortal(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull List<TouristPortalRecord> portals
    ) {
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null || portals.size() == 1) {
            return portals.get(0);
        }
        Vector3d pos = tc.getPosition();
        TouristPortalRecord best = portals.get(0);
        double bestSq = Double.MAX_VALUE;
        for (TouristPortalRecord portal : portals) {
            Vector3d stand = TouristPortalBlockUtil.returnStandPosition(world, portal.getBlockPosition());
            double dx = pos.x - stand.x;
            double dz = pos.z - stand.z;
            double sq = dx * dx + dz * dz;
            if (sq < bestSq) {
                bestSq = sq;
                best = portal;
            }
        }
        return best;
    }

    private static long resolveNowMs(@Nonnull Store<EntityStore> store) {
        TimeResource tr = store.getResource(TimeResource.getResourceType());
        return tr != null ? tr.getNow().toEpochMilli() : System.currentTimeMillis();
    }

    @Nullable
    private static Ref<EntityStore> refForRecord(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull Map<String, TownsfolkExistenceService.LiveTownsfolkEntity> liveByCharacter,
        @Nonnull TouristRecord rec
    ) {
        TownsfolkExistenceService.LiveTownsfolkEntity live = liveByCharacter.get(rec.getCharacterId());
        if (live != null && live.ref().isValid()) {
            return live.ref();
        }
        UUID recorded = rec.getEntityUuid();
        if (recorded == null) {
            return null;
        }
        return store.getExternalData().getRefFromUUID(recorded);
    }

    private static void syncPoolCheckout(
        @Nonnull TownsfolkPoolState pool,
        @Nonnull TownRecord town,
        @Nonnull TouristRecord rec,
        @Nullable TownsfolkExistenceService.LiveTownsfolkEntity live
    ) {
        String characterId = rec.getCharacterId();
        if (characterId.isBlank()) {
            return;
        }
        UUID entityUuid = live != null ? live.entityUuid() : rec.getEntityUuid();
        if (entityUuid == null) {
            return;
        }
        TownsfolkPoolCheckoutRecord checkout = pool.checkoutForCharacter(town.getTownId(), characterId);
        if (checkout == null) {
            pool.checkout(
                new TownsfolkPoolCheckoutRecord(
                    characterId,
                    town.getTownId().toString(),
                    entityUuid.toString(),
                    TownsfolkAssignmentKinds.TOURIST,
                    ""
                )
            );
            return;
        }
        if (!town.getTownId().toString().equalsIgnoreCase(checkout.getTownId())) {
            UUID oldTownId = parseTownUuid(checkout.getTownId());
            if (oldTownId != null) {
                pool.release(oldTownId, characterId);
            }
            pool.checkout(
                new TownsfolkPoolCheckoutRecord(
                    characterId,
                    town.getTownId().toString(),
                    entityUuid.toString(),
                    TownsfolkAssignmentKinds.TOURIST,
                    ""
                )
            );
            return;
        }
        if (!entityUuid.toString().equalsIgnoreCase(checkout.getEntityUuid())) {
            checkout.setEntityUuid(entityUuid.toString());
        }
        if (!TownsfolkAssignmentKinds.isTourist(checkout.getAssignmentKind())) {
            checkout.setAssignmentKind(TownsfolkAssignmentKinds.TOURIST);
        }
    }

    @Nullable
    private static UUID parseTownUuid(@Nonnull String townIdStr) {
        try {
            return UUID.fromString(townIdStr.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
