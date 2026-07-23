package com.hexvane.aetherhaven.townsfolk;

import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Composite ledger keys: one checkout slot per character per town. */
public final class TownsfolkPoolKeys {
    private static final char SEP = '|';

    private TownsfolkPoolKeys() {}

    @Nonnull
    public static String checkoutKey(@Nonnull UUID townId, @Nonnull String characterId) {
        return checkoutKey(townId.toString(), characterId);
    }

    @Nonnull
    public static String checkoutKey(@Nonnull String townId, @Nonnull String characterId) {
        return normalizeTownId(townId) + SEP + characterId.trim();
    }

    @Nonnull
    private static String normalizeTownId(@Nonnull String townId) {
        return townId.trim().toLowerCase();
    }

    public static boolean isBlankTownId(@Nullable String townId) {
        return townId == null || townId.isBlank();
    }
}
