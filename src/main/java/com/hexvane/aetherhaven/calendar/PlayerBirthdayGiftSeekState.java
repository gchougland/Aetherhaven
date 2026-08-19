package com.hexvane.aetherhaven.calendar;

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

/** A villager walking to a player to deliver a birthday gift. */
public final class PlayerBirthdayGiftSeekState implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<PlayerBirthdayGiftSeekState> CODEC =
        BuilderCodec.builder(PlayerBirthdayGiftSeekState.class, PlayerBirthdayGiftSeekState::new)
            .append(new KeyedCodec<>("Active", Codec.BOOLEAN), (v, x) -> v.active = x != null && x, v -> v.active)
            .add()
            .append(
                new KeyedCodec<>("PlayerUuid", Codec.STRING),
                (v, x) -> v.playerUuid = x,
                v -> v.playerUuid
            )
            .add()
            .append(
                new KeyedCodec<>("DialogueOpened", Codec.BOOLEAN),
                (v, x) -> v.dialogueOpened = x != null && x,
                v -> v.dialogueOpened
            )
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, PlayerBirthdayGiftSeekState> componentType;

    private boolean active;
    @Nullable
    private String playerUuid;
    private boolean dialogueOpened;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        if (componentType != null) {
            return;
        }
        componentType =
            registry.registerComponent(
                PlayerBirthdayGiftSeekState.class,
                "AetherhavenPlayerBirthdayGiftSeek",
                PlayerBirthdayGiftSeekState.CODEC
            );
    }

    public static boolean isRegistered() {
        return componentType != null;
    }

    @Nonnull
    public static ComponentType<EntityStore, PlayerBirthdayGiftSeekState> getComponentType() {
        ComponentType<EntityStore, PlayerBirthdayGiftSeekState> t = componentType;
        if (t == null) {
            throw new IllegalStateException("PlayerBirthdayGiftSeekState not registered");
        }
        return t;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isDialogueOpened() {
        return dialogueOpened;
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

    public void start(@Nonnull UUID playerId) {
        this.active = true;
        this.playerUuid = playerId.toString();
        this.dialogueOpened = false;
    }

    public void markDialogueOpened() {
        this.dialogueOpened = true;
    }

    public void clear() {
        this.active = false;
        this.playerUuid = null;
        this.dialogueOpened = false;
    }

    @Nonnull
    @Override
    public Component<EntityStore> clone() {
        PlayerBirthdayGiftSeekState c = new PlayerBirthdayGiftSeekState();
        c.active = active;
        c.playerUuid = playerUuid;
        c.dialogueOpened = dialogueOpened;
        return c;
    }
}
