package com.hexvane.aetherhaven.patrol;

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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PatrolWandToggleClosedInteraction extends SimpleInstantInteraction {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<PatrolWandToggleClosedInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(
                PatrolWandToggleClosedInteraction.class,
                PatrolWandToggleClosedInteraction::new,
                SimpleInstantInteraction.CODEC
            )
            .documentation("Patrol wand: toggle closed loop vs ping-pong path.")
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
        if (SubpluginInteractionGuard.failIfDisabled(context, AetherhavenPluginIds.PATROL_ROUTES)) {
            return;
        }
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (type != InteractionType.Ability3) {
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
        ItemStack h = InventoryComponent.getItemInHand(commandBuffer, playerRef);
        if (h == null || h.isEmpty() || !AetherhavenConstants.PATROL_WAND_ITEM_ID.equals(h.getItemId())) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        World world = commandBuffer.getStore().getExternalData().getWorld();
        PatrolWandInteractions.handleToggleClosedLoop(playerRef, commandBuffer, world, context, commandBuffer.getStore());
    }
}
