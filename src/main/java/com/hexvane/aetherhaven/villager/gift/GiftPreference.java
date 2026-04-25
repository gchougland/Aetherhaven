package com.hexvane.aetherhaven.villager.gift;

import javax.annotation.Nonnull;

/**
 * Reputation: LOVE +5, LIKE +3, NEUTRAL +1, DISLIKE -2. Serialized for town gift log (lower-case id).
 */
public enum GiftPreference {
    LOVE,
    LIKE,
    NEUTRAL,
    DISLIKE;

    @Nonnull
    public String toWireId() {
        return name().toLowerCase();
    }

    @Nonnull
    public static GiftPreference fromLabel(@Nonnull String s) {
        String t = s.trim();
        for (GiftPreference p : values()) {
            if (p.name().equalsIgnoreCase(t) || p.toWireId().equals(t)) {
                return p;
            }
        }
        return NEUTRAL;
    }
}
