package com.hexvane.aetherhaven.town;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.restaurant.RestaurantUpgrades;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.workplace.WorkplaceArea;
import com.hexvane.aetherhaven.workplace.WorkplaceFood;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Town food from the restaurant's Provisions upgrades, beside what farms and ranches give. */
@Tag("town")
class RestaurantProvisionsFoodTest {
    private static final Gson GSON = new Gson();

    private static PlotInstance plot(String construction, String state) {
        return GSON.fromJson("{\"plotId\":\"" + UUID.randomUUID() + "\",\"constructionId\":\"" + construction + "\",\"state\":\"" + state + "\"}", PlotInstance.class);
    }

    private static TownRecord town() { return new TownRecord(); }

    /** Elder and Innkeeper with the Inn, Farm and Barn: 30 food before any restaurant. */
    private static TownRecord fedTown() {
        var town = town();
        town.setElderEntityUuid(UUID.randomUUID()); town.setInnkeeperEntityUuid(UUID.randomUUID());
        for (String id : List.of("plot_inn", "plot_farm", "plot_barn")) town.getPlotInstances().add(plot(id, "COMPLETE"));
        return town;
    }

    private static PlotInstance addRestaurant(TownRecord town, int provisionsLevel) {
        var restaurant = plot("plot_restaurant", "COMPLETE");
        town.getPlotInstances().add(restaurant);
        town.getOrCreatePlotRestaurant(restaurant.getPlotId()).setProvisionsLevel(provisionsLevel);
        return restaurant;
    }

    private static TownPopulation.Snapshot population(TownRecord town) {
        return TownPopulation.snapshot(town, ConstructionCatalog.empty());
    }

    @Test void eachProvisionsLevelAddsFiveFoodBesideTheTenOfAFarmOrRanchSite() {
        assertEquals(WorkplaceFood.FOOD_POINTS / 2, RestaurantUpgrades.PROVISIONS_FOOD_PER_LEVEL, "One level is half a tended site");
        int[] food = {40, 45, 50, 55}, residents = {20, 22, 25, 27};
        for (int level = 0; level <= 3; level++) {
            var town = fedTown(); addRestaurant(town, level);
            var snapshot = population(town);
            assertEquals(food[level], snapshot.foodPoints(), "Provisions " + level);
            assertEquals(residents[level], snapshot.capacity(), "Provisions " + level);
            assertEquals(2, snapshot.residents());
        }
        assertEquals(15, RestaurantUpgrades.provisionsFoodPoints(3));
        assertEquals(15, RestaurantUpgrades.provisionsFoodPoints(9), "Levels past the maximum add nothing more");
        assertEquals(0, RestaurantUpgrades.provisionsFoodPoints(-2));
        assertTrue(RestaurantUpgrades.provisionsFoodPoints(3) < 2 * WorkplaceFood.FOOD_POINTS, "A maxed restaurant stays under two farm sites");
    }

    @Test void provisionsNeedAFinishedRestaurantAndNeverReplaceFarmsOrRanches() {
        var building = fedTown();
        var unfinished = plot("plot_restaurant", "BLUEPRINTING");
        building.getPlotInstances().add(unfinished);
        building.getOrCreatePlotRestaurant(unfinished.getPlotId()).setProvisionsLevel(3);
        assertEquals(30, population(building).foodPoints(), "An unfinished restaurant feeds nobody");

        var noFarms = town();
        noFarms.getPlotInstances().add(plot("plot_inn", "COMPLETE"));
        addRestaurant(noFarms, 3);
        assertEquals(0, TownPopulation.farmAndRanchFood(noFarms, ConstructionCatalog.empty()));
        assertEquals(20, population(noFarms).foodPoints(), "With nothing grown or raised the kitchen has nothing to stretch");

        var oneFarm = town();
        oneFarm.getPlotInstances().add(plot("plot_inn", "COMPLETE")); oneFarm.getPlotInstances().add(plot("plot_farm", "COMPLETE"));
        addRestaurant(oneFarm, 3);
        assertEquals(40, population(oneFarm).foodPoints(), "Provisions add no more than the farms and ranches provide");
        assertEquals(10, RestaurantUpgrades.provisionsFoodPoints(3, 10));
        assertEquals(15, RestaurantUpgrades.provisionsFoodPoints(3, 20));
        assertEquals(0, RestaurantUpgrades.provisionsFoodPoints(3, -5));

        var two = fedTown(); addRestaurant(two, 2); addRestaurant(two, 3);
        assertEquals(55, population(two).foodPoints(), "A second restaurant is not a second helping");
    }

    @Test void tendedFarmSitesStackWithProvisionsAndCountingFoodNeverChangesTheSave() {
        var town = fedTown(); addRestaurant(town, 3);
        town.getClaimedTerritoryChunksMutable().add(ClaimedTerritoryChunkRecord.of(0, 0));
        town.getResidentNpcRecords().add(new ResidentNpcRecord("Farmer", "farmer", null, UUID.randomUUID()));
        var sites = town.getApprenticeWorkplaces();
        assertNull(sites.unlock(town, TownsfolkWorkType.FARMING));
        assertNull(sites.save(town, null, TownsfolkWorkType.FARMING, new WorkplaceArea(0, 100, 0, 8, 8), "Garden"));
        assertEquals(55, population(town).foodPoints(), "An untended site adds nothing");
        var food = sites.sites().getFirst().food();
        for (int i = 0; i < 48; i++) if (food.work(.25, true, true)) food.completeCare(() -> true);
        var snapshot = population(town);
        assertEquals(65, snapshot.foodPoints()); assertEquals(32, snapshot.capacity());
        assertEquals(30, TownPopulation.farmAndRanchFood(town, ConstructionCatalog.empty()));

        var bare = fedTown(); var restaurant = plot("plot_restaurant", "COMPLETE"); bare.getPlotInstances().add(restaurant);
        assertEquals(40, population(bare).foodPoints());
        assertNull(bare.peekPlotRestaurant(restaurant.getPlotId()), "Reading population must not create restaurant records");
        assertFalse(GSON.toJson(bare).contains("plotRestaurantByPlotId"), "A town that never upgraded saves no restaurant data");
        // Other parts of the town fill in their own empty lists on first use; after that, counting food changes nothing.
        String settled = GSON.toJson(bare);
        assertEquals(40, population(bare).foodPoints());
        assertEquals(settled, GSON.toJson(bare));
    }
}
