package com.hexvane.aetherhaven.town;

import com.hypixel.hytale.math.vector.Rotation3f;

import com.hypixel.hytale.math.vector.Vector3fUtil;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.entity.EntityPresenceUtil;
import com.hexvane.aetherhaven.entity.EntityPresenceUtil.EntityPresence;
import com.hexvane.aetherhaven.autonomy.VillagerAutonomyTravelKick;
import com.hexvane.aetherhaven.reputation.VillagerReputationService;
import com.hexvane.aetherhaven.schedule.VillagerScheduleService;
import com.hexvane.aetherhaven.villager.AetherhavenVillagerHandle;
import com.hexvane.aetherhaven.villager.NpcSpawnOriginUtil;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import org.joml.Vector3d;
import org.joml.Vector3f;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class VillagerRevivalService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private VillagerRevivalService() {}

    /**
     * Spawns a new NPC for the given registry row near {@code spawnPos}, migrates reputation, updates town UUIDs.
     *
     * @return true if revival succeeded
     */
    public static boolean reviveResident(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager tm,
        @Nonnull Store<EntityStore> store,
        @Nonnull ResidentNpcRecord record,
        @Nonnull Vector3d spawnPos
    ) {
        UUID oldUuid = record.getLastEntityUuid();
        EntityPresence presence = EntityPresenceUtil.resolve(store, oldUuid);
        if (EntityPresenceUtil.isLoadedLive(presence)) {
            return false;
        }
        String roleId = record.getNpcRoleId().trim();
        if (roleId.isEmpty()) {
            return false;
        }
        if (!ResidentRegistryService.isGaiaRevivalEligible(record.getKind(), roleId)) {
            return false;
        }
        if (ResidentRegistryService.hasLiveTownRevivalNpcForRole(store, town, record)) {
            LOGGER.atInfo()
                .log(
                    "Revival blocked: live town NPC still present for role %s in town %s",
                    roleId,
                    town.getTownId()
                );
            TownResidentReconcileService.syncRegistryToLiveEntityIfNeeded(town, tm, store, record);
            return false;
        }
        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            return false;
        }
        int roleIndex = npc.getIndex(roleId);
        if (roleIndex < 0) {
            LOGGER.atWarning().log("Failed to revive NPC: role id not registered (getIndex < 0): %s for town %s", roleId, town.getTownId());
            return false;
        }
        var pair = npc.spawnNPC(store, roleId, null, spawnPos, Rotation3f.ZERO);
        if (pair == null) {
            LOGGER.atWarning()
                .log(
                    "Failed to revive NPC role %s for town %s (spawnNPC returned null; see NPCPlugin/RoleBuilder logs for this role, e.g. model asset missing or failed spawn)",
                    roleId,
                    town.getTownId()
                );
            return false;
        }
        Ref<EntityStore> ref = pair.first();
        store.putComponent(ref, VillagerNeeds.getComponentType(), VillagerNeeds.full());
        String kind = record.getKind();
        String hex = town.getTownId().toString().replace("-", "");
        String suffix = hex.length() >= 8 ? hex.substring(0, 8) : hex;
        store.putComponent(ref, AetherhavenVillagerHandle.getComponentType(), new AetherhavenVillagerHandle("Villager_" + kind + "_" + suffix));
        UUID job = record.getJobPlotId();
        TownVillagerBinding binding;
        if (job != null) {
            binding = new TownVillagerBinding(town.getTownId(), kind, job, job);
        } else {
            binding = new TownVillagerBinding(town.getTownId(), kind, null);
        }
        store.putComponent(ref, TownVillagerBinding.getComponentType(), binding);
        com.hexvane.aetherhaven.villagercosmetic.VillagerCosmeticAppearanceService.applySavedCosmetics(ref, store, town);
        String revivalSource = record.isPendingDawnRevival() ? "DAWN_REVIVAL" : "GAIA_REVIVAL";
        NpcSpawnOriginUtil.attach(
            store,
            ref,
            revivalSource,
            "roleId=" + roleId + ",kind=" + kind + ",oldUuid=" + oldUuid,
            world,
            spawnPos
        );
        UUIDComponent nu = store.getComponent(ref, UUIDComponent.getComponentType());
        if (nu == null) {
            return false;
        }
        UUID newUuid = nu.getUuid();
        VillagerReputationService.migrateVillagerEntityUuid(town, tm, oldUuid, newUuid);
        ResidentRegistryService.replaceEntityUuidEverywhere(town, tm, oldUuid, newUuid);
        record.setPendingDawnRevival(false);
        tm.updateTown(town);
        TownResidentReconcileService.reconcileTownOnWorldThread(world, plugin, town);
        world.execute(
            () -> {
                VillagerScheduleService.applyForWorld(world, store, plugin, true);
                VillagerAutonomyTravelKick.kickTravelToSchedulePoi(plugin, world, store, newUuid, false);
            }
        );
        LOGGER.atInfo().log("Revived villager role %s at %s,%s,%s", roleId, spawnPos.x, spawnPos.y, spawnPos.z);
        return true;
    }

    /** @return null if revival is allowed, otherwise a short English message for the player */
    @Nullable
    public static String validateCanRevive(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull ResidentNpcRecord record
    ) {
        if (!ResidentRegistryService.isGaiaRevivalEligible(record.getKind(), record.getNpcRoleId())) {
            return "That villager cannot be revived at the Gaia statue.";
        }
        UUID oldUuid = record.getLastEntityUuid();
        EntityPresence presence = EntityPresenceUtil.resolve(store, oldUuid);
        if (EntityPresenceUtil.isLoadedLive(presence)) {
            return "That villager is already present in the world.";
        }
        return null;
    }

    /**
     * Package-visible for tests — Gaia may revive when the saved uuid is merely unloaded; duplicates are purged on
     * load / reconcile instead.
     */
    static boolean appliesUnloadedRevivalGuard(@Nonnull ResidentNpcRecord record) {
        return false;
    }

    /**
     * Package-visible for tests — only blocks when the saved uuid is already loaded live (unloaded copies are allowed).
     */
    static boolean isRevivalBlockedForUnloadedEntity(
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull ResidentNpcRecord record
    ) {
        return EntityPresenceUtil.isLoadedLive(EntityPresenceUtil.resolve(store, record.getLastEntityUuid()));
    }
}
