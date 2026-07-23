package com.hexvane.aetherhaven.questboard;

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

        for (int i = 0; i < roster.size(); i++) {
            String roleId = roster.get(i);
            String spawnRoleId = RaidQuestMarchRoles.spawnRoleFor(roleId);
            RaidApproachDirection direction = slot.raidApproachDirectionEnum();
            Vector3d pos = computeOutskirtsSpawnPosition(world, town, direction, rng, i);
            if (pos == null) {
                pos = computeFallbackSpawnPosition(world, town, direction, rng, i);
            }
            if (pos == null) {
                LOGGER.atWarning().log("Raid quest %s: no valid spawn position for %s", instanceId, roleId);
                continue;
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
                        configureRaidMobMarch(npcEntity, mobRef, spawnPos, marchTarget, st)
                );
            if (pair == null) {
                LOGGER.atWarning().log("Raid quest %s: failed to spawn %s", instanceId, roleId);
                continue;
            }
            Ref<EntityStore> mobRef = pair.first();
            RaidQuestMobBinding binding = new RaidQuestMobBinding(townId, instanceId);
            RaidQuestMarchUtil.bootstrapMarch(binding, spawnPos, marchTarget, marchNowMs);
            store.putComponent(mobRef, RaidQuestMobBinding.getComponentType(), binding);
            UUIDComponent mobUuid = store.getComponent(mobRef, UUIDComponent.getComponentType());
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
    public static Vector3d charterMarchTarget(@Nonnull World world, @Nonnull TownRecord town) {
        int charterX = town.getCharterX();
        int charterZ = town.getCharterZ();
        Vector3d pos = RaidSpawnGroundUtil.findSpawnPosition(world, charterX, charterZ, town.getCharterY());
        if (pos != null) {
            return pos;
        }
        return new Vector3d(charterX + 0.5, town.getCharterY(), charterZ + 0.5);
    }

    private static void configureRaidMobMarch(
        @Nonnull NPCEntity npcEntity,
        @Nonnull Ref<EntityStore> mobRef,
        @Nonnull Vector3d spawnPos,
        @Nonnull Vector3d charterTarget,
        @Nonnull Store<EntityStore> store
    ) {
        Vector3d firstWaypoint = RaidQuestMarchUtil.computeNextWaypoint(spawnPos, charterTarget);
        npcEntity.setLeashPoint(firstWaypoint);
        RaidQuestMarchUtil.applyMarchState(mobRef, npcEntity, store);
        store.putComponent(mobRef, NPCEntity.getComponentType(), npcEntity);
    }

    @Nullable
    private static Vector3d computeOutskirtsSpawnPosition(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull RaidApproachDirection direction,
        @Nonnull Random rng,
        int mobIndex
    ) {
        int cx = town.getCharterX();
        int cz = town.getCharterZ();
        int charterY = town.getCharterY();
        TownTerritoryClaims.migrateIfNeeded(town);
        int edgeBlocks = TownTerritoryClaims.maxCharterToClaimEdgeBlocks(town);
        int baseDist = edgeBlocks + 16 + 8 + rng.nextInt(17) + mobIndex * 3;

        for (int attempt = 0; attempt < 48; attempt++) {
            int lateral = (attempt % 17) * 8 - 64 + rng.nextInt(5);
            int along = baseDist - (attempt / 17) * 6;
            if (along < 16) {
                along = 16;
            }
            int bx = cx + direction.axisX() * along;
            int bz = cz + direction.axisZ() * along;
            if (direction.axisX() != 0) {
                bz += lateral;
            } else {
                bx += lateral;
            }
            Vector3d pos = RaidSpawnGroundUtil.findSpawnPosition(world, bx, bz, charterY);
            if (pos != null) {
                return pos;
            }
        }
        return null;
    }

    @Nullable
    private static Vector3d computeFallbackSpawnPosition(
        @Nonnull World world,
        @Nonnull TownRecord town,
        @Nonnull RaidApproachDirection direction,
        @Nonnull Random rng,
        int mobIndex
    ) {
        int cx = town.getCharterX();
        int cz = town.getCharterZ();
        int charterY = town.getCharterY();
        TownTerritoryClaims.migrateIfNeeded(town);
        int edgeBlocks = TownTerritoryClaims.maxCharterToClaimEdgeBlocks(town);
        int baseDist = edgeBlocks + 16 + 12 + mobIndex * 4;

        for (int alongStep = 0; alongStep < 10; alongStep++) {
            int along = baseDist - alongStep * 8;
            if (along < 12) {
                break;
            }
            for (int lateral = -80; lateral <= 80; lateral += 4) {
                int bx = cx + direction.axisX() * along;
                int bz = cz + direction.axisZ() * along;
                if (direction.axisX() != 0) {
                    bz += lateral;
                } else {
                    bx += lateral;
                }
                Vector3d pos = RaidSpawnGroundUtil.findSpawnPosition(world, bx, bz, charterY);
                if (pos != null) {
                    return pos;
                }
            }
        }

        for (int ring = 0; ring < 24; ring++) {
            int dist = baseDist + ring * 2;
            for (RaidApproachDirection dir : RaidApproachDirection.values()) {
                if (dir != direction && rng.nextInt(3) != 0) {
                    continue;
                }
                int bx = cx + dir.axisX() * dist;
                int bz = cz + dir.axisZ() * dist;
                Vector3d pos = RaidSpawnGroundUtil.findSpawnPosition(world, bx, bz, charterY);
                if (pos != null) {
                    return pos;
                }
            }
        }
        return null;
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
