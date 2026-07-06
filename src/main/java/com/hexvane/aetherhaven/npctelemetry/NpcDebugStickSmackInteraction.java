package com.hexvane.aetherhaven.npctelemetry;

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

/** Primary smack on an NPC: dumps telemetry JSON to the plugin data directory. */
public final class NpcDebugStickSmackInteraction extends SimpleInstantInteraction {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<NpcDebugStickSmackInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(NpcDebugStickSmackInteraction.class, NpcDebugStickSmackInteraction::new, SimpleInstantInteraction.CODEC)
            .documentation("Dumps NPC telemetry to plugin data when smacking an entity with the debug stick.")
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
        if (SubpluginInteractionGuard.failIfDisabled(context, AetherhavenPluginIds.ADMIN_TOOLS)) {
            return;
        }
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null || type != InteractionType.Primary) {
            if (commandBuffer != null) {
                context.getState().state = InteractionState.Failed;
            }
            return;
        }
        @Nullable
        Ref<EntityStore> playerRef = context.getEntity();
        if (playerRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        @Nullable
        ItemStack itemInHand = InventoryComponent.getItemInHand(commandBuffer, playerRef);
        if (!NpcDebugStickInteractions.isDebugStickItem(itemInHand)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        NpcDebugStickInteractions.handleSmack(playerRef, commandBuffer, context, itemInHand);
        context.getState().state = InteractionState.Finished;
    }

    @Override
    protected void simulateFirstRun(
        @Nonnull InteractionType type,
        @Nonnull InteractionContext context,
        @Nonnull CooldownHandler cooldownHandler
    ) {
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null || type != InteractionType.Primary) {
            if (commandBuffer != null) {
                context.getState().state = InteractionState.Failed;
            }
            return;
        }
        @Nullable
        Ref<EntityStore> playerRef = context.getEntity();
        @Nullable
        ItemStack itemInHand = playerRef == null ? null : InventoryComponent.getItemInHand(commandBuffer, playerRef);
        context.getState().state =
            playerRef != null && NpcDebugStickInteractions.isDebugStickItem(itemInHand)
                ? InteractionState.Finished
                : InteractionState.Failed;
    }
}
