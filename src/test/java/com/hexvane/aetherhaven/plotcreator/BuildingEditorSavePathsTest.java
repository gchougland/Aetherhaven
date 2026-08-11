package com.hexvane.aetherhaven.plotcreator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
final class BuildingEditorSavePathsTest {
    @Test
    void prefabFileKeepsFestivalSubfolder() {
        Path root = Path.of("C:/pack");
        Path out = BuildingEditorSavePaths.prefabFile(root, "Festivals/Festival_Square.prefab.json");
        assertEquals(
            root.resolve("Server/Prefabs/Festivals/Festival_Square.prefab.json").normalize(),
            out.normalize()
        );
        assertEquals("Festival_Square.prefab.json", BuildingEditorSavePaths.prefabFileName("Festivals/Festival_Square.prefab.json"));
        assertEquals(
            "Festivals/Festival_Square.prefab.json",
            BuildingEditorSavePaths.prefabRelativeUnderPrefabs("Festivals/Festival_Square.prefab.json")
        );
    }

    @Test
    void prefabFileKeepsFlatBuildingKeys() {
        Path root = Path.of("C:/pack");
        Path out = BuildingEditorSavePaths.prefabFile(root, "plot_house.prefab.json");
        assertTrue(out.toString().replace('\\', '/').endsWith("Server/Prefabs/plot_house.prefab.json"));
    }

    @Test
    void festivalFileGoesUnderAetherhavenFestivals() {
        Path root = Path.of("C:/pack");
        Path out = BuildingEditorSavePaths.festivalFile(root, "carnival");
        assertEquals(root.resolve("Server/Aetherhaven/Festivals/carnival.json").normalize(), out.normalize());
    }
}
