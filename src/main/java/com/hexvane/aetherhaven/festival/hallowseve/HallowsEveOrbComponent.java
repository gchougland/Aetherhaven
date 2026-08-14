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

/** Marker for a glowing maze orb. */
public final class HallowsEveOrbComponent implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<HallowsEveOrbComponent> CODEC =
        BuilderCodec
            .builder(HallowsEveOrbComponent.class, HallowsEveOrbComponent::new)
            .append(new KeyedCodec<>("TownId", Codec.STRING), (o, v) -> o.townId = v, o -> o.townId)
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, HallowsEveOrbComponent> componentType;

    @Nullable
    private String townId;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType = registry.registerComponent(HallowsEveOrbComponent.class, "AetherhavenHallowsEveOrb", CODEC);
    }

    public static boolean isRegistered() {
        return componentType != null;
    }

    @Nonnull
    public static ComponentType<EntityStore, HallowsEveOrbComponent> getComponentType() {
        ComponentType<EntityStore, HallowsEveOrbComponent> type = componentType;
        if (type == null) {
            throw new IllegalStateException("HallowsEveOrbComponent not registered");
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
        HallowsEveOrbComponent copy = new HallowsEveOrbComponent();
        copy.townId = townId;
        return copy;
    }
}
