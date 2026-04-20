package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.placement.CharterRelocationService;
import com.hexvane.aetherhaven.placement.CharterRelocationSession;
import com.hexvane.aetherhaven.placement.CharterRelocationSessions;
import com.hexvane.aetherhaven.placement.PlotPlacementCameraUtil;
import com.hexvane.aetherhaven.placement.PlotPreviewSpawner;
import com.hexvane.aetherhaven.placement.PlotPlacementWireframeOverlay;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Positions a new charter block; validates that all town buildings remain inside the territory centered on the new
 * charter.
 */
public final class CharterRelocationPage extends InteractiveCustomUIPage<CharterRelocationPage.PageData> {
    @Nonnull
    private final CharterRelocationSession session;

    private boolean birdsEyeEnabled;
    private float birdsEyeDistance = PlotPlacementCameraUtil.DEFAULT_DISTANCE;
    private int smoothPanGeneration;

    public CharterRelocationPage(@Nonnull PlayerRef playerRef, @Nonnull CharterRelocationSession session) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.session = session;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull UICommandBuilder commandBuilder,
        @Nonnull UIEventBuilder eventBuilder,
        @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.append("Aetherhaven/PlotPlacementPage.ui");
        Vector3i a = session.getAnchor();
        commandBuilder.set(
            "#Info.TextSpans",
            Message.join(
                Message.translation("server.aetherhaven.ui.charterrelocation.title"),
                Message.raw("\n\n"),
                Message.translation("server.aetherhaven.ui.charterrelocation.info"),
                Message.raw("\n\n" + a.x + ", " + a.y + ", " + a.z + "\n"),
                Message.raw("Yaw step " + session.getRotationSteps() + " / 4 (90° each)"),
                Message.raw("\n\n"),
                Message.translation("server.aetherhaven.ui.plotplacement.cameraHint")
            )
        );

        commandBuilder.set("#PlotTypeDropdown.Visible", false);
        commandBuilder.set("#PlotTypeLabel.Visible", false);
        commandBuilder.set("#BtnRotate.Visible", true);

        commandBuilder.set("#BirdsEyeToggle #CheckBox.Value", birdsEyeEnabled);
        commandBuilder.set("#BirdsEyeZoomRow.Visible", birdsEyeEnabled);
        commandBuilder.set("#BirdsEyePanLabel.Visible", birdsEyeEnabled);
        commandBuilder.set("#BirdsEyePanRow.Visible", birdsEyeEnabled);
        commandBuilder.set(
            "#BirdsEyeDistanceValue.TextSpans",
            Message.raw(String.format("%.0f", birdsEyeDistance))
        );
        commandBuilder.set("#BtnZoomOut.Disabled", birdsEyeDistance <= PlotPlacementCameraUtil.MIN_DISTANCE + 0.01f);
        commandBuilder.set("#BtnZoomIn.Disabled", birdsEyeDistance >= PlotPlacementCameraUtil.MAX_DISTANCE - 0.01f);

        commandBuilder.set(
            "#PlaceButton.TextSpans",
            Message.translation("server.aetherhaven.ui.charterrelocation.place")
        );

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
        bind(eventBuilder, "#PlaceButton", "Place");
        bind(eventBuilder, "#CancelButton", "Cancel");

        eventBuilder.addEventBinding(
            CustomUIEventBindingType.ValueChanged,
            "#BirdsEyeToggle #CheckBox",
            EventData.of("@BirdsEye", "#BirdsEyeToggle #CheckBox.Value"),
            false
        );

        scheduleRefreshPreview(ref, store);
    }

    private static void bind(@Nonnull UIEventBuilder eventBuilder, @Nonnull String selector, @Nonnull String action) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, selector, new EventData().append("Action", action), false);
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

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
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
        if (data.action == null) {
            return;
        }
        switch (data.action) {
            case "MoveXm" -> session.nudge(-1, 0, 0);
            case "MoveXp" -> session.nudge(1, 0, 0);
            case "MoveZm" -> session.nudge(0, 0, -1);
            case "MoveZp" -> session.nudge(0, 0, 1);
            case "MoveYm" -> session.nudge(0, -1, 0);
            case "MoveYp" -> session.nudge(0, 1, 0);
            case "Rotate" -> session.rotateClockwise90();
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
            case "ZoomIn" -> {
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
            case "Cancel" -> {
                scheduleCancel(ref, store);
                return;
            }
            case "Place" -> {
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

    private void captureBirdsEyeSnapshot(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        Vector3i anchor = session.getAnchor();
        session.setBirdsEyeSnapshot(anchor.x + 0.5, anchor.y + 0.5, anchor.z + 0.5);
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
        double fx = session.getBirdsEyeSnapshotX();
        double fy = session.getBirdsEyeSnapshotY();
        double fz = session.getBirdsEyeSnapshotZ();
        fx += session.getBirdsEyePanX();
        fz += session.getBirdsEyePanZ();
        PlotPlacementCameraUtil.applyBirdsEye(pr, birdsEyeDistance, p.x, p.y, p.z, fx, fy, fz);
    }

    private void scheduleRefreshPreview(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        world.execute(
            () -> {
                if (!ref.isValid()) {
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
                PlotPreviewSpawner.clear(store, session.getPreviewEntityRefs());
                PlotPlacementWireframeOverlay.clearFor(prCancel);
                UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
                if (uc != null) {
                    CharterRelocationSessions.remove(uc.getUuid());
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
                UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
                if (uc == null) {
                    return;
                }
                if (CharterRelocationService.tryCommit(ref, store, session, uc.getUuid())) {
                    PlotPreviewSpawner.clear(store, session.getPreviewEntityRefs());
                    PlayerRef prDone = store.getComponent(ref, PlayerRef.getComponentType());
                    PlotPlacementWireframeOverlay.clearFor(prDone);
                    CharterRelocationSessions.remove(uc.getUuid());
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
            PlotPreviewSpawner.clear(store, session.getPreviewEntityRefs());
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
        TownRecord town = tm.getTown(session.getTownId());
        Vector3i anchor = session.getAnchor();
        PlotFootprintRecord fp = new PlotFootprintRecord(anchor.x, anchor.y, anchor.z, anchor.x, anchor.y, anchor.z);

        String placementErr = null;
        if (town == null) {
            placementErr = "Town not found.";
        } else if (!town.getOwnerUuid().equals(uc.getUuid())) {
            placementErr = "Only the town owner can move the charter.";
        } else if (!tm.allPlotFootprintsFitTerritoryWithCharterAt(town, anchor.x, anchor.z)) {
            placementErr =
                "Territory from this spot would not cover all your buildings. Move closer to your town.";
        } else if (!isReplaceable(world, anchor.x, anchor.y, anchor.z)) {
            placementErr = "That cell is not clear for the charter.";
        } else {
            int ox = town.getCharterX();
            int oy = town.getCharterY();
            int oz = town.getCharterZ();
            if (anchor.x == ox && anchor.y == oy && anchor.z == oz) {
                placementErr = "Choose a different block than the current charter.";
            }
        }
        boolean placementValid = placementErr == null;
        PlotPreviewSpawner.rebuildCharterBlockPreview(
            store,
            anchor.x,
            anchor.y,
            anchor.z,
            session.getBlockHorizontalRotation(),
            session.getPreviewEntityRefs()
        );
        if (pr != null) {
            PlotPlacementWireframeOverlay.send(pr, fp, placementValid, town);
        }
    }

    private static boolean isReplaceable(@Nonnull World world, int x, int y, int z) {
        BlockType t = world.getBlockType(x, y, z);
        return t == null || t.getMaterial() == BlockMaterial.Empty;
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, a) -> d.action = a, d -> d.action)
            .add()
            .append(new KeyedCodec<>("@BirdsEye", Codec.BOOLEAN), (d, v) -> d.birdsEye = v, d -> d.birdsEye)
            .add()
            .build();

        @Nullable
        private String action;
        @Nullable
        private Boolean birdsEye;
    }
}
