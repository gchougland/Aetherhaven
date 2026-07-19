package com.hexvane.aetherhaven.worldnpc;

import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public enum WorldNpcScheduleMode {
    STATIC,
    STATIONS,
    ROUTE;

    @Nonnull
    public static WorldNpcScheduleMode fromString(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return STATIC;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "stations", "station" -> STATIONS;
            case "route", "patrol" -> ROUTE;
            default -> STATIC;
        };
    }

    @Nonnull
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
