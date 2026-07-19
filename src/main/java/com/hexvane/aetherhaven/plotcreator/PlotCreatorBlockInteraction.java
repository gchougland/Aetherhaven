package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.plugin.SubpluginInteractionGuard;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class PlotCreatorBlockInteraction extends SimpleBlockInteraction {
    @Nonnull
    public static final com.hypixel.hytale.codec.builder.BuilderCodec<PlotCreatorBlockInteraction> CODEC =
        com.hypixel.hytale.codec.builder.BuilderCodec
            .builder(PlotCreatorBlockInteraction.class, PlotCreatorBlockInteraction::new, SimpleBlockInteraction.CODEC)
            .documentation("Plot creator staff: click blocks while the wizard is open.")
            .build();

    @Override
    protected void interactWithBlock(
        @Nonnull World world,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionType type,
        @Nonnull InteractionContext context,
        @Nullable ItemStack itemInHand,
        @Nonnull Vector3i targetBlock,
        @Nonnull com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler cooldownHandler
    ) {
        if (SubpluginInteractionGuard.failIfDisabled(context, AetherhavenPluginIds.PLOT_CREATOR)) {
            return;
        }
        if (!PlotCreatorInteractions.isPlotCreatorStaff(itemInHand)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Ref<EntityStore> ref = context.getEntity();
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
        Store<EntityStore> store = commandBuffer.getStore();
        Vector3i block =
            PlotCreatorBlockTarget.resolve(ref, store, context, targetBlock);
        if (block == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (type == InteractionType.Secondary) {
            PlotCreatorDraft draft = session.getDraft();
            if (draft.getStep() == PlotCreatorStep.SUBSTEP) {
                if (PlotCreatorSubstepHandler.tryRemoveCurrentSubstepAt(session, block, playerRef, commandBuffer)) {
                    PlotCreatorInteractions.refreshHud(playerRef, ref, store, session);
                }
            }
            context.getState().state = InteractionState.Finished;
            return;
        }
        if (PlotCreatorSubstepHandler.handleBlockClick(session, block, playerRef, ref, commandBuffer)) {
            PlotCreatorInteractions.refreshHud(playerRef, ref, store, session);
        }
        context.getState().state = InteractionState.Finished;
    }

    @Override
    protected void simulateInteractWithBlock(
        @Nonnull InteractionType interactionType,
        @Nonnull InteractionContext interactionContext,
        @Nullable ItemStack itemStack,
        @Nonnull World world,
        @Nonnull Vector3i vector3i
    ) {}
}
