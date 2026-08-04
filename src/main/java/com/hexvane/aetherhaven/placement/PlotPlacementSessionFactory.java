package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.plot.PlotTokenPlacementOption;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Creates plot placement sessions from inventory token options. */
public final class PlotPlacementSessionFactory {
    private PlotPlacementSessionFactory() {}

    @Nullable
    public static PlotPlacementSession createFromOption(
        @Nonnull World world,
        @Nonnull Vector3i anchor,
        @Nonnull PlotTokenPlacementOption option,
        @Nonnull AetherhavenPlugin plugin
    ) {
        if (option.isMovePlot()) {
            UUID plotId = option.getMovePlotId();
            if (plotId == null) {
                return null;
            }
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            TownRecord town = tm.findTownOwningPlot(plotId);
            if (town == null) {
                return null;
            }
            PlotInstance plot = town.findPlotById(plotId);
            if (plot == null || plot.getState() != PlotInstanceState.COMPLETE) {
                return null;
            }
            String constructionId = plot.getConstructionId();
            if (constructionId == null || constructionId.isBlank()) {
                constructionId = option.getConstructionId();
            }
            int steps = PlotPlacementSession.rotationStepsFromPrefabYaw(plot.resolvePrefabYaw());
            PlotPlacementSession session =
                PlotPlacementSession.forRelocatingPlot(world, anchor, steps, constructionId, plotId);
            session.setMoveViaToken(true);
            return session;
        }
        return new PlotPlacementSession(world, anchor, 0, option.getConstructionId());
    }
}
