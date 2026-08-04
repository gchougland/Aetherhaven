package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.entity.EntityRotationUtil;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.entity.TransformComponentUtil;
import com.hexvane.aetherhaven.equipment.VillagerEquipmentService;
import com.hexvane.aetherhaven.npc.NpcAnimationPlayback;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiInteractionKind;
import com.hexvane.aetherhaven.shopspot.ShopSpotBrowseVisuals;
import com.hypixel.hytale.builtin.mounts.BlockMountAPI;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import org.joml.Vector3d;
import org.joml.Vector3i;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.ModelComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * POI-facing yaw plus {@link AnimationSlot#Status} playback for autonomy {@code USE}. Clearing Status when
 * returning to {@link com.hexvane.aetherhaven.AetherhavenConstants#NPC_STATE_AUTONOMY_POI} Idle is handled by role
 * {@code StateTransitions}, not here.
 */
public final class PoiAutonomyVisuals {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private PoiAutonomyVisuals() {}

    public static void beginPoiUse(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PoiEntry poi
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        boolean equippedHeldItems = false;
        if (plugin != null) {
            String poiProfile = poi.getEquipmentProfileId();
            if (poiProfile != null) {
                VillagerEquipmentService.applyProfile(
                    npcRef,
                    store,
                    commandBuffer,
                    plugin.getEquipmentProfileCatalog(),
                    poiProfile
                );
                equippedHeldItems = true;
            } else if (poi.getInteractionKind() == PoiInteractionKind.WORK_SURFACE
                || poi.getTags().contains("WORK")) {
                NPCEntity roleNpc = store.getComponent(npcRef, NPCEntity.getComponentType());
                if (roleNpc != null && roleNpc.getRoleName() != null) {
                    var villagerDef = plugin.getVillagerDefinitionCatalog().byNpcRoleId(roleNpc.getRoleName());
                    if (villagerDef != null) {
                        String workProfile = villagerDef.getWorkEquipmentProfileId();
                        if (workProfile != null) {
                            VillagerEquipmentService.applyProfile(
                                npcRef,
                                store,
                                commandBuffer,
                                plugin.getEquipmentProfileCatalog(),
                                workProfile
                            );
                            equippedHeldItems = true;
                        }
                    }
                }
            }
        }
        // Leisure / sleep / shop: never keep watering cans, picks, etc. from a prior work USE.
        if (!equippedHeldItems) {
            VillagerEquipmentService.clearHotbar(npcRef, store, commandBuffer);
        }
        Set<String> tags = poi.getTags();
        World world = store.getExternalData().getWorld();
        if (isEatPoi(tags)) {
            beginEatPoiUse(npcRef, store, commandBuffer, poi, world);
            return;
        }
        VillagerBlockUtil.FurnitureMountKind furniture =
            VillagerBlockUtil.furnitureMountKind(world, poi.getX(), poi.getY(), poi.getZ());
        // Chairs always seat the NPC, even for WORK / desk POIs (mountOnUse only defaults true for SIT/SLEEP).
        boolean shouldMount =
            furniture != VillagerBlockUtil.FurnitureMountKind.NONE
                && (poi.isMountOnUse() || furniture == VillagerBlockUtil.FurnitureMountKind.SEAT);
        if (shouldMount) {
            if (tryMountBlockPoi(npcRef, store, commandBuffer, poi)) {
                playMountedFurnitureAnim(npcRef, store, commandBuffer, furniture);
                return;
            }
            if (furniture == VillagerBlockUtil.FurnitureMountKind.SEAT) {
                // Failed to attach to a seat — do not play a standing Sit beside the furniture.
                faceTowardBlock(npcRef, store, commandBuffer, poi);
                return;
            }
            if (furniture == VillagerBlockUtil.FurnitureMountKind.BED) {
                TransformComponent tcSleep = store.getComponent(npcRef, TransformComponent.getComponentType());
                if (tcSleep != null
                    && (poi.hasInteractionTarget()
                        || VillagerBlockUtil.canNpcMountBlockPoi(
                            world,
                            tcSleep.getPosition().x,
                            tcSleep.getPosition().y,
                            tcSleep.getPosition().z,
                            poi.getX(),
                            poi.getY(),
                            poi.getZ()
                        ))) {
                    sleepPoiFallbackPose(npcRef, store, commandBuffer, poi);
                    playMountedFurnitureAnim(npcRef, store, commandBuffer, furniture);
                    return;
                }
            }
        }
        faceTowardBlock(npcRef, store, commandBuffer, poi);
        if (tags.contains("SHOP") || tags.contains(AetherhavenConstants.POI_TAG_QUEST_BOARD)) {
            ShopSpotBrowseVisuals.beginPonder(npcRef, store, commandBuffer);
            return;
        }
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc == null) {
            return;
        }
        String anim = pickNonFurnitureAnimationId(store, npcRef, poi.getInteractionKind());
        if (anim != null) {
            NpcAnimationPlayback.play(npcRef, npc, AnimationSlot.Status, anim, commandBuffer);
        }
    }

    /**
     * While holding a need-fill USE at a seat/bed POI, (re)attach block mount when the POI sits on mountable furniture.
     * Returns true when the NPC is block-mounted afterward.
     */
    public static boolean ensureMountedForPoi(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PoiEntry poi
    ) {
        if (VillagerAutonomySystem.isNpcBlockMounted(store, commandBuffer, npcRef)) {
            return true;
        }
        Set<String> tags = poi.getTags();
        World world = store.getExternalData().getWorld();
        if (isEatPoi(tags)) {
            VillagerBlockUtil.FurnitureMountKind furniture =
                VillagerBlockUtil.furnitureMountKind(world, poi.getX(), poi.getY(), poi.getZ());
            if (furniture == VillagerBlockUtil.FurnitureMountKind.SEAT && tryMountBlockPoi(npcRef, store, commandBuffer, poi)) {
                playMountedFurnitureAnim(npcRef, store, commandBuffer, furniture);
                return true;
            }
            return false;
        }
        VillagerBlockUtil.FurnitureMountKind furniture =
            VillagerBlockUtil.furnitureMountKind(world, poi.getX(), poi.getY(), poi.getZ());
        boolean shouldMount =
            furniture != VillagerBlockUtil.FurnitureMountKind.NONE
                && (poi.isMountOnUse() || furniture == VillagerBlockUtil.FurnitureMountKind.SEAT);
        if (!shouldMount) {
            return false;
        }
        if (tryMountBlockPoi(npcRef, store, commandBuffer, poi)) {
            playMountedFurnitureAnim(npcRef, store, commandBuffer, furniture);
            return true;
        }
        if (furniture == VillagerBlockUtil.FurnitureMountKind.BED) {
            TransformComponent tcSleep = store.getComponent(npcRef, TransformComponent.getComponentType());
            if (tcSleep != null
                && (poi.hasInteractionTarget()
                    || VillagerBlockUtil.canNpcMountBlockPoi(
                        world,
                        tcSleep.getPosition().x,
                        tcSleep.getPosition().y,
                        tcSleep.getPosition().z,
                        poi.getX(),
                        poi.getY(),
                        poi.getZ()
                    ))) {
                sleepPoiFallbackPose(npcRef, store, commandBuffer, poi);
                playMountedFurnitureAnim(npcRef, store, commandBuffer, furniture);
                return false;
            }
        }
        return false;
    }

    /** Stop item consume / walk overlays after POI USE. {@code commandBuffer} may be null on the world thread. */
    public static void cleanupAfterPoiUse(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PoiEntry poi
    ) {
        cleanupAfterPoiUse(npcRef, store, commandBuffer, poi, true);
    }

    public static void cleanupAfterPoiUse(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PoiEntry poi,
        boolean restoreEquipmentAfterEat
    ) {
        if (restoreEquipmentAfterEat) {
            finishPoiUseCleanup(npcRef, store, commandBuffer, poi, true);
        } else {
            abortInterruptedPoiUse(npcRef, store, commandBuffer, poi);
        }
    }

    /**
     * Immediately ends any in-progress POI USE (sit, sleep, eat, shop browse, work bench, etc.) when schedule, workplace
     * assignment, or building completion changes what the villager should be doing.
     */
    public static void abortInterruptedPoiUse(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nullable PoiEntry poi
    ) {
        finishPoiUseCleanup(npcRef, store, commandBuffer, poi, false);
    }

    /**
     * Clears POI USE visuals when POI metadata is gone but the NPC is still mid-activity (e.g. ephemeral feast POI removed).
     */
    public static void forceAbortUseVisuals(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        abortInterruptedPoiUse(npcRef, store, commandBuffer, null);
    }

    private static void finishPoiUseCleanup(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nullable PoiEntry poi,
        boolean restoreEquipmentAfterEat
    ) {
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        BlockMountRelease.release(npcRef, store, commandBuffer);
        if (poi != null && isEatPoi(poi.getTags())) {
            if (restoreEquipmentAfterEat) {
                tryRestoreHeldEquipmentAfterCampfireEat(npcRef, store, commandBuffer, poi);
            } else {
                tryClearCampfireHeldFood(npcRef, store, commandBuffer);
            }
        } else if (!restoreEquipmentAfterEat) {
            tryClearCampfireHeldFood(npcRef, store, commandBuffer);
        }
        // Leaving a work station: drop tools so park / home / leisure do not keep the watering can, etc.
        if (poi != null && isWorkTaggedPoi(poi)) {
            VillagerEquipmentService.clearHotbar(npcRef, store, commandBuffer);
        }
        stopAllUseOverlayAnimations(npcRef, store, commandBuffer, npc);
    }

    private static boolean isWorkTaggedPoi(@Nonnull PoiEntry poi) {
        return poi.getInteractionKind() == PoiInteractionKind.WORK_SURFACE || poi.getTags().contains("WORK");
    }

    /** Clears sit / sleep / eat / shop / emote overlays regardless of POI interaction kind. */
    public static void stopAllUseOverlayAnimations(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nullable NPCEntity npc
    ) {
        if (npc == null) {
            npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        }
        if (commandBuffer != null) {
            stopCampfireConsumeVisuals(npcRef, commandBuffer, npc);
            NpcAnimationPlayback.stop(npcRef, AnimationSlot.Movement, commandBuffer);
            ShopSpotBrowseVisuals.endPonder(npcRef, store, commandBuffer);
            if (npc != null) {
                NpcAnimationPlayback.clearOverlaySlots(npcRef, npc, commandBuffer);
                commandBuffer.putComponent(npcRef, NPCEntity.getComponentType(), npc);
            }
        } else {
            stopCampfireConsumeVisuals(npcRef, store, npc);
            AnimationUtils.stopAnimation(npcRef, AnimationSlot.Movement, store);
            ShopSpotBrowseVisuals.endPonder(npcRef, store);
            if (npc != null) {
                npc.playAnimation(npcRef, AnimationSlot.Status, null, store);
                npc.playAnimation(npcRef, AnimationSlot.Action, null, store);
                npc.playAnimation(npcRef, AnimationSlot.Emote, null, store);
                store.putComponent(npcRef, NPCEntity.getComponentType(), npc);
            }
        }
    }

    private static void tryEquipCampfireHeldFood(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        InventoryComponent.Hotbar hb = store.getComponent(npcRef, InventoryComponent.Hotbar.getComponentType());
        if (hb == null) {
            return;
        }
        try {
            byte slot = hb.getActiveSlot();
            if (slot < 0 || slot >= hb.getInventory().getCapacity()) {
                slot = 0;
            }
            hb.getInventory().setItemStackForSlot((short) slot, new ItemStack(AetherhavenConstants.CAMPFIRE_EAT_ITEM_ID, 1));
            VillagerEquipmentService.markHotbarEquipmentDirty(hb, slot, npcRef, commandBuffer);
            commandBuffer.putComponent(npcRef, InventoryComponent.Hotbar.getComponentType(), hb);
        } catch (RuntimeException ex) {
            LOGGER.at(Level.FINE).withCause(ex).log("Could not equip campfire display item on NPC hotbar");
        }
    }

    private static void tryRestoreHeldEquipmentAfterCampfireEat(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PoiEntry poi
    ) {
        // Only re-equip if the eat POI itself defines a profile — never fall back to the role's work tools
        // (that left farmers holding watering cans after lunch at the park).
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        String profileId = poi.getEquipmentProfileId();
        if (plugin != null && profileId != null) {
            VillagerEquipmentService.applyProfile(
                npcRef,
                store,
                commandBuffer,
                plugin.getEquipmentProfileCatalog(),
                profileId
            );
            return;
        }
        tryClearCampfireHeldFood(npcRef, store, commandBuffer);
    }

    private static void tryClearCampfireHeldFood(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        InventoryComponent.Hotbar hb = store.getComponent(npcRef, InventoryComponent.Hotbar.getComponentType());
        if (hb == null) {
            return;
        }
        try {
            byte slot = hb.getActiveSlot();
            if (slot < 0 || slot >= hb.getInventory().getCapacity()) {
                slot = 0;
            }
            ItemStack active = hb.getActiveItem();
            if (active != null && AetherhavenConstants.CAMPFIRE_EAT_ITEM_ID.equals(active.getItemId())) {
                hb.getInventory().removeItemStackFromSlot((short) slot);
                VillagerEquipmentService.markHotbarEquipmentDirty(hb, slot, npcRef, commandBuffer);
            }
            commandBuffer.putComponent(npcRef, InventoryComponent.Hotbar.getComponentType(), hb);
        } catch (RuntimeException ex) {
            LOGGER.at(Level.FINE).withCause(ex).log("Could not clear campfire display item from NPC hotbar");
        }
    }

    private static boolean isEatPoi(@Nonnull Set<String> tags) {
        return tags.contains("EAT") || tags.contains(AetherhavenConstants.POI_TAG_FEAST);
    }

    /**
     * Any eat / feast POI: held food + Consume. If the POI block is a chair/seat, mount then Sit (do not Seek).
     */
    private static void beginEatPoiUse(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PoiEntry poi,
        @Nonnull World world
    ) {
        VillagerBlockUtil.FurnitureMountKind furniture =
            VillagerBlockUtil.furnitureMountKind(world, poi.getX(), poi.getY(), poi.getZ());
        if (furniture == VillagerBlockUtil.FurnitureMountKind.SEAT && tryMountBlockPoi(npcRef, store, commandBuffer, poi)) {
            playMountedFurnitureAnim(npcRef, store, commandBuffer, furniture);
        } else {
            faceTowardBlock(npcRef, store, commandBuffer, poi);
        }
        tryEquipCampfireHeldFood(npcRef, store, commandBuffer);
        playCampfireConsumeAnim(npcRef, commandBuffer);
    }

    private static void playMountedFurnitureAnim(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull VillagerBlockUtil.FurnitureMountKind furniture
    ) {
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc == null) {
            return;
        }
        String mountAnim = pickFurnitureAnimationId(store, npcRef, furniture);
        if (mountAnim != null) {
            NpcAnimationPlayback.play(npcRef, npc, AnimationSlot.Status, mountAnim, commandBuffer);
        }
        // Walk/seek overlays must not keep running on a seated NPC.
        NpcAnimationPlayback.play(npcRef, npc, AnimationSlot.Movement, null, commandBuffer);
    }

    /**
     * Align to the seat then mount like {@link com.hexvane.aetherhaven.guild.GuildHallAdventurerChairMount}: put
     * Transform on the command buffer, try several hit points. Trust {@link BlockMountAPI.Mounted}; do not face-yaw
     * after (that fights the seat orientation and unseats visuals).
     */
    private static boolean tryMountBlockPoi(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PoiEntry poi
    ) {
        World world = store.getExternalData().getWorld();
        Vector3i block = VillagerBlockUtil.resolveMountBaseBlock(world, poi.getX(), poi.getY(), poi.getZ());
        TransformComponent tc = commandBuffer.getComponent(npcRef, TransformComponent.getComponentType());
        if (tc == null) {
            tc = store.getComponent(npcRef, TransformComponent.getComponentType());
        }
        if (tc == null) {
            return false;
        }
        Vector3d p = tc.getPosition();
        if (!poi.hasInteractionTarget()
            && !VillagerBlockUtil.canNpcMountBlockPoi(world, p.x, p.y, p.z, block.x, block.y, block.z)) {
            return false;
        }
        try {
            // Prefer the best free seat (center first on multi-seat benches). Never hit from the approach cell first —
            // that biases village benches onto their sideways offset seat. Always mount the furniture origin, never a
            // filler voxel — filler origin shifts the side seat into empty air for some orientations.
            Vector3d seatPos = VillagerBlockUtil.preferredAvailableSeatWorldPosition(world, block);
            Vector3d blockCenter = new Vector3d(block.x + 0.5, block.y + 0.5, block.z + 0.5);
            if (seatPos != null) {
                TransformComponentUtil.replacePreservingChunk(npcRef, store, commandBuffer, seatPos, tc.getRotation());
            } else {
                commandBuffer.putComponent(npcRef, TransformComponent.getComponentType(), tc);
            }
            Vector3d primary = seatPos != null ? seatPos : blockCenter;
            BlockMountAPI.BlockMountResult result =
                tryMountWithHits(npcRef, commandBuffer, block, primary, blockCenter);
            if (!(result instanceof BlockMountAPI.Mounted)) {
                return false;
            }
            // Re-put mounted transform so chunk linkage and seat pose stick through autonomy systems.
            TransformComponent mountedTc = commandBuffer.getComponent(npcRef, TransformComponent.getComponentType());
            if (mountedTc == null) {
                mountedTc = store.getComponent(npcRef, TransformComponent.getComponentType());
            }
            if (mountedTc != null) {
                commandBuffer.putComponent(npcRef, TransformComponent.getComponentType(), mountedTc);
            }
            return true;
        } catch (RuntimeException ex) {
            LOGGER.at(Level.FINE).withCause(ex).log("Could not mount NPC for POI block mount");
            return false;
        }
    }

    @Nonnull
    private static BlockMountAPI.BlockMountResult tryMountWithHits(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Vector3i mountBlock,
        @Nonnull Vector3d... hits
    ) {
        BlockMountAPI.BlockMountResult last = BlockMountAPI.DidNotMount.NO_MOUNT_POINT_FOUND;
        for (Vector3d hit : hits) {
            BlockMountAPI.BlockMountResult result = BlockMountAPI.mountOnBlock(npcRef, commandBuffer, mountBlock, hit);
            if (result instanceof BlockMountAPI.Mounted) {
                return result;
            }
            last = result;
        }
        return last;
    }

    /** When bed mount fails (chunk, etc.): lie on mattress height without the old corner nudge (wrong pillow / below bed). */
    private static void sleepPoiFallbackPose(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PoiEntry poi
    ) {
        TransformComponent tc = store.getComponent(npcRef, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }
        Vector3d pos = tc.getPosition();
        pos.x = poi.getX() + 0.5;
        pos.z = poi.getZ() + 0.5;
        pos.y = poi.getY() + 0.35;
        commandBuffer.putComponent(npcRef, TransformComponent.getComponentType(), tc);
        faceAlongBedHeading(npcRef, store, commandBuffer, poi);
    }

    /** Bed +Z in block space is “forward” for {@link RotationTuple}; align lying yaw to headboard / pillow. */
    private static void faceAlongBedHeading(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PoiEntry poi
    ) {
        TransformComponent tc = store.getComponent(npcRef, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        int rotIdx = VillagerBlockUtil.blockRotationIndexNoLoad(world, poi.getX(), poi.getY(), poi.getZ());
        RotationTuple rt = rotationTupleOrNone(rotIdx);
        Vector3d forward = rt.rotatedVector(new Vector3d(0, 0, 1));
        EntityRotationUtil.setBodyYaw(tc.getRotation(), bodyYawAlongMove(forward.x, forward.z));
        commandBuffer.putComponent(npcRef, TransformComponent.getComponentType(), tc);
    }

    private static void playCampfireConsumeAnim(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Item item = Item.getAssetMap().getAsset(AetherhavenConstants.CAMPFIRE_EAT_ITEM_ID);
        if (item == null) {
            return;
        }
        String pid = item.getPlayerAnimationsId();
        if (pid == null || pid.isBlank()) {
            return;
        }
        ItemPlayerAnimations ipa = ItemPlayerAnimations.getAssetMap().getAsset(pid);
        if (ipa == null) {
            return;
        }
        NpcAnimationPlayback.playItem(npcRef, AnimationSlot.Action, ipa, "Consume", commandBuffer);
    }

    /**
     * Item “Consume” can leave client-side state on Action and/or Emote; also send an explicit clear with the same
     * item-animations id (matches interaction {@code ClearAnimationOnFinish} behaviour).
     */
    private static void stopCampfireConsumeVisuals(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nullable NPCEntity npc
    ) {
        NpcAnimationPlayback.stop(npcRef, AnimationSlot.Action, commandBuffer);
        NpcAnimationPlayback.stop(npcRef, AnimationSlot.Emote, commandBuffer);
        Item item = Item.getAssetMap().getAsset(AetherhavenConstants.CAMPFIRE_EAT_ITEM_ID);
        String pid = item != null ? item.getPlayerAnimationsId() : null;
        if (pid != null && !pid.isBlank()) {
            NpcAnimationPlayback.playItem(npcRef, AnimationSlot.Action, pid, null, commandBuffer);
        }
        if (npc != null) {
            NpcAnimationPlayback.play(npcRef, npc, AnimationSlot.Action, null, commandBuffer);
            NpcAnimationPlayback.play(npcRef, npc, AnimationSlot.Emote, null, commandBuffer);
        }
    }

    private static void stopCampfireConsumeVisuals(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nullable NPCEntity npc
    ) {
        AnimationUtils.stopAnimation(npcRef, AnimationSlot.Action, store);
        AnimationUtils.stopAnimation(npcRef, AnimationSlot.Emote, store);
        Item item = Item.getAssetMap().getAsset(AetherhavenConstants.CAMPFIRE_EAT_ITEM_ID);
        String pid = item != null ? item.getPlayerAnimationsId() : null;
        if (pid != null && !pid.isBlank()) {
            AnimationUtils.playAnimation(npcRef, AnimationSlot.Action, pid, null, false, store);
        }
        if (npc != null) {
            npc.playAnimation(npcRef, AnimationSlot.Action, null, store);
            npc.playAnimation(npcRef, AnimationSlot.Emote, null, store);
        }
    }

    @Nonnull
    private static RotationTuple rotationTupleOrNone(int index) {
        if (index < 0 || index >= RotationTuple.VALUES.length) {
            return RotationTuple.NONE;
        }
        RotationTuple t = RotationTuple.VALUES[index];
        return t != null ? t : RotationTuple.NONE;
    }

    private static void faceTowardBlock(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PoiEntry poi
    ) {
        if (poi.hasInteractionTarget()) {
            Float storedYaw = poi.getInteractionTargetYawRadians();
            if (storedYaw != null) {
                applyBodyYaw(npcRef, store, commandBuffer, storedYaw);
                return;
            }
            Double tx = poi.getInteractionTargetX();
            Double ty = poi.getInteractionTargetY();
            Double tz = poi.getInteractionTargetZ();
            if (tx != null && tz != null) {
                TransformComponent tc = store.getComponent(npcRef, TransformComponent.getComponentType());
                if (tc != null) {
                    Vector3d pos = tc.getPosition();
                    double dx = tx - pos.x;
                    double dz = tz - pos.z;
                    if (dx * dx + dz * dz >= 1.0e-6) {
                        applyBodyYaw(npcRef, store, commandBuffer, bodyYawAlongMove(dx, dz));
                        return;
                    }
                }
            }
        }
        TransformComponent tc = store.getComponent(npcRef, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }
        Vector3d pos = tc.getPosition();
        double dx = (poi.getX() + 0.5) - pos.x;
        double dz = (poi.getZ() + 0.5) - pos.z;
        if (dx * dx + dz * dz < 1.0e-6) {
            return;
        }
        applyBodyYaw(npcRef, store, commandBuffer, bodyYawAlongMove(dx, dz));
    }

    private static void applyInteractionTargetFacing(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PoiEntry poi
    ) {
        if (!poi.hasInteractionTarget()) {
            return;
        }
        Float yaw = poi.getInteractionTargetYawRadians();
        if (yaw != null) {
            applyBodyYaw(npcRef, store, commandBuffer, yaw);
        }
    }

    private static void applyBodyYaw(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        float yawRadians
    ) {
        TransformComponent tc = store.getComponent(npcRef, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }
        EntityRotationUtil.setBodyYaw(tc.getRotation(), yawRadians);
        commandBuffer.putComponent(npcRef, TransformComponent.getComponentType(), tc);
    }

    private static float bodyYawAlongMove(double dx, double dz) {
        return (float) (Math.atan2(dx, dz) + Math.PI);
    }

    @Nullable
    private static String pickFurnitureAnimationId(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull VillagerBlockUtil.FurnitureMountKind furniture
    ) {
        String primary =
            switch (furniture) {
                case SEAT -> "Sit";
                case BED -> "Sleep";
                case NONE -> null;
            };
        return resolveModelAnimation(store, npcRef, primary, furniture == VillagerBlockUtil.FurnitureMountKind.BED);
    }

    /**
     * Status anim for non-furniture POIs. Sit/sleep only apply when a seat/bed was mounted earlier — never play a
     * standing Sit on the floor for {@code SIT}/{@code USE_BENCH} on ordinary blocks.
     */
    @Nullable
    private static String pickNonFurnitureAnimationId(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull PoiInteractionKind kind
    ) {
        String primary =
            switch (kind) {
                case SIT, USE_BENCH -> null;
                case SLEEP -> "Sleep";
                case WORK_SURFACE, USE_CONTAINER, NONE -> null;
            };
        return resolveModelAnimation(store, npcRef, primary, kind == PoiInteractionKind.SLEEP);
    }

    @Nullable
    private static String resolveModelAnimation(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> npcRef,
        @Nullable String primary,
        boolean preferSitFallback
    ) {
        if (primary == null) {
            return null;
        }
        ModelComponent mc = store.getComponent(npcRef, ModelComponent.getComponentType());
        Model model = mc != null ? mc.getModel() : null;
        if (model != null && model.getAnimationSetMap().containsKey(primary)) {
            return primary;
        }
        String fallback = preferSitFallback ? "Sit" : primary;
        if (model != null && fallback != null && model.getAnimationSetMap().containsKey(fallback)) {
            return fallback;
        }
        LOGGER.at(Level.FINE).atMostEvery(1, TimeUnit.MINUTES).log(
            "POI furniture animation missing (tried %s); NPC may T-pose briefly",
            primary
        );
        return primary;
    }
}
