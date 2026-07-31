package com.hexvane.aetherhaven.plotcreator;

import javax.annotation.Nonnull;
import org.joml.Vector3i;

/** One axis-aligned face of the plot creator bounds box. */
public enum PlotCreatorBoundsFace {
    MIN_X,
    MAX_X,
    MIN_Y,
    MAX_Y,
    MIN_Z,
    MAX_Z;

    /** Unit outward normal for this face. */
    @Nonnull
    public Vector3i outwardNormal() {
        return switch (this) {
            case MIN_X -> new Vector3i(-1, 0, 0);
            case MAX_X -> new Vector3i(1, 0, 0);
            case MIN_Y -> new Vector3i(0, -1, 0);
            case MAX_Y -> new Vector3i(0, 1, 0);
            case MIN_Z -> new Vector3i(0, 0, -1);
            case MAX_Z -> new Vector3i(0, 0, 1);
        };
    }
}
