package com.hexvane.aetherhaven.bard;

import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public enum BardPlaybackMode {
    ONCE,
    LOOP,
    SHUFFLE;

    @Nonnull
    public static BardPlaybackMode fromString(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return ONCE;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "loop" -> LOOP;
            case "shuffle" -> SHUFFLE;
            default -> ONCE;
        };
    }

    @Nonnull
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
