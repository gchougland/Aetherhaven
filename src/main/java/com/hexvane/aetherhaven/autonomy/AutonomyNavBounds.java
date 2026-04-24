package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.server.core.universe.world.World;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Vertical range for “feet in air” stand Y when a POI/commute target lies inside a placed building footprint. Cuts off
 * walkable {@link com.hexvane.aetherhaven.construction.ConstructionDefinition#getAutonomyNavRoofExclusionYBelowMaxY roof}
 * and ties floors to {@link com.hexvane.aetherhaven.town.PlotInstance#toFootprint() plot AABB}.
 */
public final class AutonomyNavBounds {
    public record NavVerticalRange(int minFeetY, int maxFeetY) {
        public boolean isUsable() {
            return minFeetY <= maxFeetY;
        }
    }

    private AutonomyNavBounds() {}

    public static boolean isColumnInFootprintHorizontally(int bx, int bz, @Nonnull PlotFootprintRecord fp) {
        return bx >= fp.getMinX() && bx <= fp.getMaxX() && bz >= fp.getMinZ() && bz <= fp.getMaxZ();
    }

    @Nullable
    public static NavVerticalRange rangeForPlotFootprint(
        @Nonnull PlotFootprintRecord fp,
        @Nullable ConstructionDefinition def
    ) {
        int floorY = fp.getMinY() + (def != null ? def.getAutonomyNavFloorYAboveMinY() : 0);
        int roofEx = def != null ? def.getAutonomyNavRoofExclusionYBelowMaxY() : 1;
        int span = def != null ? def.getAutonomyNavMaxStandYSpanAboveMinY() : 32;
        int fromSpan = fp.getMinY() + span;
        int fromRoof = fp.getMaxY() - roofEx;
        int maxY = Math.min(fromSpan, fromRoof);
        if (maxY < floorY) {
            return null;
        }
        return new NavVerticalRange(floorY, maxY);
    }

    /**
     * If the POI is tied to a plot and the stand column lies in that plot’s horizontal AABB, returns a vertical cap;
     * otherwise null (heuristic POI-block-only nav applies).
     */
    @Nullable
    public static NavVerticalRange tryRangeForPoi(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull PoiEntry pick,
        int standColumnX,
        int standColumnZ
    ) {
        if (pick.getPlotId() == null) {
            return null;
        }
        PlotInstance plot = town.findPlotById(pick.getPlotId());
        if (plot == null) {
            return null;
        }
        PlotFootprintRecord fp = plot.toFootprint();
        if (!isColumnInFootprintHorizontally(standColumnX, standColumnZ, fp)) {
            return null;
        }
        String cid = plot.getConstructionId();
        ConstructionDefinition def = cid != null && !cid.isEmpty() ? plugin.getConstructionCatalog().get(cid) : null;
        return rangeForPlotFootprint(fp, def);
    }

    /** Integer feet block Y (air) for leash when a POI has no interaction target; 0.02 is added by callers. */
    public static int standBlockYForPoiWithoutTarget(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nullable TownRecord town,
        @Nonnull PoiEntry pick,
        int columnX,
        int columnZ,
        int npcFeetY
    ) {
        NavVerticalRange r = town != null ? tryRangeForPoi(plugin, town, pick, columnX, columnZ) : null;
        return VillagerBlockUtil.findStandYForNav(world, columnX, columnZ, pick.getY(), npcFeetY, r);
    }
}
