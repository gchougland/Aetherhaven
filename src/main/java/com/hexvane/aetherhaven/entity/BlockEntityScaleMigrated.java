package com.hexvane.aetherhaven.entity;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;

/**
 * Marks a block entity whose {@code EntityScale} was converted to Hytale Update 6 units
 * (1.0 is natural size). Prevents a second halve on plot-creator export and re-place.
 */
public final class BlockEntityScaleMigrated implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<BlockEntityScaleMigrated> CODEC =
        BuilderCodec.builder(BlockEntityScaleMigrated.class, BlockEntityScaleMigrated::new).build();

    private static volatile ComponentType<EntityStore, BlockEntityScaleMigrated> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType =
            registry.registerComponent(
                BlockEntityScaleMigrated.class,
                "AetherhavenBlockEntityScaleMigrated",
                CODEC
            );
    }

    public static boolean isRegistered() {
        return componentType != null;
    }

    @Nonnull
    public static ComponentType<EntityStore, BlockEntityScaleMigrated> getComponentType() {
        ComponentType<EntityStore, BlockEntityScaleMigrated> t = componentType;
        if (t == null) {
            throw new IllegalStateException("BlockEntityScaleMigrated not registered");
        }
        return t;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        return new BlockEntityScaleMigrated();
    }
}
