package com.hexvane.aetherhaven.shopspot;

import static org.junit.jupiter.api.Assertions.*;
import com.hexvane.aetherhaven.difficulty.TownDifficultySettings;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Catalog prices set by hand in the 4.0 balance pass. */
@Tag("crossmod")
class ShopPriceTuningTest {
    private static ShopPriceCatalog catalog() throws Exception {
        return ShopPriceCatalog.parseJson(ShopPriceFiles.readDefaultJson());
    }

    @Test void aSmallCrudeChestIsWorthItsTenLogsAndNoMoreThanOtherCrudeFurniture() throws Exception {
        var catalog = catalog();
        String chest = "Furniture_Crude_Chest_Small";
        assertEquals(10, catalog.getGoldPrice(chest));
        assertEquals(1, catalog.getBatchSize(chest));
        // It is ten logs at a workbench. Furniture that needs sawn planks and a Builder's bench stays dearer.
        for (String other : List.of("Furniture_Crude_Stool", "Furniture_Crude_Sign", "Furniture_Crude_Shelf", "Furniture_Crude_Table",
                "Furniture_Crude_Door", "Furniture_Crude_Wardrobe", "Furniture_Village_Chest_Small"))
            assertTrue(catalog.getGoldPrice(other) > catalog.getGoldPrice(chest), other);
        assertEquals(6, ShopSpotPricing.playerListingGoldPerBatch(catalog.getGoldPrice(chest), new TownDifficultySettings()),
            "What a player's own shop gets for one at the usual margin");
    }

    @Test void everyEnergyPotionHasAPriceALittleAboveTheSameTierOfHealthAndStamina() throws Exception {
        var catalog = catalog();
        long fallback = catalog.getGoldPrice("Item_That_Does_Not_Exist");
        String[][] tiers = {{"_Lesser", "20"}, {"_Small", "15"}, {"", "35"}, {"_Large", "45"}, {"_Greater", "60"}};
        for (String[] tier : tiers) {
            String energy = "Potion_Signature" + tier[0];
            long price = catalog.getGoldPrice(energy);
            assertEquals(Integer.parseInt(tier[1]), price, energy);
            assertEquals(0, price % 5, energy);
            assertNotEquals(fallback, price, energy + " must not fall back to the default price");
            for (String family : List.of("Health", "Stamina")) {
                long sibling = catalog.getGoldPrice("Potion_" + family + tier[0]);
                assertTrue(price > sibling && price <= sibling + 8, energy + " " + price + " beside " + family + " " + sibling);
            }
        }
    }
}
