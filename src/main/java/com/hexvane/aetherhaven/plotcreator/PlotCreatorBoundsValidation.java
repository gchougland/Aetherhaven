package com.hexvane.aetherhaven.plotcreator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

final class PlotCreatorBoundsValidation {
    private PlotCreatorBoundsValidation() {}

    @Nullable
    static String validateMinMax(@Nonnull Vector3i min, @Nonnull Vector3i max) {
        if (max.x - min.x < PlotCreatorBoundsConstants.MIN_AXIS_SPAN
            || max.y - min.y < PlotCreatorBoundsConstants.MIN_AXIS_SPAN
            || max.z - min.z < PlotCreatorBoundsConstants.MIN_AXIS_SPAN) {
            return "boundsTooSmall";
        }
        if (max.y - min.y < PlotCreatorBoundsConstants.MIN_HEIGHT_SPAN) {
            return "boundsTooFlat";
        }
        return null;
    }

    static boolean initialDragLargeEnough(@Nullable Vector3i start, @Nullable Vector3i end) {
        if (start == null || end == null) {
            return false;
        }
        Vector3i min = min(start, end);
        Vector3i max = max(start, end);
        return max.x - min.x >= PlotCreatorBoundsConstants.MIN_INITIAL_DRAG_BLOCKS
            || max.y - min.y >= PlotCreatorBoundsConstants.MIN_INITIAL_DRAG_BLOCKS
            || max.z - min.z >= PlotCreatorBoundsConstants.MIN_INITIAL_DRAG_BLOCKS;
    }

    @Nonnull
    static Vector3i min(@Nonnull Vector3i a, @Nonnull Vector3i b) {
        return new Vector3i(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z));
    }

    @Nonnull
    static Vector3i max(@Nonnull Vector3i a, @Nonnull Vector3i b) {
        return new Vector3i(Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z));
    }

    static void commitCorners(@Nonnull PlotCreatorDraft draft, @Nonnull Vector3i min, @Nonnull Vector3i max) {
        draft.setCornerFirst(new Vector3i(min));
        draft.setCornerSecond(new Vector3i(max));
    }
}
