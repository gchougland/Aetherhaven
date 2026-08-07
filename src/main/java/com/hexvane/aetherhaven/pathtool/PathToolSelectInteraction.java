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
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PathToolSelectInteraction extends SimpleInstantInteraction {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<PathToolSelectInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(PathToolSelectInteraction.class, PathToolSelectInteraction::new, SimpleInstantInteraction.CODEC)
            .documentation("Path tool: select node or path by look, or click ground to move in translate mode.")
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
        if (commandBuffer == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        @Nullable
        Ref<EntityStore> playerRef = context.getEntity();
        if (playerRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PathToolPlayerComponent st = commandBuffer.getComponent(playerRef, PathToolPlayerComponent.getComponentType());
        boolean replaceFilter = st != null && st.getGizmoMode() == PathToolGizmoMode.ReplaceFilter;
        boolean removeMode = st != null && st.getGizmoMode() == PathToolGizmoMode.Remove;
        if (type != InteractionType.Primary && !replaceFilter && !removeMode) {
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
        World world = commandBuffer.getStore().getExternalData().getWorld();
        PathToolInteractions.handleSelect(playerRef, commandBuffer, world, context, commandBuffer.getStore());
        if (context.getState().state != InteractionState.Failed) {
            context.getState().state = InteractionState.Finished;
        }
    }
}
