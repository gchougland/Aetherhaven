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
 * Hytale Update 6 made block-entity scale 1.0 the natural size (it used to be 2.0) and moved the
 * visual center onto the entity position. Prefab JSON still stores the old scales; this applies the
 * same math as Hytale's {@code PositionMigrationSystem}.
 */
public final class BlockEntityScaleMigration {
    private BlockEntityScaleMigration() {}

    /**
     * Prefab spawn: always convert old-convention {@code EntityScale} unless this clone was already
     * migrated (plot-creator re-export). Consumes Hytale's outdated-anchor flag so the engine does
     * not apply the same shift a second time on add.
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
        apply(blockEntity, scaleComponent, transform);
        holder.putComponent(BlockEntityScaleMigrated.getComponentType(), new BlockEntityScaleMigrated());
    }

    /**
     * World load repair for decorations that skipped Hytale's version-0 path (binary prefab cache).
     * Only touches scales still above 1.0 so already-migrated entities stay put.
     */
    public static void migrateLoadedIfOversized(@Nonnull Holder<EntityStore> holder) {
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
        if (scaleComponent.getScale() <= 1.0f) {
            return;
        }
        apply(blockEntity, scaleComponent, transform);
        holder.putComponent(BlockEntityScaleMigrated.getComponentType(), new BlockEntityScaleMigrated());
    }

    static void applyAnchorShift(@Nonnull Vector3d position, @Nonnull Rotation3fc rotation, float oldScale) {
        position.add(rotation.transform(new Vector3d(0.0, (oldScale / 4.0f) - 0.5, 0.0)));
        position.add(0.0, 0.5, 0.0);
    }

    private static void apply(
        @Nonnull BlockEntity blockEntity,
        @Nonnull EntityScaleComponent scaleComponent,
        @Nonnull TransformComponent transform
    ) {
        float scale = scaleComponent.getScale();
        scaleComponent.setScale(scale / 2.0f);
        applyAnchorShift(transform.getPosition(), transform.getRotation(), scale);
        blockEntity.consumeOutdatedAnchor();
    }
}
