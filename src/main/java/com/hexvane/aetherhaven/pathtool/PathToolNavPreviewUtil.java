package com.hexvane.aetherhaven.pathtool;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Nav polyline helpers for path-tool Remove preview. */
public final class PathToolNavPreviewUtil {
    private static final int LEGACY_UNDO_MAX_NODES = 48;

    private PathToolNavPreviewUtil() {}

    @Nonnull
    public static List<PathNavPoint> navPointsForPreview(@Nullable PathCommitRecord rec) {
        if (rec == null) {
            return List.of();
        }
        if (rec.navNodes != null && rec.navNodes.size() >= 2) {
            return rec.navNodes;
        }
        return legacyNavFromUndo(rec.undo);
    }

    @Nonnull
    private static List<PathNavPoint> legacyNavFromUndo(@Nullable List<PathToolUndoCell> undo) {
        if (undo == null || undo.isEmpty()) {
            return List.of();
        }
        List<PathToolUndoCell> cells = new ArrayList<>();
        for (PathToolUndoCell c : undo) {
            if (c != null) {
                cells.add(c);
            }
        }
        if (cells.size() < 2) {
            if (cells.isEmpty()) {
                return List.of();
            }
            PathToolUndoCell only = cells.get(0);
            return List.of(new PathNavPoint(only.x + 0.5, only.y + 0.5, only.z + 0.5));
        }
        cells.sort(
            (a, b) -> {
                int cmp = Integer.compare(a.z, b.z);
                if (cmp != 0) {
                    return cmp;
                }
                return Integer.compare(a.x, b.x);
            }
        );
        int n = cells.size();
        int step = Math.max(1, (n + LEGACY_UNDO_MAX_NODES - 1) / LEGACY_UNDO_MAX_NODES);
        List<PathNavPoint> out = new ArrayList<>();
        for (int i = 0; i < n; i += step) {
            PathToolUndoCell c = cells.get(i);
            out.add(new PathNavPoint(c.x + 0.5, c.y + 0.5, c.z + 0.5));
        }
        PathToolUndoCell last = cells.get(n - 1);
        PathNavPoint tail = new PathNavPoint(last.x + 0.5, last.y + 0.5, last.z + 0.5);
        if (out.isEmpty() || distanceSq(out.get(out.size() - 1), tail) > 0.25) {
            out.add(tail);
        }
        return out;
    }

    private static double distanceSq(@Nonnull PathNavPoint a, @Nonnull PathNavPoint b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }
}
