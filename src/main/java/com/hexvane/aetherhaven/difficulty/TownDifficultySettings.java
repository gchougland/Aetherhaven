package com.hexvane.aetherhaven.difficulty;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nonnull;

/** Per-town difficulty settings persisted on {@link com.hexvane.aetherhaven.town.TownRecord}. */
public final class TownDifficultySettings {
    @SerializedName("preset")
    private String preset = DifficultyPreset.NORMAL.persisted();

    @SerializedName("resourceCostMultiplier")
    private double resourceCostMultiplier = 1.0;

    @SerializedName("goldCostMultiplier")
    private double goldCostMultiplier = 1.0;

    @SerializedName("requireAllPrefabBlocks")
    private boolean requireAllPrefabBlocks;

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
        s.setPreset(DifficultyPreset.NORMAL);
        s.setResourceCostMultiplier(1.0);
        s.setGoldCostMultiplier(1.0);
        s.setRequireAllPrefabBlocks(false);
        s.setDifficultyChosen(false);
        return s;
    }

    public void applyPreset(@Nonnull DifficultyPreset p) {
        setPreset(p);
        switch (p) {
            case EASY -> {
                setResourceCostMultiplier(0.5);
                setGoldCostMultiplier(0.5);
                setRequireAllPrefabBlocks(false);
            }
            case HARD -> {
                setResourceCostMultiplier(1.0);
                setGoldCostMultiplier(1.0);
                setRequireAllPrefabBlocks(true);
            }
            case NORMAL -> {
                setResourceCostMultiplier(1.0);
                setGoldCostMultiplier(1.0);
                setRequireAllPrefabBlocks(false);
            }
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
        setDifficultyChosen(other.isDifficultyChosen());
    }

    public static double clampMultiplier(double v) {
        double rounded = Math.round(v * 10.0) / 10.0;
        return Math.max(0.0, Math.min(5.0, rounded));
    }
}
