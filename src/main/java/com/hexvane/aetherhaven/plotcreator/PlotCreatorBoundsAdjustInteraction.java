package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.plugin.SubpluginInteractionGuard;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.WaitForDataFrom;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.SimpleInstantInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Plot creator staff secondary during bounds face adjust: expand or shrink toward look direction. */
public final class PlotCreatorBoundsAdjustInteraction extends SimpleInstantInteraction {
    private static final String MSG = "aetherhaven_plot_creator.aetherhaven.plotcreator";

    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<PlotCreatorBoundsAdjustInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(
                PlotCreatorBoundsAdjustInteraction.class,
                PlotCreatorBoundsAdjustInteraction::new,
                SimpleInstantInteraction.CODEC
            )
            .documentation("Plot creator staff: secondary expands bounds toward look; crouch+secondary shrinks.")
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
        if (commandBuffer == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (type != InteractionType.Secondary) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        @Nullable
        Ref<EntityStore> ref = context.getEntity();
        if (ref == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        @Nullable
        ItemStack hand = InventoryComponent.getItemInHand(commandBuffer, ref);
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
        PlotCreatorDraft draft = session.getDraft();
        if (draft.getStep() != PlotCreatorStep.BOUNDS
            || draft.getBoundsPhase() != PlotCreatorBoundsPhase.FACE_ADJUST) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Store<EntityStore> store = commandBuffer.getStore();
        boolean expand = !isCrouching(ref, store);
        @Nullable
        String err = PlotCreatorBoundsLookAdjust.tryNudgeFromLook(ref, store, draft, expand);
        if (err != null) {
            playerRef.sendMessage(Message.translation(MSG + ".error." + err));
            context.getState().state = InteractionState.Failed;
            return;
        }
        playerRef.sendMessage(
            Message.translation(MSG + (expand ? ".hint.boundsExpanded" : ".hint.boundsShrunk"))
        );
        PlotCreatorService.refreshBoundsVisuals(session, playerRef);
        PlotCreatorInteractions.refreshHud(playerRef, ref, store, session);
        context.getState().state = InteractionState.Finished;
    }

    private static boolean isCrouching(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        MovementStatesComponent ms = store.getComponent(ref, MovementStatesComponent.getComponentType());
        if (ms == null || ms.getMovementStates() == null) {
            return false;
        }
        var states = ms.getMovementStates();
        return states.crouching || states.forcedCrouching;
    }
}
