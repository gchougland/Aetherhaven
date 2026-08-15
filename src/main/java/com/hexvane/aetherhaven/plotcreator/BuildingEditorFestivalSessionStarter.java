package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.festival.FestivalDefinition;
import com.hexvane.aetherhaven.festival.FestivalPrefabSize;
import com.hexvane.aetherhaven.prefab.ConstructionAnimator;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Pastes an existing festival prefab near the player and starts a building-editor festival session. */
public final class BuildingEditorFestivalSessionStarter {
    private static final String MSG = "aetherhaven_building_editor.aetherhaven.buildingeditor";
    private static final int INSTANT_BLOCKS_PER_BATCH = 500_000;
    private static final long INSTANT_BATCH_DELAY_MS = 1L;
    private static final int PASTE_FORWARD_BLOCKS = 6;

    private BuildingEditorFestivalSessionStarter() {}

    public static void start(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull String festivalId
    ) {
        if (!BuildingEditorSessionStarter.requireCreative(playerRef, ref, store)) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        FestivalDefinition existing = plugin.getFestivalCatalog().get(festivalId.trim());
        if (existing == null) {
            playerRef.sendMessage(Message.translation(MSG + ".error.unknownFestival").param("id", festivalId));
            return;
        }
        String prefabPath = existing.getPrefabPath();
        IPrefabBuffer buffer = PrefabResolveUtil.resolvePrefabBuffer(prefabPath);
        if (buffer == null) {
            playerRef.sendMessage(Message.translation(MSG + ".error.prefabMissing"));
            return;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            playerRef.sendMessage(Message.translation(MSG + ".error.pasteFailed"));
            return;
        }
        Vector3d pos = transform.getPosition();
        float yawRad = transform.getRotation().yaw();
        // Festival prefabs paste from their center (same as festival square swaps), not the min corner.
        Vector3i center = pasteCenterNearPlayer(pos, yawRad);
        Vector3i min =
            new Vector3i(
                center.x - FestivalPrefabSize.SPAN_X / 2,
                center.y,
                center.z - FestivalPrefabSize.SPAN_Z / 2
            );
        Vector3i max = FestivalPrefabSize.maxFromMin(min);
        String display = existing.getDisplayName();
        playerRef.sendMessage(Message.translation(MSG + ".hint.loadingFestival").param("name", display));

        UUID uuid = playerRef.getUuid();
        PlotCreatorSessions.remove(uuid);
        World world = store.getExternalData().getWorld();
        Vector3i minCopy = new Vector3i(min);
        Vector3i maxCopy = new Vector3i(max);
        Vector3i centerCopy = new Vector3i(center);
        ConstructionAnimator.start(
            plugin,
            world,
            centerCopy,
            Rotation.None,
            true,
            false,
            buffer,
            store,
            INSTANT_BLOCKS_PER_BATCH,
            INSTANT_BATCH_DELAY_MS,
            () ->
                world.execute(
                    () ->
                        finishAfterPaste(
                            plugin,
                            playerRef,
                            ref,
                            store,
                            world,
                            existing,
                            minCopy,
                            maxCopy,
                            centerCopy,
                            display
                        )
                )
        );
    }

    private static void finishAfterPaste(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull FestivalDefinition existing,
        @Nonnull Vector3i min,
        @Nonnull Vector3i max,
        @Nonnull Vector3i center,
        @Nonnull String display
    ) {
        if (!ref.isValid()) {
            return;
        }
        UUID uuid = playerRef.getUuid();
        PlotCreatorSessions.remove(uuid);
        PlotCreatorSession session = new PlotCreatorSession(uuid, world);
        PlotCreatorDraft draft = session.getDraft();
        draft.setKinds(List.of(PlotBuildingKind.FESTIVAL));
        draft.setBuildingEditorMode(true);
        PlotCreatorBoundsValidation.commitCorners(draft, min, max);
        draft.setBoundsPhase(PlotCreatorBoundsPhase.FACE_ADJUST);
        draft.setPlotAnchor(new Vector3i(center));
        draft.setPrefabOriginMin(new Vector3i(min));
        draft.setFestivalSizeLocked(true);
        draft.setFestivalPicked(true);
        PlotCreatorFestivalDraftSetup.applyFestivalFields(draft, existing);
        draft.setSaveEmptySpaces(false);
        PlotCreatorStep startStep = editorStartStep(draft);
        draft.setStep(startStep);
        draft.setMaxReachedStepIndex(Math.max(0, PlotCreatorService.stepOrder(draft).indexOf(startStep)));
        PlotCreatorService.seedImportantSpotsIfEmpty(draft);
        PlotCreatorSessions.put(session);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            PlotCreatorHudSupport.refreshAll(player, playerRef, session);
        }
        PlotCreatorService.refreshWireframe(session, playerRef);
        PlotCreatorService.refreshSpawnMarkers(session, playerRef);
        playerRef.sendMessage(Message.translation(MSG + ".hint.loadedFestival").param("name", display));
        if (startStep == PlotCreatorStep.IMPORTANT_SPOTS) {
            PlotCreatorInteractions.openImportantSpotsPanel(playerRef, ref, store, session);
        } else if (player != null) {
            player.getPageManager().setPage(ref, store, Page.None);
        }
    }

    @Nonnull
    private static PlotCreatorStep editorStartStep(@Nonnull PlotCreatorDraft draft) {
        List<PlotCreatorStep> order = PlotCreatorService.stepOrder(draft);
        if (order.contains(PlotCreatorStep.IMPORTANT_SPOTS)) {
            return PlotCreatorStep.IMPORTANT_SPOTS;
        }
        if (order.contains(PlotCreatorStep.CONFIGURE)) {
            return PlotCreatorStep.CONFIGURE;
        }
        return PlotCreatorStep.CONFIGURE;
    }

    @Nonnull
    private static Vector3i pasteCenterNearPlayer(@Nonnull Vector3d pos, float yawRad) {
        double forwardX = -Math.sin(yawRad);
        double forwardZ = -Math.cos(yawRad);
        int ox = (int) Math.floor(pos.x + forwardX * PASTE_FORWARD_BLOCKS);
        int oy = (int) Math.floor(pos.y);
        int oz = (int) Math.floor(pos.z + forwardZ * PASTE_FORWARD_BLOCKS);
        return new Vector3i(ox, oy, oz);
    }
}
