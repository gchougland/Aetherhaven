package com.hexvane.aetherhaven.festival.market;

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

/** Interactable pad on the shared town stall. */
public final class MarketStallComponent implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<MarketStallComponent> CODEC =
        BuilderCodec
            .builder(MarketStallComponent.class, MarketStallComponent::new)
            .append(new KeyedCodec<>("TownId", Codec.STRING), (o, v) -> o.townId = v, o -> o.townId)
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, MarketStallComponent> componentType;

    @Nullable
    private String townId;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType = registry.registerComponent(MarketStallComponent.class, "AetherhavenMarketStall", CODEC);
    }

    public static boolean isRegistered() {
        return componentType != null;
    }

    @Nonnull
    public static ComponentType<EntityStore, MarketStallComponent> getComponentType() {
        ComponentType<EntityStore, MarketStallComponent> type = componentType;
        if (type == null) {
            throw new IllegalStateException("MarketStallComponent not registered");
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
        MarketStallComponent copy = new MarketStallComponent();
        copy.townId = townId;
        return copy;
    }
}
