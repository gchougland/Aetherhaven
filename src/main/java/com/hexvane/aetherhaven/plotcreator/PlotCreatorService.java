package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.community.CommunitySubmissionService;
import com.hexvane.aetherhaven.prop.PropIconRenderClient;
import com.hexvane.aetherhaven.community.CommunitySubmitLocalSave;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.economy.GoldCoinPayment.SpendBreakdown;
import com.hexvane.aetherhaven.festival.CustomFestivalPaths;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.placement.PlotFootprintOverlayRefresh;
import com.hexvane.aetherhaven.placement.PlotPlacementWireframeOverlay;
import com.hexvane.aetherhaven.plot.PlotTokenInventory;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownPlayerResolution;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class PlotCreatorService {
    private static final ConcurrentHashMap<UUID, Boolean> PLOT_CREATOR_WIREFRAME_ACTIVE = new ConcurrentHashMap<>();
    /** Gold coins charged in non-Creative mode when saving a building as a plot. */
    private static final long SURVIVAL_SAVE_GOLD_COST = 100L;
    private PlotCreatorService() {}

    public static boolean hasPermission(@Nonnull PlayerRef playerRef) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin != null && plugin.getConfig().get().isGrantPlotCreatorPermissionToEveryone()) {
            return true;
        }
        return playerRef.hasPermission(com.hexvane.aetherhaven.AetherhavenConstants.PERMISSION_PLOT_CREATOR);
    }

    public static boolean limitBuildingTypesToPlayerKinds() {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        return plugin == null || plugin.getConfig().get().isPlotCreatorPlayerBuildingTypesOnly();
    }

    /** @return plot creator error lang suffix ({@code aetherhaven.plotcreator.error.<suffix>}), or null if valid */
    @Nullable
    public static String validateKindSelection(@Nonnull PlotCreatorDraft draft) {
        List<PlotBuildingKind> kinds = draft.getKinds();
        if (kinds.isEmpty()) {
            return "needKind";
        }
        if (kinds.contains(PlotBuildingKind.DECORATION) && kinds.size() > 1) {
            return "decorationExclusive";
        }
        if (kinds.contains(PlotBuildingKind.FESTIVAL) && kinds.size() > 1) {
            return "festivalExclusive";
        }
        if (kinds.contains(PlotBuildingKind.WALL) && kinds.size() > 1) {
            return "wallExclusive";
        }
        if (kinds.contains(PlotBuildingKind.PROP) && kinds.size() > 1) {
            return "propExclusive";
        }
        // A wall in the editor only works when the whole style was loaded from the walls tab, never a single piece.
        if (kinds.contains(PlotBuildingKind.WALL)
            && draft.isBuildingEditorMode()
            && draft.getEditingWallStyleBaseId() == null) {
            return "wallNotEditable";
        }
        if (limitBuildingTypesToPlayerKinds()) {
            if (draft.isBuildingEditorMode()) {
                return null;
            }
            for (PlotBuildingKind kind : kinds) {
                if (!kind.isPlayerKind() && kind != PlotBuildingKind.TOURIST_PORTAL) {
                    return "kindNotAllowed";
                }
            }
        }
        return null;
    }

    public static void startEditSession(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull String constructionId
    ) {
        if (!hasPermission(playerRef)) {
            playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.noPermission"));
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        String id = constructionId.trim();
        ConstructionCatalog catalog = plugin.getConstructionCatalog();
        if (!catalog.isCustomConstruction(id)) {
            playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.editNotCustom"));
            return;
        }
        ConstructionDefinition def = catalog.get(id);
        if (def == null) {
            playerRef.sendMessage(
                Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.editUnknown").param("id", id)
            );
            return;
        }
        UUID uuid = playerRef.getUuid();
        PlotCreatorSessions.remove(uuid);
        World world = store.getExternalData().getWorld();
        PlotCreatorSession session = new PlotCreatorSession(uuid, world);
        PlotCreatorDraftLoader.loadIntoDraft(session.getDraft(), def);
        applyCommunityMarketplaceDefaults(session.getDraft());
        session.getDraft().setStep(PlotCreatorStep.REVIEW);
        session
            .getDraft()
            .setMaxReachedStepIndex(
                Math.max(0, stepOrder(session.getDraft()).indexOf(PlotCreatorStep.REVIEW))
            );
        PlotCreatorSessions.put(session);
        playerRef.sendMessage(
            Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.edit.loaded").param("id", id)
        );
        showSessionHud(playerRef, ref, store, session);
        playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.session.started"));
    }

    public static void startSession(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        if (!hasPermission(playerRef)) {
            playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.noPermission"));
            return;
        }
        UUID uuid = playerRef.getUuid();
        PlotCreatorSessions.remove(uuid);
        World world = store.getExternalData().getWorld();
        PlotCreatorSession session = new PlotCreatorSession(uuid, world);
        applyCommunityMarketplaceDefaults(session.getDraft());
        PlotCreatorSessions.put(session);
        showSessionHud(playerRef, ref, store, session);
        playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.session.started"));
    }

    private static void showSessionHud(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotCreatorSession session
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null && session.getDraft().getStep() != PlotCreatorStep.DONE) {
            PlotCreatorHudSupport.refreshAll(player, playerRef, session);
        }
    }

    public static void cancelSession(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        PlotCreatorSession session = PlotCreatorSessions.remove(playerRef.getUuid());
        if (session == null) {
            return;
        }
        PlotCreatorSubstepGrants.revokeAllIfPresent(session, ref, store);
        PlotCreatorSelectionBoundsService.deactivateIfPresent(playerRef);
        PlotCreatorSelectionBoundsService.restoreNormalStaffInHand(playerRef, ref, store);
        PlotCreatorCleanup.endSession(session, playerRef, true);
    }

    /**
     * Copies builder tool selection bounds into the active session draft.
     *
     * @return plot creator error lang suffix ({@code aetherhaven.plotcreator.error.<suffix>}), or null on success
     */
    @Nullable
    public static String applyBoundsFromBuilderSelection(
        @Nonnull PlotCreatorSession session,
        @Nonnull Vector3i min,
        @Nonnull Vector3i max
    ) {
        PlotCreatorDraft draft = session.getDraft();
        if (draft.isFestivalSizeLocked()) {
            return "boundsLockedFestival";
        }
        String err = PlotCreatorBoundsValidation.validateMinMax(min, max);
        if (err != null) {
            return err;
        }
        PlotCreatorBoundsValidation.commitCorners(draft, min, max);
        draft.setBoundsPhase(PlotCreatorBoundsPhase.SELECTION);
        if (PlotCreatorWallPieceAuthoring.isBoundsSubstep(draft)) {
            PlotCreatorWallPieceAuthoring.commitBoundsToCurrentPiece(draft);
        }
        return null;
    }

    /** Opens a short form for the current text step (identity, tags, variant). */
    public static void openConfigPanel(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotCreatorSession session
    ) {
        PlotCreatorInteractions.openConfigPanel(playerRef, ref, store, session);
    }

    public static void refreshWireframe(@Nonnull PlotCreatorSession session, @Nonnull PlayerRef playerRef) {
        refreshBoundsVisuals(session, playerRef);
    }

    /** Wireframe when not using vanilla selection; wall connection markers on wall pieces. */
    public static void refreshBoundsVisuals(@Nonnull PlotCreatorSession session, @Nonnull PlayerRef playerRef) {
        PlotCreatorDraft draft = session.getDraft();
        World world = session.getWorld();
        UUID uuid = playerRef.getUuid();
        if (PlotCreatorSelectionBoundsService.isActive(uuid) && draft.isEditingBounds()) {
            clearPlotCreatorWireframe(playerRef, world);
            if (draft.getStep() == PlotCreatorStep.WALL_PIECES) {
                PlotCreatorWallConnectionOverlay.draw(playerRef, draft);
            }
            return;
        }
        @Nullable
        BoundsPreview preview = boundsPreview(draft);
        if (preview == null) {
            clearPlotCreatorWireframe(playerRef, world);
            return;
        }
        if (PLOT_CREATOR_WIREFRAME_ACTIVE.containsKey(uuid)) {
            PlotPlacementWireframeOverlay.clearFor(playerRef);
            restoreOtherDebugOverlays(playerRef, world);
        }
        PlotFootprintRecord fp =
            new PlotFootprintRecord(preview.min.x, preview.min.y, preview.min.z, preview.max.x, preview.max.y, preview.max.z);
        PlotPlacementWireframeOverlay.sendWithoutClear(playerRef, fp, true, null);
        PLOT_CREATOR_WIREFRAME_ACTIVE.put(uuid, Boolean.TRUE);
        if (draft.getStep() == PlotCreatorStep.WALL_PIECES) {
            PlotCreatorWallConnectionOverlay.draw(playerRef, draft);
        }
    }

    @Nullable
    static BoundsPreview boundsPreview(@Nonnull PlotCreatorDraft draft) {
        if (draft.getCornerFirst() != null && draft.getCornerSecond() != null) {
            return new BoundsPreview(draft.boundsMin(), draft.boundsMax());
        }
        return null;
    }

    record BoundsPreview(@Nonnull Vector3i min, @Nonnull Vector3i max) {}

    public static void refreshSpawnMarkers(@Nonnull PlotCreatorSession session, @Nonnull PlayerRef playerRef) {
        // Important-spot markers are entity-based (PlotCreatorSpotMarkerSync); debug spheres retired.
    }

    private static void restoreOtherDebugOverlays(@Nonnull PlayerRef playerRef, @Nullable World world) {
        if (world == null || world.getEntityStore() == null) {
            return;
        }
        Ref<EntityStore> ref = world.getEntityStore().getStore().getExternalData().getRefFromUUID(playerRef.getUuid());
        if (ref != null && ref.isValid()) {
            PlotFootprintOverlayRefresh.afterClearDebugShapes(ref, world.getEntityStore().getStore());
        }
    }

    /**
     * Clears only the plot creator bounds wireframe once. Does not call {@link ClearDebugShapes} unless this session
     * had sent a creator wireframe (avoids wiping build staff / plot placement overlays).
     */
    public static void clearPlotCreatorWireframe(@Nullable PlayerRef playerRef, @Nullable World world) {
        if (playerRef == null || PLOT_CREATOR_WIREFRAME_ACTIVE.remove(playerRef.getUuid()) == null) {
            return;
        }
        PlotPlacementWireframeOverlay.clearFor(playerRef);
        restoreOtherDebugOverlays(playerRef, world);
    }

    @Nonnull
    public static List<PlotCreatorStep> stepOrder(@Nonnull PlotCreatorDraft draft) {
        List<PlotCreatorStep> steps = new ArrayList<>();
        steps.add(PlotCreatorStep.WELCOME);
        steps.add(PlotCreatorStep.KIND);
        if (draft.isWallMode()) {
            // Wall styles have five build boxes instead of one, so each piece marks its own box inside WALL_PIECES.
            steps.add(PlotCreatorStep.CONFIGURE);
            steps.add(PlotCreatorStep.WALL_PIECES);
            steps.add(PlotCreatorStep.REVIEW);
            steps.add(PlotCreatorStep.DONE);
            return steps;
        }
        if (draft.isFestivalMode()) {
            // Festivals use a fixed square size; no player bounds step.
            steps.add(PlotCreatorStep.FESTIVAL);
            steps.add(PlotCreatorStep.IMPORTANT_SPOTS);
            List<PlotBuildingKindRequirements.SubstepRequirement> festivalSubs =
                PlotBuildingKindRequirements.forDraft(draft, AetherhavenPlugin.get());
            if (!festivalSubs.isEmpty()) {
                steps.add(PlotCreatorStep.SUBSTEP);
            }
            steps.add(PlotCreatorStep.CONFIGURE);
            steps.add(PlotCreatorStep.PREFAB_SAVE);
            steps.add(PlotCreatorStep.REVIEW);
            steps.add(PlotCreatorStep.DONE);
            return steps;
        }
        if (draft.isPropMode()) {
            if (!draft.isBuildingEditorMode()) {
                steps.add(PlotCreatorStep.BOUNDS);
            }
            steps.add(PlotCreatorStep.CONFIGURE);
            steps.add(PlotCreatorStep.PREFAB_SAVE);
            steps.add(PlotCreatorStep.REVIEW);
            steps.add(PlotCreatorStep.DONE);
            return steps;
        }
        if (!draft.isBuildingEditorMode()) {
            steps.add(PlotCreatorStep.BOUNDS);
        }
        if (draft.hasKind(PlotBuildingKind.VARIANT)) {
            steps.add(PlotCreatorStep.VARIANT);
        }
        if (!draft.isDecorationOnly()) {
            steps.add(PlotCreatorStep.IMPORTANT_SPOTS);
            List<PlotBuildingKindRequirements.SubstepRequirement> subs =
                PlotBuildingKindRequirements.forDraft(draft, AetherhavenPlugin.get());
            if (!subs.isEmpty()) {
                steps.add(PlotCreatorStep.SUBSTEP);
            }
        }
        steps.add(PlotCreatorStep.CONFIGURE);
        steps.add(PlotCreatorStep.PREFAB_SAVE);
        steps.add(PlotCreatorStep.MATERIALS);
        steps.add(PlotCreatorStep.REVIEW);
        steps.add(PlotCreatorStep.DONE);
        return steps;
    }

    /** Steps that open a wizard sub-panel when entered (replace current page; do not setPage(None) first). */
    public static boolean stepAutoOpensPanel(@Nonnull PlotCreatorStep step) {
        return step == PlotCreatorStep.KIND
            || step == PlotCreatorStep.VARIANT
            || step == PlotCreatorStep.FESTIVAL
            || step == PlotCreatorStep.IMPORTANT_SPOTS
            || step == PlotCreatorStep.CONFIGURE;
    }

    public static void advance(@Nonnull PlotCreatorSession session) {
        advance(session, null, null);
    }

    public static void advance(
        @Nonnull PlotCreatorSession session,
        @Nullable Ref<EntityStore> ref,
        @Nullable Store<EntityStore> store
    ) {
        List<PlotCreatorStep> order = stepOrder(session.getDraft());
        PlotCreatorStep current = session.getDraft().getStep();
        int idx = order.indexOf(current);
        if (idx < 0 || idx >= order.size() - 1) {
            return;
        }
        Player player = playerFrom(ref, store);
        if (current == PlotCreatorStep.SUBSTEP && player != null) {
            PlotCreatorSubstepGrants.revokeAll(session, player);
        }
        if (current == PlotCreatorStep.MATERIALS && player != null && ref != null && store != null) {
            PlotCreatorMaterialsHelper.snapshotAndCloseMaterials(session, player, ref, store);
            PlotCreatorMaterialsActions.clearFillConfirm(session);
        }
        PlotCreatorStep next = order.get(idx + 1);
        if (next == PlotCreatorStep.SUBSTEP) {
            session.getDraft().setSubstepIndex(0);
        }
        if (current == PlotCreatorStep.WALL_PIECES) {
            leaveWallPieces(session, player, ref, store);
        }
        if (next == PlotCreatorStep.WALL_PIECES) {
            session.getDraft().setWallPieceIndex(0);
            session.getDraft().setWallPieceSubstepIndex(0);
        }
        if (next == PlotCreatorStep.MATERIALS) {
            PlotCreatorMaterialsActions.onEnterMaterialsStep(session);
        }
        session.getDraft().setStep(next);
        session.getDraft().setMaxReachedStepIndex(Math.max(session.getDraft().getMaxReachedStepIndex(), idx + 1));
        onStepEntered(session, ref, store, next);
    }

    public static void back(@Nonnull PlotCreatorSession session) {
        back(session, null, null);
    }

    public static void back(
        @Nonnull PlotCreatorSession session,
        @Nullable Ref<EntityStore> ref,
        @Nullable Store<EntityStore> store
    ) {
        List<PlotCreatorStep> order = stepOrder(session.getDraft());
        PlotCreatorStep current = session.getDraft().getStep();
        int idx = order.indexOf(current);
        if (idx <= 0) {
            return;
        }
        Player player = playerFrom(ref, store);
        if (current == PlotCreatorStep.SUBSTEP && session.getDraft().getSubstepIndex() > 0) {
            int leaving = session.getDraft().getSubstepIndex();
            session.getDraft().setSubstepIndex(leaving - 1);
            if (player != null) {
                PlotCreatorSubstepGrants.revokeSubstepIndex(session, player, leaving);
            }
            return;
        }
        if (current == PlotCreatorStep.WALL_PIECES) {
            closeMaterialsMenu(session, player, ref, store);
            if (PlotCreatorWallPieceAuthoring.backWithinStep(session.getDraft())) {
                maybeSuggestWallPieceCost(session);
                syncSelectionBoundsMode(session, ref, store);
                return;
            }
        }
        PlotCreatorStep prev = order.get(idx - 1);
        if (current == PlotCreatorStep.BOUNDS && prev == PlotCreatorStep.KIND) {
            session.getDraft().resetBoundsEditing();
        }
        if (current == PlotCreatorStep.WALL_PIECES) {
            leaveWallPieces(session, player, ref, store);
            session.getDraft().resetBoundsEditing();
        }
        if (prev == PlotCreatorStep.WALL_PIECES) {
            PlotCreatorDraft d = session.getDraft();
            d.ensureWallPieces();
            d.setWallPieceIndex(Math.max(0, d.getWallPieces().size() - 1));
            PlotCreatorWallPieceDraft last = d.currentWallPiece();
            d.setWallPieceSubstepIndex(
                last != null ? PlotCreatorWallPieceAuthoring.substepCount(last.getRole()) - 1 : 0
            );
        }
        if (current == PlotCreatorStep.MATERIALS && player != null && ref != null && store != null) {
            PlotCreatorMaterialsHelper.snapshotAndCloseMaterials(session, player, ref, store);
            PlotCreatorMaterialsActions.clearFillConfirm(session);
        }
        if (current == PlotCreatorStep.SUBSTEP && player != null) {
            PlotCreatorSubstepGrants.revokeAll(session, player);
        }
        if (prev == PlotCreatorStep.SUBSTEP) {
            List<PlotBuildingKindRequirements.SubstepRequirement> subs =
                PlotBuildingKindRequirements.forDraft(session.getDraft(), AetherhavenPlugin.get());
            session.getDraft().setSubstepIndex(Math.max(0, subs.size() - 1));
        }
        if (prev == PlotCreatorStep.MATERIALS) {
            PlotCreatorMaterialsActions.onEnterMaterialsStep(session);
        }
        session.getDraft().setStep(prev);
        onStepEntered(session, ref, store, prev);
    }

    /**
     * Jump directly to a previously reached wizard step (step jump menu). Does not re-run forward validation.
     *
     * @return false if the target is unreachable or invalid
     */
    public static boolean jumpToStep(
        @Nonnull PlotCreatorSession session,
        @Nonnull PlotCreatorStep target,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        PlotCreatorDraft draft = session.getDraft();
        if (target == PlotCreatorStep.DONE || draft.getStep() == PlotCreatorStep.DONE) {
            return false;
        }
        List<PlotCreatorStep> order = stepOrder(draft);
        int targetIdx = order.indexOf(target);
        if (targetIdx < 0 || targetIdx > draft.getMaxReachedStepIndex()) {
            return false;
        }
        PlotCreatorStep current = draft.getStep();
        if (current == target) {
            return true;
        }
        int currentIdx = order.indexOf(current);
        if (currentIdx < 0) {
            return false;
        }
        Player player = playerFrom(ref, store);
        leaveStepForJump(session, current, target, order, player, ref, store);
        prepareEnterStepForJump(session, current, target, order);
        draft.setStep(target);
        onStepEntered(session, ref, store, target);
        return true;
    }

    private static void leaveStepForJump(
        @Nonnull PlotCreatorSession session,
        @Nonnull PlotCreatorStep current,
        @Nonnull PlotCreatorStep target,
        @Nonnull List<PlotCreatorStep> order,
        @Nullable Player player,
        @Nullable Ref<EntityStore> ref,
        @Nullable Store<EntityStore> store
    ) {
        PlotCreatorDraft draft = session.getDraft();
        if (current == PlotCreatorStep.SUBSTEP && player != null) {
            PlotCreatorSubstepGrants.revokeAll(session, player);
        }
        if (current == PlotCreatorStep.MATERIALS && player != null && ref != null && store != null) {
            PlotCreatorMaterialsHelper.snapshotAndCloseMaterials(session, player, ref, store);
            PlotCreatorMaterialsActions.clearFillConfirm(session);
        }
        if (current == PlotCreatorStep.BOUNDS
            && (target == PlotCreatorStep.WELCOME || target == PlotCreatorStep.KIND)) {
            draft.resetBoundsEditing();
        }
        if (current == PlotCreatorStep.WALL_PIECES) {
            leaveWallPieces(session, player, ref, store);
            draft.resetBoundsEditing();
        }
    }

    /** Keeps the box and the build cost of the piece being authored before the wizard moves off the wall step. */
    private static void leaveWallPieces(
        @Nonnull PlotCreatorSession session,
        @Nullable Player player,
        @Nullable Ref<EntityStore> ref,
        @Nullable Store<EntityStore> store
    ) {
        // Closing first so anything still sitting in the deposit chest is counted before the cost is copied over.
        closeMaterialsMenu(session, player, ref, store);
        PlotCreatorWallPieceAuthoring.commitBoundsToCurrentPiece(session.getDraft());
        PlotCreatorWallPieceAuthoring.commitMaterialsToCurrentPiece(session.getDraft());
    }

    private static void closeMaterialsMenu(
        @Nonnull PlotCreatorSession session,
        @Nullable Player player,
        @Nullable Ref<EntityStore> ref,
        @Nullable Store<EntityStore> store
    ) {
        if (player != null && ref != null && store != null) {
            PlotCreatorMaterialsHelper.snapshotAndCloseMaterials(session, player, ref, store);
        }
        PlotCreatorMaterialsActions.clearFillConfirm(session);
    }

    private static void prepareEnterStepForJump(
        @Nonnull PlotCreatorSession session,
        @Nonnull PlotCreatorStep current,
        @Nonnull PlotCreatorStep target,
        @Nonnull List<PlotCreatorStep> order
    ) {
        PlotCreatorDraft draft = session.getDraft();
        int currentIdx = order.indexOf(current);
        int targetIdx = order.indexOf(target);
        if (target == PlotCreatorStep.SUBSTEP && currentIdx < targetIdx) {
            draft.setSubstepIndex(0);
        }
        if (target == PlotCreatorStep.WALL_PIECES && currentIdx < targetIdx) {
            draft.setWallPieceIndex(0);
            draft.setWallPieceSubstepIndex(0);
        }
        if (target == PlotCreatorStep.MATERIALS) {
            PlotCreatorMaterialsActions.onEnterMaterialsStep(session);
        }
    }

    private static void onStepEntered(
        @Nonnull PlotCreatorSession session,
        @Nullable Ref<EntityStore> ref,
        @Nullable Store<EntityStore> store,
        @Nonnull PlotCreatorStep step
    ) {
        if (ref == null || store == null) {
            return;
        }
        Player player = playerFrom(ref, store);
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (player == null || playerRef == null) {
            return;
        }
        if (step == PlotCreatorStep.SUBSTEP) {
            PlotCreatorSubstepGrants.grantCurrentSubstep(session, player, ref, store);
        }
        if (step == PlotCreatorStep.BOUNDS) {
            session.getDraft().setBoundsPhase(PlotCreatorBoundsPhase.SELECTION);
            PlotCreatorService.refreshBoundsVisuals(session, playerRef);
        }
        if (step == PlotCreatorStep.WALL_PIECES) {
            PlotCreatorWallPieceAuthoring.enterCurrentPiece(session.getDraft());
            maybeSuggestWallPieceCost(session);
            PlotCreatorService.refreshBoundsVisuals(session, playerRef);
        }
        syncSelectionBoundsMode(session, ref, store);
        if (step == PlotCreatorStep.KIND) {
            PlotCreatorInteractions.openKindPanel(playerRef, ref, store, session);
        }
        if (step == PlotCreatorStep.VARIANT) {
            PlotCreatorInteractions.openConfigPanel(playerRef, ref, store, session);
        }
        if (step == PlotCreatorStep.FESTIVAL) {
            PlotCreatorInteractions.openFestivalPanel(playerRef, ref, store, session);
        }
        if (step == PlotCreatorStep.IMPORTANT_SPOTS) {
            seedImportantSpotsIfEmpty(session.getDraft());
            World world = store.getExternalData().getWorld();
            world.execute(
                () -> {
                    if (!ref.isValid()) {
                        return;
                    }
                    PlotCreatorInteractions.openImportantSpotsPanel(playerRef, ref, store, session);
                }
            );
        }
        if (step == PlotCreatorStep.CONFIGURE) {
            PlotCreatorInteractions.openConfigurePanel(playerRef, ref, store, session);
        }
    }

    /** Seeds {@link PlotCreatorDraft#getSelectedSpots()} from defaults when empty (before the chooser). */
    public static void seedImportantSpotsIfEmpty(@Nonnull PlotCreatorDraft draft) {
        if (!draft.getSelectedSpots().isEmpty()) {
            ensureRequiredSpots(draft);
            return;
        }
        for (PlotBuildingKindRequirements.SubstepRequirement req :
            PlotBuildingKindRequirements.defaultRequirements(draft, AetherhavenPlugin.get())) {
            draft.getSelectedSpots().add(req.toSpotEntry());
        }
        ensureRequiredSpots(draft);
    }

    /** Restores spots that cannot be deselected for the draft's effective building kinds. */
    public static void ensureRequiredSpots(@Nonnull PlotCreatorDraft draft) {
        if (draft.isFestivalMode()) {
            PlotCreatorFestivalMechanicDefaults.ensureRequiredSelectedSpots(draft);
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        List<PlotBuildingKind> kinds = PlotBuildingKindRequirements.effectiveKinds(draft, plugin);
        boolean isShop = kinds.contains(PlotBuildingKind.SHOP) || kinds.contains(PlotBuildingKind.PLAYER_SHOP);
        boolean isPlayerShop = kinds.contains(PlotBuildingKind.PLAYER_SHOP)
            || PlotBuildingKindRequirements.requiresShopSafe(draft, plugin);
        List<String> shopWorkRoles = isShop
            ? PlotBuildingKindRequirements.workplaceRolesForDraft(draft, plugin)
            : List.of();
        String shopWorkRole = shopWorkRoles.isEmpty() ? null : shopWorkRoles.get(0);

        boolean hasManagement = false;
        boolean hasInnBell = false;
        boolean hasGaiaStatue = false;
        boolean hasPriestessWork = false;
        boolean hasShopSafe = false;
        boolean hasShopSpot = false;
        boolean hasShopPoi = false;
        boolean hasTouristVisit = false;
        boolean hasShopWork = false;
        for (PlotCreatorSpotEntry entry : draft.getSelectedSpots()) {
            if (entry.type() == PlotCreatorSubstepType.MANAGEMENT_BLOCK) {
                hasManagement = true;
            } else if (entry.type() == PlotCreatorSubstepType.INN_BELL_BLOCK) {
                hasInnBell = true;
            } else if (entry.type() == PlotCreatorSubstepType.GAIA_STATUE_BLOCK) {
                hasGaiaStatue = true;
            } else if (entry.type() == PlotCreatorSubstepType.WORK_POI
                && TownVillagerBinding.KIND_PRIESTESS.equals(entry.workResidentKind())) {
                hasPriestessWork = true;
            } else if (entry.type() == PlotCreatorSubstepType.SHOP_SAFE_BLOCK) {
                hasShopSafe = true;
            } else if (entry.type() == PlotCreatorSubstepType.SHOP_SPOT) {
                hasShopSpot = true;
            } else if (entry.type() == PlotCreatorSubstepType.SHOP_POI) {
                hasShopPoi = true;
            } else if (entry.type() == PlotCreatorSubstepType.TOURIST_VISIT_POI) {
                hasTouristVisit = true;
            }
            if (isShop && entry.type() == PlotCreatorSubstepType.WORK_POI) {
                if (shopWorkRole == null || shopWorkRole.isBlank()) {
                    if (entry.workResidentKind() == null || entry.workResidentKind().isBlank()) {
                        hasShopWork = true;
                    }
                } else if (shopWorkRole.equals(entry.workResidentKind())) {
                    hasShopWork = true;
                }
            }
        }
        if (!hasManagement) {
            draft.getSelectedSpots().add(0, PlotCreatorSpotEntry.of(PlotCreatorSubstepType.MANAGEMENT_BLOCK, 1));
        }
        if (!hasInnBell && kinds.contains(PlotBuildingKind.INN)) {
            draft.getSelectedSpots().add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.INN_BELL_BLOCK, 1));
        }
        if (!hasGaiaStatue && PlotBuildingKindRequirements.requiresGaiaStatue(draft, plugin)) {
            draft.getSelectedSpots().add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.GAIA_STATUE_BLOCK, 1));
        }
        if (!hasPriestessWork && PlotBuildingKindRequirements.requiresGaiaStatue(draft, plugin)) {
            draft.getSelectedSpots().add(PlotCreatorSpotEntry.work(TownVillagerBinding.KIND_PRIESTESS, 1));
        }
        if (isShop) {
            if (isPlayerShop && !hasShopSafe) {
                draft.getSelectedSpots().add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.SHOP_SAFE_BLOCK, 1));
            }
            if (!hasShopWork) {
                if (shopWorkRole != null && !shopWorkRole.isBlank()) {
                    draft.getSelectedSpots().add(PlotCreatorSpotEntry.work(shopWorkRole, 1));
                } else {
                    draft.getSelectedSpots().add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.WORK_POI, 1));
                }
            }
            boolean needsNpcShopSpots = PlotBuildingKindRequirements.requiresNpcShopSpots(draft, plugin);
            if (!needsNpcShopSpots) {
                draft.getSelectedSpots().removeIf(e -> e.type() == PlotCreatorSubstepType.SHOP_SPOT);
            } else if (!hasShopSpot) {
                draft.getSelectedSpots().add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.SHOP_SPOT, 1));
            }
            if (!hasShopPoi) {
                draft.getSelectedSpots().add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.SHOP_POI, 1));
            }
            if (!hasTouristVisit) {
                draft.getSelectedSpots().add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.TOURIST_VISIT_POI, 1));
            }
        }
    }

    /**
     * Commits important-spot choices: seed defaults if needed, restore required spots, mark confirmed.
     */
    public static void confirmImportantSpots(@Nonnull PlotCreatorDraft draft) {
        seedImportantSpotsIfEmpty(draft);
        ensureRequiredSpots(draft);
        draft.setImportantSpotsConfirmed(true);
    }

    private static void syncSelectionBoundsMode(
        @Nonnull PlotCreatorSession session,
        @Nullable Ref<EntityStore> ref,
        @Nullable Store<EntityStore> store
    ) {
        if (ref == null || store == null) {
            return;
        }
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null) {
            return;
        }
        PlotCreatorSelectionBoundsService.syncForSession(session, playerRef, ref, store);
    }

    @Nullable
    private static Player playerFrom(@Nullable Ref<EntityStore> ref, @Nullable Store<EntityStore> store) {
        if (ref == null || store == null) {
            return null;
        }
        return store.getComponent(ref, Player.getComponentType());
    }

    @Nullable
    public static PlotBuildingKindRequirements.SubstepRequirement currentSubstep(@Nonnull PlotCreatorDraft draft) {
        List<PlotBuildingKindRequirements.SubstepRequirement> subs =
            PlotBuildingKindRequirements.forDraft(draft, AetherhavenPlugin.get());
        int i = draft.getSubstepIndex();
        if (i < 0 || i >= subs.size()) {
            return null;
        }
        return subs.get(i);
    }

    public static boolean advanceSubstepOrStep(@Nonnull PlotCreatorSession session) {
        return advanceSubstepOrStep(session, null, null);
    }

    public static boolean advanceSubstepOrStep(
        @Nonnull PlotCreatorSession session,
        @Nullable Ref<EntityStore> ref,
        @Nullable Store<EntityStore> store
    ) {
        List<PlotBuildingKindRequirements.SubstepRequirement> subs =
            PlotBuildingKindRequirements.forDraft(session.getDraft(), AetherhavenPlugin.get());
        if (session.getDraft().getStep() == PlotCreatorStep.SUBSTEP
            && session.getDraft().getSubstepIndex() + 1 < subs.size()) {
            int leaving = session.getDraft().getSubstepIndex();
            session.getDraft().setSubstepIndex(leaving + 1);
            Player player = playerFrom(ref, store);
            if (player != null) {
                PlotCreatorSubstepGrants.revokeSubstepIndex(session, player, leaving);
                PlotCreatorSubstepGrants.grantCurrentSubstep(session, player, ref, store);
            }
            return true;
        }
        advance(session, ref, store);
        return false;
    }

    /**
     * Moves to the next wall piece substep, or leaves the wall step when the last piece is finished.
     *
     * @return true when it stayed inside the wall step
     */
    public static boolean advanceWallPieceOrStep(
        @Nonnull PlotCreatorSession session,
        @Nullable Ref<EntityStore> ref,
        @Nullable Store<EntityStore> store
    ) {
        closeMaterialsMenu(session, playerFrom(ref, store), ref, store);
        if (PlotCreatorWallPieceAuthoring.advanceWithinStep(session.getDraft())) {
            maybeSuggestWallPieceCost(session);
            syncSelectionBoundsMode(session, ref, store);
            return true;
        }
        advance(session, ref, store);
        return false;
    }

    /** Arriving at the build cost of a piece with nothing set yet suggests what its blocks are worth. */
    private static void maybeSuggestWallPieceCost(@Nonnull PlotCreatorSession session) {
        if (PlotCreatorWallPieceAuthoring.isMaterialsSubstep(session.getDraft())) {
            PlotCreatorWallPieceMaterials.autoFillIfEmpty(session);
        }
    }

    public static boolean saveAndFinish(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PlotCreatorSession session,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        PlotCreatorDraft draft = session.getDraft();
        if (draft.isFestivalMode()) {
            return saveAndFinishFestival(plugin, session, playerRef, draft);
        }
        if (draft.isWallMode()) {
            return saveAndFinishWallStyle(plugin, session, playerRef, draft);
        }
        if (draft.isPropMode()) {
            return saveAndFinishProp(plugin, session, playerRef, draft);
        }
        applyDefaultTagsForKind(draft);
        applyTagsInput(draft);
        applyConfigureInput(draft);
        if (draft.isDecorationOnly() && !draft.getBuildingTags().contains("decoration")) {
            draft.getBuildingTags().add("decoration");
        }
        if (draft.getPlotAnchor() == null && PlotCreatorAnchorRules.hasBounds(draft)) {
            PlotCreatorAutoAnchor.applyCenter(draft);
        }
        if (draft.getPlotAnchor() != null) {
            PlotCreatorLocalCoords.recomputeAnchorOffset(draft);
        }
        String err = PlotCreatorValidator.validateBeforeSave(draft, plugin);
        if (err != null) {
            String messageKey = err.startsWith("substep_")
                ? "aetherhaven_plot_creator.aetherhaven.plotcreator.substep." + err.substring("substep_".length())
                : "aetherhaven_plot_creator.aetherhaven.plotcreator.error." + err;
            playerRef.sendMessage(Message.translation(messageKey));
            return false;
        }
        if (draft.isBuildingEditorMode()) {
            return saveAndFinishBuildingEditor(plugin, session, playerRef, draft);
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        SpendBreakdown saveFeePaid = null;
        TownRecord feeTown = null;
        TownManager feeTm = null;
        if (player == null || player.getGameMode() != GameMode.Creative) {
            CombinedItemContainer inv = InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
            if (inv == null) {
                playerRef.sendMessage(
                    Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.insufficientGold")
                );
                return false;
            }
            World feeWorld = store.getExternalData().getWorld();
            feeTm = AetherhavenWorldRegistries.getOrCreateTownManager(feeWorld, plugin);
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            feeTown = uc != null ? TownPlayerResolution.resolveActiveTown(feeWorld, store, ref, feeTm) : null;
            boolean allowTreasury = uc != null && feeTown != null && feeTown.playerCanSpendTreasuryGold(uc.getUuid());
            saveFeePaid =
                GoldCoinPayment.trySpendReturningBreakdown(feeTown, inv, SURVIVAL_SAVE_GOLD_COST, allowTreasury);
            if (saveFeePaid == null) {
                playerRef.sendMessage(
                    Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.insufficientGold")
                );
                return false;
            }
            if (saveFeePaid.fromTreasury() > 0L && feeTown != null) {
                feeTm.updateTown(feeTown);
            }
        }
        if (draft.isSubmitToCommunity() && plugin.getConfig().get().getCommunityMarketplace().isEnabled()) {
            try {
                String remapErr =
                    CommunitySubmitLocalSave.prepareDraftForCommunitySubmit(plugin, draft, playerRef.getUuid());
                if (remapErr != null) {
                    refundSaveFeeIfNeeded(feeTown, feeTm, player, ref, store, saveFeePaid);
                    playerRef.sendMessage(
                        Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error." + remapErr)
                    );
                    return false;
                }
            } catch (Exception e) {
                refundSaveFeeIfNeeded(feeTown, feeTm, player, ref, store, saveFeePaid);
                playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.saveFailed"));
                return false;
            }
        }
        Path buildingFile = CustomBuildingsPaths.buildingFile(plugin.getDataDirectory(), draft.getConstructionId().trim());
        try {
            PlotCreatorJsonWriter.writeBuilding(buildingFile, draft);
        } catch (Exception e) {
            refundSaveFeeIfNeeded(feeTown, feeTm, player, ref, store, saveFeePaid);
            playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.saveFailed"));
            return false;
        }
        String prefabPath = draft.getPrefabPath();
        if (prefabPath != null && !prefabPath.isBlank()) {
            plugin.getPrefabMaterialsService().generateOne(draft.getConstructionId().trim(), prefabPath, plugin.getDataDirectory());
        }
        plugin.reloadConfigsAndAssetCatalogs();
        Path iconFile = CustomBuildingsPaths.iconFile(plugin.getDataDirectory(), draft.getConstructionId().trim());
        if (Files.isRegularFile(iconFile)) {
            CustomBuildingIconAssetRegistry.registerIconFile(plugin, iconFile);
        }
        if (draft.isSubmitToCommunity() && plugin.getConfig().get().getCommunityMarketplace().isEnabled()) {
            String playerName = playerRef.getUsername() != null ? playerRef.getUsername() : "Unknown";
            String submitErr = CommunitySubmissionService.submitSavedBuilding(
                plugin,
                playerRef.getUuid(),
                playerName,
                draft.getConstructionId().trim()
            );
            CommunitySubmissionService.notifyPlayer(playerRef, submitErr);
        }
        World world = session.getWorld();
        String registerErr = PlotCreatorWorldRegistrar.registerInTown(world, plugin, playerRef.getUuid(), draft, store);
        if (registerErr == null) {
            draft.setStep(PlotCreatorStep.DONE);
            playerRef.sendMessage(
                Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.success.registered")
                    .param("id", draft.getConstructionId())
            );
            if (player != null) {
                PlotTokenInventory.giveToPlayer(player, draft.getConstructionId().trim(), 1, draft.getDisplayName(), ref, store);
            }
            endSessionAfterSave(playerRef, session);
            return true;
        }
        if ("noTown".equals(registerErr)) {
            playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.noTown"));
        } else if ("signBlocked".equals(registerErr)) {
            playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.signBlocked"));
        } else if ("incomplete".equals(registerErr) || "unknownConstruction".equals(registerErr)) {
            playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error." + registerErr));
        } else {
            playerRef.sendMessage(Message.raw(registerErr));
        }
        if (player != null) {
            PlotTokenInventory.giveToPlayer(player, draft.getConstructionId().trim(), 1, draft.getDisplayName(), ref, store);
        }
        draft.setStep(PlotCreatorStep.DONE);
        playerRef.sendMessage(
            Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.success.savedTokenFallback")
                .param("id", draft.getConstructionId())
        );
        endSessionAfterSave(playerRef, session);
        return true;
    }

    private static void refundSaveFeeIfNeeded(
        @Nullable TownRecord town,
        @Nullable TownManager tm,
        @Nullable Player player,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nullable SpendBreakdown saveFeePaid
    ) {
        if (saveFeePaid == null || player == null) {
            return;
        }
        GoldCoinPayment.refund(town, player, ref, store, saveFeePaid);
        if (saveFeePaid.fromTreasury() > 0L && town != null && tm != null) {
            tm.updateTown(town);
        }
    }

    /**
     * Writes a prop definition and prefab. Props are placed with the prop item, so nothing is registered in the town
     * and no plot token is handed out.
     */
    private static boolean saveAndFinishProp(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PlotCreatorSession session,
        @Nonnull PlayerRef playerRef,
        @Nonnull PlotCreatorDraft draft
    ) {
        applyConfigureInput(draft);
        if (draft.getPlotAnchor() == null && PlotCreatorAnchorRules.hasBounds(draft)) {
            PlotCreatorAutoAnchor.applyCenter(draft);
        }
        if (draft.getPlotAnchor() != null) {
            PlotCreatorLocalCoords.recomputeAnchorOffset(draft);
        }
        String err = PlotCreatorValidator.validatePropBeforeSave(draft, plugin);
        if (err != null) {
            playerRef.sendMessage(
                Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error." + err)
            );
            return false;
        }
        boolean submit =
            draft.isSubmitToCommunity() && plugin.getConfig().get().getCommunityMarketplace().isEnabled();
        String propId = draft.getConstructionId().trim();
        if (submit) {
            propId =
                com.hexvane.aetherhaven.community.CommunityPropValidator.assignCatalogId(
                    propId, draft.getDisplayName(), playerRef.getUuid()
                );
            draft.setConstructionId(propId);
            syncPrefabFileNameFromConstructionId(draft);
        }
        if (plugin.getPropCatalog().contains(propId) && draft.getEditingConstructionId() == null) {
            playerRef.sendMessage(
                Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.id_taken")
            );
            return false;
        }
        String prefabKey = draft.getPrefabPath();
        if (prefabKey == null || prefabKey.isBlank()) {
            playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.incomplete"));
            return false;
        }
        // Always write the prefab under the final id/path before icon render or community submit.
        // Community remap changes the key; an earlier export under the local id would miss.
        Path prefabOut =
            com.hexvane.aetherhaven.prop.PropPaths.propPrefabFile(
                plugin.getDataDirectory(),
                com.hexvane.aetherhaven.prop.PropPaths.prefabFileNameFromKey(prefabKey)
            );
        PlotCreatorPrefabExporter.ExportResult exported =
            PlotCreatorPrefabExporter.export(session.getWorld(), draft, prefabOut, true, false);
        if (exported != PlotCreatorPrefabExporter.ExportResult.SUCCESS) {
            playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.prefabExport"));
            return false;
        }
        draft.setPrefabPath(prefabKey);
        draft.setPrefabFileName(com.hexvane.aetherhaven.prop.PropPaths.prefabFileNameFromKey(prefabKey));
        var def =
            com.hexvane.aetherhaven.prop.PropDefinition.create(
                propId,
                draft.getDisplayName(),
                prefabKey.trim(),
                null,
                draft.getFrontFacing(),
                draft.getPropGoldPrice()
            );
        if (!plugin.getPropCatalog().persist(def)) {
            playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.saveFailed"));
            return false;
        }
        // Always try the website greenscreen render for a local icon, even when not submitting.
        PropIconRenderClient.Result iconResult = PropIconRenderClient.generateAndWire(plugin, propId);
        if (iconResult != PropIconRenderClient.Result.SUCCESS
            && iconResult != PropIconRenderClient.Result.DISABLED) {
            HytaleLogger.forEnclosingClass()
                .atWarning()
                .log("Prop icon generation after save returned %s for %s", iconResult, propId);
        }
        if (submit) {
            String playerName = playerRef.getUsername() != null ? playerRef.getUsername() : "Unknown";
            String submitErr =
                CommunitySubmissionService.submitSavedProp(plugin, playerRef.getUuid(), playerName, propId);
            CommunitySubmissionService.notifyPlayer(playerRef, submitErr);
        }
        draft.setStep(PlotCreatorStep.DONE);
        playerRef.sendMessage(
            Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.success.propSaved")
                .param("name", draft.getDisplayName() != null ? draft.getDisplayName() : propId)
        );
        endSessionAfterSave(playerRef, session);
        return true;
    }

    /**
     * Writes every piece of an authored wall style. Walls are placed with the wand, so nothing is registered in the
     * town and no plot token is handed out.
     */
    private static boolean saveAndFinishWallStyle(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PlotCreatorSession session,
        @Nonnull PlayerRef playerRef,
        @Nonnull PlotCreatorDraft draft
    ) {
        applyTagsInput(draft);
        String err = PlotCreatorValidator.validateWallStyleBeforeSave(draft, plugin);
        if (err != null) {
            playerRef.sendMessage(
                Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error." + err)
            );
            return false;
        }
        boolean submit =
            draft.isSubmitToCommunity() && plugin.getConfig().get().getCommunityMarketplace().isEnabled();
        String baseId = PlotCreatorWallStyleIds.baseId(draft);
        if (baseId == null) {
            playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.wallIdPrefix"));
            return false;
        }
        if (submit) {
            baseId =
                com.hexvane.aetherhaven.community.CommunityBuildingValidator.assignCatalogId(
                    baseId, draft.getDisplayName(), playerRef.getUuid()
                );
        }
        // Editing a style in the building editor writes over the pieces it loaded, so only a different style's ids clash.
        if (!PlotCreatorWallStyleIds.isSavingLoadedStyle(draft, baseId)) {
            for (String pieceId : PlotCreatorWallStyleIds.pieceConstructionIds(draft, baseId)) {
                if (plugin.getConstructionCatalog().get(pieceId) != null) {
                    playerRef.sendMessage(
                        Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.id_taken")
                    );
                    return false;
                }
            }
        }
        List<PlotCreatorWallStyleWriter.SavedPiece> saved;
        try {
            saved = PlotCreatorWallStyleWriter.write(plugin, session.getWorld(), draft, baseId, submit);
        } catch (Exception e) {
            playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.saveFailed"));
            return false;
        }
        if (saved == null) {
            playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.saveFailed"));
            return false;
        }
        for (PlotCreatorWallStyleWriter.SavedPiece piece : saved) {
            plugin.getPrefabMaterialsService()
                .generateOne(piece.constructionId(), piece.prefabPathKey(), piece.writeRoot());
        }
        plugin.reloadConfigsAndAssetCatalogs();
        for (PlotCreatorWallStyleWriter.SavedPiece piece : saved) {
            Path iconFile = CustomBuildingsPaths.iconFile(piece.writeRoot(), piece.constructionId());
            if (Files.isRegularFile(iconFile)) {
                CustomBuildingIconAssetRegistry.registerIconFile(plugin, iconFile);
            }
        }
        if (submit) {
            String playerName = playerRef.getUsername() != null ? playerRef.getUsername() : "Unknown";
            String submitErr = null;
            for (PlotCreatorWallStyleWriter.SavedPiece piece : saved) {
                String pieceErr = CommunitySubmissionService.submitSavedBuilding(
                    plugin,
                    playerRef.getUuid(),
                    playerName,
                    piece.constructionId()
                );
                if (pieceErr != null && submitErr == null) {
                    submitErr = pieceErr;
                }
            }
            CommunitySubmissionService.notifyPlayer(playerRef, submitErr);
        }
        draft.setStep(PlotCreatorStep.DONE);
        playerRef.sendMessage(
            Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.success.wallStyleSaved")
                .param("name", draft.getDisplayName() != null ? draft.getDisplayName() : baseId)
        );
        endSessionAfterSave(playerRef, session);
        return true;
    }

    /** Writes the festival JSON and reloads the catalog. Festivals are not plots, so no token is handed out. */
    private static boolean saveAndFinishFestival(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PlotCreatorSession session,
        @Nonnull PlayerRef playerRef,
        @Nonnull PlotCreatorDraft draft
    ) {
        String settingsError = PlotCreatorFestivalSettings.applyInput(draft);
        if (settingsError != null) {
            playerRef.sendMessage(
                Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error." + settingsError)
            );
            return false;
        }
        String err = PlotCreatorValidator.validateBeforeSave(draft, plugin);
        if (err != null) {
            String messageKey = err.startsWith("substep_")
                ? "aetherhaven_plot_creator.aetherhaven.plotcreator.substep." + err.substring("substep_".length())
                : "aetherhaven_plot_creator.aetherhaven.plotcreator.error." + err;
            playerRef.sendMessage(Message.translation(messageKey));
            return false;
        }
        boolean communityEnabled = plugin.getConfig().get().getCommunityMarketplace().isEnabled();
        boolean submitLook = draft.isFestivalLookMode() && draft.isSubmitToCommunity() && communityEnabled;
        if (submitLook) {
            try {
                String remapErr =
                    CommunitySubmitLocalSave.prepareDraftForFestivalLookSubmit(plugin, draft, playerRef.getUuid());
                if (remapErr != null) {
                    playerRef.sendMessage(
                        Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error." + remapErr)
                    );
                    return false;
                }
            } catch (Exception e) {
                playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.saveFailed"));
                return false;
            }
        }
        String id = draft.getFestivalId();
        if (id == null) {
            playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.incomplete"));
            return false;
        }
        FestivalDefinition existing =
            draft.getEditingFestivalId() != null ? plugin.getFestivalCatalog().get(draft.getEditingFestivalId()) : null;
        try {
            Path writeRoot = BuildingEditorSavePaths.resolveWriteRootForFestival(plugin, id);
            PlotCreatorFestivalJsonWriter.writeFestival(
                BuildingEditorSavePaths.festivalFile(writeRoot, id),
                draft,
                existing
            );
        } catch (Exception e) {
            playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.saveFailed"));
            return false;
        }
        plugin.reloadConfigsAndAssetCatalogs();
        if (submitLook) {
            String playerName = playerRef.getUsername() != null ? playerRef.getUsername() : "Unknown";
            String submitErr =
                CommunitySubmissionService.submitSavedBuilding(plugin, playerRef.getUuid(), playerName, id);
            CommunitySubmissionService.notifyPlayer(playerRef, submitErr);
        }
        draft.setStep(PlotCreatorStep.DONE);
        playerRef.sendMessage(
            Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.success.festivalSaved")
                .param("name", draft.getDisplayName() != null ? draft.getDisplayName() : id)
        );
        endSessionAfterSave(playerRef, session);
        return true;
    }

    private static boolean saveAndFinishBuildingEditor(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PlotCreatorSession session,
        @Nonnull PlayerRef playerRef,
        @Nonnull PlotCreatorDraft draft
    ) {
        String id = draft.getConstructionId().trim();
        if (draft.getEditingConstructionId() != null && !id.equals(draft.getEditingConstructionId())) {
            draft.setConstructionId(draft.getEditingConstructionId());
            id = draft.getEditingConstructionId();
        }
        boolean communityEnabled = plugin.getConfig().get().getCommunityMarketplace().isEnabled();
        boolean submitChecked = draft.isSubmitToCommunity() && !draft.isCommunitySubmissionEdit();
        if (submitChecked && communityEnabled) {
            try {
                String remapErr =
                    CommunitySubmitLocalSave.prepareDraftForCommunitySubmit(plugin, draft, playerRef.getUuid());
                if (remapErr != null) {
                    playerRef.sendMessage(
                        Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error." + remapErr)
                    );
                    return false;
                }
            } catch (Exception e) {
                playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.saveFailed"));
                return false;
            }
            id = draft.getConstructionId().trim();
        }
        String prefabKey =
            submitChecked && communityEnabled
                ? draft.getPrefabPath()
                : (draft.getLockedPrefabPathKey() != null ? draft.getLockedPrefabPathKey() : draft.getPrefabPath());
        Path writeRoot =
            submitChecked && communityEnabled
                ? plugin.getDataDirectory().toAbsolutePath().normalize()
                : BuildingEditorSavePaths.resolveWriteRoot(plugin, id);
        Path prefabOut = BuildingEditorSavePaths.prefabFile(writeRoot, prefabKey);
        PlotCreatorPrefabExporter.ExportResult exported = PlotCreatorPrefabExporter.export(session.getWorld(), draft, prefabOut, true);
        if (exported != PlotCreatorPrefabExporter.ExportResult.SUCCESS) {
            playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.prefabExport"));
            return false;
        }
        if (prefabKey != null) {
            draft.setPrefabPath(prefabKey);
            draft.setPrefabFileName(prefabKey);
        }
        Path buildingFile = BuildingEditorSavePaths.buildingFile(writeRoot, id);
        try {
            BuildingEditorJsonWriter.writeMerged(buildingFile, draft, draft.getOriginalBuildingJsonSnapshot());
        } catch (Exception e) {
            playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.saveFailed"));
            return false;
        }
        String prefabPath = draft.getPrefabPath();
        if (prefabPath != null && !prefabPath.isBlank()) {
            plugin.getPrefabMaterialsService().generateOne(id, prefabPath, writeRoot);
        }
        if (draft.isCommunitySubmissionEdit() && communityEnabled) {
            String playerName = playerRef.getUsername() != null ? playerRef.getUsername() : "Unknown";
            CommunitySubmissionService.UpdateOutcome outcome = new CommunitySubmissionService.UpdateOutcome();
            String submitErr =
                CommunitySubmissionService.updateSavedBuilding(
                    plugin,
                    playerRef.getUuid(),
                    playerName,
                    id,
                    outcome
                );
            boolean waitingForReview = draft.isCommunitySubmissionApproved() || outcome.isWaitingForReview();
            CommunitySubmissionService.notifyUpdatePlayer(playerRef, submitErr, waitingForReview);
            if (submitErr != null) {
                return false;
            }
        } else if (submitChecked && communityEnabled) {
            String playerName = playerRef.getUsername() != null ? playerRef.getUsername() : "Unknown";
            String submitErr =
                CommunitySubmissionService.submitSavedBuilding(plugin, playerRef.getUuid(), playerName, id);
            CommunitySubmissionService.notifyPlayer(playerRef, submitErr);
        }
        plugin.reloadConfigsAndAssetCatalogs();
        draft.setStep(PlotCreatorStep.DONE);
        playerRef.sendMessage(
            Message.translation("aetherhaven_building_editor.aetherhaven.buildingeditor.success.saved")
                .param("id", id)
        );
        endSessionAfterSave(playerRef, session);
        return true;
    }

    private static void endSessionAfterSave(@Nonnull PlayerRef playerRef, @Nonnull PlotCreatorSession session) {
        PlotCreatorSessions.remove(playerRef.getUuid());
        // Building editor and festival authoring paste a full box; clear it after save as well as cancel.
        PlotCreatorDraft draft = session.getDraft();
        boolean removeWorldArtifacts = draft.isBuildingEditorMode() || draft.isFestivalMode();
        PlotCreatorCleanup.endSession(session, playerRef, removeWorldArtifacts);
    }

    public static void applyDefaultTagsForKind(@Nonnull PlotCreatorDraft draft) {
        if (draft.hasKind(PlotBuildingKind.VARIANT) && draft.getCountsAsConstructionIds().isEmpty()) {
            return;
        }
        List<PlotBuildingKind> kinds = PlotBuildingKindRequirements.effectiveKinds(draft, AetherhavenPlugin.get());
        for (PlotBuildingKind kind : kinds) {
            switch (kind) {
                case AMENITY, SHOP, PLAYER_SHOP, INN -> draft.setTouristDestination(true);
                default -> {}
            }
        }
        if (!draft.getBuildingTags().isEmpty() || draft.getBuildingTagsInput() != null) {
            return;
        }
        for (PlotBuildingKind kind : kinds) {
            switch (kind) {
                case AMENITY -> {
                    addTagOnce(draft, "amenity");
                    addTagOnce(draft, "fun");
                    draft.setScheduleSharedUtilityPick(true);
                }
                case HOME -> addTagOnce(draft, "home");
                case WORK -> addTagOnce(draft, "work");
                case SHOP, PLAYER_SHOP -> {
                    addTagOnce(draft, "shop");
                    addTagOnce(draft, "work");
                }
                case INN -> addTagOnce(draft, "civic");
                case TOWN_HALL, GUILD_HALL -> addTagOnce(draft, "civic");
                case TOURIST_PORTAL -> addTagOnce(draft, "civic");
                case DECORATION -> addTagOnce(draft, "decoration");
                default -> {}
            }
        }
        if (!draft.getBuildingTags().isEmpty()) {
            draft.setBuildingTagsInput(String.join(", ", draft.getBuildingTags()));
        }
    }

    private static void addTagOnce(@Nonnull PlotCreatorDraft draft, @Nonnull String tag) {
        if (!draft.getBuildingTags().contains(tag)) {
            draft.getBuildingTags().add(tag);
        }
    }

    public static void suggestIdFromDisplayName(@Nonnull PlotCreatorDraft draft) {
        if (draft.getDisplayName() == null || draft.getDisplayName().isBlank()) {
            return;
        }
        String slug =
            draft.getDisplayName()
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_+", "_");
        if (slug.startsWith("_")) {
            slug = slug.substring(1);
        }
        if (slug.endsWith("_")) {
            slug = slug.substring(0, slug.length() - 1);
        }
        if (slug.isEmpty()) {
            return;
        }
        if (draft.isFestivalMode()) {
            PlotCreatorFestivalSettings.applySuggestedId(draft, slug);
            return;
        }
        if (draft.isPropMode()) {
            draft.setConstructionId("prop_" + slug);
            syncPrefabFileNameFromConstructionId(draft);
            return;
        }
        String prefix =
            draft.isWallMode()
                ? PlotCreatorWallStyleIds.ID_PREFIX
                : draft.isDecorationOnly() ? "plot_decoration_" : "plot_";
        draft.setConstructionId(prefix + slug);
        syncPrefabFileNameFromConstructionId(draft);
    }

    /** Sets the export file name from the building id (used after identity, before prefab save). */
    public static void syncPrefabFileNameFromConstructionId(@Nonnull PlotCreatorDraft draft) {
        if (draft.isFestivalMode()) {
            PlotCreatorFestivalSettings.syncPrefabFileName(draft);
            return;
        }
        if (draft.isPropMode()) {
            String id = draft.getConstructionId();
            if (id != null && !id.isBlank()) {
                String key = com.hexvane.aetherhaven.prop.PropPaths.prefabPathKeyFromPropId(id);
                draft.setPrefabPath(key);
                draft.setPrefabFileName(com.hexvane.aetherhaven.prop.PropPaths.prefabFileNameFromKey(key));
            }
            return;
        }
        if (draft.countsAsFestivalSquare() && !draft.isBuildingEditorMode()) {
            String id = draft.getConstructionId();
            if (id != null && !id.isBlank()) {
                draft.setPrefabPath(CustomFestivalPaths.prefabPathKey(id));
                draft.setPrefabFileName(CustomFestivalPaths.prefabFileName(id));
            }
            return;
        }
        if (draft.isBuildingEditorMode()) {
            String locked = draft.getLockedPrefabPathKey();
            if (locked != null && !locked.isBlank()) {
                draft.setPrefabPath(locked);
                draft.setPrefabFileName(locked);
            }
            return;
        }
        String file = PlotCreatorPrefabExporter.prefabPathKeyFromConstructionId(draft.getConstructionId());
        if (file != null) {
            draft.setPrefabFileName(file);
        }
    }

    /**
     * Validates and applies name, id, tags, and building settings from the combined settings step or panel.
     * Returns an error message key, or null on success.
     */
    @Nullable
    public static String applySettingsStepInput(@Nonnull PlotCreatorDraft draft) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return "saveFailed";
        }
        if (draft.isFestivalMode()) {
            return PlotCreatorFestivalSettings.applyInput(draft);
        }
        if (draft.isPropMode()) {
            String idErr =
                PlotCreatorValidator.validatePropId(
                    draft.getConstructionId(), plugin, draft.getEditingConstructionId()
                );
            if (idErr != null) {
                return idErr;
            }
            if (draft.getDisplayName() == null || draft.getDisplayName().isBlank()) {
                return "id_empty";
            }
            syncPrefabFileNameFromConstructionId(draft);
            return null;
        }
        String err =
            PlotCreatorValidator.validateId(
                draft.getConstructionId(),
                plugin.getConstructionCatalog(),
                draft.getEditingConstructionId(),
                draft.isWallMode()
            );
        if (err != null) {
            return err;
        }
        if (draft.getDisplayName() == null || draft.getDisplayName().isBlank()) {
            return "id_empty";
        }
        if (draft.isWallMode()) {
            if (PlotCreatorWallStyleIds.styleId(draft) == null) {
                return "wallIdPrefix";
            }
            applyTagsInput(draft);
            return applyConfigureInput(draft);
        }
        syncPrefabFileNameFromConstructionId(draft);
        applyTagsInput(draft);
        return applyConfigureInput(draft);
    }

    /** Parses configure-panel fields into the draft. Returns an error message key, or null on success. */
    @Nullable
    public static String applyConfigureInput(@Nonnull PlotCreatorDraft draft) {
        String raw = draft.getSelfBuildDaysInput();
        if (raw == null || raw.isBlank()) {
            if (draft.getSelfBuildGameDays() <= 0.0) {
                return "selfBuildDays";
            }
        } else {
            try {
                double days = Double.parseDouble(raw.trim());
                if (days <= 0.0) {
                    return "selfBuildDays";
                }
                draft.setSelfBuildGameDays(days);
            } catch (NumberFormatException e) {
                return "selfBuildDays";
            }
        }

        if (PlotBuildingKindRequirements.effectiveKinds(draft, null).contains(PlotBuildingKind.HOME)) {
            String residentsRaw = draft.getMaxHomeResidentsInput();
            if (residentsRaw == null || residentsRaw.isBlank()) {
                draft.setMaxHomeResidents(Math.max(1, draft.getMaxHomeResidents()));
            } else {
                try {
                    int residents = Integer.parseInt(residentsRaw.trim());
                    if (residents < 1 || residents > 8) {
                        return "maxHomeResidents";
                    }
                    draft.setMaxHomeResidents(residents);
                } catch (NumberFormatException e) {
                    return "maxHomeResidents";
                }
            }
        }
        return null;
    }

    @Nonnull
    public static String formatSelfBuildDaysForField(double days) {
        if (days == Math.rint(days) && days >= 0.0 && days <= Long.MAX_VALUE) {
            return String.valueOf((long) days);
        }
        return String.valueOf(days);
    }

    /** Parses {@link PlotCreatorDraft#getBuildingTagsInput()} into {@link PlotCreatorDraft#getBuildingTags()}. */
    public static void applyTagsInput(@Nonnull PlotCreatorDraft draft) {
        draft.getBuildingTags().clear();
        String raw = draft.getBuildingTagsInput();
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String part : raw.split(",")) {
            String t = part.trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty()) {
                draft.getBuildingTags().add(t);
            }
        }
    }

    /** Default plot creator community submit checkbox from {@code CommunityMarketplace.SubmitOnSaveDefault}. */
    public static void applyCommunityMarketplaceDefaults(@Nonnull PlotCreatorDraft draft) {
        draft.setSubmitToCommunity(false);
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        var cfg = plugin.getConfig().get().getCommunityMarketplace();
        if (cfg.isEnabled() && cfg.isSubmitOnSaveDefault()) {
            draft.setSubmitToCommunity(true);
        }
    }
}
