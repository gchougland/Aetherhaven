package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.plugin.SubpluginInteractionGuard;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionSyncData;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.ChargingInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Plot creator staff primary: hold primary to drag mark build bounds. Uses {@link ChargingInteraction} so the
 * client keeps sending hold ticks; a plain {@code SimpleInteraction} finishes instantly and never drags.
 */
public final class PlotCreatorBoundsDragInteraction extends ChargingInteraction {
    private static final float CHARGE_HELD = -1f;
    private static final float CHARGE_CANCELED = -2f;

    @Nonnull
    public static final BuilderCodec<PlotCreatorBoundsDragInteraction> CODEC =
        BuilderCodec
            .builder(
                PlotCreatorBoundsDragInteraction.class,
                PlotCreatorBoundsDragInteraction::new,
                ChargingInteraction.CODEC
            )
            .documentation("Plot creator staff: hold primary to drag mark build bounds.")
            .build();

    public PlotCreatorBoundsDragInteraction() {
        allowIndefiniteHold = true;
        displayProgress = false;
    }

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
        if (type != InteractionType.Primary) {
            context.getState().state = InteractionState.Finished;
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
        if (session == null || session.getDraft().getStep() != PlotCreatorStep.BOUNDS) {
            context.getState().state = InteractionState.Finished;
            return;
        }

        InteractionSyncData clientState = context.getClientState();
        if (clientState == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }

        Store<EntityStore> store = commandBuffer.getStore();
        PlotCreatorDraft draft = session.getDraft();
        float charge = clientState.chargeValue;

        if (charge == CHARGE_HELD) {
            if (!draft.isBoundsPrimaryHeld()) {
                PlotCreatorBoundsInput.onPrimaryPress(session, ref, store, playerRef);
            }
            PlotCreatorBoundsInput.onDragTick(session, ref, store, playerRef);
            context.getState().state = InteractionState.NotFinished;
            return;
        }

        if (draft.isBoundsPrimaryHeld()) {
            if (charge == CHARGE_CANCELED) {
                PlotCreatorBoundsInput.cancelPrimaryHold(session, playerRef);
            } else {
                PlotCreatorBoundsInput.onPrimaryRelease(session, ref, store, playerRef);
            }
        }
        context.getState().state = InteractionState.Finished;
    }
}
