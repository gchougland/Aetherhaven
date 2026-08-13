package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.prop.PropCatalog;
import com.hexvane.aetherhaven.prop.PropDefinition;
import com.hexvane.aetherhaven.prop.PropPlacementCommit;
import com.hexvane.aetherhaven.prop.PropPlacementSession;
import com.hexvane.aetherhaven.prop.PropPlacementSessions;
import com.hexvane.aetherhaven.prop.PropPlacementValidator;
import com.hexvane.aetherhaven.prop.PropPlacementWireframeOverlay;
import com.hexvane.aetherhaven.prop.PropPrefabOps;
import com.hexvane.aetherhaven.placement.PlotPlacementClientPrefabPreview;
import com.hexvane.aetherhaven.placement.PlotPlacementNudgeUtil;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Simplified plot-placement style UI for props: nudge, rotate, place, cancel. */
public final class PropPlacementPage extends AetherhavenInteractiveCustomUIPage<PropPlacementPage.PageData> {
    private static final String MSG_UI = "aetherhaven_props.aetherhaven.ui.propplacement";

    @Nonnull
    private final PropPlacementSession session;

    public PropPlacementPage(@Nonnull PlayerRef playerRef, @Nonnull PropPlacementSession session) {
        super(playerRef, CustomPageLifetime.CanDismissOrCloseThroughInteraction, PageData.CODEC);
        this.session = session;
    }

    @Override
    public void build(
        @Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder commandBuilder, @Nonnull UIEventBuilder eventBuilder, @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.append("Aetherhaven/PropPlacementPage.ui");
        commandBuilder.set("#PropPlacementTitle.TextSpans", Message.translation(MSG_UI + ".title"));
        commandBuilder.set("#PlaceButton.TextSpans", Message.translation(MSG_UI + ".place"));
        commandBuilder.set("#CancelButton.TextSpans", Message.translation(MSG_UI + ".cancel"));

        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        PropDefinition def = plugin != null ? plugin.getPropCatalog().get(session.getPropId()) : null;
        String name = def != null ? def.getDisplayName() : session.getPropId();
        Vector3i anchor = session.getAnchor();
        commandBuilder.set("#Summary.TextSpans", Message.translation(MSG_UI + ".summary").param("prop", name));
        commandBuilder.set(
            "#Details.TextSpans",
            Message.translation(MSG_UI + ".details")
                .param("x", anchor.x)
                .param("y", anchor.y)
                .param("z", anchor.z)
                .param("step", session.getRotationSteps())
        );

        bind(eventBuilder, "#BtnNudgeXm", "NudgeXm");
        bind(eventBuilder, "#BtnNudgeXp", "NudgeXp");
        bind(eventBuilder, "#BtnNudgeZm", "NudgeZm");
        bind(eventBuilder, "#BtnNudgeZp", "NudgeZp");
        bind(eventBuilder, "#BtnYm", "NudgeYm");
        bind(eventBuilder, "#BtnYp", "NudgeYp");
        bind(eventBuilder, "#BtnRotate", "Rotate");
        bind(eventBuilder, "#PlaceButton", "Place");
        bind(eventBuilder, "#CancelButton", "Cancel");

        scheduleRefreshPreview(ref, store);
    }

    private static void bind(@Nonnull UIEventBuilder eventBuilder, @Nonnull String selector, @Nonnull String action) {
        eventBuilder.addEventBinding(CustomUIEventBindingType.Activating, selector, new EventData().append("Action", action), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        if (data.action == null) {
            return;
        }
        float yawRad = PlotPlacementNudgeUtil.getPlayerYawRadians(ref, store);
        switch (data.action) {
            case "NudgeXm" -> {
                int[] step = PlotPlacementNudgeUtil.horizontalStep(false, yawRad, PlotPlacementNudgeUtil.Horizontal.NEG_X);
                session.nudge(step[0], 0, step[1]);
            }
            case "NudgeXp" -> {
                int[] step = PlotPlacementNudgeUtil.horizontalStep(false, yawRad, PlotPlacementNudgeUtil.Horizontal.POS_X);
                session.nudge(step[0], 0, step[1]);
            }
            case "NudgeZm" -> {
                int[] step = PlotPlacementNudgeUtil.horizontalStep(false, yawRad, PlotPlacementNudgeUtil.Horizontal.NEG_Z);
                session.nudge(step[0], 0, step[1]);
            }
            case "NudgeZp" -> {
                int[] step = PlotPlacementNudgeUtil.horizontalStep(false, yawRad, PlotPlacementNudgeUtil.Horizontal.POS_Z);
                session.nudge(step[0], 0, step[1]);
            }
            case "NudgeYm" -> session.nudge(0, -1, 0);
            case "NudgeYp" -> session.nudge(0, 1, 0);
            case "Rotate" -> session.rotateClockwise90();
            case "Place" -> {
                schedulePlace(ref, store);
                return;
            }
            case "Cancel" -> {
                scheduleCancel(ref, store);
                return;
            }
            default -> {
                return;
            }
        }
        scheduleRebuild(ref, store);
    }

    private void scheduleRebuild(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        world.execute(() -> {
            if (!ref.isValid()) {
                return;
            }
            rebuild();
        });
    }

    private void scheduleCancel(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        world.execute(() -> {
            PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
            PlayerRef clearTarget = pr != null ? pr : playerRef;
            PropPlacementWireframeOverlay.clearFor(clearTarget);
            PlotPlacementClientPrefabPreview.hide(clearTarget);
            UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
            if (uc != null) {
                PropPlacementSessions.remove(uc.getUuid());
            }
            close();
        });
    }

    private void schedulePlace(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        world.execute(() -> {
            if (!ref.isValid()) {
                return;
            }
            if (tryPlace(ref, store)) {
                PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
                PlayerRef clearTarget = pr != null ? pr : playerRef;
                PropPlacementWireframeOverlay.clearFor(clearTarget);
                PlotPlacementClientPrefabPreview.hide(clearTarget);
                UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
                if (uc != null) {
                    PropPlacementSessions.remove(uc.getUuid());
                }
                close();
            } else {
                rebuild();
            }
        });
    }

    private boolean tryPlace(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin == null) {
            return false;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        CombinedItemContainer inv =
            player != null ? InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING) : null;
        if (inv == null) {
            sendError(store, ref, MSG_UI + ".error.notHoldingProp");
            return false;
        }
        World world = store.getExternalData().getWorld();
        String err =
            PropPlacementValidator.validate(
                world, plugin.getPropCatalog(), session.getPropId(), session.getAnchor(), session.getYaw()
            );
        if (err != null) {
            sendError(store, ref, MSG_UI + ".error." + err);
            return false;
        }
        boolean placed =
            PropPlacementCommit.commit(world, plugin, inv, session.getPropId(), session.getAnchor(), session.getYaw());
        if (!placed) {
            sendError(store, ref, MSG_UI + ".error.blocked");
        }
        return placed;
    }

    private void refreshPreview(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (plugin == null || pr == null) {
            return;
        }
        PropCatalog catalog = plugin.getPropCatalog();
        World world = store.getExternalData().getWorld();
        IPrefabBuffer buffer =
            PropPlacementValidator.resolveValidatedBuffer(world, catalog, session.getPropId(), session.getAnchor(), session.getYaw());
        if (buffer == null) {
            PropPlacementWireframeOverlay.clearFor(pr);
            PlotPlacementClientPrefabPreview.hide(pr);
            return;
        }
        String err = PropPlacementValidator.validate(world, catalog, session.getPropId(), session.getAnchor(), session.getYaw());
        PlotFootprintRecord fp = PropPrefabOps.placementOutlineFootprint(session.getAnchor(), session.getYaw(), buffer);
        PropPlacementWireframeOverlay.send(pr, fp, err == null);
        PropDefinition def = catalog.get(session.getPropId());
        if (def != null) {
            boolean ghostOk =
                PlotPlacementClientPrefabPreview.sendFullStandalone(
                    pr,
                    def.getPrefabPath(),
                    session.getRotationSteps(),
                    session.getAnchor(),
                    session.getYaw()
                );
            if (!ghostOk) {
                PlotPlacementClientPrefabPreview.hide(pr);
            }
        }
    }

    private void scheduleRefreshPreview(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        world.execute(() -> {
            if (!ref.isValid()) {
                return;
            }
            refreshPreview(ref, store);
        });
    }

    /** Re-send outline after another system issued {@code ClearDebugShapes}. */
    public void refreshFootprintOverlayAfterDebugClear(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        refreshPreview(ref, store);
    }

    private void sendError(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref, @Nonnull String messageKey) {
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr != null) {
            pr.sendMessage(Message.translation(messageKey));
        }
    }

    public static final class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
            .append(new KeyedCodec<>("Action", Codec.STRING), (d, a) -> d.action = a, d -> d.action)
            .add()
            .build();

        @Nullable
        private String action;
    }
}
