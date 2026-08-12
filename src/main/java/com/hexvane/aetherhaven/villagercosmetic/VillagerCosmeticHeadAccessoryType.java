package com.hexvane.aetherhaven.villagercosmetic;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * How a head accessory interacts with the villager's haircut attachment. Mirrors player
 * {@code HeadAccessoryType} for wardrobe visuals on NPCs.
 */
public enum VillagerCosmeticHeadAccessoryType {
    /** Leave hair unchanged. */
    Simple,
    /** Swap haircuts that require it to Generic{Short|Medium|Long}, keeping color. */
    HalfCovering,
    /** Hide the haircut attachment entirely. */
    FullyCovering;

    @Nonnull
    public static VillagerCosmeticHeadAccessoryType parse(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return Simple;
        }
        String key = raw.trim();
        for (VillagerCosmeticHeadAccessoryType t : values()) {
            if (t.name().equalsIgnoreCase(key)) {
                return t;
            }
        }
        return Simple;
    }
}
