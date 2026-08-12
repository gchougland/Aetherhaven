package com.hexvane.aetherhaven.prop;

import com.hexvane.aetherhaven.placement.PlotPlacementWireframeOverlay;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

/**
 * Prop placement outline: same clear-then-edge wireframe path as plot placement, so nudging does not leave stale
 * boxes behind.
 */
public final class PropPlacementWireframeOverlay {
    private PropPlacementWireframeOverlay() {}

    public static void clearFor(@Nonnull PlayerRef player) {
        PlotPlacementWireframeOverlay.clearFor(player);
    }

    public static void send(@Nonnull PlayerRef player, @Nonnull PlotFootprintRecord footprint, boolean placementValid) {
        // Clears first, then draws edge cylinders (no solid cubes that stack while moving).
        PlotPlacementWireframeOverlay.send(player, footprint, placementValid, null);
    }
}
