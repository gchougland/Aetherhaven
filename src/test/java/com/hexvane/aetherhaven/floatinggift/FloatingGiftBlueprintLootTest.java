package com.hexvane.aetherhaven.floatinggift;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.AetherhavenConstants;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
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

    @Test
    void buildPool_excludesBlueprintsOwnerAlreadyUnlocked() {
        FloatingGiftLootTable table = blueprintOnlyTable("plot_a", "plot_b", "plot_c");
        Set<String> unlocked =
            Set.of(
                FloatingGiftBlueprintLoot.normalizeConstructionId("plot_a"),
                FloatingGiftBlueprintLoot.normalizeConstructionId("plot_b")
            );

        List<FloatingGiftLootTable.Entry> pool =
            FloatingGiftBlueprintLoot.buildFilteredRollPool(
                table,
                constructionId -> unlocked.contains(FloatingGiftBlueprintLoot.normalizeConstructionId(constructionId))
            );

        assertEquals(List.of("plot_c"), blueprintConstructionIds(pool));
    }

    @Test
    void buildPool_whenAllBlueprintsOwned_includesEveryBlueprintForDuplicateRolls() {
        FloatingGiftLootTable table = blueprintOnlyTable("plot_a", "plot_b", "plot_c");
        Set<String> unlocked =
            Set.of(
                FloatingGiftBlueprintLoot.normalizeConstructionId("plot_a"),
                FloatingGiftBlueprintLoot.normalizeConstructionId("plot_b"),
                FloatingGiftBlueprintLoot.normalizeConstructionId("plot_c")
            );

        List<FloatingGiftLootTable.Entry> pool =
            FloatingGiftBlueprintLoot.buildFilteredRollPool(
                table,
                constructionId -> unlocked.contains(FloatingGiftBlueprintLoot.normalizeConstructionId(constructionId))
            );

        assertEquals(Set.of("plot_a", "plot_b", "plot_c"), Set.copyOf(blueprintConstructionIds(pool)));
    }

    @Test
    void buildPool_withNoUnlocks_matchesUnfilteredBlueprintTable() {
        FloatingGiftLootTable table = blueprintOnlyTable("plot_a", "plot_b");

        List<FloatingGiftLootTable.Entry> pool =
            FloatingGiftBlueprintLoot.buildFilteredRollPool(table, constructionId -> false);

        assertEquals(blueprintConstructionIds(table.entries()), blueprintConstructionIds(pool));
    }

    @Test
    void buildPool_keepsNonBlueprintEntries() {
        FloatingGiftLootTable table =
            FloatingGiftLootTable.of(
                List.of(
                    new FloatingGiftLootTable.Entry("Some_Other_Item", 3),
                    new FloatingGiftLootTable.Entry(AetherhavenConstants.PLOT_TOKEN_UNLOCK_PAGE, "plot_a", 5),
                    new FloatingGiftLootTable.Entry(AetherhavenConstants.PLOT_TOKEN_UNLOCK_PAGE, "plot_b", 5)
                )
            );
        Set<String> unlocked = Set.of(FloatingGiftBlueprintLoot.normalizeConstructionId("plot_a"));

        List<FloatingGiftLootTable.Entry> pool =
            FloatingGiftBlueprintLoot.buildFilteredRollPool(
                table,
                constructionId -> unlocked.contains(FloatingGiftBlueprintLoot.normalizeConstructionId(constructionId))
            );

        assertEquals(2, pool.size());
        assertEquals("Some_Other_Item", pool.get(0).itemId());
        assertEquals("plot_b", pool.get(1).constructionId());
    }

    @Nonnull
    private static FloatingGiftLootTable blueprintOnlyTable(@Nonnull String... constructionIds) {
        List<FloatingGiftLootTable.Entry> entries =
            Arrays.stream(constructionIds)
                .map(id -> new FloatingGiftLootTable.Entry(AetherhavenConstants.PLOT_TOKEN_UNLOCK_PAGE, id, 5))
                .toList();
        return FloatingGiftLootTable.of(entries);
    }

    @Nonnull
    private static List<String> blueprintConstructionIds(@Nonnull List<FloatingGiftLootTable.Entry> entries) {
        return entries
            .stream()
            .map(FloatingGiftLootTable.Entry::constructionId)
            .filter(id -> id != null && !id.isBlank())
            .collect(Collectors.toList());
    }
}
