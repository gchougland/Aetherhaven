package com.hexvane.aetherhaven.transfer;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("construction")
class SaveContentTransferTest {
    @TempDir Path temp;

    private Path save(String name) throws IOException {
        Path save = temp.resolve("Saves").resolve(name);
        Files.createDirectories(save.resolve("universe"));
        write(save, "config.json", "{}");
        Files.createDirectories(save.resolve("mods/Hexvane_Aetherhaven"));
        return save;
    }
    private Path data(Path save) { return save.resolve("mods/Hexvane_Aetherhaven"); }
    private void write(Path root, String relative, String content) throws IOException {
        Path file = root.resolve(relative); Files.createDirectories(file.getParent()); Files.writeString(file, content);
    }
    private void building(Path root, String id, String prefab) throws IOException {
        write(root, "Server/Aetherhaven/Buildings/" + id + ".json", "{\"id\":\"" + id + "\",\"prefabPath\":\"" + prefab + "\"}");
        write(root, "Server/Prefabs/" + prefab, "prefab");
        write(root, "Common/Icons/ItemsGenerated/Aetherhaven_Token_" + id + ".png", "icon");
    }
    private SaveContentTransfer.Target target(Path save) { return new SaveContentTransfer.Target("Other", save); }

    @Test void transfersDownloadedPropsWithDependenciesWithoutTownOrBuildingData() throws Exception {
        Path source = data(save("Current")), other = save("Other");
        building(source, "cottage", "Cottage.prefab.json");
        write(source, "Server/Aetherhaven/Props/prop_community_test.json",
            "{\"id\":\"prop_community_test\",\"prefabPath\":\"Props/Bench.prefab.json\",\"iconPath\":\"Icons/ItemsGenerated/Aetherhaven_Prop_Prop_Community_Test.png\"}");
        write(source, "Server/Prefabs/Props/Bench.prefab.json", "bench");
        write(source, "Community/Common/Icons/ItemsGenerated/Aetherhaven_Prop_Prop_Community_Test.png", "bench icon");
        write(source, "Community/.install-meta/prop_community_test.json", "{\"version\":\"3\"}");
        write(source, "worlds/default/towns.json", "private town");
        write(source, "config.json", "settings");
        write(source, "community_install_instance.json", "identity");
        var result = SaveContentTransfer.transfer(source, target(other), Set.of(SaveContentTransfer.Category.PROPS), false);
        assertEquals(1, result.copied()); assertEquals(0, result.failed());
        assertEquals("bench", Files.readString(data(other).resolve("Server/Prefabs/Props/Bench.prefab.json")));
        assertTrue(Files.exists(data(other).resolve("Community/Common/Icons/ItemsGenerated/Aetherhaven_Prop_Prop_Community_Test.png")));
        assertTrue(Files.exists(data(other).resolve("Community/.install-meta/prop_community_test.json")));
        assertFalse(Files.exists(data(other).resolve("Server/Aetherhaven/Buildings")));
        assertFalse(Files.exists(data(other).resolve("worlds")));
        assertFalse(Files.exists(data(other).resolve("config.json")));
        assertFalse(Files.exists(data(other).resolve("community_install_instance.json")));
    }

    @Test void existingConflictSkipsWholeBuildingUnlessOverwriteSelected() throws Exception {
        Path source = data(save("Current")), other = save("Other");
        building(source.resolve("Community"), "house", "House.prefab.json");
        String definition = "Community/Server/Aetherhaven/Buildings/house.json";
        write(data(other), definition, "old definition");
        var skipped = SaveContentTransfer.transfer(source, target(other), Set.of(SaveContentTransfer.Category.BUILDINGS), false);
        assertEquals(1, skipped.skipped());
        assertEquals("old definition", Files.readString(data(other).resolve(definition)));
        assertFalse(Files.exists(data(other).resolve("Community/Server/Prefabs/House.prefab.json")));
        var copied = SaveContentTransfer.transfer(source, target(other), Set.of(SaveContentTransfer.Category.BUILDINGS), true);
        assertEquals(1, copied.copied()); assertEquals(0, copied.failed());
        assertEquals(Files.readString(source.resolve(definition)), Files.readString(data(other).resolve(definition)));
        assertTrue(Files.exists(source.resolve(definition)), "Copy must leave originals intact");
    }

    @Test void configSelectionUsesAllowlistAndPreservesDestinationTown() throws Exception {
        Path source = data(save("Current")), other = save("Other");
        for (String file : List.of("config.json", "shop_prices.json", "shop_loot/farmer.json")) write(source, file, "settings");
        for (String file : List.of("worlds/default/towns.json", "villager_audit/default/audit.jsonl", "community_install_instance.json", "config.json.bak")) write(source, file, "excluded");
        write(data(other), "worlds/default/towns.json", "destination town");
        var result = SaveContentTransfer.transfer(source, target(other), Set.of(SaveContentTransfer.Category.CONFIG), true);
        assertEquals(3, result.copied());
        assertEquals("destination town", Files.readString(data(other).resolve("worlds/default/towns.json")));
        assertFalse(Files.exists(data(other).resolve("community_install_instance.json")));
        assertFalse(Files.exists(data(other).resolve("villager_audit")));
    }

    @Test void twoBuildingsCanShareAnIdenticalPrefabWithoutEnablingOverwrite() throws Exception {
        Path source = data(save("Current")), other = save("Other");
        building(source, "one", "Shared.prefab.json"); building(source, "two", "Shared.prefab.json");
        var result = SaveContentTransfer.transfer(source, target(other), Set.of(SaveContentTransfer.Category.BUILDINGS), false);
        assertEquals(2, result.copied()); assertEquals(0, result.skipped());
    }

    @Test void invalidReferencesAndSameSaveAreRejectedBeforeCopying() throws Exception {
        Path current = save("Current"), source = data(current), other = save("Other");
        write(source, "Server/Aetherhaven/Props/bad.json", "{\"id\":\"bad\",\"prefabPath\":\"../../worlds/towns.prefab.json\"}");
        assertThrows(IOException.class, () -> SaveContentTransfer.transfer(source, target(other), Set.of(SaveContentTransfer.Category.PROPS), true));
        assertThrows(IOException.class, () -> SaveContentTransfer.transfer(source, target(current), Set.of(SaveContentTransfer.Category.PROPS), true));
        assertFalse(Files.exists(data(other).resolve("Server")));
        assertThrows(IOException.class, () -> SaveContentTransfer.checked(source, source.resolve("../escape")));
    }

    @Test void failedBundleRestoresEarlierFilesAndReportsFailure() throws Exception {
        Path source = data(save("Current")), other = save("Other");
        building(source, "house", "House.prefab.json");
        String definition = "Server/Aetherhaven/Buildings/house.json";
        write(data(other), definition, "old definition");
        write(data(other), "Server/Prefabs/House.prefab.json/occupied", "obstruction");
        var result = SaveContentTransfer.transfer(source, target(other), Set.of(SaveContentTransfer.Category.BUILDINGS), true);
        assertEquals(1, result.failed()); assertEquals(0, result.copied());
        assertEquals("old definition", Files.readString(data(other).resolve(definition)));
        assertEquals("obstruction", Files.readString(data(other).resolve("Server/Prefabs/House.prefab.json/occupied")));
    }

    @Test void discoveryIncludesOnlyOtherRealSaveFolders() throws Exception {
        Path current = save("Current"), other = save("Other");
        Files.createDirectories(temp.resolve("Saves/NotASave"));
        var targets = SaveContentTransfer.discoverTargets(current, List.of(temp.resolve("Saves")));
        assertEquals(List.of(other.toRealPath()), targets.stream().map(SaveContentTransfer.Target::save).toList());
    }
}
