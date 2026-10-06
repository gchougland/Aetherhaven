package com.hexvane.aetherhaven.patrol;

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

/** Tracks a hired guard escorting a player (battle horn or Follow Me dialogue). */
public final class GuardFollowPlayerState implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<GuardFollowPlayerState> CODEC =
        BuilderCodec.builder(GuardFollowPlayerState.class, GuardFollowPlayerState::new)
            .append(new KeyedCodec<>("Active", Codec.BOOLEAN), (v, x) -> v.active = x != null && x, v -> v.active)
            .add()
            .append(
                new KeyedCodec<>("PlayerUuid", Codec.STRING),
                (v, x) -> v.playerUuid = x,
                v -> v.playerUuid
            )
            .add()
            .append(new KeyedCodec<>("Stance", Codec.STRING), (v, x) -> v.stance = GuardEscortStance.fromSaved(x), v -> v.stance.name()).add()
            .append(new KeyedCodec<>("Waiting", Codec.BOOLEAN), (v, x) -> v.waiting = Boolean.TRUE.equals(x), v -> v.waiting).add()
            .append(new KeyedCodec<>("WaitX", Codec.DOUBLE), (v, x) -> v.waitX = x, v -> v.waitX).add()
            .append(new KeyedCodec<>("WaitY", Codec.DOUBLE), (v, x) -> v.waitY = x, v -> v.waitY).add()
            .append(new KeyedCodec<>("WaitZ", Codec.DOUBLE), (v, x) -> v.waitZ = x, v -> v.waitZ).add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, GuardFollowPlayerState> componentType;

    private boolean active;
    @Nullable
    private String playerUuid;
    private GuardEscortStance stance = GuardEscortStance.OFFENSIVE;
    private boolean waiting;
    private double waitX;
    private double waitY;
    private double waitZ;

    public GuardEscortStance getStance() { return stance; }
    public void setStance(@Nonnull GuardEscortStance stance) { this.stance = java.util.Objects.requireNonNull(stance); }
    public boolean isWaiting() { return active && waiting; }
    public double getWaitX() { return waitX; }
    public double getWaitY() { return waitY; }
    public double getWaitZ() { return waitZ; }

    public void waitHere(double x, double y, double z) {
        if (!active || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Waiting requires an active escort and finite position");
        }
        waiting = true;
        waitX = x;
        waitY = y;
        waitZ = z;
    }

    public void resumeFollowing() { waiting = false; }

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        if (componentType != null) {
            return;
        }
        componentType =
            registry.registerComponent(
                GuardFollowPlayerState.class,
                "AetherhavenGuardFollowPlayer",
                GuardFollowPlayerState.CODEC
            );
    }

    @Nonnull
    public static ComponentType<EntityStore, GuardFollowPlayerState> getComponentType() {
        ComponentType<EntityStore, GuardFollowPlayerState> t = componentType;
        if (t == null) {
            throw new IllegalStateException("GuardFollowPlayerState not registered");
        }
        return t;
    }

    public boolean isActive() {
        return active;
    }

    @Nullable
    public UUID getPlayerUuid() {
        if (playerUuid == null || playerUuid.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(playerUuid.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void startFollowing(@Nonnull UUID playerId) {
        this.active = true;
        this.playerUuid = playerId.toString();
        this.waiting = false;
    }

    public void clear() {
        this.active = false;
        this.playerUuid = null;
        this.waiting = false;
    }

    public boolean isFollowing(@Nonnull UUID playerId) {
        return active && playerId.equals(getPlayerUuid());
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        GuardFollowPlayerState c = new GuardFollowPlayerState();
        c.active = active;
        c.playerUuid = playerUuid;
        c.stance = stance;
        c.waiting = waiting;
        c.waitX = waitX;
        c.waitY = waitY;
        c.waitZ = waitZ;
        return c;
    }
}
