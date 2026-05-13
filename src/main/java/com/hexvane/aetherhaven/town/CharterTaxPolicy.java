package com.hexvane.aetherhaven.town;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Level-1 charter amendment: how morning treasury tax scales with residents vs villager needs. */
public enum CharterTaxPolicy {
    /**
     * Headcount-stable revenue: each resident pays between configured min and max gold per morning, scaling linearly
     * with average needs (hunger, energy, fun).
     */
    PER_CAPITA("per_capita"),
    /**
     * Rewards high average needs: no tax at or below a comfort threshold, then a smooth curve up to a peak above the
     * linear treasury cap at full needs.
     */
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
