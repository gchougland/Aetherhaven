package com.hexvane.aetherhaven.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.production.ProductionWorkplaceKinds;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hexvane.aetherhaven.villager.data.VillagerDefinitionCatalog;
import java.util.List;
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
    void uniqueCrossmodShopIsNotTreatedAsMarketStall() {
        ConstructionDefinition hobbitShop =
            GSON.fromJson(
                """
                {"id":"plot_hobbit_shop","displayName":"Hobbit Shop","prefabPath":"plot_hobbit_shop.prefab.json","plotTokenItemId":"Lotr_Plot_Token_Hobbit_Shop","countsAsConstructionId":["plot_house"]}
                """,
                ConstructionDefinition.class
            );
        ConstructionCatalog catalog = ConstructionCatalog.forTests(Map.of(hobbitShop.getId(), hobbitShop));

        assertFalse(
            catalog.matchesGameplayConstruction(
                hobbitShop.getId(),
                AetherhavenConstants.CONSTRUCTION_PLOT_MARKET_STALL
            )
        );
        assertFalse(
            ProductionWorkplaceKinds.residentBindingKindsForPlot(catalog, hobbitShop.getId())
                .contains(TownVillagerBinding.KIND_MERCHANT)
        );
        assertTrue(catalog.matchesGameplayConstruction(hobbitShop.getId(), "plot_house"));
        assertTrue(catalog.matchesGameplayConstruction(hobbitShop.getId(), hobbitShop.getId()));
        assertTrue(ProductionWorkplaceKinds.residentBindingKindsForPlot(catalog, hobbitShop.getId()).isEmpty());
    }

    @Test
    void variantCountsAsHouseStillMatchesOwnConstructionIdForQuests() {
        ConstructionDefinition hobbitShop =
            GSON.fromJson(
                """
                {"id":"plot_hobbit_shop","displayName":"Hobbit Shop","prefabPath":"plot_hobbit_shop.prefab.json","plotTokenItemId":"Lotr_Plot_Token_Hobbit_Shop","countsAsConstructionId":["plot_house"]}
                """,
                ConstructionDefinition.class
            );
        ConstructionCatalog catalog = ConstructionCatalog.forTests(Map.of(hobbitShop.getId(), hobbitShop));

        assertTrue(catalog.matchesGameplayConstruction(hobbitShop.getId(), hobbitShop.getId()));
        assertFalse(catalog.matchesGameplayConstruction(hobbitShop.getId(), AetherhavenConstants.CONSTRUCTION_PLOT_MARKET_STALL));
    }

    @Test
    void dualWorkplaceAndHouseVariantExposesOnlyJobRole() {
        ConstructionDefinition constructionCo =
            GSON.fromJson(
                """
                {"id":"plot_stormwind_construction_co","displayName":"Stormwind Construction Co","prefabPath":"Stormwind/plot_stormwind_construction_co.prefab.json","plotTokenItemId":"Aetherhaven_Plot_Token","countsAsConstructionId":["plot_builders_hut","plot_house"]}
                """,
                ConstructionDefinition.class
            );
        ConstructionCatalog catalog = ConstructionCatalog.forTests(Map.of(constructionCo.getId(), constructionCo));

        List<String> roles =
            ProductionWorkplaceKinds.residentBindingKindsForPlot(catalog, constructionCo.getId());

        assertEquals(List.of(TownVillagerBinding.KIND_BUILDER), roles);
    }

    @Test
    void dualShopAndHouseVariantExposesOnlyShopJobRole() {
        ConstructionDefinition generalStore =
            GSON.fromJson(
                """
                {"id":"plot_stormwind_general_store","displayName":"Stormwind General Store","prefabPath":"Stormwind/plot_stormwind_general_store.prefab.json","plotTokenItemId":"Aetherhaven_Plot_Token","countsAsConstructionId":["plot_market_stall","plot_house"]}
                """,
                ConstructionDefinition.class
            );
        ConstructionCatalog catalog = ConstructionCatalog.forTests(Map.of(generalStore.getId(), generalStore));

        List<String> roles =
            ProductionWorkplaceKinds.residentBindingKindsForPlot(catalog, generalStore.getId());

        assertEquals(List.of(TownVillagerBinding.KIND_MERCHANT), roles);
    }

    @Test
    void crossmodShopkeepMatchingPlotHouseDoesNotBleedOntoDualWorkplacePlots() {
        ConstructionDefinition lotrShop =
            GSON.fromJson(
                """
                {"id":"plot_lotr_shop","displayName":"LOTR Shop","prefabPath":"plot_lotr_shop.prefab.json","plotTokenItemId":"Lotr_Plot_Token","countsAsConstructionId":["plot_house"]}
                """,
                ConstructionDefinition.class
            );
        ConstructionDefinition constructionCo =
            GSON.fromJson(
                """
                {"id":"plot_stormwind_construction_co","displayName":"Stormwind Construction Co","prefabPath":"Stormwind/plot_stormwind_construction_co.prefab.json","plotTokenItemId":"Aetherhaven_Plot_Token","countsAsConstructionId":["plot_builders_hut","plot_house"]}
                """,
                ConstructionDefinition.class
            );
        ConstructionCatalog catalog =
            ConstructionCatalog.forTests(
                Map.of(
                    lotrShop.getId(),
                    lotrShop,
                    constructionCo.getId(),
                    constructionCo
                )
            );
        VillagerDefinition lotrShopkeep =
            GSON.fromJson(
                """
                {"npcRoleId":"Lotr_Shopkeep","dialogueVillagerKind":"lotr.shopkeep","workConstructionId":"plot_lotr_shop"}
                """,
                VillagerDefinition.class
            );
        VillagerDefinitionCatalog villagers =
            VillagerDefinitionCatalog.forTests(Map.of(lotrShopkeep.getNpcRoleId(), lotrShopkeep));

        assertNull(
            ProductionWorkplaceKinds.residentBindingKindFromVillagerCatalogForTests(
                villagers,
                catalog,
                AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE
            )
        );
        assertEquals(
            List.of(TownVillagerBinding.KIND_BUILDER),
            ProductionWorkplaceKinds.residentBindingKindsForPlotForTests(catalog, villagers, constructionCo.getId())
        );
    }

    @Test
    void crossmodShopCountsAsHouseOnlyCanAssignShopkeepToOwnPlot() {
        ConstructionDefinition hobbitShop =
            GSON.fromJson(
                """
                {"id":"plot_hobbit_shop","displayName":"Hobbit Shop","prefabPath":"plot_hobbit_shop.prefab.json","plotTokenItemId":"Lotr_Plot_Token_Hobbit_Shop","countsAsConstructionId":["plot_house"]}
                """,
                ConstructionDefinition.class
            );
        VillagerDefinition bilbo =
            GSON.fromJson(
                """
                {"npcRoleId":"Lotr_Bilbo","dialogueVillagerKind":"lotr.shopkeep","workConstructionId":"plot_hobbit_shop"}
                """,
                VillagerDefinition.class
            );
        ConstructionCatalog catalog = ConstructionCatalog.forTests(Map.of(hobbitShop.getId(), hobbitShop));
        VillagerDefinitionCatalog villagers = VillagerDefinitionCatalog.forTests(Map.of(bilbo.getNpcRoleId(), bilbo));

        assertEquals(
            List.of("lotr.shopkeep"),
            ProductionWorkplaceKinds.residentBindingKindsForPlotForTests(catalog, villagers, hobbitShop.getId())
        );
        assertEquals(
            "plot_hobbit_shop",
            ProductionWorkplaceKinds.gameplayConstructionIdForResidentKindForTests(
                catalog,
                villagers,
                hobbitShop.getId(),
                "lotr.shopkeep"
            )
        );
    }

    @Test
    void townHallVariantWithElderLyrenExposesOnlyElderRole() {
        ConstructionDefinition stormwindTownHall =
            GSON.fromJson(
                """
                {"id":"plot_stormwind_town_hall","displayName":"Stormwind Town Hall","prefabPath":"Stormwind/plot_stormwind_town_hall.prefab.json","plotTokenItemId":"Aetherhaven_Plot_Token","countsAsConstructionId":"plot_town_hall"}
                """,
                ConstructionDefinition.class
            );
        VillagerDefinition elderLyren =
            GSON.fromJson(
                """
                {"npcRoleId":"Aetherhaven_Elder_Lyren","dialogueVillagerKind":"elder_lyren","workConstructionId":"plot_town_hall"}
                """,
                VillagerDefinition.class
            );
        ConstructionCatalog catalog = ConstructionCatalog.forTests(Map.of(stormwindTownHall.getId(), stormwindTownHall));
        VillagerDefinitionCatalog villagers =
            VillagerDefinitionCatalog.forTests(Map.of(elderLyren.getNpcRoleId(), elderLyren));

        assertEquals(
            List.of(TownVillagerBinding.KIND_ELDER),
            ProductionWorkplaceKinds.residentBindingKindsForPlotForTests(catalog, villagers, stormwindTownHall.getId())
        );
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
