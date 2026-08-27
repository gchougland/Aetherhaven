package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/** Tracks gizmo pivot position while the vanilla Point tool gizmo moves the hologram. */
final class PlacementGizmoPivot {
    private static final ConcurrentHashMap<UUID, Vector3d> LAST_CENTER = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Vector3d> DRAG_COMMITTED_CENTER = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Vector3d> DRAG_HOLOGRAM_BASE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Vector3i> LAST_WIREFRAME_SHIFT = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Vector3i> LAST_OVERLAY_FLOOR = new ConcurrentHashMap<>();

    private PlacementGizmoPivot() {}

    @Nonnull
    static Vector3d centerOf(@Nonnull PlotFootprintRecord footprint) {
        return new Vector3d(
            (footprint.getMinX() + footprint.getMaxX() + 1) * 0.5,
            (footprint.getMinY() + footprint.getMaxY() + 1) * 0.5,
            (footprint.getMinZ() + footprint.getMaxZ() + 1) * 0.5
        );
    }

    static void remember(@Nonnull UUID playerUuid, @Nonnull Vector3d center) {
        LAST_CENTER.put(playerUuid, new Vector3d(center));
    }

    @Nullable
    static Vector3d remembered(@Nonnull UUID playerUuid) {
        Vector3d center = LAST_CENTER.get(playerUuid);
        return center == null ? null : new Vector3d(center);
    }

    static void forget(@Nonnull UUID playerUuid) {
        LAST_CENTER.remove(playerUuid);
        DRAG_COMMITTED_CENTER.remove(playerUuid);
        DRAG_HOLOGRAM_BASE.remove(playerUuid);
        LAST_WIREFRAME_SHIFT.remove(playerUuid);
        LAST_OVERLAY_FLOOR.remove(playerUuid);
    }

    static boolean isDragging(@Nonnull UUID playerUuid) {
        return DRAG_COMMITTED_CENTER.containsKey(playerUuid);
    }

    static void beginDrag(
        @Nonnull UUID playerUuid,
        @Nonnull Vector3d committedCenter,
        @Nullable Vector3d hologramBase
    ) {
        DRAG_COMMITTED_CENTER.put(playerUuid, new Vector3d(committedCenter));
        LAST_WIREFRAME_SHIFT.put(playerUuid, new Vector3i());
        if (hologramBase != null) {
            DRAG_HOLOGRAM_BASE.put(playerUuid, new Vector3d(hologramBase));
            LAST_OVERLAY_FLOOR.put(
                playerUuid,
                new Vector3i((int) Math.floor(hologramBase.x), (int) Math.floor(hologramBase.y), (int) Math.floor(hologramBase.z))
            );
        } else {
            DRAG_HOLOGRAM_BASE.remove(playerUuid);
            LAST_OVERLAY_FLOOR.remove(playerUuid);
        }
    }

    @Nullable
    static Vector3d dragCommittedCenter(@Nonnull UUID playerUuid) {
        Vector3d center = DRAG_COMMITTED_CENTER.get(playerUuid);
        return center == null ? null : new Vector3d(center);
    }

    @Nullable
    static Vector3d dragHologramBase(@Nonnull UUID playerUuid) {
        Vector3d base = DRAG_HOLOGRAM_BASE.get(playerUuid);
        return base == null ? null : new Vector3d(base);
    }

    static void endDrag(@Nonnull UUID playerUuid) {
        DRAG_COMMITTED_CENTER.remove(playerUuid);
        DRAG_HOLOGRAM_BASE.remove(playerUuid);
        LAST_WIREFRAME_SHIFT.remove(playerUuid);
        LAST_OVERLAY_FLOOR.remove(playerUuid);
    }

    static boolean takeWireframeShiftIfChanged(@Nonnull UUID playerUuid, int dx, int dy, int dz) {
        Vector3i shift = new Vector3i(dx, dy, dz);
        Vector3i previous = LAST_WIREFRAME_SHIFT.get(playerUuid);
        if (previous != null && previous.equals(shift)) {
            return false;
        }
        LAST_WIREFRAME_SHIFT.put(playerUuid, shift);
        return true;
    }

    static boolean takeOverlayFloorIfChanged(@Nonnull UUID playerUuid, @Nonnull Vector3i floor) {
        Vector3i previous = LAST_OVERLAY_FLOOR.get(playerUuid);
        if (previous != null && previous.equals(floor)) {
            return false;
        }
        LAST_OVERLAY_FLOOR.put(playerUuid, new Vector3i(floor));
        return true;
    }
}
