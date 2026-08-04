package com.hexvane.aetherhaven.poi;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.restaurant.PlotRestaurantState;
import com.hexvane.aetherhaven.restaurant.RestaurantUpgrades;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Need restoration and USE duration by POI tags and {@link PoiInteractionKind}. */
public final class PoiEffectTable {
    private static final float GENERIC_EAT_HUNGER_RESTORE = 30f;
    private static final float REST_ENERGY_RESTORE = 28f;
    private static final float FUN_RESTORE = 22f;
    private static final float CONTAINER_HUNGER_RESTORE = 18f;

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
        if (isEatPoi(poi.getTags()) && poi.getTags().contains(AetherhavenConstants.POI_TAG_RESTAURANT) && restaurantState != null) {
            return RestaurantUpgrades.serviceEatDurationSeconds(restaurantState.getServiceLevel());
        }
        return useDurationSeconds(poi.getInteractionKind());
    }

    /** Apply a single USE completion tick to needs (called once when USE phase ends). */
    public static void applyUseComplete(@Nonnull VillagerNeeds needs, @Nonnull PoiEntry poi) {
        applyUseComplete(needs, poi, null);
    }

    /**
     * Each POI restores at most one need meter. Tags decide the effect; {@link PoiInteractionKind} only affects
     * duration and visuals (e.g. eat on a chair vs standing at the inn hearth).
     */
    public static void applyUseComplete(
        @Nonnull VillagerNeeds needs,
        @Nonnull PoiEntry poi,
        @Nullable PlotRestaurantState restaurantState
    ) {
        Set<String> tags = poi.getTags();
        if (tags.contains(AetherhavenConstants.POI_TAG_FEAST)) {
            needs.setHunger(VillagerNeeds.MAX);
            return;
        }
        if (tags.contains("EAT")) {
            float restore = GENERIC_EAT_HUNGER_RESTORE;
            if (tags.contains(AetherhavenConstants.POI_TAG_RESTAURANT) && restaurantState != null) {
                restore = RestaurantUpgrades.serviceHungerRestore(restaurantState.getServiceLevel());
            }
            needs.setHunger(Math.min(VillagerNeeds.MAX, needs.getHunger() + restore));
            return;
        }
        if (poi.getInteractionKind() == PoiInteractionKind.USE_CONTAINER) {
            needs.setHunger(Math.min(VillagerNeeds.MAX, needs.getHunger() + CONTAINER_HUNGER_RESTORE));
            return;
        }
        if (isRestPoi(tags, poi.getInteractionKind())) {
            needs.setEnergy(Math.min(VillagerNeeds.MAX, needs.getEnergy() + REST_ENERGY_RESTORE));
            return;
        }
        if (isFunPoi(tags)) {
            needs.setFun(Math.min(VillagerNeeds.MAX, needs.getFun() + FUN_RESTORE));
        }
    }

    /** Same restore amount as park fun spots; used while browsing during a shop schedule segment. */
    public static void applyShopFunRestore(@Nonnull VillagerNeeds needs) {
        needs.setFun(Math.min(VillagerNeeds.MAX, needs.getFun() + FUN_RESTORE));
    }

    static boolean isEatPoi(@Nonnull Set<String> tags) {
        return tags.contains("EAT") || tags.contains(AetherhavenConstants.POI_TAG_FEAST);
    }

    private static boolean isRestPoi(@Nonnull Set<String> tags, @Nonnull PoiInteractionKind kind) {
        return tags.contains("SLEEP") || tags.contains("ENERGY") || kind == PoiInteractionKind.SLEEP;
    }

    private static boolean isFunPoi(@Nonnull Set<String> tags) {
        return tags.contains("FUN");
    }
}
