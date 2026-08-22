package com.hexvane.aetherhaven.festival.snowball;

import com.hexvane.aetherhaven.autonomy.VillagerAutonomySystem;
import com.hexvane.aetherhaven.autonomy.VillagerFollowPlayerSystem;
import com.hexvane.aetherhaven.entity.TransformComponentUtil;
import com.hexvane.aetherhaven.equipment.VillagerEquipmentService;
import com.hexvane.aetherhaven.festival.pigrace.PigRaceLanes;
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
import com.hypixel.hytale.server.npc.systems.SteppableTickingSystem;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.system.TransformSystems;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.projectile.ProjectileModule;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.systems.MovementStatesSystem;
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
public final class SnowballVillagerSystem extends SteppableTickingSystem {
    /** Head height above feet for aim, and hand height for the throw origin. */
    private static final double AIM_HEAD_OFFSET = 1.7;
    private static final double THROW_HAND_OFFSET = 1.45;
    /** Model animation set id for stationary crouch (not CrouchWalk). */
    private static final String CROUCH_IDLE_ANIM = "Crouch";

    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies =
        Set.of(
            new SystemDependency<>(Order.AFTER, VillagerAutonomySystem.class),
            new SystemDependency<>(Order.AFTER, VillagerFollowPlayerSystem.class),
            new SystemDependency<>(Order.AFTER, TouristAutonomySystem.class),
            new SystemDependency<>(Order.AFTER, MovementStatesSystem.class),
            new SystemDependency<>(Order.BEFORE, TransformSystems.EntityTrackerUpdate.class)
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
            TransformComponent.getComponentType(),
            MovementStatesComponent.getComponentType()
        );
    }

    @Override
    public boolean isParallel(int archetypeChunkSize, int taskCount) {
        return EntityTickingSystem.maybeUseParallel(archetypeChunkSize, taskCount);
    }

    @Override
    public void steppedTick(
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
            SnowballPin.unpin(ref, npc, commandBuffer);
            clearHeldSnowball(ref, store, commandBuffer);
            applyFightMovementState(archetypeChunk, index, ref, commandBuffer, false);
            return;
        }

        SnowballSession.Fighter fighter = session.fighter(uuid);
        if (fighter != null) {
            SnowballPin.hold(ref, store, commandBuffer, npc, fighter.pad());
        }

        boolean wantCrouch = ai == null || ai.phase() == SnowballSession.VillagerAiPhase.CROUCH;
        if (ai == null) {
            if (fighter != null) {
                pinToPad(ref, store, commandBuffer, transform, fighter.pad());
            }
            syncCrouchAnimation(ref, npc, commandBuffer, null, wantCrouch);
            applyFightMovementState(archetypeChunk, index, ref, commandBuffer, wantCrouch);
            return;
        }
        if (ai.consumePrepare()) {
            if (fighter != null) {
                SnowballPin.start(ref, store, npc, commandBuffer, fighter.pad());
            }
        }
        equipSnowball(ref, store, commandBuffer);

        switch (ai.phase()) {
            case CROUCH -> {
                if (now >= ai.nextEpochMs()) {
                    pickThrowTarget(ai, session, uuid);
                    ai.set(
                        SnowballSession.VillagerAiPhase.STAND_BEFORE,
                        now + SnowballIds.VILLAGER_STAND_BEFORE_THROW_MS
                    );
                    wantCrouch = false;
                } else if (ai.throwTargetUuid() == null) {
                    pickThrowTarget(ai, session, uuid);
                }
            }
            case STAND_BEFORE -> {
                if (now >= ai.nextEpochMs()) {
                    ai.set(SnowballSession.VillagerAiPhase.THROW, now);
                }
            }
            case THROW -> {
                ai.set(
                    SnowballSession.VillagerAiPhase.STAND_AFTER,
                    now + SnowballIds.VILLAGER_STAND_AFTER_THROW_MS
                );
                wantCrouch = false;
                try {
                    throwAtOpponent(ref, store, commandBuffer, session, uuid, transform, ai);
                } catch (RuntimeException ignored) {
                    // Stay in STAND_AFTER so a failed spawn cannot throw every tick.
                }
            }
            case STAND_AFTER -> {
                if (now >= ai.nextEpochMs()) {
                    long span = SnowballIds.VILLAGER_CROUCH_MAX_MS - SnowballIds.VILLAGER_CROUCH_MIN_MS;
                    long hold = SnowballIds.VILLAGER_CROUCH_MIN_MS
                        + ThreadLocalRandom.current().nextLong(Math.max(1L, span + 1L));
                    ai.setThrowTargetUuid(null);
                    ai.set(SnowballSession.VillagerAiPhase.CROUCH, now + hold);
                    wantCrouch = true;
                }
            }
        }
        if (fighter != null) {
            pinToPad(ref, store, commandBuffer, transform, fighter.pad());
        }
        syncCrouchAnimation(ref, npc, commandBuffer, ai, wantCrouch);
        applyFightMovementState(archetypeChunk, index, ref, commandBuffer, wantCrouch);
        faceThrowTarget(ref, store, commandBuffer, session, uuid, transform, ai);
    }

    /** Plays stationary crouch once per crouch phase; Movement slot walk blending uses idle=true. */
    private static void syncCrouchAnimation(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull NPCEntity npc,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nullable SnowballSession.VillagerAi ai,
        boolean wantCrouch
    ) {
        if (ai != null) {
            if (!ai.crouchChanged(wantCrouch)) {
                return;
            }
        }
        if (wantCrouch) {
            NpcAnimationPlayback.play(ref, npc, AnimationSlot.Movement, CROUCH_IDLE_ANIM, commandBuffer);
        } else {
            NpcAnimationPlayback.stop(ref, AnimationSlot.Movement, commandBuffer);
        }
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
        TransformComponent live = commandBuffer.getComponent(self, TransformComponent.getComponentType());
        if (live == null) {
            live = transform;
        }
        Vector3d from = new Vector3d(live.getPosition());
        from.y += AIM_HEAD_OFFSET;
        Vector3d look = new Vector3d(to).sub(from);
        if (look.lengthSquared() < 0.01) {
            return;
        }
        Rotation3f lookRotation = Rotation3f.lookAt(look);
        HeadRotation head = commandBuffer.getComponent(self, HeadRotation.getComponentType());
        if (head == null) {
            head = store.getComponent(self, HeadRotation.getComponentType());
        }
        if (head != null) {
            head.setRotation(lookRotation);
            commandBuffer.putComponent(self, HeadRotation.getComponentType(), head);
        }
        // While crouched on the pad, turn the head only. Stand up to throw with the body facing the target.
        if (ai.phase() != SnowballSession.VillagerAiPhase.CROUCH) {
            float yaw = PigRaceLanes.facingYawRadians(look.x, look.z);
            live.setRotation(new Rotation3f(0f, yaw, 0f));
            commandBuffer.putComponent(self, TransformComponent.getComponentType(), live);
        }
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
        HeadRotation head = commandBuffer.getComponent(self, HeadRotation.getComponentType());
        if (head == null) {
            head = store.getComponent(self, HeadRotation.getComponentType());
        }
        if (head != null) {
            head.setRotation(Rotation3f.lookAt(dir));
            commandBuffer.putComponent(self, HeadRotation.getComponentType(), head);
        }
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

    /**
     * After steering/knockback and after throw facing writes, lock feet to the pad. Keep current yaw so they can
     * still turn to throw. Soft BodyMotion Nothing does not skip steering the way Frozen did.
     */
    private static void pinToPad(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull TransformComponent transform,
        @Nonnull SnowballSession.StartPad pad
    ) {
        TransformComponent live = commandBuffer.getComponent(ref, TransformComponent.getComponentType());
        if (live == null) {
            live = transform;
        }
        TransformComponentUtil.replacePreservingChunk(
            ref,
            commandBuffer,
            new Vector3d(pad.x(), pad.y(), pad.z()),
            live.getRotation()
        );
        Velocity velocity = commandBuffer.getComponent(ref, Velocity.getComponentType());
        if (velocity == null) {
            velocity = store.getComponent(ref, Velocity.getComponentType());
        }
        if (velocity != null && (velocity.getX() != 0d || velocity.getY() != 0d || velocity.getZ() != 0d)) {
            velocity.setZero();
            commandBuffer.putComponent(ref, Velocity.getComponentType(), velocity);
        }
    }

    /**
     * Writes on the live archetype chunk (same object NPC {@link MovementStatesSystem} updates) and re-queues the
     * component so client sync sees the corrected pose after this system runs.
     */
    private static void applyFightMovementState(
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        int index,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        boolean crouching
    ) {
        MovementStatesComponent component =
            archetypeChunk.getComponent(index, MovementStatesComponent.getComponentType());
        if (component == null || component.getMovementStates() == null) {
            return;
        }
        applySnowballFightMovementStates(component.getMovementStates(), crouching);
        commandBuffer.putComponent(ref, MovementStatesComponent.getComponentType(), component);
    }

    /** Stand/crouch snapshot for pad fighters. Re-applied every tick after NPC movement-state updates. */
    static void applySnowballFightMovementStates(@Nonnull MovementStates states, boolean crouching) {
        states.crouching = crouching;
        states.forcedCrouching = crouching;
        states.walking = false;
        states.running = false;
        states.sprinting = false;
        states.jumping = false;
        states.falling = false;
        states.fallingFar = false;
        states.idle = true;
        states.horizontalIdle = true;
        states.onGround = true;
    }
}
