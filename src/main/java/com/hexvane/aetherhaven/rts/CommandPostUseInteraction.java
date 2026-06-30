package com.hexvane.aetherhaven.rts;

import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.plugin.SubpluginInteractionGuard;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class CommandPostUseInteraction extends SimpleBlockInteraction {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<CommandPostUseInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(CommandPostUseInteraction.class, CommandPostUseInteraction::new, SimpleBlockInteraction.CODEC)
            .documentation("Command post: enter or exit RTS guard command mode.")
            .build();

    @Override
    protected void interactWithBlock(
        @Nonnull World world,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionType type,
        @Nonnull InteractionContext context,
        @Nullable ItemStack itemInHand,
        @Nonnull Vector3i targetBlock,
        @Nonnull CooldownHandler cooldownHandler
    ) {
        if (SubpluginInteractionGuard.failIfDisabled(context, AetherhavenPluginIds.RTS)) {
            return;
        }
        if (type != InteractionType.Use) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Ref<EntityStore> playerRef = context.getEntity();
        if (playerRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (!CommandPostBlockUtil.isCommandPostBlock(world, targetBlock)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        RtsInteractions.handleCommandPostUse(playerRef, commandBuffer, targetBlock, commandBuffer.getStore());
    }

    @Override
    protected void simulateInteractWithBlock(
        @Nonnull InteractionType interactionType,
        @Nonnull InteractionContext interactionContext,
        @Nullable ItemStack itemStack,
        @Nonnull World world,
        @Nonnull Vector3i vector3i
    ) {}
}
