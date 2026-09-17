package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.builtin.mounts.BlockMountComponent;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.dependency.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.component.system.RefChangeSystem;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.BlockMountType;
import com.hypixel.hytale.protocol.MountController;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesSystems;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.system.TransformSystems;
import com.hypixel.hytale.server.core.modules.entity.system.ModelSystems;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.systems.MovementStatesSystem;
import java.util.Set;

/** Final furniture pose, after NPC steering/collision and before viewer updates. */
public final class VillagerMountedPoseSystem extends EntityTickingSystem<EntityStore> {
    private final Set<Dependency<EntityStore>> dependencies = Set.of(
        new SystemDependency<>(Order.AFTER, MovementStatesSystem.class),
        new SystemDependency<>(Order.BEFORE, ModelSystems.UpdateMovementStateBoundingBox.class),
        new SystemDependency<>(Order.BEFORE, TransformSystems.EntityTrackerUpdate.class),
        new SystemDependency<>(Order.BEFORE, MovementStatesSystems.TickingSystem.class));

    @Override public Set<Dependency<EntityStore>> getDependencies() { return dependencies; }

    @Override public Query<EntityStore> getQuery() {
        return Query.and(NPCEntity.getComponentType(), TransformComponent.getComponentType(),
            Query.or(VillagerSeatExit.getComponentType(),
                Query.and(TownVillagerBinding.getComponentType(), MountedComponent.getComponentType())));
    }

    @Override public void tick(float dt, int index, ArchetypeChunk<EntityStore> chunk,
                               Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        var ref = chunk.getReferenceTo(index);
        var mounted = chunk.getComponent(index, MountedComponent.getComponentType());
        if (mounted != null && mounted.getControllerType() != MountController.BlockMount) return;
        // Re-read after preceding queued mount/dismount operations. Never restore a
        // pose from a stale component captured earlier in the tick.
        buffer.run(s -> {
            if (!ref.isValid()) return;
            var pending = s.getComponent(ref, VillagerSeatExit.getComponentType());
            if (pending != null && VillagerSeatExit.tryFinish(ref, s, pending)) return;
            var current = s.getComponent(ref, MountedComponent.getComponentType());
            if (current == null || current != mounted) return;
            var autonomy = s.getComponent(ref, VillagerAutonomyState.getComponentType());
            if (pending == null && autonomy != null && autonomy.getPhase() != VillagerAutonomyState.PHASE_USE) {
                // A stale mount must not pin a villager who has already resumed travel.
                VillagerSeatExit.request(ref, s, null);
                if (s.getComponent(ref, MountedComponent.getComponentType()) == null) return;
            }
            var block = current.getMountedToBlock();
            var seats = block != null && block.isValid()
                ? block.getStore().getComponent(block, BlockMountComponent.getComponentType()) : null;
            var transform = s.getComponent(ref, TransformComponent.getComponentType());
            var npc = s.getComponent(ref, NPCEntity.getComponentType());
            if (transform == null || npc == null) return;
            if (seats == null || !alignToOccupiedSeat(ref, seats, transform)) {
                // A component without a real seat must not keep someone suspended.
                VillagerAutonomySystem.releaseBlockMountAndSnapToGround(ref, s, null);
                npc.playAnimation(ref, AnimationSlot.Status, null, s);
                return;
            }
            s.putComponent(ref, TransformComponent.getComponentType(), transform);
            var movement = s.getComponent(ref, MovementStatesComponent.getComponentType());
            boolean enteringPose = movement != null && applyMountedMovement(movement.getMovementStates(), current.getBlockMountType());
            var velocity = s.getComponent(ref, com.hypixel.hytale.server.core.modules.physics.component.Velocity.getComponentType());
            if (velocity != null) velocity.setZero();
            // Automatic falling/walking is client-driven and may not appear in
            // ActiveAnimationComponent. Clear it explicitly on the seat transition,
            // then keep the pose without restarting the animation each tick.
            if (enteringPose) npc.playAnimation(ref, AnimationSlot.Movement, null, true, s);
            npc.playAnimation(ref, AnimationSlot.Status, poseFor(current.getBlockMountType()), enteringPose, s);
        });
    }

    static String poseFor(BlockMountType type) { return type == BlockMountType.Bed ? "Sleep" : "Sit"; }

    static boolean applyMountedMovement(MovementStates states, BlockMountType type) {
        boolean bed = type == BlockMountType.Bed;
        boolean entering = states.sitting != !bed || states.sleeping != bed;
        states.sitting = !bed;
        states.sleeping = bed;
        states.falling = false;
        states.fallingFar = false;
        states.jumping = false;
        states.walking = false;
        states.running = false;
        states.sprinting = false;
        states.flying = false;
        states.swimming = false;
        states.sliding = false;
        states.crouching = false;
        states.forcedCrouching = false;
        states.onGround = true;
        states.idle = true;
        states.horizontalIdle = true;
        return entering;
    }

    static void clearMountedMovement(MovementStates states) {
        states.sitting = false;
        states.sleeping = false;
    }

    /** Also handles external/vanilla dismounts, so the next walk cannot stay seated. */
    public static final class Dismount extends RefChangeSystem<EntityStore, MountedComponent> {
        @Override public ComponentType<EntityStore, MountedComponent> componentType() { return MountedComponent.getComponentType(); }
        @Override public Query<EntityStore> getQuery() { return TownVillagerBinding.getComponentType(); }
        @Override public void onComponentAdded(Ref<EntityStore> ref, MountedComponent component, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {}
        @Override public void onComponentSet(Ref<EntityStore> ref, MountedComponent old, MountedComponent current, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
            if (old != null && current.getControllerType() != MountController.BlockMount) onComponentRemoved(ref, old, store, buffer);
        }
        @Override public void onComponentRemoved(Ref<EntityStore> ref, MountedComponent component, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
            if (component.getControllerType() != MountController.BlockMount) return;
            var movement = buffer.getComponent(ref, MovementStatesComponent.getComponentType());
            if (movement != null) {
                clearMountedMovement(movement.getMovementStates());
                buffer.putComponent(ref, MovementStatesComponent.getComponentType(), movement);
            }
        }
    }

    static boolean alignToOccupiedSeat(Ref<EntityStore> entity, BlockMountComponent seats, TransformComponent transform) {
        var point = seats.getSeatBlockBySeatedEntity(entity);
        if (point == null) return false;
        var position = point.computeWorldSpacePosition(seats.getBlockPos());
        if (transform.getPosition().distanceSquared(position) > .0001) transform.setPosition(position);
        transform.setRotation(point.computeRotationEuler(seats.getExpectedRotation()));
        return true;
    }
}
