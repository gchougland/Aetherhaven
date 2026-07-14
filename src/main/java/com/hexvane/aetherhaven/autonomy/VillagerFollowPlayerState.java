package com.hexvane.aetherhaven.autonomy;

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

/** Tracks a town villager escorting a player (Follow Me dialogue). */
public final class VillagerFollowPlayerState implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<VillagerFollowPlayerState> CODEC =
        BuilderCodec.builder(VillagerFollowPlayerState.class, VillagerFollowPlayerState::new)
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
    private static volatile ComponentType<EntityStore, VillagerFollowPlayerState> componentType;

    private boolean active;
    @Nullable
    private String playerUuid;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        if (componentType != null) {
            return;
        }
        componentType =
            registry.registerComponent(
                VillagerFollowPlayerState.class,
                "AetherhavenVillagerFollowPlayer",
                VillagerFollowPlayerState.CODEC
            );
    }

    @Nonnull
    public static ComponentType<EntityStore, VillagerFollowPlayerState> getComponentType() {
        ComponentType<EntityStore, VillagerFollowPlayerState> t = componentType;
        if (t == null) {
            throw new IllegalStateException("VillagerFollowPlayerState not registered");
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
        VillagerFollowPlayerState c = new VillagerFollowPlayerState();
        c.active = active;
        c.playerUuid = playerUuid;
        return c;
    }
}
