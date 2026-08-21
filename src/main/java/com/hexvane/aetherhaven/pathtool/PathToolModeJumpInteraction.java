package com.hexvane.aetherhaven.pathtool;

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

/** {@link InteractionType#Pick}: open the path tool mode jump menu. */
public final class PathToolModeJumpInteraction extends SimpleInstantInteraction {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<PathToolModeJumpInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(
                PathToolModeJumpInteraction.class,
                PathToolModeJumpInteraction::new,
                SimpleInstantInteraction.CODEC
            )
            .documentation("Path tool: Pick / middle mouse — open mode jump menu.")
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
        if (SubpluginInteractionGuard.failIfDisabled(context, AetherhavenPluginIds.PATH_DESIGNER)) {
            return;
        }
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null || type != InteractionType.Pick) {
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
        if (h == null
            || h.isEmpty()
            || !com.hexvane.aetherhaven.AetherhavenConstants.PATH_TOOL_ITEM_ID.equals(h.getItemId())) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PathToolInteractions.handleModeJump(playerRef, commandBuffer, context);
    }
}
