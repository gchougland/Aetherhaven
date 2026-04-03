package com.hexvane.aetherhaven.poi.tool;

import com.hypixel.hytale.math.matrix.Matrix4d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.protocol.packets.player.DisplayDebug;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

/**
 * Sends a single debug cylinder (line) to one player — {@link DebugUtils#addLine} broadcasts to the whole world.
 */
public final class PoiDebugLineHelper {
    private PoiDebugLineHelper() {}

    public static void addLineToPlayer(
        @Nonnull PlayerRef playerRef,
        double startX,
        double startY,
        double startZ,
        double endX,
        double endY,
        double endZ,
        @Nonnull Vector3f color,
        double thickness,
        float timeSeconds,
        int flags
    ) {
        double dirX = endX - startX;
        double dirY = endY - startY;
        double dirZ = endZ - startZ;
        double length = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (length < 0.001) {
            return;
        }
        Matrix4d tmp = new Matrix4d();
        Matrix4d matrix = new Matrix4d();
        matrix.identity();
        matrix.translate(startX, startY, startZ);
        double angleY = Math.atan2(dirZ, dirX);
        matrix.rotateAxis(angleY + (Math.PI / 2), 0.0, 1.0, 0.0, tmp);
        double angleX = Math.atan2(Math.sqrt(dirX * dirX + dirZ * dirZ), dirY);
        matrix.rotateAxis(angleX, 1.0, 0.0, 0.0, tmp);
        matrix.translate(0.0, length / 2.0, 0.0);
        matrix.scale(thickness, length, thickness);
        DisplayDebug packet = new DisplayDebug(
            DebugShape.Cylinder,
            matrix.asFloatData(),
            new com.hypixel.hytale.protocol.Vector3f(color.x, color.y, color.z),
            timeSeconds,
            (byte) flags,
            null,
            DebugUtils.DEFAULT_OPACITY
        );
        playerRef.getPacketHandler().write(packet);
    }
}
