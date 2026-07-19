package com.hexvane.aetherhaven.worldnpc;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Links an NPC to a stable world placement (not a town). */
public final class WorldNpcBinding implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<WorldNpcBinding> CODEC =
        BuilderCodec.builder(WorldNpcBinding.class, WorldNpcBinding::new)
            .append(
                new KeyedCodec<>("PlacementId", Codec.STRING),
                (b, v) -> b.placementId = v != null ? v : "",
                b -> b.placementId
            )
            .add()
            .append(
                new KeyedCodec<>("NpcRoleId", Codec.STRING),
                (b, v) -> b.npcRoleId = v != null ? v : "",
                b -> b.npcRoleId
            )
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, WorldNpcBinding> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        if (componentType != null) {
            return;
        }
        componentType = registry.registerComponent(WorldNpcBinding.class, "AetherhavenWorldNpcBinding", CODEC);
    }

    @Nonnull
    public static ComponentType<EntityStore, WorldNpcBinding> getComponentType() {
        ComponentType<EntityStore, WorldNpcBinding> t = componentType;
        if (t == null) {
            throw new IllegalStateException("WorldNpcBinding not registered");
        }
        return t;
    }

    private String placementId = "";
    private String npcRoleId = "";

    public WorldNpcBinding() {}

    public WorldNpcBinding(@Nonnull String placementId, @Nonnull String npcRoleId) {
        this.placementId = placementId.trim();
        this.npcRoleId = npcRoleId.trim();
    }

    @Nonnull
    public String getPlacementId() {
        return placementId != null ? placementId : "";
    }

    @Nonnull
    public String getNpcRoleId() {
        return npcRoleId != null ? npcRoleId : "";
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        return new WorldNpcBinding(getPlacementId(), getNpcRoleId());
    }
}
