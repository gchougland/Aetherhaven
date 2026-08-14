package com.hexvane.aetherhaven.floatinggift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.AetherhavenConstants;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("floating-gift")
class FloatingGiftBlueprintLootTest {

    @Test
    void merge_stripsLegacyManualBlueprints_andAddsSingleGenericEntry() {
        FloatingGiftLootTable manual =
            FloatingGiftLootTable.of(
                List.of(
                    new FloatingGiftLootTable.Entry(
                        AetherhavenConstants.PLOT_TOKEN_UNLOCK_PAGE,
                        "plot_legacy_manual",
                        5
                    ),
                    new FloatingGiftLootTable.Entry("Some_Other_Item", 3)
                )
            );

        FloatingGiftLootTable merged =
            FloatingGiftBlueprintLoot.mergeIntoRegularTable(
                manual,
                List.of("plot_balloon_a", "plot_balloon_b"),
                5
            );

        assertEquals(2, merged.entries().size());
        assertEquals("Some_Other_Item", merged.entries().get(0).itemId());
        assertEquals(AetherhavenConstants.PLOT_TOKEN_UNLOCK_PAGE, merged.entries().get(1).itemId());
        assertNull(merged.entries().get(1).constructionId());
        assertTrue(
            merged
                .entries()
                .stream()
                .noneMatch(e -> "plot_legacy_manual".equals(e.constructionId()))
        );
    }

    @Test
    void merge_withEmptyBlueprintPool_addsNoGenericEntry() {
        FloatingGiftLootTable manual =
            FloatingGiftLootTable.of(
                List.of(new FloatingGiftLootTable.Entry("Some_Other_Item", 3))
            );

        FloatingGiftLootTable merged =
            FloatingGiftBlueprintLoot.mergeIntoRegularTable(
                manual,
                List.of(),
                5
            );

        assertEquals(1, merged.entries().size());
        assertEquals("Some_Other_Item", merged.entries().get(0).itemId());
    }

    @Test
    void parseBundle_readsPlotBlueprintWeight() {
        String json =
            """
            {"version":2,"regular":{"plotBlueprintWeight":7,"entries":[]},"green":{"entries":[]},"red":{"entries":[]}}
            """;
        FloatingGiftLootBundle bundle = FloatingGiftLootBundle.parseJson(json);
        assertEquals(7, bundle.getRegularPlotBlueprintWeight());
    }

    @Test
    void parseBundle_readsFurnitureAndPropRolls() {
        String json =
            """
            {"version":4,"regular":{"entries":[]},"green":{"entries":[]},"red":{"furnitureRolls":2,"propRolls":1,"entries":[]}}
            """;
        FloatingGiftLootBundle bundle = FloatingGiftLootBundle.parseJson(json);
        assertEquals(2, bundle.getRedFurnitureRolls());
        assertEquals(1, bundle.getRedPropRolls());
    }

    @Test
    void parseBundle_defaultsPropRollsWhenMissing() {
        String json =
            """
            {"version":4,"regular":{"entries":[]},"green":{"entries":[]},"red":{"furnitureRolls":2,"entries":[]}}
            """;
        FloatingGiftLootBundle bundle = FloatingGiftLootBundle.parseJson(json);
        assertEquals(2, bundle.getRedFurnitureRolls());
        assertEquals(1, bundle.getRedPropRolls());
    }

    @Test
    void parseBundle_readsFillerEntriesAndRolls() {
        String json =
            """
            {"version":3,"filler":{"rollsMin":1,"rollsMax":2,"entries":[{"itemId":"Ore_Copper","weight":16},{"itemId":"Ore_Iron","weight":8}]},"regular":{"entries":[]},"green":{"entries":[]},"red":{"entries":[]}}
            """;
        FloatingGiftLootBundle bundle = FloatingGiftLootBundle.parseJson(json);
        assertEquals(1, bundle.getFillerRollsMin());
        assertEquals(2, bundle.getFillerRollsMax());
        assertEquals(2, bundle.getFillerTable().entries().size());
        assertEquals("Ore_Copper", bundle.getFillerTable().entries().get(0).itemId());
        assertEquals(16, bundle.getFillerTable().entries().get(0).weight());
    }

    @Test
    void parseTable_readsQuantityRange() {
        String json =
            """
            {"entries":[{"itemId":"Ore_Copper","weight":1,"quantityMin":1,"quantityMax":3}]}
            """;
        FloatingGiftLootTable table = FloatingGiftLootTable.parseJson(json);
        assertEquals(1, table.entries().get(0).quantityMin());
        assertEquals(3, table.entries().get(0).quantityMax());
    }

    @Test
    void parseTable_normalizesQuantityRange() {
        String json =
            """
            {"entries":[{"itemId":"Ore_Copper","weight":1,"quantityMin":5,"quantityMax":2}]}
            """;
        FloatingGiftLootTable table = FloatingGiftLootTable.parseJson(json);
        assertEquals(2, table.entries().get(0).quantityMin());
        assertEquals(5, table.entries().get(0).quantityMax());
    }

    @Test
    void parseTable_defaultsQuantityToOne() {
        String json =
            """
            {"entries":[{"itemId":"Ore_Copper","weight":1}]}
            """;
        FloatingGiftLootTable table = FloatingGiftLootTable.parseJson(json);
        assertEquals(1, table.entries().get(0).quantityMin());
        assertEquals(1, table.entries().get(0).quantityMax());
    }
}
