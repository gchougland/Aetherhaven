package com.hexvane.aetherhaven.town;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.testsupport.GameItems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** What townsfolk ask for before moving in must be something a player can really bring. */
@Tag("town")
class TownsfolkMoveInItemsTest {
    private static final Path FOLDER = Path.of("src/main/resources/Server/Aetherhaven/Townsfolk");
    /**
     * Asked for today although the data shows no recipe, drop, trader, shop or production source. Reported to the owner
     * and left as they are. Remove an entry here when its item gains a source or stops being asked for.
     */
    private static final Set<String> REPORTED_WITHOUT_A_SOURCE = Set.of(
        "Ingredient_Stud_Iron", "Plant_Fruit_Poison");

    private static Map<String, JsonObject> townsfolk() throws Exception {
        var result = new TreeMap<String, JsonObject>();
        try (var list = Files.list(FOLDER)) {
            for (Path file : list.filter(p -> p.toString().endsWith(".json")).sorted().toList())
                result.put(file.getFileName().toString().replace(".json", ""), JsonParser.parseString(Files.readString(file)).getAsJsonObject());
        }
        return result;
    }
    private static Map<String, Integer> asks(JsonObject person) {
        var result = new LinkedHashMap<String, Integer>();
        if (person.has("moveInRequirements")) for (var entry : person.getAsJsonArray("moveInRequirements")) {
            var row = entry.getAsJsonObject();
            assertNull(result.put(row.get("itemId").getAsString(), row.get("count").getAsInt()), "Repeated move in item");
        }
        return result;
    }

    @Test void lightMotesAreReplacedByTenLifeEssenceEach() throws Exception {
        var all = townsfolk();
        for (var entry : all.entrySet())
            assertFalse(asks(entry.getValue()).containsKey("Ingredient_Motes_Light"), entry.getKey() + " still asks for Light Motes");
        assertEquals(Map.of("Ingredient_Life_Essence", 20, "Food_Cheese", 1), asks(all.get("female_elf_05")));
        assertEquals("Virel Ashsong", all.get("female_elf_05").get("displayName").getAsString());
        assertEquals(Map.of("Ingredient_Fabric_Scrap_Wool", 2, "Ingredient_Life_Essence", 10), asks(all.get("female_mannequin_01")));
        assertEquals("Lacey Stand", all.get("female_mannequin_01").get("displayName").getAsString());
        assertEquals(Map.of("Ingredient_Life_Essence", 10), asks(all.get("male_human_01")));
        assertEquals("Harlan Tidewalk", all.get("male_human_01").get("displayName").getAsString());
    }

    @Test void everyMoveInItemIsARealItemInASensibleAmount() throws Exception {
        var items = GameItems.loadOrNull();
        assumeTrue(items != null, "Installed Hytale assets needed to check item ids");
        int checked = 0;
        for (var entry : townsfolk().entrySet()) for (var ask : asks(entry.getValue()).entrySet()) {
            assertTrue(items.exists(ask.getKey()), entry.getKey() + " asks for an item that does not exist: " + ask.getKey());
            assertTrue(ask.getValue() >= 1 && ask.getValue() <= 64, entry.getKey() + " " + ask);
            checked++;
        }
        assertTrue(checked >= 60, "Move in lists were not read: " + checked);
    }

    @Test void everyMoveInItemCanBeObtainedOrIsOnTheReportedList() throws Exception {
        var items = GameItems.loadOrNull();
        assumeTrue(items != null, "Installed Hytale assets needed to check item sources");
        var missing = new TreeMap<String, Set<String>>();
        var asked = new TreeSet<String>();
        for (var entry : townsfolk().entrySet()) for (String id : asks(entry.getValue()).keySet()) {
            asked.add(id);
            if (!items.obtainable(id)) missing.computeIfAbsent(id, ignored -> new TreeSet<>()).add(entry.getKey());
        }
        assertTrue(items.obtainable("Ingredient_Life_Essence"), "Life Essence comes from every crop harvest");
        assertFalse(items.obtainable("Ingredient_Motes_Light"), "If Light Motes gain a source they may be asked for again");
        // Bronze bars and candy canes are asked for too. They are sold only at festival stalls, which counts.
        assertEquals(Set.of("barter"), items.sources("Ingredient_Bar_Bronze"));
        assertEquals(Set.of("barter"), items.sources("Food_Candy_Cane"));
        var unreported = new TreeMap<>(missing);
        unreported.keySet().removeAll(REPORTED_WITHOUT_A_SOURCE);
        assertTrue(unreported.isEmpty(), "Move in items with no recipe, drop, trader, shop or production source: " + unreported);
        for (String id : REPORTED_WITHOUT_A_SOURCE) {
            assertTrue(asked.contains(id), id + " is no longer asked for. Take it off the reported list.");
            assertTrue(missing.containsKey(id), id + " can now be obtained (" + items.sources(id) + "). Take it off the reported list.");
        }
    }
}
