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
