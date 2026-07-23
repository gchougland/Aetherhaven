package com.hexvane.aetherhaven.poi;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.restaurant.PlotRestaurantState;
import com.hexvane.aetherhaven.restaurant.RestaurantUpgrades;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Need restoration and USE duration by {@link PoiInteractionKind}. */
public final class PoiEffectTable {
    private PoiEffectTable() {}

    public static float useDurationSeconds(@Nonnull PoiInteractionKind kind) {
        return switch (kind) {
            case SLEEP -> 14f;
            case SIT, USE_BENCH -> 10f;
            case WORK_SURFACE -> 8f;
            case USE_CONTAINER -> 9f;
            case NONE -> 6f;
        };
    }

    public static float useDurationSeconds(@Nonnull PoiEntry poi, @Nullable PlotRestaurantState restaurantState) {
        if (poi.getTags().contains(AetherhavenConstants.POI_TAG_RESTAURANT) && restaurantState != null) {
            return RestaurantUpgrades.serviceEatDurationSeconds(restaurantState.getServiceLevel());
        }
        return useDurationSeconds(poi.getInteractionKind());
    }

    /** Apply a single USE completion tick to needs (called once when USE phase ends). */
    public static void applyUseComplete(@Nonnull VillagerNeeds needs, @Nonnull PoiEntry poi) {
        applyUseComplete(needs, poi, null);
    }

    public static void applyUseComplete(
        @Nonnull VillagerNeeds needs,
        @Nonnull PoiEntry poi,
        @Nullable PlotRestaurantState restaurantState
    ) {
        PoiInteractionKind k = poi.getInteractionKind();
        switch (k) {
            case SLEEP -> {
                needs.setEnergy(Math.min(VillagerNeeds.MAX, needs.getEnergy() + 28f));
                needs.setFun(Math.min(VillagerNeeds.MAX, needs.getFun() + 6f));
            }
            case SIT -> {
                if (poi.getTags().contains(AetherhavenConstants.POI_TAG_FEAST)) {
                    needs.setHunger(VillagerNeeds.MAX);
                } else if (poi.getTags().contains(AetherhavenConstants.POI_TAG_RESTAURANT) && restaurantState != null) {
                    float restore = RestaurantUpgrades.serviceHungerRestore(restaurantState.getServiceLevel());
                    needs.setHunger(Math.min(VillagerNeeds.MAX, needs.getHunger() + restore));
                } else if (poi.getTags().contains("EAT")) {
                    needs.setHunger(Math.min(VillagerNeeds.MAX, needs.getHunger() + 30f));
                } else {
                    needs.setFun(Math.min(VillagerNeeds.MAX, needs.getFun() + 22f));
                }
            }
            case USE_BENCH -> {
                if (poi.getTags().contains(AetherhavenConstants.POI_TAG_FEAST)) {
                    needs.setHunger(VillagerNeeds.MAX);
                } else if (poi.getTags().contains(AetherhavenConstants.POI_TAG_RESTAURANT) && restaurantState != null) {
                    float restore = RestaurantUpgrades.serviceHungerRestore(restaurantState.getServiceLevel());
                    needs.setHunger(Math.min(VillagerNeeds.MAX, needs.getHunger() + restore));
                } else if (poi.getTags().contains("EAT")) {
                    needs.setHunger(Math.min(VillagerNeeds.MAX, needs.getHunger() + 30f));
                } else {
                    needs.setFun(Math.min(VillagerNeeds.MAX, needs.getFun() + 22f));
                    needs.setHunger(Math.min(VillagerNeeds.MAX, needs.getHunger() + 4f));
                }
            }
            case WORK_SURFACE -> { }
            case USE_CONTAINER -> needs.setHunger(Math.min(VillagerNeeds.MAX, needs.getHunger() + 18f));
            default -> needs.setFun(Math.min(VillagerNeeds.MAX, needs.getFun() + 8f));
        }
    }
}
