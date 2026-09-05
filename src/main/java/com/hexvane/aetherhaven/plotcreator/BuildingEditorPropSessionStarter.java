package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.placement.PlotFootprintUtil;
import com.hexvane.aetherhaven.prefab.ConstructionAnimator;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.prop.PropDefinition;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
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

/** Pastes a marketplace prop near the player and starts a building-editor prop session. */
public final class BuildingEditorPropSessionStarter {
    private static final String MSG = "aetherhaven_building_editor.aetherhaven.buildingeditor";
    private static final int INSTANT_BLOCKS_PER_BATCH = 500_000;
    private static final long INSTANT_BATCH_DELAY_MS = 1L;
    private static final int PASTE_FORWARD_BLOCKS = 6;

    private BuildingEditorPropSessionStarter() {}

    public static void startFromPropId(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull String propId,
        boolean communitySubmissionEdit,
        boolean communitySubmissionApproved
    ) {
        if (!BuildingEditorSessionStarter.requireCreative(playerRef, ref, store)) {
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        PropDefinition def = plugin.getPropCatalog().get(propId.trim());
        if (def == null) {
            playerRef.sendMessage(Message.translation(MSG + ".error.unknownProp").param("id", propId));
            return;
        }
        IPrefabBuffer buffer = PrefabResolveUtil.resolvePrefabBuffer(def.getPrefabPath());
        if (buffer == null) {
            playerRef.sendMessage(Message.translation(MSG + ".error.prefabMissingProp"));
            return;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null || transform.getPosition() == null) {
            playerRef.sendMessage(Message.translation(MSG + ".error.pasteFailedProp"));
            return;
        }
        Vector3d pos = transform.getPosition();
        float yawRad = transform.getRotation().yaw();
        Rotation yaw = Rotation.None;
        Vector3i prefabOrigin = pasteOriginNearPlayer(pos, yawRad);

        UUID uuid = playerRef.getUuid();
        PlotCreatorSessions.remove(uuid);
        World world = store.getExternalData().getWorld();
        String display = def.getDisplayName();
        playerRef.sendMessage(Message.translation(MSG + ".hint.loadingProp").param("name", display));

        Vector3i originCopy = new Vector3i(prefabOrigin);
        boolean communityEdit = communitySubmissionEdit;
        boolean communityApproved = communitySubmissionApproved;
        ConstructionAnimator.start(
            plugin,
            world,
            prefabOrigin,
            yaw,
            true,
            false,
            buffer,
            store,
            INSTANT_BLOCKS_PER_BATCH,
            INSTANT_BATCH_DELAY_MS,
            () ->
                world.execute(
                    () ->
                        finishSessionAfterPaste(
                            plugin,
                            playerRef,
                            ref,
                            store,
                            world,
                            def,
                            buffer,
                            originCopy,
                            yaw,
                            communityEdit,
                            communityApproved
                        )
                )
        );
    }

    private static void finishSessionAfterPaste(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull PropDefinition def,
        @Nonnull IPrefabBuffer buffer,
        @Nonnull Vector3i prefabOrigin,
        @Nonnull Rotation yaw,
        boolean communitySubmissionEdit,
        boolean communitySubmissionApproved
    ) {
        if (!ref.isValid()) {
            return;
        }
        UUID uuid = playerRef.getUuid();
        PlotCreatorSessions.remove(uuid);
        PlotCreatorSession session = new PlotCreatorSession(uuid, world);
        PlotCreatorDraft draft = session.getDraft();
        draft.setKinds(List.of(PlotBuildingKind.PROP));
        draft.setBuildingEditorMode(true);
        draft.setCommunitySubmissionEdit(communitySubmissionEdit);
        draft.setCommunitySubmissionApproved(communitySubmissionApproved);
        draft.setEditingConstructionId(def.getId());
        draft.setConstructionId(def.getId());
        draft.setConstructionIdUserEdited(true);
        draft.setSubmitToCommunity(false);
        draft.setDisplayName(def.getDisplayName());
        draft.setFrontFacing(def.getFrontFacing());
        draft.setPropGoldPrice(def.getGoldPrice());
        draft.setPrefabPath(def.getPrefabPath());
        draft.setPrefabFileName(
            com.hexvane.aetherhaven.prop.PropPaths.prefabFileNameFromKey(def.getPrefabPath())
        );
        draft.setLockedPrefabPathKey(def.getPrefabPath());

        PlotFootprintRecord fp = PlotFootprintUtil.computeFootprint(prefabOrigin, yaw, buffer);
        PlotCreatorBoundsValidation.commitCorners(
            draft,
            new Vector3i(fp.getMinX(), fp.getMinY(), fp.getMinZ()),
            new Vector3i(fp.getMaxX(), fp.getMaxY(), fp.getMaxZ())
        );
        draft.setBoundsPhase(PlotCreatorBoundsPhase.SELECTION);
        PlotCreatorAutoAnchor.applyCenter(draft);

        PlotCreatorStep startStep = PlotCreatorStep.CONFIGURE;
        draft.setStep(startStep);
        draft.setMaxReachedStepIndex(Math.max(0, PlotCreatorService.stepOrder(draft).indexOf(startStep)));
        PlotCreatorSessions.put(session);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player != null) {
            PlotCreatorHudSupport.refreshAll(player, playerRef, session);
        }
        PlotCreatorService.refreshWireframe(session, playerRef);
        playerRef.sendMessage(
            Message.translation(MSG + ".hint.loadedProp").param("name", def.getDisplayName()).param("id", def.getId())
        );
        PlotCreatorInteractions.openConfigurePanel(playerRef, ref, store, session);
    }

    @Nonnull
    private static Vector3i pasteOriginNearPlayer(@Nonnull Vector3d pos, float yawRad) {
        double forwardX = -Math.sin(yawRad);
        double forwardZ = -Math.cos(yawRad);
        int ox = (int) Math.floor(pos.x + forwardX * PASTE_FORWARD_BLOCKS);
        int oy = (int) Math.floor(pos.y);
        int oz = (int) Math.floor(pos.z + forwardZ * PASTE_FORWARD_BLOCKS);
        return new Vector3i(ox, oy, oz);
    }
}
