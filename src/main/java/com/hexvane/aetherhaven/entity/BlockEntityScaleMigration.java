package com.hexvane.aetherhaven.entity;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.math.vector.Rotation3fc;
import com.hypixel.hytale.server.core.entity.entities.BlockEntity;
import com.hypixel.hytale.server.core.modules.entity.component.EntityScaleComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * Migrate only entities explicitly flagged by Hytale's versioned BlockEntity codec.
 * Scale itself is not a version: both small and large decorations can be intentional.
 */
public final class BlockEntityScaleMigration {
    private BlockEntityScaleMigration() {}

    /**
     * Consume the same legacy flag as the engine, so either spawn path can run first safely.
     */
    public static void migratePrefabSpawn(@Nonnull Holder<EntityStore> holder) {
        if (!BlockEntityScaleMigrated.isRegistered()) {
            return;
        }
        if (holder.getComponent(BlockEntityScaleMigrated.getComponentType()) != null) {
            return;
        }
        BlockEntity blockEntity = holder.getComponent(BlockEntity.getComponentType());
        EntityScaleComponent scaleComponent = holder.getComponent(EntityScaleComponent.getComponentType());
        TransformComponent transform = holder.getComponent(TransformComponent.getComponentType());
        if (blockEntity == null || scaleComponent == null || transform == null) {
            return;
        }
        if (migrateLegacyAnchor(blockEntity, scaleComponent, transform))
            holder.putComponent(BlockEntityScaleMigrated.getComponentType(), new BlockEntityScaleMigrated());
    }

    /**
     * No scale threshold is safe here. The engine may already have converted a large decoration.
     */
    public static void migrateLoadedLegacyAnchor(@Nonnull Holder<EntityStore> holder) {
        if (!BlockEntityScaleMigrated.isRegistered()) {
            return;
        }
        if (holder.getComponent(BlockEntityScaleMigrated.getComponentType()) != null) {
            return;
        }
        BlockEntity blockEntity = holder.getComponent(BlockEntity.getComponentType());
        EntityScaleComponent scaleComponent = holder.getComponent(EntityScaleComponent.getComponentType());
        TransformComponent transform = holder.getComponent(TransformComponent.getComponentType());
        if (blockEntity == null || scaleComponent == null || transform == null) {
            return;
        }
        if (migrateLegacyAnchor(blockEntity, scaleComponent, transform))
            holder.putComponent(BlockEntityScaleMigrated.getComponentType(), new BlockEntityScaleMigrated());
    }

    static void applyAnchorShift(@Nonnull Vector3d position, @Nonnull Rotation3fc rotation, float oldScale) {
        position.add(rotation.transform(new Vector3d(0.0, (oldScale / 4.0f) - 0.5, 0.0)));
        position.add(0.0, 0.5, 0.0);
    }

    static boolean migrateLegacyAnchor(
        @Nonnull BlockEntity blockEntity,
        @Nonnull EntityScaleComponent scaleComponent,
        @Nonnull TransformComponent transform
    ) {
        if (!blockEntity.consumeOutdatedAnchor()) return false;
        float scale = scaleComponent.getScale();
        scaleComponent.setScale(scale / 2.0f);
        applyAnchorShift(transform.getPosition(), transform.getRotation(), scale);
        return true;
    }
}
