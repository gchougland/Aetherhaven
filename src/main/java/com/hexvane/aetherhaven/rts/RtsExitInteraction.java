package com.hexvane.aetherhaven.rts;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.plugin.SubpluginInteractionGuard;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Hotbar exit tool — leaves RTS command mode without using the command post. */
public final class RtsExitInteraction extends SimpleInstantInteraction {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<RtsExitInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(RtsExitInteraction.class, RtsExitInteraction::new, SimpleInstantInteraction.CODEC)
            .documentation("RTS exit tool: leave guard command mode.")
            .build();

    @Nonnull
    @Override
    public WaitForDataFrom getWaitForDataFrom() {
        return WaitForDataFrom.Client;
    }

    @Override
    public boolean needsRemoteSync() {
        return true;
    }

    @Override
    protected void firstRun(
        @Nonnull InteractionType type,
        @Nonnull InteractionContext context,
        @Nonnull CooldownHandler cooldownHandler
    ) {
        if (SubpluginInteractionGuard.failIfDisabled(context, AetherhavenPluginIds.RTS)) {
            return;
        }
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null || type != InteractionType.Primary) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        @Nullable
        Ref<EntityStore> playerRef = context.getEntity();
        if (playerRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        @Nullable
        ItemStack hand = InventoryComponent.getItemInHand(commandBuffer, playerRef);
        if (hand == null || hand.isEmpty() || !AetherhavenConstants.RTS_EXIT_ITEM_ID.equals(hand.getItemId())) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        RtsCommandService.exit(playerRef, commandBuffer);
        context.getState().state = InteractionState.Finished;
    }
}
