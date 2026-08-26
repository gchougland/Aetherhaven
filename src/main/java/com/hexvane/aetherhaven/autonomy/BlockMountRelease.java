package com.hexvane.aetherhaven.autonomy;

import com.hypixel.hytale.builtin.mounts.BlockMountComponent;
import com.hypixel.hytale.builtin.mounts.MountedComponent;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.MountController;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Clears {@link BlockMountComponent} seated-entity refs before {@link MountedComponent} is dropped or the entity is
 * removed. Vanilla {@code MountSystems.RemoveMounted} can miss block cleanup when {@code MountedComponent} was already
 * queued for removal, leaving invalid refs that crash chunk save. During chunk unload it can also call
 * {@code ChunkStore.removeComponent} while the chunk store is processing and throw {@link IllegalStateException}.
 */
public final class BlockMountRelease {
    private BlockMountRelease() {}

    public static void release(
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull Store<EntityStore> store,
        @Nullable CommandBuffer<EntityStore> commandBuffer
    ) {
        MountedComponent mounted = store.getComponent(entityRef, MountedComponent.getComponentType());
        if (mounted != null && mounted.getControllerType() == MountController.BlockMount) {
            Ref<ChunkStore> deadSeatRef = clearBlockSeatSync(entityRef, mounted);
            if (deadSeatRef != null) {
                scheduleDeadSeatRemoval(deadSeatRef, store.getExternalData().getWorld());
            }
        }
        if (commandBuffer != null) {
            commandBuffer.tryRemoveComponent(entityRef, MountedComponent.getComponentType());
        } else {
            store.tryRemoveComponent(entityRef, MountedComponent.getComponentType());
        }
    }

    /**
     * Clears block-seat refs and replaces {@link MountedComponent} with a disconnected stub via
     * {@link Store#replaceComponent}, which triggers {@code onComponentSet} (not {@code onComponentRemoved}) on vanilla
     * mount systems. Safe to call from chunk unload before {@code EntityChunkLoadingSystem} removes entities.
     *
     * @return block mount ref that became empty, if any (queue removal on a chunk {@link CommandBuffer})
     */
    @Nullable
    public static Ref<ChunkStore> disconnectForUnload(
        @Nonnull Ref<EntityStore> entityRef,
        @Nonnull Store<EntityStore> store
    ) {
        MountedComponent mounted = store.getComponent(entityRef, MountedComponent.getComponentType());
        if (mounted == null) {
            return null;
        }
        Ref<ChunkStore> deadSeatRef = clearBlockSeatSync(entityRef, mounted);
        MountedComponent stub =
            new MountedComponent(null, new org.joml.Vector3f(mounted.getAttachmentOffset()), MountController.Minecart);
        store.replaceComponent(entityRef, MountedComponent.getComponentType(), stub);
        return deadSeatRef;
    }

    @Nullable
    static Ref<ChunkStore> clearBlockSeatSync(
        @Nonnull Ref<EntityStore> entityRef, @Nonnull MountedComponent mounted) {
        if (mounted.getControllerType() != MountController.BlockMount) {
            return null;
        }
        Ref<ChunkStore> blockRef = mounted.getMountedToBlock();
        if (blockRef == null || !blockRef.isValid()) {
            return null;
        }
        Store<ChunkStore> chunkStore = blockRef.getStore();
        BlockMountComponent seat = chunkStore.getComponent(blockRef, BlockMountComponent.getComponentType());
        if (seat == null) {
            return null;
        }
        seat.removeSeatedEntity(entityRef);
        return seat.isDead() ? blockRef : null;
    }

    private static void scheduleDeadSeatRemoval(@Nonnull Ref<ChunkStore> blockRef, @Nonnull World world) {
        world.execute(
            () -> {
                if (!blockRef.isValid()) {
                    return;
                }
                Store<ChunkStore> chunkStore = blockRef.getStore();
                BlockMountComponent seat =
                    chunkStore.getComponent(blockRef, BlockMountComponent.getComponentType());
                if (seat != null && seat.isDead()) {
                    chunkStore.tryRemoveComponent(blockRef, BlockMountComponent.getComponentType());
                }
            });
    }
}
