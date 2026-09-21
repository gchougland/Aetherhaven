package com.hexvane.aetherhaven.shopspot;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
class CropVegetablePricesTest {
    @Test void everyHarvestedVegetableHasAnExplicitCheapBatchPrice() throws Exception {
        var catalog = ShopPriceCatalog.parseJson(ShopPriceFiles.readDefaultJson());
        for (String crop : List.of("Aubergine", "Carrot", "Cauliflower", "Chilli", "Corn", "Lettuce", "Onion",
                "Potato", "Pumpkin", "Tomato", "Turnip")) {
            String id = "Plant_Crop_" + crop + "_Item";
            assertEquals(1, catalog.getGoldPrice(id), id);
            assertEquals(10, catalog.getBatchSize(id), id);
            assertEquals(1, ShopSpotPricing.playerListingGoldPerBatch(catalog.getGoldPrice(id),
                new com.hexvane.aetherhaven.difficulty.TownDifficultySettings()), "Even cheap produce must pay at least one coin per batch");
        }
    }
}
