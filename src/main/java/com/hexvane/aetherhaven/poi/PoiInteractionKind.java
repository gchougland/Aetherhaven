package com.hexvane.aetherhaven.poi;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Drives USE behavior at anchored POI cells (see building JSON {@code interactionKind}). */
public enum PoiInteractionKind {
    NONE,
    SIT,
    SLEEP,
    USE_BENCH,
    USE_CONTAINER,
    WORK_SURFACE;

    @Nonnull
    public static PoiInteractionKind fromJson(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
