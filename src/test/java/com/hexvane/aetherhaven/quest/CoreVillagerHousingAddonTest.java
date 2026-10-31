package com.hexvane.aetherhaven.quest;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.dialogue.DialogueCatalog;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.data.VillagerDefinitionCatalog;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CoreVillagerHousingAddonTest {
    private static final Gson GSON = new Gson();
    @TempDir Path pack;

    @ParameterizedTest
    @ValueSource(strings = {"CozyTales-Fishing", "Machinaria", "both"})
    void authoredAddonHousingSurvivesCatalogLoadingAndReloading(String selection) throws Exception {
        verify(Path.of("src/test/resources/housing-addons"), selection);
    }

    @Test
    void currentAddonRepositoriesKeepTheirAuthoredHousing() throws Exception {
        Path siblings = Path.of("..");
        org.junit.jupiter.api.Assumptions.assumeTrue(
            Files.isDirectory(siblings.resolve("CozyTales-Fishing/src/main/resources/Server/Aetherhaven"))
                && Files.isDirectory(siblings.resolve("Machinaria/src/main/resources/Server/Aetherhaven")),
            "Live addon repositories are optional; the bundled regression assets always run");
        verify(siblings, "both");
    }

    private void verify(Path sources, String selection) throws Exception {
        Path catalogs = pack.resolve("Server/Aetherhaven");
        Files.createDirectories(catalogs);
        Files.writeString(catalogs.resolve("ClasspathAnchor.json"), "{}");
        for (String folder : List.of("Villagers", "Dialogue"))
            copyJson(Path.of("src/main/resources/Server/Aetherhaven").resolve(folder), catalogs.resolve(folder));
        copyJson(Path.of("subplugin-assets/Quests/Server/Aetherhaven/Quests"), catalogs.resolve("Quests"));
        for (String addon : List.of("CozyTales-Fishing", "Machinaria")) {
            if (!selection.equals("both") && !selection.equals(addon)) continue;
            Path root = sources.resolve(addon);
            if (Files.isDirectory(root.resolve("src/main/resources"))) root = root.resolve("src/main/resources");
            for (String folder : List.of("Villagers", "Dialogue", "Quests"))
                copyJson(root.resolve("Server/Aetherhaven").resolve(folder), catalogs.resolve(folder));
        }
        try (var loader = new URLClassLoader(new java.net.URL[]{pack.toUri().toURL()}, null)) {
            var villagers = VillagerDefinitionCatalog.loadFromAssetPacksOrClasspath(loader);
            var dialogues = DialogueCatalog.loadFromAssetPacksOrClasspath(loader);
            var original = QuestCatalog.loadFromAssetPacksOrClasspath(loader);
            var authoredDialogue = new LinkedHashMap<String, String>();
            for (String treeId : List.of("cozyfishing_fisherman", "aetherhaven_machinaria_mechanic")) {
                if (dialogues.get(treeId) != null) authoredDialogue.put(treeId, GSON.toJson(dialogues.get(treeId)));
            }
            assertEquals(selection.equals("both") ? 2 : 1, authoredDialogue.size());
            var quests = assertDoesNotThrow(() -> CoreVillagerHousing.ensure(
                villagers, ConstructionCatalog.empty(), original, dialogues));
            for (String[] resident : List.of(
                new String[]{"CozyFishing_Fisherman", "cozyfishing_fisherman", "q_house_fisherman"},
                new String[]{"Machinaria_Mechanic", "aetherhaven_machinaria_mechanic", "q_house_mechanic"})) {
                if (!authoredDialogue.containsKey(resident[1])) continue;
                var quest = quests.get(resident[2]);
                assertNotNull(quest);
                assertSame(original.get(resident[2]), quest, "Preserve authored IDs, prerequisites, objectives and rewards");
                assertEquals(1, quests.all().values().stream().filter(q -> CoreVillagerHousing.isHousingFor(q, resident[0])).count());
                assertTrue(CoreVillagerHousing.hasAction(dialogues.get(resident[1]), "start_quest", resident[2]));
                assertTrue(CoreVillagerHousing.hasAction(dialogues.get(resident[1]), "complete_quest", resident[2]));
                var town = new TownRecord();
                town.addActiveQuest(resident[2]);
                assertFalse(QuestDialogueEntry.isOfferable(quest, town), "Do not regrant an active addon house quest");
                town.completeQuest(resident[2]);
                assertFalse(QuestDialogueEntry.isOfferable(quest, town), "Keep completed addon house quests completed");
            }
            if (!selection.equals("CozyTales-Fishing")) {
                assertNotNull(villagers.byNpcRoleId("Machinaria_Mechanic_Rescue"));
                assertFalse(quests.all().values().stream().anyMatch(q -> CoreVillagerHousing.isHousingFor(q, "Machinaria_Mechanic_Rescue")));
            }
            assertNotNull(quests.get("q_house_alchemist"), "Addon metadata must not prevent core housing repair");
            String afterFirstLoad = GSON.toJson(dialogues.all());
            var reloaded = CoreVillagerHousing.ensure(villagers, ConstructionCatalog.empty(), quests, dialogues);
            assertEquals(quests.all().keySet(), reloaded.all().keySet());
            assertEquals(afterFirstLoad, GSON.toJson(dialogues.all()), "Reload must not add duplicate choices");
            authoredDialogue.forEach((id, json) -> assertEquals(json, GSON.toJson(dialogues.get(id)), id));
        }
    }

    private static void copyJson(Path source, Path destination) throws Exception {
        try (var files = Files.walk(source)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                Path target = destination.resolve(source.relativize(file));
                Files.createDirectories(target.getParent());
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }
}
