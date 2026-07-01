package com.hexvane.aetherhaven.floatinggift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.AetherhavenConstants;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("floating-gift")
class FloatingGiftBlueprintLootTest {

    @Test
    void merge_stripsLegacyManualBlueprints_andAddsCatalogFlaggedBuildings() {
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

        assertEquals(3, merged.entries().size());
        assertEquals("Some_Other_Item", merged.entries().get(0).itemId());
        assertEquals("plot_balloon_a", merged.entries().get(1).constructionId());
        assertEquals("plot_balloon_b", merged.entries().get(2).constructionId());
        assertTrue(
            merged
                .entries()
                .stream()
                .noneMatch(e -> "plot_legacy_manual".equals(e.constructionId()))
        );
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
}
