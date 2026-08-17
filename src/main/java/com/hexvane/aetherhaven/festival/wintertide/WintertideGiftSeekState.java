package com.hexvane.aetherhaven.festival.wintertide;

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

/** A villager walking to a player to deliver their Wintertide gift. */
public final class WintertideGiftSeekState implements Component<EntityStore> {
    @Nonnull
    public static final BuilderCodec<WintertideGiftSeekState> CODEC =
        BuilderCodec.builder(WintertideGiftSeekState.class, WintertideGiftSeekState::new)
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
    private static volatile ComponentType<EntityStore, WintertideGiftSeekState> componentType;

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
                WintertideGiftSeekState.class,
                "AetherhavenWintertideGiftSeek",
                WintertideGiftSeekState.CODEC
            );
    }

    public static boolean isRegistered() {
        return componentType != null;
    }

    @Nonnull
    public static ComponentType<EntityStore, WintertideGiftSeekState> getComponentType() {
        ComponentType<EntityStore, WintertideGiftSeekState> t = componentType;
        if (t == null) {
            throw new IllegalStateException("WintertideGiftSeekState not registered");
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
        WintertideGiftSeekState c = new WintertideGiftSeekState();
        c.active = active;
        c.playerUuid = playerUuid;
        c.dialogueOpened = dialogueOpened;
        return c;
    }
}
