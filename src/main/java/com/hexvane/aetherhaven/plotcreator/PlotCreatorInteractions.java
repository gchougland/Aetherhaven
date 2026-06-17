package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.ui.PlotCreatorCancelConfirmPage;
import com.hexvane.aetherhaven.ui.PlotCreatorWizardPage;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.InteractionState;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Path;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Keybinding-driven plot creator flow (HUD + staff abilities), modeled after the path tool. */
public final class PlotCreatorInteractions {
    private static final String MSG = "aetherhaven_plot_creator.aetherhaven.plotcreator";

    private PlotCreatorInteractions() {}

    public static boolean isPlotCreatorStaff(@Nullable ItemStack stack) {
        return stack != null
            && !stack.isEmpty()
            && AetherhavenConstants.PLOT_CREATOR_STAFF_ITEM_ID.equals(stack.getItemId());
    }

    public static boolean hasPlotCreatorPermission(@Nonnull PlayerRef playerRef) {
        return PlotCreatorService.hasPermission(playerRef);
    }

    public static void refreshHud(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotCreatorSession session
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null && session.getDraft().getStep() != PlotCreatorStep.DONE) {
            PlotCreatorHudSupport.obtainHud(player, playerRef).refresh(session);
        }
        PlotCreatorService.refreshWireframe(session, playerRef);
        PlotCreatorService.refreshSpawnMarkers(session, playerRef);
    }

    public static void handleStepBack(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionContext context
    ) {
        if (!prepareSession(ref, commandBuffer, context)) {
            return;
        }
        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        PlotCreatorSession session = PlotCreatorSessions.get(playerRef.getUuid());
        if (session == null || playerRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PlotCreatorService.back(session, ref, commandBuffer.getStore());
        refreshHud(playerRef, ref, commandBuffer.getStore(), session);
        context.getState().state = InteractionState.Finished;
    }

    public static void handleStepForward(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionContext context
    ) {
        if (!prepareSession(ref, commandBuffer, context)) {
            return;
        }
        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        PlotCreatorSession session = PlotCreatorSessions.get(playerRef.getUuid());
        if (session == null || playerRef == null) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        if (!tryAdvanceForward(session, playerRef, ref, commandBuffer.getStore())) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        refreshHud(playerRef, ref, commandBuffer.getStore(), session);
        context.getState().state = InteractionState.Finished;
    }

    public static void handleUse(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionContext context
    ) {
        ItemStack hand = context.getHeldItem();
        if (!isPlotCreatorStaff(hand)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null || !hasPlotCreatorPermission(playerRef)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        Store<EntityStore> store = commandBuffer.getStore();
        PlotCreatorSession session = PlotCreatorSessions.get(playerRef.getUuid());
        if (session == null) {
            PlotCreatorService.startSession(playerRef, ref, store);
            context.getState().state = InteractionState.Finished;
            return;
        }
        if (!runStepUseAction(session, playerRef, ref, commandBuffer)) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        refreshHud(playerRef, ref, store, session);
        context.getState().state = InteractionState.Finished;
    }

    public static void openCancelConfirm(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new PlotCreatorCancelConfirmPage(playerRef));
    }

    public static void closeConfigPanel(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null && player.getPageManager().getCustomPage() instanceof PlotCreatorWizardPage) {
            player.getPageManager().setPage(ref, store, Page.None);
        }
    }

    public static void openConfigPanel(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotCreatorSession session
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new PlotCreatorWizardPage(playerRef, session, true, false));
    }

    public static void openKindPanel(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotCreatorSession session
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store, PlotCreatorWizardPage.kindPanel(playerRef, session));
    }

    public static void openConfigurePanel(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotCreatorSession session
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store, PlotCreatorWizardPage.configurePanel(playerRef, session));
    }

    public static boolean tryAdvanceForward(
        @Nonnull PlotCreatorSession session,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        PlotCreatorDraft d = session.getDraft();
        PlotCreatorStep step = d.getStep();
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return false;
        }
        if (step == PlotCreatorStep.WELCOME) {
            PlotCreatorService.advance(session, ref, store);
            return true;
        }
        if (step == PlotCreatorStep.CORNER_FIRST && d.getCornerFirst() == null) {
            playerRef.sendMessage(Message.translation(MSG + ".error.needCornerFirst"));
            return false;
        }
        if (step == PlotCreatorStep.CORNER_SECOND && d.getCornerSecond() == null) {
            playerRef.sendMessage(Message.translation(MSG + ".error.needCornerSecond"));
            return false;
        }
        if (step == PlotCreatorStep.ANCHOR) {
            Vector3i anchor = d.getPlotAnchor();
            if (anchor == null) {
                playerRef.sendMessage(Message.translation(MSG + ".error.needAnchor"));
                return false;
            }
            if (d.isInsideBounds(anchor)) {
                playerRef.sendMessage(Message.translation(MSG + ".error.anchorInside"));
                return false;
            }
            if (!PlotCreatorAnchorRules.isOutsideCorner(d, anchor)) {
                playerRef.sendMessage(Message.translation(MSG + ".error.anchorNotCorner"));
                return false;
            }
        }
        if (step == PlotCreatorStep.PREFAB_SAVE) {
            if (d.getConstructionId() == null || d.getConstructionId().isBlank()) {
                playerRef.sendMessage(Message.translation(MSG + ".error.needIdentity"));
                return false;
            }
            if (d.getPrefabPath() == null || d.getPrefabPath().isBlank()) {
                playerRef.sendMessage(Message.translation(MSG + ".error.prefab_missing"));
                return false;
            }
            PlotCreatorService.advance(session, ref, store);
            return true;
        }
        if (step == PlotCreatorStep.KIND) {
            if (d.getKind() == null) {
                playerRef.sendMessage(Message.translation(MSG + ".error.needKind"));
                return false;
            }
            PlotCreatorService.advance(session, ref, store);
            return true;
        }
        if (step == PlotCreatorStep.IDENTITY) {
            String err = PlotCreatorValidator.validateId(d.getConstructionId(), plugin.getConstructionCatalog(), d.getEditingConstructionId());
            if (err != null) {
                playerRef.sendMessage(Message.translation(MSG + ".error." + err));
                return false;
            }
            if (d.getDisplayName() == null || d.getDisplayName().isBlank()) {
                playerRef.sendMessage(Message.translation(MSG + ".error.id_empty"));
                return false;
            }
            PlotCreatorService.advance(session, ref, store);
            return true;
        }
        if (step == PlotCreatorStep.VARIANT) {
            String base = d.getCountsAsConstructionId();
            if (base == null || base.isBlank()) {
                playerRef.sendMessage(Message.translation(MSG + ".error.needVariantOf"));
                return false;
            }
            PlotCreatorService.advance(session, ref, store);
            return true;
        }
        if (step == PlotCreatorStep.TAGS) {
            PlotCreatorService.applyTagsInput(d);
            PlotCreatorService.advance(session, ref, store);
            return true;
        }
        if (step == PlotCreatorStep.SUBSTEP) {
            PlotCreatorService.advanceSubstepOrStep(session, ref, store);
            return true;
        }
        if (step == PlotCreatorStep.MATERIALS) {
            PlotCreatorService.advance(session, ref, store);
            return true;
        }
        if (step == PlotCreatorStep.CONFIGURE) {
            String configureErr = PlotCreatorService.applyConfigureInput(d);
            if (configureErr != null) {
                playerRef.sendMessage(Message.translation(MSG + ".error." + configureErr));
                return false;
            }
            PlotCreatorService.advance(session, ref, store);
            return true;
        }
        if (step == PlotCreatorStep.REVIEW) {
            return PlotCreatorService.saveAndFinish(plugin, session, playerRef, ref, store);
        }
        if (step == PlotCreatorStep.DONE) {
            PlotCreatorService.cancelSession(playerRef, ref, store);
            return true;
        }
        PlotCreatorService.advance(session, ref, store);
        return true;
    }

    public static boolean runStepUseAction(
        @Nonnull PlotCreatorSession session,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Store<EntityStore> store = commandBuffer.getStore();
        PlotCreatorDraft d = session.getDraft();
        return switch (d.getStep()) {
            case WELCOME -> {
                PlotCreatorService.advance(session, ref, store);
                yield true;
            }
            case CORNER_FIRST, CORNER_SECOND, ANCHOR, SUBSTEP -> {
                playerRef.sendMessage(Message.translation(MSG + ".hint.clickBlock"));
                yield true;
            }
            case PREFAB_SAVE -> exportPrefab(session, playerRef, commandBuffer);
            case KIND -> {
                openKindPanel(playerRef, ref, store, session);
                yield true;
            }
            case IDENTITY, TAGS, VARIANT -> {
                openConfigPanel(playerRef, ref, store, session);
                yield true;
            }
            case CONFIGURE -> {
                openConfigurePanel(playerRef, ref, store, session);
                yield true;
            }
            case MATERIALS -> {
                PlotCreatorMaterialsActions.openMaterialsPanel(session, playerRef, ref, store);
                yield true;
            }
            case REVIEW -> PlotCreatorService.saveAndFinish(AetherhavenPlugin.get(), session, playerRef, ref, store);
            case DONE -> {
                PlotCreatorService.cancelSession(playerRef, ref, store);
                yield true;
            }
        };
    }

    public static boolean exportPrefab(@Nonnull PlotCreatorSession session, @Nonnull PlayerRef playerRef) {
        session.getWorld().execute(() -> doExportPrefab(session, playerRef));
        return true;
    }

    public static boolean exportPrefab(
        @Nonnull PlotCreatorSession session,
        @Nonnull PlayerRef playerRef,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        session.getWorld().execute(() -> doExportPrefab(session, playerRef));
        return true;
    }

    private static void doExportPrefab(@Nonnull PlotCreatorSession session, @Nonnull PlayerRef playerRef) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        PlotCreatorDraft d = session.getDraft();
        String fileName = PlotCreatorPrefabExporter.prefabPathKeyFromConstructionId(d.getConstructionId());
        if (fileName == null) {
            playerRef.sendMessage(Message.translation(MSG + ".error.needIdentity"));
            return;
        }
        Path out = CustomBuildingsPaths.prefabsDirectory(plugin.getDataDirectory()).resolve(fileName);
        boolean ok = PlotCreatorPrefabExporter.export(session.getWorld(), d, out, d.getEditingConstructionId() != null);
        if (!ok) {
            playerRef.sendMessage(Message.translation(MSG + ".error.prefabExport"));
            return;
        }
        d.setPrefabPath(fileName);
        d.setPrefabFileName(fileName);
        com.hexvane.aetherhaven.prefab.PrefabResolveUtil.resolvePrefabBuffer(fileName);
        playerRef.sendMessage(Message.translation(MSG + ".hint.prefabSaved").param("file", fileName));
    }

    public static boolean stepUsesConfigPanel(@Nonnull PlotCreatorStep step) {
        return step == PlotCreatorStep.IDENTITY || step == PlotCreatorStep.TAGS || step == PlotCreatorStep.VARIANT;
    }

    private static boolean prepareSession(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionContext context
    ) {
        ItemStack hand = context.getHeldItem();
        if (!isPlotCreatorStaff(hand)) {
            context.getState().state = InteractionState.Failed;
            return false;
        }
        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null || !hasPlotCreatorPermission(playerRef)) {
            context.getState().state = InteractionState.Failed;
            return false;
        }
        if (PlotCreatorSessions.get(playerRef.getUuid()) == null) {
            context.getState().state = InteractionState.Failed;
            return false;
        }
        return true;
    }

}
