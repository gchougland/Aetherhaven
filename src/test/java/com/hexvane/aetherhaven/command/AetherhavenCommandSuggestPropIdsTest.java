package com.hexvane.aetherhaven.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.prop.PropCatalog;
import com.hexvane.aetherhaven.prop.PropDefinition;
import com.hypixel.hytale.server.core.command.system.suggestion.SuggestionResult;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("prop")
class AetherhavenCommandSuggestPropIdsTest {

    @Test
    void emptyPartialListsCommunityDownloadsBeforeShippedProps() {
        PropCatalog catalog = PropCatalog.empty();
        catalog.register(PropDefinition.create("aqua_lamp", "Aqua Lamp", "Props/Aqua_Lamp.prefab.json"));
        catalog.register(
            PropDefinition.create(
                "prop_community_abcd1234_bench",
                "Community Bench",
                "Props/Prop_Community_Abcd1234_Bench.prefab.json"
            )
        );

        SuggestionResult result = new SuggestionResult();
        AetherhavenCommandSuggest.suggestPropIds(result, "", catalog);

        assertEquals("prop_community_abcd1234_bench", result.getSuggestions().get(0));
        assertTrue(result.getSuggestions().contains("aqua_lamp"));
    }

    @Test
    void communityDownloadsStayVisibleWhenTheClientCapsTheList() {
        PropCatalog catalog = PropCatalog.empty();
        for (char c = 'a'; c <= 'z'; c++) {
            String id = "alpha_" + c;
            catalog.register(PropDefinition.create(id, id, "Props/" + id + ".prefab.json"));
        }
        catalog.register(
            PropDefinition.create(
                "prop_community_abcd1234_bench",
                "Community Bench",
                "Props/Prop_Community_Abcd1234_Bench.prefab.json"
            )
        );

        SuggestionResult result = new SuggestionResult(20);
        AetherhavenCommandSuggest.suggestPropIds(result, "", catalog);

        assertTrue(result.getSuggestions().contains("prop_community_abcd1234_bench"));
        assertEquals("prop_community_abcd1234_bench", result.getSuggestions().get(0));
    }
}
