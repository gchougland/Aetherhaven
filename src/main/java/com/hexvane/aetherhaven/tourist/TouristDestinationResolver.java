package com.hexvane.aetherhaven.tourist;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.AutonomyNavBounds;
import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class TouristDestinationResolver {
    private static final int PLOT_EDGE_PADDING = 2;
    /** When inn/town hall exist, they are picked this often; other {@code touristDestination} plots share the rest. */
    private static final int CIVIC_PLOT_VISIT_WEIGHT_PERCENT = 30;

    private TouristDestinationResolver() {}

    @Nullable
    public static TouristPlotVisit pickVisitPlot(
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull World world,
        @Nullable UUID excludePlotId,
        @Nonnull Random random
    ) {
        List<TouristPlotVisit> candidates = listVisitPlots(town, catalog, world);
        if (candidates.isEmpty()) {
            return null;
        }
        List<TouristPlotVisit> pool = new ArrayList<>();
        for (TouristPlotVisit plot : candidates) {
            if (excludePlotId == null || !excludePlotId.equals(plot.plotId())) {
                pool.add(plot);
            }
        }
        if (pool.isEmpty()) {
            pool = candidates;
        }
        List<TouristPlotVisit> civic = new ArrayList<>();
        for (TouristPlotVisit plot : pool) {
            if (isPreferredPlot(town, catalog, plot.plotId())) {
                civic.add(plot);
            }
        }
        if (!civic.isEmpty() && random.nextInt(100) < CIVIC_PLOT_VISIT_WEIGHT_PERCENT) {
            return civic.get(random.nextInt(civic.size()));
        }
        return pool.get(random.nextInt(pool.size()));
    }

    @Nonnull
    public static List<TouristPlotVisit> listVisitPlots(
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull World world
    ) {
        List<TouristPlotVisit> out = new ArrayList<>();
        for (PlotInstance plot : town.getPlotInstances()) {
            if (plot.getState() != PlotInstanceState.COMPLETE) {
                continue;
            }
            UUID plotId = plot.getPlotId();
            if (plotId == null) {
                continue;
            }
            ConstructionDefinition def = catalog.get(plot.getConstructionId());
            if (def == null || !def.isTouristDestination()) {
                continue;
            }
            double[] entry = resolvePlotEntryPosition(world, plot, def);
            if (entry == null) {
                continue;
            }
            out.add(TouristPlotVisit.of(plotId, entry[0], entry[1], entry[2]));
        }
        return out;
    }

    @Nullable
    public static PoiEntry pickVisitPoiOnPlot(
        @Nonnull TownRecord town,
        @Nonnull PoiRegistry poiRegistry,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull UUID plotId,
        @Nullable UUID excludePoiId,
        @Nonnull Random random,
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        int npcFeetY
    ) {
        List<PoiEntry> candidates = listVisitPoisOnPlot(town, poiRegistry, catalog, plotId, world, plugin, npcFeetY);
        if (candidates.isEmpty()) {
            return null;
        }
        List<PoiEntry> pool = new ArrayList<>();
        for (PoiEntry poi : candidates) {
            if (excludePoiId == null || !excludePoiId.equals(poi.getId())) {
                pool.add(poi);
            }
        }
        if (pool.isEmpty()) {
            pool = candidates;
        }
        return pool.get(random.nextInt(pool.size()));
    }

    @Nonnull
    public static List<PoiEntry> listVisitPoisOnPlot(
        @Nonnull TownRecord town,
        @Nonnull PoiRegistry poiRegistry,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull UUID plotId
    ) {
        return listVisitPoisOnPlot(town, poiRegistry, catalog, plotId, null, null, Integer.MIN_VALUE);
    }

    @Nonnull
    public static List<PoiEntry> listVisitPoisOnPlot(
        @Nonnull TownRecord town,
        @Nonnull PoiRegistry poiRegistry,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull UUID plotId,
        @Nullable World world,
        @Nullable AetherhavenPlugin plugin,
        int npcFeetY
    ) {
        List<PoiEntry> out = new ArrayList<>();
        for (PoiEntry poi : poiRegistry.allEntries()) {
            if (!town.getTownId().equals(poi.getTownId())) {
                continue;
            }
            if (!plotId.equals(poi.getPlotId())) {
                continue;
            }
            if (!isUsableVisitPoi(poi, town, catalog)) {
                continue;
            }
            if (world != null && plugin != null && !isReachableVisitPoi(world, plugin, town, poi, npcFeetY)) {
                continue;
            }
            out.add(poi);
        }
        return out;
    }

    /** True when the POI interaction stand column is walkable for an NPC at {@code npcFeetY}. */
    public static boolean isReachableVisitPoi(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull PoiEntry poi,
        int npcFeetY
    ) {
        int columnX;
        int columnZ;
        int poiBlockY;
        if (poi.hasInteractionTarget()) {
            Double tx = poi.getInteractionTargetX();
            Double ty = poi.getInteractionTargetY();
            Double tz = poi.getInteractionTargetZ();
            if (tx == null || ty == null || tz == null) {
                return false;
            }
            columnX = tx.intValue();
            columnZ = tz.intValue();
            poiBlockY = ty.intValue();
        } else {
            columnX = poi.getX();
            columnZ = poi.getZ();
            poiBlockY = poi.getY();
        }
        int feetYHint = npcFeetY != Integer.MIN_VALUE ? npcFeetY : poiBlockY;
        AutonomyNavBounds.NavVerticalRange range =
            AutonomyNavBounds.tryRangeForPoi(plugin, town, poi, columnX, columnZ);
        int standY = VillagerBlockUtil.findStandYForNav(world, columnX, columnZ, poiBlockY, feetYHint, range);
        if (standY == Integer.MIN_VALUE) {
            return false;
        }
        return VillagerBlockUtil.isNpcStandColumn(world, columnX, standY, columnZ);
    }

    public static boolean isInsidePlotFootprint(double x, double z, @Nonnull PlotInstance plot, int padding) {
        PlotFootprintRecord fp = plot.toFootprint();
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        return bx >= fp.getMinX() - padding
            && bx <= fp.getMaxX() + padding
            && bz >= fp.getMinZ() - padding
            && bz <= fp.getMaxZ() + padding;
    }

    public static int plotEdgePadding() {
        return PLOT_EDGE_PADDING;
    }

    @Nullable
    public static PlotInstance findVisitPlot(@Nonnull TownRecord town, @Nullable UUID plotId) {
        if (plotId == null) {
            return null;
        }
        PlotInstance plot = town.findPlotById(plotId);
        if (plot == null || plot.getState() != PlotInstanceState.COMPLETE) {
            return null;
        }
        return plot;
    }

    public static boolean isPreferredVisitPlot(
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull UUID plotId
    ) {
        return isPreferredPlot(town, catalog, plotId);
    }

    /** Shop plots: tourists browse shop spots only (no POI wandering inside the building). */
    public static boolean isTouristShopPlot(
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull UUID plotId
    ) {
        PlotInstance plot = findVisitPlot(town, plotId);
        if (plot == null) {
            return false;
        }
        ConstructionDefinition def = catalog.get(plot.getConstructionId());
        return def != null && def.getBuildingTags().contains("shop");
    }

    private static boolean isPreferredPlot(
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull UUID plotId
    ) {
        PlotInstance plot = town.findPlotById(plotId);
        if (plot == null) {
            return false;
        }
        ConstructionDefinition def = catalog.get(plot.getConstructionId());
        if (def == null) {
            return false;
        }
        String gid = def.getGameplayConstructionId();
        return AetherhavenConstants.CONSTRUCTION_PLOT_INN.equals(gid)
            || AetherhavenConstants.CONSTRUCTION_PLOT_TOWN_HALL.equals(gid);
    }

    @Nullable
    private static double[] resolvePlotEntryPosition(
        @Nonnull World world,
        @Nonnull PlotInstance plot,
        @Nonnull ConstructionDefinition def
    ) {
        Vector3i anchor = plot.resolvePrefabAnchorWorld(def);
        Rotation yaw = plot.resolvePrefabYaw();

        int[][] visitorLocals = def.getVisitorSpawnLocals();
        if (visitorLocals != null && visitorLocals.length > 0) {
            int[] local = visitorLocals[0];
            if (local != null && local.length >= 3) {
                return standFromLocal(world, anchor, yaw, local[0], local[1], local[2]);
            }
        }

        int[] management = def.getManagementBlockLocalPos();
        if (management != null && management.length >= 3) {
            return standFromLocal(world, anchor, yaw, management[0], management[1], management[2] + 1);
        }

        PlotFootprintRecord footprint = plot.toFootprint();
        int cx = (footprint.getMinX() + footprint.getMaxX()) / 2;
        int cz = (footprint.getMinZ() + footprint.getMaxZ()) / 2;
        int standY = VillagerBlockUtil.findStandY(world, cx, cz, footprint.getMinY() + 3);
        double y = standY != Integer.MIN_VALUE ? standY + 0.02 : footprint.getMinY() + 1.02;
        return new double[] {cx + 0.5, y, cz + 0.5};
    }

    @Nullable
    private static double[] standFromLocal(
        @Nonnull World world,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw,
        int localX,
        int localY,
        int localZ
    ) {
        Vector3i delta = PrefabLocalOffset.rotate(yaw, localX, localY, localZ);
        int wx = anchor.x + delta.x;
        int wy = anchor.y + delta.y;
        int wz = anchor.z + delta.z;
        int standY = VillagerBlockUtil.findStandY(world, wx, wz, wy + 3);
        double y = standY != Integer.MIN_VALUE ? standY + 0.02 : wy + 0.02;
        return new double[] {wx + 0.5, y, wz + 0.5};
    }

    /** POIs a tourist may walk to while visiting a plot (never required to visit the plot itself). */
    private static boolean isUsableVisitPoi(
        @Nonnull PoiEntry poi,
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog catalog
    ) {
        UUID plotId = poi.getPlotId();
        if (plotId == null) {
            return false;
        }
        PlotInstance plot = town.findPlotById(plotId);
        if (plot == null || plot.getState() != PlotInstanceState.COMPLETE) {
            return false;
        }
        ConstructionDefinition def = catalog.get(plot.getConstructionId());
        if (def == null || !def.isTouristDestination()) {
            return false;
        }
        if (isTouristShopPlot(town, catalog, plotId)) {
            return false;
        }
        if (poi.getTags().contains("SLEEP") && !poi.getTags().contains("EAT") && !poi.getTags().contains("FUN")) {
            return false;
        }
        if (poi.getTags().contains(AetherhavenConstants.POI_TAG_TOURIST_VISIT)) {
            return true;
        }
        if (poi.getTags().contains("FUN") || poi.getTags().contains("SIT") || poi.getTags().contains("EAT")) {
            return true;
        }
        if (poi.getTags().contains("WORK")) {
            return true;
        }
        return poi.hasInteractionTarget();
    }
}
