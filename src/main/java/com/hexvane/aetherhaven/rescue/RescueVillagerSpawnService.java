package com.hexvane.aetherhaven.rescue;

import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.AetherhavenVillagerHandle;
import com.hexvane.aetherhaven.villager.NpcSpawnOriginUtil;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Spawns one-shot rescue NPCs after trigger blocks are broken. */
public final class RescueVillagerSpawnService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private RescueVillagerSpawnService() {}

    public static boolean townHasActiveRescueNpc(
        @Nonnull Store<EntityStore> store,
        @Nonnull UUID townId,
        @Nonnull String rescueBindingKind
    ) {
        AtomicBoolean found = new AtomicBoolean(false);
        store.forEachEntityParallel(TownVillagerBinding.getComponentType(), (index, archetypeChunk, commandBuffer) -> {
            if (found.get()) {
                return;
            }
            TownVillagerBinding b = archetypeChunk.getComponent(index, TownVillagerBinding.getComponentType());
            if (b == null || !townId.equals(b.getTownId()) || !rescueBindingKind.equals(b.getKind())) {
                return;
            }
            Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
            if (ref != null && ref.isValid()) {
                found.set(true);
            }
        });
        return found.get();
    }

    /**
     * Spawns the rescue NPC once every trigger block in the column scan range is broken. Returns null when blocks
     * remain or spawn fails.
     */
    @Nullable
    public static UUID trySpawnAfterBlockBroken(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull TownRecord town,
        @Nonnull Vector3i brokenBlock,
        @Nullable UUID breakerPlayerUuid,
        @Nonnull RescueVillagerTrigger trigger
    ) {
        if (columnStillHasTriggerBlock(world, brokenBlock.x, brokenBlock.y, brokenBlock.z, trigger)) {
            return null;
        }
        if (townHasActiveRescueNpc(store, town.getTownId(), trigger.rescueBindingKind())) {
            return null;
        }
        Vector3d stand = resolveRescueStandPosition(world, brokenBlock.x, brokenBlock.y, brokenBlock.z);
        if (stand == null) {
            LOGGER.atWarning().log(
                "No stand position for %s rescue at %s,%s,%s",
                trigger.logLabel(),
                brokenBlock.x,
                brokenBlock.y,
                brokenBlock.z
            );
            return null;
        }
        Rotation3f rotation = rotationTowardPlayer(store, stand, breakerPlayerUuid);
        NPCPlugin npc = NPCPlugin.get();
        if (npc == null) {
            return null;
        }
        var pair = npc.spawnNPC(store, trigger.rescueNpcRoleId(), null, stand, rotation);
        if (pair == null) {
            LOGGER.atWarning().log("Failed to spawn %s rescue NPC for town %s", trigger.logLabel(), town.getTownId());
            return null;
        }
        Ref<EntityStore> ref = pair.first();
        store.putComponent(ref, VillagerNeeds.getComponentType(), VillagerNeeds.full());
        String handle = "Villager_" + trigger.rescueBindingKind() + "_" + shortHex(town.getTownId());
        store.putComponent(ref, AetherhavenVillagerHandle.getComponentType(), new AetherhavenVillagerHandle(handle));
        store.putComponent(
            ref,
            TownVillagerBinding.getComponentType(),
            new TownVillagerBinding(town.getTownId(), trigger.rescueBindingKind(), null)
        );
        NpcSpawnOriginUtil.attach(
            store,
            ref,
            "RESCUE_BLOCK",
            "kind=" + trigger.rescueBindingKind() + ",block=" + brokenBlock.x + "," + brokenBlock.y + "," + brokenBlock.z,
            world,
            stand
        );
        NPCEntity npcEntity = store.getComponent(ref, NPCEntity.getComponentType());
        if (npcEntity != null) {
            npcEntity.getLeashPoint().set(stand);
            npcEntity.setLeashHeading(rotation.yaw());
            npcEntity.setLeashPitch(rotation.pitch());
        }
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc != null) {
            tc.getPosition().set(stand);
            tc.getRotation().set(rotation.pitch(), rotation.yaw(), rotation.roll());
            store.putComponent(ref, TransformComponent.getComponentType(), tc);
        }
        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        UUID spawned = uuidComp != null ? uuidComp.getUuid() : null;
        LOGGER.atInfo().log(
            "Spawned %s rescue NPC %s for town %s at %s,%s,%s",
            trigger.logLabel(),
            spawned,
            town.getTownId(),
            stand.x,
            stand.y,
            stand.z
        );
        return spawned;
    }

    static boolean columnStillHasTriggerBlock(
        @Nonnull World world,
        int bx,
        int by,
        int bz,
        @Nonnull RescueVillagerTrigger trigger
    ) {
        for (int dy = trigger.columnScanMinDy(); dy <= trigger.columnScanMaxDy(); dy++) {
            if (isTriggerBlock(world, bx, by + dy, bz, trigger.triggerBlockTypeId())) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    static Vector3d resolveRescueStandPosition(@Nonnull World world, int bx, int brokenY, int bz) {
        int searchTop = Math.min(319, brokenY + 3);
        int feetY = VillagerBlockUtil.findStandY(world, bx, bz, searchTop);
        if (feetY == Integer.MIN_VALUE) {
            feetY = Math.min(319, brokenY + 2);
            if (!VillagerBlockUtil.isNpcStandColumn(world, bx, feetY, bz)) {
                return null;
            }
        }
        return new Vector3d(bx + 0.5, feetY, bz + 0.5);
    }

    @Nonnull
    private static Rotation3f rotationTowardPlayer(
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3d stand,
        @Nullable UUID breakerPlayerUuid
    ) {
        if (breakerPlayerUuid == null) {
            return new Rotation3f();
        }
        Ref<EntityStore> playerRef = store.getExternalData().getRefFromUUID(breakerPlayerUuid);
        if (playerRef == null || !playerRef.isValid()) {
            return new Rotation3f();
        }
        TransformComponent playerTc = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (playerTc == null) {
            return new Rotation3f();
        }
        Vector3d playerPos = playerTc.getPosition();
        double dx = playerPos.x - stand.x;
        double dz = playerPos.z - stand.z;
        if (Math.abs(dx) < 1.0e-4 && Math.abs(dz) < 1.0e-4) {
            return new Rotation3f();
        }
        float yaw = (float) (Math.atan2(dx, dz) + Math.PI);
        return new Rotation3f(0f, yaw, 0f);
    }

    private static boolean isTriggerBlock(@Nonnull World world, int x, int y, int z, @Nonnull String blockTypeId) {
        if (y < 0 || y >= 320) {
            return false;
        }
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return false;
        }
        BlockType bt = BlockType.getAssetMap().getAsset(chunk.getBlock(x, y, z));
        return bt != null && blockTypeId.equals(bt.getId());
    }

    @Nonnull
    private static String shortHex(@Nonnull UUID townId) {
        String s = townId.toString().replace("-", "");
        return s.substring(0, Math.min(8, s.length()));
    }
}
