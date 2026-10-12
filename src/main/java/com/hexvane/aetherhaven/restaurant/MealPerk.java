package com.hexvane.aetherhaven.restaurant;

import javax.annotation.Nullable;

/** The few short effects a restaurant dish can leave on the resident who ate it. */
public enum MealPerk {
    /** Apprentice work advances faster, inside the shared double speed limit. */
    WORK("work"),
    /** Energy drains slower, so the resident rests less often. */
    ENERGY("energy"),
    /** Fun drains slower, so the resident stays in a good mood for longer. */
    CHEER("cheer");

    /** No dish may strengthen or slow anything by more than half. */
    public static final float MAX_BONUS = 0.5f;

    public final String id;

    MealPerk(String id) {
        this.id = id;
    }

    @Nullable
    public static MealPerk byId(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String wanted = raw.trim();
        for (MealPerk perk : values()) {
            if (perk.id.equalsIgnoreCase(wanted)) {
                return perk;
            }
        }
        return null;
    }
}
