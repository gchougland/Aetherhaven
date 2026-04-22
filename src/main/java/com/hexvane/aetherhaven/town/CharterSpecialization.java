package com.hexvane.aetherhaven.town;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Level-2 town specialization; production hooks may be partial until all jobs exist. */
public enum CharterSpecialization {
    MINING("mining"),
    LOGGING("logging"),
    FARMING("farming"),
    SMITHING("smithing");

    private final String id;

    CharterSpecialization(@Nonnull String id) {
        this.id = id;
    }

    @Nonnull
    public String id() {
        return id;
    }

    @Nullable
    public static CharterSpecialization fromId(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        for (CharterSpecialization v : values()) {
            if (v.id.equalsIgnoreCase(s)) {
                return v;
            }
        }
        return null;
    }
}
