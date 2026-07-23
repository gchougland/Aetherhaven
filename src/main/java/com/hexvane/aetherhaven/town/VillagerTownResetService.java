package com.hexvane.aetherhaven.town;

import com.hypixel.hytale.math.vector.Rotation3f;

import com.hypixel.hytale.math.vector.Vector3fUtil;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomyTravelKick;
import com.hexvane.aetherhaven.guild.GuardHireService;
import com.hexvane.aetherhaven.inn.InnPoolService;
import com.hexvane.aetherhaven.inn.InnPlotResolver;
import com.hexvane.aetherhaven.patrol.PatrolRoutePersistence;
import com.hexvane.aetherhaven.patrol.PatrolRouteRegistry;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.schedule.VillagerScheduleService;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.equipment.data.EquipmentProfileDefinition;
import com.hexvane.aetherhaven.townsfolk.TownsfolkAssignmentKinds;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hexvane.aetherhaven.townsfolk.TownsfolkExistenceService;
import com.hexvane.aetherhaven.townsfolk.TownsfolkSpawnService;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterDefinition;
import com.hexvane.aetherhaven.tourist.TouristRecord;
import com.hexvane.aetherhaven.villager.AetherhavenVillagerHandle;
import com.hexvane.aetherhaven.villager.NpcSpawnOriginUtil;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.logger.HytaleLogger;
import org.joml.Vector3d;
import org.joml.Vector3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Debug/admin: remove all town-tracked villager NPCs and respawn them near a position while preserving town quest state,
 * reputation (entity-UUID keys migrated), gift logs (role-keyed), and inn pool rules. Missing or unloaded entities are
 * still respawned from persisted town data; loaded entities are removed before respawn. Also removes loaded stray
 * Aetherhaven villager NPCs for this town (same-town binding not in town data, or mod-marked with no binding / lost
 * binding when the debug handle matches this town). NPCs bound to another town that still exists in this world's save
 * are left alone; NPCs whose binding town id is missing from save data are treated as invalid and removed.
 */
public final class VillagerTownResetService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private record CapturedNpc(
        @Nonnull UUID previousEntityUuid,
        boolean entityPresentInStore,
        @Nonnull String npcRoleId,
        @Nonnull String bindingKind,
        @Nullable UUID jobPlotId,
        boolean visitor,
        @Nullable String guardCharacterId,
        @Nullable String guardEquipmentProfileId,
        @Nullable TownsfolkCharacterBinding guardCharacterBinding,
        boolean guardCitizen,
        @Nullable String poolCharacterId,
        @Nullable TownsfolkCharacterBinding poolCharacterBinding,
        @Nullable String poolAssignmentKind,
        boolean poolTownsfolkCitizen
    ) {}

    private VillagerTownResetService() {}

    /**
     * @return English diagnostic when reset cannot proceed safely, otherwise null
     */
    @Nullable
    public static String resetAllTownVillagersNearPlayer(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3d basePosition
    ) {
        town.migrateInnFieldsIfNeeded();
        // Sync visitor bindings / resident registry with completed job plots (fixes "quest completed but still visitor").
        InnPoolService.repairInnPoolForTown(world, plugin, town, tm, store, false);
        LinkedHashMap<UUID, CapturedNpc> captured = captureNpcs(town, store, plugin);
        InnPoolService.reconcileInnVisitorEntities(world, town, tm, store, true);
        purgeLoadedTouristCitizenDuplicates(world, store, town, captured);
        int genericDupesPurged = 0;
        if (townHasPromotedTouristCitizen(town)) {
            genericDupesPurged = purgeDuplicateGenericTownsfolkResidents(store, town, captured.keySet());
        }
        if (genericDupesPurged > 0) {
            LOGGER.atInfo().log(
                "Reset: removed %s duplicate generic townsfolk NPC(s) for town %s",
                genericDupesPurged,
                town.getTownId()
            );
        }
        int straysPurged = purgeStrayLoadedVillagerNpcsForTownReset(store, town, tm, captured.keySet());
        if (straysPurged > 0) {
            LOGGER.atInfo().log("Reset: removed %s stray loaded villager NPC(s) for town %s", straysPurged, town.getTownId());
        }
        InnPoolService.despawnAllTownInnVisitors(town, store);
        if (captured.isEmpty()) {
            return straysPurged > 0 ? null : "No tracked villager NPCs found for this town.";
        }

        for (CapturedNpc c : captured.values()) {
            if (!c.entityPresentInStore) {
                continue;
            }
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(c.previousEntityUuid);
            if (ref == null || !ref.isValid()) {
                continue;
            }
            store.removeEntity(ref, RemoveReason.REMOVE);
        }

        town.getInnPoolNpcIds().clear();
        town.getInnLockedEntityUuids().clear();
        tm.updateTown(town);

        List<CapturedNpc> order = new ArrayList<>(captured.values());
        order.sort(Comparator.comparingInt(VillagerTownResetService::captureSortKey));

        PlotInstance innPlot =
            InnPlotResolver.resolveInnPlotForVisitors(town, plugin.getConstructionCatalog(), store);
        int slot = 0;
        List<UUID> spawnedUuids = new ArrayList<>();
        for (CapturedNpc c : order) {
            Vector3d pos = new Vector3d(basePosition.x + slot * 1.25, basePosition.y, basePosition.z);
            slot++;
            UUID newUuid;
            if (c.visitor) {
                UUID spawned = InnPoolService.spawnVisitorAtWorldPosition(
                    store,
                    town,
                    c.npcRoleId.trim(),
                    c.bindingKind,
                    pos,
                    innPlot
                );
                if (spawned == null) {
                    LOGGER.atWarning().log("Reset: failed to spawn visitor %s for town %s", c.npcRoleId, town.getTownId());
                    continue;
                }
                newUuid = spawned;
                town.getInnPoolNpcIds().add(spawned.toString());
                if (InnPoolService.innQuestLocksVisitorRole(town, c.npcRoleId.trim())) {
                    town.addInnLockedEntity(spawned);
                }
            } else if (
                TownVillagerBinding.KIND_GUARD.equals(c.bindingKind)
                    && c.guardCharacterId() != null
                    && !c.guardCharacterId().isBlank()
                    && c.guardCharacterBinding() != null
            ) {
                UUID spawned = GuardHireService.respawnHiredGuardAtPosition(
                    world,
                    plugin,
                    town,
                    store,
                    c.guardCharacterId(),
                    c.guardEquipmentProfileId() != null && !c.guardEquipmentProfileId().isBlank()
                        ? c.guardEquipmentProfileId()
                        : "guard_knight",
                    c.guardCharacterBinding(),
                    pos,
                    c.jobPlotId()
                );
                if (spawned == null) {
                    LOGGER.atWarning().log("Reset: failed to spawn guard %s for town %s", c.guardCharacterId(), town.getTownId());
                    continue;
                }
                newUuid = spawned;
                if (c.guardCitizen()) {
                    ResidentRegistryService.upsert(town, tm, c.npcRoleId(), c.bindingKind(), c.jobPlotId(), newUuid);
                }
            } else if (
                c.poolTownsfolkCitizen()
                    && c.poolCharacterId() != null
                    && !c.poolCharacterId().isBlank()
                    && c.poolCharacterBinding() != null
                    && c.poolAssignmentKind() != null
                    && !c.poolAssignmentKind().isBlank()
            ) {
                UUID spawned =
                    TownsfolkSpawnService.respawnPoolCharacterAtPosition(
                        world,
                        plugin,
                        town,
                        store,
                        c.poolCharacterId(),
                        c.poolAssignmentKind(),
                        c.poolCharacterBinding(),
                        pos,
                        "ADMIN_RESET",
                        "assignmentKind="
                            + c.poolAssignmentKind()
                            + ",characterId="
                            + c.poolCharacterId()
                            + ",previousUuid="
                            + c.previousEntityUuid
                    );
                if (spawned == null) {
                    LOGGER.atWarning()
                        .log(
                            "Reset: failed to spawn tourist citizen %s for town %s",
                            c.poolCharacterId(),
                            town.getTownId()
                        );
                    continue;
                }
                newUuid = spawned;
                for (TouristRecord rec : town.getTouristRecords()) {
                    if (rec.isCitizen() && c.poolCharacterId().equalsIgnoreCase(rec.getCharacterId())) {
                        rec.setEntityUuid(newUuid);
                        break;
                    }
                }
                if (townHasPromotedTouristCitizen(town)) {
                    town.getResidentNpcRecords().removeIf(VillagerTownResetService::isGenericTownsfolkResidentRecord);
                }
            } else {
                UUID spawned = spawnResidentLikeNpc(store, town, tm, c, pos, innPlot);
                if (spawned == null) {
                    LOGGER.atWarning().log("Reset: failed to spawn %s for town %s", c.npcRoleId, town.getTownId());
                    continue;
                }
                newUuid = spawned;
            }
            VillagerReputationService.migrateVillagerEntityUuid(town, tm, c.previousEntityUuid, newUuid);
            ResidentRegistryService.replaceEntityUuidEverywhere(town, tm, c.previousEntityUuid, newUuid);
            migratePatrolRoutesForGuard(world, plugin, c.previousEntityUuid, newUuid);
            spawnedUuids.add(newUuid);
        }

        if (innPlot != null) {
            InnPoolService.fillRemainingInnVisitorSlotsNear(world, plugin, town, tm, store, innPlot, basePosition, slot);
        }

        tm.updateTown(town);
        // New spawns can still be visitors while their stall/farm/etc. is already complete; promote after UUID migration.
        InnPoolService.repairInnPoolForTown(world, plugin, town, tm, store);
        tm.updateTown(town);
        world.execute(
            () -> {
                VillagerScheduleService.applyForWorld(world, store, plugin, true);
                for (UUID id : spawnedUuids) {
                    VillagerAutonomyTravelKick.kickTravelToSchedulePoi(plugin, world, store, id, false);
                }
            }
        );
        return null;
    }

    /**
     * Debug/admin: remove duplicate loaded copies of one core story citizen and respawn a single NPC near {@code basePosition}
     * while preserving town quest state and reputation (entity-UUID keys migrated).
     *
     * @return English diagnostic when reset cannot proceed safely, otherwise null
     */
    @Nullable
    public static String resetOneCoreCitizenNearPlayer(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID targetEntityUuid,
        @Nonnull Vector3d basePosition
    ) {
        CoreCitizenVillagerEligibility.Outcome eligibility =
            CoreCitizenVillagerEligibility.resolveCoreCitizen(town, world, store, targetEntityUuid);
        if (!eligibility.isOk()) {
            return eligibility.error() != null ? eligibility.error() : "Not a core story citizen.";
        }

        LinkedHashMap<UUID, CapturedNpc> captured = new LinkedHashMap<>();
        putNonVisitorFromTownData(captured, town, store, targetEntityUuid);
        CapturedNpc c = captured.get(targetEntityUuid);
        if (c == null) {
            return "Could not capture villager state from town data.";
        }
        if (c.visitor) {
            return "Inn visitors cannot be respawned with this command.";
        }

        String roleId = c.npcRoleId.trim();
        if (roleId.isEmpty()) {
            return "Could not resolve villager role.";
        }

        int dupesRemoved = removeLoadedDuplicatesForCitizenRole(store, town, roleId);
        if (dupesRemoved > 0) {
            LOGGER.atInfo().log(
                "Single respawn: removed %s duplicate loaded NPC(s) for role %s in town %s",
                dupesRemoved,
                roleId,
                town.getTownId()
            );
        }

        Ref<EntityStore> oldRef = store.getExternalData().getRefFromUUID(targetEntityUuid);
        if (oldRef != null && oldRef.isValid()) {
            store.removeEntity(oldRef, RemoveReason.REMOVE);
        }

        PlotInstance innPlot =
            InnPlotResolver.resolveInnPlotForVisitors(town, plugin.getConstructionCatalog(), store);
        UUID newUuid = spawnResidentLikeNpc(store, town, tm, c, basePosition, innPlot);
        if (newUuid == null) {
            return "Failed to spawn villager NPC.";
        }

        VillagerReputationService.migrateVillagerEntityUuid(town, tm, c.previousEntityUuid, newUuid);
        ResidentRegistryService.replaceEntityUuidEverywhere(town, tm, c.previousEntityUuid, newUuid);
        tm.updateTown(town);

        UUID spawnedUuid = newUuid;
        world.execute(
            () -> {
                VillagerScheduleService.applyForWorld(world, store, plugin, true);
                VillagerAutonomyTravelKick.kickTravelToSchedulePoi(plugin, world, store, spawnedUuid, false);
            }
        );
        return null;
    }

    /**
     * Removes every loaded NPC bound to {@code town} with the given NPC role id (including stray duplicates).
     *
     * @return number of entities removed
     */
    private static int removeLoadedDuplicatesForCitizenRole(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull String roleId
    ) {
        UUID townId = town.getTownId();
        String wantedRole = roleId.trim();
        if (wantedRole.isEmpty()) {
            return 0;
        }
        Query<EntityStore> q = Query.and(NPCEntity.getComponentType(), TownVillagerBinding.getComponentType());
        List<Ref<EntityStore>> toRemove = new ArrayList<>();
        store.forEachChunk(q, (archetypeChunk, commandBuffer) -> {
            int n = archetypeChunk.size();
            for (int i = 0; i < n; i++) {
                Ref<EntityStore> npcRef = archetypeChunk.getReferenceTo(i);
                if (npcRef == null || !npcRef.isValid()) {
                    continue;
                }
                TownVillagerBinding b = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
                if (b == null || !townId.equals(b.getTownId())) {
                    continue;
                }
                NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
                if (npc == null || npc.getRoleName() == null || npc.getRoleName().isBlank()) {
                    continue;
                }
                if (wantedRole.equals(npc.getRoleName().trim())) {
                    toRemove.add(npcRef);
                }
            }
        });
        int count = 0;
        for (Ref<EntityStore> r : toRemove) {
            if (r.isValid()) {
                store.removeEntity(r, RemoveReason.REMOVE);
                count++;
            }
        }
        return count;
    }

    private static int captureSortKey(@Nonnull CapturedNpc c) {
        if (c.visitor) {
            return 300;
        }
        if (TownVillagerBinding.KIND_ELDER.equals(c.bindingKind)) {
            return 0;
        }
        if (TownVillagerBinding.KIND_INNKEEPER.equals(c.bindingKind)) {
            return 1;
        }
        if (TownVillagerBinding.KIND_GUARD.equals(c.bindingKind)) {
            return 2;
        }
        if (c.poolTownsfolkCitizen()) {
            return 5;
        }
        return 10;
    }

    @Nonnull
    private static LinkedHashMap<UUID, CapturedNpc> captureNpcs(
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin
    ) {
        LinkedHashMap<UUID, CapturedNpc> map = new LinkedHashMap<>();

        for (ResidentNpcRecord r : town.getResidentNpcRecords()) {
            if (TownVillagerBinding.isVisitorKind(r.getKind())) {
                continue;
            }
            UUID old = r.getLastEntityUuid();
            if (old.equals(NIL_UUID)) {
                continue;
            }
            if (isTrackedTouristCitizenUuid(town, old)
                || (townHasPromotedTouristCitizen(town) && isGenericTownsfolkResidentRecord(r))) {
                continue;
            }
            putNonVisitorFromTownData(map, town, store, old);
        }

        if (town.getElderEntityUuid() != null && !town.getElderEntityUuid().equals(NIL_UUID)) {
            putNonVisitorFromTownData(map, town, store, town.getElderEntityUuid());
        }
        if (town.getInnkeeperEntityUuid() != null && !town.getInnkeeperEntityUuid().equals(NIL_UUID)) {
            putNonVisitorFromTownData(map, town, store, town.getInnkeeperEntityUuid());
        }

        LinkedHashSet<String> visitorRolesTaken = new LinkedHashSet<>();
        for (String sid : new ArrayList<>(town.getInnPoolNpcIds())) {
            if (sid == null || sid.isBlank()) {
                continue;
            }
            UUID old;
            try {
                old = UUID.fromString(sid.trim());
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (old.equals(NIL_UUID) || map.containsKey(old)) {
                continue;
            }
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(old);
            boolean loaded = ref != null && ref.isValid();
            if (loaded) {
                TownVillagerBinding b = store.getComponent(ref, TownVillagerBinding.getComponentType());
                NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
                if (b == null || npc == null || npc.getRoleName() == null || npc.getRoleName().isBlank()) {
                    continue;
                }
                if (!town.getTownId().equals(b.getTownId())) {
                    continue;
                }
                if (!TownVillagerBinding.isVisitorKind(b.getKind())) {
                    continue;
                }
                String role = npc.getRoleName().trim();
                visitorRolesTaken.add(role);
                map.put(old, new CapturedNpc(old, true, role, b.getKind(), b.getJobPlotId(), true, null, null, null, false, null, null, null, false));
            }
        }

        List<String> merged = InnPoolService.mergedVisitorRoleOrder(town, plugin, store);
        for (String sid : new ArrayList<>(town.getInnPoolNpcIds())) {
            if (sid == null || sid.isBlank()) {
                continue;
            }
            UUID old;
            try {
                old = UUID.fromString(sid.trim());
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (old.equals(NIL_UUID) || map.containsKey(old)) {
                continue;
            }
            String roleId = null;
            for (String candidate : merged) {
                if (visitorRolesTaken.contains(candidate)) {
                    continue;
                }
                if (town.getInnVisitorPoolExcludedRoleIds().contains(candidate)) {
                    continue;
                }
                if (InnPoolService.townHasResidentWithNpcRole(store, town, candidate)) {
                    continue;
                }
                roleId = candidate;
                visitorRolesTaken.add(candidate);
                break;
            }
            if (roleId == null) {
                LOGGER.atInfo().log("Reset: inn pool uuid %s has no loaded entity and no free visitor role to infer", old);
                continue;
            }
            String kind = InnPoolService.visitorBindingKindForRole(plugin, roleId);
            map.put(old, new CapturedNpc(old, false, roleId, kind, null, true, null, null, null, false, null, null, null, false));
        }

        captureHiredGuards(map, town, store, plugin);
        captureTouristCitizens(map, town, store, plugin);

        return map;
    }

    /** Promoted tourist citizens are tracked in {@link TouristRecord}, not generic {@link ResidentNpcRecord} rows. */
    private static void captureTouristCitizens(
        @Nonnull LinkedHashMap<UUID, CapturedNpc> map,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin
    ) {
        for (TouristRecord rec : town.getTouristRecords()) {
            if (!rec.isCitizen()) {
                continue;
            }
            String characterId = rec.getCharacterId().trim();
            if (characterId.isEmpty()) {
                continue;
            }
            UUID resolved = resolveTouristCitizenEntityUuid(store, town, rec);
            if (resolved == null || NIL_UUID.equals(resolved)) {
                continue;
            }
            UUID recorded = rec.getEntityUuid();
            if (recorded != null && !recorded.equals(resolved)) {
                rec.setEntityUuid(resolved);
                if (map.containsKey(recorded)) {
                    CapturedNpc stale = map.get(recorded);
                    if (stale != null && isGenericTownsfolkResidentCapture(stale)) {
                        map.remove(recorded);
                    }
                }
            }
            TownsfolkCharacterBinding binding = resolveTouristCitizenCharacterBinding(store, resolved, plugin, characterId);
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(resolved);
            boolean loaded = ref != null && ref.isValid();
            map.put(
                resolved,
                new CapturedNpc(
                    resolved,
                    loaded,
                    AetherhavenConstants.NPC_TOWNSFOLK,
                    TownVillagerBinding.KIND_TOWNSFOLK,
                    null,
                    false,
                    null,
                    null,
                    null,
                    false,
                    characterId,
                    binding,
                    TownsfolkAssignmentKinds.TOURIST,
                    true
                )
            );
        }
    }

    @Nullable
    private static UUID resolveTouristCitizenEntityUuid(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull TouristRecord rec
    ) {
        String characterId = rec.getCharacterId().trim();
        UUID recorded = rec.getEntityUuid();
        if (recorded != null && !NIL_UUID.equals(recorded)) {
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(recorded);
            if (ref != null && ref.isValid()) {
                TownsfolkCharacterBinding live = store.getComponent(ref, TownsfolkCharacterBinding.getComponentType());
                if (live != null && characterId.equalsIgnoreCase(live.getCharacterId().trim())) {
                    return recorded;
                }
            }
        }
        UUID tid = town.getTownId();
        UUID[] found = { null };
        Query<EntityStore> q =
            Query.and(
                TownVillagerBinding.getComponentType(),
                TownsfolkCharacterBinding.getComponentType(),
                UUIDComponent.getComponentType()
            );
        store.forEachChunk(q, (archetypeChunk, commandBuffer) -> {
            if (found[0] != null) {
                return;
            }
            int n = archetypeChunk.size();
            for (int i = 0; i < n; i++) {
                TownVillagerBinding b = archetypeChunk.getComponent(i, TownVillagerBinding.getComponentType());
                TownsfolkCharacterBinding tb = archetypeChunk.getComponent(i, TownsfolkCharacterBinding.getComponentType());
                UUIDComponent uc = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                if (b == null || tb == null || uc == null) {
                    continue;
                }
                if (!tid.equals(b.getTownId())) {
                    continue;
                }
                if (!characterId.equalsIgnoreCase(tb.getCharacterId().trim())) {
                    continue;
                }
                if (!TownsfolkAssignmentKinds.isTourist(tb.getAssignmentKind())) {
                    continue;
                }
                found[0] = uc.getUuid();
                return;
            }
        });
        if (found[0] != null) {
            return found[0];
        }
        return recorded;
    }

    @Nonnull
    private static TownsfolkCharacterBinding resolveTouristCitizenCharacterBinding(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID entityUuid,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull String characterId
    ) {
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(entityUuid);
        if (ref != null && ref.isValid()) {
            TownsfolkCharacterBinding live = store.getComponent(ref, TownsfolkCharacterBinding.getComponentType());
            if (live != null && characterId.equalsIgnoreCase(live.getCharacterId())) {
                return new TownsfolkCharacterBinding(
                    live.getCharacterId(),
                    live.getActivePersonalityId(),
                    TownsfolkAssignmentKinds.TOURIST,
                    live.getModelAssetId(),
                    live.getPersonalityIds()
                );
            }
        }
        TownsfolkCharacterDefinition character = plugin.getTownsfolkCharacterCatalog().byId(characterId);
        if (character == null) {
            return new TownsfolkCharacterBinding(characterId, "", TownsfolkAssignmentKinds.TOURIST, "", List.of());
        }
        return new TownsfolkCharacterBinding(
            characterId,
            "",
            TownsfolkAssignmentKinds.TOURIST,
            character.getModelAssetId(),
            character.getPersonalityIds()
        );
    }

    private static void purgeLoadedTouristCitizenDuplicates(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull Map<UUID, CapturedNpc> captured
    ) {
        for (CapturedNpc c : captured.values()) {
            if (!c.poolTownsfolkCitizen() || c.poolCharacterId() == null || c.poolCharacterId().isBlank()) {
                continue;
            }
            TownsfolkExistenceService.purgeDuplicateEntities(
                world,
                store,
                town.getTownId(),
                c.poolCharacterId(),
                c.previousEntityUuid()
            );
        }
    }

    /**
     * Removes loaded {@code Villager_townsfolk_*} shells left by prior admin resets when the town tracks a pool tourist
     * citizen instead.
     */
    private static int purgeDuplicateGenericTownsfolkResidents(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull Set<UUID> allowedUuids
    ) {
        UUID townId = town.getTownId();
        String genericHandle = genericTownsfolkVillagerHandle(townId);
        Query<EntityStore> q =
            Query.and(
                NPCEntity.getComponentType(),
                TownVillagerBinding.getComponentType(),
                UUIDComponent.getComponentType()
            );
        List<Ref<EntityStore>> toRemove = new ArrayList<>();
        store.forEachChunk(q, (archetypeChunk, commandBuffer) -> {
            int n = archetypeChunk.size();
            for (int i = 0; i < n; i++) {
                Ref<EntityStore> npcRef = archetypeChunk.getReferenceTo(i);
                if (npcRef == null || !npcRef.isValid()) {
                    continue;
                }
                UUIDComponent uuc = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                if (uuc == null) {
                    continue;
                }
                UUID uuid = uuc.getUuid();
                if (NIL_UUID.equals(uuid) || allowedUuids.contains(uuid)) {
                    continue;
                }
                TownVillagerBinding b = archetypeChunk.getComponent(i, TownVillagerBinding.getComponentType());
                if (b == null || !townId.equals(b.getTownId()) || !TownVillagerBinding.KIND_TOWNSFOLK.equals(b.getKind())) {
                    continue;
                }
                NPCEntity npc = archetypeChunk.getComponent(i, NPCEntity.getComponentType());
                if (npc == null
                    || npc.getRoleName() == null
                    || !AetherhavenConstants.NPC_TOWNSFOLK.equalsIgnoreCase(npc.getRoleName().trim())) {
                    continue;
                }
                TownsfolkCharacterBinding tb = store.getComponent(npcRef, TownsfolkCharacterBinding.getComponentType());
                if (tb != null && TownsfolkAssignmentKinds.isTourist(tb.getAssignmentKind())) {
                    continue;
                }
                AetherhavenVillagerHandle handle = store.getComponent(npcRef, AetherhavenVillagerHandle.getComponentType());
                if (handle != null && genericHandle.equalsIgnoreCase(handle.getHandle().trim())) {
                    toRemove.add(npcRef);
                    continue;
                }
                if (handle == null && tb == null) {
                    toRemove.add(npcRef);
                }
            }
        });
        int count = 0;
        for (Ref<EntityStore> r : toRemove) {
            if (r.isValid()) {
                store.removeEntity(r, RemoveReason.REMOVE);
                count++;
            }
        }
        return count;
    }

    static boolean isGenericTownsfolkResidentRecord(@Nonnull ResidentNpcRecord record) {
        return TownVillagerBinding.KIND_TOWNSFOLK.equals(record.getKind())
            && AetherhavenConstants.NPC_TOWNSFOLK.equalsIgnoreCase(record.getNpcRoleId().trim());
    }

    private static boolean isGenericTownsfolkResidentCapture(@Nonnull CapturedNpc capture) {
        return !capture.visitor()
            && !capture.poolTownsfolkCitizen()
            && !capture.guardCitizen()
            && (capture.guardCharacterId() == null || capture.guardCharacterId().isBlank())
            && TownVillagerBinding.KIND_TOWNSFOLK.equals(capture.bindingKind())
            && AetherhavenConstants.NPC_TOWNSFOLK.equalsIgnoreCase(capture.npcRoleId().trim());
    }

    private static boolean isTrackedTouristCitizenUuid(@Nonnull TownRecord town, @Nonnull UUID entityUuid) {
        for (TouristRecord rec : town.getTouristRecords()) {
            if (!rec.isCitizen()) {
                continue;
            }
            UUID recorded = rec.getEntityUuid();
            if (recorded != null && recorded.equals(entityUuid)) {
                return true;
            }
        }
        return false;
    }

    private static boolean townHasPromotedTouristCitizen(@Nonnull TownRecord town) {
        for (TouristRecord rec : town.getTouristRecords()) {
            if (rec.isCitizen()) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    private static String genericTownsfolkVillagerHandle(@Nonnull UUID townId) {
        String hex = townId.toString().replace("-", "");
        String suffix = hex.length() >= 8 ? hex.substring(0, 8) : hex;
        return "Villager_" + TownVillagerBinding.KIND_TOWNSFOLK + "_" + suffix;
    }

    /** Hired guards are tracked in {@link HiredGuardRecord}, not only {@link ResidentNpcRecord}. */
    private static void captureHiredGuards(
        @Nonnull LinkedHashMap<UUID, CapturedNpc> map,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin
    ) {
        UUID guildJobPlot = null;
        PlotInstance guildPlot = town.findCompletePlotWithConstruction(
            plugin.getConstructionCatalog(),
            AetherhavenConstants.CONSTRUCTION_PLOT_GUILD_HALL
        );
        if (guildPlot != null) {
            guildJobPlot = guildPlot.getPlotId();
        }
        for (HiredGuardRecord rec : town.getHiredGuardRecords()) {
            UUID resolved = resolveGuardEntityUuid(store, town, rec);
            if (resolved == null || NIL_UUID.equals(resolved)) {
                continue;
            }
            UUID recorded = rec.getEntityUuid();
            if (recorded != null && !recorded.equals(resolved) && map.containsKey(recorded)) {
                map.remove(recorded);
            }
            if (!resolved.equals(recorded)) {
                rec.setEntityUuid(resolved);
            }
            String characterId = rec.getCharacterId().trim();
            if (characterId.isEmpty()) {
                continue;
            }
            String equipmentProfileId = rec.getEquipmentProfileId().trim();
            if (equipmentProfileId.isEmpty()) {
                equipmentProfileId = "guard_knight";
            }
            String roleId = guardRoleIdForProfile(plugin, equipmentProfileId);
            TownsfolkCharacterBinding binding = resolveGuardCharacterBinding(store, resolved, plugin, characterId);
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(resolved);
            boolean loaded = ref != null && ref.isValid();
            UUID jobPlotId = guildJobPlot;
            if (loaded) {
                TownVillagerBinding b = store.getComponent(ref, TownVillagerBinding.getComponentType());
                if (b != null && b.getJobPlotId() != null) {
                    jobPlotId = b.getJobPlotId();
                }
            } else {
                CapturedNpc existing = map.get(resolved);
                if (existing != null && existing.jobPlotId() != null) {
                    jobPlotId = existing.jobPlotId();
                }
            }
            map.put(
                resolved,
                new CapturedNpc(
                    resolved,
                    loaded,
                    roleId,
                    TownVillagerBinding.KIND_GUARD,
                    jobPlotId,
                    false,
                    characterId,
                    equipmentProfileId,
                    binding,
                    rec.isCitizen(),
                    null,
                    null,
                    null,
                    false
                )
            );
        }
    }

    @Nullable
    private static UUID resolveGuardEntityUuid(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull HiredGuardRecord rec
    ) {
        UUID recorded = rec.getEntityUuid();
        if (recorded != null && !NIL_UUID.equals(recorded)) {
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(recorded);
            if (ref != null && ref.isValid()) {
                return recorded;
            }
        }
        String characterId = rec.getCharacterId().trim();
        if (characterId.isEmpty()) {
            return recorded;
        }
        UUID tid = town.getTownId();
        UUID[] found = { null };
        Query<EntityStore> q = Query.and(
            TownVillagerBinding.getComponentType(),
            TownsfolkCharacterBinding.getComponentType(),
            UUIDComponent.getComponentType()
        );
        store.forEachChunk(q, (archetypeChunk, commandBuffer) -> {
            if (found[0] != null) {
                return;
            }
            int n = archetypeChunk.size();
            for (int i = 0; i < n; i++) {
                TownVillagerBinding b = archetypeChunk.getComponent(i, TownVillagerBinding.getComponentType());
                if (b == null || !tid.equals(b.getTownId()) || !TownVillagerBinding.KIND_GUARD.equals(b.getKind())) {
                    continue;
                }
                TownsfolkCharacterBinding tb = archetypeChunk.getComponent(i, TownsfolkCharacterBinding.getComponentType());
                if (tb == null || !characterId.equalsIgnoreCase(tb.getCharacterId())) {
                    continue;
                }
                UUIDComponent uc = archetypeChunk.getComponent(i, UUIDComponent.getComponentType());
                if (uc != null) {
                    found[0] = uc.getUuid();
                    return;
                }
            }
        });
        if (found[0] != null) {
            return found[0];
        }
        return recorded;
    }

    @Nonnull
    private static String guardRoleIdForProfile(@Nonnull AetherhavenPlugin plugin, @Nonnull String equipmentProfileId) {
        EquipmentProfileDefinition profile = plugin.getEquipmentProfileCatalog().byId(equipmentProfileId);
        return profile != null ? profile.getGuardNpcRole() : AetherhavenConstants.NPC_GUARD_KNIGHT;
    }

    @Nonnull
    private static TownsfolkCharacterBinding resolveGuardCharacterBinding(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID entityUuid,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull String characterId
    ) {
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(entityUuid);
        if (ref != null && ref.isValid()) {
            TownsfolkCharacterBinding live = store.getComponent(ref, TownsfolkCharacterBinding.getComponentType());
            if (live != null && characterId.equalsIgnoreCase(live.getCharacterId())) {
                return new TownsfolkCharacterBinding(
                    live.getCharacterId(),
                    live.getActivePersonalityId(),
                    com.hexvane.aetherhaven.townsfolk.TownsfolkAssignmentKinds.GUARD,
                    live.getModelAssetId(),
                    live.getPersonalityIds()
                );
            }
        }
        return GuardHireService.characterBindingFromCatalog(plugin, characterId);
    }

    private static void migratePatrolRoutesForGuard(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull UUID oldUuid,
        @Nonnull UUID newUuid
    ) {
        if (oldUuid.equals(newUuid)) {
            return;
        }
        PatrolRouteRegistry reg = AetherhavenWorldRegistries.getOrCreatePatrolRouteRegistry(world, plugin);
        if (reg.migrateGuardAssignment(oldUuid, newUuid)) {
            PatrolRoutePersistence.save(world, plugin, reg);
        }
    }

    /**
     * Removes loaded NPCs that look like Aetherhaven villagers but are not part of this town's reset capture set.
     * <ul>
     *   <li>Same {@link TownVillagerBinding#getTownId()} as {@code town} and not in the capture set: orphan for this
     *       town, removed.
     *   <li>Binding to a <em>different</em> town id that {@link TownManager#getTown(UUID)} still resolves: that town
     *       exists in this world's save; NPC is left alone.
     *   <li>Binding to a different id with no {@link TownRecord} in {@code tm}, or unreadable town id on the component:
     *       invalid / stale binding, removed.
     *   <li>No binding but mod-marked ({@link AetherhavenVillagerHandle} suffix for this town, or {@link VillagerNeeds}
     *       alone): removed as before.
     * </ul>
     *
     * @return number of entities removed
     */
    private static int purgeStrayLoadedVillagerNpcsForTownReset(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Set<UUID> uuidsInResetCapture
    ) {
        UUID townId = town.getTownId();
        Query<EntityStore> q = Query.and(
            NPCEntity.getComponentType(),
            Query.or(
                TownVillagerBinding.getComponentType(),
                VillagerNeeds.getComponentType(),
                AetherhavenVillagerHandle.getComponentType()
            )
        );
        List<Ref<EntityStore>> toRemove = new ArrayList<>();
        store.forEachChunk(q, (archetypeChunk, commandBuffer) -> {
            int n = archetypeChunk.size();
            for (int i = 0; i < n; i++) {
                Ref<EntityStore> npcRef = archetypeChunk.getReferenceTo(i);
                if (npcRef == null || !npcRef.isValid()) {
                    continue;
                }
                UUIDComponent uuc = store.getComponent(npcRef, UUIDComponent.getComponentType());
                if (uuc == null) {
                    continue;
                }
                UUID uuid = uuc.getUuid();
                if (NIL_UUID.equals(uuid) || uuidsInResetCapture.contains(uuid)) {
                    continue;
                }
                TownVillagerBinding b = store.getComponent(npcRef, TownVillagerBinding.getComponentType());
                if (b != null) {
                    UUID bindingTownId;
                    try {
                        bindingTownId = b.getTownId();
                    } catch (RuntimeException ex) {
                        toRemove.add(npcRef);
                        continue;
                    }
                    if (townId.equals(bindingTownId)) {
                        toRemove.add(npcRef);
                        continue;
                    }
                    TownRecord boundTown = tm.getTown(bindingTownId);
                    if (boundTown != null) {
                        continue;
                    }
                    toRemove.add(npcRef);
                    continue;
                }
                AetherhavenVillagerHandle h = store.getComponent(npcRef, AetherhavenVillagerHandle.getComponentType());
                VillagerNeeds needs = store.getComponent(npcRef, VillagerNeeds.getComponentType());
                boolean hasHandle = h != null && !h.getHandle().isBlank();
                boolean hasNeeds = needs != null;
                if (!hasHandle && !hasNeeds) {
                    continue;
                }
                if (hasHandle && !villagerHandleMatchesTownSuffix(townId, h.getHandle())) {
                    continue;
                }
                toRemove.add(npcRef);
            }
        });
        int count = 0;
        for (Ref<EntityStore> r : toRemove) {
            if (r.isValid()) {
                store.removeEntity(r, RemoveReason.REMOVE);
                count++;
            }
        }
        return count;
    }

    private static boolean villagerHandleMatchesTownSuffix(@Nonnull UUID townId, @Nonnull String handle) {
        String hex = townId.toString().replace("-", "");
        if (hex.isEmpty()) {
            return false;
        }
        String suffix = hex.length() >= 8 ? hex.substring(0, 8) : hex;
        return handle.toLowerCase().endsWith(suffix.toLowerCase());
    }

    private static void putNonVisitorFromTownData(
        @Nonnull Map<UUID, CapturedNpc> map,
        @Nonnull TownRecord town,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID oldUuid
    ) {
        if (map.containsKey(oldUuid)) {
            return;
        }
        Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(oldUuid);
        boolean loaded = ref != null && ref.isValid();
        String roleId = "";
        String kind = "";
        UUID jobPlotId = null;
        if (loaded) {
            TownVillagerBinding b = store.getComponent(ref, TownVillagerBinding.getComponentType());
            NPCEntity npc = store.getComponent(ref, NPCEntity.getComponentType());
            if (b == null || npc == null || npc.getRoleName() == null || npc.getRoleName().isBlank()) {
                return;
            }
            if (!town.getTownId().equals(b.getTownId())) {
                return;
            }
            if (TownVillagerBinding.isVisitorKind(b.getKind())) {
                return;
            }
            roleId = npc.getRoleName().trim();
            kind = b.getKind();
            jobPlotId = b.getJobPlotId();
        } else {
            for (ResidentNpcRecord r : town.getResidentNpcRecords()) {
                if (oldUuid.equals(r.getLastEntityUuid())) {
                    roleId = r.getNpcRoleId().trim();
                    kind = r.getKind();
                    jobPlotId = r.getJobPlotId();
                    break;
                }
            }
            if (roleId.isEmpty()) {
                if (town.getElderEntityUuid() != null && town.getElderEntityUuid().equals(oldUuid)) {
                    roleId = AetherhavenConstants.ELDER_NPC_ROLE_ID;
                    kind = TownVillagerBinding.KIND_ELDER;
                } else if (town.getInnkeeperEntityUuid() != null && town.getInnkeeperEntityUuid().equals(oldUuid)) {
                    roleId = AetherhavenConstants.INNKEEPER_NPC_ROLE_ID;
                    kind = TownVillagerBinding.KIND_INNKEEPER;
                }
            }
            if (roleId.isEmpty()) {
                return;
            }
        }
        map.put(
            oldUuid,
            new CapturedNpc(oldUuid, loaded, roleId, kind, jobPlotId, false, null, null, null, false, null, null, null, false)
        );
    }

    @Nullable
    private static UUID spawnResidentLikeNpc(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull CapturedNpc c,
        @Nonnull Vector3d pos,
        @Nullable PlotInstance innPlot
    ) {
        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            return null;
        }
        String role = c.npcRoleId.trim();
        if (role.isEmpty()) {
            return null;
        }
        var pair = npc.spawnNPC(store, role, null, pos, Rotation3f.ZERO);
        if (pair == null) {
            return null;
        }
        Ref<EntityStore> ref = pair.first();
        store.putComponent(ref, VillagerNeeds.getComponentType(), VillagerNeeds.full());
        String hex = town.getTownId().toString().replace("-", "");
        String suffix = hex.length() >= 8 ? hex.substring(0, 8) : hex;
        store.putComponent(ref, AetherhavenVillagerHandle.getComponentType(), new AetherhavenVillagerHandle("Villager_" + c.bindingKind + "_" + suffix));

        TownVillagerBinding binding;
        if (TownVillagerBinding.KIND_INNKEEPER.equals(c.bindingKind) && innPlot != null) {
            UUID pid = innPlot.getPlotId();
            binding = new TownVillagerBinding(town.getTownId(), TownVillagerBinding.KIND_INNKEEPER, pid, pid);
        } else if (c.jobPlotId != null) {
            binding = new TownVillagerBinding(town.getTownId(), c.bindingKind, c.jobPlotId, c.jobPlotId);
        } else {
            binding = new TownVillagerBinding(town.getTownId(), c.bindingKind, null);
        }
        store.putComponent(ref, TownVillagerBinding.getComponentType(), binding);
        World world = store.getExternalData().getWorld();
        NpcSpawnOriginUtil.attach(
            store,
            ref,
            "ADMIN_RESET",
            "roleId=" + role + ",kind=" + c.bindingKind + ",previousUuid=" + c.previousEntityUuid,
            world,
            pos
        );

        UUIDComponent nu = store.getComponent(ref, UUIDComponent.getComponentType());
        if (nu == null) {
            return null;
        }
        UUID newUuid = nu.getUuid();
        ResidentRegistryService.upsert(town, tm, role, c.bindingKind, c.jobPlotId, newUuid);
        if (TownVillagerBinding.KIND_ELDER.equals(c.bindingKind)) {
            town.setElderEntityUuid(newUuid);
        } else if (TownVillagerBinding.KIND_INNKEEPER.equals(c.bindingKind)) {
            town.setInnkeeperEntityUuid(newUuid);
        }
        tm.updateTown(town);
        return newUuid;
    }

    /**
     * Emergency staff tool: remove <em>every</em> loaded NPC in this world whose role name matches
     * {@code roleId}, with no town-binding / mod-component filter. Town save data (resident registry, inn
     * pool, elder/innkeeper UUIDs, etc.) is left untouched so {@code /ah villager respawn} / {@code reset}
     * can bring the tracked villager back.
     *
     * <p>Only entities currently in the entity store are removed (same limit as {@code /npc clean}). Unloaded
     * chunks may still hold copies — run again after those areas load.
     *
     * @return number of entities removed
     */
    public static int purgeAllLoadedNpcsByRole(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull String roleId
    ) {
        String wanted = roleId.trim();
        if (wanted.isEmpty()) {
            return 0;
        }
        List<Ref<EntityStore>> toRemove = new ArrayList<>();
        store.forEachChunk(NPCEntity.getComponentType(), (archetypeChunk, commandBuffer) -> {
            int n = archetypeChunk.size();
            for (int i = 0; i < n; i++) {
                Ref<EntityStore> npcRef = archetypeChunk.getReferenceTo(i);
                if (npcRef == null || !npcRef.isValid()) {
                    continue;
                }
                NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
                if (npc == null || npc.getRoleName() == null || npc.getRoleName().isBlank()) {
                    continue;
                }
                if (!wanted.equalsIgnoreCase(npc.getRoleName().trim())) {
                    continue;
                }
                toRemove.add(npcRef);
            }
        });
        int count = 0;
        for (Ref<EntityStore> r : toRemove) {
            if (r.isValid()) {
                store.removeEntity(r, RemoveReason.REMOVE);
                count++;
            }
        }
        if (count > 0) {
            LOGGER.atInfo().log(
                "Purge: removed %s loaded NPC(s) with role %s in world %s (town save data unchanged)",
                count,
                wanted,
                world.getName()
            );
        }
        return count;
    }
}
