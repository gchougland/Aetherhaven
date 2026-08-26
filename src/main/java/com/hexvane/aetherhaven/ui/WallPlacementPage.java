package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyPreviewSystem;
import com.hexvane.aetherhaven.placement.PlotFootprintUtil;
import com.hexvane.aetherhaven.placement.PlotPlacementCameraUtil;
import com.hexvane.aetherhaven.placement.PlotPlacementRotationUtil;
import com.hexvane.aetherhaven.placement.WallPlacementCameraUtil;
import com.hexvane.aetherhaven.placement.PlotPlacementClientPrefabPreview;
import com.hexvane.aetherhaven.placement.PlotPlacementCommit;
import com.hexvane.aetherhaven.placement.PlotPlacementHeights;
import com.hexvane.aetherhaven.placement.PlotPlacementValidator;
import com.hexvane.aetherhaven.prefab.AetherhavenWorldPrefabPreview;
import com.hexvane.aetherhaven.placement.WallPlacementDebug;
import com.hexvane.aetherhaven.placement.WallPlacementRemoveService;
import com.hexvane.aetherhaven.placement.WallPlacementSession;
import com.hexvane.aetherhaven.placement.WallPlacementSessions;
import com.hexvane.aetherhaven.placement.WallPlacementWireframeOverlay;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownPlayerResolution;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.wall.WallCardinal;
import com.hexvane.aetherhaven.wall.WallPieceGeometry;
import com.hexvane.aetherhaven.wall.WallStyle;
import com.hexvane.aetherhaven.wall.WallStyleCatalog;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import org.joml.Vector3d;
import org.joml.Vector3i;
import java.nio.file.Path;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class WallPlacementPage extends AetherhavenInteractiveCustomUIPage<WallPlacementPage.PageData> {
    static final String MSG = "aetherhaven_wall_placement.aetherhaven.ui.wallplacement";

    @Nonnull
    private final WallPlacementSession session;

    private static final String WALL_UI = "Aetherhaven/WallPlacementPage.ui";
    private static final ArrowButtonStyles STYLES_UP =
        new ArrowButtonStyles(Value.ref(WALL_UI, "IconUpOnStyle"), Value.ref(WALL_UI, "IconUpOffStyle"));
    private static final ArrowButtonStyles STYLES_DOWN =
        new ArrowButtonStyles(Value.ref(WALL_UI, "IconDownOnStyle"), Value.ref(WALL_UI, "IconDownOffStyle"));
    private static final ArrowButtonStyles STYLES_BACK =
        new ArrowButtonStyles(Value.ref(WALL_UI, "IconBackOnStyle"), Value.ref(WALL_UI, "IconBackOffStyle"));
    private static final ArrowButtonStyles STYLES_FWD =
        new ArrowButtonStyles(Value.ref(WALL_UI, "IconFwdOnStyle"), Value.ref(WALL_UI, "IconFwdOffStyle"));

    private float birdsEyeDistance = WallPlacementCameraUtil.DEFAULT_DISTANCE;
    private int wireframeRefreshSerial;

    public WallPlacementPage(@Nonnull PlayerRef playerRef, @Nonnull WallPlacementSession session) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.session = session;
    }

    public static void applyLocalization(@Nonnull UICommandBuilder commandBuilder) {
        String p = MSG;
        commandBuilder.set("#WallPlacementTitle.TextSpans", Message.translation(p + ".title"));
        commandBuilder.set("#Summary.TextSpans", Message.translation(p + ".summary"));
        commandBuilder.set("#Tips.TextSpans", Message.translation(p + ".tips"));
        commandBuilder.set("#EditHint.TextSpans", Message.translation(p + ".editHint"));
        commandBuilder.set("#EditContinueButton.TextSpans", Message.translation(p + ".editContinue"));
        commandBuilder.set("#EditRemoveButton.TextSpans", Message.translation(p + ".editRemove"));
        commandBuilder.set("#EditCancelButton.TextSpans", Message.translation(p + ".editCancel"));
        commandBuilder.set("#RemoveConfirmText.TextSpans", Message.translation(p + ".removeConfirm"));
        commandBuilder.set("#RemoveConfirmButton.TextSpans", Message.translation(p + ".removeConfirmYes"));
        commandBuilder.set("#RemoveConfirmBackButton.TextSpans", Message.translation(p + ".removeConfirmBack"));
        commandBuilder.set("#BtnPieceWall.TextSpans", Message.translation(p + ".pieceWall"));
        commandBuilder.set("#BtnPieceGate.TextSpans", Message.translation(p + ".pieceGate"));
        commandBuilder.set("#BtnPieceTower.TextSpans", Message.translation(p + ".pieceTower"));
        commandBuilder.set("#BackButton.TextSpans", Message.translation(p + ".undo"));
        commandBuilder.set("#PlaceButton.TextSpans", Message.translation(p + ".completeWall"));
        commandBuilder.set("#CancelButton.TextSpans", Message.translation(p + ".cancel"));
        commandBuilder.set("#BtnExpandZm.TooltipTextSpans", Message.translation(p + ".placeScreenUp"));
        commandBuilder.set("#BtnExpandZp.TooltipTextSpans", Message.translation(p + ".placeScreenDown"));
        commandBuilder.set("#BtnExpandXm.TooltipTextSpans", Message.translation(p + ".placeScreenLeft"));
        commandBuilder.set("#BtnExpandXp.TooltipTextSpans", Message.translation(p + ".placeScreenRight"));
        commandBuilder.set("#BtnYm.TooltipTextSpans", Message.translation(p + ".moveDown"));
        commandBuilder.set("#BtnYp.TooltipTextSpans", Message.translation(p + ".moveUp"));
        commandBuilder.set("#BtnViewZm.TooltipTextSpans", Message.translation(p + ".viewFromNorth"));
        commandBuilder.set("#BtnViewXp.TooltipTextSpans", Message.translation(p + ".viewFromEast"));
        commandBuilder.set("#BtnViewZp.TooltipTextSpans", Message.translation(p + ".viewFromSouth"));
        commandBuilder.set("#BtnViewXm.TooltipTextSpans", Message.translation(p + ".viewFromWest"));
        commandBuilder.set("#BtnNudgeZm.TooltipTextSpans", Message.translation(p + ".positionNudgeNorth"));
        commandBuilder.set("#BtnNudgeZp.TooltipTextSpans", Message.translation(p + ".positionNudgeSouth"));
        commandBuilder.set("#BtnNudgeXm.TooltipTextSpans", Message.translation(p + ".positionNudgeWest"));
        commandBuilder.set("#BtnNudgeXp.TooltipTextSpans", Message.translation(p + ".positionNudgeEast"));
        commandBuilder.set("#BtnRotate.TooltipTextSpans", Message.translation(p + ".rotateTooltip"));
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store
    ) {
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc != null) {
            WallPlacementUiRegistry.register(uc.getUuid(), this);
        }
        commandBuilder.append("Aetherhaven/WallPlacementPage.ui");
        applyLocalization(commandBuilder);
        boolean editPrompt = session.hasEditTarget() && !session.isRemoveConfirmOpen();
        boolean removeConfirm = session.isRemoveConfirmOpen();
        commandBuilder.set("#EditGroup.Visible", editPrompt);
        commandBuilder.set("#RemoveConfirmGroup.Visible", removeConfirm);
        commandBuilder.set("#PieceTypeRow.Visible", !editPrompt && !removeConfirm);
        commandBuilder.set("#BtnPieceTower.Disabled", !session.canPlaceTowerNow());
        applyWallStyleRow(commandBuilder, !editPrompt && !removeConfirm);
        commandBuilder.set("#CameraPlotRow.Visible", !editPrompt && !removeConfirm);
        commandBuilder.set("#RowYBack.Visible", !editPrompt && !removeConfirm);
        commandBuilder.set("#RowActions.Visible", !editPrompt && !removeConfirm);
        boolean firstAdjust = session.isAdjustingFirstPiece();
        commandBuilder.set("#NudgePadColumn.Visible", firstAdjust && !editPrompt && !removeConfirm);
        commandBuilder.set("#BtnRotate.Visible", firstAdjust && !editPrompt && !removeConfirm);
        commandBuilder.set("#BirdsEyeDistanceSlider.Value", birdsEyeDistance);
        commandBuilder.set("#BirdsEyeDistanceValue.TextSpans", Message.raw(String.format("%.0f", birdsEyeDistance)));
        if (!editPrompt && !removeConfirm) {
            applyViewSideButtons(commandBuilder);
            applyExpandPadButtons(commandBuilder);
        }

        bind(eventBuilder, "#BtnStylePrev", "StylePrev");
        bind(eventBuilder, "#BtnStyleNext", "StyleNext");
        bind(eventBuilder, "#BtnPieceWall", "PieceWall");
        bind(eventBuilder, "#BtnPieceGate", "PieceGate");
        bind(eventBuilder, "#BtnPieceTower", "PieceTower");
        bind(eventBuilder, "#BtnExpandZm", "ExpandZm");
        bind(eventBuilder, "#BtnExpandXm", "ExpandXm");
        bind(eventBuilder, "#BtnExpandXp", "ExpandXp");
        bind(eventBuilder, "#BtnExpandZp", "ExpandZp");
        bind(eventBuilder, "#BtnNudgeZm", "NudgeZm");
        bind(eventBuilder, "#BtnNudgeXm", "NudgeXm");
        bind(eventBuilder, "#BtnNudgeXp", "NudgeXp");
        bind(eventBuilder, "#BtnNudgeZp", "NudgeZp");
        bind(eventBuilder, "#BtnRotate", "Rotate");
        bind(eventBuilder, "#BtnYm", "MoveYm");
        bind(eventBuilder, "#BtnYp", "MoveYp");
        bind(eventBuilder, "#BackButton", "Back");
        bind(eventBuilder, "#PlaceButton", "Place");
        bind(eventBuilder, "#CancelButton", "Cancel");
        bind(eventBuilder, "#BtnZoomOut", "ZoomOut");
        bind(eventBuilder, "#BtnZoomIn", "ZoomIn");
        bind(eventBuilder, "#BtnViewXm", "ViewXm");
        bind(eventBuilder, "#BtnViewXp", "ViewXp");
        bind(eventBuilder, "#BtnViewZm", "ViewZm");
        bind(eventBuilder, "#BtnViewZp", "ViewZp");
        bind(eventBuilder, "#EditContinueButton", "EditContinue");
        bind(eventBuilder, "#EditRemoveButton", "EditRemove");
        bind(eventBuilder, "#EditCancelButton", "EditCancel");
        bind(eventBuilder, "#RemoveConfirmButton", "RemoveConfirm");
        bind(eventBuilder, "#RemoveConfirmBackButton", "RemoveConfirmBack");
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#BirdsEyeDistanceSlider",
            EventData.of("@BirdsEyeDistance", "#BirdsEyeDistanceSlider.Value"),
            false
        );

        scheduleRefreshPreview(ref, store);
        scheduleApplyCamera(ref, store);
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc != null) {
            WallPlacementUiRegistry.unregister(uc.getUuid(), this);
        }
        super.onDismiss(ref, store);
        World world = store.getExternalData().getWorld();
        world.execute(
            () -> {
                if (!ref.isValid()) {
                    return;
                }
                PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
                if (pr != null) {
                    PlotPlacementCameraUtil.resetToPlayerCamera(pr);
                }
            }
        );
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.birdsEyeDistance != null) {
            birdsEyeDistance =
                Math.max(
                    WallPlacementCameraUtil.MIN_DISTANCE,
                    Math.min(WallPlacementCameraUtil.MAX_DISTANCE, data.birdsEyeDistance)
                );
            scheduleApplyCameraAfterSlider(ref, store);
            return;
        }
        if (data.action == null) {
            return;
        }
        switch (data.action) {
            case "StylePrev", "StyleNext" -> {
                if (session.cycleWallStyle(data.action.equals("StyleNext") ? 1 : -1)) {
                    scheduleRefreshPreviewAndCamera(ref, store);
                    rebuild();
                }
                return;
            }
            case "PieceWall" -> {
                session.setPieceKind(WallPlacementSession.PieceKind.SEGMENT);
                scheduleRefreshPreview(ref, store);
            }
            case "PieceGate" -> {
                session.setPieceKind(WallPlacementSession.PieceKind.GATE);
                scheduleRefreshPreview(ref, store);
            }
            case "PieceTower" -> {
                if (session.canPlaceTowerNow()) {
                    session.setPieceKind(WallPlacementSession.PieceKind.TOWER);
                    scheduleRefreshPreview(ref, store);
                    scheduleApplyCamera(ref, store);
                }
            }
            case "ExpandZm", "ExpandZp", "ExpandXm", "ExpandXp" -> {
                WallCardinal expandDir = WallCardinal.fromExpandPad(data.action, session.getCameraViewFromSide());
                if (!session.allowedExpandDirections().contains(expandDir)) {
                    return;
                }
                schedulePlaceAndExpand(ref, store, expandDir);
                return;
            }
            case "NudgeZm" -> {
                if (!session.isAdjustingFirstPiece()) {
                    return;
                }
                session.nudgeHorizontal(0, -1);
                scheduleRefreshPreviewAndCamera(ref, store);
                return;
            }
            case "NudgeZp" -> {
                if (!session.isAdjustingFirstPiece()) {
                    return;
                }
                session.nudgeHorizontal(0, 1);
                scheduleRefreshPreviewAndCamera(ref, store);
                return;
            }
            case "NudgeXm" -> {
                if (!session.isAdjustingFirstPiece()) {
                    return;
                }
                session.nudgeHorizontal(-1, 0);
                scheduleRefreshPreviewAndCamera(ref, store);
                return;
            }
            case "NudgeXp" -> {
                if (!session.isAdjustingFirstPiece()) {
                    return;
                }
                session.nudgeHorizontal(1, 0);
                scheduleRefreshPreviewAndCamera(ref, store);
                return;
            }
            case "Rotate" -> {
                if (!session.isAdjustingFirstPiece()) {
                    return;
                }
                applyRotatePreservingFootprintCenter();
                scheduleRefreshPreviewAndCamera(ref, store);
                return;
            }
            case "MoveYm" -> {
                session.nudgeY(-1);
                scheduleRefreshPreview(ref, store);
                return;
            }
            case "MoveYp" -> {
                session.nudgeY(1);
                scheduleRefreshPreview(ref, store);
                return;
            }
            case "Place" -> {
                schedulePlace(ref, store);
                return;
            }
            case "Back" -> scheduleUndo(ref, store);
            case "Cancel" -> scheduleCancel(ref, store);
            case "ViewZm" -> {
                session.setCameraViewFromSide(WallCardinal.NORTH);
                scheduleApplyCameraAndRebuild(ref, store);
                return;
            }
            case "ViewZp" -> {
                session.setCameraViewFromSide(WallCardinal.SOUTH);
                scheduleApplyCameraAndRebuild(ref, store);
                return;
            }
            case "ViewXm" -> {
                session.setCameraViewFromSide(WallCardinal.WEST);
                scheduleApplyCameraAndRebuild(ref, store);
                return;
            }
            case "ViewXp" -> {
                session.setCameraViewFromSide(WallCardinal.EAST);
                scheduleApplyCameraAndRebuild(ref, store);
                return;
            }
            case "ZoomOut" -> {
                birdsEyeDistance = Math.min(WallPlacementCameraUtil.MAX_DISTANCE, birdsEyeDistance + 2f);
                scheduleApplyCameraAndRebuild(ref, store);
                return;
            }
            case "ZoomIn" -> {
                birdsEyeDistance = Math.max(WallPlacementCameraUtil.MIN_DISTANCE, birdsEyeDistance - 2f);
                scheduleApplyCameraAndRebuild(ref, store);
                return;
            }
            case "EditContinue" -> {
                if (!session.tryBeginEditContinue()) {
                    return;
                }
                UUID plotId = session.getEditTargetPlotId();
                UUID segId = session.getEditTargetSegmentId();
                if (plotId == null && segId == null) {
                    session.endEditContinue();
                    scheduleRebuild(ref, store);
                    return;
                }
                UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
                WallPlacementPage target = this;
                if (uc != null) {
                    WallPlacementPage registered = WallPlacementUiRegistry.get(uc.getUuid());
                    if (registered != null) {
                        target = registered;
                    }
                }
                target.scheduleContinueFromEdit(ref, store, plotId, segId);
                return;
            }
            case "EditRemove" -> {
                session.setRemoveConfirmOpen(true);
                scheduleRebuild(ref, store);
                return;
            }
            case "EditCancel" -> scheduleCancel(ref, store);
            case "RemoveConfirm" -> scheduleRemoveTarget(ref, store);
            case "RemoveConfirmBack" -> session.setRemoveConfirmOpen(false);
            default -> {}
        }
        scheduleRebuild(ref, store);
    }

    public void refreshFootprintOverlayAfterDebugClear(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        refreshPreview(ref, store);
    }

    /** Refreshes this page when the session was updated outside {@link #handleDataEvent} (e.g. primary-use on a wall). */
    public void scheduleUiRebuild(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        scheduleRebuild(ref, store);
    }

    void scheduleContinueFromEdit(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nullable UUID plotId,
        @Nullable UUID segmentId
    ) {
        World world = store.getExternalData().getWorld();
        world.execute(
            () -> {
                try {
                    if (!ref.isValid()) {
                        return;
                    }
                    AetherhavenPlugin plugin = AetherhavenPlugin.get();
                    UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
                    if (plugin == null || uc == null) {
                        return;
                    }
                    TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
                    TownRecord town = TownPlayerResolution.resolveTownAtPlayerOrActive(world, store, ref, tm);
                    if (town == null || !session.continueFromEditTarget(town, plotId, segmentId)) {
                        sendError(store, ref, Message.translation(MSG + ".errorNoWallHere"));
                        rebuild();
                        return;
                    }
                    session.clearBirdsEyeSnapshot();
                    captureBirdsEyeSnapshot(ref, store);
                    applyBirdsEyeCameraPacket(ref, store);
                    rebuild();
                    refreshPreview(ref, store);
                } finally {
                    session.endEditContinue();
                }
            }
        );
    }

    private void scheduleRebuild(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        world.execute(
            () -> {
                if (ref.isValid()) {
                    rebuild();
                }
            }
        );
    }

    private void scheduleApplyCamera(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        world.execute(
            () -> {
                if (!ref.isValid()) {
                    return;
                }
                session.clearBirdsEyeSnapshot();
                captureBirdsEyeSnapshot(ref, store);
                applyBirdsEyeCameraPacket(ref, store);
            }
        );
    }

    private void scheduleApplyCameraAndRebuild(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        world.execute(
            () -> {
                if (!ref.isValid()) {
                    return;
                }
                applyBirdsEyeCameraPacket(ref, store);
                rebuild();
            }
        );
    }

    private void scheduleApplyCameraAfterSlider(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        world.execute(
            () -> {
                if (ref.isValid()) {
                    applyBirdsEyeCameraPacket(ref, store);
                    UICommandBuilder cmd = new UICommandBuilder();
                    cmd.set("#BirdsEyeDistanceValue.TextSpans", Message.raw(String.format("%.0f", birdsEyeDistance)));
                    sendUpdate(cmd, new UIEventBuilder(), false);
                }
            }
        );
    }

    private void captureBirdsEyeSnapshot(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        Vector3i anchor = session.getCurrentAnchor();
        if (plugin == null) {
            session.setBirdsEyeSnapshot(anchor.x + 0.5, anchor.y + 0.5, anchor.z + 0.5);
            return;
        }
        ConstructionDefinition def = plugin.getConstructionCatalog().get(session.resolveConstructionId());
        if (def == null) {
            session.setBirdsEyeSnapshot(anchor.x + 0.5, anchor.y + 0.5, anchor.z + 0.5);
            return;
        }
        Path prefabPath = PrefabResolveUtil.resolvePrefabPath(def.getPrefabPath());
        if (prefabPath == null) {
            session.setBirdsEyeSnapshot(anchor.x + 0.5, anchor.y + 0.5, anchor.z + 0.5);
            return;
        }
        IPrefabBuffer buf = PrefabBufferUtil.getCached(prefabPath);
        try {
            Vector3i prefabOrigin = def.resolvePrefabAnchorWorld(anchor, session.getCurrentPrefabYaw());
            PlotFootprintRecord fp = PlotFootprintUtil.computeFootprint(prefabOrigin, session.getCurrentPrefabYaw(), buf);
            session.setBirdsEyeSnapshot(
                (fp.getMinX() + fp.getMaxX() + 1) / 2.0,
                (fp.getMinY() + fp.getMaxY() + 1) / 2.0,
                (fp.getMinZ() + fp.getMaxZ() + 1) / 2.0
            );
        } finally {
        }
    }

    private void applyBirdsEyeCameraPacket(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (!session.hasBirdsEyeSnapshot()) {
            captureBirdsEyeSnapshot(ref, store);
        }
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (pr == null || tc == null) {
            return;
        }
        Vector3d p = tc.getPosition();
        double fx = session.getBirdsEyeSnapshotX();
        double fy = session.getBirdsEyeSnapshotY();
        double fz = session.getBirdsEyeSnapshotZ();
        WallPlacementCameraUtil.apply(
            pr, birdsEyeDistance, session.getCameraViewFromSide(), p.x, p.y, p.z, fx, fy, fz
        );
    }

    private void applyViewSideButtons(@Nonnull UICommandBuilder commandBuilder) {
        WallCardinal active = session.getCameraViewFromSide();
        applyArrowButton(commandBuilder, "#BtnViewZm", STYLES_UP, active == WallCardinal.NORTH);
        applyArrowButton(commandBuilder, "#BtnViewZp", STYLES_DOWN, active == WallCardinal.SOUTH);
        applyArrowButton(commandBuilder, "#BtnViewXm", STYLES_BACK, active == WallCardinal.WEST);
        applyArrowButton(commandBuilder, "#BtnViewXp", STYLES_FWD, active == WallCardinal.EAST);
    }

    private void applyExpandPadButtons(@Nonnull UICommandBuilder commandBuilder) {
        WallCardinal view = session.getCameraViewFromSide();
        var allowed = session.allowedExpandDirections();
        applyExpandPadButton(commandBuilder, "#BtnExpandZm", WallCardinal.screenUp(view), allowed.contains(WallCardinal.screenUp(view)));
        applyExpandPadButton(commandBuilder, "#BtnExpandZp", WallCardinal.screenDown(view), allowed.contains(WallCardinal.screenDown(view)));
        applyExpandPadButton(commandBuilder, "#BtnExpandXm", WallCardinal.screenLeft(view), allowed.contains(WallCardinal.screenLeft(view)));
        applyExpandPadButton(commandBuilder, "#BtnExpandXp", WallCardinal.screenRight(view), allowed.contains(WallCardinal.screenRight(view)));
    }

    private void applyExpandPadButton(
        @Nonnull UICommandBuilder commandBuilder, @Nonnull String selector, @Nonnull WallCardinal world, boolean enabled
    ) {
        commandBuilder.set(selector + ".Disabled", !enabled);
        ArrowButtonStyles bright = expandPadStylesForScreenButton(selector);
        applyArrowButton(commandBuilder, selector, bright, enabled);
    }

    @Nonnull
    private static ArrowButtonStyles expandPadStylesForScreenButton(@Nonnull String selector) {
        return switch (selector) {
            case "#BtnExpandZm" -> STYLES_UP;
            case "#BtnExpandZp" -> STYLES_DOWN;
            case "#BtnExpandXp" -> STYLES_FWD;
            default -> STYLES_BACK;
        };
    }

    private static void applyArrowButton(
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull String selector,
        @Nonnull ArrowButtonStyles styles,
        boolean selected
    ) {
        commandBuilder.set(selector + ".Style", selected ? styles.on : styles.off);
    }

    private record ArrowButtonStyles(@Nonnull Value<String> on, @Nonnull Value<String> off) {}

    private void scheduleRefreshPreview(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        final int serial = ++wireframeRefreshSerial;
        world.execute(
            () -> {
                if (!ref.isValid() || serial != wireframeRefreshSerial) {
                    return;
                }
                refreshPreview(ref, store);
            }
        );
    }

    private void scheduleRefreshPreviewAndCamera(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        final int serial = ++wireframeRefreshSerial;
        world.execute(
            () -> {
                if (!ref.isValid() || serial != wireframeRefreshSerial) {
                    return;
                }
                captureBirdsEyeSnapshot(ref, store);
                applyBirdsEyeCameraPacket(ref, store);
                refreshPreview(ref, store);
            }
        );
    }

    /**
     * Rotates the prefab 90° while keeping the axis-aligned footprint center fixed (avoids spinning around the buffer
     * origin corner).
     */
    private void applyRotatePreservingFootprintCenter() {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        String consId = session.resolveConstructionId();
        ConstructionDefinition def = plugin != null ? plugin.getConstructionCatalog().get(consId) : null;
        if (def == null) {
            session.rotateClockwise90();
            return;
        }
        Path prefabPath = PrefabResolveUtil.resolvePrefabPath(def.getPrefabPath());
        if (prefabPath == null) {
            session.rotateClockwise90();
            return;
        }
        IPrefabBuffer buf = PrefabBufferUtil.getCached(prefabPath);
        try {
            Rotation oldYaw = session.getCurrentPrefabYaw();
            Vector3d k0 = PlotPlacementRotationUtil.footprintCenterAtSignOrigin(def, oldYaw, buf);
            Vector3i sign0 = session.getCurrentAnchor();
            session.rotateClockwise90();
            Rotation newYaw = session.getCurrentPrefabYaw();
            Vector3d k1 = PlotPlacementRotationUtil.footprintCenterAtSignOrigin(def, newYaw, buf);
            double x = sign0.x + k0.x - k1.x;
            double y = sign0.y + k0.y - k1.y;
            double z = sign0.z + k0.z - k1.z;
            session.setCurrentAnchor(new Vector3i((int) Math.round(x), (int) Math.round(y), (int) Math.round(z)));
        } finally {
        }
    }

    /** Shows the style picker only once a second wall style is installed. */
    private void applyWallStyleRow(@Nonnull UICommandBuilder commandBuilder, boolean visible) {
        List<String> styleIds = WallStyleCatalog.get().completeStyleIds();
        boolean show = visible && styleIds.size() > 1;
        commandBuilder.set("#WallStyleRow.Visible", show);
        if (!show) {
            return;
        }
        commandBuilder.set("#WallStyleName.TextSpans", wallStyleLabel());
        commandBuilder.set("#BtnStylePrev.TooltipTextSpans", Message.translation(MSG + ".stylePrev"));
        commandBuilder.set("#BtnStyleNext.TooltipTextSpans", Message.translation(MSG + ".styleNext"));
    }

    @Nonnull
    private Message wallStyleLabel() {
        WallStyle style = session.resolveStyle();
        if (style == null) {
            return Message.translation(MSG + ".styleUnknown");
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        WallStyle.Piece segment = style.piece(com.hexvane.aetherhaven.wall.WallPieceRole.SEGMENT);
        if (plugin != null && segment != null) {
            ConstructionDefinition def = plugin.getConstructionCatalog().get(segment.constructionId());
            if (def != null) {
                return def.displayNameMessage();
            }
        }
        return Message.raw(style.displayName());
    }

    private void refreshPreview(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (session.hasEditTarget() || session.isRemoveConfirmOpen()) {
            AetherhavenWorldPrefabPreview.clearAll(store, session.getPreviewEntityRefs());
            PlayerRef clearTarget = store.getComponent(ref, PlayerRef.getComponentType());
            if (clearTarget != null) {
                PlotPlacementClientPrefabPreview.hide(clearTarget);
            }
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (plugin == null) {
            return;
        }
        if (session.getPieceKind() == WallPlacementSession.PieceKind.TOWER) {
            session.prepareTowerForCommit();
        }
        String consId = session.resolveConstructionId();
        ConstructionDefinition def = plugin.getConstructionCatalog().get(consId);
        if (def == null) {
            AetherhavenWorldPrefabPreview.clearAll(store, session.getPreviewEntityRefs());
            if (pr != null) {
                PlotPlacementClientPrefabPreview.hide(pr);
            }
            return;
        }
        Path prefabPath = PrefabResolveUtil.resolvePrefabPath(def.getPrefabPath());
        if (prefabPath == null) {
            AetherhavenWorldPrefabPreview.clearAll(store, session.getPreviewEntityRefs());
            if (pr != null) {
                PlotPlacementClientPrefabPreview.hide(pr);
            }
            return;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = TownPlayerResolution.resolveTownAtPlayerOrActive(world, store, ref, tm);
        String err;
        if (town == null) {
            err = "need town";
        } else if (!town.playerCanPlacePlots(uc.getUuid())) {
            err = "permission";
        } else {
            err =
                PlotPlacementValidator.validate(
                    world, tm, town, uc.getUuid(), session.getCurrentAnchor(), session.getCurrentPrefabYaw(), def, plugin
                );
        }
        boolean valid = err == null;
        IPrefabBuffer buf = PrefabBufferUtil.getCached(prefabPath);
        try {
            session.reGroundSignYAtCurrentColumn(world, def, buf);
            List<PlotFootprintRecord> committedFps = new ArrayList<>();
            for (WallPlacementSession.CommittedStep step : session.getCommitted()) {
                ConstructionDefinition stepDef = plugin.getConstructionCatalog().get(step.constructionId);
                if (stepDef == null) {
                    continue;
                }
                Path stepPath = PrefabResolveUtil.resolvePrefabPath(stepDef.getPrefabPath());
                if (stepPath == null) {
                    continue;
                }
                IPrefabBuffer stepBuf = PrefabBufferUtil.getCached(stepPath);
                try {
                    Vector3i origin = step.ghostPrefabOriginWorld();
                    committedFps.add(PlotFootprintUtil.computeFootprint(origin, step.getPrefabYaw(), stepBuf));
                } finally {
                }
            }
            AetherhavenWorldPrefabPreview.clearAll(store, session.getPreviewEntityRefs());
            boolean skipInWorldSeedGhost =
                session.isSeededContinueFromEdit() && session.getCommitted().size() == 1;
            for (WallPlacementSession.CommittedStep step : session.getCommitted()) {
                if (skipInWorldSeedGhost) {
                    continue;
                }
                spawnPreviewForStep(store, plugin, step, session.getPreviewEntityRefs());
            }
            WallPlacementSession.CommittedStep last = session.getLastCommitted();
            PlotFootprintRecord currentFp = null;
            if (session.shouldShowNextPiecePreview()) {
                Vector3i currentOrigin =
                    def.resolvePrefabAnchorWorld(session.getCurrentAnchor(), session.getCurrentPrefabYaw());
                Ref<EntityStore> activeRef =
                    AetherhavenWorldPrefabPreview.spawnAtBlockCorner(
                        store,
                        currentOrigin,
                        AetherhavenWorldPrefabPreview.rotationFromYaw(session.getCurrentPrefabYaw()),
                        def.getPrefabPath(),
                        session.getCurrentRotationSteps(),
                        AetherhavenWorldPrefabPreview.ALL_LAYERS
                    );
                if (activeRef != null) {
                    session.getPreviewEntityRefs().add(activeRef);
                }
                PlotPlacementClientPrefabPreview.Payload activePayload =
                    PlotPlacementClientPrefabPreview.loadPayload(def.getPrefabPath(), session.getCurrentRotationSteps());
                if (activePayload != null) {
                    PlotPlacementClientPrefabPreview.sendEntityOverlayFull(
                        pr,
                        currentOrigin,
                        activePayload,
                        session.getCurrentPrefabYaw()
                    );
                }
                currentFp = PlotFootprintUtil.computeFootprint(currentOrigin, session.getCurrentPrefabYaw(), buf);
            } else if (pr != null) {
                PlotPlacementClientPrefabPreview.hide(pr);
            }
            PlotFootprintRecord previousFp = null;
            if (last != null) {
                ConstructionDefinition lastDef = plugin.getConstructionCatalog().get(last.constructionId);
                if (lastDef != null) {
                    Path lastPath = PrefabResolveUtil.resolvePrefabPath(lastDef.getPrefabPath());
                    if (lastPath != null) {
                        IPrefabBuffer lastBuf = PrefabBufferUtil.getCached(lastPath);
                        try {
                            Vector3i lastOrigin = last.ghostPrefabOriginWorld();
                            previousFp = PlotFootprintUtil.computeFootprint(lastOrigin, last.getPrefabYaw(), lastBuf);
                        } finally {
                        }
                    }
                }
            }
            if (pr != null) {
                WallPlacementWireframeOverlay.send(pr, currentFp, valid, previousFp, town, committedFps);
                PlotAssemblyPreviewSystem.repaintFrontierAfterExternalDebugClear(ref, store);
            }
        } finally {
        }
    }

    private static void spawnPreviewForStep(
        @Nonnull Store<EntityStore> store,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull WallPlacementSession.CommittedStep step,
        @Nonnull List<Ref<EntityStore>> refs
    ) {
        ConstructionDefinition def = plugin.getConstructionCatalog().get(step.constructionId);
        if (def == null) {
            return;
        }
        Path path = PrefabResolveUtil.resolvePrefabPath(def.getPrefabPath());
        if (path == null) {
            return;
        }
        Ref<EntityStore> ref =
            AetherhavenWorldPrefabPreview.spawnAtBlockCorner(
                store,
                step.ghostPrefabOriginWorld(),
                AetherhavenWorldPrefabPreview.rotationFromYaw(step.getPrefabYaw()),
                def.getPrefabPath(),
                step.rotationSteps,
                AetherhavenWorldPrefabPreview.ALL_LAYERS
            );
        if (ref != null) {
            refs.add(ref);
        }
    }

    /** Places the current preview, then advances the preview along {@code dir} for the next piece. */
    private void schedulePlaceAndExpand(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull WallCardinal dir) {
        World world = store.getExternalData().getWorld();
        world.execute(
            () -> {
                if (!ref.isValid()) {
                    return;
                }
                WallCardinal towerAttach = session.towerAttachDir();
                if (towerAttach != null) {
                    placeTowerAndLeaveIt(ref, store, towerAttach, dir);
                    return;
                }
                applyTowerUpgrade(ref, store, session.previewExpandDirection(dir));
                logWallDebug(
                    ref,
                    store,
                    "expandPad",
                    "outgoing=" + dir + (session.getPieceKind() == WallPlacementSession.PieceKind.TOWER ? " (commit tower)" : "")
                );
                if (commitCurrentPlacement(ref, store, dir, true)) {
                    logWallDebug(ref, store, "placedAndChained", "dir=" + dir);
                    refreshPreview(ref, store);
                }
                rebuild();
            }
        );
    }

    /**
     * Places the tower being previewed on the open face of the piece behind it, then leaves it toward {@code outgoing}.
     * The tower only fits on that one face, so the arrow the player pressed says where the run goes from the tower
     * rather than where the tower goes.
     */
    private void placeTowerAndLeaveIt(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull WallCardinal attach,
        @Nonnull WallCardinal outgoing
    ) {
        logWallDebug(ref, store, "expandPad", "tower attach=" + attach + " outgoing=" + outgoing);
        if (!commitCurrentPlacement(ref, store, attach, false)) {
            rebuild();
            return;
        }
        applyTowerUpgrade(ref, store, session.previewExpandDirection(outgoing));
        logWallDebug(ref, store, "placedTower", "outgoing=" + outgoing);
        refreshPreview(ref, store);
        rebuild();
    }

    /** Places the current preview without starting the next segment (for the last piece in a run). */
    private void schedulePlace(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        world.execute(
            () -> {
                if (!ref.isValid()) {
                    return;
                }
                // Nothing is lined up yet, so there is nothing to commit and this just ends the run.
                if (!session.shouldShowNextPiecePreview()) {
                    scheduleCancel(ref, store);
                    return;
                }
                WallCardinal dir = session.getPlacementExpandDir();
                if (dir == null) {
                    dir = session.getLastExpandDir();
                }
                logWallDebug(ref, store, "placeButton", dir == null ? "dir=-" : "dir=" + dir);
                if (commitCurrentPlacement(ref, store, dir, false)) {
                    logWallDebug(ref, store, "placedFinal", null);
                    scheduleCancel(ref, store);
                    return;
                }
                rebuild();
            }
        );
    }

    private void logWallDebug(
        @Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull String event, @Nullable String detail
    ) {
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (detail == null) {
            WallPlacementDebug.logState(pr, session, event);
        } else {
            WallPlacementDebug.log(pr, session, event, detail + " | " + session.describeState());
        }
    }

    /**
     * @param chainAfter when true, moves the preview to the next slot along {@code dir} after a successful place
     * @return whether placement succeeded
     */
    private boolean commitCurrentPlacement(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nullable WallCardinal dir,
        boolean chainAfter
    ) {
        boolean placingTower = session.getPieceKind() == WallPlacementSession.PieceKind.TOWER;
        WallPlacementSession.CommittedStep last = session.getLastCommitted();

        if (dir == null && chainAfter && (placingTower || last != null)) {
            sendError(store, ref, Message.translation(MSG + ".errorPickDirection"));
            return false;
        }

        if (placingTower) {
            session.prepareTowerForCommit();
        }

        if (dir != null) {
            session.setPlacementExpandDir(dir);
            if (placingTower) {
                session.previewExpandDirection(dir);
            } else {
                // The tower behind this piece opens the door the run leaves by, which can move it, so the piece is
                // planned again against the tower's new shape.
                WallPlacementSession.TowerUpgrade upgrade = session.upgradeLastCommittedTowerIfNeeded(dir);
                if (upgrade != null) {
                    applyTowerUpgrade(ref, store, upgrade);
                    session.previewExpandDirection(dir);
                }
            }
        }

        if (!tryPlace(ref, store)) {
            logWallDebug(ref, store, "placeFailed", dir == null ? "dir=-" : "dir=" + dir);
            return false;
        }
        if (chainAfter && dir != null) {
            // Lining the next piece up opens the far door on a tower we just placed, so the sign and plot have to
            // follow the shape the wand is showing.
            applyTowerUpgrade(ref, store, session.previewExpandDirection(dir));
        }
        return true;
    }

    /**
     * The run ends here, so a tower that opened a door for a piece the player never placed goes back to being an end
     * cap instead of being left with a doorway to nowhere.
     */
    private void closeDoorOnDanglingTower(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        WallPlacementSession.TowerUpgrade reverted = session.revertLastCommittedTowerUpgrade();
        if (reverted != null) {
            syncTowerToWorld(ref, store, reverted);
        }
    }

    /**
     * After a piece is undone, closes the door its tower opened for it so a tower at the end of a run goes back to
     * being an end cap.
     */
    private void revertTowerBehindUndonePiece(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull WallPlacementSession.CommittedStep undone
    ) {
        WallPlacementSession.TowerUpgrade reverted = session.revertLastCommittedTowerUpgrade();
        if (reverted == null) {
            return;
        }
        applyTowerUpgrade(ref, store, reverted);
        if (undone.chainExpandDir != null) {
            session.planPreviewFromLastCommitted(undone.chainExpandDir);
        }
    }

    /** Applies a tower door upgrade to the world: swaps the prefab and moves the sign when the tower shifted. */
    private void applyTowerUpgrade(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nullable WallPlacementSession.TowerUpgrade upgrade
    ) {
        if (upgrade == null) {
            return;
        }
        syncTowerToWorld(ref, store, upgrade);
        scheduleRefreshPreview(ref, store);
    }

    private void syncTowerToWorld(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull WallPlacementSession.TowerUpgrade upgrade
    ) {
        World world = store.getExternalData().getWorld();
        if (upgrade.moved()) {
            WallPlacementRemoveService.breakPlotSignAt(
                world, upgrade.previousSignAnchor().x, upgrade.previousSignAnchor().y, upgrade.previousSignAnchor().z
            );
        }
        syncLastCommittedTowerPlot(ref, store);
        WallPlacementSession.CommittedStep last = session.getLastCommitted();
        if (last == null || !WallPieceGeometry.isTowerConstructionId(last.constructionId)) {
            return;
        }
        PlotPlacementCommit.replacePlotSign(
            world,
            last.signAnchor.x,
            last.signAnchor.y,
            last.signAnchor.z,
            last.getPrefabYaw(),
            last.constructionId,
            last.plotId,
            store
        );
    }

    private void syncLastCommittedTowerPlot(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        WallPlacementSession.CommittedStep last = session.getLastCommitted();
        if (plugin == null || uc == null || last == null || last.towerConnectionDirs == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = TownPlayerResolution.resolveTownAtPlayerOrActive(world, store, ref, tm);
        if (town == null) {
            return;
        }
        PlotInstance plot = town.findPlotById(last.plotId);
        if (plot == null) {
            return;
        }
        plot.setConstructionId(last.constructionId);
        plot.setPlacementPrefabYaw(last.getPrefabYaw());
        ConstructionDefinition def = plugin.getConstructionCatalog().get(last.constructionId);
        Path prefabPath = def != null ? PrefabResolveUtil.resolvePrefabPath(def.getPrefabPath()) : null;
        if (prefabPath != null) {
            Vector3i anchor = last.ghostPrefabOriginWorld();
            IPrefabBuffer buf = PrefabBufferUtil.getCached(prefabPath);
            plot.applySignAndFootprint(
                last.signAnchor.x,
                last.signAnchor.y,
                last.signAnchor.z,
                PlotFootprintUtil.computeFootprint(anchor, last.getPrefabYaw(), buf)
            );
            plot.setPrefabWorldPlacement(anchor.x, anchor.y, anchor.z, last.getPrefabYaw());
        }
        tm.updateTown(town);
    }

    private boolean tryPlace(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return false;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return false;
        }
        String consId = session.resolveConstructionId();
        ConstructionDefinition def = plugin.getConstructionCatalog().get(consId);
        if (def == null || !def.isWallSegment()) {
            sendError(store, ref, Message.translation(MSG + ".errorUnknownPiece"));
            return false;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = TownPlayerResolution.resolveTownAtPlayerOrActive(world, store, ref, tm);
        if (town == null) {
            sendError(store, ref, Message.translation(MSG + ".errorNeedTown"));
            return false;
        }
        if (!town.playerCanPlacePlots(uc.getUuid())) {
            sendError(store, ref, Message.translation(MSG + ".errorPermission"));
            return false;
        }
        Vector3i previewAnchor = session.getCurrentAnchor();
        int rotationSteps = session.getCurrentRotationSteps();
        Path prefabPath = PrefabResolveUtil.resolvePrefabPath(def.getPrefabPath());
        PlotPlacementHeights.ResolvedPlacement resolved =
            PlotPlacementHeights.resolveWallPiece(previewAnchor, def, session.getCurrentPrefabYaw());
        Vector3i placedSignPos = resolved.signCell();
        Vector3i buildingAnchor = resolved.buildingPrefabAnchor();
        String err =
            prefabPath != null
                ? PlotPlacementValidator.validateWithResolvedHeights(
                    world,
                    tm,
                    town,
                    uc.getUuid(),
                    previewAnchor,
                    placedSignPos,
                    buildingAnchor,
                    session.getCurrentPrefabYaw(),
                    def,
                    plugin,
                    null
                )
                : PlotPlacementValidator.validate(
                    world, tm, town, uc.getUuid(), previewAnchor, session.getCurrentPrefabYaw(), def, plugin
                );
        if (err != null) {
            sendError(store, ref, Message.raw(err));
            return false;
        }
        UUID plotId = UUID.randomUUID();
        boolean placed =
            PlotPlacementCommit.placePlotSign(
                world,
                placedSignPos.x,
                placedSignPos.y,
                placedSignPos.z,
                session.getCurrentPrefabYaw(),
                consId,
                plotId,
                store
            );
        if (!placed) {
            sendError(store, ref, Message.translation(MSG + ".errorBlocked"));
            return false;
        }
        if (prefabPath != null) {
            IPrefabBuffer buf = PrefabBufferUtil.getCached(prefabPath);
            try {
                PlotFootprintRecord fp =
                    PlotFootprintUtil.computeFootprint(buildingAnchor, session.getCurrentPrefabYaw(), buf);
                PlotInstance inst =
                    new PlotInstance(
                        plotId,
                        consId,
                        PlotInstanceState.BLUEPRINTING,
                        fp,
                        placedSignPos.x,
                        placedSignPos.y,
                        placedSignPos.z,
                        System.currentTimeMillis()
                    );
                inst.setPrefabWorldPlacement(
                    buildingAnchor.x, buildingAnchor.y, buildingAnchor.z, session.getCurrentPrefabYaw()
                );
                town.addPlotInstance(inst);
                tm.updateTown(town);
            } finally {
            }
        }
        session.addCommitted(
            new WallPlacementSession.CommittedStep(
                plotId,
                consId,
                placedSignPos,
                rotationSteps,
                session.towerConnectionsForCommit(),
                session.chainExpandDirForCommit(),
                buildingAnchor
            )
        );
        session.setPlacementExpandDir(null);
        if (WallPieceGeometry.isTowerConstructionId(consId)) {
            session.afterTowerCommittedSwitchToWall();
        }
        session.setCurrentAnchor(new Vector3i(placedSignPos.x, previewAnchor.y, placedSignPos.z));
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.plotSign.placed"));
        }
        return true;
    }

    private void scheduleUndo(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        world.execute(
            () -> {
                if (!ref.isValid()) {
                    return;
                }
                WallPlacementSession.CommittedStep undone = session.undoLastCommitted();
                if (undone == null) {
                    rebuild();
                    return;
                }
                AetherhavenPlugin plugin = AetherhavenPlugin.get();
                UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
                if (plugin == null || uc == null) {
                    rebuild();
                    return;
                }
                TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
                TownRecord town = TownPlayerResolution.resolveTownAtPlayerOrActive(world, store, ref, tm);
                if (town != null) {
                    WallPlacementRemoveService.removeWallPlot(world, plugin, town, undone.plotId, store);
                }
                WallPlacementRemoveService.breakPlotSignAt(
                    world, undone.signAnchor.x, undone.signAnchor.y, undone.signAnchor.z
                );
                session.restoreStateAfterUndo(undone);
                revertTowerBehindUndonePiece(ref, store, undone);
                captureBirdsEyeSnapshot(ref, store);
                applyBirdsEyeCameraPacket(ref, store);
                refreshPreview(ref, store);
                rebuild();
            }
        );
    }

    private void scheduleRemoveTarget(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        world.execute(
            () -> {
                if (!ref.isValid()) {
                    return;
                }
                AetherhavenPlugin plugin = AetherhavenPlugin.get();
                UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
                if (plugin == null || uc == null) {
                    return;
                }
                TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
                TownRecord town = TownPlayerResolution.resolveTownAtPlayerOrActive(world, store, ref, tm);
                if (town == null) {
                    scheduleCancel(ref, store);
                    return;
                }
                UUID plotTarget = session.getEditTargetPlotId();
                UUID segTarget = session.getEditTargetSegmentId();
                if (plotTarget != null) {
                    WallPlacementRemoveService.removeWallPlot(world, plugin, town, plotTarget, store);
                } else if (segTarget != null) {
                    WallPlacementRemoveService.removeWallSegment(world, plugin, town, segTarget, store);
                }
                scheduleCancel(ref, store);
            }
        );
    }

    private void scheduleCancel(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        world.execute(
            () -> {
                if (!ref.isValid()) {
                    return;
                }
                closeDoorOnDanglingTower(ref, store);
                AetherhavenWorldPrefabPreview.clearAll(store, session.getPreviewEntityRefs());
                PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
                WallPlacementWireframeOverlay.clearFor(pr);
                UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
                if (uc != null) {
                    WallPlacementSessions.remove(uc.getUuid());
                }
                if (pr != null) {
                    PlotPlacementCameraUtil.resetToPlayerCamera(pr);
                }
                close();
            }
        );
    }

    private static void bind(@Nonnull UIEventBuilder eventBuilder, @Nonnull String selector, @Nonnull String action) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, selector, new EventData().append("Action", action), false);
    }

    private void sendError(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull Message msg) {
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(msg);
        }
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC =
            BuilderCodec.builder(PageData.class, PageData::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .add()
                .append(new KeyedCodec<>("BirdsEyeDistance", Codec.FLOAT), (d, v) -> d.birdsEyeDistance = v, d -> d.birdsEyeDistance)
                .add()
                .build();

        @Nullable
        private String action;

        @Nullable
        private Float birdsEyeDistance;
    }
}
