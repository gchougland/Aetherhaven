package com.hexvane.aetherhaven.questboard;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.map.RaidQuestCompassCache;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.TownTerritoryClaims;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Rotation3f;
import org.joml.Vector3d;
import com.hypixel.hytale.protocol.packets.interface_.NotificationStyle;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.util.NotificationUtil;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class RaidQuestSpawnService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private RaidQuestSpawnService() {}

    public static boolean startRaid(
        @Nonnull TownRecord town,
        @Nonnull QuestBoardSlotRecord slot,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID acceptingPlayerUuid
    ) {
        if (!slot.isRaidQuest()) {
            return false;
        }
        List<String> roster = slot.raidMobRoleIdsOrEmpty();
        if (roster.isEmpty()) {
            return false;
        }

        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            return false;
        }

        Random rng = new Random(slot.getGenerationSeed() ^ acceptingPlayerUuid.getLeastSignificantBits());

        Vector3d charterMarchTarget = charterMarchTarget(world, town);
        RaidQuestCompassCache.removeForTown(world.getName(), town.getTownId());

        List<String> spawnedUuids = new ArrayList<>();
        String instanceId = slot.instanceIdOrEmpty();
        UUID townId = town.getTownId();
        RaidApproachDirection preferredDirection = slot.raidApproachDirectionEnum();
        RaidApproachDirection waveDirection = null;

        for (int i = 0; i < roster.size(); i++) {
            String roleId = roster.get(i);
            String spawnRoleId = RaidQuestMarchRoles.spawnRoleFor(roleId);
            RaidSpawnPositionFinder.Result spawn = RaidSpawnPositionFinder.findForMob(
                world,
                town,
                preferredDirection,
                waveDirection,
                rng,
                i
            );
            if (spawn == null) {
                LOGGER.atWarning().log("Raid quest %s: no valid spawn position for %s", instanceId, roleId);
                continue;
            }
            if (waveDirection == null) {
                waveDirection = spawn.direction();
                if (waveDirection != preferredDirection) {
                    slot.setRaidApproachDirection(waveDirection.id());
                    LOGGER.atInfo().log(
                        "Raid quest %s: switched approach from %s to %s after spawn search",
                        instanceId,
                        preferredDirection.id(),
                        waveDirection.id()
                    );
                }
            }
            Vector3d pos = spawn.position();
            if (RaidQuestMarchRoles.isFlyingRosterRole(roleId)) {
                pos = new Vector3d(pos.x, pos.y + RaidQuestMarchRoles.FLY_RAID_SPAWN_LIFT, pos.z);
            }
            int roleIndex = npc.getIndex(spawnRoleId);
            if (roleIndex < 0) {
                LOGGER.atWarning().log(
                    "Raid quest %s: missing raid spawn role %s (from %s); using base role",
                    instanceId,
                    spawnRoleId,
                    roleId
                );
                roleIndex = npc.getIndex(roleId);
            }
            if (roleIndex < 0) {
                LOGGER.atWarning().log("Raid quest %s: unknown NPC role %s", instanceId, roleId);
                continue;
            }

            Vector3d marchTarget = charterMarchTarget;
            Vector3d spawnPos = new Vector3d(pos);
            long marchNowMs = RaidQuestMarchUtil.resolveNowMs(store);
            var pair =
                npc.spawnEntity(
                    store,
                    roleIndex,
                    pos,
                    Rotation3f.ZERO,
                    null,
                    (npcEntity, mobRef, st) ->
                        configureRaidMobMarch(npcEntity, mobRef, spawnPos, marchTarget, roleId, st)
                );
            if (pair == null) {
                LOGGER.atWarning().log("Raid quest %s: failed to spawn %s", instanceId, roleId);
                continue;
            }
            Ref<EntityStore> mobRef = pair.first();
            NPCEntity spawnedNpc = store.getComponent(mobRef, NPCEntity.getComponentType());
            UUIDComponent mobUuid = store.getComponent(mobRef, UUIDComponent.getComponentType());
            if (spawnedNpc != null && mobUuid != null) {
                RaidQuestMarchDebugLog.logSpawn(
                    AetherhavenPlugin.get(),
                    instanceId,
                    roleId,
                    spawnRoleId,
                    mobUuid.getUuid(),
                    spawnPos,
                    marchTarget,
                    spawnedNpc.getLeashPoint(),
                    spawnedNpc
                );
            }
            RaidQuestMobBinding binding = new RaidQuestMobBinding(townId, instanceId);
            if (RaidQuestMarchRoles.isFlyingRosterRole(roleId)) {
                binding.setMarchFlyCruiseY(spawnPos.y);
            }
            RaidQuestMarchUtil.bootstrapMarch(binding, spawnPos, marchTarget, marchNowMs);
            store.putComponent(mobRef, RaidQuestMobBinding.getComponentType(), binding);
            if (mobUuid != null) {
                spawnedUuids.add(mobUuid.getUuid().toString());
            }
        }

        if (spawnedUuids.isEmpty()) {
            cleanupRaid(slot, world, store, townId);
            return false;
        }

        slot.setRaidSpawnedEntityUuids(spawnedUuids);
        slot.setRaidKillRequired(spawnedUuids.size());
        slot.setRaidKillProgress(0);

        if (spawnedUuids.size() < roster.size()) {
            LOGGER.atWarning().log(
                "Raid quest %s spawned %d of %d mobs; kill count adjusted to spawned total",
                instanceId,
                spawnedUuids.size(),
                roster.size()
            );
        }

        notifyAcceptingPlayer(store, acceptingPlayerUuid, slot);
        return true;
    }

    public static void cleanupRaid(
        @Nonnull QuestBoardSlotRecord slot,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID townId
    ) {
        RaidQuestCompassCache.removeMobs(world.getName(), slot.raidSpawnedEntityUuidsOrEmpty());
        RaidQuestCompassCache.removeForTown(world.getName(), townId);
        for (String uuidStr : new ArrayList<>(slot.raidSpawnedEntityUuidsOrEmpty())) {
            removeEntityByUuidString(store, uuidStr);
        }
        slot.setRaidSpawnedEntityUuids(new ArrayList<>());
    }

    @Nonnull
    public static Vector3d charterMarchTargetFromTown(@Nonnull TownRecord town) {
        return new Vector3d(town.getCharterX() + 0.5, town.getCharterY(), town.getCharterZ() + 0.5);
    }

    /** Resolves a ground snapped charter target. Only call outside entity tick systems (quest accept / spawn). */
    @Nonnull
    public static Vector3d charterMarchTarget(@Nonnull World world, @Nonnull TownRecord town) {
        return com.hexvane.aetherhaven.autonomy.VillagerBlockUtil.snapNpcFeetToStand(
            world,
            charterMarchTargetFromTown(town)
        );
    }

    private static void configureRaidMobMarch(
        @Nonnull NPCEntity npcEntity,
        @Nonnull Ref<EntityStore> mobRef,
        @Nonnull Vector3d spawnPos,
        @Nonnull Vector3d charterTarget,
        @Nonnull String rosterRoleId,
        @Nonnull Store<EntityStore> store
    ) {
        RaidQuestMobBinding bootstrapBinding = new RaidQuestMobBinding(java.util.UUID.randomUUID(), "bootstrap");
        if (RaidQuestMarchRoles.isFlyingRosterRole(rosterRoleId)) {
            bootstrapBinding.setMarchFlyCruiseY(spawnPos.y);
        }
        Vector3d firstWaypoint = RaidQuestMarchUtil.computeNextWaypoint(spawnPos, charterTarget, bootstrapBinding);
        npcEntity.setLeashPoint(firstWaypoint);
        RaidQuestMarchUtil.applyMarchState(mobRef, npcEntity, store);
        RaidQuestMarchUtil.applyFlyingCruiseAltitude(npcEntity);
        store.putComponent(mobRef, NPCEntity.getComponentType(), npcEntity);
    }

    private static void removeEntityByUuidString(@Nonnull Store<EntityStore> store, @Nullable String uuidStr) {
        if (uuidStr == null || uuidStr.isBlank()) {
            return;
        }
        try {
            UUID uuid = UUID.fromString(uuidStr.trim());
            Ref<EntityStore> ref = store.getExternalData().getRefFromUUID(uuid);
            if (ref != null && ref.isValid()) {
                store.removeEntity(ref, RemoveReason.REMOVE);
            }
        } catch (IllegalArgumentException ignored) {
            // invalid uuid string
        }
    }

    private static void notifyAcceptingPlayer(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID acceptingPlayerUuid,
        @Nonnull QuestBoardSlotRecord slot
    ) {
        Ref<EntityStore> playerRef = store.getExternalData().getRefFromUUID(acceptingPlayerUuid);
        if (playerRef == null || !playerRef.isValid()) {
            return;
        }
        PlayerRef pr = store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr == null) {
            return;
        }
        NotificationUtil.sendNotification(
            pr.getPacketHandler(),
            Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.raidSpawnedToast")
                .param("direction", RaidQuestBoardHandler.raidApproachDirectionLabel(slot)),
            NotificationStyle.Default
        );
    }
}
