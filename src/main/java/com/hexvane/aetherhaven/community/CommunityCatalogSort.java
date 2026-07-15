package com.hexvane.aetherhaven.community;

import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Sort order for the Community marketplace catalog list. */
public enum CommunityCatalogSort {
    UPVOTES,
    DOWNLOADS,
    LATEST,
    NAME;

    @Nonnull
    public static CommunityCatalogSort fromValue(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return UPVOTES;
        }
        try {
            return CommunityCatalogSort.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return UPVOTES;
        }
    }

    @Nonnull
    public String value() {
        return name();
    }
}
