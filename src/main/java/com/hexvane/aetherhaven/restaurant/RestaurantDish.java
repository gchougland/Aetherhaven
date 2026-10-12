package com.hexvane.aetherhaven.restaurant;

import javax.annotation.Nullable;

/**
 * Pack supplied restaurant dish ({@code Server/Aetherhaven/RestaurantDishes}). The item is only what the menu shows:
 * nothing is taken from storage when a resident eats.
 */
public record RestaurantDish(String itemId, int menuLevel, String perk, double bonus, int gameHours) {
    /** Real time in one game hour at the default day length. Needs and meal perks run on this same clock. */
    public static final long MS_PER_GAME_HOUR = 120_000L;

    @Nullable
    public MealPerk mealPerk() {
        return MealPerk.byId(perk);
    }

    public boolean valid() {
        return itemId != null
            && !itemId.isBlank()
            && menuLevel >= 1
            && menuLevel <= RestaurantUpgrades.MAX_BRANCH_LEVEL
            && mealPerk() != null
            && Double.isFinite(bonus)
            && bonus > 0
            && bonus <= MealPerk.MAX_BONUS
            && gameHours >= 1
            && gameHours <= 24;
    }

    public long durationMs() {
        return gameHours * MS_PER_GAME_HOUR;
    }

    /** Whole percent for the menu text. */
    public int percent() {
        return (int) Math.round(bonus * 100);
    }
}
