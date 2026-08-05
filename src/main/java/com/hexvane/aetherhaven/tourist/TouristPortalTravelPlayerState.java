package com.hexvane.aetherhaven.tourist;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Tracks whether the player was standing on a portal last tick (edge-trigger UI open). */
public final class TouristPortalTravelPlayerState implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<TouristPortalTravelPlayerState> CODEC = BuilderCodec.builder(
            TouristPortalTravelPlayerState.class,
            TouristPortalTravelPlayerState::new
        )
        .append(
            new KeyedCodec<>("WasOnPortal", Codec.BOOLEAN),
            (c, v) -> c.wasOnPortal = v != null && v,
            c -> c.wasOnPortal
        )
        .add()
        .build();

    @Nullable
    private static volatile ComponentType<EntityStore, TouristPortalTravelPlayerState> componentType;

    private boolean wasOnPortal;
    private int lastProbeBlockX = Integer.MIN_VALUE;
    private int lastProbeBlockZ = Integer.MIN_VALUE;

    @Nonnull
    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType = registry.registerComponent(
            TouristPortalTravelPlayerState.class,
            "AetherhavenTouristPortalTravelPlayer",
            TouristPortalTravelPlayerState.CODEC
        );
    }

    @Nonnull
    public static ComponentType<EntityStore, TouristPortalTravelPlayerState> getComponentType() {
        ComponentType<EntityStore, TouristPortalTravelPlayerState> t = componentType;
        if (t == null) {
            throw new IllegalStateException("TouristPortalTravelPlayerState not registered");
        }
        return t;
    }

    public boolean wasOnPortal() {
        return wasOnPortal;
    }

    public void setWasOnPortal(boolean wasOnPortal) {
        this.wasOnPortal = wasOnPortal;
    }

    /** True when the player has not moved to a new block column since the last stand probe. */
    public boolean sameProbeColumn(int blockX, int blockZ) {
        return lastProbeBlockX == blockX && lastProbeBlockZ == blockZ;
    }

    public void setLastProbeBlock(int blockX, int blockZ) {
        this.lastProbeBlockX = blockX;
        this.lastProbeBlockZ = blockZ;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        TouristPortalTravelPlayerState c = new TouristPortalTravelPlayerState();
        c.wasOnPortal = this.wasOnPortal;
        return c;
    }
}
