package com.hexvane.aetherhaven.difficulty;

import com.google.gson.annotations.SerializedName;
import javax.annotation.Nonnull;

/** Plugin-wide difficulty settings and admin force-all-towns flag. */
public final class ServerDifficultyState {
    @SerializedName("forceAllTowns")
    private boolean forceAllTowns;

    @SerializedName("difficulty")
    private TownDifficultySettings difficulty = defaultServerSettings();

    public ServerDifficultyState() {}

    public boolean isForceAllTowns() {
        return forceAllTowns;
    }

    public void setForceAllTowns(boolean forceAllTowns) {
        this.forceAllTowns = forceAllTowns;
    }

    @Nonnull
    public TownDifficultySettings getDifficulty() {
        if (difficulty == null) {
            difficulty = defaultServerSettings();
        }
        return difficulty;
    }

    public void setDifficulty(@Nonnull TownDifficultySettings difficulty) {
        this.difficulty = difficulty;
    }

    /** Settings used when force is on (always treated as chosen). */
    @Nonnull
    public TownDifficultySettings effectiveForcedSettings() {
        TownDifficultySettings s = getDifficulty();
        if (!s.isDifficultyChosen()) {
            s.setDifficultyChosen(true);
        }
        return s.effectiveForGameplay();
    }

    @Nonnull
    public static TownDifficultySettings defaultServerSettings() {
        TownDifficultySettings s = TownDifficultySettings.normalUntilChosen();
        s.setDifficultyChosen(true);
        return s;
    }
}
