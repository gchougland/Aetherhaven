package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiInteractionKind;
import com.hypixel.hytale.builtin.mounts.BlockMountAPI;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations;
import com.hypixel.hytale.server.core.asset.type.model.config.Model;
import com.hypixel.hytale.server.core.entity.AnimationUtils;
import com.hypixel.hytale.server.core.entity.Entity;
import com.hypixel.hytale.server.core.entity.EntityUtils;
import com.hypixel.hytale.server.core.entity.LivingEntity;
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
        Set<String> tags = poi.getTags();
        if (poi.getInteractionKind() == PoiInteractionKind.USE_BENCH && tags.contains("EAT")) {
            faceTowardBlock(npcRef, store, commandBuffer, poi);
            tryEquipCampfireHeldFood(npcRef, store, commandBuffer);
            playCampfireConsumeAnim(npcRef, store);
            return;
        }
        if (poi.getInteractionKind() == PoiInteractionKind.SIT) {
            if (tryMountBlockPoi(npcRef, store, commandBuffer, poi)) {
                NPCEntity mountedNpc = store.getComponent(npcRef, NPCEntity.getComponentType());
                if (mountedNpc != null) {
                    String sitAnim = pickAnimationId(store, npcRef, PoiInteractionKind.SIT);
                    if (sitAnim != null) {
                        mountedNpc.playAnimation(npcRef, AnimationSlot.Status, sitAnim, store);
                    }
                }
                return;
            }
            faceTowardBlock(npcRef, store, commandBuffer, poi);
        } else if (poi.getInteractionKind() == PoiInteractionKind.SLEEP) {
            if (tryMountBlockPoi(npcRef, store, commandBuffer, poi)) {
                NPCEntity mountedNpc = store.getComponent(npcRef, NPCEntity.getComponentType());
                if (mountedNpc != null) {
                    String sleepAnim = pickAnimationId(store, npcRef, PoiInteractionKind.SLEEP);
                    if (sleepAnim != null) {
                        mountedNpc.playAnimation(npcRef, AnimationSlot.Status, sleepAnim, store);
                    }
                }
                return;
            }
            sleepPoiFallbackPose(npcRef, store, commandBuffer, poi);
        } else {
            faceTowardBlock(npcRef, store, commandBuffer, poi);
        }
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (npc == null) {
            return;
        }
        String anim = pickAnimationId(store, npcRef, poi.getInteractionKind());
        if (anim != null) {
            npc.playAnimation(npcRef, AnimationSlot.Status, anim, store);
        }
    }

    /** Stop item consume / walk overlays after campfire USE. */
    public static void cleanupAfterPoiUse(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PoiEntry poi
    ) {
        NPCEntity npc = store.getComponent(npcRef, NPCEntity.getComponentType());
        if (poi.getInteractionKind() == PoiInteractionKind.SIT || poi.getInteractionKind() == PoiInteractionKind.SLEEP) {
            commandBuffer.tryRemoveComponent(npcRef, MountedComponent.getComponentType());
        }
        if (poi.getInteractionKind() == PoiInteractionKind.USE_BENCH && poi.getTags().contains("EAT")) {
            stopCampfireConsumeVisuals(npcRef, store, commandBuffer, npc);
            AnimationUtils.stopAnimation(npcRef, AnimationSlot.Movement, store);
            tryClearCampfireHeldFood(npcRef, store, commandBuffer);
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
            hb.getInventory().setItemStackForSlot((short) 0, new ItemStack(AetherhavenConstants.CAMPFIRE_EAT_ITEM_ID, 1));
            hb.setActiveSlot((byte) 0);
            commandBuffer.putComponent(npcRef, InventoryComponent.Hotbar.getComponentType(), hb);
            invalidateLivingEquipment(npcRef, store);
        } catch (RuntimeException ex) {
            LOGGER.at(Level.FINE).withCause(ex).log("Could not equip campfire display item on NPC hotbar");
        }
    }

    private static void tryClearCampfireHeldFood(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        InventoryComponent.Hotbar hb = store.getComponent(npcRef, InventoryComponent.Hotbar.getComponentType());
        if (hb == null) {
            return;
        }
        try {
            hb.getInventory().removeItemStackFromSlot((short) 0);
            commandBuffer.putComponent(npcRef, InventoryComponent.Hotbar.getComponentType(), hb);
            invalidateLivingEquipment(npcRef, store);
        } catch (RuntimeException ex) {
            LOGGER.at(Level.FINE).withCause(ex).log("Could not clear campfire display item from NPC hotbar");
        }
    }

    /**
     * {@link com.hypixel.hytale.server.core.inventory.Inventory} keeps the same Java references as ECS hotbar
     * components; cloning hotbar desyncs {@code getItemInHand()} and skips equipment network updates.
     */
    private static void invalidateLivingEquipment(@Nonnull Ref<EntityStore> npcRef, @Nonnull Store<EntityStore> store) {
        Entity entity = EntityUtils.getEntity(npcRef, store);
        if (entity instanceof LivingEntity living) {
            living.invalidateEquipmentNetwork();
        }
    }

    /**
     * Uses {@link BlockMountAPI} like player seating / beds. Pick point is near the NPC so
     * {@link com.hypixel.hytale.builtin.mounts.BlockMountComponent#findAvailableSeat} chooses the mount closest to their
     * approach (block center alone can tie-break wrong for some props).
     */
    private static boolean tryMountBlockPoi(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull PoiEntry poi
    ) {
        try {
            Vector3i block = new Vector3i(poi.getX(), poi.getY(), poi.getZ());
            Vector3f hit = mountPickHit(store, npcRef, poi);
            BlockMountAPI.BlockMountResult result = BlockMountAPI.mountOnBlock(npcRef, commandBuffer, block, hit);
            return result instanceof BlockMountAPI.Mounted;
        } catch (RuntimeException ex) {
            LOGGER.at(Level.FINE).withCause(ex).log("Could not mount NPC for POI block mount");
            return false;
        }
    }

    @Nonnull
    private static Vector3f mountPickHit(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull PoiEntry poi
    ) {
        TransformComponent tc = store.getComponent(npcRef, TransformComponent.getComponentType());
        if (tc != null) {
            Vector3d p = tc.getPosition();
            return new Vector3f((float) p.x, (float) p.y + 0.5F, (float) p.z);
        }
        return new Vector3f(poi.getX() + 0.5F, poi.getY() + 0.5F, poi.getZ() + 0.5F);
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
        int rotIdx = VillagerPoiPathfinder.blockRotationIndexNoLoad(world, poi.getX(), poi.getY(), poi.getZ());
        RotationTuple rt = rotationTupleOrNone(rotIdx);
        Vector3d forward = rt.rotatedVector(new Vector3d(0, 0, 1));
        tc.getRotation().setYaw(bodyYawAlongMove(forward.x, forward.z));
        commandBuffer.putComponent(npcRef, TransformComponent.getComponentType(), tc);
    }

    private static void playCampfireConsumeAnim(@Nonnull Ref<EntityStore> npcRef, @Nonnull Store<EntityStore> store) {
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
        AnimationUtils.playAnimation(npcRef, AnimationSlot.Action, ipa, "Consume", store);
    }

    /**
     * Item “Consume” can leave client-side state on Action and/or Emote; also send an explicit clear with the same
     * item-animations id (matches interaction {@code ClearAnimationOnFinish} behaviour).
     */
    private static void stopCampfireConsumeVisuals(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
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
        tc.getRotation().setYaw(bodyYawAlongMove(dx, dz));
        commandBuffer.putComponent(npcRef, TransformComponent.getComponentType(), tc);
    }

    private static float bodyYawAlongMove(double dx, double dz) {
        return (float) (Math.atan2(dx, dz) + Math.PI);
    }

    @Nullable
    private static String pickAnimationId(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull PoiInteractionKind kind
    ) {
        String primary =
            switch (kind) {
                case SIT -> "Sit";
                case SLEEP -> "Sleep";
                case USE_BENCH -> "Sit";
                case WORK_SURFACE -> null;
                case USE_CONTAINER -> null;
                default -> null;
            };
        if (primary == null) {
            return null;
        }
        ModelComponent mc = store.getComponent(npcRef, ModelComponent.getComponentType());
        Model model = mc != null ? mc.getModel() : null;
        if (model != null && model.getAnimationSetMap().containsKey(primary)) {
            return primary;
        }
        String fallback = kind == PoiInteractionKind.SLEEP ? "Sit" : primary;
        if (model != null && fallback != null && model.getAnimationSetMap().containsKey(fallback)) {
            return fallback;
        }
        LOGGER.at(Level.FINE).atMostEvery(1, TimeUnit.MINUTES).log(
            "POI animation missing for kind %s (tried %s); NPC may T-pose briefly",
            kind,
            primary
        );
        return primary;
    }
}
