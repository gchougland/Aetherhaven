package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.npc.NpcStandStill;
import com.hypixel.hytale.builtin.mounts.BlockMountComponent;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.protocol.MountController;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import org.joml.Vector3d;
import org.joml.Vector3i;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** A seat stays claimed until its occupant can be detached onto clear floor. */
public final class VillagerSeatExit implements Component<EntityStore> {
    private static ComponentType<EntityStore, VillagerSeatExit> type;
    private MountedComponent mount;
    private Vector3d origin;
    private int floorY;
    private long retryAfterMs;

    public static void register(ComponentRegistryProxy<EntityStore> registry) {
        type = registry.registerComponent(VillagerSeatExit.class, VillagerSeatExit::new);
    }
    public static ComponentType<EntityStore, VillagerSeatExit> getComponentType() { return type; }
    @Override public Component<EntityStore> clone() {
        var copy = new VillagerSeatExit();
        copy.mount = mount;
        copy.origin = origin == null ? null : new Vector3d(origin);
        copy.floorY = floorY;
        copy.retryAfterMs = retryAfterMs;
        return copy;
    }

    public static void request(Ref<EntityStore> ref, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        // Resolve after earlier queued mounts. Detach + move + release run without
        // another NPC claiming the seat between those operations.
        if (buffer != null) { buffer.run(s -> request(ref, s, null)); return; }
        if (!ref.isValid()) return;
        var pending = store.getComponent(ref, type);
        if (pending == null) {
            var mounted = store.getComponent(ref, MountedComponent.getComponentType());
            if (mounted == null || mounted.getControllerType() != MountController.BlockMount) return;
            var transform = store.getComponent(ref, TransformComponent.getComponentType());
            if (transform == null) return;
            pending = new VillagerSeatExit();
            pending.mount = mounted;
            pending.origin = new Vector3d(transform.getPosition());
            pending.floorY = (int) Math.floor(pending.origin.y);
            var block = mounted.getMountedToBlock();
            var seats = block != null && block.isValid()
                ? block.getStore().getComponent(block, BlockMountComponent.getComponentType()) : null;
            if (seats != null) {
                pending.floorY = seats.getBlockPos().y;
                var point = seats.getSeatBlockBySeatedEntity(ref);
                if (point != null) pending.origin = point.computeWorldSpacePosition(seats.getBlockPos());
            }
            store.putComponent(ref, type, pending);
        }
        tryFinish(ref, store, pending);
    }

    /** Called outside ECS processing, including the final mounted-pose callback. */
    static boolean tryFinish(Ref<EntityStore> ref, Store<EntityStore> store, VillagerSeatExit pending) {
        var mounted = store.getComponent(ref, MountedComponent.getComponentType());
        if (mounted != null && mounted != pending.mount) {
            store.tryRemoveComponent(ref, type); // Never dismantle a different/new mount.
            return true;
        }
        long now = System.currentTimeMillis();
        if (now < pending.retryAfterMs) return false;
        pending.retryAfterMs = now + 500;
        var world = store.getExternalData().getWorld();
        var others = new ArrayList<Vector3d>();
        store.forEachChunk(Query.and(TransformComponent.getComponentType(),
            Query.or(NPCEntity.getComponentType(), Player.getComponentType())), (chunk, ignored) -> {
            for (int i = 0; i < chunk.size(); i++) {
                if (chunk.getReferenceTo(i).equals(ref)) continue;
                var position = chunk.getComponent(i, TransformComponent.getComponentType()).getPosition();
                if (position.distanceSquared(pending.origin) < 100) others.add(new Vector3d(position));
            }
        });
        Vector3d exit = findExit(pending.origin, pending.floorY, cell ->
            VillagerBlockUtil.isNpcStandColumn(world, cell.x, cell.y, cell.z)
                && !VillagerBlockUtil.isFurnitureMountPoi(world, cell.x, cell.y - 1, cell.z), others);
        if (exit == null) return false; // Stay seated until a floor spot opens; never stack on the bench.
        exit.y = VillagerBlockUtil.resolveFeetYForStandCell(world, (int) Math.floor(exit.x),
            (int) Math.floor(exit.y), (int) Math.floor(exit.z));
        var transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) return false;

        BlockMountRelease.release(ref, store, null);
        transform.setPosition(exit);
        store.putComponent(ref, TransformComponent.getComponentType(), transform);
        var movement = store.getComponent(ref, MovementStatesComponent.getComponentType());
        if (movement != null) VillagerMountedPoseSystem.clearMountedMovement(movement.getMovementStates());
        NpcStandStill.forceIdleMovementStates(store, ref);
        var velocity = store.getComponent(ref, Velocity.getComponentType());
        if (velocity != null) velocity.setZero();
        var npc = store.getComponent(ref, NPCEntity.getComponentType());
        if (npc != null) {
            npc.setLeashPoint(new Vector3d(exit));
            NpcStandStill.release(ref, npc, store);
            npc.playAnimation(ref, AnimationSlot.Status, null, true, store);
            npc.playAnimation(ref, AnimationSlot.Movement, null, true, store);
        }
        store.tryRemoveComponent(ref, type);
        return true;
    }

    static Vector3d findExit(Vector3d origin, int floorY, Predicate<Vector3i> clearFloor, List<Vector3d> occupants) {
        Vector3d best = null;
        double score = Double.POSITIVE_INFINITY;
        int ox = (int) Math.floor(origin.x), oz = (int) Math.floor(origin.z);
        for (int dy : new int[]{0, -1, 1}) {
            for (int dx = -4; dx <= 4; dx++) for (int dz = -4; dz <= 4; dz++) {
                var cell = new Vector3i(ox + dx, floorY + dy, oz + dz);
                var point = new Vector3d(cell.x + .5, cell.y + .02, cell.z + .5);
                // Leave enough room for the next occupant of the newly freed seat.
                if (!clearOfOccupants(point, List.of(origin))) continue;
                double distance = point.distanceSquared(origin) + Math.abs(dy) * 4;
                if (distance >= score || !clearFloor.test(cell) || !clearOfOccupants(point, occupants)) continue;
                score = distance;
                best = point;
            }
        }
        return best;
    }

    static boolean clearOfOccupants(Vector3d point, List<Vector3d> occupants) {
        for (var other : occupants) {
            double dx = point.x - other.x, dz = point.z - other.z;
            if (Math.abs(point.y - other.y) < 1.8 && dx * dx + dz * dz < .9 * .9) return false;
        }
        return true;
    }
}
