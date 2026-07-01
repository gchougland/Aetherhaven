package com.hexvane.aetherhaven.plotcreator;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** High level building role chosen after the prefab is saved. */
public enum PlotBuildingKind {
    DECORATION,
    VARIANT,
    HOME,
    WORK,
    AMENITY,
    SHOP,
    PLAYER_SHOP,
    INN,
    TOWN_HALL,
    GUILD_HALL,
    TOURIST_PORTAL;

    private static final List<PlotBuildingKind> PLAYER_KINDS = List.of(DECORATION, VARIANT);

    /** Decoration and variant — the building types intended for player-authored plots. */
    public boolean isPlayerKind() {
        return this == DECORATION || this == VARIANT;
    }

    @Nonnull
    public static List<PlotBuildingKind> selectableKinds(
        boolean playerTypesOnly,
        @Nullable PlotBuildingKind includeExisting
    ) {
        List<PlotBuildingKind> base = playerTypesOnly ? PLAYER_KINDS : List.of(values());
        if (includeExisting == null || base.contains(includeExisting)) {
            return base;
        }
        List<PlotBuildingKind> extended = new ArrayList<>(base.size() + 1);
        extended.addAll(base);
        extended.add(includeExisting);
        return extended;
    }

    @Nullable
    public static PlotBuildingKind fromSerialized(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
