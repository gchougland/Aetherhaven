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
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, GuardFollowPlayerState> componentType;

    private boolean active;
    @Nullable
    private String playerUuid;

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
    }

    public void clear() {
        this.active = false;
        this.playerUuid = null;
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
        return c;
    }
}
