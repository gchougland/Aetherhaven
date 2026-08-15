package com.hexvane.aetherhaven.wall;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * The {@code wallPiece} block on a wall building. Connection points are prefab local cells: each one is the last solid
 * cell of the piece on that face, so two pieces join when their connection cells sit exactly one block apart along the
 * join direction. Only X and Z matter, which is what lets players raise and lower a piece freely.
 */
public final class WallPieceDefinition {
    @SerializedName("role")
    @Nullable
    private String role;

    @SerializedName("selectable")
    @Nullable
    private Boolean selectable;

    @SerializedName("boundsLocal")
    @Nullable
    private BoundsJson boundsLocal;

    @SerializedName("connections")
    @Nullable
    private List<ConnectionJson> connections;

    private transient boolean parsed;
    private transient WallPieceRole parsedRole;
    private transient EnumMap<WallCardinal, Vector3i> parsedConnections;
    private transient Vector3i parsedMin;
    private transient Vector3i parsedMax;

    /** Builds a definition directly (tests and the plot creator draft). */
    @Nonnull
    public static WallPieceDefinition of(
        @Nonnull WallPieceRole role,
        @Nonnull Vector3i boundsMinLocal,
        @Nonnull Vector3i boundsMaxLocal,
        @Nonnull Map<WallCardinal, Vector3i> connectionsLocal
    ) {
        WallPieceDefinition def = new WallPieceDefinition();
        def.parsed = true;
        def.parsedRole = role;
        def.parsedMin = new Vector3i(boundsMinLocal);
        def.parsedMax = new Vector3i(boundsMaxLocal);
        def.parsedConnections = new EnumMap<>(WallCardinal.class);
        for (Map.Entry<WallCardinal, Vector3i> e : connectionsLocal.entrySet()) {
            def.parsedConnections.put(e.getKey(), new Vector3i(e.getValue()));
        }
        def.selectable = Boolean.TRUE;
        return def;
    }

    /**
     * Copy shifted by a prefab local offset, used to rebase connection points from prefab space into plot sign space
     * when a building's {@code plotAnchorOffset} is not zero.
     */
    @Nonnull
    public WallPieceDefinition translatedLocal(int dx, int dz) {
        ensureParsed();
        if (dx == 0 && dz == 0) {
            return this;
        }
        WallPieceDefinition out = new WallPieceDefinition();
        out.parsed = true;
        out.parsedRole = parsedRole;
        out.selectable = selectable;
        out.parsedMin = new Vector3i(parsedMin.x + dx, parsedMin.y, parsedMin.z + dz);
        out.parsedMax = new Vector3i(parsedMax.x + dx, parsedMax.y, parsedMax.z + dz);
        out.parsedConnections = new EnumMap<>(WallCardinal.class);
        for (Map.Entry<WallCardinal, Vector3i> e : parsedConnections.entrySet()) {
            Vector3i v = e.getValue();
            out.parsedConnections.put(e.getKey(), new Vector3i(v.x + dx, v.y, v.z + dz));
        }
        return out;
    }

    /** True when this piece may be picked as the style's piece for its role. */
    public boolean isSelectable() {
        return selectable == null || selectable;
    }

    @Nullable
    public WallPieceRole role() {
        ensureParsed();
        return parsedRole;
    }

    /** Prefab local faces that carry a connection point. */
    @Nonnull
    public EnumSet<WallCardinal> localFaces() {
        ensureParsed();
        EnumSet<WallCardinal> out = EnumSet.noneOf(WallCardinal.class);
        out.addAll(parsedConnections.keySet());
        return out;
    }

    @Nullable
    public Vector3i localConnection(@Nonnull WallCardinal localFace) {
        ensureParsed();
        Vector3i v = parsedConnections.get(localFace);
        return v == null ? null : new Vector3i(v);
    }

    @Nonnull
    public Vector3i boundsMinLocal() {
        ensureParsed();
        return new Vector3i(parsedMin);
    }

    @Nonnull
    public Vector3i boundsMaxLocal() {
        ensureParsed();
        return new Vector3i(parsedMax);
    }

    public boolean isValid() {
        ensureParsed();
        return parsedRole != null && parsedConnections.size() == parsedRole.connectionCount();
    }

    /** World faces this piece opens onto at {@code rotationSteps}. */
    @Nonnull
    public EnumSet<WallCardinal> worldFaces(int rotationSteps) {
        ensureParsed();
        EnumSet<WallCardinal> out = EnumSet.noneOf(WallCardinal.class);
        for (WallCardinal localFace : parsedConnections.keySet()) {
            out.add(WallCardinal.rotated(localFace, rotationSteps));
        }
        return out;
    }

    /**
     * Offset from the piece anchor to its connection cell on {@code worldFace}, after rotating by
     * {@code rotationSteps}. Null when this piece has no connection on that world face.
     */
    @Nullable
    public Vector3i worldConnectionOffset(@Nonnull WallCardinal worldFace, int rotationSteps) {
        ensureParsed();
        for (Map.Entry<WallCardinal, Vector3i> e : parsedConnections.entrySet()) {
            if (WallCardinal.rotated(e.getKey(), rotationSteps) == worldFace) {
                return WallCardinal.rotateOffset(e.getValue(), rotationSteps);
            }
        }
        return null;
    }

    /** Rotations (0..3) whose world faces are exactly {@code requiredWorldFaces}. */
    @Nonnull
    public List<Integer> rotationsMatchingFaces(@Nonnull EnumSet<WallCardinal> requiredWorldFaces) {
        List<Integer> out = new ArrayList<>(4);
        for (int steps = 0; steps < 4; steps++) {
            if (worldFaces(steps).equals(requiredWorldFaces)) {
                out.add(steps);
            }
        }
        return out;
    }

    /** First rotation (0..3) that puts a connection on {@code worldFace}, or -1. */
    public int rotationWithFaceToward(@Nonnull WallCardinal worldFace) {
        for (int steps = 0; steps < 4; steps++) {
            if (worldFaces(steps).contains(worldFace)) {
                return steps;
            }
        }
        return -1;
    }

    /** World space bounds of the piece placed with its anchor at {@code anchor}. */
    @Nonnull
    public int[] worldBoundsXZ(@Nonnull Vector3i anchor, int rotationSteps) {
        ensureParsed();
        Vector3i a = WallCardinal.rotateOffset(new Vector3i(parsedMin.x, 0, parsedMin.z), rotationSteps);
        Vector3i b = WallCardinal.rotateOffset(new Vector3i(parsedMax.x, 0, parsedMax.z), rotationSteps);
        Vector3i c = WallCardinal.rotateOffset(new Vector3i(parsedMin.x, 0, parsedMax.z), rotationSteps);
        Vector3i d = WallCardinal.rotateOffset(new Vector3i(parsedMax.x, 0, parsedMin.z), rotationSteps);
        int minX = Math.min(Math.min(a.x, b.x), Math.min(c.x, d.x)) + anchor.x;
        int maxX = Math.max(Math.max(a.x, b.x), Math.max(c.x, d.x)) + anchor.x;
        int minZ = Math.min(Math.min(a.z, b.z), Math.min(c.z, d.z)) + anchor.z;
        int maxZ = Math.max(Math.max(a.z, b.z), Math.max(c.z, d.z)) + anchor.z;
        return new int[] {minX, minZ, maxX, maxZ};
    }

    private void ensureParsed() {
        if (parsed) {
            return;
        }
        parsedRole = WallPieceRole.fromSerialized(role);
        parsedConnections = new EnumMap<>(WallCardinal.class);
        if (connections != null) {
            for (ConnectionJson c : connections) {
                WallCardinal face = c.face();
                Vector3i local = c.local();
                if (face != null && local != null) {
                    parsedConnections.put(face, local);
                }
            }
        }
        parsedMin = boundsLocal != null ? boundsLocal.min() : new Vector3i(0, 0, 0);
        parsedMax = boundsLocal != null ? boundsLocal.max() : new Vector3i(0, 0, 0);
        parsed = true;
    }

    private static final class BoundsJson {
        @SerializedName("min")
        @Nullable
        private int[] min;

        @SerializedName("max")
        @Nullable
        private int[] max;

        @Nonnull
        Vector3i min() {
            return min != null && min.length == 3 ? new Vector3i(min[0], min[1], min[2]) : new Vector3i(0, 0, 0);
        }

        @Nonnull
        Vector3i max() {
            return max != null && max.length == 3 ? new Vector3i(max[0], max[1], max[2]) : new Vector3i(0, 0, 0);
        }
    }

    private static final class ConnectionJson {
        @SerializedName("face")
        @Nullable
        private String face;

        /** Prefab local cell on that face: {@code [x, z]}. */
        @SerializedName("local")
        @Nullable
        private int[] local;

        @Nullable
        WallCardinal face() {
            if (face == null || face.isBlank()) {
                return null;
            }
            for (WallCardinal dir : WallCardinal.values()) {
                if (dir.name().equalsIgnoreCase(face.trim())) {
                    return dir;
                }
            }
            return null;
        }

        @Nullable
        Vector3i local() {
            if (local == null) {
                return null;
            }
            if (local.length == 2) {
                return new Vector3i(local[0], 0, local[1]);
            }
            if (local.length == 3) {
                return new Vector3i(local[0], 0, local[2]);
            }
            return null;
        }
    }
}
