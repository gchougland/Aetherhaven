package com.hexvane.aetherhaven.startertown;

import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import java.util.List;
import javax.annotation.Nonnull;

public enum StarterTownPreset {
    MINIMAL,
    FULL;

    private static final List<String> MINIMAL_IDS = List.of(
        "plot_town_hall",
        "plot_inn",
        "plot_house",
        "plot_builders_hut",
        "plot_farm"
    );
    /**
     * Built-in Aetherhaven core set. Keep this explicit: asset packs and community buildings may also use
     * {@code styleId: core}, but they are not part of the stable full starter-town preset.
     */
    private static final List<String> FULL_IDS = List.of(
        "plot_town_hall",
        "plot_inn",
        "plot_house",
        "plot_builders_hut",
        "plot_farm",
        "plot_barn",
        "plot_miners_hut",
        "plot_lumbermill",
        "plot_blacksmith_shop",
        "plot_market_stall",
        "plot_flower_shop",
        "plot_furniture_shop",
        "plot_crystal_keepers_shop",
        "plot_bomb_shop",
        "plot_restaurant",
        "plot_player_shop",
        "plot_park",
        "plot_gaia_altar",
        "plot_guild_hall",
        "plot_tourist_portal"
    );

    @Nonnull
    public List<String> resolve(@Nonnull ConstructionCatalog catalog) {
        List<String> configured = this == MINIMAL ? MINIMAL_IDS : FULL_IDS;
        return configured.stream()
            .filter(
                id -> {
                    var definition = catalog.get(id);
                    return definition != null
                        && definition.getPrefabPath() != null
                        && !definition.getPrefabPath().isBlank();
                }
            )
            .toList();
    }

    static List<String> fullCanonicalIds() {
        return FULL_IDS;
    }

    @Nonnull
    public static StarterTownPreset parse(@Nonnull String value) {
        return "full".equalsIgnoreCase(value) ? FULL : MINIMAL;
    }
}
