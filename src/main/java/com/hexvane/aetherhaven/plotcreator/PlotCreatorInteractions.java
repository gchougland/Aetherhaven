package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.ui.PlotCreatorCancelConfirmPage;
import com.hexvane.aetherhaven.ui.PlotCreatorImportantSpotsPage;
import com.hexvane.aetherhaven.ui.PlotCreatorPoiActivityPage;
import com.hexvane.aetherhaven.ui.PlotCreatorStepJumpPage;
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

    /** Plot creator staff or building editor staff (wizard keybinds while a session is active). */
    public static boolean isWizardStaff(@Nullable ItemStack stack) {
        return isPlotCreatorStaff(stack) || BuildingEditorInteractions.isBuildingEditorStaff(stack);
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
            PlotCreatorHudSupport.refreshAll(player, playerRef, session);
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
        PlotCreatorDraft draft = session.getDraft();
        if (draft.getStep() == PlotCreatorStep.BOUNDS && draft.getBoundsPhase() == PlotCreatorBoundsPhase.FACE_ADJUST) {
            draft.resetBoundsEditing();
            PlotCreatorService.refreshBoundsVisuals(session, playerRef);
            refreshHud(playerRef, ref, commandBuffer.getStore(), session);
            context.getState().state = InteractionState.Finished;
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

    public static void handleStepJump(
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
        if (session.getDraft().getStep() == PlotCreatorStep.DONE) {
            context.getState().state = InteractionState.Failed;
            return;
        }
        openStepJumpPage(playerRef, ref, commandBuffer.getStore(), session);
        context.getState().state = InteractionState.Finished;
    }

    public static void openStepJumpPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotCreatorSession session
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new PlotCreatorStepJumpPage(playerRef, session));
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
        if (player != null && player.getPageManager().getCustomPage() instanceof PlotCreatorWizardPage wizard
            && wizard.isSubPanelMode()) {
            player.getPageManager().setPage(ref, store, Page.None);
        }
    }

    /**
     * Opens or in-place refreshes the wizard sub-panel for the draft's current step (kind, variant, or settings).
     * Reuses the active {@link PlotCreatorWizardPage} when possible to avoid PageManager acknowledgement races.
     */
    public static void openWizardSubPanel(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotCreatorSession session
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage current =
            player.getPageManager().getCustomPage();
        if (current instanceof PlotCreatorWizardPage wizard && wizard.isSubPanelMode()) {
            wizard.refreshSubPanel(ref, store);
            refreshHud(playerRef, ref, store, session);
            return;
        }
        player.getPageManager().openCustomPage(ref, store, PlotCreatorWizardPage.subPanel(playerRef, session));
    }

    public static void openConfigPanel(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotCreatorSession session
    ) {
        openWizardSubPanel(playerRef, ref, store, session);
    }

    public static void openKindPanel(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotCreatorSession session
    ) {
        openWizardSubPanel(playerRef, ref, store, session);
    }

    public static void openConfigurePanel(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotCreatorSession session
    ) {
        openWizardSubPanel(playerRef, ref, store, session);
    }

    public static void openImportantSpotsPanel(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotCreatorSession session
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        PlotCreatorService.seedImportantSpotsIfEmpty(session.getDraft());
        com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage current =
            player.getPageManager().getCustomPage();
        if (current instanceof PlotCreatorImportantSpotsPage spotsPage) {
            spotsPage.refreshOpenPanel(ref, store);
            refreshHud(playerRef, ref, store, session);
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new PlotCreatorImportantSpotsPage(playerRef, session));
    }

    public static void openPoiActivityPage(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotCreatorSession session
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        player.getPageManager().openCustomPage(ref, store, new PlotCreatorPoiActivityPage(playerRef, session));
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
        if (step == PlotCreatorStep.BOUNDS) {
            if (d.getCornerFirst() == null || d.getCornerSecond() == null) {
                playerRef.sendMessage(Message.translation(MSG + ".error.needBounds"));
                return false;
            }
            Vector3i min = d.boundsMin();
            Vector3i max = d.boundsMax();
            String boundsErr = PlotCreatorBoundsValidation.validateMinMax(min, max);
            if (boundsErr != null) {
                playerRef.sendMessage(Message.translation(MSG + ".error." + boundsErr));
                return false;
            }
            int width = max.x - min.x + 1;
            int depth = max.z - min.z + 1;
            int height = max.y - min.y + 1;
            playerRef.sendMessage(
                Message.translation(MSG + ".hint.boundsSet")
                    .param("width", width)
                    .param("depth", depth)
                    .param("height", height)
            );
            PlotCreatorAutoAnchor.applyCenter(d);
            PlotCreatorService.advance(session, ref, store);
            return true;
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
            String kindErr = PlotCreatorService.validateKindSelection(d);
            if (kindErr != null) {
                playerRef.sendMessage(Message.translation(MSG + ".error." + kindErr));
                return false;
            }
            PlotCreatorService.applyDefaultTagsForKind(d);
            PlotCreatorService.advance(session, ref, store);
            return true;
        }
        if (step == PlotCreatorStep.VARIANT) {
            if (d.getCountsAsConstructionIds().isEmpty()) {
                playerRef.sendMessage(Message.translation(MSG + ".error.needVariantOf"));
                return false;
            }
            for (String base : d.getCountsAsConstructionIds()) {
                if (!PlotCreatorMainConstructions.isKnownMainConstruction(plugin, base)) {
                    playerRef.sendMessage(Message.translation(MSG + ".error.invalidVariantOf"));
                    return false;
                }
            }
            PlotCreatorService.advance(session, ref, store);
            return true;
        }
        if (step == PlotCreatorStep.IMPORTANT_SPOTS) {
            PlotCreatorService.confirmImportantSpots(d);
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
            String settingsErr = PlotCreatorService.applySettingsStepInput(d);
            if (settingsErr != null) {
                playerRef.sendMessage(Message.translation(MSG + ".error." + settingsErr));
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
            case BOUNDS -> {
                playerRef.sendMessage(Message.translation(MSG + ".hint.boundsHelp"));
                yield true;
            }
            case ANCHOR -> {
                PlotCreatorAutoAnchor.applyCenter(d);
                PlotCreatorService.advance(session, ref, store);
                yield true;
            }
            case SUBSTEP -> {
                playerRef.sendMessage(Message.translation(MSG + ".hint.clickBlock"));
                yield true;
            }
            case PREFAB_SAVE -> exportPrefab(session, playerRef, commandBuffer);
            case KIND -> {
                openKindPanel(playerRef, ref, store, session);
                yield true;
            }
            case IMPORTANT_SPOTS -> {
                openImportantSpotsPanel(playerRef, ref, store, session);
                yield true;
            }
            case VARIANT -> {
                openConfigPanel(playerRef, ref, store, session);
                yield true;
            }
            case IDENTITY, TAGS, CONFIGURE -> {
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
        String fileName;
        Path out;
        if (d.isBuildingEditorMode()) {
            String locked = d.getLockedPrefabPathKey() != null ? d.getLockedPrefabPathKey() : d.getPrefabPath();
            fileName = BuildingEditorSavePaths.prefabFileName(locked);
            Path writeRoot = BuildingEditorSavePaths.resolveWriteRoot(
                plugin,
                d.getConstructionId() != null ? d.getConstructionId() : ""
            );
            out = BuildingEditorSavePaths.prefabFile(writeRoot, locked);
        } else {
            fileName = PlotCreatorPrefabExporter.prefabPathKeyFromConstructionId(d.getConstructionId());
            if (fileName == null) {
                playerRef.sendMessage(Message.translation(MSG + ".error.needIdentity"));
                return;
            }
            out = CustomBuildingsPaths.prefabsDirectory(plugin.getDataDirectory()).resolve(fileName);
        }
        boolean overwrite = allowPrefabOverwrite(d, fileName);
        if (!overwrite && java.nio.file.Files.isRegularFile(out)) {
            playerRef.sendMessage(Message.translation(MSG + ".error.prefabAlreadyExists"));
            return;
        }
        PlotCreatorPrefabExporter.ExportResult result = PlotCreatorPrefabExporter.export(session.getWorld(), d, out, overwrite);
        if (result == PlotCreatorPrefabExporter.ExportResult.ALREADY_EXISTS) {
            playerRef.sendMessage(Message.translation(MSG + ".error.prefabAlreadyExists"));
            return;
        }
        if (result != PlotCreatorPrefabExporter.ExportResult.SUCCESS) {
            playerRef.sendMessage(Message.translation(MSG + ".error.prefabExport"));
            return;
        }
        if (!d.isBuildingEditorMode()) {
            d.setSessionExportedPrefabPath(fileName);
        }
        if (d.isBuildingEditorMode() && d.getLockedPrefabPathKey() != null) {
            d.setPrefabPath(d.getLockedPrefabPathKey());
            d.setPrefabFileName(d.getLockedPrefabPathKey());
        } else {
            d.setPrefabPath(fileName);
            d.setPrefabFileName(fileName);
        }
        com.hexvane.aetherhaven.prefab.PrefabResolveUtil.resolvePrefabBuffer(d.getPrefabPath());
        playerRef.sendMessage(Message.translation(MSG + ".hint.prefabSaved").param("file", fileName));
    }

    private static boolean allowPrefabOverwrite(@Nonnull PlotCreatorDraft draft, @Nonnull String prefabFileName) {
        if (draft.isBuildingEditorMode() || draft.getEditingConstructionId() != null) {
            return true;
        }
        return prefabFileName.equals(draft.getSessionExportedPrefabPath());
    }

    public static boolean stepUsesConfigPanel(@Nonnull PlotCreatorStep step) {
        return step == PlotCreatorStep.VARIANT;
    }

    private static boolean prepareSession(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull InteractionContext context
    ) {
        ItemStack hand = context.getHeldItem();
        if (!isWizardStaff(hand)) {
            context.getState().state = InteractionState.Failed;
            return false;
        }
        PlayerRef playerRef = commandBuffer.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            context.getState().state = InteractionState.Failed;
            return false;
        }
        if (BuildingEditorInteractions.isBuildingEditorStaff(hand)) {
            PlotCreatorSession session = PlotCreatorSessions.get(playerRef.getUuid());
            if (session == null || !session.getDraft().isBuildingEditorMode()) {
                context.getState().state = InteractionState.Failed;
                return false;
            }
            return true;
        }
        if (!hasPlotCreatorPermission(playerRef)) {
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
