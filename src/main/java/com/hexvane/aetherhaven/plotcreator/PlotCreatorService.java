package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.community.CommunitySubmissionService;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.placement.PlotFootprintOverlayRefresh;
import com.hexvane.aetherhaven.placement.PlotPlacementWireframeOverlay;
import com.hexvane.aetherhaven.plot.PlotTokenInventory;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
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
        PlotCreatorCleanup.endSession(session, playerRef, true);
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
        PlotCreatorDraft draft = session.getDraft();
        World world = session.getWorld();
        if (draft.getCornerFirst() == null || draft.getCornerSecond() == null) {
            clearPlotCreatorWireframe(playerRef, world);
            return;
        }
        Vector3i min = draft.boundsMin();
        Vector3i max = draft.boundsMax();
        PlotFootprintRecord fp = new PlotFootprintRecord(min.x, min.y, min.z, max.x, max.y, max.z);
        UUID uuid = playerRef.getUuid();
        if (PLOT_CREATOR_WIREFRAME_ACTIVE.containsKey(uuid)) {
            PlotPlacementWireframeOverlay.clearFor(playerRef);
            restoreOtherDebugOverlays(playerRef, world);
        }
        PlotPlacementWireframeOverlay.sendWithoutClear(playerRef, fp, true, null);
        PLOT_CREATOR_WIREFRAME_ACTIVE.put(uuid, Boolean.TRUE);
    }

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
        steps.add(PlotCreatorStep.CORNER_FIRST);
        steps.add(PlotCreatorStep.CORNER_SECOND);
        steps.add(PlotCreatorStep.ANCHOR);
        steps.add(PlotCreatorStep.KIND);
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
        steps.add(PlotCreatorStep.IDENTITY);
        steps.add(PlotCreatorStep.TAGS);
        steps.add(PlotCreatorStep.CONFIGURE);
        steps.add(PlotCreatorStep.PREFAB_SAVE);
        steps.add(PlotCreatorStep.MATERIALS);
        steps.add(PlotCreatorStep.REVIEW);
        steps.add(PlotCreatorStep.DONE);
        return steps;
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
        if (next == PlotCreatorStep.MATERIALS) {
            PlotCreatorMaterialsActions.onEnterMaterialsStep(session);
        }
        session.getDraft().setStep(next);
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
        PlotCreatorStep prev = order.get(idx - 1);
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
        if (step == PlotCreatorStep.KIND) {
            PlotCreatorInteractions.openKindPanel(playerRef, ref, store, session);
        }
        if (step == PlotCreatorStep.IMPORTANT_SPOTS) {
            seedImportantSpotsIfEmpty(session.getDraft());
            PlotCreatorInteractions.openImportantSpotsPanel(playerRef, ref, store, session);
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
        boolean hasManagement = false;
        boolean hasInnBell = false;
        for (PlotCreatorSpotEntry entry : draft.getSelectedSpots()) {
            if (entry.type() == PlotCreatorSubstepType.MANAGEMENT_BLOCK) {
                hasManagement = true;
            } else if (entry.type() == PlotCreatorSubstepType.INN_BELL_BLOCK) {
                hasInnBell = true;
            }
        }
        if (!hasManagement) {
            draft.getSelectedSpots().add(0, PlotCreatorSpotEntry.of(PlotCreatorSubstepType.MANAGEMENT_BLOCK, 1));
        }
        if (!hasInnBell
            && PlotBuildingKindRequirements.effectiveKinds(draft, AetherhavenPlugin.get()).contains(PlotBuildingKind.INN)) {
            draft.getSelectedSpots().add(PlotCreatorSpotEntry.of(PlotCreatorSubstepType.INN_BELL_BLOCK, 1));
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

    public static boolean saveAndFinish(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PlotCreatorSession session,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        PlotCreatorDraft draft = session.getDraft();
        applyConfigureInput(draft);
        if (draft.isDecorationOnly() && !draft.getBuildingTags().contains("decoration")) {
            draft.getBuildingTags().add("decoration");
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
        Path buildingFile = CustomBuildingsPaths.buildingFile(plugin.getDataDirectory(), draft.getConstructionId().trim());
        try {
            PlotCreatorJsonWriter.writeBuilding(buildingFile, draft);
        } catch (Exception e) {
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
        Player player = store.getComponent(ref, Player.getComponentType());
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
        String lockedPrefab = draft.getLockedPrefabPathKey() != null ? draft.getLockedPrefabPathKey() : draft.getPrefabPath();
        Path writeRoot = BuildingEditorSavePaths.resolveWriteRoot(plugin, id);
        Path prefabOut = BuildingEditorSavePaths.prefabFile(writeRoot, lockedPrefab);
        boolean exported = PlotCreatorPrefabExporter.export(session.getWorld(), draft, prefabOut, true);
        if (!exported) {
            playerRef.sendMessage(Message.translation("aetherhaven_plot_creator.aetherhaven.plotcreator.error.prefabExport"));
            return false;
        }
        if (lockedPrefab != null) {
            draft.setPrefabPath(lockedPrefab);
            draft.setPrefabFileName(lockedPrefab);
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
        // Building editor pastes temporary blocks; clear them after save as well as cancel.
        boolean removeWorldArtifacts = session.getDraft().isBuildingEditorMode();
        PlotCreatorCleanup.endSession(session, playerRef, removeWorldArtifacts);
    }

    public static void applyDefaultTagsForKind(@Nonnull PlotCreatorDraft draft) {
        if (!draft.getBuildingTags().isEmpty() || draft.getBuildingTagsInput() != null) {
            return;
        }
        for (PlotBuildingKind kind : draft.getKinds()) {
            switch (kind) {
                case AMENITY -> {
                    addTagOnce(draft, "amenity");
                    addTagOnce(draft, "fun");
                    draft.setScheduleSharedUtilityPick(true);
                    draft.setTouristDestination(true);
                }
                case HOME -> addTagOnce(draft, "home");
                case WORK -> addTagOnce(draft, "work");
                case SHOP, PLAYER_SHOP -> {
                    addTagOnce(draft, "shop");
                    addTagOnce(draft, "work");
                    draft.setTouristDestination(true);
                }
                case INN -> {
                    addTagOnce(draft, "civic");
                    draft.setTouristDestination(true);
                }
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
        if (!slug.isEmpty()) {
            String prefix = draft.isDecorationOnly() ? "plot_decoration_" : "plot_";
            draft.setConstructionId(prefix + slug);
            syncPrefabFileNameFromConstructionId(draft);
        }
    }

    /** Sets the export file name from the building id (used after identity, before prefab save). */
    public static void syncPrefabFileNameFromConstructionId(@Nonnull PlotCreatorDraft draft) {
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
