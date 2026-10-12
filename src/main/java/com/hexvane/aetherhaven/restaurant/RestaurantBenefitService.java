package com.hexvane.aetherhaven.restaurant;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownRecord;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Resolves restaurant upgrade benefits for a town or POI. */
public final class RestaurantBenefitService {

    private RestaurantBenefitService() {}

    public static boolean townHasCompleteRestaurant(
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog catalog
    ) {
        return town.findCompletePlotWithConstruction(catalog, AetherhavenConstants.CONSTRUCTION_PLOT_RESTAURANT) != null;
    }

    /** Town wide hunger decay scale from the restaurant's satiety tier (1.0 when none). */
    public static float satietyDecayMultiplier(
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog catalog
    ) {
        PlotInstance plot = town.findCompletePlotWithConstruction(catalog, AetherhavenConstants.CONSTRUCTION_PLOT_RESTAURANT);
        if (plot == null || plot.getState() != PlotInstanceState.COMPLETE) {
            return 1.0f;
        }
        PlotRestaurantState state = town.getOrCreatePlotRestaurant(plot.getPlotId());
        state.migrateIfNeeded();
        return RestaurantUpgrades.satietyDecayMultiplier(state.getSatietyLevel());
    }

    /**
     * Highest Provisions level among the town's finished restaurants (0 when none). A second restaurant never adds
     * a second helping, the same way a second Inn does not.
     */
    public static int provisionsLevel(
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog catalog
    ) {
        int level = 0;
        for (PlotInstance plot :
            town.listCompletePlotsWithGameplayConstruction(catalog, AetherhavenConstants.CONSTRUCTION_PLOT_RESTAURANT)) {
            PlotRestaurantState state = town.peekPlotRestaurant(plot.getPlotId());
            if (state != null) {
                level = Math.max(level, state.getProvisionsLevel());
            }
        }
        return level;
    }

    /**
     * Town food from the restaurant's Provisions upgrades, in the same unit as a farm or ranch site. Needs a finished
     * restaurant, and is limited to what the town's farms and ranches provide.
     *
     * @param farmAndRanchFoodPoints food points from the core farm, the core barn and tended farm or ranch sites
     */
    public static int provisionsFoodPoints(
        @Nonnull TownRecord town,
        @Nonnull ConstructionCatalog catalog,
        int farmAndRanchFoodPoints
    ) {
        return RestaurantUpgrades.provisionsFoodPoints(provisionsLevel(town, catalog), farmAndRanchFoodPoints);
    }

    @Nullable
    public static PlotRestaurantState restaurantStateForPoi(
        @Nullable TownRecord town,
        @Nullable PoiEntry poi
    ) {
        if (town == null || poi == null || poi.getPlotId() == null) {
            return null;
        }
        if (!poi.getTags().contains(AetherhavenConstants.POI_TAG_RESTAURANT)) {
            return null;
        }
        PlotRestaurantState state = town.getOrCreatePlotRestaurant(poi.getPlotId());
        state.migrateIfNeeded();
        return state;
    }

    @Nullable
    public static PlotRestaurantState restaurantStateForPlot(
        @Nullable TownRecord town,
        @Nullable UUID plotId
    ) {
        if (town == null || plotId == null) {
            return null;
        }
        PlotRestaurantState state = town.getOrCreatePlotRestaurant(plotId);
        state.migrateIfNeeded();
        return state;
    }
}
