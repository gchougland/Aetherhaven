package com.hexvane.aetherhaven.villager;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** One-tick marker: the next {@link com.hypixel.hytale.server.core.modules.entity.teleport.Teleport} on this entity was issued by Aetherhaven. */
public final class AetherhavenAllowedTeleport implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<AetherhavenAllowedTeleport> CODEC =
        BuilderCodec.builder(AetherhavenAllowedTeleport.class, AetherhavenAllowedTeleport::new).build();

    @Nonnull
    public static final AetherhavenAllowedTeleport INSTANCE = new AetherhavenAllowedTeleport();

    @Nullable
    private static volatile ComponentType<EntityStore, AetherhavenAllowedTeleport> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        if (componentType != null) {
            return;
        }
        componentType =
            registry.registerComponent(AetherhavenAllowedTeleport.class, "AetherhavenAllowedTeleport", CODEC);
    }

    @Nonnull
    public static ComponentType<EntityStore, AetherhavenAllowedTeleport> getComponentType() {
        ComponentType<EntityStore, AetherhavenAllowedTeleport> t = componentType;
        if (t == null) {
            throw new IllegalStateException("AetherhavenAllowedTeleport not registered");
        }
        return t;
    }

    private AetherhavenAllowedTeleport() {}

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        return INSTANCE;
    }
}
