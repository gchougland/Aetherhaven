package com.hexvane.aetherhaven.villager;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Short debug handle for {@code /aetherhaven needs} (typed in chat; avoids copying UUIDs).
 * Example: {@code Villager_Elder_a1b2c3d} for a town's elder.
 */
public final class AetherhavenVillagerHandle implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<AetherhavenVillagerHandle> CODEC =
        BuilderCodec.builder(AetherhavenVillagerHandle.class, AetherhavenVillagerHandle::new)
            .append(new KeyedCodec<>("Handle", Codec.STRING), (h, v) -> h.handle = v != null ? v : "", h -> h.handle)
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, AetherhavenVillagerHandle> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType = registry.registerComponent(AetherhavenVillagerHandle.class, "AetherhavenVillagerHandle", CODEC);
    }

    @Nonnull
    public static ComponentType<EntityStore, AetherhavenVillagerHandle> getComponentType() {
        ComponentType<EntityStore, AetherhavenVillagerHandle> t = componentType;
        if (t == null) {
            throw new IllegalStateException("AetherhavenVillagerHandle not registered");
        }
        return t;
    }

    private String handle = "";

    public AetherhavenVillagerHandle() {}

    public AetherhavenVillagerHandle(@Nonnull String handle) {
        this.handle = handle != null ? handle : "";
    }

    @Nonnull
    public String getHandle() {
        return handle;
    }

    public void setHandle(@Nonnull String handle) {
        this.handle = handle != null ? handle : "";
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        return new AetherhavenVillagerHandle(handle);
    }
}
