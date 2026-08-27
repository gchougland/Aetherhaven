package com.hexvane.aetherhaven.placement;

import com.hypixel.hytale.protocol.packets.player.AddOrUpdatePointDisplay;
import com.hypixel.hytale.protocol.packets.player.PointDisplayEntry;
import com.hypixel.hytale.protocol.packets.player.PointShapeType;
import com.hypixel.hytale.protocol.packets.player.PointToolSelection;
import com.hypixel.hytale.protocol.packets.player.RemovePointDisplay;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Drives the vanilla Point tool transform gizmo on the client via {@link PointToolSelection}, the same mechanism
 * the Point inspector uses to auto attach drag arrows without manual picking.
 */
public final class PlacementGizmoPointClient {
    private static final String ID_PREFIX = "ah_place_gizmo_";

    private PlacementGizmoPointClient() {}

    @Nonnull
    public static String pointId(@Nonnull UUID playerUuid) {
        return ID_PREFIX + playerUuid.toString().replace("-", "");
    }

    public static boolean ownsPoint(@Nonnull UUID playerUuid, @Nullable String pointId) {
        return pointId != null && pointId(playerUuid).equals(pointId);
    }

    /** Spawns the invisible gizmo point and auto selects it so drag arrows appear immediately. */
    public static void activate(
        @Nonnull PlayerRef playerRef,
        @Nonnull Vector3d center,
        float yawRadians
    ) {
        String id = pointId(playerRef.getUuid());
        writeDisplay(playerRef, id, center, yawRadians);
        writeSelection(playerRef, id);
    }

    public static void updateDisplay(
        @Nonnull PlayerRef playerRef,
        @Nonnull Vector3d center,
        float yawRadians
    ) {
        writeDisplay(playerRef, pointId(playerRef.getUuid()), center, yawRadians);
    }

    /** Reasserts selection after the hologram moves so the client keeps the gizmo attached. */
    public static void reselect(@Nonnull PlayerRef playerRef) {
        writeSelection(playerRef, pointId(playerRef.getUuid()));
    }

    public static void deactivate(@Nonnull PlayerRef playerRef) {
        String id = pointId(playerRef.getUuid());
        writeSelectionClear(playerRef);
        playerRef.getPacketHandler().write(new RemovePointDisplay(id));
    }

    private static void writeDisplay(
        @Nonnull PlayerRef playerRef,
        @Nonnull String id,
        @Nonnull Vector3d center,
        float yawRadians
    ) {
        PointDisplayEntry entry = new PointDisplayEntry();
        entry.id = id;
        entry.position = new Vector3f((float) center.x, (float) center.y, (float) center.z);
        entry.rotation = new Vector3f(0f, yawRadians, 0f);
        entry.shape = PointShapeType.Sphere;
        playerRef.getPacketHandler().write(new AddOrUpdatePointDisplay(id, entry));
    }

    private static void writeSelection(@Nonnull PlayerRef playerRef, @Nonnull String id) {
        PointToolSelection packet = new PointToolSelection();
        packet.primaryPointId = id;
        packet.pointIds = new String[] { id };
        playerRef.getPacketHandler().write(packet);
    }

    private static void writeSelectionClear(@Nonnull PlayerRef playerRef) {
        PointToolSelection packet = new PointToolSelection();
        playerRef.getPacketHandler().write(packet);
    }
}
