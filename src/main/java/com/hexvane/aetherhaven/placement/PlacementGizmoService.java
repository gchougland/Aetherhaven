package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.prop.PropItemMetadata;
import com.hexvane.aetherhaven.prop.PropPlacementSession;
import com.hexvane.aetherhaven.prop.PropPlacementSessions;
import com.hexvane.aetherhaven.prop.PropShopItemIds;
import com.hexvane.aetherhaven.prop.PropVirtualItemRegistry;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolsEnabledTools;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.protocol.packets.player.PointToolMove;
import com.hypixel.hytale.protocol.packets.player.PointToolMultiMove;
import com.hypixel.hytale.protocol.packets.player.PointToolRotate;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.buildertool.config.BuilderTool;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Adventure scoped Point tool gizmo for plot, charter, and prop placement previews. */
public final class PlacementGizmoService {
    private static final String POINT_TOOL_ID = "Point";
    private static final String MSG_PLOT = "aetherhaven_plot_move.aetherhaven.ui.plotplacement";
    private static final String MSG_PROP = "aetherhaven_props.aetherhaven.ui.propplacement";

    private PlacementGizmoService() {}

    public static boolean tryEnterPlotGizmoMode(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef
    ) {
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return false;
        }
        PlotPlacementSession session = PlotPlacementSessions.get(uc.getUuid());
        if (session == null) {
            return false;
        }
        if (!isPlotPlacementStaffInHand(ref, store)) {
            playerRef.sendMessage(Message.translation(MSG_PLOT + ".moveGizmoNeedStaff"));
            return false;
        }
        return enterGizmoMode(ref, store, playerRef, () -> {
            session.setGizmoMoveActive(true);
            PlacementGizmoPreviewRefresh.refreshPlot(ref, store, playerRef, session);
        });
    }

    public static boolean tryEnterCharterGizmoMode(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef
    ) {
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return false;
        }
        CharterRelocationSession session = CharterRelocationSessions.get(uc.getUuid());
        if (session == null) {
            return false;
        }
        if (!isPlotPlacementStaffInHand(ref, store)) {
            playerRef.sendMessage(Message.translation(MSG_PLOT + ".moveGizmoNeedStaff"));
            return false;
        }
        return enterGizmoMode(ref, store, playerRef, () -> {
            session.setGizmoMoveActive(true);
            PlacementGizmoPreviewRefresh.refreshCharter(ref, store, playerRef, session);
        });
    }

    public static boolean tryEnterPropGizmoMode(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef
    ) {
        UUIDComponent uc = store.getComponent(ref, UUIDComponent.getComponentType());
        if (uc == null) {
            return false;
        }
        PropPlacementSession session = PropPlacementSessions.get(uc.getUuid());
        if (session == null) {
            return false;
        }
        if (!isPropItemInHand(ref, store)) {
            playerRef.sendMessage(Message.translation(MSG_PROP + ".moveGizmoNeedProp"));
            return false;
        }
        return enterGizmoMode(ref, store, playerRef, () -> {
            session.setGizmoMoveActive(true);
            PlacementGizmoPreviewRefresh.refreshProp(ref, store, playerRef, session);
        });
    }

    public static void exitGizmoModeForPlayer(@Nonnull UUID playerUuid, @Nonnull PlayerRef playerRef) {
        // Apply pending drag to session anchors only. Never remove/spawn preview entities here: this is
        // often called from interaction ticks where Store forbids removeEntity.
        applyPendingDragAnchorOnly(playerUuid);
        PlacementGizmoPivot.forget(playerUuid);
        PlacementGizmoPointClient.deactivate(playerRef);
        PlotPlacementSession plot = PlotPlacementSessions.get(playerUuid);
        if (plot != null && plot.isGizmoMoveActive()) {
            plot.setGizmoMoveActive(false);
            return;
        }
        CharterRelocationSession charter = CharterRelocationSessions.get(playerUuid);
        if (charter != null && charter.isGizmoMoveActive()) {
            charter.setGizmoMoveActive(false);
            return;
        }
        PropPlacementSession prop = PropPlacementSessions.get(playerUuid);
        if (prop != null && prop.isGizmoMoveActive()) {
            prop.setGizmoMoveActive(false);
        }
    }

    public static boolean isGizmoMoveActive(@Nonnull UUID playerUuid) {
        PlotPlacementSession plot = PlotPlacementSessions.get(playerUuid);
        if (plot != null && plot.isGizmoMoveActive()) {
            return true;
        }
        CharterRelocationSession charter = CharterRelocationSessions.get(playerUuid);
        if (charter != null && charter.isGizmoMoveActive()) {
            return true;
        }
        PropPlacementSession prop = PropPlacementSessions.get(playerUuid);
        return prop != null && prop.isGizmoMoveActive();
    }

    @Nullable
    public static World findGizmoWorld(@Nonnull UUID playerUuid) {
        PlotPlacementSession plot = PlotPlacementSessions.get(playerUuid);
        if (plot != null && plot.isGizmoMoveActive()) {
            return plot.getWorld();
        }
        CharterRelocationSession charter = CharterRelocationSessions.get(playerUuid);
        if (charter != null && charter.isGizmoMoveActive()) {
            return charter.getWorld();
        }
        PropPlacementSession prop = PropPlacementSessions.get(playerUuid);
        if (prop != null && prop.isGizmoMoveActive()) {
            return prop.getWorld();
        }
        return null;
    }

    public static void syncPointGizmo(
        @Nonnull PlayerRef playerRef,
        @Nonnull PlotFootprintRecord footprint,
        float yawRadians
    ) {
        UUID playerUuid = playerRef.getUuid();
        if (!isGizmoMoveActive(playerUuid) || PlacementGizmoPivot.isDragging(playerUuid)) {
            return;
        }
        Vector3d center = PlacementGizmoPivot.centerOf(footprint);
        PlacementGizmoPivot.remember(playerUuid, center);
        PlacementGizmoPointClient.updateDisplay(playerRef, center, yawRadians);
        PlacementGizmoPointClient.reselect(playerRef);
    }

    public static void commitDrag(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        UUID playerUuid = playerRef.getUuid();
        if (!PlacementGizmoPivot.isDragging(playerUuid)) {
            return;
        }
        Vector3d committed = PlacementGizmoPivot.dragCommittedCenter(playerUuid);
        Vector3d current = PlacementGizmoPivot.remembered(playerUuid);
        PlacementGizmoPivot.endDrag(playerUuid);
        int dx = 0;
        int dy = 0;
        int dz = 0;
        if (committed != null && current != null) {
            dx = (int) Math.round(current.x - committed.x);
            dy = (int) Math.round(current.y - committed.y);
            dz = (int) Math.round(current.z - committed.z);
        }
        final int fdx = dx;
        final int fdy = dy;
        final int fdz = dz;
        World world = store.getExternalData().getWorld();
        // Defer Store entity work: mouse release / UI can run while the Store is ticking.
        world.execute(
            () -> {
                if (!ref.isValid()) {
                    return;
                }
                Store<EntityStore> liveStore = ref.getStore();
                if (fdx != 0 || fdy != 0 || fdz != 0) {
                    shiftAnchorByDelta(playerUuid, ref, liveStore, playerRef, fdx, fdy, fdz);
                } else {
                    refreshActiveSession(playerUuid, ref, liveStore, playerRef);
                }
            }
        );
    }

    /** Writes the rounded drag delta into session anchors without touching Store entities. */
    private static void applyPendingDragAnchorOnly(@Nonnull UUID playerUuid) {
        if (!PlacementGizmoPivot.isDragging(playerUuid)) {
            return;
        }
        Vector3d committed = PlacementGizmoPivot.dragCommittedCenter(playerUuid);
        Vector3d current = PlacementGizmoPivot.remembered(playerUuid);
        PlacementGizmoPivot.endDrag(playerUuid);
        if (committed == null || current == null) {
            return;
        }
        int dx = (int) Math.round(current.x - committed.x);
        int dy = (int) Math.round(current.y - committed.y);
        int dz = (int) Math.round(current.z - committed.z);
        if (dx == 0 && dy == 0 && dz == 0) {
            return;
        }
        PlotPlacementSession plot = PlotPlacementSessions.get(playerUuid);
        if (plot != null && plot.isGizmoMoveActive()) {
            Vector3i anchor = plot.getAnchor();
            plot.setAnchor(new Vector3i(anchor.x + dx, anchor.y + dy, anchor.z + dz));
            return;
        }
        CharterRelocationSession charter = CharterRelocationSessions.get(playerUuid);
        if (charter != null && charter.isGizmoMoveActive()) {
            Vector3i anchor = charter.getAnchor();
            charter.setAnchor(new Vector3i(anchor.x + dx, anchor.y + dy, anchor.z + dz));
            return;
        }
        PropPlacementSession prop = PropPlacementSessions.get(playerUuid);
        if (prop != null && prop.isGizmoMoveActive()) {
            Vector3i anchor = prop.getAnchor();
            prop.setAnchor(new Vector3i(anchor.x + dx, anchor.y + dy, anchor.z + dz));
        }
    }

    public static boolean handlePointMove(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PointToolMove packet
    ) {
        return applyGizmoCenter(
            playerRef,
            ref,
            store,
            packet.pointId,
            new Vector3d(packet.newPosition.x(), packet.newPosition.y(), packet.newPosition.z())
        );
    }

    public static boolean handlePointMultiMove(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PointToolMultiMove packet
    ) {
        UUID playerUuid = playerRef.getUuid();
        String ownedId = PlacementGizmoPointClient.pointId(playerUuid);
        if (packet.pointIds == null) {
            return false;
        }
        boolean owns = false;
        for (String pointId : packet.pointIds) {
            if (ownedId.equals(pointId)) {
                owns = true;
                break;
            }
        }
        if (!owns || !hasPointToolInHand(ref, store)) {
            return false;
        }
        Vector3d base = PlacementGizmoPivot.remembered(playerUuid);
        if (base == null) {
            base = PlacementGizmoLivePreview.committedCenter(playerUuid);
        }
        if (base == null) {
            return false;
        }
        Vector3d after =
            new Vector3d(base).add(packet.moveDelta.x(), packet.moveDelta.y(), packet.moveDelta.z());
        return applyGizmoCenter(playerRef, ref, store, ownedId, after);
    }

    private static boolean applyGizmoCenter(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nullable String pointId,
        @Nonnull Vector3d after
    ) {
        UUID playerUuid = playerRef.getUuid();
        if (!PlacementGizmoPointClient.ownsPoint(playerUuid, pointId)) {
            return false;
        }
        if (!hasPointToolInHand(ref, store)) {
            return false;
        }
        if (!PlacementGizmoPivot.isDragging(playerUuid)) {
            Vector3d committed = PlacementGizmoLivePreview.committedCenter(playerUuid);
            if (committed == null) {
                committed = after;
            }
            Vector3d hologramBase = PlacementGizmoLivePreview.currentHologramPosition(store, playerUuid);
            PlacementGizmoPivot.beginDrag(playerUuid, committed, hologramBase);
        }
        Vector3d committed = PlacementGizmoPivot.dragCommittedCenter(playerUuid);
        if (committed == null) {
            return true;
        }
        Vector3d offset = new Vector3d(after).sub(committed);
        PlacementGizmoPivot.remember(playerUuid, after);
        PlacementGizmoLivePreview.applyDragOffset(playerRef, ref, store, offset);
        return true;
    }

    public static boolean handlePointRotate(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PointToolRotate packet
    ) {
        UUID playerUuid = playerRef.getUuid();
        if (!PlacementGizmoPointClient.ownsPoint(playerUuid, packet.pointId)) {
            return false;
        }
        if (!hasPointToolInHand(ref, store)) {
            return false;
        }
        float yawRadians = packet.newRotation.y();
        int steps = PlacementGizmoPreviewRefresh.rotationStepsFromBodyYawRadians(yawRadians);
        applyRotationSteps(playerUuid, ref, store, playerRef, steps);
        PlacementGizmoLivePreview.applyDragRotation(store, playerUuid, yawRadians);
        return true;
    }

    private static boolean enterGizmoMode(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        @Nonnull Runnable onActivated
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return false;
        }
        ItemStack held = InventoryComponent.getItemInHand(store, ref);
        String enabledItemId = ItemStack.isEmpty(held) ? null : held.getItemId();
        if (enabledItemId == null) {
            return false;
        }
        World world = store.getExternalData().getWorld();
        UUID playerUuid = playerRef.getUuid();
        PlotPlacementCameraUtil.resetToPlayerCamera(playerRef);
        player.getPageManager().setPage(ref, store, Page.None);
        onActivated.run();
        sendEnabledBuilderTools(playerRef, enabledItemId);
        world.execute(
            () -> {
                sendEnabledBuilderTools(playerRef, enabledItemId);
                Vector3d center = PlacementGizmoPivot.remembered(playerUuid);
                if (center != null) {
                    PlacementGizmoPointClient.activate(playerRef, center, currentYawRadians(playerUuid));
                }
            }
        );
        return true;
    }

    private static void shiftAnchorByDelta(
        @Nonnull UUID playerUuid,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        int dx,
        int dy,
        int dz
    ) {
        PlotPlacementSession plot = PlotPlacementSessions.get(playerUuid);
        if (plot != null && plot.isGizmoMoveActive()) {
            Vector3i anchor = plot.getAnchor();
            plot.setAnchor(new Vector3i(anchor.x + dx, anchor.y + dy, anchor.z + dz));
            PlacementGizmoPreviewRefresh.refreshPlot(ref, store, playerRef, plot);
            return;
        }
        CharterRelocationSession charter = CharterRelocationSessions.get(playerUuid);
        if (charter != null && charter.isGizmoMoveActive()) {
            Vector3i anchor = charter.getAnchor();
            charter.setAnchor(new Vector3i(anchor.x + dx, anchor.y + dy, anchor.z + dz));
            PlacementGizmoPreviewRefresh.refreshCharter(ref, store, playerRef, charter);
            return;
        }
        PropPlacementSession prop = PropPlacementSessions.get(playerUuid);
        if (prop != null && prop.isGizmoMoveActive()) {
            Vector3i anchor = prop.getAnchor();
            prop.setAnchor(new Vector3i(anchor.x + dx, anchor.y + dy, anchor.z + dz));
            PlacementGizmoPreviewRefresh.refreshProp(ref, store, playerRef, prop);
        }
    }

    private static void refreshActiveSession(
        @Nonnull UUID playerUuid,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef
    ) {
        PlotPlacementSession plot = PlotPlacementSessions.get(playerUuid);
        if (plot != null && plot.isGizmoMoveActive()) {
            PlacementGizmoPreviewRefresh.refreshPlot(ref, store, playerRef, plot);
            return;
        }
        CharterRelocationSession charter = CharterRelocationSessions.get(playerUuid);
        if (charter != null && charter.isGizmoMoveActive()) {
            PlacementGizmoPreviewRefresh.refreshCharter(ref, store, playerRef, charter);
            return;
        }
        PropPlacementSession prop = PropPlacementSessions.get(playerUuid);
        if (prop != null && prop.isGizmoMoveActive()) {
            PlacementGizmoPreviewRefresh.refreshProp(ref, store, playerRef, prop);
        }
    }

    private static void applyRotationSteps(
        @Nonnull UUID playerUuid,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef,
        int steps
    ) {
        PlotPlacementSession plot = PlotPlacementSessions.get(playerUuid);
        if (plot != null && plot.isGizmoMoveActive()) {
            plot.setRotationSteps(steps);
            if (!PlacementGizmoPivot.isDragging(playerUuid)) {
                PlacementGizmoPreviewRefresh.refreshPlot(ref, store, playerRef, plot);
            }
            return;
        }
        CharterRelocationSession charter = CharterRelocationSessions.get(playerUuid);
        if (charter != null && charter.isGizmoMoveActive()) {
            charter.setRotationSteps(steps);
            if (!PlacementGizmoPivot.isDragging(playerUuid)) {
                PlacementGizmoPreviewRefresh.refreshCharter(ref, store, playerRef, charter);
            }
            return;
        }
        PropPlacementSession prop = PropPlacementSessions.get(playerUuid);
        if (prop != null && prop.isGizmoMoveActive()) {
            prop.setRotationSteps(steps);
            if (!PlacementGizmoPivot.isDragging(playerUuid)) {
                PlacementGizmoPreviewRefresh.refreshProp(ref, store, playerRef, prop);
            }
        }
    }

    private static float currentYawRadians(@Nonnull UUID playerUuid) {
        PlotPlacementSession plot = PlotPlacementSessions.get(playerUuid);
        if (plot != null && plot.isGizmoMoveActive()) {
            return (float) plot.getPrefabYaw().getRadians();
        }
        CharterRelocationSession charter = CharterRelocationSessions.get(playerUuid);
        if (charter != null && charter.isGizmoMoveActive()) {
            return (float) charter.getBlockHorizontalRotation().getRadians();
        }
        PropPlacementSession prop = PropPlacementSessions.get(playerUuid);
        if (prop != null && prop.isGizmoMoveActive()) {
            return (float) prop.getYaw().getRadians();
        }
        return 0f;
    }

    private static boolean isPlotPlacementStaffInHand(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        ItemStack held = InventoryComponent.getItemInHand(store, ref);
        return held != null
            && AetherhavenConstants.PLOT_PLACEMENT_TOOL_ITEM_ID.equals(held.getItemId())
            && hasPointBuilderTool(held);
    }

    private static boolean isPropItemInHand(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        ItemStack held = InventoryComponent.getItemInHand(store, ref);
        if (ItemStack.isEmpty(held) || !hasPointBuilderTool(held)) {
            return false;
        }
        if (PropItemMetadata.readPropId(held) != null) {
            return true;
        }
        String itemId = held.getItemId();
        if (itemId == null) {
            return false;
        }
        if (AetherhavenConstants.PROP_ITEM_ID.equals(itemId)) {
            return true;
        }
        if (PropVirtualItemRegistry.isVirtualId(itemId)) {
            return true;
        }
        return PropShopItemIds.propIdFromItemId(itemId) != null;
    }

    private static boolean hasPointToolInHand(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        ItemStack held = InventoryComponent.getItemInHand(store, ref);
        return !ItemStack.isEmpty(held) && hasPointBuilderTool(held);
    }

    private static boolean hasPointBuilderTool(@Nonnull ItemStack stack) {
        BuilderTool builderTool = stack.getItem().getBuilderTool();
        return builderTool != null && POINT_TOOL_ID.equals(builderTool.getId());
    }

    private static void sendEnabledBuilderTools(@Nonnull PlayerRef playerRef, @Nonnull String itemId) {
        BuilderToolsEnabledTools packet = new BuilderToolsEnabledTools();
        packet.toolIds = new String[] { itemId };
        playerRef.getPacketHandler().write(packet);
    }
}
