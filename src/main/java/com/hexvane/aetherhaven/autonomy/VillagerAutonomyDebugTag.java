package com.hexvane.aetherhaven.autonomy;

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
 * Marker: this town villager should show Aetherhaven autonomy debug (role DisplayCustom string + VisPath). Presence
 * on the entity is the source of truth (not a UUID map), so the command and {@link VillagerAutonomySystem} always agree.
 */
public final class VillagerAutonomyDebugTag implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<VillagerAutonomyDebugTag> CODEC =
        BuilderCodec.builder(VillagerAutonomyDebugTag.class, VillagerAutonomyDebugTag::new)
            .append(new KeyedCodec<>("_", Codec.BYTE), (t, v) -> {}, t -> (byte) 0)
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, VillagerAutonomyDebugTag> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType =
            registry.registerComponent(
                VillagerAutonomyDebugTag.class,
                "AetherhavenVillagerAutonomyDebugTag",
                CODEC
            );
    }

    @Nonnull
    public static ComponentType<EntityStore, VillagerAutonomyDebugTag> getComponentType() {
        ComponentType<EntityStore, VillagerAutonomyDebugTag> t = componentType;
        if (t == null) {
            throw new IllegalStateException("VillagerAutonomyDebugTag not registered");
        }
        return t;
    }

    public VillagerAutonomyDebugTag() {}

    @Override
    public Component<EntityStore> clone() {
        return new VillagerAutonomyDebugTag();
    }
}
