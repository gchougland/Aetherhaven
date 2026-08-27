package com.hexvane.aetherhaven.entity;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3fc;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3dc;

/** Helpers for updating {@link TransformComponent} without dropping chunk linkage. */
public final class TransformComponentUtil {
    private TransformComponentUtil() {}

    public static void replacePreservingChunk(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Vector3dc position,
        @Nonnull Rotation3fc rotation
    ) {
        putPreservingChunk(ref, commandBuffer, commandBuffer.getComponent(ref, TransformComponent.getComponentType()), position, rotation);
    }

    public static void replacePreservingChunk(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Vector3dc position,
        @Nonnull Rotation3fc rotation
    ) {
        TransformComponent transform = commandBuffer.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            transform = store.getComponent(ref, TransformComponent.getComponentType());
        }
        putPreservingChunk(ref, commandBuffer, transform, position, rotation);
    }

    /** World-thread Store write. Do not call from a ticking system. */
    public static void replacePreservingChunk(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3dc position,
        @Nonnull Rotation3fc rotation
    ) {
        if (!ref.isValid()) {
            return;
        }
        TransformComponent current = store.getComponent(ref, TransformComponent.getComponentType());
        if (current == null) {
            return;
        }
        Ref<ChunkStore> sectionRef = current.getSectionRef();
        TransformComponent updated = new TransformComponent(position, rotation);
        if (sectionRef != null) {
            updated.setSectionLocation(sectionRef);
        }
        store.putComponent(ref, TransformComponent.getComponentType(), updated);
    }

    private static void putPreservingChunk(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nullable TransformComponent current,
        @Nonnull Vector3dc position,
        @Nonnull Rotation3fc rotation
    ) {
        if (current == null) {
            return;
        }
        Ref<ChunkStore> sectionRef = current.getSectionRef();
        TransformComponent updated = new TransformComponent(position, rotation);
        if (sectionRef != null) {
            updated.setSectionLocation(sectionRef);
        }
        commandBuffer.putComponent(ref, TransformComponent.getComponentType(), updated);
    }
}
