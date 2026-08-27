package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.plugin.SubpluginInteractionGuard;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.none.BuilderToolInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Plot creator staff: runs the embedded Selection builder tool while marking build bounds. */
public final class PlotCreatorBoundsSelectionInteraction extends BuilderToolInteraction {
    @Nonnull
    public static final BuilderCodec<PlotCreatorBoundsSelectionInteraction> CODEC =
        BuilderCodec
            .builder(
                PlotCreatorBoundsSelectionInteraction.class,
                PlotCreatorBoundsSelectionInteraction::new,
                BuilderToolInteraction.CODEC
            )
            .documentation("Plot creator staff: vanilla Selection tool while marking build bounds.")
            .build();

    @Override
    protected void tick0(
        boolean firstRun,
        float time,
        @Nonnull InteractionType type,
        @Nonnull InteractionContext context,
        @Nonnull CooldownHandler cooldownHandler
    ) {
        if (SubpluginInteractionGuard.failIfDisabled(context, AetherhavenPluginIds.PLOT_CREATOR)) {
            return;
        }
        CommandBuffer<EntityStore> commandBuffer = context.getCommandBuffer();
        @Nullable
        Ref<EntityStore> ref = context.getEntity();
        if (commandBuffer == null || ref == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        ItemStack hand = context.getHeldItem();
        if (!PlotCreatorStaffBoundsSwap.isBoundsStaff(hand)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null || !PlotCreatorInteractions.hasPlotCreatorPermission(playerRef)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PlotCreatorSession session = PlotCreatorSessions.get(playerRef.getUuid());
        PlotCreatorDraft draft = session != null ? session.getDraft() : null;
        if (session == null
            || draft == null
            || !draft.isEditingBounds()
            || draft.isFestivalSizeLocked()) {
            context.getState().state = InteractionState.Finished;
            return;
        }
        PlotCreatorSelectionBoundsService.ensureSurvivalAccess(playerRef, ref, commandBuffer.getStore());
        super.tick0(firstRun, time, type, context, cooldownHandler);
        // Keep the tool active while dragging; release when idle so F / Use is not blocked behind this chain.
        if ((context.getState().state == InteractionState.Finished
                || context.getState().state == InteractionState.Skip)
            && PlotCreatorSelectionBoundsService.isSelectionDragRecent(playerRef.getUuid())) {
            context.getState().state = InteractionState.NotFinished;
        }
    }
}
