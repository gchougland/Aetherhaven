package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.plugin.SubpluginInteractionGuard;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Plot creator staff: R / Ability3 — open cancel confirmation. */
public final class PlotCreatorCancelInteraction extends SimpleInstantInteraction {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<PlotCreatorCancelInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(PlotCreatorCancelInteraction.class, PlotCreatorCancelInteraction::new, SimpleInstantInteraction.CODEC)
            .documentation("Plot creator staff: Ability3 — confirm cancel of the active session.")
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
        if (SubpluginInteractionGuard.failIfDisabled(context, AetherhavenPluginIds.PLOT_CREATOR)) {
            return;
        }
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        if (commandBuffer == null || type != InteractionType.Ability3) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        @Nullable
        Ref<EntityStore> ref = context.getEntity();
        if (ref == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        ItemStack hand = context.getHeldItem();
        if (!PlotCreatorInteractions.isPlotCreatorStaff(hand)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null || !PlotCreatorInteractions.hasPlotCreatorPermission(playerRef)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PlotCreatorSession session = PlotCreatorSessions.get(playerRef.getUuid());
        if (session == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PlotCreatorInteractions.openCancelConfirm(ref, commandBuffer.getStore(), playerRef);
        context.getState().state = InteractionState.Finished;
    }
}
