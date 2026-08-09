package com.hexvane.aetherhaven.plotcreator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** High level building role chosen after the prefab is saved. */
public enum PlotBuildingKind {
    DECORATION,
    VARIANT,
    FESTIVAL,
    HOME,
    WORK,
    AMENITY,
    SHOP,
    PLAYER_SHOP,
    INN,
    TOWN_HALL,
    GUILD_HALL,
    TOURIST_PORTAL;

    private static final List<PlotBuildingKind> PLAYER_KINDS = List.of(DECORATION, VARIANT, FESTIVAL);

    /** Decoration, variant, and festival — the building types intended for player-authored plots. */
    public boolean isPlayerKind() {
        return this == DECORATION || this == VARIANT || this == FESTIVAL;
    }

    /** Kinds that own the whole build and cannot be combined with another kind. */
    public boolean isExclusiveKind() {
        return this == DECORATION || this == FESTIVAL;
    }

    @Nonnull
    public static List<PlotBuildingKind> selectableKinds(
        boolean playerTypesOnly,
        @Nullable PlotBuildingKind includeExisting
    ) {
        return selectableKinds(
            playerTypesOnly,
            includeExisting == null ? List.of() : List.of(includeExisting)
        );
    }

    @Nonnull
    public static List<PlotBuildingKind> selectableKinds(
        boolean playerTypesOnly,
        @Nonnull Collection<PlotBuildingKind> includeExisting
    ) {
        List<PlotBuildingKind> base =
            playerTypesOnly ? new ArrayList<>(PLAYER_KINDS) : new ArrayList<>(List.of(values()));
        for (PlotBuildingKind existing : includeExisting) {
            if (existing != null && !base.contains(existing)) {
                base.add(existing);
            }
        }
        return base;
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
