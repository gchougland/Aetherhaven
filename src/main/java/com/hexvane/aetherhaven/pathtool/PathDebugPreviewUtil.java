package com.hexvane.aetherhaven.pathtool;

import com.hexvane.aetherhaven.debug.DebugLineCylinderUtil;
import com.hypixel.hytale.math.matrix.Matrix4dUtil;
import org.joml.Vector3d;
import org.joml.Vector3f;
import com.hypixel.hytale.protocol.DebugFlags;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.protocol.packets.player.ClearDebugShapes;
import com.hypixel.hytale.protocol.packets.player.DisplayDebug;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Matrix4d;

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
    /** Assembly frontier: filled cube tint, no wireframe edges (same basis as {@link #FLAG_SOLID_OVERLAY}). */
    public static final int FLAG_ASSEMBLY_FRONTIER = FLAG_SOLID_OVERLAY;
    private static final float PREVIEW_SECONDS = 0.35f;
    /**
     * Long lifetime for path-tool spline / gizmo packets so we can skip resending identical geometry every tick (reduces
     * flicker from {@link ClearDebugShapes}).
     */
    public static final float PATH_TOOL_DEBUG_HOLD_SECONDS = 48f;

    public static final Vector3f COLOR_KEYFRAME = new Vector3f(0.78f, 0.82f, 0.88f);
    public static final Vector3f COLOR_KEYFRAME_SEL = new Vector3f(1.0f, 0.72f, 0.12f);
    public static final Vector3f COLOR_KEYFRAME_LOOK = new Vector3f(0.45f, 0.78f, 1.0f);
    public static final Vector3f COLOR_PATH_EDGE = new Vector3f(0.95f, 0.95f, 0.98f);
    public static final Vector3f COLOR_TANGENT = new Vector3f(0.88f, 0.9f, 0.95f);

    private PathDebugPreviewUtil() {}

    public static void clear(@Nullable PlayerRef player) {
        if (player == null) {
            return;
        }
        player.getPacketHandler().write(new ClearDebugShapes());
    }

    /** Default path control node cube half-extent (world units). */
    public static final double PATH_CONTROL_NODE_CUBE_HALF = 0.16;

    /** Matches in-world debug cube placement vs anchor Y (path nodes use {@link PathToolInteractions#blockTopCenter}). */
    private static final double PATH_CONTROL_NODE_VISUAL_Y_EXTRA = 0.25;

    /**
     * Debug cube center Y for a path node anchor ({@link PathToolInteractions#blockTopCenter}). Spline lines use this so
     * segments pass through the visible node cubes.
     */
    public static double pathNodeDebugCenterY(double nodeAnchorY, double cubeHalf) {
        return nodeAnchorY + cubeHalf + PATH_CONTROL_NODE_VISUAL_Y_EXTRA;
    }

    public static double pathNodeDebugCenterY(double nodeAnchorY) {
        return pathNodeDebugCenterY(nodeAnchorY, PATH_CONTROL_NODE_CUBE_HALF);
    }

    @Nonnull
    public static Vector3d pathControlNodeLinePoint(@Nonnull Vector3d nodeAnchorPosition) {
        return new Vector3d(
            nodeAnchorPosition.x(),
            pathNodeDebugCenterY(nodeAnchorPosition.y()),
            nodeAnchorPosition.z()
        );
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
        drawPathControlNode(player, center, yawDeg, selected, false);
    }

    /**
     * Spline control node: cube marker and yaw handle.
     */
    public static void drawPathControlNode(
        @Nonnull PlayerRef player,
        @Nonnull Vector3d center,
        double yawDeg,
        boolean selected,
        boolean lookAtHighlight
    ) {
        Vector3f c;
        double half;
        if (selected) {
            c = COLOR_KEYFRAME_SEL;
            half = 0.2;
        } else if (lookAtHighlight) {
            c = COLOR_KEYFRAME_LOOK;
            half = 0.19;
        } else {
            c = COLOR_KEYFRAME;
            half = PATH_CONTROL_NODE_CUBE_HALF;
        }
        double cx = center.x();
        double cy = pathNodeDebugCenterY(center.y(), half);
        double cz = center.z();
        Matrix4d m = new Matrix4d();
        m.identity();
        m.translate(cx, cy, cz);
        m.scale(half, half, half);
        float opacity = selected ? 0.92f : lookAtHighlight ? 0.86f : 0.78f;
        add(player, DebugShape.Cube, m, c, opacity, FLAG_SOLID_OVERLAY, PATH_TOOL_DEBUG_HOLD_SECONDS);
        Vector3d f = PathSplineUtil.forwardHorizontal(yawDeg);
        double start = half * 0.85 + 0.02;
        double handleLen = 0.32;
        double anchorY = pathNodeDebugCenterY(center.y(), half);
        double ax = center.x() + f.x() * start;
        double ay = anchorY + f.y() * start;
        double az = center.z() + f.z() * start;
        double bx = center.x() + f.x() * (start + handleLen);
        double by = anchorY + f.y() * (start + handleLen);
        double bz = center.z() + f.z() * (start + handleLen);
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
        m.translate(center.x(), center.y(), center.z());
        m.scale(radius, radius, radius);
        add(player, DebugShape.Sphere, m, color, opacity, FLAG_MACHINIMA, PATH_TOOL_DEBUG_HOLD_SECONDS);
    }

    public static void drawLine(
        @Nonnull PlayerRef player, @Nonnull Vector3d a, @Nonnull Vector3d b, @Nonnull Vector3f color, double thickness
    ) {
        double dirX = b.x() - a.x();
        double dirY = b.y() - a.y();
        double dirZ = b.z() - a.z();
        double len = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        Matrix4d matrix = DebugLineCylinderUtil.segmentMatrix(
            a.x(), a.y(), a.z(), b.x(), b.y(), b.z(), thickness, len
        );
        if (matrix == null) {
            return;
        }
        add(player, DebugShape.Cylinder, matrix, color, 0.75f, FLAG_MACHINIMA, PATH_TOOL_DEBUG_HOLD_SECONDS);
    }

    /**
     * Ghost footprint sitting on the block top face (slightly above y+1) so it is not buried inside solid terrain.
     */
    private static final double NAV_NODE_CUBE_HALF = 0.22;
    private static final double NAV_NODE_CUBE_HALF_SELECTED = 0.28;
    private static final Vector3f COLOR_NAV_ENDPOINT = new Vector3f(0.98f, 0.82f, 0.2f);

    /**
     * Line endpoint Y for remove-mode nav previews ({@link #drawNavNodeCube}).
     */
    @Nonnull
    public static Vector3d navNodeLinePoint(double x, double y, double z, boolean pathSelected) {
        double half = pathSelected ? NAV_NODE_CUBE_HALF_SELECTED : NAV_NODE_CUBE_HALF;
        return new Vector3d(x, y + half, z);
    }

    /**
     * Small solid cube at a path nav waypoint (Remove mode and nav previews).
     */
    public static void drawNavNodeCube(
        @Nonnull PlayerRef player,
        double x,
        double y,
        double z,
        @Nonnull Vector3f color,
        boolean selected,
        boolean endpoint
    ) {
        Vector3f c = endpoint ? blendToward(color, COLOR_NAV_ENDPOINT, 0.35f) : color;
        double half = selected ? NAV_NODE_CUBE_HALF_SELECTED : NAV_NODE_CUBE_HALF;
        double cx = x;
        double cy = y + half;
        double cz = z;
        Matrix4d m = new Matrix4d();
        m.identity();
        m.translate(cx, cy, cz);
        m.scale(half, half, half);
        float opacity = selected ? 0.88f : 0.78f;
        add(player, DebugShape.Cube, m, c, opacity, FLAG_SOLID_OVERLAY, PATH_TOOL_DEBUG_HOLD_SECONDS);
    }

    private static Vector3f blendToward(@Nonnull Vector3f base, @Nonnull Vector3f toward, float t) {
        float u = Math.min(1f, Math.max(0f, t));
        return new Vector3f(
            base.x + (toward.x - base.x) * u,
            base.y + (toward.y - base.y) * u,
            base.z + (toward.z - base.z) * u
        );
    }

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

    /** Full-size assembly hint cube (axis half-extent), slightly larger than 1m like vanilla block footprint. */
    private static final double ASSEMBLY_CUBE_HALF_MAX = 0.5 * 1.06;
    /** Idle / start size: half block edge (half of full debug cube extent). */
    private static final double ASSEMBLY_CUBE_HALF_MIN = ASSEMBLY_CUBE_HALF_MAX * 0.5;

    /**
     * Assembly frontier cell: solid cube centered on the block; {@code grow01} 0 = half-block size, 1 = full block.
     */
    public static void drawAssemblyFrontierCellCube(
        @Nonnull PlayerRef pr,
        int x,
        int y,
        int z,
        @Nonnull Vector3f color,
        @Nonnull com.hypixel.hytale.server.core.universe.world.World w,
        double grow01
    ) {
        if (w.getChunkIfInMemory(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z)) == null) {
            return;
        }
        double g = Math.min(1.0, Math.max(0.0, grow01));
        double half = ASSEMBLY_CUBE_HALF_MIN + (ASSEMBLY_CUBE_HALF_MAX - ASSEMBLY_CUBE_HALF_MIN) * g;
        double cx = x + 0.5;
        double cy = y + 0.5;
        double cz = z + 0.5;
        Matrix4d m = new Matrix4d();
        m.identity();
        m.translate(cx, cy, cz);
        m.scale(half, half, half);
        float opacity = (float) (0.72 + 0.14 * g);
        add(pr, DebugShape.Cube, m, color, opacity, FLAG_ASSEMBLY_FRONTIER, PATH_TOOL_DEBUG_HOLD_SECONDS);
    }

    /** Path-tool blocked tint for assembly clearing markers. */
    public static final Vector3f COLOR_OBSTRUCTION_MARKER = new Vector3f(0.72f, 0.12f, 0.12f);

    private static final double OBSTRUCTION_NUB_HALF_MIN = 0.06;
    private static final double OBSTRUCTION_NUB_HALF_MAX = 0.11;
    private static final double OBSTRUCTION_NUB_OFFSET_MIN = 0.54;
    private static final double OBSTRUCTION_NUB_OFFSET_MAX = 0.62;

    /**
     * Six face nubs protruding outside the block on every side so markers stay visible on opaque terrain such as
     * {@code Soil_Grass} (not only transparent {@code Plant_Grass} foliage).
     */
    public static void drawObstructionCellMarkers(
        @Nonnull PlayerRef pr,
        int x,
        int y,
        int z,
        @Nonnull Vector3f color,
        @Nonnull com.hypixel.hytale.server.core.universe.world.World w,
        double grow01
    ) {
        if (w.getChunkIfInMemory(com.hypixel.hytale.math.util.ChunkUtil.indexChunkFromBlock(x, z)) == null) {
            return;
        }
        double g = Math.min(1.0, Math.max(0.0, grow01));
        double nubHalf = OBSTRUCTION_NUB_HALF_MIN + (OBSTRUCTION_NUB_HALF_MAX - OBSTRUCTION_NUB_HALF_MIN) * g;
        double offset = OBSTRUCTION_NUB_OFFSET_MIN + (OBSTRUCTION_NUB_OFFSET_MAX - OBSTRUCTION_NUB_OFFSET_MIN) * g;
        double cx = x + 0.5;
        double cy = y + 0.5;
        double cz = z + 0.5;
        float opacity = (float) (0.76 + 0.16 * g);
        drawObstructionNub(pr, cx + offset, cy, cz, nubHalf, color, opacity);
        drawObstructionNub(pr, cx - offset, cy, cz, nubHalf, color, opacity);
        drawObstructionNub(pr, cx, cy + offset, cz, nubHalf, color, opacity);
        drawObstructionNub(pr, cx, cy - offset, cz, nubHalf, color, opacity);
        drawObstructionNub(pr, cx, cy, cz + offset, nubHalf, color, opacity);
        drawObstructionNub(pr, cx, cy, cz - offset, nubHalf, color, opacity);
    }

    private static void drawObstructionNub(
        @Nonnull PlayerRef pr,
        double cx,
        double cy,
        double cz,
        double half,
        @Nonnull Vector3f color,
        float opacity
    ) {
        Matrix4d m = new Matrix4d();
        m.identity();
        m.translate(cx, cy, cz);
        m.scale(half, half, half);
        add(pr, DebugShape.Cube, m, color, opacity, FLAG_SOLID_OVERLAY, PATH_TOOL_DEBUG_HOLD_SECONDS);
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
        DisplayDebug p = new DisplayDebug(shape, Matrix4dUtil.asFloatData(matrix), color, lifetimeSeconds, (byte) flags, null, opacity);
        player.getPacketHandler().write(p);
    }
}
