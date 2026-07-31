package com.hexvane.aetherhaven.poi.tool;

import com.hexvane.aetherhaven.debug.DebugLineCylinderUtil;
import com.hypixel.hytale.math.matrix.Matrix4dUtil;
import org.joml.Vector3f;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.protocol.packets.player.DisplayDebug;
import com.hypixel.hytale.server.core.modules.debug.DebugUtils;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;
import org.joml.Matrix4d;

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
        double dx = endX - startX;
        double dy = endY - startY;
        double dz = endZ - startZ;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        Matrix4d matrix = DebugLineCylinderUtil.segmentMatrix(
            startX, startY, startZ, endX, endY, endZ, thickness, length
        );
        if (matrix == null) {
            return;
        }
        int renderFlags = flags | DebugUtils.FLAG_NO_WIREFRAME;
        DisplayDebug packet = new DisplayDebug(
            DebugShape.Cylinder,
            Matrix4dUtil.asFloatData(matrix),
            color,
            timeSeconds,
            (byte) renderFlags,
            null,
            DebugUtils.DEFAULT_OPACITY
        );
        playerRef.getPacketHandler().write(packet);
    }
}
