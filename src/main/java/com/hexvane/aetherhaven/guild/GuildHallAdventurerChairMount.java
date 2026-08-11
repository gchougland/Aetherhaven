package com.hexvane.aetherhaven.guild;



import com.hexvane.aetherhaven.autonomy.SafeBlockMount;
import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.entity.TransformComponentUtil;

import com.hypixel.hytale.builtin.mounts.BlockMountAPI;

import com.hypixel.hytale.builtin.mounts.MountedComponent;

import com.hypixel.hytale.component.CommandBuffer;

import com.hypixel.hytale.protocol.MountController;

import com.hypixel.hytale.component.Ref;

import com.hypixel.hytale.component.Store;

import com.hypixel.hytale.logger.HytaleLogger;

import com.hypixel.hytale.math.vector.Rotation3f;

import com.hypixel.hytale.protocol.AnimationSlot;

import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;

import com.hypixel.hytale.server.core.universe.world.World;

import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.hypixel.hytale.server.npc.entities.NPCEntity;

import java.util.logging.Level;

import javax.annotation.Nonnull;

import javax.annotation.Nullable;

import org.joml.Vector3d;

import org.joml.Vector3i;



/** Mounts guild hall display adventurers onto a chair/stool block under their spawn marker when present. */

public final class GuildHallAdventurerChairMount {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();



    private GuildHallAdventurerChairMount() {}



    public static boolean hasSeatNearSpawn(@Nonnull Store<EntityStore> store, @Nonnull GuildHallDisplayAnchor anchor) {
        World world = store.getExternalData().getWorld();
        return VillagerBlockUtil.findGuildHallSeatBelowSpawn(world, anchor.getSpawnMarkerPosition()) != null;
    }



    /**

     * @return true when a block mount was applied

     */

    public static boolean tryMountChairBelowSpawn(

        @Nonnull Ref<EntityStore> npcRef,

        @Nonnull Store<EntityStore> store,

        @Nonnull CommandBuffer<EntityStore> commandBuffer,

        @Nonnull GuildHallDisplayAnchor anchor

    ) {

        if (SafeBlockMount.isMountedOrPending(store, commandBuffer, npcRef)) {
            return true;
        }

        World world = store.getExternalData().getWorld();

        Vector3i mountBlock = VillagerBlockUtil.findGuildHallSeatBelowSpawn(world, anchor.getSpawnMarkerPosition());

        if (mountBlock == null) {

            return false;

        }

        TransformComponent tc = commandBuffer.getComponent(npcRef, TransformComponent.getComponentType());
        if (tc == null) {
            tc = store.getComponent(npcRef, TransformComponent.getComponentType());
        }
        if (tc == null) {
            return false;
        }
        if (!anchor.isChairAlignedForMount()) {
            alignFeetForSeatMount(npcRef, commandBuffer, world, mountBlock);
            anchor.setChairAlignedForMount(true);
            return false;
        }
        try {
            Vector3d feet = tc.getPosition();
            Vector3d hitBlockCenter = new Vector3d(mountBlock.x + 0.5, mountBlock.y + 0.5, mountBlock.z + 0.5);
            Vector3d seatHit = seatWorldPosition(world, mountBlock);
            Vector3d feetPick = new Vector3d(feet.x, feet.y + 0.5, feet.z);
            Vector3d primaryHit = seatHit != null ? seatHit : feetPick;
            BlockMountAPI.BlockMountResult result =
                tryMountWithHits(store, npcRef, commandBuffer, mountBlock, feetPick, primaryHit, hitBlockCenter);

            if (!(result instanceof BlockMountAPI.Mounted)) {

                return false;

            }

            playSitAnimationOnce(npcRef, store, anchor);

            TransformComponent mountedTransform = commandBuffer.getComponent(npcRef, TransformComponent.getComponentType());
            if (mountedTransform == null) {
                mountedTransform = store.getComponent(npcRef, TransformComponent.getComponentType());
            }
            if (mountedTransform != null) {
                commandBuffer.putComponent(npcRef, TransformComponent.getComponentType(), mountedTransform);
            }

            syncAnchorAfterMount(npcRef, store, commandBuffer, anchor);

            return true;

        } catch (RuntimeException ex) {

            LOGGER.at(Level.FINE).withCause(ex).log("Could not mount guild hall adventurer on chair");

            return false;

        }

    }



    /** Last resort when {@link BlockMountAPI} fails but a real seat block exists under the spawn marker. */

    public static void applySeatPoseFallback(

        @Nonnull Ref<EntityStore> npcRef,

        @Nonnull Store<EntityStore> store,

        @Nonnull CommandBuffer<EntityStore> commandBuffer,

        @Nonnull GuildHallDisplayAnchor anchor

    ) {

        World world = store.getExternalData().getWorld();

        Vector3i mountBlock = VillagerBlockUtil.findGuildHallSeatBelowSpawn(world, anchor.getSpawnMarkerPosition());

        if (mountBlock == null) {
            return;
        }

        Vector3d seatPos = seatWorldPosition(world, mountBlock);
        if (seatPos == null) {
            seatPos = new Vector3d(mountBlock.x + 0.5, mountBlock.y + 1.0, mountBlock.z + 0.5);
        }

        TransformComponentUtil.replacePreservingChunk(
            npcRef,
            store,
            commandBuffer,
            seatPos,
            new Rotation3f(
                0.0F,
                VillagerBlockUtil.resolveMountBodyYawRadians(world, mountBlock, anchor.getYawRadians()),
                0.0F
            )
        );

        playSitAnimationOnce(npcRef, store, anchor);

        anchor.updatePosition(seatPos);

    }



    public static boolean isBlockMounted(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> npcRef) {

        return isBlockMounted(store, null, npcRef);

    }



    public static boolean isBlockMounted(

        @Nonnull Store<EntityStore> store,

        @Nullable CommandBuffer<EntityStore> commandBuffer,

        @Nonnull Ref<EntityStore> npcRef

    ) {

        MountedComponent mounted = commandBuffer != null

            ? commandBuffer.getComponent(npcRef, MountedComponent.getComponentType())

            : null;

        if (mounted == null) {

            mounted = store.getComponent(npcRef, MountedComponent.getComponentType());

        }

        return mounted != null && mounted.getControllerType() == MountController.BlockMount;

    }



    @Nullable

    private static Vector3d seatWorldPosition(@Nonnull World world, @Nonnull Vector3i mountBlock) {
        return VillagerBlockUtil.seatWorldPosition(world, mountBlock);
    }



    /** Re-applies Sit while seated; {@link com.hexvane.aetherhaven.AetherhavenConstants#NPC_STATE_GUILD_HALL_DISPLAY} uses standing idle. */
    public static void ensureSitVisuals(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull GuildHallDisplayAnchor anchor
    ) {
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc == null) {
            return;
        }
        npc.playAnimation(npcRef, AnimationSlot.Status, "Sit", store);
        anchor.setSitAnimationApplied(true);
    }

    private static void playSitAnimationOnce(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull GuildHallDisplayAnchor anchor
    ) {
        ensureSitVisuals(npcRef, store, anchor);
    }



    private static void alignFeetForSeatMount(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull World world,
        @Nonnull Vector3i mountBlock
    ) {
        TransformComponent tc = commandBuffer.getComponent(npcRef, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }
        Vector3d pos = seatWorldPosition(world, mountBlock);
        if (pos == null) {
            pos = new Vector3d(mountBlock.x + 0.5, mountBlock.y + 1.0, mountBlock.z + 0.5);
        }
        TransformComponentUtil.replacePreservingChunk(npcRef, commandBuffer, pos, tc.getRotation());
    }



    @Nonnull

    private static BlockMountAPI.BlockMountResult tryMountWithHits(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Vector3i mountBlock,
        @Nonnull Vector3d... hits
    ) {
        BlockMountAPI.BlockMountResult last = BlockMountAPI.DidNotMount.NO_MOUNT_POINT_FOUND;
        for (Vector3d hit : hits) {
            BlockMountAPI.BlockMountResult result =
                SafeBlockMount.mountOnBlock(store, npcRef, commandBuffer, mountBlock, hit);
            if (result instanceof BlockMountAPI.Mounted) {
                return result;
            }
            last = result;
        }
        return last;
    }



    private static void syncAnchorAfterMount(

        @Nonnull Ref<EntityStore> npcRef,

        @Nonnull Store<EntityStore> store,

        @Nonnull CommandBuffer<EntityStore> commandBuffer,

        @Nonnull GuildHallDisplayAnchor anchor

    ) {

        TransformComponent tc = commandBuffer.getComponent(npcRef, TransformComponent.getComponentType());

        if (tc == null) {

            tc = store.getComponent(npcRef, TransformComponent.getComponentType());

        }

        if (tc == null) {

            return;

        }

        anchor.updatePosition(tc.getPosition());

        commandBuffer.putComponent(npcRef, GuildHallDisplayAnchor.getComponentType(), anchor);

    }

}


