package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.plot.PlotTokenInventory;
import com.hexvane.aetherhaven.plot.PlotTokenPlacementOption;
import com.hexvane.aetherhaven.placement.PlotPlacementSessionFactory;
import com.hexvane.aetherhaven.placement.PlotPlacementSnapUtil;
import com.hexvane.aetherhaven.quest.QuestProgressionService;
import com.hexvane.aetherhaven.placement.PlotFootprintUtil;
import com.hexvane.aetherhaven.placement.PlotPlacementCommit;
import com.hexvane.aetherhaven.placement.PlotPlacementHeights;
import com.hexvane.aetherhaven.placement.PlotPlacementSession;
import com.hexvane.aetherhaven.placement.PlotPlacementSessions;
import com.hexvane.aetherhaven.placement.PlotPlacementRotationUtil;
import com.hexvane.aetherhaven.placement.PlotPlacementValidator;
import com.hexvane.aetherhaven.placement.PlotPlacementCameraUtil;
import com.hexvane.aetherhaven.placement.PlotBuildingRelocation;
import com.hexvane.aetherhaven.placement.PlotPlacementNudgeUtil;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyPreviewSystem;
import com.hexvane.aetherhaven.placement.PlotPlacementClientPrefabPreview;
import com.hexvane.aetherhaven.placement.PlotPlacementPreviewSync;
import com.hexvane.aetherhaven.placement.PlotPlacementWireframeOverlay;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownPlayerResolution;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import org.joml.Vector3i;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import org.joml.Vector3d;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hexvane.aetherhaven.ui.AetherhavenInteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.ui.DropdownEntryInfo;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PlotPlacementPage extends AetherhavenInteractiveCustomUIPage<PlotPlacementPage.PageData> {
    /** Shared with {@link CharterRelocationPage} (same .ui document). */
    static final String MSG_PLOT_UI = "aetherhaven_plot_move.aetherhaven.ui.plotplacement";

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

    @Nullable
    private String lastPreviewConstructionId;
    private int lastPreviewRotationSteps = -1;
    @Nullable
    private Vector3i lastPreviewOriginFloored;
    private boolean clientPrefabPreviewActive;

    /** Selected plot type dropdown value (construction id or move:plotUuid). */
    @Nullable
    private String selectedDropdownValue;

    public PlotPlacementPage(@Nonnull PlayerRef playerRef, @Nonnull PlotPlacementSession session) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.session = session;
        this.selectedDropdownValue = resolveDropdownValueFromSession(session);
    }

    /**
     * Pushes plot-placement chrome through {@link Message} so keys resolve on the server. The client often shows raw
     * message ids for long bundle-prefixed strings in {@code .ui} markup ({@code $C.@Title}, {@code @CheckBoxWithLabel},
     * {@code TooltipText}, etc.).
     */
    public static void applySharedPlotPlacementLocalization(@Nonnull UICommandBuilder commandBuilder) {
        String p = MSG_PLOT_UI;
        commandBuilder.set("#PlotPlacementTitle.TextSpans", Message.translation(p + ".title"));
        commandBuilder.set("#PlotTypeLabel.TextSpans", Message.translation(p + ".plotType"));
        commandBuilder.set("#HeightLabel.TextSpans", Message.translation(p + ".heightSection"));
        commandBuilder.set("#MoveConfirmText.TextSpans", Message.translation(p + ".moveWarningText"));
        commandBuilder.set("#MoveConfirmButton.TextSpans", Message.translation(p + ".moveConfirm"));
        commandBuilder.set("#MoveConfirmBackButton.TextSpans", Message.translation(p + ".moveBack"));
        commandBuilder.set("#CancelButton.TextSpans", Message.translation(p + ".cancel"));
        commandBuilder.set("#BirdsEyeToggle #BirdsEyeLabel.TextSpans", Message.translation(p + ".birdsEye"));
        commandBuilder.set("#BirdsEyeToggle #BirdsEyeLabel.TooltipTextSpans", Message.translation(p + ".birdsEyeTooltip"));
        commandBuilder.set("#BtnZoomIn.TooltipTextSpans", Message.translation(p + ".zoomInTooltip"));
        commandBuilder.set("#BtnZoomOut.TooltipTextSpans", Message.translation(p + ".zoomOutTooltip"));
        commandBuilder.set("#BtnPanZm.TooltipTextSpans", Message.translation(p + ".panZmTooltip"));
        commandBuilder.set("#BtnPanXm.TooltipTextSpans", Message.translation(p + ".panXmTooltip"));
        commandBuilder.set("#BtnPanXp.TooltipTextSpans", Message.translation(p + ".panXpTooltip"));
        commandBuilder.set("#BtnPanZp.TooltipTextSpans", Message.translation(p + ".panZpTooltip"));
        commandBuilder.set("#BtnZm.TooltipTextSpans", Message.translation(p + ".moveZmTooltip"));
        commandBuilder.set("#BtnXm.TooltipTextSpans", Message.translation(p + ".moveXmTooltip"));
        commandBuilder.set("#BtnXp.TooltipTextSpans", Message.translation(p + ".moveXpTooltip"));
        commandBuilder.set("#BtnZp.TooltipTextSpans", Message.translation(p + ".moveZpTooltip"));
        commandBuilder.set("#BtnYm.TooltipTextSpans", Message.translation(p + ".moveYmTooltip"));
        commandBuilder.set("#BtnYp.TooltipTextSpans", Message.translation(p + ".moveYpTooltip"));
        commandBuilder.set("#BtnRotate.TooltipTextSpans", Message.translation(p + ".rotateTooltip"));
        commandBuilder.set("#SnapToLocationButton.TextSpans", Message.translation(p + ".snapToLocation"));
        commandBuilder.set("#SnapToLocationButton.TooltipTextSpans", Message.translation(p + ".snapToLocationTooltip"));
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.append("Aetherhaven/PlotPlacementPage.ui");
        applySharedPlotPlacementLocalization(commandBuilder);
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
        commandBuilder.set("#Summary.TextSpans", Message.translation(MSG_PLOT_UI + ".summary").param("building", name));
        commandBuilder.set(
            "#Details.TextSpans",
            Message.translation(MSG_PLOT_UI + ".detailsBlock")
                .param("sx", sign.x)
                .param("sy", sign.y)
                .param("sz", sign.z)
                .param("ox", prefabO.x)
                .param("oy", prefabO.y)
                .param("oz", prefabO.z)
                .param("step", session.getRotationSteps())
        );
        PlotPlacementHotkeyTips.appendTipsRows(commandBuilder, "#TipsRows");

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
            commandBuilder.set("#PlaceButton.TextSpans", Message.translation(MSG_PLOT_UI + ".placeMove"));
        } else {
            commandBuilder.set("#PlaceButton.TextSpans", Message.translation(MSG_PLOT_UI + ".place"));
        }
        // Keep the building picker visible for move tokens; hide it only for shelf Move tab (no token in inventory).
        boolean showPlotTypeDropdown =
            plugin != null && inv != null && (!moveMode || session.isMoveViaToken());
        if (showPlotTypeDropdown) {
            List<PlotTokenPlacementOption> options = PlotTokenInventory.listPlacementOptions(plugin, inv);
            if (session.isMoveViaToken() && session.getMovePlotId() != null) {
                selectedDropdownValue = PlotTokenPlacementOption.MOVE_VALUE_PREFIX + session.getMovePlotId();
            } else if (!options.isEmpty() && selectedDropdownValue == null) {
                selectedDropdownValue = options.get(0).getDropdownValue();
            } else if (
                selectedDropdownValue != null
                    && !options.isEmpty()
                    && options.stream().noneMatch(o -> o.getDropdownValue().equals(selectedDropdownValue))
            ) {
                selectedDropdownValue = options.get(0).getDropdownValue();
            }
            List<DropdownEntryInfo> entries = collectPlotDropdownEntries(plugin, inv, playerRef.getLanguage());
            showPlotTypeDropdown = !entries.isEmpty();
            commandBuilder.set("#PlotTypeDropdown.Entries", entries);
            commandBuilder.set("#PlotTypeDropdown.Visible", showPlotTypeDropdown);
            commandBuilder.set("#PlotTypeLabel.Visible", showPlotTypeDropdown);
            if (showPlotTypeDropdown && selectedDropdownValue != null) {
                commandBuilder.set("#PlotTypeDropdown.Value", selectedDropdownValue);
            }
        } else {
            commandBuilder.set("#PlotTypeDropdown.Visible", false);
            commandBuilder.set("#PlotTypeLabel.Visible", false);
        }

        bind(eventBuilder, "#SnapToLocationButton", "SnapToLocation");
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
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        super.onDismiss(ref, store);
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
        }
    }

    private static void bind(@Nonnull UIEventBuilder eventBuilder, @Nonnull String selector, @Nonnull String action) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, selector, new EventData().append("Action", action), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.constructionId != null && !data.constructionId.isBlank()) {
            String id = data.constructionId.trim();
            selectedDropdownValue = id;
            AetherhavenPlugin plugin = AetherhavenPlugin.get();
            Player player = store.getComponent(ref, Player.getComponentType());
            CombinedItemContainer inv =
                player != null ? InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING) : null;
            if (plugin == null || inv == null) {
                scheduleRebuild(ref, store);
                return;
            }
            PlotTokenPlacementOption chosen = findPlacementOption(plugin, inv, id);
            if (chosen == null) {
                sendError(store, ref, "You need the matching plot token in your inventory for that building.");
                scheduleRebuild(ref, store);
                return;
            }
            World world = store.getExternalData().getWorld();
            float yaw = PlotPlacementNudgeUtil.getPlayerYawRadians(ref, store);
            if (chosen.isMovePlot()) {
                PlotPlacementSession moveSession =
                    PlotPlacementSessionFactory.createFromOption(world, session.getAnchor(), chosen, plugin, yaw);
                if (moveSession == null) {
                    sendError(store, ref, "That building can no longer be moved.");
                    scheduleRebuild(ref, store);
                    return;
                }
                session.setConstructionId(moveSession.getConstructionId());
                session.setMovePlotId(moveSession.getMovePlotId());
                session.setMoveViaToken(true);
                session.setRotationSteps(moveSession.getRotationSteps());
            } else {
                session.setConstructionId(chosen.getConstructionId());
                session.setMovePlotId(null);
                session.setMoveViaToken(false);
                session.setRotationSteps(
                    PlotPlacementSessionFactory.initialRotationSteps(plugin, chosen.getConstructionId(), yaw)
                );
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
            case "SnapToLocation" -> {
                PlotPlacementSnapUtil.snapSessionToPlayer(session, ref, store);
                if (birdsEyeEnabled) {
                    session.clearBirdsEyeSnapshot();
                    captureBirdsEyeSnapshot(ref, store);
                    scheduleApplyCameraAndRebuild(ref, store);
                } else {
                    scheduleRebuild(ref, store);
                }
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
            PlotFootprintRecord fp = PlotFootprintUtil.computeFootprint(prefabOrigin, session.getPrefabYaw(), buf, def);
            session.setBirdsEyeSnapshot(
                (fp.getMinX() + fp.getMaxX() + 1) / 2.0,
                (fp.getMinY() + fp.getMaxY() + 1) / 2.0,
                (fp.getMinZ() + fp.getMaxZ() + 1) / 2.0
            );
        } finally {
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
            boolean ok = PlotBuildingRelocation.tryCommit(ref, store, session, uc.getUuid());
            if (ok && session.isMoveViaToken()) {
                UUID movePlotId = session.getMovePlotId();
                Player playerMove = store.getComponent(ref, Player.getComponentType());
                CombinedItemContainer invMove =
                    playerMove != null ? InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING) : null;
                if (movePlotId != null && invMove != null) {
                    PlotTokenInventory.consumeMoveToken(invMove, movePlotId);
                }
            }
            return ok;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town =
            TownPlayerResolution.resolveTownForPlotPlacement(tm, world.getName(), uc.getUuid(), session.getAnchor());
        if (town == null) {
            sendError(store, ref, "You need a town (place a charter) first.");
            return false;
        }
        if (!town.playerCanPlacePlots(uc.getUuid())) {
            sendError(store, ref, "You do not have permission to place buildings for this town.");
            return false;
        }
        ConstructionDefinition def = plugin.getConstructionCatalog().get(session.getConstructionId());
        if (def == null) {
            sendError(store, ref, "Unknown construction: " + session.getConstructionId());
            return false;
        }
        if (def.isLegacyPlotSupport()) {
            sendError(store, ref, "This building now comes from the marketplace.");
            return false;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        CombinedItemContainer inv =
            player != null ? InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING) : null;
        if (inv == null || !PlotTokenInventory.hasPlotToken(inv, def)) {
            sendError(store, ref, "You need the plot token for this building in your inventory to place it.");
            return false;
        }
        Vector3i previewAnchor = session.getAnchor();
        Path prefabPathEarly = resolvePrefabAssetPath(def.getPrefabPath());
        PlotPlacementHeights.ResolvedPlacement resolved;
        if (prefabPathEarly != null) {
            IPrefabBuffer groundBuf = PrefabBufferUtil.getCached(prefabPathEarly);
            try {
                resolved =
                    PlotPlacementHeights.resolve(
                        world, previewAnchor, def, session.getPrefabYaw(), groundBuf
                    );
            } finally {
            }
        } else {
            Vector3i buildingAnchor = def.resolvePrefabAnchorWorld(previewAnchor, session.getPrefabYaw());
            resolved = new PlotPlacementHeights.ResolvedPlacement(previewAnchor, buildingAnchor);
        }
        Vector3i placedSignPos = resolved.signCell();
        Vector3i buildingAnchor = resolved.buildingPrefabAnchor();
        String err =
            prefabPathEarly != null
                ? PlotPlacementValidator.validateWithResolvedHeights(
                    world,
                    tm,
                    town,
                    uc.getUuid(),
                    previewAnchor,
                    placedSignPos,
                    buildingAnchor,
                    session.getPrefabYaw(),
                    def,
                    plugin,
                    session.getMovePlotId()
                )
                : PlotPlacementValidator.validate(
                    world,
                    tm,
                    town,
                    uc.getUuid(),
                    previewAnchor,
                    session.getPrefabYaw(),
                    def,
                    plugin,
                    session.getMovePlotId()
                );
        if (err != null) {
            sendError(store, ref, err);
            return false;
        }
        if (!tm.isInsideTerritory(town, placedSignPos.x, placedSignPos.z)) {
            sendError(store, ref, "Plot sign position is outside your town territory.");
            return false;
        }
        if (!isReplaceableSignCell(world, placedSignPos.x, placedSignPos.y, placedSignPos.z)) {
            sendError(store, ref, "Could not place plot sign (blocked or invalid spot).");
            return false;
        }
        if (!PlotTokenInventory.consumePlotToken(inv, def)) {
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
            inv.addItemStack(PlotTokenInventory.createTokenStack(session.getConstructionId(), 1, null));
            sendError(store, ref, "Could not place plot sign (blocked or invalid spot).");
            return false;
        }
        Path prefabPath = prefabPathEarly != null ? prefabPathEarly : resolvePrefabAssetPath(def.getPrefabPath());
        if (prefabPath != null) {
            IPrefabBuffer buf = PrefabBufferUtil.getCached(prefabPath);
            try {
                Vector3i prefabOrigin = buildingAnchor;
                PlotFootprintRecord fp = PlotFootprintUtil.computeFootprint(prefabOrigin, session.getPrefabYaw(), buf, def);
                PlotInstance inst =
                    new PlotInstance(
                        plotId,
                        session.getConstructionId(),
                        PlotInstanceState.BLUEPRINTING,
                        fp,
                        placedSignPos.x,
                        placedSignPos.y,
                        placedSignPos.z,
                        System.currentTimeMillis()
                    );
                inst.setPrefabWorldPlacement(prefabOrigin.x, prefabOrigin.y, prefabOrigin.z, session.getPrefabYaw());
                town.addPlotInstance(inst);
            } finally {
            }
        } else {
            PlotFootprintRecord mini =
                new PlotFootprintRecord(
                    placedSignPos.x,
                    placedSignPos.y,
                    placedSignPos.z,
                    placedSignPos.x,
                    placedSignPos.y,
                    placedSignPos.z
                );
            PlotInstance miniPlot =
                new PlotInstance(
                    plotId,
                    session.getConstructionId(),
                    PlotInstanceState.BLUEPRINTING,
                    mini,
                    placedSignPos.x,
                    placedSignPos.y,
                    placedSignPos.z,
                    System.currentTimeMillis()
                );
            miniPlot.setPrefabWorldPlacement(
                buildingAnchor.x,
                buildingAnchor.y,
                buildingAnchor.z,
                session.getPrefabYaw()
            );
            town.addPlotInstance(miniPlot);
        }
        QuestProgressionService.onConstructionPlaced(plugin, town, session.getConstructionId());
        tm.updateTown(town);
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.translation("aetherhaven_world_debug.aetherhaven.plotSign.placed"));
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
                PlayerRef prCancel = store.getComponent(ref, PlayerRef.getComponentType());
                if (prCancel != null) {
                    clearClientPrefabPreview(prCancel);
                }
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
                    PlayerRef prDone = store.getComponent(ref, PlayerRef.getComponentType());
                    if (prDone != null) {
                        clearClientPrefabPreview(prDone);
                    }
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
            if (pr != null) {
                clearClientPrefabPreview(pr);
                PlotPlacementWireframeOverlay.clearFor(pr);
            }
            return;
        }
        IPrefabBuffer buf = resolvePrefabBuffer(def.getPrefabPath());
        if (buf == null) {
            if (pr != null) {
                clearClientPrefabPreview(pr);
                PlotPlacementWireframeOverlay.clearFor(pr);
            }
            return;
        }
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            if (pr != null) {
                clearClientPrefabPreview(pr);
                PlotPlacementWireframeOverlay.clearFor(pr);
            }
            return;
        }
        World world = store.getExternalData().getWorld();
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        TownRecord town =
            TownPlayerResolution.resolveTownForPlotPlacement(tm, world.getName(), uc.getUuid(), session.getAnchor());
        String placementErr;
        if (town == null) {
            placementErr = "You need a town (place a charter) first.";
        } else if (!town.playerCanPlacePlots(uc.getUuid())) {
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
        Vector3i prefabOrigin = def.resolvePrefabAnchorWorld(session.getAnchor(), session.getPrefabYaw());
                PlotFootprintRecord fp = PlotFootprintUtil.computeFootprint(prefabOrigin, session.getPrefabYaw(), buf, def);
        if (pr != null) {
            boolean placerNeedFull =
                !clientPrefabPreviewActive
                    || !session.getConstructionId().equals(lastPreviewConstructionId)
                    || session.getRotationSteps() != lastPreviewRotationSteps;
            syncClientPrefabPreview(pr, def, prefabOrigin);
            PlotPlacementPreviewSync.syncSpectators(world, uc.getUuid(), session, def, prefabOrigin, placerNeedFull);
            PlotPlacementWireframeOverlay.send(pr, fp, placementValid, town);
            PlotAssemblyPreviewSystem.repaintFrontierAfterExternalDebugClear(ref, store);
        }
    }

    private void syncClientPrefabPreview(
        @Nonnull PlayerRef pr,
        @Nonnull ConstructionDefinition def,
        @Nonnull Vector3i prefabOriginWorld
    ) {
        boolean needFull =
            !clientPrefabPreviewActive
                || !session.getConstructionId().equals(lastPreviewConstructionId)
                || session.getRotationSteps() != lastPreviewRotationSteps;
        if (needFull) {
            boolean ok =
                PlotPlacementClientPrefabPreview.sendFull(
                    pr,
                    def.getPrefabPath(),
                    session.getRotationSteps(),
                    prefabOriginWorld,
                    session.getPrefabYaw(),
                    session
                );
            if (!ok) {
                clearClientPrefabPreview(pr);
                return;
            }
            clientPrefabPreviewActive = true;
            lastPreviewConstructionId = session.getConstructionId();
            lastPreviewRotationSteps = session.getRotationSteps();
            lastPreviewOriginFloored =
                PlotPlacementClientPrefabPreview.flooredClientPreviewOrigin(
                    prefabOriginWorld,
                    session,
                    session.getPrefabYaw()
                );
            return;
        }
        PlotPlacementClientPrefabPreview.Payload payload = session.getClientPrefabPreviewPayload();
        Vector3i floored =
            PlotPlacementClientPrefabPreview.flooredClientPreviewOrigin(
                prefabOriginWorld,
                session,
                session.getPrefabYaw()
            );
        if (lastPreviewOriginFloored != null && lastPreviewOriginFloored.equals(floored)) {
            return;
        }
        if (payload != null) {
            PlotPlacementClientPrefabPreview.sendPositionOnly(
                pr,
                prefabOriginWorld,
                payload,
                session.getPrefabYaw(),
                session
            );
        }
        lastPreviewOriginFloored = floored;
    }

    private void clearClientPrefabPreview(@Nonnull PlayerRef pr) {
        PlotPlacementPreviewSync.hideSpectators(session.getWorld(), pr.getUuid(), session);
        Ref<EntityStore> entityRef = pr.getReference();
        if (entityRef != null) {
            PlotPlacementClientPrefabPreview.clearWorldPreview(entityRef.getStore(), session);
        }
        if (clientPrefabPreviewActive) {
            PlotPlacementClientPrefabPreview.hide(pr);
        }
        clientPrefabPreviewActive = false;
        lastPreviewConstructionId = null;
        lastPreviewRotationSteps = -1;
        lastPreviewOriginFloored = null;
        PlotPlacementClientPrefabPreview.clearSessionCache(session);
    }

    /**
     * Re-sends footprint wireframes after {@code ClearDebugShapes} from another subsystem (e.g. assembly frontier cubes).
     */
    public void refreshFootprintOverlayAfterDebugClear(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        refreshPreview(ref, store);
    }

    private void sendError(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull String text) {
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.raw(text));
        }
    }

    private static boolean isReplaceableSignCell(@Nonnull World world, int x, int y, int z) {
        BlockType t = ChunkSectionBlockUtil.blockType(world, x, y, z);
        return t == null || t.getMaterial() == BlockMaterial.Empty;
    }

    @Nonnull
    private static List<DropdownEntryInfo> collectPlotDropdownEntries(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull CombinedItemContainer inv,
        @Nullable String language
    ) {
        ObjectArrayList<DropdownEntryInfo> entries = new ObjectArrayList<>();
        String moveSuffix = resolveMoveDropdownSuffix(language);
        for (PlotTokenPlacementOption option : PlotTokenInventory.listPlacementOptions(plugin, inv)) {
            ConstructionDefinition d = plugin.getConstructionCatalog().get(option.getConstructionId());
            if (d == null && !option.isMovePlot()) {
                continue;
            }
            String label = d != null ? d.getDisplayName() : option.getConstructionId();
            if (option.isMovePlot()) {
                label = label + moveSuffix;
            }
            entries.add(new DropdownEntryInfo(LocalizableString.fromString(label), option.getDropdownValue()));
        }
        return entries;
    }

    @Nonnull
    private static String resolveMoveDropdownSuffix(@Nullable String language) {
        String lang = language != null && !language.isBlank() ? language : "en-US";
        I18nModule i18n = I18nModule.get();
        String text =
            i18n != null
                ? i18n.getMessage(lang, "aetherhaven_plot_move.aetherhaven.ui.plotplacement.moveDropdownSuffix")
                : null;
        if (text == null || text.isBlank()) {
            return " (move)";
        }
        return text;
    }

    @Nullable
    private static PlotTokenPlacementOption findPlacementOption(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull CombinedItemContainer inv,
        @Nonnull String dropdownValue
    ) {
        for (PlotTokenPlacementOption option : PlotTokenInventory.listPlacementOptions(plugin, inv)) {
            if (option.getDropdownValue().equals(dropdownValue)) {
                return option;
            }
        }
        return null;
    }

    @Nullable
    private static String resolveDropdownValueFromSession(@Nonnull PlotPlacementSession session) {
        if (session.isMoveMode() && session.getMovePlotId() != null) {
            return PlotTokenPlacementOption.MOVE_VALUE_PREFIX + session.getMovePlotId();
        }
        return session.getConstructionId();
    }

    @Nullable
    public static String defaultConstructionFromInventory(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (plugin == null || player == null) {
            return null;
        }
        CombinedItemContainer inv = InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
        PlotTokenPlacementOption option = PlotTokenInventory.defaultPlacementOption(plugin, inv);
        return option != null ? option.getConstructionId() : null;
    }

    @Nullable
    private static Path resolvePrefabAssetPath(@Nullable String key) {
        return PrefabResolveUtil.resolvePrefabPath(key);
    }

    @Nullable
    private static IPrefabBuffer resolvePrefabBuffer(@Nullable String key) {
        return PrefabResolveUtil.resolvePrefabBuffer(key);
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
