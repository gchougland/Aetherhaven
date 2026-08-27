package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownDissolutionService;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.town.WallSegmentRecord;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class WallPlacementRemoveService {
    private static final int BREAK_SETTINGS = 10;

    private WallPlacementRemoveService() {}

    /** Removes the plot sign block if it is still in the world (undo fallback). */
    public static void breakPlotSignAt(@Nonnull World world, int x, int y, int z) {
        ChunkSectionBlockUtil.breakBlock(world, x, y, z, BREAK_SETTINGS);
    }

    public static boolean removeWallPlot(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull UUID plotId,
        @Nonnull Store<EntityStore> entityStore
    ) {
        PlotInstance plot = town.findPlotById(plotId);
        if (plot == null) {
            return false;
        }
        ConstructionDefinition def = plugin.getConstructionCatalog().get(plot.getConstructionId());
        if (def == null || !def.isWallSegment()) {
            return false;
        }
        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        TownDissolutionService.clearPlotFromWorld(world, plugin, town, plot, entityStore, reg);
        town.removePlotInstance(plotId);
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        tm.updateTown(town);
        return true;
    }

    public static boolean removeWallSegment(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull UUID segmentId,
        @Nonnull Store<EntityStore> entityStore
    ) {
        WallSegmentRecord seg = town.findWallSegmentById(segmentId);
        if (seg == null) {
            return false;
        }
        if (town.getPlotInstances().stream().anyMatch(p -> p.getPlotId().equals(segmentId))) {
            PlotInstance p = town.findPlotById(segmentId);
            if (p != null) {
                return removeWallPlot(world, plugin, town, segmentId, entityStore);
            }
        }
        PoiRegistry reg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        PlotInstance pseudo =
            new PlotInstance(
                segmentId,
                seg.getConstructionId(),
                PlotInstanceState.COMPLETE,
                seg.toFootprint(),
                seg.getPrefabAnchorX(),
                seg.getPrefabAnchorY(),
                seg.getPrefabAnchorZ(),
                System.currentTimeMillis()
            );
        pseudo.setPrefabWorldPlacement(seg.getPrefabAnchorX(), seg.getPrefabAnchorY(), seg.getPrefabAnchorZ(), seg.resolvePrefabYaw());
        TownDissolutionService.clearPlotFromWorld(world, plugin, town, pseudo, entityStore, reg);
        town.removeWallSegment(segmentId);
        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        tm.updateTown(town);
        return true;
    }
}
