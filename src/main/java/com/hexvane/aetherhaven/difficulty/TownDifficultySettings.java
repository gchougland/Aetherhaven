package com.hexvane.aetherhaven.difficulty;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nonnull;

/** Per-town difficulty settings persisted on {@link com.hexvane.aetherhaven.town.TownRecord}. */
public final class TownDifficultySettings {
    public static final int DEFAULT_SELL_PROFIT_MARGIN_PERCENT = 60;
    public static final int HARD_SELL_PROFIT_MARGIN_PERCENT = 48;

    @SerializedName("preset")
    private String preset = DifficultyPreset.NORMAL.persisted();

    @SerializedName("resourceCostMultiplier")
    private double resourceCostMultiplier = 1.0;

    @SerializedName("goldCostMultiplier")
    private double goldCostMultiplier = 1.0;

    @SerializedName("requireAllPrefabBlocks")
    private boolean requireAllPrefabBlocks;

    @SerializedName("buyPriceMultiplier")
    private double buyPriceMultiplier = 1.0;

    @SerializedName("sellProfitMarginPercent")
    private int sellProfitMarginPercent = DEFAULT_SELL_PROFIT_MARGIN_PERCENT;

    @SerializedName("taxMultiplier")
    private double taxMultiplier = 1.0;

    @SerializedName("buildingUpgradeGoldMultiplier")
    private double buildingUpgradeGoldMultiplier = 1.0;

    @SerializedName("buildingUpgradeResourceMultiplier")
    private double buildingUpgradeResourceMultiplier = 1.0;

    @SerializedName("productionUnlockGoldMultiplier")
    private double productionUnlockGoldMultiplier = 1.0;

    @SerializedName("productionUnlockResourceMultiplier")
    private double productionUnlockResourceMultiplier = 1.0;

    @SerializedName("buildingStaffDisabled")
    private boolean buildingStaffDisabled;

    @SerializedName("goldLootRarityMultiplier")
    private double goldLootRarityMultiplier = 1.0;

    @SerializedName("otherLootRarityMultiplier")
    private double otherLootRarityMultiplier = 1.0;

    @SerializedName("difficultyChosen")
    private boolean difficultyChosen;

    public TownDifficultySettings() {}

    @Nonnull
    public DifficultyPreset getPreset() {
        return DifficultyPreset.fromPersisted(preset);
    }

    public void setPreset(@Nonnull DifficultyPreset p) {
        this.preset = p.persisted();
    }

    public double getResourceCostMultiplier() {
        return resourceCostMultiplier;
    }

    public void setResourceCostMultiplier(double resourceCostMultiplier) {
        this.resourceCostMultiplier = resourceCostMultiplier;
    }

    public double getGoldCostMultiplier() {
        return goldCostMultiplier;
    }

    public void setGoldCostMultiplier(double goldCostMultiplier) {
        this.goldCostMultiplier = goldCostMultiplier;
    }

    public boolean isRequireAllPrefabBlocks() {
        return requireAllPrefabBlocks;
    }

    public void setRequireAllPrefabBlocks(boolean requireAllPrefabBlocks) {
        this.requireAllPrefabBlocks = requireAllPrefabBlocks;
    }

    public double getBuyPriceMultiplier() {
        return buyPriceMultiplier;
    }

    public void setBuyPriceMultiplier(double buyPriceMultiplier) {
        this.buyPriceMultiplier = buyPriceMultiplier;
    }

    public int getSellProfitMarginPercent() {
        return sellProfitMarginPercent;
    }

    public void setSellProfitMarginPercent(int sellProfitMarginPercent) {
        this.sellProfitMarginPercent = sellProfitMarginPercent;
    }

    public double getTaxMultiplier() {
        return taxMultiplier;
    }

    public void setTaxMultiplier(double taxMultiplier) {
        this.taxMultiplier = taxMultiplier;
    }

    public double getBuildingUpgradeGoldMultiplier() {
        return buildingUpgradeGoldMultiplier;
    }

    public void setBuildingUpgradeGoldMultiplier(double buildingUpgradeGoldMultiplier) {
        this.buildingUpgradeGoldMultiplier = buildingUpgradeGoldMultiplier;
    }

    public double getBuildingUpgradeResourceMultiplier() {
        return buildingUpgradeResourceMultiplier;
    }

    public void setBuildingUpgradeResourceMultiplier(double buildingUpgradeResourceMultiplier) {
        this.buildingUpgradeResourceMultiplier = buildingUpgradeResourceMultiplier;
    }

    public double getProductionUnlockGoldMultiplier() {
        return productionUnlockGoldMultiplier;
    }

    public void setProductionUnlockGoldMultiplier(double productionUnlockGoldMultiplier) {
        this.productionUnlockGoldMultiplier = productionUnlockGoldMultiplier;
    }

    public double getProductionUnlockResourceMultiplier() {
        return productionUnlockResourceMultiplier;
    }

    public void setProductionUnlockResourceMultiplier(double productionUnlockResourceMultiplier) {
        this.productionUnlockResourceMultiplier = productionUnlockResourceMultiplier;
    }

    public boolean isBuildingStaffDisabled() {
        return buildingStaffDisabled;
    }

    public void setBuildingStaffDisabled(boolean buildingStaffDisabled) {
        this.buildingStaffDisabled = buildingStaffDisabled;
    }

    public double getGoldLootRarityMultiplier() {
        return goldLootRarityMultiplier;
    }

    public void setGoldLootRarityMultiplier(double goldLootRarityMultiplier) {
        this.goldLootRarityMultiplier = goldLootRarityMultiplier;
    }

    public double getOtherLootRarityMultiplier() {
        return otherLootRarityMultiplier;
    }

    public void setOtherLootRarityMultiplier(double otherLootRarityMultiplier) {
        this.otherLootRarityMultiplier = otherLootRarityMultiplier;
    }

    public boolean isDifficultyChosen() {
        return difficultyChosen;
    }

    public void setDifficultyChosen(boolean difficultyChosen) {
        this.difficultyChosen = difficultyChosen;
    }

    /** Gameplay costs before the town has saved a difficulty choice. */
    @Nonnull
    public static TownDifficultySettings normalUntilChosen() {
        TownDifficultySettings s = new TownDifficultySettings();
        s.applyNormalDefaults();
        s.setPreset(DifficultyPreset.NORMAL);
        s.setDifficultyChosen(false);
        return s;
    }

    private void applyNormalDefaults() {
        setResourceCostMultiplier(1.0);
        setGoldCostMultiplier(1.0);
        setRequireAllPrefabBlocks(false);
        setBuyPriceMultiplier(1.0);
        setSellProfitMarginPercent(DEFAULT_SELL_PROFIT_MARGIN_PERCENT);
        setTaxMultiplier(1.0);
        setBuildingUpgradeGoldMultiplier(1.0);
        setBuildingUpgradeResourceMultiplier(1.0);
        setProductionUnlockGoldMultiplier(1.0);
        setProductionUnlockResourceMultiplier(1.0);
        setBuildingStaffDisabled(false);
        setGoldLootRarityMultiplier(1.0);
        setOtherLootRarityMultiplier(1.0);
    }

    public void applyPreset(@Nonnull DifficultyPreset p) {
        setPreset(p);
        switch (p) {
            case EASY -> {
                applyNormalDefaults();
                setResourceCostMultiplier(0.5);
                setGoldCostMultiplier(0.5);
            }
            case HARD -> {
                applyNormalDefaults();
                setResourceCostMultiplier(2.0);
                setGoldCostMultiplier(1.5);
                setRequireAllPrefabBlocks(false);
                setBuyPriceMultiplier(1.2);
                setSellProfitMarginPercent(HARD_SELL_PROFIT_MARGIN_PERCENT);
                setTaxMultiplier(0.8);
                setBuildingUpgradeGoldMultiplier(2.0);
                setBuildingUpgradeResourceMultiplier(2.0);
                setProductionUnlockGoldMultiplier(2.0);
                setProductionUnlockResourceMultiplier(2.0);
                setGoldLootRarityMultiplier(0.5);
                setOtherLootRarityMultiplier(0.5);
            }
            case NORMAL -> applyNormalDefaults();
            case CUSTOM -> {
                // keep current slider values
            }
        }
    }

    /** Effective settings for building costs (Normal until chosen). */
    @Nonnull
    public TownDifficultySettings effectiveForGameplay() {
        if (difficultyChosen) {
            return this;
        }
        return normalUntilChosen();
    }

    /** Copies all fields from another settings object. */
    public void copyFrom(@Nonnull TownDifficultySettings other) {
        setPreset(other.getPreset());
        setResourceCostMultiplier(other.getResourceCostMultiplier());
        setGoldCostMultiplier(other.getGoldCostMultiplier());
        setRequireAllPrefabBlocks(other.isRequireAllPrefabBlocks());
        setBuyPriceMultiplier(other.getBuyPriceMultiplier());
        setSellProfitMarginPercent(other.getSellProfitMarginPercent());
        setTaxMultiplier(other.getTaxMultiplier());
        setBuildingUpgradeGoldMultiplier(other.getBuildingUpgradeGoldMultiplier());
        setBuildingUpgradeResourceMultiplier(other.getBuildingUpgradeResourceMultiplier());
        setProductionUnlockGoldMultiplier(other.getProductionUnlockGoldMultiplier());
        setProductionUnlockResourceMultiplier(other.getProductionUnlockResourceMultiplier());
        setBuildingStaffDisabled(other.isBuildingStaffDisabled());
        setGoldLootRarityMultiplier(other.getGoldLootRarityMultiplier());
        setOtherLootRarityMultiplier(other.getOtherLootRarityMultiplier());
        setDifficultyChosen(other.isDifficultyChosen());
    }

    /** Construction material/gold multipliers (legacy 0–5 range). */
    public static double clampMultiplier(double v) {
        return clampMultiplier(v, 0.0, 5.0);
    }

    /** Economy multipliers (0.1–10). */
    public static double clampEconomyMultiplier(double v) {
        return clampMultiplier(v, 0.1, 10.0);
    }

    public static double clampMultiplier(double v, double min, double max) {
        double rounded = Math.round(v * 10.0) / 10.0;
        return Math.max(min, Math.min(max, rounded));
    }

    public static int clampSellProfitMarginPercent(int v) {
        return Math.max(1, Math.min(100, v));
    }
}
