package com.hexvane.aetherhaven.restaurant;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nonnull;

/** Per plot restaurant upgrade tiers (satiety + service). */
public final class PlotRestaurantState {

    @SerializedName("satietyLevel")
    private int satietyLevel;

    @SerializedName("serviceLevel")
    private int serviceLevel;

    public PlotRestaurantState() {}

    public int getSatietyLevel() {
        return Math.max(0, Math.min(RestaurantUpgrades.MAX_BRANCH_LEVEL, satietyLevel));
    }

    public void setSatietyLevel(int satietyLevel) {
        this.satietyLevel = Math.max(0, Math.min(RestaurantUpgrades.MAX_BRANCH_LEVEL, satietyLevel));
    }

    public int getServiceLevel() {
        return Math.max(0, Math.min(RestaurantUpgrades.MAX_BRANCH_LEVEL, serviceLevel));
    }

    public void setServiceLevel(int serviceLevel) {
        this.serviceLevel = Math.max(0, Math.min(RestaurantUpgrades.MAX_BRANCH_LEVEL, serviceLevel));
    }

    public void migrateIfNeeded() {
        satietyLevel = Math.max(0, Math.min(RestaurantUpgrades.MAX_BRANCH_LEVEL, satietyLevel));
        serviceLevel = Math.max(0, Math.min(RestaurantUpgrades.MAX_BRANCH_LEVEL, serviceLevel));
    }

    @Nonnull
    public static PlotRestaurantState empty() {
        return new PlotRestaurantState();
    }
}
