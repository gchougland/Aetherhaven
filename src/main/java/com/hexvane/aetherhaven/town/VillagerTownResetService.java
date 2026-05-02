package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomyTravelKick;
import com.hexvane.aetherhaven.inn.InnPoolService;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.schedule.VillagerScheduleService;
import com.hexvane.aetherhaven.villager.AetherhavenVillagerHandle;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
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
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Debug/admin: remove all town-tracked villager NPCs and respawn them near a position while preserving town quest state,
 * reputation (entity-UUID keys migrated), gift logs (role-keyed), and inn pool rules. Missing or unloaded entities are
 * still respawned from persisted town data; loaded entities are removed before respawn.
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
        boolean visitor
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
        InnPoolService.repairInnPoolForTown(world, plugin, town, tm, store);
        LinkedHashMap<UUID, CapturedNpc> captured = captureNpcs(town, store, plugin);
        if (captured.isEmpty()) {
            return "No tracked villager NPCs found for this town.";
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

        PlotInstance innPlot = town.findCompletePlotWithConstruction(AetherhavenConstants.CONSTRUCTION_PLOT_INN);
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
                map.put(old, new CapturedNpc(old, true, role, b.getKind(), b.getJobPlotId(), true));
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
            map.put(old, new CapturedNpc(old, false, roleId, kind, null, true));
        }

        return map;
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
        map.put(oldUuid, new CapturedNpc(oldUuid, loaded, roleId, kind, jobPlotId, false));
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
        var pair = npc.spawnNPC(store, role, null, pos, Vector3f.ZERO);
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
}
