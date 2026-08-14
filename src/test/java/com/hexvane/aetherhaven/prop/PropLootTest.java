package com.hexvane.aetherhaven.prop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("prop")
class PropLootTest {

    @Test
    void listEligible_skipsExcludedIds() {
        PropCatalog catalog = PropCatalog.empty();
        catalog.register(PropDefinition.create("fish_plaque", "Fish Plaque", "Props/Fish_Plaque.prefab.json"));
        catalog.register(PropDefinition.create("cabbage_trough", "Cabbage Trough", "Props/Cabbage_Trough.prefab.json"));
        catalog.register(PropDefinition.create("balloons", "Balloons", "Props/Balloons.prefab.json"));

        List<String> ids = PropLoot.listEligibleIds(catalog, Set.of("cabbage_trough", "balloons"));

        assertEquals(List.of("fish_plaque"), ids);
        assertFalse(ids.contains("cabbage_trough"));
        assertFalse(ids.contains("balloons"));
    }

    @Test
    void listEligible_emptyWhenAllExcluded() {
        PropCatalog catalog = PropCatalog.empty();
        catalog.register(PropDefinition.create("pig_pile", "Pig Pile", "Props/Pig_Pile.prefab.json"));

        assertTrue(PropLoot.listEligibleIds(catalog, Set.of("pig_pile")).isEmpty());
    }
}
