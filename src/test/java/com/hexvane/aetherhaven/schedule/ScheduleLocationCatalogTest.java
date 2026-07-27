package com.hexvane.aetherhaven.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
class ScheduleLocationCatalogTest {

    @Test
    void knowsBuiltInAndCustomSymbols() {
        ScheduleLocationDefinition loc = new ScheduleLocationDefinition();
        loc.setConstructionId("plot_fishing_shop");
        ScheduleLocationCatalog catalog =
            ScheduleLocationCatalog.forTests(Map.of("fishing_dock", loc));

        assertTrue(catalog.isKnownSymbol("work"));
        assertTrue(catalog.isKnownSymbol("fishing_dock"));
        assertFalse(catalog.isKnownSymbol("unknown_spot"));
        assertEquals("plot_fishing_shop", catalog.constructionIdForSymbol("fishing_dock"));
    }

    @Test
    void laterSymbolOverridesInForTestsMap() {
        ScheduleLocationDefinition first = new ScheduleLocationDefinition();
        first.setConstructionId("plot_a");
        ScheduleLocationDefinition second = new ScheduleLocationDefinition();
        second.setConstructionId("plot_b");
        Map<String, ScheduleLocationDefinition> map = new LinkedHashMap<>();
        map.put("dock", first);
        map.put("dock", second);
        ScheduleLocationCatalog catalog = ScheduleLocationCatalog.forTests(map);
        assertEquals("plot_b", catalog.constructionIdForSymbol("dock"));
    }
}
