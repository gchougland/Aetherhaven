package com.hexvane.aetherhaven.ui;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import javax.annotation.Nonnull;

/**
 * Opens the hand mirror: two ring slots, one necklace slot, and jewelry from hotbar and main
 * storage to equip.
 */
public final class OpenHandMirrorUiInteraction extends SimpleInstantInteraction {
    @Nonnull
    public static final BuilderCodec<OpenHandMirrorUiInteraction> CODEC =
        BuilderCodec.builder(OpenHandMirrorUiInteraction.class, OpenHandMirrorUiInteraction::new, SimpleInstantInteraction.CODEC)
            .documentation("Open the hand mirror to equip or unequip jewelry (two rings, one necklace).")
            .build();

    @Override
    protected void firstRun(
        @Nonnull InteractionType type, @Nonnull InteractionContext context, @Nonnull CooldownHandler cooldownHandler) {
        Ref<EntityStore> ref = context.getEntity();
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        Player player = commandBuffer.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        if (player.getPageManager().getCustomPage() != null) {
            return;
        }
        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        Store<EntityStore> store = commandBuffer.getStore();
        player.getPageManager().openCustomPage(ref, store, new HandMirrorPage(playerRef));
    }
}
