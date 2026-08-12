package com.hexvane.aetherhaven.prop;

import com.hexvane.aetherhaven.pathtool.PathDebugPreviewUtil;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hypixel.hytale.math.matrix.Matrix4dUtil;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.protocol.packets.player.ClearDebugShapes;
import com.hypixel.hytale.protocol.packets.player.DisplayDebug;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Matrix4d;
import org.joml.Vector3f;

/** Sends translucent {@link DebugShape#Cube} overlays for prop footprints, player-specific (see {@link PathDebugPreviewUtil}). */
public final class PropDebugCubeUtil {
    /**
     * Long hold so placement previews stay visible until cleared / replaced. Packaging wand refreshes sooner and can
     * pass a shorter lifetime.
     */
    private static final float DEFAULT_SECONDS = 6f * 60f * 60f;

    private PropDebugCubeUtil() {}

    public static void clearFor(@Nullable PlayerRef player) {
        if (player == null) {
            return;
        }
        player.getPacketHandler().write(new ClearDebugShapes());
    }

    /** Sends a padded solid cube over {@code fp} in the given color/opacity, held for {@link #DEFAULT_SECONDS}. */
    public static void sendFootprintCube(
        @Nonnull PlayerRef player,
        @Nonnull PlotFootprintRecord fp,
        double padding,
        float r,
        float g,
        float b,
        float a
    ) {
        sendFootprintCube(player, fp, padding, r, g, b, a, DEFAULT_SECONDS);
    }

    public static void sendFootprintCube(
        @Nonnull PlayerRef player,
        @Nonnull PlotFootprintRecord fp,
        double padding,
        float r,
        float g,
        float b,
        float a,
        float seconds
    ) {
        double x0 = fp.getMinX() - padding;
        double y0 = fp.getMinY() - padding;
        double z0 = fp.getMinZ() - padding;
        double x1 = fp.getMaxX() + 1.0 + padding;
        double y1 = fp.getMaxY() + 1.0 + padding;
        double z1 = fp.getMaxZ() + 1.0 + padding;
        double cx = (x0 + x1) * 0.5;
        double cy = (y0 + y1) * 0.5;
        double cz = (z0 + z1) * 0.5;
        double sx = x1 - x0;
        double sy = y1 - y0;
        double sz = z1 - z0;
        Matrix4d m = new Matrix4d();
        m.identity();
        m.translate(cx, cy, cz);
        // DisplayDebug Cube mesh is unit-sized (extent 1 on each axis); scale by full world extent.
        m.scale(sx, sy, sz);
        DisplayDebug packet =
            new DisplayDebug(
                DebugShape.Cube,
                Matrix4dUtil.asFloatData(m),
                new Vector3f(r, g, b),
                seconds,
                (byte) PathDebugPreviewUtil.FLAG_SOLID_OVERLAY,
                null,
                a
            );
        player.getPacketHandler().write(packet);
    }
}
