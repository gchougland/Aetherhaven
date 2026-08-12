package com.hexvane.aetherhaven.festival.firework;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Marker + fuse state for a launched firework rocket entity. */
public final class FireworkRocketComponent implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<FireworkRocketComponent> CODEC =
        BuilderCodec
            .builder(FireworkRocketComponent.class, FireworkRocketComponent::new)
            .append(new KeyedCodec<>("LifeSeconds", Codec.FLOAT), (o, v) -> o.lifeSeconds = v, o -> o.lifeSeconds)
            .add()
            .append(new KeyedCodec<>("FuseSeconds", Codec.FLOAT), (o, v) -> o.fuseSeconds = v, o -> o.fuseSeconds)
            .add()
            .append(new KeyedCodec<>("TrailAccumSeconds", Codec.FLOAT), (o, v) -> o.trailAccumSeconds = v, o -> o.trailAccumSeconds)
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, FireworkRocketComponent> componentType;

    private float lifeSeconds;
    private float fuseSeconds = FireworkIds.FUSE_MIN_SECONDS;
    private float trailAccumSeconds;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType = registry.registerComponent(FireworkRocketComponent.class, "AetherhavenFireworkRocket", CODEC);
    }

    public static boolean isRegistered() {
        return componentType != null;
    }

    @Nonnull
    public static ComponentType<EntityStore, FireworkRocketComponent> getComponentType() {
        ComponentType<EntityStore, FireworkRocketComponent> type = componentType;
        if (type == null) {
            throw new IllegalStateException("FireworkRocketComponent not registered");
        }
        return type;
    }

    public float getLifeSeconds() {
        return lifeSeconds;
    }

    public void addLifeSeconds(float dt) {
        lifeSeconds += dt;
    }

    public float getFuseSeconds() {
        return fuseSeconds;
    }

    public void setFuseSeconds(float fuseSeconds) {
        this.fuseSeconds = fuseSeconds;
    }

    public boolean isReadyToExplode() {
        return lifeSeconds >= fuseSeconds;
    }

    /** Returns true when a trail puff should spawn this tick. */
    public boolean consumeTrailInterval(float dt, float intervalSeconds) {
        trailAccumSeconds += dt;
        if (trailAccumSeconds < intervalSeconds) {
            return false;
        }
        trailAccumSeconds = 0.0f;
        return true;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        FireworkRocketComponent copy = new FireworkRocketComponent();
        copy.lifeSeconds = lifeSeconds;
        copy.fuseSeconds = fuseSeconds;
        copy.trailAccumSeconds = trailAccumSeconds;
        return copy;
    }
}
