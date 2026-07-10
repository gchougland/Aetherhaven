package com.hexvane.aetherhaven.shopspot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
class ShopPriceCatalogMergeTest {

    @Test
    void withOverrides_laterKeysWin_andKeepsBaseDefaults() {
        ShopPriceCatalog base = ShopPriceCatalog.parseJson(
            """
            {
              "catalogRevision": 1,
              "defaultGoldPrice": 5,
              "defaultBatchSize": 1,
              "prices": {
                "Item_A": 10,
                "Item_B": { "gold": 20, "batchSize": 2 }
              }
            }
            """
        );
        ShopPriceCatalog pack = ShopPriceCatalog.parseJson(
            """
            {
              "catalogRevision": 1,
              "prices": {
                "Item_B": 50,
                "Item_C": 7
              }
            }
            """
        );
        ShopPriceCatalog merged = base.withOverrides(pack);
        assertEquals(10L, merged.getGoldPrice("Item_A"));
        assertEquals(50L, merged.getGoldPrice("Item_B"));
        assertEquals(1, merged.getBatchSize("Item_B"));
        assertEquals(7L, merged.getGoldPrice("Item_C"));
        assertEquals(5L, merged.getDefaultGoldPrice());
        assertEquals(3, merged.getExplicitPriceCount());
    }
}
