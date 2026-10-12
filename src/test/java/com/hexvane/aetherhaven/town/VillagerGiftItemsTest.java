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

/** Gift preferences: real items only, one feeling per item, and enough of them for every core villager. */
@Tag("town")
class VillagerGiftItemsTest {
    private static final List<String> LISTS = List.of("giftLoves", "giftLikes", "giftDislikes");
    private static final List<String> NEWER = List.of("Warehouse_Manager", "Alchemist", "Druid", "Doctor", "Monster_Hunter",
        "Crafter", "Leatherworker", "Halloween");
    private static final Path CORE = Path.of("src/main/resources/Server/Aetherhaven/Villagers");

    private static List<Path> villagerFiles() throws Exception {
        var folders = new ArrayList<Path>();
        folders.add(CORE);
        Path optional = Path.of("subplugin-assets");
        if (Files.isDirectory(optional)) try (var packs = Files.list(optional)) {
            for (Path pack : packs.sorted().toList()) {
                Path folder = pack.resolve("Server/Aetherhaven/Villagers");
                if (Files.isDirectory(folder)) folders.add(folder);
            }
        }
        var files = new ArrayList<Path>();
        for (Path folder : folders) try (var list = Files.list(folder)) {
            files.addAll(list.filter(p -> p.toString().endsWith(".json")).sorted().toList());
        }
        return files;
    }
    private static JsonObject read(Path file) throws Exception {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }
    private static List<String> ids(JsonObject villager, String list) {
        var result = new ArrayList<String>();
        if (villager.has(list) && villager.get(list).isJsonArray()) for (var id : villager.getAsJsonArray(list)) result.add(id.getAsString());
        return result;
    }

    @Test void everyGiftItemInEveryVillagerFileIsARealItem() throws Exception {
        var items = GameItems.loadOrNull();
        assumeTrue(items != null, "Installed Hytale assets needed to check item ids");
        var unknown = new TreeMap<String, Set<String>>();
        int checked = 0;
        for (Path file : villagerFiles()) {
            var villager = read(file);
            for (String list : LISTS) for (String id : ids(villager, list)) {
                checked++;
                if (!items.exists(id)) unknown.computeIfAbsent(id, ignored -> new TreeSet<>()).add(file.getFileName() + " " + list);
            }
        }
        assertTrue(checked > 3000, "Gift lists were not read: " + checked);
        assertTrue(unknown.isEmpty(), "Gift items that do not exist: " + unknown);
    }

    @Test void noVillagerFeelsTwoWaysAboutOneItem() throws Exception {
        for (Path file : villagerFiles()) {
            var villager = read(file);
            var seen = new HashMap<String, String>();
            for (String list : LISTS) for (String id : ids(villager, list)) {
                assertFalse(id.isBlank(), file + " has a blank gift item");
                assertEquals(id, id.strip(), file.toString());
                String earlier = seen.put(id, list);
                assertNull(earlier, file.getFileName() + " lists " + id + " under " + earlier + " and " + list);
            }
        }
    }

    @Test void theNewerVillagersHaveAsManyPreferencesAsAHandWrittenOlderOne() throws Exception {
        // The Chef is an older villager whose lists were written by hand for the character.
        var chef = read(CORE.resolve("Aetherhaven_Chef.json"));
        for (String role : NEWER) {
            var villager = read(CORE.resolve("Aetherhaven_" + role + ".json"));
            assertTrue(ids(villager, "giftLoves").size() >= 12, role + " loves " + ids(villager, "giftLoves").size());
            assertTrue(ids(villager, "giftLikes").size() >= 30, role + " likes " + ids(villager, "giftLikes").size());
            assertTrue(ids(villager, "giftDislikes").size() >= 20, role + " dislikes " + ids(villager, "giftDislikes").size());
            for (String list : LISTS) assertTrue(ids(villager, list).size() >= ids(chef, list).size(), role + " " + list);
        }
    }

    @Test void whatANewerVillagerLovesCanActuallyBeFound() throws Exception {
        var items = GameItems.loadOrNull();
        assumeTrue(items != null, "Installed Hytale assets needed to check item sources");
        var unreachable = new TreeSet<String>();
        for (String role : NEWER) {
            var villager = read(CORE.resolve("Aetherhaven_" + role + ".json"));
            for (String id : ids(villager, "giftLoves")) if (!items.obtainable(id)) unreachable.add(role + " " + id);
        }
        assertTrue(unreachable.isEmpty(), "Loved gifts with no recipe, drop, shop or production source: " + unreachable);
    }
}
