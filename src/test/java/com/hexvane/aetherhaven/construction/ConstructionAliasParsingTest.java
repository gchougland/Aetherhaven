package com.hexvane.aetherhaven.construction;

import com.google.gson.Gson;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class ConstructionAliasParsingTest {
    private final Gson gson = new Gson();

    @Test void legacyAndArrayAliasesRetainTheirSavedShapeAfterLookup() {
        for (String aliases : List.of("\" plot_house \"", "[\"plot_house\",null,3,\" plot_inn \",\"plot_house\"]")) {
            var definition = gson.fromJson("{\"id\":\"custom\",\"countsAsConstructionId\":" + aliases + "}", ConstructionDefinition.class);
            String before = gson.toJson(definition);
            var parsed = definition.getCountsAsConstructionIds();
            assertEquals(aliases.startsWith("[") ? List.of("plot_house", "plot_inn") : List.of("plot_house"), parsed);
            assertSame(parsed, definition.getCountsAsConstructionIds());
            assertThrows(UnsupportedOperationException.class, () -> parsed.add("plot_barn"));
            assertEquals(before, gson.toJson(definition));
            assertEquals(parsed, gson.fromJson(before, ConstructionDefinition.class).getCountsAsConstructionIds());
        }
    }

    @Test void editingAliasesRefreshesParsedAndCatalogResults() {
        var definition = gson.fromJson("{\"id\":\"custom\",\"countsAsConstructionId\":\"plot_house\"}", ConstructionDefinition.class);
        var catalog = ConstructionCatalog.forTests(Map.of("custom", definition));
        assertEquals(List.of("plot_house"), catalog.resolveGameplayConstructionIds("custom"));
        definition.setCountsAsConstructionIds(List.of("plot_inn", "plot_barn", " plot_inn "));
        assertEquals(List.of("plot_inn", "plot_barn"), catalog.resolveGameplayConstructionIds("custom"));
        definition.setCountsAsConstructionIds(List.of("plot_market_stall"));
        assertEquals(List.of("plot_market_stall"), catalog.resolveGameplayConstructionIds("custom"));
        definition.setCountsAsConstructionIds(List.of(" "));
        assertEquals(List.of("custom"), catalog.resolveGameplayConstructionIds("custom"));
        definition.setCountsAsConstructionIds(null);
        assertTrue(definition.getCountsAsConstructionIds().isEmpty());
    }

    @Test void cachedResolutionTracksEveryAliasBranchAndIntermediateDefinition() {
        var root = definition("custom", "left", "right");
        var left = definition("left", "house");
        var right = definition("right", "inn");
        var inn = definition("inn");
        var catalog = ConstructionCatalog.forTests(Map.of("custom", root, "left", left, "right", right, "inn", inn));
        var original = catalog.resolveGameplayConstructionIds("custom");
        assertEquals(List.of("house", "inn"), original);
        assertSame(original, catalog.resolveGameplayConstructionIds(" custom "));
        assertThrows(UnsupportedOperationException.class, () -> original.add("barn"));
        inn.setCountsAsConstructionIds(List.of("barn"));
        assertEquals(List.of("house", "barn"), catalog.resolveGameplayConstructionIds("custom"));
        left.setCountsAsConstructionIds(List.of("barn"));
        assertEquals(List.of("barn"), catalog.resolveGameplayConstructionIds("custom"));
        right.setCountsAsConstructionIds(List.of("market"));
        assertEquals(List.of("barn", "market"), catalog.resolveGameplayConstructionIds("custom"));
        assertEquals(List.of("house", "inn"), original);
    }

    @Test void cyclesCanBeEditedAndReloadedWithoutReusingOldCatalogResults() {
        var a = definition("a", "b");
        var b = definition("b", "a");
        var catalog = ConstructionCatalog.forTests(Map.of("a", a, "b", b));
        assertEquals(List.of("b"), catalog.resolveGameplayConstructionIds("a"));
        b.setCountsAsConstructionIds(List.of("house"));
        assertEquals(List.of("house"), catalog.resolveGameplayConstructionIds("a"));
        var reloaded = ConstructionCatalog.forTests(Map.of("a", definition("a", "inn")));
        assertEquals(List.of("inn"), reloaded.resolveGameplayConstructionIds("a"));
        assertEquals(List.of("house"), catalog.resolveGameplayConstructionIds("a"));
        assertEquals(List.of("missing"), catalog.resolveGameplayConstructionIds(" missing "));
        assertEquals(List.of(), catalog.resolveGameplayConstructionIds(null));
        assertEquals(List.of(), catalog.resolveGameplayConstructionIds(" "));
    }

    private ConstructionDefinition definition(String id, String... aliases) {
        var result = gson.fromJson("{\"id\":\"" + id + "\"}", ConstructionDefinition.class);
        result.setCountsAsConstructionIds(List.of(aliases));
        return result;
    }
}
