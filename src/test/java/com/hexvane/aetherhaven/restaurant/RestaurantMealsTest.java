package com.hexvane.aetherhaven.restaurant;

import static org.junit.jupiter.api.Assertions.*;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiInteractionKind;
import com.hexvane.aetherhaven.restaurant.RestaurantUpgrades.Branch;
import com.hexvane.aetherhaven.restaurant.RestaurantUpgrades.IngredientCost;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import com.hypixel.hytale.codec.ExtraInfo;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The shipped menu, who gets a perk from a meal, what the perk does and when it ends. */
@Tag("autonomy")
class RestaurantMealsTest {
    private static final long START = 5_000_000L;
    private static final float DECAY = 0.0525f;
    private static final long HOUR = RestaurantDish.MS_PER_GAME_HOUR;

    private static final RestaurantMenuCatalog MENU = RestaurantMenuCatalog.load(RestaurantMealsTest.class.getClassLoader());

    /** A villager whose needs clock has started, as it has for every ticking resident. */
    private static VillagerNeeds diner() {
        var needs = VillagerNeeds.full();
        needs.applyDecay(START, DECAY);
        return needs;
    }

    /** Lets time pass in one second ticks; a single long gap would be treated as an unloaded chunk. */
    private static void pass(VillagerNeeds needs, long ms) {
        long now = needs.getLastSimulatedEpochMs();
        for (long done = 0; done < ms; done += 1000) needs.applyDecay(now += Math.min(1000, ms - done), DECAY);
    }

    private static PoiEntry seat(String... tags) {
        return new PoiEntry(UUID.randomUUID(), UUID.randomUUID(), 1, 64, 1, Set.of(tags), 1, UUID.randomUUID(), null, PoiInteractionKind.USE_BENCH);
    }

    private static PoiEntry restaurantSeat() { return seat("EAT", AetherhavenConstants.POI_TAG_RESTAURANT); }

    private static PlotRestaurantState restaurant(int menuLevel) {
        var state = PlotRestaurantState.empty(); state.setMenuLevel(menuLevel); return state;
    }

    // --- the shipped menu ----------------------------------------------------------------------------------------

    @Test void shippedMenuHasTwoDishesAndOnePerkPerLevelAndCostsTheDishesItAdds() {
        assertEquals(6, MENU.all().size());
        assertTrue(MENU.served(0).isEmpty(), "A restaurant with no Menu level serves plain meals");
        var perkByLevel = List.of(MealPerk.WORK, MealPerk.ENERGY, MealPerk.CHEER);
        for (int level = 1; level <= 3; level++) {
            var added = MENU.addedAt(level);
            assertEquals(2, added.size(), "Menu " + level);
            assertEquals(2 * level, MENU.served(level).size());
            for (var dish : added) {
                assertTrue(dish.valid()); assertEquals(perkByLevel.get(level - 1), dish.mealPerk());
                assertEquals(8, dish.gameHours()); assertEquals(0, dish.percent() % 5);
                assertTrue(dish.itemId().startsWith("Food_"), "Dishes are base game food items");
            }
            var costItems = RestaurantUpgrades.ingredientCosts(Branch.MENU, level).stream().map(IngredientCost::itemId).toList();
            assertEquals(new HashSet<>(costItems), new HashSet<>(added.stream().map(RestaurantDish::itemId).toList()),
                "The kitchen learns a level from samples of the dishes that level adds");
            assertEquals(1, RestaurantMenuCatalog.courses(added).size());
        }
        var courses = RestaurantMenuCatalog.courses(MENU.served(3));
        assertEquals(3, courses.size(), "Three perks, each explained once");
        assertEquals(10, courses.get(0).percent()); assertEquals(25, courses.get(1).percent()); assertEquals(25, courses.get(2).percent());
        assertEquals(List.of("Food_Kebab_Mushroom", "Food_Kebab_Vegetable"), courses.get(0).itemIds());
    }

    @Test void badDishDefinitionsAreIgnored() {
        var catalog = new RestaurantMenuCatalog(java.util.Arrays.asList(
            new RestaurantDish("Food_Bread", 1, "work", 0.1, 8),
            new RestaurantDish("Food_Bread", 2, "energy", 0.2, 6),
            new RestaurantDish("", 1, "work", 0.1, 8),
            new RestaurantDish("Food_Cheese", 0, "work", 0.1, 8),
            new RestaurantDish("Food_Cheese", 4, "work", 0.1, 8),
            new RestaurantDish("Food_Cheese", 1, "flying", 0.1, 8),
            new RestaurantDish("Food_Cheese", 1, "work", 0.9, 8),
            new RestaurantDish("Food_Cheese", 1, "work", Double.NaN, 8),
            new RestaurantDish("Food_Cheese", 1, "work", 0.1, 0),
            new RestaurantDish("Food_Cheese", 1, "work", 0.1, 48),
            null));
        assertEquals(1, catalog.all().size());
        assertEquals(MealPerk.ENERGY, catalog.all().getFirst().mealPerk(), "A later definition of the same item replaces the earlier one");
        assertTrue(RestaurantMenuCatalog.empty().served(3).isEmpty());
    }

    // --- who gets a perk ---------------------------------------------------------------------------------------------

    @Test void onlyAFinishedMealAtARestaurantSeatWithAMenuLeavesAPerk() {
        var needs = diner();
        assertNull(RestaurantMeals.serve(needs, restaurantSeat(), restaurant(0), MENU), "No Menu level, no dish");
        assertNull(RestaurantMeals.serve(needs, seat("EAT"), restaurant(3), MENU), "The inn hearth serves no dishes");
        assertNull(RestaurantMeals.serve(needs, seat("FUN", AetherhavenConstants.POI_TAG_RESTAURANT), restaurant(3), MENU), "Sitting is not eating");
        assertNull(RestaurantMeals.serve(needs, restaurantSeat(), null, MENU));
        assertNull(RestaurantMeals.serve(needs, restaurantSeat(), restaurant(3), null));
        assertNull(RestaurantMeals.serve(needs, restaurantSeat(), restaurant(3), RestaurantMenuCatalog.empty()));
        for (MealPerk perk : MealPerk.values()) assertEquals(0f, needs.mealPerkBonus(perk));
        assertEquals(1.0, RestaurantMeals.work(needs, 1.0));

        var dish = RestaurantMeals.serve(needs, restaurantSeat(), restaurant(1), MENU);
        assertNotNull(dish); assertEquals(1, dish.menuLevel()); assertEquals(MealPerk.WORK, dish.mealPerk());
        assertEquals(0.1f, needs.mealPerkBonus(MealPerk.WORK), 1e-6);
        assertEquals(8 * HOUR, needs.mealPerkRemainingMs(MealPerk.WORK));
        assertEquals(0f, needs.mealPerkBonus(MealPerk.ENERGY)); assertEquals(0f, needs.mealPerkBonus(MealPerk.CHEER));

        var unstarted = VillagerNeeds.full();
        assertNull(RestaurantMeals.serve(unstarted, restaurantSeat(), restaurant(3), MENU), "A villager that has never ticked has no clock to time a perk");
    }

    @Test void theChefServesWhatTheDinerIsMissingSoAFullMenuGivesEveryPerk() {
        var needs = diner();
        var seat = restaurantSeat(); var full = restaurant(3);
        assertEquals(MealPerk.WORK, RestaurantMeals.serve(needs, seat, full, MENU).mealPerk());
        assertEquals(MealPerk.ENERGY, RestaurantMeals.serve(needs, seat, full, MENU).mealPerk());
        assertEquals(MealPerk.CHEER, RestaurantMeals.serve(needs, seat, full, MENU).mealPerk());
        for (MealPerk perk : MealPerk.values()) assertTrue(needs.mealPerkBonus(perk) > 0f, perk + " should be running");
        pass(needs, 2 * HOUR);
        // Everything has six hours left: the next course renews the first perk on the menu.
        assertEquals(MealPerk.WORK, RestaurantMeals.serve(needs, seat, full, MENU).mealPerk());
        assertEquals(8 * HOUR, needs.mealPerkRemainingMs(MealPerk.WORK));
        assertEquals(6 * HOUR, needs.mealPerkRemainingMs(MealPerk.ENERGY));
        // With only the first level, every course is a skewer.
        var skewersOnly = diner();
        for (int i = 0; i < 3; i++) assertEquals(MealPerk.WORK, RestaurantMeals.serve(skewersOnly, seat, restaurant(1), MENU).mealPerk());
        assertEquals(0f, skewersOnly.mealPerkBonus(MealPerk.ENERGY));
    }

    @Test void aWeakerDishNeverReplacesAStrongerPerkThatIsStillRunning() {
        var needs = diner();
        assertTrue(needs.grantMealPerk(MealPerk.WORK, 0.2f, 4 * HOUR));
        assertFalse(needs.grantMealPerk(MealPerk.WORK, 0.1f, 8 * HOUR));
        assertEquals(0.2f, needs.mealPerkBonus(MealPerk.WORK), 1e-6); assertEquals(4 * HOUR, needs.mealPerkRemainingMs(MealPerk.WORK));
        assertTrue(needs.grantMealPerk(MealPerk.WORK, 0.2f, 8 * HOUR), "An equal dish renews the perk");
        assertEquals(8 * HOUR, needs.mealPerkRemainingMs(MealPerk.WORK));
        pass(needs, 8 * HOUR);
        assertTrue(needs.grantMealPerk(MealPerk.WORK, 0.1f, 8 * HOUR), "Once it has ended a weaker dish is welcome again");
        assertFalse(needs.grantMealPerk(MealPerk.CHEER, 0f, HOUR)); assertFalse(needs.grantMealPerk(MealPerk.CHEER, Float.NaN, HOUR));
        assertFalse(needs.grantMealPerk(MealPerk.CHEER, 0.2f, 0));
        assertTrue(needs.grantMealPerk(MealPerk.CHEER, 5f, 1000 * HOUR));
        assertEquals(MealPerk.MAX_BONUS, needs.mealPerkBonus(MealPerk.CHEER), "No dish may change anything by more than half");
        assertEquals(24 * HOUR, needs.mealPerkRemainingMs(MealPerk.CHEER), "No perk outlasts a day");
        var stronger = new RestaurantDish("Food_A", 1, "work", 0.3, 8);
        var weaker = new RestaurantDish("Food_B", 1, "work", 0.1, 8);
        assertSame(stronger, RestaurantMeals.pick(diner(), List.of(weaker, stronger)));
        assertNull(RestaurantMeals.pick(diner(), List.of()));
    }

    // --- what the perks do and when they end -------------------------------------------------------------------------

    @Test void theWorkPerkSpeedsApprenticeWorkInsideTheDoubleSpeedLimitThenExpires() {
        var needs = diner();
        RestaurantMeals.serve(needs, restaurantSeat(), restaurant(1), MENU);
        assertEquals(1.1, RestaurantMeals.work(needs, 1.0), 1e-6, "Plain worker");
        assertEquals(1.15 * 1.1, RestaurantMeals.work(needs, 1.15), 1e-6, "Stacks with a profession kit");
        assertEquals(2.0, RestaurantMeals.work(needs, 1.95), 1e-9, "A meal never lifts work past double speed");
        assertEquals(2.0, RestaurantMeals.work(needs, 2.0), 1e-9);
        assertEquals(2.4, RestaurantMeals.work(needs, 2.4), 1e-9, "It never lowers what the worker already had either");
        assertEquals(1.0, RestaurantMeals.work(null, 1.0)); assertTrue(Double.isNaN(RestaurantMeals.work(needs, Double.NaN)));
        // The food site clamps the same way, so a fed worker finishes a tending visit sooner but never instantly.
        var fed = new com.hexvane.aetherhaven.workplace.WorkplaceFood(); var plain = new com.hexvane.aetherhaven.workplace.WorkplaceFood();
        for (int i = 0; i < 44; i++) {
            if (fed.work(.25, true, true, RestaurantMeals.work(needs, 1.0))) fed.completeCare(() -> true);
            if (plain.work(.25, true, true, RestaurantMeals.work(diner(), 1.0))) plain.completeCare(() -> true);
        }
        assertEquals(1, fed.completedVisits()); assertEquals(0, plain.completedVisits());

        pass(needs, 8 * HOUR - 1000);
        assertEquals(1.1, RestaurantMeals.work(needs, 1.0), 1e-6, "Still running a second before the end");
        pass(needs, 1000);
        assertEquals(0f, needs.mealPerkBonus(MealPerk.WORK)); assertEquals(0L, needs.mealPerkRemainingMs(MealPerk.WORK));
        assertEquals(1.0, RestaurantMeals.work(needs, 1.0), "Eight game hours later the perk is gone");
    }

    @Test void energyAndCheerPerksSlowOnlyTheirOwnMeterAndOnlyWhileTheyLast() {
        var plain = diner(); var fed = diner();
        var seat = restaurantSeat();
        RestaurantMeals.serve(fed, seat, restaurant(3), MENU); RestaurantMeals.serve(fed, seat, restaurant(3), MENU); RestaurantMeals.serve(fed, seat, restaurant(3), MENU);
        pass(plain, 4 * HOUR); pass(fed, 4 * HOUR);
        float hungerLost = VillagerNeeds.MAX - plain.getHunger(), energyLost = VillagerNeeds.MAX - plain.getEnergy(), funLost = VillagerNeeds.MAX - plain.getFun();
        assertEquals(480 * DECAY, hungerLost, 0.05f);
        assertEquals(plain.getHunger(), fed.getHunger(), 1e-3f, "Dishes do not change hunger; that is the Satiety branch");
        assertEquals(energyLost * 0.75f, VillagerNeeds.MAX - fed.getEnergy(), 0.05f, "Energy drains 25% slower");
        assertEquals(funLost * 0.75f, VillagerNeeds.MAX - fed.getFun(), 0.05f, "Fun drains 25% slower");
        // After the perks end both villagers lose the same amount again.
        pass(plain, 4 * HOUR); pass(fed, 4 * HOUR);
        float plainEnergy = plain.getEnergy(), fedEnergy = fed.getEnergy(), plainFun = plain.getFun(), fedFun = fed.getFun();
        pass(plain, HOUR); pass(fed, HOUR);
        assertEquals(plainEnergy - plain.getEnergy(), fedEnergy - fed.getEnergy(), 1e-3f);
        assertEquals(plainFun - plain.getFun(), fedFun - fed.getFun(), 1e-3f);
        for (MealPerk perk : MealPerk.values()) assertEquals(0f, fed.mealPerkBonus(perk));
    }

    // --- saving ------------------------------------------------------------------------------------------------------

    @Test void villagersSavedBeforeMealPerksLoadWithNoneAndRunningPerksSurviveASave() {
        var old = VillagerNeeds.CODEC.decode(BsonDocument.parse("{\"Hunger\":61.5,\"Energy\":40.0,\"Fun\":72.0,\"LastSimulatedEpochMs\":{\"$numberLong\":\"" + START + "\"},\"Schema\":1}"), new ExtraInfo());
        assertEquals(61.5f, old.getHunger()); assertEquals(40f, old.getEnergy()); assertEquals(72f, old.getFun());
        for (MealPerk perk : MealPerk.values()) { assertEquals(0f, old.mealPerkBonus(perk)); assertEquals(0L, old.mealPerkRemainingMs(perk)); }
        assertEquals(1.0, RestaurantMeals.work(old, 1.0));

        var needs = diner();
        RestaurantMeals.serve(needs, restaurantSeat(), restaurant(2), MENU); RestaurantMeals.serve(needs, restaurantSeat(), restaurant(2), MENU);
        pass(needs, HOUR);
        var restored = VillagerNeeds.CODEC.decode(VillagerNeeds.CODEC.encode(needs, new ExtraInfo()), new ExtraInfo());
        var copy = (VillagerNeeds) needs.clone();
        for (var loaded : List.of(restored, copy)) {
            assertEquals(0.1f, loaded.mealPerkBonus(MealPerk.WORK), 1e-6); assertEquals(7 * HOUR, loaded.mealPerkRemainingMs(MealPerk.WORK));
            assertEquals(0.25f, loaded.mealPerkBonus(MealPerk.ENERGY), 1e-6); assertEquals(0f, loaded.mealPerkBonus(MealPerk.CHEER));
            assertEquals(needs.getEnergy(), loaded.getEnergy(), 1e-4f);
        }
        copy.grantMealPerk(MealPerk.CHEER, 0.25f, HOUR);
        assertEquals(0f, needs.mealPerkBonus(MealPerk.CHEER), "A copy does not share its perks with the original");

        var corrupt = VillagerNeeds.CODEC.decode(BsonDocument.parse("{\"Hunger\":50.0,\"Energy\":50.0,\"Fun\":50.0,\"LastSimulatedEpochMs\":{\"$numberLong\":\"" + START
            + "\"},\"Schema\":1,\"MealWorkUntil\":{\"$numberLong\":\"9223372036854775807\"},\"MealWorkBonus\":40.0,\"MealEnergyUntil\":{\"$numberLong\":\"" + (START + HOUR) + "\"},\"MealEnergyBonus\":-3.0}"), new ExtraInfo());
        assertEquals(0f, corrupt.mealPerkBonus(MealPerk.WORK), "An end time beyond one day is not a real perk");
        assertEquals(0f, corrupt.mealPerkBonus(MealPerk.ENERGY), "A negative strength is not a perk");
        float energy = corrupt.getEnergy(); pass(corrupt, 60_000);
        assertTrue(corrupt.getEnergy() < energy && corrupt.getEnergy() > energy - 5f);
    }
}
