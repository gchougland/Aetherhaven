package com.hexvane.aetherhaven.shopspot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
class ShopLootMergeTest {

    @Test
    void appendAndReplaceLayers() {
        ShopLootTable base = ShopLootTable.parseJson(
            """
            { "entries": [ { "itemId": "A", "weight": 1, "stockMin": 1, "stockMax": 1 } ] }
            """
        );
        ShopLootTable appended = ShopLootFiles.mergeLayers(
            base,
            List.of(
                """
                { "entries": [ { "itemId": "B", "weight": 2, "stockMin": 1, "stockMax": 2 } ] }
                """
            )
        );
        assertEquals(2, appended.entryCount());
        assertEquals("A", appended.getEntries().get(0).getItemId());
        assertEquals("B", appended.getEntries().get(1).getItemId());

        ShopLootTable replaced = ShopLootFiles.mergeLayers(
            appended,
            List.of(
                """
                {
                  "replace": true,
                  "entries": [ { "itemId": "C", "weight": 3, "stockMin": 1, "stockMax": 3 } ]
                }
                """
            )
        );
        assertEquals(1, replaced.entryCount());
        assertEquals("C", replaced.getEntries().get(0).getItemId());

        ShopLootTable.Parsed flags = ShopLootTable.parseJsonWithFlags(
            """
            { "replace": true, "entries": [] }
            """
        );
        assertTrue(flags.replace());
        assertEquals(0, flags.table().entryCount());

        ShopLootTable.Parsed noReplace = ShopLootTable.parseJsonWithFlags(
            """
            { "entries": [ { "itemId": "D", "weight": 1 } ] }
            """
        );
        assertFalse(noReplace.replace());
    }
}
