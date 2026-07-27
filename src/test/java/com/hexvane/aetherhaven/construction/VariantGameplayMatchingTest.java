package com.hexvane.aetherhaven.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.production.ProductionWorkplaceKinds;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("construction")
class VariantGameplayMatchingTest {
    private static final Gson GSON = new Gson();

    @Test
    void townHallVariantMatchesGameplayTownHall() {
        ConstructionDefinition variant =
            GSON.fromJson(
                """
                {"id":"plot_jimmys_town_hall","displayName":"Jimmy's Town Hall","prefabPath":"Jimmy.prefab.json","plotTokenItemId":"Aetherhaven_Plot_Token","countsAsConstructionId":"plot_town_hall"}
                """,
                ConstructionDefinition.class
            );
        ConstructionCatalog catalog = ConstructionCatalog.forTests(Map.of(variant.getId(), variant));

        assertTrue(
            catalog.matchesGameplayConstruction(variant.getId(), AetherhavenConstants.CONSTRUCTION_PLOT_TOWN_HALL)
        );
        assertFalse(catalog.matchesGameplayConstruction(variant.getId(), AetherhavenConstants.CONSTRUCTION_PLOT_INN));
    }

    @Test
    void dualShopVariantResolvesRoleSpecificGameplayIds() {
        ConstructionDefinition dualShop =
            GSON.fromJson(
                """
                {"id":"plot_community_dual_shop","displayName":"Dual Shop","prefabPath":"Dual.prefab.json","plotTokenItemId":"Aetherhaven_Plot_Token","countsAsConstructionId":["plot_market_stall","plot_flower_shop"]}
                """,
                ConstructionDefinition.class
            );
        ConstructionCatalog catalog = ConstructionCatalog.forTests(Map.of(dualShop.getId(), dualShop));

        assertEquals(
            AetherhavenConstants.CONSTRUCTION_PLOT_MARKET_STALL,
            ProductionWorkplaceKinds.gameplayConstructionIdForResidentKind(
                catalog,
                dualShop.getId(),
                TownVillagerBinding.KIND_MERCHANT
            )
        );
        assertEquals(
            AetherhavenConstants.CONSTRUCTION_PLOT_FLOWER_SHOP,
            ProductionWorkplaceKinds.gameplayConstructionIdForResidentKind(
                catalog,
                dualShop.getId(),
                TownVillagerBinding.KIND_FLORIST
            )
        );
    }

    @Test
    void tavernVariantResolvesInnAndGuildHallByRoleRegardlessOfAliasOrder() {
        ConstructionDefinition innFirst =
            GSON.fromJson(
                """
                {"id":"plot_jimmys_tavern","displayName":"Jimmy's Tavern","prefabPath":"Tavern.prefab.json","plotTokenItemId":"Aetherhaven_Plot_Token","countsAsConstructionId":["plot_inn","plot_guild_hall"]}
                """,
                ConstructionDefinition.class
            );
        ConstructionDefinition guildFirst =
            GSON.fromJson(
                """
                {"id":"plot_hytinys_cozy_tavern","displayName":"Cozy Tavern","prefabPath":"Cozy.prefab.json","plotTokenItemId":"Aetherhaven_Plot_Token","countsAsConstructionId":["plot_guild_hall","plot_inn"]}
                """,
                ConstructionDefinition.class
            );
        ConstructionCatalog innFirstCatalog = ConstructionCatalog.forTests(Map.of(innFirst.getId(), innFirst));
        ConstructionCatalog guildFirstCatalog = ConstructionCatalog.forTests(Map.of(guildFirst.getId(), guildFirst));

        for (ConstructionCatalog catalog : new ConstructionCatalog[] {innFirstCatalog, guildFirstCatalog}) {
            String stored = catalog.list().get(0).getId();
            assertEquals(
                AetherhavenConstants.CONSTRUCTION_PLOT_INN,
                ProductionWorkplaceKinds.gameplayConstructionIdForResidentKind(
                    catalog,
                    stored,
                    TownVillagerBinding.KIND_INNKEEPER
                )
            );
            assertEquals(
                AetherhavenConstants.CONSTRUCTION_PLOT_GUILD_HALL,
                ProductionWorkplaceKinds.gameplayConstructionIdForResidentKind(
                    catalog,
                    stored,
                    TownVillagerBinding.KIND_GUILD_MASTER
                )
            );
        }
    }

    @Test
    void plotLookupMatchesAnyResolvedAlias() {
        ConstructionDefinition variant =
            GSON.fromJson(
                """
                {"id":"plot_community_dual_shop","displayName":"Dual Shop","prefabPath":"Dual.prefab.json","plotTokenItemId":"Aetherhaven_Plot_Token","countsAsConstructionId":["plot_market_stall","plot_flower_shop"]}
                """,
                ConstructionDefinition.class
            );
        ConstructionCatalog catalog = ConstructionCatalog.forTests(Map.of(variant.getId(), variant));
        PlotInstance plot = new PlotInstance();
        plot.setConstructionId(variant.getId());

        assertTrue(matchesPlotConstructionQuery(catalog, plot, AetherhavenConstants.CONSTRUCTION_PLOT_FLOWER_SHOP));
        assertTrue(matchesPlotConstructionQuery(catalog, plot, AetherhavenConstants.CONSTRUCTION_PLOT_MARKET_STALL));
        assertFalse(matchesPlotConstructionQuery(catalog, plot, AetherhavenConstants.CONSTRUCTION_PLOT_INN));
    }

    /** Mirrors {@code PlotConstructionIdResolver.matchesConstructionId}. */
    private static boolean matchesPlotConstructionQuery(
        ConstructionCatalog catalog,
        PlotInstance plot,
        String want
    ) {
        String stored = plot.getConstructionId();
        if (stored == null) {
            return false;
        }
        if (want.equals(stored)) {
            return true;
        }
        return catalog.matchesGameplayConstruction(stored, want);
    }
}
