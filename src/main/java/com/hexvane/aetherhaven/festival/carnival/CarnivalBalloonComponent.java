package com.hexvane.aetherhaven.festival.carnival;

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

/** Marker + motion state for a carnival balloon floater. */
public final class CarnivalBalloonComponent implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<CarnivalBalloonComponent> CODEC =
        BuilderCodec
            .builder(CarnivalBalloonComponent.class, CarnivalBalloonComponent::new)
            .append(new KeyedCodec<>("TownId", Codec.STRING), (o, v) -> o.townId = v, o -> o.townId)
            .add()
            .append(new KeyedCodec<>("LifeSeconds", Codec.FLOAT), (o, v) -> o.lifeSeconds = v, o -> o.lifeSeconds)
            .add()
            .append(new KeyedCodec<>("MaxLifeSeconds", Codec.FLOAT), (o, v) -> o.maxLifeSeconds = v, o -> o.maxLifeSeconds)
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, CarnivalBalloonComponent> componentType;

    @Nullable
    private String townId;
    private float lifeSeconds;
    private float maxLifeSeconds = CarnivalIds.BALLOON_FLOAT_SECONDS;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType = registry.registerComponent(CarnivalBalloonComponent.class, "AetherhavenCarnivalBalloon", CODEC);
    }

    public static boolean isRegistered() {
        return componentType != null;
    }

    @Nonnull
    public static ComponentType<EntityStore, CarnivalBalloonComponent> getComponentType() {
        ComponentType<EntityStore, CarnivalBalloonComponent> type = componentType;
        if (type == null) {
            throw new IllegalStateException("CarnivalBalloonComponent not registered");
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

    public float getLifeSeconds() {
        return lifeSeconds;
    }

    public void addLifeSeconds(float dt) {
        lifeSeconds += dt;
    }

    public float getMaxLifeSeconds() {
        return maxLifeSeconds;
    }

    public void setMaxLifeSeconds(float maxLifeSeconds) {
        this.maxLifeSeconds = maxLifeSeconds;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        CarnivalBalloonComponent copy = new CarnivalBalloonComponent();
        copy.townId = townId;
        copy.lifeSeconds = lifeSeconds;
        copy.maxLifeSeconds = maxLifeSeconds;
        return copy;
    }
}
