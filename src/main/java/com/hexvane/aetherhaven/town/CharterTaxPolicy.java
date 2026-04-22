package com.hexvane.aetherhaven.town;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Level-1 charter amendment: how morning treasury tax scales with residents vs villager needs. */
public enum CharterTaxPolicy {
    /** Emphasize headcount: higher base per resident, weaker dependence on average needs. */
    PER_CAPITA("per_capita"),
    /** Emphasize happiness: stronger scaling from average needs ratio. */
    HAPPINESS_WEIGHTED("happiness_weighted");

    private final String id;

    CharterTaxPolicy(@Nonnull String id) {
        this.id = id;
    }

    @Nonnull
    public String id() {
        return id;
    }

    @Nullable
    public static CharterTaxPolicy fromId(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        for (CharterTaxPolicy p : values()) {
            if (p.id.equalsIgnoreCase(s)) {
                return p;
            }
        }
        return null;
    }
}
