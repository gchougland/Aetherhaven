package com.hexvane.aetherhaven.festival.snowball;

import com.hexvane.aetherhaven.autonomy.VillagerAutonomySystem;
import com.hexvane.aetherhaven.autonomy.VillagerFollowPlayerSystem;
import com.hexvane.aetherhaven.equipment.VillagerEquipmentService;
import com.hexvane.aetherhaven.npc.NpcAnimationPlayback;
import com.hexvane.aetherhaven.tourist.TouristAutonomySystem;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.Order;
import com.hypixel.hytale.component.dependency.SystemDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.systems.SteeringSystem;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Crouch-in-place fight AI for villager snowball fighters. Autonomy is skipped separately for living fighters.
 */
public final class SnowballVillagerSystem extends EntityTickingSystem<EntityStore> {
    /** Head height above feet for aim, and hand height for the throw origin. */
    private static final double AIM_HEAD_OFFSET = 1.7;
    private static final double THROW_HAND_OFFSET = 1.45;

    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies =
        Set.of(
            new SystemDependency<>(Order.AFTER, SteeringSystem.class),
            new SystemDependency<>(Order.AFTER, VillagerAutonomySystem.class),
            new SystemDependency<>(Order.AFTER, VillagerFollowPlayerSystem.class),
            new SystemDependency<>(Order.AFTER, TouristAutonomySystem.class)
        );

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(
            NPCEntity.getComponentType(),
            UUIDComponent.getComponentType(),
            TransformComponent.getComponentType()
        );
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        UUIDComponent uuidComponent = archetypeChunk.getComponent(index, UUIDComponent.getComponentType());
        NPCEntity npc = archetypeChunk.getComponent(index, NPCEntity.getComponentType());
        TransformComponent transform = archetypeChunk.getComponent(index, TransformComponent.getComponentType());
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        if (uuidComponent == null || npc == null || transform == null || ref == null) {
            return;
        }
        UUID uuid = uuidComponent.getUuid();
        SnowballSession session = SnowballSessionIndex.sessionForFighter(uuid);
        if (session == null || !session.isVillagerFighter(uuid)) {
            return;
        }
        long now = System.currentTimeMillis();
        SnowballSession.VillagerAi ai = session.villagerAi(uuid);
        if (!session.isLivingFighter(uuid)) {
            SnowballPin.unpin(ref, commandBuffer);
            if (ai == null || ai.crouchChanged(false)) {
                setCrouching(ref, store, commandBuffer, false);
            }
            clearHeldSnowball(ref, store, commandBuffer);
            return;
        }

        SnowballSession.Fighter fighter = session.fighter(uuid);
        if (fighter != null) {
            SnowballPin.hold(ref, store, commandBuffer, npc, fighter.pad());
            snapIfNeeded(ref, commandBuffer, transform, fighter.pad());
        }

        if (ai == null) {
            setCrouching(ref, store, commandBuffer, true);
            return;
        }
        if (ai.consumePrepare()) {
            if (fighter != null) {
                SnowballPin.start(ref, store, npc, commandBuffer, fighter.pad());
            }
            applyCrouch(ref, store, commandBuffer, ai, true);
        }
        faceThrowTarget(ref, store, commandBuffer, session, uuid, transform, ai);
        equipSnowball(ref, store, commandBuffer);

        switch (ai.phase()) {
            case CROUCH -> {
                applyCrouch(ref, store, commandBuffer, ai, true);
                if (now >= ai.nextEpochMs()) {
                    pickThrowTarget(ai, session, uuid);
                    applyCrouch(ref, store, commandBuffer, ai, false);
                    ai.set(
                        SnowballSession.VillagerAiPhase.STAND_BEFORE,
                        now + SnowballIds.VILLAGER_STAND_BEFORE_THROW_MS
                    );
                }
            }
            case STAND_BEFORE -> {
                applyCrouch(ref, store, commandBuffer, ai, false);
                if (now >= ai.nextEpochMs()) {
                    ai.set(SnowballSession.VillagerAiPhase.THROW, now);
                }
            }
            case THROW -> {
                applyCrouch(ref, store, commandBuffer, ai, false);
                ai.set(
                    SnowballSession.VillagerAiPhase.STAND_AFTER,
                    now + SnowballIds.VILLAGER_STAND_AFTER_THROW_MS
                );
                try {
                    throwAtOpponent(ref, store, commandBuffer, session, uuid, transform, ai);
                } catch (RuntimeException ignored) {
                    // Stay in STAND_AFTER so a failed spawn cannot throw every tick.
                }
            }
            case STAND_AFTER -> {
                applyCrouch(ref, store, commandBuffer, ai, false);
                if (now >= ai.nextEpochMs()) {
                    long span = SnowballIds.VILLAGER_CROUCH_MAX_MS - SnowballIds.VILLAGER_CROUCH_MIN_MS;
                    long hold = SnowballIds.VILLAGER_CROUCH_MIN_MS
                        + ThreadLocalRandom.current().nextLong(Math.max(1L, span + 1L));
                    ai.setThrowTargetUuid(null);
                    applyCrouch(ref, store, commandBuffer, ai, true);
                    ai.set(SnowballSession.VillagerAiPhase.CROUCH, now + hold);
                }
            }
        }
    }

    private static void applyCrouch(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull SnowballSession.VillagerAi ai,
        boolean crouching
    ) {
        if (!ai.crouchChanged(crouching)) {
            return;
        }
        setCrouching(ref, store, commandBuffer, crouching);
    }

    private static void pickThrowTarget(
        @Nonnull SnowballSession.VillagerAi ai,
        @Nonnull SnowballSession session,
        @Nonnull UUID uuid
    ) {
        List<UUID> opponents = session.livingOpponentUuids(uuid);
        if (opponents.isEmpty()) {
            ai.setThrowTargetUuid(null);
            return;
        }
        ai.setThrowTargetUuid(opponents.get(ThreadLocalRandom.current().nextInt(opponents.size())));
    }

    private static void faceThrowTarget(
        @Nonnull Ref<EntityStore> self,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull SnowballSession session,
        @Nonnull UUID uuid,
        @Nonnull TransformComponent transform,
        @Nonnull SnowballSession.VillagerAi ai
    ) {
        UUID target = resolveLivingThrowTarget(session, uuid, ai);
        if (target == null) {
            return;
        }
        Vector3d to = aimPoint(store, session, target);
        if (to == null) {
            return;
        }
        Vector3d from = transform.getPosition();
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        if (dx * dx + dz * dz < 0.01) {
            return;
        }
        // Hytale body forward is opposite raw atan2(dx, dz).
        float yaw = (float) (Math.atan2(dx, dz) + Math.PI);
        transform.setRotation(new Rotation3f(0f, yaw, 0f));
        commandBuffer.putComponent(self, TransformComponent.getComponentType(), transform);
    }

    @Nullable
    private static UUID resolveLivingThrowTarget(
        @Nonnull SnowballSession session,
        @Nonnull UUID uuid,
        @Nonnull SnowballSession.VillagerAi ai
    ) {
        UUID locked = ai.throwTargetUuid();
        if (locked != null && session.isLivingFighter(locked) && session.wouldHit(locked, uuid)) {
            return locked;
        }
        List<UUID> opponents = session.livingOpponentUuids(uuid);
        if (opponents.isEmpty()) {
            ai.setThrowTargetUuid(null);
            return null;
        }
        UUID fallback = opponents.get(0);
        ai.setThrowTargetUuid(fallback);
        return fallback;
    }

    private static void throwAtOpponent(
        @Nonnull Ref<EntityStore> self,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull SnowballSession session,
        @Nonnull UUID uuid,
        @Nonnull TransformComponent transform,
        @Nonnull SnowballSession.VillagerAi ai
    ) {
        UUID target = resolveLivingThrowTarget(session, uuid, ai);
        if (target == null) {
            return;
        }
        Vector3d to = aimPoint(store, session, target);
        if (to == null) {
            return;
        }
        ProjectileConfig config = ProjectileConfig.getAssetMap().getAsset(SnowballIds.PROJECTILE_CONFIG_ID);
        if (config == null) {
            return;
        }
        Vector3d from = new Vector3d(transform.getPosition());
        from.y += THROW_HAND_OFFSET;
        applyGravityLoft(from, to, config.getLaunchForce(), config.getGravity());
        Vector3d dir = new Vector3d(to).sub(from);
        if (dir.lengthSquared() < 0.01) {
            return;
        }
        dir.normalize();
        // Hytale body forward is opposite raw atan2(dx, dz).
        float yaw = (float) (Math.atan2(dir.x, dir.z) + Math.PI);
        transform.setRotation(new Rotation3f(0f, yaw, 0f));
        commandBuffer.putComponent(self, TransformComponent.getComponentType(), transform);
        playThrowAnimation(self, commandBuffer);
        ProjectileModule.get().spawnProjectile(self, commandBuffer, config, from, dir);
    }

    /**
     * Raises the aim point so gravity does not drop the snowball into the feet over distance.
     */
    private static void applyGravityLoft(
        @Nonnull Vector3d from,
        @Nonnull Vector3d to,
        double launchForce,
        double gravity
    ) {
        if (!(launchForce > 0.1) || !(gravity > 0.0)) {
            return;
        }
        double horiz = Math.hypot(to.x - from.x, to.z - from.z);
        if (horiz < 0.5) {
            return;
        }
        double flightSec = horiz / launchForce;
        to.y += 0.5 * gravity * flightSec * flightSec;
    }

    @Nullable
    private static Vector3d aimPoint(
        @Nonnull Store<EntityStore> store,
        @Nonnull SnowballSession session,
        @Nonnull UUID target
    ) {
        Ref<EntityStore> targetRef = store.getExternalData().getRefFromUUID(target);
        if (targetRef != null && targetRef.isValid()) {
            TransformComponent targetTransform = store.getComponent(targetRef, TransformComponent.getComponentType());
            if (targetTransform != null) {
                Vector3d to = new Vector3d(targetTransform.getPosition());
                to.y += AIM_HEAD_OFFSET;
                return to;
            }
        }
        SnowballSession.Fighter fighter = session.fighter(target);
        if (fighter == null) {
            return null;
        }
        SnowballSession.StartPad pad = fighter.pad();
        return new Vector3d(pad.x(), pad.y() + AIM_HEAD_OFFSET, pad.z());
    }

    private static void playThrowAnimation(
        @Nonnull Ref<EntityStore> self,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Item item = Item.getAssetMap().getAsset(SnowballIds.SNOWBALL_ITEM_ID);
        String animId = item != null ? item.getPlayerAnimationsId() : null;
        if (animId == null || animId.isBlank()) {
            return;
        }
        ItemPlayerAnimations ipa = ItemPlayerAnimations.getAssetMap().getAsset(animId);
        if (ipa == null) {
            return;
        }
        NpcAnimationPlayback.playItem(self, AnimationSlot.Action, ipa, "Throw", commandBuffer);
    }

    private static void equipSnowball(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        InventoryComponent.Hotbar hb = store.getComponent(npcRef, InventoryComponent.Hotbar.getComponentType());
        if (hb == null) {
            return;
        }
        try {
            ItemStack active = hb.getActiveItem();
            if (active != null && SnowballIds.SNOWBALL_ITEM_ID.equals(active.getItemId())) {
                return;
            }
            byte slot = hb.getActiveSlot();
            if (slot < 0 || slot >= hb.getInventory().getCapacity()) {
                slot = 0;
            }
            hb.getInventory().setItemStackForSlot((short) slot, new ItemStack(SnowballIds.SNOWBALL_ITEM_ID, 1));
            VillagerEquipmentService.markHotbarEquipmentDirty(hb, slot, npcRef, commandBuffer);
            commandBuffer.putComponent(npcRef, InventoryComponent.Hotbar.getComponentType(), hb);
        } catch (RuntimeException ignored) {
            // Some NPCs have a hotbar that cannot take a display item.
        }
    }

    private static void clearHeldSnowball(
        @Nonnull Ref<EntityStore> npcRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        InventoryComponent.Hotbar hb = store.getComponent(npcRef, InventoryComponent.Hotbar.getComponentType());
        if (hb == null) {
            return;
        }
        ItemStack active = hb.getActiveItem();
        if (active == null || !SnowballIds.SNOWBALL_ITEM_ID.equals(active.getItemId())) {
            return;
        }
        byte slot = hb.getActiveSlot();
        if (slot < 0 || slot >= hb.getInventory().getCapacity()) {
            slot = 0;
        }
        hb.getInventory().removeItemStackFromSlot((short) slot);
        VillagerEquipmentService.markHotbarEquipmentDirty(hb, slot, npcRef, commandBuffer);
        commandBuffer.putComponent(npcRef, InventoryComponent.Hotbar.getComponentType(), hb);
    }

    private static void snapIfNeeded(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull TransformComponent transform,
        @Nonnull SnowballSession.StartPad pad
    ) {
        Vector3d pos = transform.getPosition();
        double dx = pos.x - pad.x();
        double dz = pos.z - pad.z();
        if (dx * dx + dz * dz < 0.35) {
            return;
        }
        transform.setPosition(new Vector3d(pad.x(), pad.y(), pad.z()));
        transform.setRotation(new Rotation3f(0f, (float) Math.toRadians(pad.yawDegrees()), 0f));
        commandBuffer.putComponent(ref, TransformComponent.getComponentType(), transform);
    }

    private static void setCrouching(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        boolean crouching
    ) {
        MovementStatesComponent states = store.getComponent(ref, MovementStatesComponent.getComponentType());
        if (states == null || states.getMovementStates() == null) {
            return;
        }
        states.getMovementStates().crouching = crouching;
        states.getMovementStates().forcedCrouching = crouching;
        commandBuffer.putComponent(ref, MovementStatesComponent.getComponentType(), states);
    }
}
