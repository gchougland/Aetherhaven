package com.hexvane.aetherhaven.plotcreator;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolGeneralAction;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolOnUseInteraction;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolSelectionToolAskForClipboard;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolSelectionTransform;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolSelectionUpdate;
import com.hypixel.hytale.protocol.packets.buildertools.BuilderToolSetTransformationModeState;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Scoped Selection tool packets for plot creator bounds editing in adventure mode. */
public final class PlotCreatorSelectionBoundsAdapter {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    public void register() {
        LOGGER.atInfo().log("Plot creator selection bounds packet adapter registered");
    }

    public void deregister() {
        // Packet interception is owned by AetherhavenInboundPackets for the lifetime of each connection.
    }

    /** Returns {@code true} when the packet was consumed and must not reach the vanilla builder tools. */
    public static boolean handleInbound(@Nonnull PlayerRef playerRef, @Nonnull Packet packet) {
        try {
            if (!isBuilderToolServerPacket(packet)) {
                return false;
            }
            PlotCreatorSession session = PlotCreatorSessions.get(playerRef.getUuid());
            if (session == null) {
                return false;
            }
            // Packet filters run on the network thread; never touch Store/Inventory here.
            boolean boundsEditing =
                session.getDraft().isEditingBounds() && !session.getDraft().isFestivalSizeLocked();
            if (boundsEditing) {
                var ref = playerRef.getReference();
                if (ref == null || !ref.isValid()) {
                    return true;
                }
                if (packet instanceof BuilderToolGeneralAction) {
                    return true;
                }
                if (packet instanceof BuilderToolSetTransformationModeState state && state.enabled) {
                    PlotCreatorSelectionBoundsService.exitSelectionTransformMode(playerRef);
                    return true;
                }
                if (isSelectionBoundsPacket(packet)) {
                    return handleSelectionBoundsPacket(playerRef, session, ref, packet);
                }
                if (packet instanceof BuilderToolOnUseInteraction) {
                    return true;
                }
                return true;
            }
            return true;
        } catch (Exception e) {
            LOGGER.atWarning().log(
                "Plot creator selection bounds packet filter error for %s: %s",
                playerRef.getUuid(),
                e.getMessage()
            );
        }
        return false;
    }

    private static boolean handleSelectionBoundsPacket(
        @Nonnull PlayerRef playerRef,
        @Nonnull PlotCreatorSession session,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Packet packet
    ) {
        if (session.getDraft().isFestivalSizeLocked()) {
            return packet instanceof BuilderToolSelectionTransform
                || packet instanceof BuilderToolSelectionToolAskForClipboard
                || packet instanceof BuilderToolSetTransformationModeState;
        }
        World world = session.getWorld();
        if (world == null) {
            return true;
        }
        if (packet instanceof BuilderToolSetTransformationModeState state) {
            if (state.enabled) {
                PlotCreatorSelectionBoundsService.exitSelectionTransformMode(playerRef);
                return true;
            }
            world.execute(
                () -> {
                    if (!ref.isValid()) {
                        return;
                    }
                    PlotCreatorSelectionBoundsService.handleTransformationModeState(playerRef, state);
                }
            );
            return true;
        }
        if (packet instanceof BuilderToolSelectionToolAskForClipboard) {
            world.execute(
                () -> {
                    if (!ref.isValid()) {
                        return;
                    }
                    PlotCreatorSelectionBoundsService.handleSelectionAskForClipboard(
                        playerRef,
                        ref,
                        ref.getStore()
                    );
                }
            );
            return true;
        }
        if (packet instanceof BuilderToolSelectionTransform transform) {
            PlotCreatorSelectionBoundsService.trackLiveSelectionFromTransform(playerRef.getUuid(), transform);
            if (transform.isExitingTransformMode) {
                world.execute(
                    () -> {
                        if (!ref.isValid()) {
                            return;
                        }
                        PlotCreatorSelectionBoundsService.exitSelectionTransformMode(playerRef);
                    }
                );
            }
            return true;
        }
        if (packet instanceof BuilderToolSelectionUpdate update) {
            PlotCreatorSelectionBoundsService.trackLiveSelectionFromUpdate(playerRef.getUuid(), update);
            return true;
        }
        return false;
    }

    private static boolean isBuilderToolServerPacket(@Nonnull Packet packet) {
        return packet.getClass().getName().contains("protocol.packets.buildertools");
    }

    private static boolean isSelectionBoundsPacket(@Nonnull Packet packet) {
        return packet instanceof BuilderToolSelectionUpdate
            || packet instanceof BuilderToolSelectionTransform
            || packet instanceof BuilderToolSelectionToolAskForClipboard
            || packet instanceof BuilderToolSetTransformationModeState;
    }
}
