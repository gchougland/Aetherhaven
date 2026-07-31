package com.hexvane.aetherhaven.plotcreator;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Resizes committed bounds by dragging one face along its axis. */
final class PlotCreatorBoundsFaceDrag {
    private PlotCreatorBoundsFaceDrag() {}

    /**
     * @return validation error key when the drag would make the box invalid, else {@code null}
     */
    @Nullable
    static String apply(
        @Nonnull PlotCreatorDraft draft,
        @Nonnull PlotCreatorBoundsFace face,
        @Nonnull Vector3i aimCell
    ) {
        if (draft.getCornerFirst() == null || draft.getCornerSecond() == null) {
            return "boundsTooSmall";
        }
        Vector3i min = draft.boundsMin();
        Vector3i max = draft.boundsMax();
        Vector3i newMin = new Vector3i(min);
        Vector3i newMax = new Vector3i(max);
        switch (face) {
            case MIN_X -> newMin.x = Math.min(aimCell.x, max.x);
            case MAX_X -> newMax.x = Math.max(aimCell.x, min.x);
            case MIN_Y -> newMin.y = Math.min(aimCell.y, max.y);
            case MAX_Y -> newMax.y = Math.max(aimCell.y, min.y);
            case MIN_Z -> newMin.z = Math.min(aimCell.z, max.z);
            case MAX_Z -> newMax.z = Math.max(aimCell.z, min.z);
        }
        String err = PlotCreatorBoundsValidation.validateMinMax(newMin, newMax);
        if (err != null) {
            return err;
        }
        PlotCreatorBoundsValidation.commitCorners(draft, newMin, newMax);
        return null;
    }
}
