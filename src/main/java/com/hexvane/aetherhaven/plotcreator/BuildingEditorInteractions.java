package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.plugin.SubpluginInteractionGuard;
import com.hexvane.aetherhaven.ui.BuildingEditorPage;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Keybinding handlers for the Creative building editor staff. */
public final class BuildingEditorInteractions {
    private BuildingEditorInteractions() {}

    public static boolean isBuildingEditorStaff(@Nullable ItemStack stack) {
        return stack != null
            && !stack.isEmpty()
            && AetherhavenConstants.BUILDING_EDITOR_STAFF_ITEM_ID.equals(stack.getItemId());
    }

    public static void handleUse(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionContext context
    ) {
        if (SubpluginInteractionGuard.failIfDisabled(context, AetherhavenPluginIds.PLOT_CREATOR)) {
            return;
        }
        ItemStack hand = context.getHeldItem();
        if (!isBuildingEditorStaff(hand)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Store<EntityStore> store = commandBuffer.getStore();
        if (!BuildingEditorSessionStarter.requireCreative(playerRef, ref, store)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PlotCreatorSession session = PlotCreatorSessions.get(playerRef.getUuid());
        if (session == null) {
            openPicker(playerRef, ref, store);
            context.getState().state = InteractionState.Finished;
            return;
        }
        if (!session.getDraft().isBuildingEditorMode()) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (!PlotCreatorInteractions.runStepUseAction(session, playerRef, ref, commandBuffer)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PlotCreatorInteractions.refreshHud(playerRef, ref, store, session);
        context.getState().state = InteractionState.Finished;
    }

    public static void openPicker(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new BuildingEditorPage(playerRef));
    }

    public static void handleStepBack(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionContext context
    ) {
        if (!prepareEditorSession(ref, commandBuffer, context)) {
            return;
        }
        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        PlotCreatorSession session = PlotCreatorSessions.get(playerRef.getUuid());
        if (session == null || playerRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PlotCreatorService.back(session, ref, commandBuffer.getStore());
        PlotCreatorInteractions.refreshHud(playerRef, ref, commandBuffer.getStore(), session);
        context.getState().state = InteractionState.Finished;
    }

    public static void handleStepForward(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionContext context
    ) {
        if (!prepareEditorSession(ref, commandBuffer, context)) {
            return;
        }
        // Reuse advance logic; prepareSession inside requires plot creator staff — call paths directly.
        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        PlotCreatorSession session = PlotCreatorSessions.get(playerRef.getUuid());
        if (session == null || playerRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (!PlotCreatorInteractions.tryAdvanceForward(session, playerRef, ref, commandBuffer.getStore())) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PlotCreatorInteractions.refreshHud(playerRef, ref, commandBuffer.getStore(), session);
        context.getState().state = InteractionState.Finished;
    }

    public static void handleStepJump(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionContext context
    ) {
        if (!prepareEditorSession(ref, commandBuffer, context)) {
            return;
        }
        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        PlotCreatorSession session = PlotCreatorSessions.get(playerRef.getUuid());
        if (session == null || playerRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (session.getDraft().getStep() == PlotCreatorStep.DONE) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PlotCreatorInteractions.openStepJumpPage(playerRef, ref, commandBuffer.getStore(), session);
        context.getState().state = InteractionState.Finished;
    }

    public static void handleCancel(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionContext context
    ) {
        if (!prepareEditorSession(ref, commandBuffer, context)) {
            return;
        }
        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PlotCreatorInteractions.openCancelConfirm(ref, commandBuffer.getStore(), playerRef);
        context.getState().state = InteractionState.Finished;
    }

    public static void handleBlock(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionContext context,
        @Nonnull org.joml.Vector3i targetBlock,
        @Nonnull InteractionType type
    ) {
        if (SubpluginInteractionGuard.failIfDisabled(context, AetherhavenPluginIds.PLOT_CREATOR)) {
            return;
        }
        ItemStack hand = context.getHeldItem();
        if (!isBuildingEditorStaff(hand)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        Store<EntityStore> store = commandBuffer.getStore();
        if (playerRef == null || !BuildingEditorSessionStarter.requireCreative(playerRef, ref, store)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PlotCreatorSession session = PlotCreatorSessions.get(playerRef.getUuid());
        if (session == null || !session.getDraft().isBuildingEditorMode()) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        org.joml.Vector3i block = PlotCreatorBlockTarget.resolve(ref, store, context, targetBlock);
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

    private static boolean prepareEditorSession(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionContext context
    ) {
        if (SubpluginInteractionGuard.failIfDisabled(context, AetherhavenPluginIds.PLOT_CREATOR)) {
            return false;
        }
        ItemStack hand = context.getHeldItem();
        if (!isBuildingEditorStaff(hand)) {
            context.getState().state = InteractionState.Failed;
            return false;
        }
        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        Store<EntityStore> store = commandBuffer.getStore();
        if (playerRef == null || !BuildingEditorSessionStarter.requireCreative(playerRef, ref, store)) {
            context.getState().state = InteractionState.Failed;
            return false;
        }
        PlotCreatorSession session = PlotCreatorSessions.get(playerRef.getUuid());
        if (session == null || !session.getDraft().isBuildingEditorMode()) {
            context.getState().state = InteractionState.Failed;
            return false;
        }
        return true;
    }
}
