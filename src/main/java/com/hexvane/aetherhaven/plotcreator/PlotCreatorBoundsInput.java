package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.rts.RtsCommandingSessionIndex;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.MouseButtonState;
import com.hypixel.hytale.protocol.MouseButtonType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseButtonEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerMouseMotionEvent;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Mouse drag input for the plot creator bounds step. */
public final class PlotCreatorBoundsInput {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final String MSG = "aetherhaven_plot_creator.aetherhaven.plotcreator";

    private PlotCreatorBoundsInput() {}

    public static void register(@Nonnull EventRegistry eventRegistry) {
        eventRegistry.register(PlayerMouseButtonEvent.class, PlotCreatorBoundsInput::onMouseButton);
        eventRegistry.register(PlayerMouseMotionEvent.class, PlotCreatorBoundsInput::onMouseMotion);
        LOGGER.atInfo().log("Plot creator bounds input registered");
    }

    public static void tickHover(
        @Nonnull PlotCreatorSession session,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef
    ) {
        PlotCreatorDraft draft = session.getDraft();
        if (draft.getStep() != PlotCreatorStep.BOUNDS
            || draft.getBoundsPhase() != PlotCreatorBoundsPhase.FACE_ADJUST
            || draft.isBoundsPrimaryHeld()
            || draft.getActiveBoundsFaceDrag() != null) {
            return;
        }
        if (draft.getCornerFirst() == null || draft.getCornerSecond() == null) {
            draft.setHoveredBoundsFace(null);
            return;
        }
        Vector3i min = draft.boundsMin();
        Vector3i max = draft.boundsMax();
        PlotCreatorBoundsFace hovered =
            PlotCreatorBoundsFacePick.pick(ref, store, min, max, PlotCreatorBoundsConstants.FACE_PICK_REACH);
        draft.setHoveredBoundsFace(hovered);
    }

    static boolean isBoundsActive(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef
    ) {
        if (RtsCommandingSessionIndex.isCommanding(playerRef.getUuid())) {
            return false;
        }
        PlotCreatorSession session = PlotCreatorSessions.get(playerRef.getUuid());
        if (session == null || session.getDraft().getStep() != PlotCreatorStep.BOUNDS) {
            return false;
        }
        return PlotCreatorInteractions.isPlotCreatorStaff(activeItem(store, ref));
    }

    static void onDragTick(
        @Nonnull PlotCreatorSession session,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef
    ) {
        PlotCreatorDraft draft = session.getDraft();
        Vector3i aim = PlotCreatorBoundsRayPick.aimCell(ref, store);
        if (draft.getBoundsPhase() == PlotCreatorBoundsPhase.INITIAL_DRAG) {
            draft.setBoundsDragEnd(aim);
            PlotCreatorService.refreshBoundsVisuals(session, playerRef);
            PlotCreatorInteractions.refreshHud(playerRef, ref, store, session);
        } else if (draft.getActiveBoundsFaceDrag() != null) {
            String err = PlotCreatorBoundsFaceDrag.apply(draft, draft.getActiveBoundsFaceDrag(), aim);
            if (err != null) {
                playerRef.sendMessage(Message.translation(MSG + ".error." + err));
            }
            PlotCreatorService.refreshBoundsVisuals(session, playerRef);
            PlotCreatorInteractions.refreshHud(playerRef, ref, store, session);
        }
    }

    private static void onMouseButton(@Nonnull PlayerMouseButtonEvent event) {
        Ref<EntityStore> ref = event.getPlayerRef();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null || !isBoundsActive(ref, store, playerRef)) {
            return;
        }
        PlotCreatorSession session = PlotCreatorSessions.get(playerRef.getUuid());
        if (session == null) {
            return;
        }
        MouseButtonType type = event.getMouseButton().mouseButtonType;
        MouseButtonState state = event.getMouseButton().state;
        if (type == MouseButtonType.Left) {
            if (state == MouseButtonState.Pressed) {
                onPrimaryPress(session, ref, store, playerRef);
            } else if (state == MouseButtonState.Released) {
                onPrimaryRelease(session, ref, store, playerRef);
            }
        }
    }

    private static void onMouseMotion(@Nonnull PlayerMouseMotionEvent event) {
        Ref<EntityStore> ref = event.getPlayerRef();
        if (ref == null || !ref.isValid()) {
            return;
        }
        Store<EntityStore> store = ref.getStore();
        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        if (playerRef == null || !isBoundsActive(ref, store, playerRef)) {
            return;
        }
        PlotCreatorSession session = PlotCreatorSessions.get(playerRef.getUuid());
        if (session == null) {
            return;
        }
        PlotCreatorDraft draft = session.getDraft();
        if (!draft.isBoundsPrimaryHeld()) {
            return;
        }
        onDragTick(session, ref, store, playerRef);
    }

    static void onPrimaryPress(
        @Nonnull PlotCreatorSession session,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef
    ) {
        PlotCreatorDraft draft = session.getDraft();
        draft.setBoundsPrimaryHeld(true);
        Vector3i aim = PlotCreatorBoundsRayPick.aimCell(ref, store);
        if (draft.getBoundsPhase() == PlotCreatorBoundsPhase.INITIAL_DRAG) {
            draft.setBoundsDragStart(aim);
            draft.setBoundsDragEnd(aim);
        } else if (draft.getHoveredBoundsFace() != null) {
            draft.setActiveBoundsFaceDrag(draft.getHoveredBoundsFace());
        }
        PlotCreatorService.refreshBoundsVisuals(session, playerRef);
    }

    static void cancelPrimaryHold(@Nonnull PlotCreatorSession session, @Nonnull PlayerRef playerRef) {
        PlotCreatorDraft draft = session.getDraft();
        draft.setBoundsPrimaryHeld(false);
        draft.setActiveBoundsFaceDrag(null);
        draft.setBoundsDragStart(null);
        draft.setBoundsDragEnd(null);
        PlotCreatorService.refreshBoundsVisuals(session, playerRef);
    }

    static void onPrimaryRelease(
        @Nonnull PlotCreatorSession session,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef
    ) {
        PlotCreatorDraft draft = session.getDraft();
        draft.setBoundsPrimaryHeld(false);
        if (draft.getBoundsPhase() == PlotCreatorBoundsPhase.INITIAL_DRAG) {
            finishInitialDrag(session, ref, store, playerRef);
        } else if (draft.getActiveBoundsFaceDrag() != null) {
            draft.setActiveBoundsFaceDrag(null);
            PlotCreatorService.refreshBoundsVisuals(session, playerRef);
            PlotCreatorInteractions.refreshHud(playerRef, ref, store, session);
        }
    }

    private static void finishInitialDrag(
        @Nonnull PlotCreatorSession session,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull PlayerRef playerRef
    ) {
        PlotCreatorDraft draft = session.getDraft();
        Vector3i start = draft.getBoundsDragStart();
        Vector3i end = draft.getBoundsDragEnd();
        if (!PlotCreatorBoundsValidation.initialDragLargeEnough(start, end)) {
            draft.setBoundsDragStart(null);
            draft.setBoundsDragEnd(null);
            playerRef.sendMessage(Message.translation(MSG + ".error.boundsDragTooShort"));
            PlotCreatorService.refreshBoundsVisuals(session, playerRef);
            return;
        }
        Vector3i min = PlotCreatorBoundsValidation.min(start, end);
        Vector3i max = PlotCreatorBoundsValidation.max(start, end);
        String err = PlotCreatorBoundsValidation.validateMinMax(min, max);
        if (err != null) {
            playerRef.sendMessage(Message.translation(MSG + ".error." + err));
            draft.setBoundsDragStart(null);
            draft.setBoundsDragEnd(null);
            PlotCreatorService.refreshBoundsVisuals(session, playerRef);
            return;
        }
        PlotCreatorBoundsValidation.commitCorners(draft, min, max);
        draft.setBoundsPhase(PlotCreatorBoundsPhase.FACE_ADJUST);
        draft.setBoundsDragStart(null);
        draft.setBoundsDragEnd(null);
        playerRef.sendMessage(Message.translation(MSG + ".hint.boundsReady"));
        PlotCreatorService.refreshBoundsVisuals(session, playerRef);
        PlotCreatorInteractions.refreshHud(playerRef, ref, store, session);
    }

    @Nullable
    private static ItemStack activeItem(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> ref) {
        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        return hotbar != null ? hotbar.getActiveItem() : null;
    }
}
