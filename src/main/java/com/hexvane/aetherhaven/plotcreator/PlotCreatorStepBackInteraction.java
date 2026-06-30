package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.plugin.SubpluginInteractionGuard;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Plot creator staff: Q / Ability1 — previous step. */
public final class PlotCreatorStepBackInteraction extends SimpleInstantInteraction {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<PlotCreatorStepBackInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(PlotCreatorStepBackInteraction.class, PlotCreatorStepBackInteraction::new, SimpleInstantInteraction.CODEC)
            .documentation("Plot creator staff: Ability1 — go to the previous step.")
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
        if (commandBuffer == null || type != InteractionType.Ability1) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        @Nullable
        Ref<EntityStore> ref = context.getEntity();
        if (ref == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PlotCreatorInteractions.handleStepBack(ref, commandBuffer, context);
    }
}
