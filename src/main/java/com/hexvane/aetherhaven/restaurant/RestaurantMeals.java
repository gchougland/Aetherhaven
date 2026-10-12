package com.hexvane.aetherhaven.restaurant;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** What a finished restaurant meal leaves on the resident who sat down and ate it. */
public final class RestaurantMeals {
    /** The warehouse and food sites refuse more than double speed, so a meal never pushes work past it. */
    public static final double MAX_WORK_MULTIPLIER = 2.0;

    private RestaurantMeals() {}

    /**
     * Called once when a resident completes a meal at an eat spot. Only a restaurant seat with a Menu level serves a
     * dish; the inn hearth, feasts and containers leave no perk.
     *
     * @return the dish served, or null when this meal carried no perk
     */
    @Nullable
    public static RestaurantDish serve(
        @Nonnull VillagerNeeds needs,
        @Nonnull PoiEntry poi,
        @Nullable PlotRestaurantState restaurantState,
        @Nullable RestaurantMenuCatalog menu
    ) {
        if (restaurantState == null || menu == null) {
            return null;
        }
        if (!poi.getTags().contains("EAT") || !poi.getTags().contains(AetherhavenConstants.POI_TAG_RESTAURANT)) {
            return null;
        }
        RestaurantDish dish = pick(needs, menu.served(restaurantState.getMenuLevel()));
        if (dish == null) {
            return null;
        }
        MealPerk perk = dish.mealPerk();
        if (perk == null || !needs.grantMealPerk(perk, (float) dish.bonus(), dish.durationMs())) {
            return null;
        }
        return dish;
    }

    /**
     * The Chef serves what helps most: the dish whose perk this diner is missing or closest to losing. Between
     * perks that are equally due the earlier dish on the menu wins; between two dishes with the same perk, the
     * stronger one.
     */
    @Nullable
    public static RestaurantDish pick(@Nonnull VillagerNeeds needs, @Nonnull List<RestaurantDish> served) {
        RestaurantDish best = null;
        long bestRemaining = Long.MAX_VALUE;
        for (RestaurantDish dish : served) {
            MealPerk perk = dish.mealPerk();
            if (perk == null) {
                continue;
            }
            long remaining = needs.mealPerkRemainingMs(perk);
            if (best == null
                || remaining < bestRemaining
                || (remaining == bestRemaining && perk == best.mealPerk() && dish.bonus() > best.bonus())) {
                best = dish;
                bestRemaining = remaining;
            }
        }
        return best;
    }

    /**
     * Folds a diner's work perk into an existing work multiplier (profession kit, furnishings). The perk alone never
     * lifts the result above {@link #MAX_WORK_MULTIPLIER} and never lowers what the worker already had.
     */
    public static double work(@Nullable VillagerNeeds needs, double multiplier) {
        if (needs == null || !Double.isFinite(multiplier)) {
            return multiplier;
        }
        float bonus = needs.mealPerkBonus(MealPerk.WORK);
        if (bonus <= 0f) {
            return multiplier;
        }
        return Math.max(multiplier, Math.min(MAX_WORK_MULTIPLIER, multiplier * (1.0 + bonus)));
    }
}
