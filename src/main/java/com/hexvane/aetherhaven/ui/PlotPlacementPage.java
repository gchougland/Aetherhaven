package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.placement.PlotFootprintUtil;
import com.hexvane.aetherhaven.placement.PlotPlacementCommit;
import com.hexvane.aetherhaven.placement.PlotSignGrounding;
import com.hexvane.aetherhaven.placement.PlotPlacementSession;
import com.hexvane.aetherhaven.placement.PlotPlacementSessions;
import com.hexvane.aetherhaven.placement.PlotPlacementRotationUtil;
import com.hexvane.aetherhaven.placement.PlotPlacementValidator;
import com.hexvane.aetherhaven.placement.PlotPlacementCameraUtil;
import com.hexvane.aetherhaven.placement.PlotBuildingRelocation;
import com.hexvane.aetherhaven.placement.PlotPlacementNudgeUtil;
import com.hexvane.aetherhaven.placement.PlotPlacementWireframeOverlay;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.placement.PlotPreviewSpawner;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PlotPlacementPage extends InteractiveCustomUIPage<PlotPlacementPage.PageData> {
    @Nonnull
    private final PlotPlacementSession session;

    private boolean birdsEyeEnabled;
    private float birdsEyeDistance = PlotPlacementCameraUtil.DEFAULT_DISTANCE;

    /** Cancels in-flight smoothed pan when starting a new pan or closing birds-eye. */
    private int smoothPanGeneration;

    /** Move building: first Place click shows warning; confirm commits. */
    private boolean movePlaceConfirmOpen;

    /** Coalesces {@link #scheduleRefreshPreview} when {@link #build} runs many times in one frame (avoids debug clear spam). */
    private int placementWireframeRefreshSerial;

    public PlotPlacementPage(@Nonnull PlayerRef playerRef, @Nonnull PlotPlacementSession session) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.session = session;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.append("Aetherhaven/PlotPlacementPage.ui");
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        Player player = store.getComponent(ref, Player.getComponentType());
        CombinedItemContainer inv =
            player != null ? InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING) : null;
        ConstructionDefinition def =
            plugin != null ? plugin.getConstructionCatalog().get(session.getConstructionId()) : null;
        String name = def != null ? def.getDisplayName() : session.getConstructionId();
        Vector3i sign = session.getAnchor();
        Vector3i prefabO =
            def != null
                ? def.resolvePrefabAnchorWorld(sign, session.getPrefabYaw())
                : new Vector3i(sign.x, sign.y, sign.z);
        commandBuilder.set(
            "#Summary.TextSpans",
            Message.translation("server.aetherhaven.ui.plotplacement.summary").param("building", name)
        );
        commandBuilder.set(
            "#Details.TextSpans",
            Message.translation("server.aetherhaven.ui.plotplacement.detailsBlock")
                .param("sx", sign.x)
                .param("sy", sign.y)
                .param("sz", sign.z)
                .param("ox", prefabO.x)
                .param("oy", prefabO.y)
                .param("oz", prefabO.z)
                .param("step", session.getRotationSteps())
        );
        commandBuilder.set(
            "#Tips.TextSpans",
            Message.join(
                Message.translation("server.aetherhaven.ui.plotplacement.tipsControls"),
                Message.raw("\n\n"),
                Message.translation("server.aetherhaven.ui.plotplacement.cameraHint")
            )
        );

        commandBuilder.set("#BirdsEyeToggle #CheckBox.Value", birdsEyeEnabled);
        commandBuilder.set("#BirdsEyeZoomRow.Visible", birdsEyeEnabled);
        commandBuilder.set("#BirdsEyePanColumn.Visible", birdsEyeEnabled);
        commandBuilder.set("#BirdsEyeDistanceSlider.Value", birdsEyeDistance);
        commandBuilder.set(
            "#BirdsEyeDistanceValue.TextSpans",
            Message.raw(String.format("%.0f", birdsEyeDistance))
        );
        commandBuilder.set("#BtnZoomOut.Disabled", birdsEyeDistance >= PlotPlacementCameraUtil.MAX_DISTANCE - 0.01f);
        commandBuilder.set("#BtnZoomIn.Disabled", birdsEyeDistance <= PlotPlacementCameraUtil.MIN_DISTANCE + 0.01f);

        boolean moveMode = session.isMoveMode();
        boolean moveConfirm = moveMode && movePlaceConfirmOpen;
        commandBuilder.set("#MoveConfirmGroup.Visible", moveConfirm);
        commandBuilder.set("#PlaceButton.Visible", !moveConfirm);
        if (moveMode) {
            commandBuilder.set(
                "#PlaceButton.TextSpans",
                Message.translation("server.aetherhaven.ui.plotplacement.placeMove")
            );
        } else {
            commandBuilder.set("#PlaceButton.TextSpans", Message.translation("server.aetherhaven.ui.plotplacement.place"));
        }
        if (!moveMode && plugin != null && inv != null) {
            List<String> validIds = listConstructionIdsWithPlotTokens(plugin, inv);
            if (!validIds.isEmpty() && !validIds.contains(session.getConstructionId())) {
                session.setConstructionId(validIds.get(0));
            }
            List<DropdownEntryInfo> entries = collectPlotDropdownEntries(plugin, inv);
            commandBuilder.set("#PlotTypeDropdown.Entries", entries);
            commandBuilder.set("#PlotTypeDropdown.Visible", !entries.isEmpty());
            commandBuilder.set("#PlotTypeLabel.Visible", !entries.isEmpty());
            if (!entries.isEmpty()) {
                commandBuilder.set("#PlotTypeDropdown.Value", session.getConstructionId());
            }
        } else {
            commandBuilder.set("#PlotTypeDropdown.Visible", false);
            commandBuilder.set("#PlotTypeLabel.Visible", false);
        }

        bind(eventBuilder, "#BtnXm", "MoveXm");
        bind(eventBuilder, "#BtnXp", "MoveXp");
        bind(eventBuilder, "#BtnZm", "MoveZm");
        bind(eventBuilder, "#BtnZp", "MoveZp");
        bind(eventBuilder, "#BtnYm", "MoveYm");
        bind(eventBuilder, "#BtnYp", "MoveYp");
        bind(eventBuilder, "#BtnRotate", "Rotate");
        bind(eventBuilder, "#BtnZoomOut", "ZoomOut");
        bind(eventBuilder, "#BtnZoomIn", "ZoomIn");
        bind(eventBuilder, "#BtnPanXm", "PanXm");
        bind(eventBuilder, "#BtnPanXp", "PanXp");
        bind(eventBuilder, "#BtnPanZm", "PanZm");
        bind(eventBuilder, "#BtnPanZp", "PanZp");
        if (moveConfirm) {
            bind(eventBuilder, "#MoveConfirmButton", "ConfirmMove");
            bind(eventBuilder, "#MoveConfirmBackButton", "BackMove");
        } else {
            bind(eventBuilder, "#PlaceButton", "Place");
        }
        bind(eventBuilder, "#CancelButton", "Cancel");

        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#PlotTypeDropdown",
            EventData.of("@ConstructionId", "#PlotTypeDropdown.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#BirdsEyeToggle #CheckBox",
            EventData.of("@BirdsEye", "#BirdsEyeToggle #CheckBox.Value"),
            false
        );
        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#BirdsEyeDistanceSlider",
            EventData.of("@BirdsEyeDistance", "#BirdsEyeDistanceSlider.Value"),
            false
        );

        scheduleRefreshPreview(ref, store);
    }

    @Override
    public void onDismiss(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        world.execute(
            () -> {
                if (!ref.isValid()) {
                    return;
                }
                smoothPanGeneration++;
                if (!birdsEyeEnabled) {
                    return;
                }
                PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
                if (pr != null) {
                    PlotPlacementCameraUtil.resetToPlayerCamera(pr);
                }
            }
        );
    }

    /**
     * Rotates the prefab 90° while keeping the axis-aligned footprint center fixed (avoids spinning around the buffer
     * origin corner).
     */
    private void applyRotatePreservingFootprintCenter() {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        ConstructionDefinition def = plugin != null ? plugin.getConstructionCatalog().get(session.getConstructionId()) : null;
        if (def == null) {
            session.rotateClockwise90();
            return;
        }
        Path prefabPath = resolvePrefabAssetPath(def.getPrefabPath());
        if (prefabPath == null) {
            session.rotateClockwise90();
            return;
        }
        IPrefabBuffer buf = PrefabBufferUtil.getCached(prefabPath);
        try {
            PlotPlacementRotationUtil.rotateClockwise90PreservingFootprintCenter(session, def, buf);
        } finally {
            buf.release();
        }
    }

    private static void bind(@Nonnull UIEventBuilder eventBuilder, @Nonnull String selector, @Nonnull String action) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, selector, new EventData().append("Action", action), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.constructionId != null && !data.constructionId.isBlank()) {
            if (session.isMoveMode()) {
                scheduleRebuild(ref, store);
                return;
            }
            String id = data.constructionId.trim();
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            ConstructionDefinition def = plugin != null ? plugin.getConstructionCatalog().get(id) : null;
            Player player = store.getComponent(ref, Player.getComponentType());
            CombinedItemContainer inv =
                player != null ? InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING) : null;
            if (def == null || inv == null || !hasPlotToken(inv, def)) {
                sendError(store, ref, "You need the matching plot token in your inventory for that building.");
            } else {
                session.setConstructionId(id);
            }
            scheduleRebuild(ref, store);
            return;
        }
        if (data.birdsEye != null) {
            birdsEyeEnabled = data.birdsEye;
            smoothPanGeneration++;
            if (birdsEyeEnabled) {
                session.resetBirdsEyePan();
                session.clearBirdsEyeSnapshot();
            } else {
                session.clearBirdsEyeSnapshot();
            }
            birdsEyeDistance =
                Math.max(
                    PlotPlacementCameraUtil.MIN_DISTANCE,
                    Math.min(PlotPlacementCameraUtil.MAX_DISTANCE, birdsEyeDistance)
                );
            scheduleApplyCameraAndRebuild(ref, store);
            return;
        }
        if (data.birdsEyeDistance != null) {
            birdsEyeDistance =
                Math.max(
                    PlotPlacementCameraUtil.MIN_DISTANCE,
                    Math.min(PlotPlacementCameraUtil.MAX_DISTANCE, data.birdsEyeDistance)
                );
            if (birdsEyeEnabled) {
                // Full rebuild resets the slider mid-drag; only sync labels + camera.
                scheduleApplyCameraAfterSliderDrag(ref, store);
            } else {
                scheduleRebuild(ref, store);
            }
            return;
        }
        if (data.action == null) {
            return;
        }
        float yawRad = PlotPlacementNudgeUtil.getPlayerYawRadians(ref, store);
        switch (data.action) {
            case "MoveXm" ->
                PlotPlacementNudgeUtil.nudgeHorizontal(
                    session, birdsEyeEnabled, yawRad, PlotPlacementNudgeUtil.Horizontal.NEG_X
                );
            case "MoveXp" ->
                PlotPlacementNudgeUtil.nudgeHorizontal(
                    session, birdsEyeEnabled, yawRad, PlotPlacementNudgeUtil.Horizontal.POS_X
                );
            case "MoveZm" ->
                PlotPlacementNudgeUtil.nudgeHorizontal(
                    session, birdsEyeEnabled, yawRad, PlotPlacementNudgeUtil.Horizontal.NEG_Z
                );
            case "MoveZp" ->
                PlotPlacementNudgeUtil.nudgeHorizontal(
                    session, birdsEyeEnabled, yawRad, PlotPlacementNudgeUtil.Horizontal.POS_Z
                );
            case "MoveYm" -> session.nudge(0, -1, 0);
            case "MoveYp" -> session.nudge(0, 1, 0);
            case "Rotate" -> applyRotatePreservingFootprintCenter();
            case "PanXm" -> {
                scheduleSmoothPan(ref, store, -PlotPlacementCameraUtil.PAN_STEP, 0.0);
                return;
            }
            case "PanXp" -> {
                scheduleSmoothPan(ref, store, PlotPlacementCameraUtil.PAN_STEP, 0.0);
                return;
            }
            case "PanZm" -> {
                scheduleSmoothPan(ref, store, 0.0, -PlotPlacementCameraUtil.PAN_STEP);
                return;
            }
            case "PanZp" -> {
                scheduleSmoothPan(ref, store, 0.0, PlotPlacementCameraUtil.PAN_STEP);
                return;
            }
            case "ZoomOut" -> {
                // Larger distance = farther camera / wider overview (matches zoom-out icon).
                birdsEyeDistance =
                    Math.min(
                        PlotPlacementCameraUtil.MAX_DISTANCE,
                        birdsEyeDistance + PlotPlacementCameraUtil.DISTANCE_STEP
                    );
                if (birdsEyeEnabled) {
                    scheduleApplyCameraAndRebuild(ref, store);
                } else {
                    scheduleRebuild(ref, store);
                }
                return;
            }
            case "ZoomIn" -> {
                // Smaller distance = closer camera / tighter view (matches zoom-in icon).
                birdsEyeDistance =
                    Math.max(
                        PlotPlacementCameraUtil.MIN_DISTANCE,
                        birdsEyeDistance - PlotPlacementCameraUtil.DISTANCE_STEP
                    );
                if (birdsEyeEnabled) {
                    scheduleApplyCameraAndRebuild(ref, store);
                } else {
                    scheduleRebuild(ref, store);
                }
                return;
            }
            case "Cancel" -> {
                if (session.isMoveMode() && movePlaceConfirmOpen) {
                    movePlaceConfirmOpen = false;
                    scheduleRebuild(ref, store);
                    return;
                }
                scheduleCancel(ref, store);
                return;
            }
            case "BackMove" -> {
                movePlaceConfirmOpen = false;
                scheduleRebuild(ref, store);
                return;
            }
            case "ConfirmMove" -> {
                movePlaceConfirmOpen = false;
                schedulePlace(ref, store);
                return;
            }
            case "Place" -> {
                if (session.isMoveMode() && !movePlaceConfirmOpen) {
                    movePlaceConfirmOpen = true;
                    scheduleRebuild(ref, store);
                    return;
                }
                schedulePlace(ref, store);
                return;
            }
            default -> {
                return;
            }
        }
        scheduleRebuild(ref, store);
    }

    private void scheduleApplyCameraAndRebuild(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        world.execute(
            () -> {
                if (!ref.isValid()) {
                    return;
                }
                PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
                if (pr != null) {
                    if (birdsEyeEnabled) {
                        applyBirdsEyeCameraPacket(ref, store);
                    } else {
                        PlotPlacementCameraUtil.resetToPlayerCamera(pr);
                    }
                }
                rebuild();
            }
        );
    }

    private void scheduleApplyCameraAfterSliderDrag(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        world.execute(
            () -> {
                if (!ref.isValid()) {
                    return;
                }
                if (birdsEyeEnabled) {
                    applyBirdsEyeCameraPacket(ref, store);
                }
                syncBirdsEyeDistanceUiOnly();
            }
        );
    }

    private void syncBirdsEyeDistanceUiOnly() {
        UICommandBuilder cmd = new UICommandBuilder();
        cmd.set("#BirdsEyeDistanceValue.TextSpans", Message.raw(String.format("%.0f", birdsEyeDistance)));
        cmd.set("#BtnZoomOut.Disabled", birdsEyeDistance >= PlotPlacementCameraUtil.MAX_DISTANCE - 0.01f);
        cmd.set("#BtnZoomIn.Disabled", birdsEyeDistance <= PlotPlacementCameraUtil.MIN_DISTANCE + 0.01f);
        sendUpdate(cmd, new UIEventBuilder(), false);
    }

    /**
     * One-time framing when birds-eye is enabled: center on the current preview footprint (or plot sign if
     * unavailable). Does not run again when the preview moves.
     */
    private void captureBirdsEyeSnapshot(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        Vector3i anchor = session.getAnchor();
        if (plugin == null) {
            session.setBirdsEyeSnapshot(anchor.x + 0.5, anchor.y + 0.5, anchor.z + 0.5);
            return;
        }
        ConstructionDefinition def = plugin.getConstructionCatalog().get(session.getConstructionId());
        if (def == null) {
            session.setBirdsEyeSnapshot(anchor.x + 0.5, anchor.y + 0.5, anchor.z + 0.5);
            return;
        }
        Path prefabPath = resolvePrefabAssetPath(def.getPrefabPath());
        if (prefabPath == null) {
            session.setBirdsEyeSnapshot(anchor.x + 0.5, anchor.y + 0.5, anchor.z + 0.5);
            return;
        }
        IPrefabBuffer buf = PrefabBufferUtil.getCached(prefabPath);
        try {
            Vector3i prefabOrigin = def.resolvePrefabAnchorWorld(anchor, session.getPrefabYaw());
            PlotFootprintRecord fp = PlotFootprintUtil.computeFootprint(prefabOrigin, session.getPrefabYaw(), buf);
            session.setBirdsEyeSnapshot(
                (fp.getMinX() + fp.getMaxX() + 1) / 2.0,
                (fp.getMinY() + fp.getMaxY() + 1) / 2.0,
                (fp.getMinZ() + fp.getMaxZ() + 1) / 2.0
            );
        } finally {
            buf.release();
        }
    }

    private void scheduleSmoothPan(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, double totalDx, double totalDz) {
        if (!birdsEyeEnabled) {
            scheduleRebuild(ref, store);
            return;
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return;
        }
        World world = store.getExternalData().getWorld();
        smoothPanGeneration++;
        final int gen = smoothPanGeneration;
        int steps = PlotPlacementCameraUtil.SMOOTH_PAN_STEPS;
        long stepDelay = PlotPlacementCameraUtil.SMOOTH_PAN_STEP_DELAY_MS;
        double stepDx = totalDx / steps;
        double stepDz = totalDz / steps;
        for (int i = 1; i <= steps; i++) {
            long delayMs = stepDelay * i;
            plugin.scheduleOnWorld(
                world,
                () -> {
                    if (gen != smoothPanGeneration || !ref.isValid() || !birdsEyeEnabled) {
                        return;
                    }
                    session.addBirdsEyePan(stepDx, stepDz);
                    applyBirdsEyeCameraPacket(ref, store);
                },
                delayMs
            );
        }
    }

    /** Sends the birds-eye camera packet without rebuilding the UI. */
    private void applyBirdsEyeCameraPacket(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (!birdsEyeEnabled) {
            return;
        }
        if (!session.hasBirdsEyeSnapshot()) {
            captureBirdsEyeSnapshot(ref, store);
        }
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (pr == null) {
            return;
        }
        if (tc == null) {
            PlotPlacementCameraUtil.resetToPlayerCamera(pr);
            return;
        }
        Vector3d p = tc.getPosition();
        double fx;
        double fy;
        double fz;
        if (session.hasBirdsEyeSnapshot()) {
            fx = session.getBirdsEyeSnapshotX();
            fy = session.getBirdsEyeSnapshotY();
            fz = session.getBirdsEyeSnapshotZ();
        } else {
            Vector3i a = session.getAnchor();
            fx = a.x + 0.5;
            fy = a.y + 0.5;
            fz = a.z + 0.5;
        }
        fx += session.getBirdsEyePanX();
        fz += session.getBirdsEyePanZ();
        PlotPlacementCameraUtil.applyBirdsEye(pr, birdsEyeDistance, p.x, p.y, p.z, fx, fy, fz);
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
        if (session.isMoveMode()) {
            return PlotBuildingRelocation.tryCommit(ref, store, session, uc.getUuid());
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.findTownForPlayerInWorld(uc.getUuid());
        if (town == null) {
            sendError(store, ref, "You need a town (place a charter) first.");
            return false;
        }
        if (!town.playerHasBuildPermission(uc.getUuid())) {
            sendError(store, ref, "You do not have permission to place buildings for this town.");
            return false;
        }
        ConstructionDefinition def = plugin.getConstructionCatalog().get(session.getConstructionId());
        if (def == null) {
            sendError(store, ref, "Unknown construction: " + session.getConstructionId());
            return false;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        CombinedItemContainer inv =
            player != null ? InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING) : null;
        if (inv == null || !hasPlotToken(inv, def)) {
            sendError(store, ref, "You need the plot token for this building in your inventory to place it.");
            return false;
        }
        Vector3i previewAnchor = session.getAnchor();
        Path prefabPathEarly = resolvePrefabAssetPath(def.getPrefabPath());
        Vector3i placedSignPos;
        if (prefabPathEarly != null) {
            IPrefabBuffer groundBuf = PrefabBufferUtil.getCached(prefabPathEarly);
            try {
                placedSignPos = PlotSignGrounding.resolveSignCell(world, previewAnchor, def, session.getPrefabYaw(), groundBuf);
            } finally {
                groundBuf.release();
            }
        } else {
            placedSignPos = previewAnchor;
        }
        String err =
            PlotPlacementValidator.validate(world, tm, town, uc.getUuid(), previewAnchor, session.getPrefabYaw(), def, plugin);
        if (err != null) {
            sendError(store, ref, err);
            return false;
        }
        String tokenId = def.getPlotTokenItemId();
        if (tokenId == null || tokenId.isBlank()) {
            sendError(store, ref, "This construction has no plot token configured.");
            return false;
        }
        ItemStackTransaction tokenTx = inv.removeItemStack(new ItemStack(tokenId, 1));
        if (!tokenTx.succeeded()) {
            sendError(store, ref, "Could not consume plot token (inventory changed?).");
            return false;
        }
        UUID plotId = UUID.randomUUID();
        boolean placed =
            PlotPlacementCommit.placePlotSign(
                world,
                placedSignPos.x,
                placedSignPos.y,
                placedSignPos.z,
                session.getPrefabYaw(),
                session.getConstructionId(),
                plotId,
                store
            );
        if (!placed) {
            inv.addItemStack(new ItemStack(tokenId, 1));
            sendError(store, ref, "Could not place plot sign (blocked or invalid spot).");
            return false;
        }
        Path prefabPath = prefabPathEarly != null ? prefabPathEarly : resolvePrefabAssetPath(def.getPrefabPath());
        if (prefabPath != null) {
            IPrefabBuffer buf = PrefabBufferUtil.getCached(prefabPath);
            try {
                Vector3i prefabOrigin = def.resolvePrefabAnchorWorld(previewAnchor, session.getPrefabYaw());
                PlotFootprintRecord fp = PlotFootprintUtil.computeFootprint(prefabOrigin, session.getPrefabYaw(), buf);
                PlotInstance inst =
                    new PlotInstance(
                        plotId,
                        session.getConstructionId(),
                        PlotInstanceState.BLUEPRINTING,
                        fp,
                        previewAnchor.x,
                        previewAnchor.y,
                        previewAnchor.z,
                        System.currentTimeMillis()
                    );
                inst.setPlacementPrefabYaw(session.getPrefabYaw());
                town.addPlotInstance(inst);
                tm.updateTown(town);
            } finally {
                buf.release();
            }
        } else {
            PlotFootprintRecord mini =
                new PlotFootprintRecord(
                    previewAnchor.x,
                    previewAnchor.y,
                    previewAnchor.z,
                    previewAnchor.x,
                    previewAnchor.y,
                    previewAnchor.z
                );
            PlotInstance miniPlot =
                new PlotInstance(
                    plotId,
                    session.getConstructionId(),
                    PlotInstanceState.BLUEPRINTING,
                    mini,
                    previewAnchor.x,
                    previewAnchor.y,
                    previewAnchor.z,
                    System.currentTimeMillis()
                );
            miniPlot.setPlacementPrefabYaw(session.getPrefabYaw());
            town.addPlotInstance(miniPlot);
            tm.updateTown(town);
        }
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.translation("server.aetherhaven.plotSign.placed"));
        }
        return true;
    }

    private void scheduleRefreshPreview(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        final int serial = ++placementWireframeRefreshSerial;
        world.execute(
            () -> {
                if (!ref.isValid()) {
                    return;
                }
                if (serial != placementWireframeRefreshSerial) {
                    return;
                }
                refreshPreview(ref, store);
            }
        );
    }

    private void scheduleRebuild(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        world.execute(
            () -> {
                if (!ref.isValid()) {
                    return;
                }
                rebuild();
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
                PlotPreviewSpawner.clear(store, session.getPreviewEntityRefs());
                PlayerRef prCancel = store.getComponent(ref, PlayerRef.getComponentType());
                PlotPlacementWireframeOverlay.clearFor(prCancel);
                UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
                if (uc != null) {
                    PlotPlacementSessions.remove(uc.getUuid());
                }
                close();
            }
        );
    }

    private void schedulePlace(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        world.execute(
            () -> {
                if (!ref.isValid()) {
                    return;
                }
                if (tryPlace(ref, store)) {
                    PlotPreviewSpawner.clear(store, session.getPreviewEntityRefs());
                    PlayerRef prDone = store.getComponent(ref, PlayerRef.getComponentType());
                    PlotPlacementWireframeOverlay.clearFor(prDone);
                    UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
                    if (uc != null) {
                        PlotPlacementSessions.remove(uc.getUuid());
                    }
                    close();
                } else {
                    rebuild();
                }
            }
        );
    }

    private void refreshPreview(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (plugin == null) {
            return;
        }
        ConstructionDefinition def = plugin.getConstructionCatalog().get(session.getConstructionId());
        if (def == null) {
            PlotPreviewSpawner.clear(store, session.getPreviewEntityRefs());
            PlotPlacementWireframeOverlay.clearFor(pr);
            return;
        }
        Path prefabPath = resolvePrefabAssetPath(def.getPrefabPath());
        if (prefabPath == null) {
            PlotPreviewSpawner.clear(store, session.getPreviewEntityRefs());
            PlotPlacementWireframeOverlay.clearFor(pr);
            return;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            PlotPreviewSpawner.clear(store, session.getPreviewEntityRefs());
            PlotPlacementWireframeOverlay.clearFor(pr);
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town = tm.findTownForPlayerInWorld(uc.getUuid());
        String placementErr;
        if (town == null) {
            placementErr = "You need a town (place a charter) first.";
        } else if (!town.playerHasBuildPermission(uc.getUuid())) {
            placementErr = "You do not have permission to place buildings for this town.";
        } else {
            placementErr =
                PlotPlacementValidator.validate(
                    world,
                    tm,
                    town,
                    uc.getUuid(),
                    session.getAnchor(),
                    session.getPrefabYaw(),
                    def,
                    plugin,
                    session.getMovePlotId()
                );
        }
        boolean placementValid = placementErr == null;
        IPrefabBuffer buf = PrefabBufferUtil.getCached(prefabPath);
        try {
            Vector3i prefabOrigin = def.resolvePrefabAnchorWorld(session.getAnchor(), session.getPrefabYaw());
            PlotFootprintRecord fp = PlotFootprintUtil.computeFootprint(prefabOrigin, session.getPrefabYaw(), buf);
            PlotPreviewSpawner.rebuild(store, prefabOrigin, session.getPrefabYaw(), buf, session.getPreviewEntityRefs());
            if (pr != null) {
                PlotPlacementWireframeOverlay.send(pr, fp, placementValid, town);
            }
        } finally {
            buf.release();
        }
    }

    private void sendError(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull String text) {
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.raw(text));
        }
    }

    /**
     * Catalog order: first construction the player has a plot token for becomes the default when opening placement.
     */
    @Nonnull
    private static List<String> listConstructionIdsWithPlotTokens(
        @Nonnull AetherhavenPlugin plugin, @Nonnull CombinedItemContainer inv
    ) {
        ObjectArrayList<String> ids = new ObjectArrayList<>();
        for (ConstructionDefinition d : plugin.getConstructionCatalog().list()) {
            String token = d.getPlotTokenItemId();
            if (token == null || token.isBlank()) {
                continue;
            }
            if (InventoryMaterials.count(inv, token) <= 0) {
                continue;
            }
            ids.add(d.getId());
        }
        return ids;
    }

    @Nonnull
    private static List<DropdownEntryInfo> collectPlotDropdownEntries(
        @Nonnull AetherhavenPlugin plugin, @Nonnull CombinedItemContainer inv
    ) {
        ObjectArrayList<DropdownEntryInfo> entries = new ObjectArrayList<>();
        for (String id : listConstructionIdsWithPlotTokens(plugin, inv)) {
            ConstructionDefinition d = plugin.getConstructionCatalog().get(id);
            if (d == null) {
                continue;
            }
            entries.add(new DropdownEntryInfo(LocalizableString.fromString(d.getDisplayName()), d.getId()));
        }
        return entries;
    }

    private static boolean hasPlotToken(@Nonnull CombinedItemContainer inv, @Nonnull ConstructionDefinition def) {
        String token = def.getPlotTokenItemId();
        if (token == null || token.isBlank()) {
            return false;
        }
        return InventoryMaterials.count(inv, token) > 0;
    }

    @Nullable
    private static Path resolvePrefabAssetPath(@Nullable String key) {
        return PrefabResolveUtil.resolvePrefabPath(key);
    }

    @Nullable
    public static String defaultConstructionFromInventory(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (plugin == null || player == null) {
            return null;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
        List<String> ids = listConstructionIdsWithPlotTokens(plugin, inv);
        return ids.isEmpty() ? null : ids.get(0);
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, a) -> d.action = a, d -> d.action)
            .add()
            .append(new KeyedCodec<>("@ConstructionId", Codec.STRING), (d, v) -> d.constructionId = v, d -> d.constructionId)
            .add()
            .append(new KeyedCodec<>("@BirdsEye", Codec.BOOLEAN), (d, v) -> d.birdsEye = v, d -> d.birdsEye)
            .add()
            .append(new KeyedCodec<>("@BirdsEyeDistance", Codec.FLOAT), (d, v) -> d.birdsEyeDistance = v, d -> d.birdsEyeDistance)
            .add()
            .build();

        @Nullable
        private String action;
        @Nullable
        private String constructionId;
        @Nullable
        private Boolean birdsEye;
        @Nullable
        private Float birdsEyeDistance;
    }
}
