package com.hexvane.aetherhaven.plot;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.production.ProductionWorkplaceKinds;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * The villagers a finished building can take: one per job it offers, its home slots, and a clerk when it is a player
 * shop. Reads the same rules the town records shelf assigns by, so the plot bench cannot promise more than the shelf gives.
 */
public record BuildingVillagerCapacity(@Nonnull List<String> workerKinds, int homeResidents, boolean shopClerk) {
    private static final BuildingVillagerCapacity NONE = new BuildingVillagerCapacity(List.of(), 0, false);

    @Nonnull
    public static BuildingVillagerCapacity of(@Nonnull ConstructionCatalog catalog, @Nonnull ConstructionDefinition def) {
        if (def.isDecorationPlot() || def.isWallSegment()) {
            return NONE;
        }
        String id = def.getId();
        return new BuildingVillagerCapacity(
            ProductionWorkplaceKinds.residentBindingKindsForPlot(catalog, id),
            catalog.matchesGameplayConstruction(id, AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE) ? def.getMaxHomeResidents() : 0,
            catalog.matchesGameplayConstruction(id, AetherhavenConstants.CONSTRUCTION_PLOT_PLAYER_SHOP)
        );
    }

    public boolean isEmpty() {
        return workerKinds.isEmpty() && homeResidents <= 0 && !shopClerk;
    }
}
