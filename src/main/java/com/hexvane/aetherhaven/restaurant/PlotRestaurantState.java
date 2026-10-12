package com.hexvane.aetherhaven.restaurant;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nonnull;

/** Per plot restaurant upgrade tiers (satiety, service, menu and provisions). Missing fields load as level 0. */
public final class PlotRestaurantState {

    @SerializedName("satietyLevel")
    private int satietyLevel;

    @SerializedName("serviceLevel")
    private int serviceLevel;

    @SerializedName("menuLevel")
    private int menuLevel;

    @SerializedName("provisionsLevel")
    private int provisionsLevel;

    public PlotRestaurantState() {}

    public int getMenuLevel() {
        return Math.max(0, Math.min(RestaurantUpgrades.MAX_BRANCH_LEVEL, menuLevel));
    }

    public void setMenuLevel(int menuLevel) {
        this.menuLevel = Math.max(0, Math.min(RestaurantUpgrades.MAX_BRANCH_LEVEL, menuLevel));
    }

    public int getProvisionsLevel() {
        return Math.max(0, Math.min(RestaurantUpgrades.MAX_BRANCH_LEVEL, provisionsLevel));
    }

    public void setProvisionsLevel(int provisionsLevel) {
        this.provisionsLevel = Math.max(0, Math.min(RestaurantUpgrades.MAX_BRANCH_LEVEL, provisionsLevel));
    }

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
        menuLevel = Math.max(0, Math.min(RestaurantUpgrades.MAX_BRANCH_LEVEL, menuLevel));
        provisionsLevel = Math.max(0, Math.min(RestaurantUpgrades.MAX_BRANCH_LEVEL, provisionsLevel));
    }

    @Nonnull
    public static PlotRestaurantState empty() {
        return new PlotRestaurantState();
    }
}
