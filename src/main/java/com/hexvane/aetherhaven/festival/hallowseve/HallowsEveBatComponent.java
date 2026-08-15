package com.hexvane.aetherhaven.festival.hallowseve;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Marker for a Hallow's Eve festival bat circling above the square. */
public final class HallowsEveBatComponent implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<HallowsEveBatComponent> CODEC =
        BuilderCodec
            .builder(HallowsEveBatComponent.class, HallowsEveBatComponent::new)
            .append(new KeyedCodec<>("TownId", Codec.STRING), (o, v) -> o.townId = v, o -> o.townId)
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, HallowsEveBatComponent> componentType;

    @Nullable
    private String townId;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType = registry.registerComponent(HallowsEveBatComponent.class, "AetherhavenHallowsEveBat", CODEC);
    }

    public static boolean isRegistered() {
        return componentType != null;
    }

    @Nonnull
    public static ComponentType<EntityStore, HallowsEveBatComponent> getComponentType() {
        ComponentType<EntityStore, HallowsEveBatComponent> type = componentType;
        if (type == null) {
            throw new IllegalStateException("HallowsEveBatComponent not registered");
        }
        return type;
    }

    @Nullable
    public UUID getTownId() {
        if (townId == null || townId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(townId.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setTownId(@Nullable UUID townId) {
        this.townId = townId != null ? townId.toString() : null;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        HallowsEveBatComponent copy = new HallowsEveBatComponent();
        copy.townId = townId;
        return copy;
    }
}
