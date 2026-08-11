package com.hexvane.aetherhaven.autonomy;

import com.hypixel.hytale.builtin.mounts.BlockMountAPI;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * {@link BlockMountAPI#mountOnBlock} queues {@link MountedComponent} via {@link CommandBuffer}; until the buffer is
 * consumed, {@code getComponent} still sees an unmounted entity. A second mount in the same tick then queues another
 * {@code addComponent} and the world thread dies with "Entity already contains component type: MountedComponent".
 *
 * <p>Track successful mounts for the current world tick so callers can safely retry / ensure-mount.
 */
public final class SafeBlockMount {
    private static final ThreadLocal<Long> TICK = new ThreadLocal<>();
    private static final ThreadLocal<Set<UUID>> PENDING = ThreadLocal.withInitial(HashSet::new);

    private SafeBlockMount() {}

    public static boolean isMountedOrPending(
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> entity
    ) {
        if (store.getComponent(entity, MountedComponent.getComponentType()) != null
            || commandBuffer.getComponent(entity, MountedComponent.getComponentType()) != null) {
            UUID uuid = entityUuid(store, commandBuffer, entity);
            if (uuid != null) {
                pendingFor(store).remove(uuid);
            }
            return true;
        }
        UUID uuid = entityUuid(store, commandBuffer, entity);
        return uuid != null && pendingFor(store).contains(uuid);
    }

    @Nonnull
    public static BlockMountAPI.BlockMountResult mountOnBlock(
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> entity,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Vector3i targetBlock,
        @Nonnull Vector3d interactPos
    ) {
        if (isMountedOrPending(store, commandBuffer, entity)) {
            return BlockMountAPI.DidNotMount.ALREADY_MOUNTED;
        }
        BlockMountAPI.BlockMountResult result =
            BlockMountAPI.mountOnBlock(entity, commandBuffer, targetBlock, interactPos);
        if (result instanceof BlockMountAPI.Mounted) {
            UUID uuid = entityUuid(store, commandBuffer, entity);
            if (uuid != null) {
                pendingFor(store).add(uuid);
            }
        }
        return result;
    }

    @Nonnull
    private static Set<UUID> pendingFor(@Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        long tick = world != null ? world.getTick() : -1L;
        Long prev = TICK.get();
        if (prev == null || prev.longValue() != tick) {
            TICK.set(tick);
            PENDING.get().clear();
        }
        return PENDING.get();
    }

    @Nullable
    private static UUID entityUuid(
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> entity
    ) {
        UUIDComponent uuid = commandBuffer.getComponent(entity, UUIDComponent.getComponentType());
        if (uuid == null) {
            uuid = store.getComponent(entity, UUIDComponent.getComponentType());
        }
        return uuid != null ? uuid.getUuid() : null;
    }
}
