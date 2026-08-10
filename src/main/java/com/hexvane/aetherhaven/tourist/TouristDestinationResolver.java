package com.hexvane.aetherhaven.tourist;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.autonomy.AutonomyNavBounds;
import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hexvane.aetherhaven.poi.BuildingPoisDefinition;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiOccupancy;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class TouristDestinationResolver {
    private static final int PLOT_EDGE_PADDING = 2;
    /** When inn/town hall exist, they are picked this often; other {@code touristDestination} plots share the rest. */
    private static final int CIVIC_PLOT_VISIT_WEIGHT_PERCENT = 30;
    /** Prefer the player's shop over other destinations when one exists. */
    private static final int PLAYER_SHOP_VISIT_WEIGHT_PERCENT = 40;
    /** While a festival is on, most visitors head for the square. */
    private static final int FESTIVAL_PLOT_VISIT_WEIGHT_PERCENT = 65;

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
        TouristPlotVisit festival = findFestivalVisit(town, pool);
        if (festival != null && random.nextInt(100) < FESTIVAL_PLOT_VISIT_WEIGHT_PERCENT) {
            return festival;
        }
        List<TouristPlotVisit> playerShops = new ArrayList<>();
        List<TouristPlotVisit> civic = new ArrayList<>();
        for (TouristPlotVisit plot : pool) {
            if (isPlayerShopPlot(town, catalog, plot.plotId())) {
                playerShops.add(plot);
            } else if (isPreferredPlot(town, catalog, plot.plotId())) {
                civic.add(plot);
            }
        }
        if (!playerShops.isEmpty() && random.nextInt(100) < PLAYER_SHOP_VISIT_WEIGHT_PERCENT) {
            return playerShops.get(random.nextInt(playerShops.size()));
        }
        if (!civic.isEmpty() && random.nextInt(100) < CIVIC_PLOT_VISIT_WEIGHT_PERCENT) {
            return civic.get(random.nextInt(civic.size()));
        }
        return pool.get(random.nextInt(pool.size()));
    }

    /** The festival square while a festival is running, when it is one of the plots a tourist could visit. */
    @Nullable
    private static TouristPlotVisit findFestivalVisit(
        @Nonnull TownRecord town,
        @Nonnull List<TouristPlotVisit> pool
    ) {
        if (town.getActiveFestivalId() == null) {
            return null;
        }
        UUID festivalPlotId = town.getActiveFestivalPlotId();
        if (festivalPlotId == null) {
            return null;
        }
        for (TouristPlotVisit plot : pool) {
            if (festivalPlotId.equals(plot.plotId())) {
                return plot;
            }
        }
        return null;
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
        return pickVisitPoiOnPlot(town, poiRegistry, catalog, plotId, excludePoiId, random, world, plugin, npcFeetY, null, null);
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
        int npcFeetY,
        @Nullable Store<EntityStore> store,
        @Nullable Map<String, Integer> occupancy
    ) {
        List<PoiEntry> candidates = listVisitPoisOnPlot(town, poiRegistry, catalog, plotId, world, plugin, npcFeetY);
        if (candidates.isEmpty()) {
            return null;
        }
        List<PoiEntry> pool = new ArrayList<>();
        for (PoiEntry poi : candidates) {
            if (excludePoiId != null && excludePoiId.equals(poi.getId())) {
                continue;
            }
            if (occupancy != null && !PoiOccupancy.isCellAvailable(occupancy, PoiOccupancy.standCellKey(poi), poi.getCapacity())) {
                continue;
            }
            if (store != null
                && !PoiOccupancy.canBeginUse(store, town.getTownId(), poiRegistry, poi, null)) {
                continue;
            }
            pool.add(poi);
        }
        if (pool.isEmpty()) {
            return null;
        }
        PoiEntry pick = pool.get(random.nextInt(pool.size()));
        if (occupancy != null) {
            PoiOccupancy.tryClaimStand(occupancy, pick);
        }
        return pick;
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
        List<PoiEntry> festivalTouristStands = new ArrayList<>();
        boolean festivalPlot =
            town.getActiveFestivalId() != null && plotId.equals(town.getActiveFestivalPlotId());
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
            if (festivalPlot
                && poi.getTags().contains(AetherhavenConstants.POI_TAG_FESTIVAL_EPHEMERAL)
                && poi.getTags().contains(AetherhavenConstants.POI_TAG_TOURIST_VISIT)) {
                festivalTouristStands.add(poi);
            }
            out.add(poi);
        }
        // During a festival on this plot, only use the festival's tourist stands (never everyday mid-square spots).
        if (festivalPlot) {
            return festivalTouristStands;
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
            columnX = (int) Math.floor(tx);
            columnZ = (int) Math.floor(tz);
            poiBlockY = (int) Math.floor(ty);
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

    /** Shop plots: tourists browse shop spots (and stand at tourist visit spots), not merchant WORK desks. */
    public static boolean isTouristShopPlot(
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull UUID plotId
    ) {
        // Player shops must count even if an older building JSON omitted the shop tag.
        if (isPlayerShopPlot(town, catalog, plotId)) {
            return true;
        }
        PlotInstance plot = findVisitPlot(town, plotId);
        if (plot == null) {
            return false;
        }
        ConstructionDefinition def = catalog.get(plot.getConstructionId());
        return def != null && def.getBuildingTags().contains("shop");
    }

    public static boolean isPlayerShopPlot(
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog catalog,
        @Nonnull UUID plotId
    ) {
        PlotInstance plot = town.findPlotById(plotId);
        if (plot == null) {
            return false;
        }
        String gid = catalog.resolveGameplayConstructionId(plot.getConstructionId());
        return AetherhavenConstants.CONSTRUCTION_PLOT_PLAYER_SHOP.equals(gid);
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

    @Nonnull
    private static double[] resolvePlotEntryPosition(
        @Nonnull World world,
        @Nonnull PlotInstance plot,
        @Nonnull ConstructionDefinition def
    ) {
        Vector3i anchor = plot.resolvePrefabAnchorWorld(def);
        Rotation yaw = plot.resolvePrefabYaw();

        // Customer-facing tourist spots first — management/center dumps shoppers behind counters.
        double[] touristEntry = firstTouristVisitStandFromDef(world, anchor, yaw, def);
        if (touristEntry != null && isInsidePlotFootprint(touristEntry[0], touristEntry[2], plot, plotEdgePadding())) {
            return touristEntry;
        }

        int[][] visitorLocals = def.getVisitorSpawnLocals();
        if (visitorLocals != null && visitorLocals.length > 0) {
            int[] local = visitorLocals[0];
            if (local != null && local.length >= 3) {
                double[] visitor = standFromLocal(world, anchor, yaw, local[0], local[1], local[2]);
                if (isInsidePlotFootprint(visitor[0], visitor[2], plot, plotEdgePadding())) {
                    return visitor;
                }
            }
        }

        int[] management = def.getManagementBlockLocalPos();
        if (management != null && management.length >= 3) {
            double[] managementStand =
                standFromLocal(world, anchor, yaw, management[0], management[1], management[2] + 1);
            if (isInsidePlotFootprint(managementStand[0], managementStand[2], plot, plotEdgePadding())) {
                return managementStand;
            }
        }

        return footprintCenterStand(world, plot);
    }

    @Nonnull
    private static double[] footprintCenterStand(@Nonnull World world, @Nonnull PlotInstance plot) {
        PlotFootprintRecord footprint = plot.toFootprint();
        int cx = (footprint.getMinX() + footprint.getMaxX()) / 2;
        int cz = (footprint.getMinZ() + footprint.getMaxZ()) / 2;
        int standY = VillagerBlockUtil.findStandY(world, cx, cz, footprint.getMinY() + 3);
        double y = standY != Integer.MIN_VALUE ? standY + 0.02 : footprint.getMinY() + 1.02;
        return new double[] {cx + 0.5, y, cz + 0.5};
    }

    @Nullable
    private static double[] firstTouristVisitStandFromDef(
        @Nonnull World world,
        @Nonnull Vector3i anchor,
        @Nonnull Rotation yaw,
        @Nonnull ConstructionDefinition def
    ) {
        for (BuildingPoisDefinition.PoiRow row : def.getPois()) {
            if (row == null || !row.getTags().contains(AetherhavenConstants.POI_TAG_TOURIST_VISIT)) {
                continue;
            }
            int lx;
            int ly;
            int lz;
            if (row.hasInteractionTargetLocal()) {
                lx = row.getInteractionTargetLocalX();
                ly = row.getInteractionTargetLocalY();
                lz = row.getInteractionTargetLocalZ();
            } else {
                lx = row.getLocalX();
                ly = row.getLocalY();
                lz = row.getLocalZ();
            }
            double[] stand = standFromLocal(world, anchor, yaw, lx, ly, lz);
            if (stand != null) {
                return stand;
            }
        }
        return null;
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
        // Festival villager markers are off limits; festival tourist stands are for visitors.
        if (poi.getTags().contains(AetherhavenConstants.POI_TAG_FESTIVAL_EPHEMERAL)) {
            return poi.getTags().contains(AetherhavenConstants.POI_TAG_TOURIST_VISIT);
        }
        // Shops: only customer tourist stands — never merchant WORK desks behind the counter.
        if (isTouristShopPlot(town, catalog, plotId)) {
            return poi.getTags().contains(AetherhavenConstants.POI_TAG_TOURIST_VISIT);
        }
        // Merchant work desks are never tourist destinations (even if the plot lacks the shop tag).
        if (poi.getTags().contains("SHOP") && poi.getTags().contains("WORK")) {
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
