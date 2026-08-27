package com.hexvane.aetherhaven.placement;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.player.PointToolCreate;
import com.hypixel.hytale.protocol.packets.player.PointToolDelete;
import com.hypixel.hytale.protocol.packets.player.PointToolDuplicate;
import com.hypixel.hytale.protocol.packets.player.PointToolMove;
import com.hypixel.hytale.protocol.packets.player.PointToolMultiMove;
import com.hypixel.hytale.protocol.packets.player.PointToolRotate;
import com.hypixel.hytale.protocol.packets.player.PointToolSelect;
import com.hypixel.hytale.protocol.packets.player.PointToolSetShape;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.io.adapter.PacketFilter;
import com.hypixel.hytale.server.core.io.adapter.PlayerPacketFilter;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Intercepts vanilla Point tool packets for scoped placement gizmo sessions. */
public final class PlacementGizmoPacketAdapter {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    @Nullable
    private PacketFilter inboundFilter;

    public void register() {
        inboundFilter = PacketAdapters.registerInbound((PlayerPacketFilter) this::onInboundPacket);
        LOGGER.atInfo().log("Placement gizmo packet adapter registered");
    }

    public void deregister() {
        if (inboundFilter != null) {
            try {
                PacketAdapters.deregisterInbound(inboundFilter);
            } catch (Exception e) {
                LOGGER.atWarning().log("Failed to deregister placement gizmo inbound filter: %s", e.getMessage());
            }
            inboundFilter = null;
        }
    }

    private boolean onInboundPacket(@Nonnull PlayerRef playerRef, @Nonnull Packet packet) {
        try {
            if (!isPointToolPacket(packet)) {
                return false;
            }
            UUID playerUuid = playerRef.getUuid();
            if (!PlacementGizmoService.isGizmoMoveActive(playerUuid)) {
                return false;
            }
            World world = PlacementGizmoService.findGizmoWorld(playerUuid);
            if (world == null) {
                return false;
            }
            var ref = playerRef.getReference();
            if (ref == null || !ref.isValid()) {
                return false;
            }
            if (packet instanceof PointToolMove move) {
                world.execute(
                    () -> {
                        if (!ref.isValid()) {
                            return;
                        }
                        PlacementGizmoService.handlePointMove(playerRef, ref, ref.getStore(), move);
                    }
                );
                return true;
            }
            if (packet instanceof PointToolMultiMove multiMove) {
                world.execute(
                    () -> {
                        if (!ref.isValid()) {
                            return;
                        }
                        PlacementGizmoService.handlePointMultiMove(playerRef, ref, ref.getStore(), multiMove);
                    }
                );
                return true;
            }
            if (packet instanceof PointToolRotate rotate) {
                world.execute(
                    () -> {
                        if (!ref.isValid()) {
                            return;
                        }
                        PlacementGizmoService.handlePointRotate(playerRef, ref, ref.getStore(), rotate);
                    }
                );
                return true;
            }
            return true;
        } catch (Exception e) {
            LOGGER.atWarning().log("Placement gizmo inbound packet filter error for %s: %s", playerRef.getUuid(), e.getMessage());
        }
        return false;
    }

    private static boolean isPointToolPacket(@Nonnull Packet packet) {
        return packet instanceof PointToolMove
            || packet instanceof PointToolRotate
            || packet instanceof PointToolMultiMove
            || packet instanceof PointToolCreate
            || packet instanceof PointToolDelete
            || packet instanceof PointToolDuplicate
            || packet instanceof PointToolSelect
            || packet instanceof PointToolSetShape;
    }
}
