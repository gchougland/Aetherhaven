package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.builtin.buildertools.BuilderToolsPlugin;
import com.hypixel.hytale.builtin.buildertools.tooloperations.ToolOperation;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolSelectionToolReplyWithClipboard;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolSelectionTransform;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolSelectionUpdate;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolSetTransformationModeState;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolsEnabledTools;
import com.hypixel.hytale.protocol.packets.interface_.EditorBlocksChange;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Activates the embedded Selection tool and syncs bounds into the plot creator draft. */
public final class PlotCreatorSelectionBoundsService {
    private static final String SELECTION_TOOL_ID = "Selection";
    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Set<String>> GRANTED_PERMISSIONS = new ConcurrentHashMap<>();
    /** Latest selection box from client packets; used on F confirm before async builder state catches up. */
    private static final Map<UUID, Vector3i[]> LIVE_SELECTION = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> LAST_SELECTION_PACKET_MS = new ConcurrentHashMap<>();
    private static final long SELECTION_DRAG_GRACE_MS = 250L;

    private PlotCreatorSelectionBoundsService() {}

    /** Ensures the client can run the embedded Selection tool in adventure mode. */
    public static void ensureSurvivalAccess(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        if (!isActive(playerRef.getUuid())) {
            return;
        }
        grantSurvivalSelectionAccess(playerRef);
    }

    public static boolean isActive(@Nonnull UUID playerUuid) {
        return ACTIVE.contains(playerUuid);
    }

    public static void activate(
        @Nonnull PlotCreatorSession session,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        PlotCreatorDraft draft = session.getDraft();
        if (!draft.isEditingBounds() || draft.isFestivalSizeLocked()) {
            return;
        }
        UUID playerUuid = playerRef.getUuid();
        ACTIVE.add(playerUuid);
        clearLiveSelection(playerUuid);
        LAST_SELECTION_PACKET_MS.remove(playerUuid);
        grantSurvivalSelectionAccess(playerRef);
        exitSelectionTransformMode(playerRef);
        World world = store.getExternalData().getWorld();
        world.execute(
            () -> {
                if (!ref.isValid()) {
                    ACTIVE.remove(playerUuid);
                    revokeSurvivalSelectionAccess(playerUuid);
                    return;
                }
                if (!PlotCreatorStaffBoundsSwap.enterBoundsMode(ref, store)) {
                    ACTIVE.remove(playerUuid);
                    revokeSurvivalSelectionAccess(playerUuid);
                    return;
                }
                sendEnabledBuilderTools(playerRef, world);
                seedBuilderSelection(playerRef, ref, store, draft);
            }
        );
    }

    public static void deactivate(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        UUID playerUuid = playerRef.getUuid();
        boolean wasActive = ACTIVE.remove(playerUuid);
        if (wasActive) {
            clearLiveSelection(playerUuid);
            LAST_SELECTION_PACKET_MS.remove(playerUuid);
            exitSelectionTransformMode(playerRef);
            revokeSurvivalSelectionAccess(playerUuid);
        }
        Store<EntityStore> storeRef = store;
        if (storeRef.isInThread()) {
            restoreNormalStaffInHand(playerRef, ref, storeRef);
            if (wasActive && ref.isValid()) {
                clearBuilderSelection(playerRef, ref, storeRef);
            }
            return;
        }
        World world = storeRef.getExternalData().getWorld();
        world.execute(
            () -> {
                if (!ref.isValid()) {
                    return;
                }
                restoreNormalStaffInHand(playerRef, ref, storeRef);
                if (wasActive) {
                    clearBuilderSelection(playerRef, ref, storeRef);
                }
            }
        );
    }

    public static void deactivateIfPresent(@Nullable PlayerRef playerRef) {
        if (playerRef == null) {
            return;
        }
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref == null || !ref.isValid()) {
            if (ACTIVE.remove(playerRef.getUuid())) {
                revokeSurvivalSelectionAccess(playerRef.getUuid());
            }
            return;
        }
        deactivate(playerRef, ref, ref.getStore());
    }

    /** Swaps back to the normal plot creator staff and clears survival builder tool overrides. */
    public static void restoreNormalStaffInHand(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        PlotCreatorStaffBoundsSwap.exitBoundsMode(ref, store);
        clearEnabledBuilderTools(playerRef, store.getExternalData().getWorld());
    }

    public static void syncForSession(
        @Nonnull PlotCreatorSession session,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        if (session.getDraft().isEditingBounds() && !session.getDraft().isFestivalSizeLocked()) {
            activate(session, playerRef, ref, store);
        } else {
            deactivate(playerRef, ref, store);
        }
    }

    public static void redoBoundsSelection(
        @Nonnull PlotCreatorSession session,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        handleSelectionClear(session, playerRef);
        clearBuilderSelection(playerRef, ref, store);
    }

    public static void onSelectionBoundsUpdatedCallback(
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        // Selection updates are handled by PlotCreatorSelectionBoundsAdapter while bounds mode is active.
    }

    public static void onSelectionClearedCallback(
        @Nonnull PlayerRef playerRef,
        @Nonnull Store<EntityStore> store
    ) {
        // Do not restore draft corners on clear; that fights live selection drags.
    }

    /** Copies the visible selection box into the draft for step confirm / advance. */
    @Nullable
    public static String syncDraftBoundsFromBuilderState(
        @Nonnull PlotCreatorSession session,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        Vector3i[] live = LIVE_SELECTION.get(playerRef.getUuid());
        if (live != null) {
            return commitSelectionBoundsToDraft(session, playerRef, ref, store, live[0], live[1]);
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return null;
        }
        var selection = BuilderToolsPlugin.getState(player, playerRef).getSelection();
        if (selection == null || !selection.hasSelectionBounds()) {
            return null;
        }
        return commitSelectionBoundsToDraft(
            session,
            playerRef,
            ref,
            store,
            selection.getSelectionMin(),
            selection.getSelectionMax()
        );
    }

    /** True while selection drag packets are still arriving (primary mouse held). */
    public static boolean isSelectionDragRecent(@Nonnull UUID playerUuid) {
        Long last = LAST_SELECTION_PACKET_MS.get(playerUuid);
        return last != null && System.currentTimeMillis() - last < SELECTION_DRAG_GRACE_MS;
    }

    /** @return plot creator error lang suffix, or null when bounds were copied into the draft */
    @Nullable
    public static String prepareBoundsStepConfirm(
        @Nonnull PlotCreatorSession session,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        exitSelectionTransformMode(playerRef);
        return syncDraftBoundsFromBuilderState(session, playerRef, ref, store);
    }

    public static void exitSelectionTransformMode(@Nonnull PlayerRef playerRef) {
        var prototypeSettings = ToolOperation.getOrCreatePrototypeSettings(playerRef.getUuid());
        prototypeSettings.setInSelectionTransformationMode(false);
        prototypeSettings.setBlockChangesForPlaySelectionToolPasteMode(null);
        prototypeSettings.setFluidChangesForPlaySelectionToolPasteMode(null);
        prototypeSettings.setEntityChangesForPlaySelectionToolPasteMode(null);
    }

    public static void handleTransformationModeState(
        @Nonnull PlayerRef playerRef,
        @Nonnull BuilderToolSetTransformationModeState packet
    ) {
        if (packet.enabled) {
            return;
        }
        exitSelectionTransformMode(playerRef);
    }

    public static void handleSelectionAskForClipboard(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        BuilderToolsPlugin.addToQueue(
            player,
            playerRef,
            (entityRef, builderState, accessor) -> {
                var selection = builderState.getSelection();
                var prototypeSettings = ToolOperation.getOrCreatePrototypeSettings(playerRef.getUuid());
                var reply = new BuilderToolSelectionToolReplyWithClipboard();
                if (selection == null || !selection.hasSelectionBounds()) {
                    prototypeSettings.setBlockChangesForPlaySelectionToolPasteMode(null);
                    prototypeSettings.setFluidChangesForPlaySelectionToolPasteMode(null);
                    prototypeSettings.setEntityChangesForPlaySelectionToolPasteMode(null);
                    playerRef.getPacketHandler().write(reply);
                    return;
                }
                prototypeSettings.setBlockChangesForPlaySelectionToolPasteMode(new com.hypixel.hytale.protocol.packets.interface_.BlockChange[0]);
                prototypeSettings.setFluidChangesForPlaySelectionToolPasteMode(null);
                prototypeSettings.setEntityChangesForPlaySelectionToolPasteMode(null);
                reply.blocksOmitted = true;
                reply.bounds = selection.toEditorSelectionBounds();
                playerRef.getPacketHandler().write(reply);
            }
        );
    }

    /** Records face resize bounds from a network packet (safe off the world thread). */
    public static void trackLiveSelectionFromTransform(
        @Nonnull UUID playerUuid,
        @Nonnull BuilderToolSelectionTransform packet
    ) {
        Vector3i[] bounds = boundsFromSelectionTransform(packet);
        if (bounds == null) {
            return;
        }
        trackLiveSelection(playerUuid, bounds[0], bounds[1]);
        LAST_SELECTION_PACKET_MS.put(playerUuid, System.currentTimeMillis());
    }

    /** Records drag bounds from a network packet (safe off the world thread). */
    public static void trackLiveSelectionFromUpdate(
        @Nonnull UUID playerUuid,
        @Nonnull BuilderToolSelectionUpdate packet
    ) {
        if (isSelectionClearPacket(packet.xMin, packet.yMin, packet.zMin, packet.xMax, packet.yMax, packet.zMax)) {
            return;
        }
        trackLiveSelection(
            playerUuid,
            new Vector3i(
                Math.min(packet.xMin, packet.xMax),
                Math.min(packet.yMin, packet.yMax),
                Math.min(packet.zMin, packet.zMax)
            ),
            new Vector3i(
                Math.max(packet.xMin, packet.xMax),
                Math.max(packet.yMin, packet.yMax),
                Math.max(packet.zMin, packet.zMax)
            )
        );
        LAST_SELECTION_PACKET_MS.put(playerUuid, System.currentTimeMillis());
    }

    static Vector3i[] boundsFromSelectionTransform(@Nonnull BuilderToolSelectionTransform packet) {
        if (packet.initialSelectionMin == null || packet.initialSelectionMax == null) {
            return null;
        }
        Vector3i min =
            new Vector3i(
                packet.initialSelectionMin.x,
                packet.initialSelectionMin.y,
                packet.initialSelectionMin.z
            );
        Vector3i max =
            new Vector3i(
                packet.initialSelectionMax.x,
                packet.initialSelectionMax.y,
                packet.initialSelectionMax.z
            );
        if (packet.applyTransformationToSelectionMinMax && packet.translationOffset != null) {
            min.add(packet.translationOffset.x, packet.translationOffset.y, packet.translationOffset.z);
            max.add(packet.translationOffset.x, packet.translationOffset.y, packet.translationOffset.z);
        }
        return new Vector3i[] {
            new Vector3i(
                Math.min(min.x, max.x),
                Math.min(min.y, max.y),
                Math.min(min.z, max.z)
            ),
            new Vector3i(
                Math.max(min.x, max.x),
                Math.max(min.y, max.y),
                Math.max(min.z, max.z)
            )
        };
    }

    public static void handleSelectionClear(@Nonnull PlotCreatorSession session, @Nonnull PlayerRef playerRef) {
        clearLiveSelection(playerRef.getUuid());
        LAST_SELECTION_PACKET_MS.remove(playerRef.getUuid());
        PlotCreatorDraft draft = session.getDraft();
        draft.setCornerFirst(null);
        draft.setCornerSecond(null);
        draft.setBoundsPhase(PlotCreatorBoundsPhase.SELECTION);
        draft.setPlotAnchor(null);
        if (PlotCreatorWallPieceAuthoring.isBoundsSubstep(draft)) {
            PlotCreatorWallPieceDraft piece = draft.currentWallPiece();
            if (piece != null) {
                piece.clearShape();
            }
        }
        Ref<EntityStore> ref = playerRef.getReference();
        if (ref != null && ref.isValid()) {
            PlotCreatorInteractions.refreshHud(playerRef, ref, ref.getStore(), session);
        }
    }

    public static boolean hasSelectionStaffInHand(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        ItemStack held = InventoryComponent.getItemInHand(store, ref);
        return PlotCreatorStaffBoundsSwap.isBoundsStaff(held);
    }

    private static void trackLiveSelection(@Nonnull UUID playerUuid, @Nonnull Vector3i min, @Nonnull Vector3i max) {
        LIVE_SELECTION.put(
            playerUuid,
            new Vector3i[] { new Vector3i(min), new Vector3i(max) }
        );
    }

    private static void clearLiveSelection(@Nonnull UUID playerUuid) {
        LIVE_SELECTION.remove(playerUuid);
    }

    @Nullable
    private static String commitSelectionBoundsToDraft(
        @Nonnull PlotCreatorSession session,
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull Vector3i min,
        @Nonnull Vector3i max
    ) {
        String err = PlotCreatorService.applyBoundsFromBuilderSelection(session, min, max);
        return err;
    }

    private static void seedBuilderSelection(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotCreatorDraft draft
    ) {
        if (draft.getCornerFirst() == null || draft.getCornerSecond() == null) {
            return;
        }
        Vector3i min = draft.boundsMin();
        Vector3i max = draft.boundsMax();
        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            return;
        }
        BuilderToolsPlugin.addToQueue(
            player,
            playerRef,
            (entityRef, builderState, accessor) -> {
                builderState.update(min.x, min.y, min.z, max.x, max.y, max.z);
                builderState.sendArea();
            }
        );
    }

    private static void clearBuilderSelection(
        @Nonnull PlayerRef playerRef,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        EditorBlocksChange packet = new EditorBlocksChange();
        packet.selection = null;
        playerRef.getPacketHandler().write(packet);
    }

    static boolean isSelectionClearPacket(
        int xMin,
        int yMin,
        int zMin,
        int xMax,
        int yMax,
        int zMax
    ) {
        return xMin == Integer.MIN_VALUE
            && xMax == Integer.MIN_VALUE
            && yMin == Integer.MIN_VALUE
            && yMax == Integer.MIN_VALUE
            && zMin == Integer.MIN_VALUE
            && zMax == Integer.MIN_VALUE;
    }

    private static void grantSurvivalSelectionAccess(@Nonnull PlayerRef playerRef) {
        UUID uuid = playerRef.getUuid();
        if (GRANTED_PERMISSIONS.containsKey(uuid)) {
            return;
        }
        PermissionsModule permissions = PermissionsModule.get();
        Set<String> toGrant = new HashSet<>();
        if (!permissions.hasPermission(uuid, HytalePermissions.EDITOR_SELECTION_USE)) {
            toGrant.add(HytalePermissions.EDITOR_SELECTION_USE.getId());
        }
        if (!permissions.hasPermission(uuid, HytalePermissions.EDITOR_SELECTION_MODIFY)) {
            toGrant.add(HytalePermissions.EDITOR_SELECTION_MODIFY.getId());
        }
        if (!permissions.hasPermission(uuid, HytalePermissions.EDITOR_SELECTION_CLIPBOARD)) {
            toGrant.add(HytalePermissions.EDITOR_SELECTION_CLIPBOARD.getId());
        }
        String toolPerm = HytalePermissions.toolPermission(SELECTION_TOOL_ID);
        if (!permissions.hasPermission(uuid, toolPerm)) {
            toGrant.add(toolPerm);
        }
        if (toGrant.isEmpty()) {
            return;
        }
        GRANTED_PERMISSIONS.put(uuid, toGrant);
        permissions.addUserPermission(uuid, toGrant);
    }

    private static void revokeSurvivalSelectionAccess(@Nonnull UUID playerUuid) {
        Set<String> granted = GRANTED_PERMISSIONS.remove(playerUuid);
        if (granted == null || granted.isEmpty()) {
            return;
        }
        PermissionsModule.get().removeUserPermission(playerUuid, granted);
    }

    private static void sendEnabledBuilderTools(@Nonnull PlayerRef playerRef, @Nonnull World world) {
        writeEnabledBuilderTools(playerRef);
        world.execute(() -> writeEnabledBuilderTools(playerRef));
    }

    private static void clearEnabledBuilderTools(@Nonnull PlayerRef playerRef, @Nonnull World world) {
        writeClearedBuilderTools(playerRef);
        world.execute(() -> writeClearedBuilderTools(playerRef));
    }

    private static void writeEnabledBuilderTools(@Nonnull PlayerRef playerRef) {
        BuilderToolsEnabledTools packet = new BuilderToolsEnabledTools();
        packet.toolIds = new String[] { AetherhavenConstants.PLOT_CREATOR_STAFF_BOUNDS_ITEM_ID };
        playerRef.getPacketHandler().write(packet);
    }

    private static void writeClearedBuilderTools(@Nonnull PlayerRef playerRef) {
        BuilderToolsEnabledTools packet = new BuilderToolsEnabledTools();
        packet.toolIds = new String[0];
        playerRef.getPacketHandler().write(packet);
    }
}
