package com.hexvane.aetherhaven.pathtool;

import com.hypixel.hytale.math.matrix.Matrix4d;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.DebugFlags;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.protocol.packets.player.ClearDebugShapes;
import com.hypixel.hytale.protocol.packets.player.DisplayDebug;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Sends {@link DisplayDebug} to a single player (unlike stock {@code DebugUtils}, which fans out to the whole world).
 * Path nodes: solid spheres and spline in white; yaw handle is a light segment.
 */
public final class PathDebugPreviewUtil {
    public static final int FLAG_FADE = 1 << DebugFlags.Fade.getValue();
    private static final int FLAG_NO_WIREFRAME = 1 << DebugFlags.NoWireframe.getValue();
    public static final int FLAG_MACHINIMA = FLAG_FADE | FLAG_NO_WIREFRAME;
    /** Solid overlay without fade (less “pulsing” when shapes are refreshed). */
    public static final int FLAG_SOLID_OVERLAY = FLAG_NO_WIREFRAME;
    private static final float PREVIEW_SECONDS = 0.35f;
    /**
     * Long lifetime for path-tool spline / gizmo packets so we can skip resending identical geometry every tick (reduces
     * flicker from {@link ClearDebugShapes}).
     */
    public static final float PATH_TOOL_DEBUG_HOLD_SECONDS = 48f;

    public static final Vector3f COLOR_KEYFRAME = new Vector3f(0.92f, 0.92f, 0.95f);
    public static final Vector3f COLOR_KEYFRAME_SEL = new Vector3f(1.0f, 1.0f, 1.0f);
    public static final Vector3f COLOR_PATH_EDGE = new Vector3f(0.95f, 0.95f, 0.98f);
    public static final Vector3f COLOR_TANGENT = new Vector3f(0.88f, 0.9f, 0.95f);

    private PathDebugPreviewUtil() {}

    public static void clear(@Nullable PlayerRef player) {
        if (player == null) {
            return;
        }
        player.getPacketHandler().write(new ClearDebugShapes());
    }

    /**
     * Keyframe sphere and yaw direction segment.
     */
    public static void drawMachinimaNode(
        @Nonnull PlayerRef player,
        @Nonnull Vector3d center,
        double yawDeg,
        boolean selected
    ) {
        Vector3f c = selected ? COLOR_KEYFRAME_SEL : COLOR_KEYFRAME;
        double srad = selected ? 0.36 : 0.3;
        drawSphere(
            player,
            center,
            c,
            srad,
            selected ? 0.9f : 0.8f
        );
        Vector3d f = PathSplineUtil.forwardHorizontal(yawDeg);
        double start = srad * 0.9 + 0.01;
        double handleLen = 0.32;
        double ax = center.getX() + f.getX() * start;
        double ay = center.getY() + f.getY() * start;
        double az = center.getZ() + f.getZ() * start;
        double bx = center.getX() + f.getX() * (start + handleLen);
        double by = center.getY() + f.getY() * (start + handleLen);
        double bz = center.getZ() + f.getZ() * (start + handleLen);
        drawLine(
            player,
            new Vector3d(ax, ay, az),
            new Vector3d(bx, by, bz),
            COLOR_TANGENT,
            0.1
        );
    }

    private static void drawSphere(
        @Nonnull PlayerRef player,
        @Nonnull Vector3d center,
        @Nonnull Vector3f color,
        double radius,
        float opacity
    ) {
        Matrix4d m = new Matrix4d();
        m.identity();
        m.translate(center.getX(), center.getY(), center.getZ());
        m.scale(radius, radius, radius);
        add(player, DebugShape.Sphere, m, color, opacity, FLAG_MACHINIMA, PATH_TOOL_DEBUG_HOLD_SECONDS);
    }

    public static void drawLine(
        @Nonnull PlayerRef player, @Nonnull Vector3d a, @Nonnull Vector3d b, @Nonnull Vector3f color, double thickness
    ) {
        double dirX = b.getX() - a.getX();
        double dirY = b.getY() - a.getY();
        double dirZ = b.getZ() - a.getZ();
        double len = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (len < 0.001) {
            return;
        }
        Matrix4d tmp = new Matrix4d();
        Matrix4d matrix = new Matrix4d();
        matrix.identity();
        matrix.translate(a.getX(), a.getY(), a.getZ());
        double angleY = Math.atan2(dirZ, dirX);
        matrix.rotateAxis(angleY + (Math.PI / 2), 0.0, 1.0, 0.0, tmp);
        double angleX = Math.atan2(Math.sqrt(dirX * dirX + dirZ * dirZ), dirY);
        matrix.rotateAxis(angleX, 1.0, 0.0, 0.0, tmp);
        matrix.translate(0.0, len * 0.5, 0.0);
        matrix.scale(thickness, len, thickness);
        add(player, DebugShape.Cylinder, matrix, color, 0.75f, FLAG_MACHINIMA, PATH_TOOL_DEBUG_HOLD_SECONDS);
    }

    /**
     * Ghost footprint sitting on the block top face (slightly above y+1) so it is not buried inside solid terrain.
     */
    public static void drawPlannedBlock(@Nonnull PlayerRef pr, int x, int y, int z, @Nonnull Vector3f color, @Nonnull com.hypixel.hytale.server.core.universe.world.World w) {
        if (w.getChunkIfInMemory(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z)) == null) {
            return;
        }
        double cx = x + 0.5;
        double cy = y + 1.0 + 0.03;
        double cz = z + 0.5;
        Matrix4d m = new Matrix4d();
        m.identity();
        m.translate(cx, cy, cz);
        m.scale(0.48, 0.055, 0.48);
        add(pr, DebugShape.Cube, m, color, 0.72f, FLAG_SOLID_OVERLAY, PATH_TOOL_DEBUG_HOLD_SECONDS);
    }

    /**
     * Next assembly cell: solid cube centered on the block, slightly larger than 1m, with fade so rapid resend reads as
     * a pulse (path tool uses a flat slab; this is intentionally different).
     */
    public static void drawAssemblyNextCellCube(
        @Nonnull PlayerRef pr,
        int x,
        int y,
        int z,
        @Nonnull Vector3f color,
        @Nonnull com.hypixel.hytale.server.core.universe.world.World w,
        double pulse01
    ) {
        if (w.getChunkIfInMemory(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z)) == null) {
            return;
        }
        double half = 0.5 * (1.06 + 0.10 * Math.sin(pulse01 * Math.PI * 2.0));
        double cx = x + 0.5;
        double cy = y + 0.5;
        double cz = z + 0.5;
        Matrix4d m = new Matrix4d();
        m.identity();
        m.translate(cx, cy, cz);
        m.scale(half, half, half);
        float opacity = (float) (0.62 + 0.18 * (0.5 + 0.5 * Math.sin(pulse01 * Math.PI * 2.0 + 0.4)));
        add(pr, DebugShape.Cube, m, color, opacity, FLAG_MACHINIMA, 0.38f);
    }

    public static float previewSeconds() {
        return PREVIEW_SECONDS;
    }

    private static void add(
        @Nonnull PlayerRef player,
        @Nonnull DebugShape shape,
        @Nonnull Matrix4d matrix,
        @Nonnull Vector3f color,
        float opacity,
        int flags,
        float lifetimeSeconds
    ) {
        com.hypixel.hytale.protocol.Vector3f col = new com.hypixel.hytale.protocol.Vector3f(color.getX(), color.getY(), color.getZ());
        DisplayDebug p = new DisplayDebug(shape, matrix.asFloatData(), col, lifetimeSeconds, (byte) flags, null, opacity);
        player.getPacketHandler().write(p);
    }
}
