package com.hexvane.aetherhaven.questboard;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.map.RaidQuestCompassCache;
import com.hexvane.aetherhaven.town.TownRecord;
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

    /** How an attempt to start a raid ended. Only {@link #STARTED} leaves anything in the world. */
    public enum RaidStart {
        STARTED,
        /** Nowhere awake to put the raiders. Nothing was spawned. */
        NO_GROUND,
        /** The raid cannot start yet and the player has already been told why. Nothing was spawned. */
        NOT_READY,
        /** Nothing was spawned, or what was spawned has been removed. */
        FAILED;

        public boolean started() {
            return this == STARTED;
        }
    }

    @Nonnull
    public static RaidStart startRaid(
        @Nonnull TownRecord town,
        @Nonnull QuestBoardSlotRecord slot,
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID acceptingPlayerUuid
    ) {
        if (!slot.isRaidQuest()) {
            return RaidStart.FAILED;
        }
        List<String> roster = slot.raidMobRoleIdsOrEmpty();
        if (roster.isEmpty()) {
            return RaidStart.FAILED;
        }

        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            return RaidStart.FAILED;
        }

        int ownerLevel = com.hexvane.aetherhaven.leveling.LevelingIntegration.ownerLevel(town, store);
        if (com.hexvane.aetherhaven.leveling.LevelingIntegration.isActive() && ownerLevel <= 0) {
            LOGGER.atWarning().log("Raid %s postponed: town owner's level is not known yet", slot.instanceIdOrEmpty());
            for (PlayerRef player : world.getPlayerRefs()) {
                if (acceptingPlayerUuid.equals(player.getUuid())) {
                    player.sendMessage(Message.raw(slot.isRescueQuest()
                        ? "The town owner's level is not available yet. Have the owner join, wait a few seconds, then accept the rescue again."
                        : "The town owner's level is not available yet. Have the owner join, wait a few seconds, then start the raid again."));
                    break;
                }
            }
            return RaidStart.NOT_READY;
        }

        Random rng = new Random(slot.getGenerationSeed() ^ acceptingPlayerUuid.getLeastSignificantBits());

        // A rescue's attackers gather around a camp outside town and march on it, not on the charter.
        if (slot.isRescueQuest()) {
            Vector3d campTarget = RescueCampService.start(town, slot, world, store, rng);
            if (campTarget == null) {
                return RaidStart.FAILED;
            }
            // The captors are placed when a player reaches the camp (RescueCampService.populate), not here: the
            // camp is far from whoever is standing at the quest board.
            slot.setRaidSpawnedEntityUuids(new ArrayList<>());
            slot.setRaidKillRequired(roster.size());
            slot.setRaidKillProgress(0);
            notifyRescueAccepted(store, acceptingPlayerUuid, slot);
            return RaidStart.STARTED;
        }

        String instanceId = slot.instanceIdOrEmpty();
        UUID townId = town.getTownId();
        RaidApproachDirection preferredDirection = slot.raidApproachDirectionEnum();

        // Every raider gets a place before the first one is spawned, so a raid with nowhere to stand leaves nothing
        // behind. The search only reads land that is already awake: it loads nothing.
        RaidSpawnTerrain terrain = WorldRaidSpawnTerrain.simulating(world);
        List<RaidSpawnPositionFinder.Result> plan = RaidSpawnPositionFinder.planWave(
            terrain,
            RaidSpawnPositionFinder.Site.of(town),
            preferredDirection,
            rng,
            roster.size()
        );
        if (plan.isEmpty()) {
            LOGGER.atWarning().log("Raid quest %s: no awake ground for the raiders on any side of town", instanceId);
            return RaidStart.NO_GROUND;
        }
        RaidSpawnPositionFinder.Result first = plan.get(0);
        if (first.tier() != RaidSpawnPositionFinder.Tier.OUTSKIRTS) {
            LOGGER.atInfo().log("Raid quest %s: no clear ground outside the border, using %s", instanceId, first.tier());
        }

        Vector3d charterMarchTarget = charterMarchTarget(world, town);
        RaidQuestCompassCache.removeForTown(world.getName(), townId);

        RaidWaveStart.Outcome outcome = RaidWaveStart.run(roster, plan, new RaidWaveStart.Spawner() {
            @Override
            public String spawn(int index, @Nonnull String roleId, @Nonnull RaidSpawnPositionFinder.Result spot) {
                return spawnRaider(npc, store, terrain, townId, instanceId, roleId, spot.position(), charterMarchTarget, ownerLevel);
            }

            @Override
            public void remove(@Nonnull String raiderId) {
                removeEntityByUuidString(store, raiderId);
            }
        });
        if (!outcome.started()) {
            if (outcome.error() != null) {
                LOGGER.atWarning().withCause(outcome.error()).log(
                    "Raid quest %s: spawning stopped part way; the raiders already placed were removed", instanceId);
            } else {
                LOGGER.atWarning().log("Raid quest %s: none of the %d raiders could be placed", instanceId, roster.size());
            }
            return RaidStart.FAILED;
        }

        RaidApproachDirection waveDirection = first.direction();
        if (waveDirection != preferredDirection) {
            slot.setRaidApproachDirection(waveDirection.id());
            LOGGER.atInfo().log(
                "Raid quest %s: switched approach from %s to %s after spawn search",
                instanceId,
                preferredDirection.id(),
                waveDirection.id()
            );
        }
        List<String> spawnedUuids = new ArrayList<>(outcome.raiders());
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
        return RaidStart.STARTED;
    }

    /**
     * Places one raider. Returns its id, or null when the raid cannot count it, in which case it is not left
     * standing in the world.
     */
    @Nullable
    private static String spawnRaider(
        @Nonnull NPCPlugin npc,
        @Nonnull Store<EntityStore> store,
        @Nonnull RaidSpawnTerrain terrain,
        @Nonnull UUID townId,
        @Nonnull String instanceId,
        @Nonnull String roleId,
        @Nonnull Vector3d ground,
        @Nonnull Vector3d marchTarget,
        int ownerLevel
    ) {
        String spawnRoleId = RaidQuestMarchRoles.spawnRoleFor(roleId);
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
            return null;
        }

        boolean flying = RaidQuestMarchRoles.isFlyingRosterRole(roleId);
        Vector3d pos = new Vector3d(ground);
        if (flying) {
            // The air above may belong to a part of the world that is asleep even when the ground is awake.
            double liftedY = ground.y + RaidQuestMarchRoles.FLY_RAID_SPAWN_LIFT;
            if (terrain.liveCell((int) Math.floor(ground.x), (int) Math.floor(liftedY), (int) Math.floor(ground.z))) {
                pos.y = liftedY;
            }
        }
        Vector3d spawnPos = new Vector3d(pos);
        long marchNowMs = RaidQuestMarchUtil.resolveNowMs(store);
        // The raider is tagged before it enters the world. If the game stores it instead of keeping it awake, it
        // still carries the tag and RaidQuestMarchSystem takes it out when it wakes.
        RaidQuestMobBinding binding = new RaidQuestMobBinding(townId, instanceId);
        if (flying) {
            binding.setMarchFlyCruiseY(spawnPos.y);
        }
        RaidQuestMarchUtil.bootstrapMarch(binding, spawnPos, marchTarget, marchNowMs);
        var pair =
            npc.spawnEntity(
                store,
                roleIndex,
                pos,
                Rotation3f.ZERO,
                null,
                (npcEntity, holder, st) -> holder.putComponent(RaidQuestMobBinding.getComponentType(), binding),
                (npcEntity, mobRef, st) ->
                    configureRaidMobMarch(npcEntity, mobRef, spawnPos, marchTarget, roleId, st)
            );
        if (pair == null) {
            LOGGER.atWarning().log("Raid quest %s: failed to spawn %s", instanceId, roleId);
            return null;
        }
        Ref<EntityStore> mobRef = pair.first();
        UUIDComponent mobUuid = mobRef.isValid() ? store.getComponent(mobRef, UUIDComponent.getComponentType()) : null;
        if (mobUuid == null) {
            // A raider the raid cannot name cannot be counted or cleaned up later, so it does not stay.
            LOGGER.atWarning().log("Raid quest %s: %s appeared without an id and was removed", instanceId, roleId);
            if (mobRef.isValid()) {
                store.removeEntity(mobRef, RemoveReason.REMOVE);
            }
            return null;
        }
        NPCEntity spawnedNpc = store.getComponent(mobRef, NPCEntity.getComponentType());
        if (spawnedNpc != null) {
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
        com.hexvane.aetherhaven.leveling.LevelingIntegration.scaleRaid(mobRef, store, ownerLevel);
        return mobUuid.getUuid().toString();
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
        if (slot.isRescueQuest()) {
            RescueCampService.stop(slot, townId, world, store, false);
        }
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

    static void removeEntityByUuidString(@Nonnull Store<EntityStore> store, @Nullable String uuidStr) {
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

    /**
     * Places one captor at a rescue camp. Returns the new entity's id, or null when the game refused it.
     */
    @Nullable
    static String spawnRescueCaptor(@Nonnull TownRecord town, @Nonnull QuestBoardSlotRecord slot, @Nonnull Store<EntityStore> store,
                                    @Nonnull String roleId, @Nonnull Vector3d position, @Nonnull Vector3d camp, int ownerLevel) {
        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            return null;
        }
        int roleIndex = npc.getIndex(RaidQuestMarchRoles.spawnRoleFor(roleId));
        if (roleIndex < 0) {
            roleIndex = npc.getIndex(roleId);
        }
        if (roleIndex < 0) {
            LOGGER.atWarning().log("Rescue %s: unknown NPC role %s", slot.instanceIdOrEmpty(), roleId);
            return null;
        }
        Vector3d spawnPos = new Vector3d(position);
        long nowMs = RaidQuestMarchUtil.resolveNowMs(store);
        var pair = npc.spawnEntity(store, roleIndex, position, Rotation3f.ZERO, null,
            (npcEntity, mobRef, st) -> configureRaidMobMarch(npcEntity, mobRef, spawnPos, camp, roleId, st));
        if (pair == null) {
            return null;
        }
        Ref<EntityStore> mobRef = pair.first();
        UUIDComponent mobUuid = store.getComponent(mobRef, UUIDComponent.getComponentType());
        if (mobUuid == null) {
            return null;
        }
        RaidQuestMobBinding binding = new RaidQuestMobBinding(town.getTownId(), slot.instanceIdOrEmpty());
        RaidQuestMarchUtil.bootstrapMarch(binding, spawnPos, camp, nowMs);
        store.putComponent(mobRef, RaidQuestMobBinding.getComponentType(), binding);
        com.hexvane.aetherhaven.leveling.LevelingIntegration.scaleRaid(mobRef, store, ownerLevel);
        // Taken off again a moment later by RescueCampService.populate.
        store.putComponent(mobRef, com.hypixel.hytale.server.core.modules.entity.component.Invulnerable.getComponentType(),
            com.hypixel.hytale.server.core.modules.entity.component.Invulnerable.INSTANCE);
        return mobUuid.getUuid().toString();
    }

    private static void notifyRescueAccepted(@Nonnull Store<EntityStore> store, @Nonnull UUID acceptingPlayerUuid, @Nonnull QuestBoardSlotRecord slot) {
        Ref<EntityStore> playerRef = store.getExternalData().getRefFromUUID(acceptingPlayerUuid);
        PlayerRef pr = playerRef == null || !playerRef.isValid() ? null : store.getComponent(playerRef, PlayerRef.getComponentType());
        if (pr == null) {
            return;
        }
        NotificationUtil.sendNotification(
            pr.getPacketHandler(),
            Message.translation("aetherhaven_ui_quest_board.aetherhaven.ui.questBoard.rescueStartedToast")
                .param("captive", RaidQuestBoardHandler.captiveName(slot))
                .param("direction", RaidQuestBoardHandler.raidApproachDirectionLabel(slot)),
            NotificationStyle.Default
        );
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
