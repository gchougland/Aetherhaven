package com.hexvane.aetherhaven.town;

import javax.annotation.Nonnull;
import org.joml.Vector3d;

/** Resolves world position for Town Journal plot locate trails from town record data. */
public final class PlotLocateTargetResolver {
    private PlotLocateTargetResolver() {}

    public record PlotLocateTarget(@Nonnull Vector3d position, boolean valid) {
        public PlotLocateTarget {
            position = new Vector3d(position);
        }

        @Nonnull
        public static PlotLocateTarget invalid() {
            return new PlotLocateTarget(new Vector3d(), false);
        }
    }

    @Nonnull
    public static PlotLocateTarget resolve(@Nonnull PlotInstance plot) {
        PlotInstanceState state = plot.getState();
        if (state == PlotInstanceState.BLUEPRINTING) {
            return new PlotLocateTarget(
                new Vector3d(plot.getSignX() + 0.5, plot.getSignY() + 0.5, plot.getSignZ() + 0.5),
                true
            );
        }
        PlotFootprintRecord footprint = plot.toFootprint();
        return new PlotLocateTarget(
            new Vector3d(
                footprint.horizontalCenterX() + 0.5,
                plot.getSignY() + 0.5,
                footprint.horizontalCenterZ() + 0.5
            ),
            true
        );
    }
}
