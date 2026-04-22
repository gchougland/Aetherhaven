package com.hexvane.aetherhaven.jewelry;

import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public enum JewelryRarity {
    COMMON,
    UNCOMMON,
    RARE,
    MYTHIC,
    LEGENDARY;

    public int traitCount() {
        return switch (this) {
            case COMMON -> 1;
            case UNCOMMON -> 2;
            case RARE, MYTHIC, LEGENDARY -> 3;
        };
    }

    public float magnitudeMultiplier() {
        return switch (this) {
            case COMMON -> 1.0f;
            case UNCOMMON -> 1.25f;
            case RARE -> 1.55f;
            case MYTHIC -> 1.85f;
            case LEGENDARY -> 2.2f;
        };
    }

    @Nonnull
    public static JewelryRarity roll(@Nonnull ThreadLocalRandom rnd) {
        int r = rnd.nextInt(100);
        if (r < 50) {
            return COMMON;
        }
        if (r < 80) {
            return UNCOMMON;
        }
        if (r < 95) {
            return RARE;
        }
        if (r < 99) {
            return MYTHIC;
        }
        return LEGENDARY;
    }

    @Nonnull
    public String wireName() {
        return name();
    }

    @Nonnull
    public static JewelryRarity fromWire(@Nullable String s) {
        if (s == null || s.isBlank()) {
            return COMMON;
        }
        try {
            return JewelryRarity.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return COMMON;
        }
    }
}
