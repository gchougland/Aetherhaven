package com.hexvane.aetherhaven.plotcreator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("construction")
class LocalBuildingRemovalServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void countMatchingPlotsInTownFileMatchesConstructionId() throws Exception {
        Path townsFile = tempDir.resolve("towns.json");
        Files.writeString(
            townsFile,
            """
            {
              "towns": [
                {
                  "plotInstances": [
                    {
                      "plotId": "11111111-1111-1111-1111-111111111111",
                      "constructionId": "plot_my_house",
                      "state": "COMPLETE",
                      "minX": 0, "minY": 0, "minZ": 0,
                      "maxX": 1, "maxY": 1, "maxZ": 1,
                      "signX": 0, "signY": 0, "signZ": 0
                    },
                    {
                      "plotId": "22222222-2222-2222-2222-222222222222",
                      "constructionId": "plot_other",
                      "state": "BLUEPRINTING",
                      "minX": 0, "minY": 0, "minZ": 0,
                      "maxX": 1, "maxY": 1, "maxZ": 1,
                      "signX": 0, "signY": 0, "signZ": 0
                    }
                  ]
                }
              ]
            }
            """
        );
        ConstructionCatalog catalog = ConstructionCatalog.empty();
        assertEquals(1, LocalBuildingRemovalService.countMatchingPlotsInTownFile(catalog, "plot_my_house", townsFile));
    }

    @Test
    void deleteLocalFilesRemovesBuildingPrefabIconAndMaterials(@TempDir Path dataDir) throws Exception {
        String id = "plot_test_remove";
        Path building = CustomBuildingsPaths.buildingFile(dataDir, id);
        Path prefab = CustomBuildingsPaths.prefabsDirectory(dataDir).resolve(id + ".prefab.json");
        Path icon = CustomBuildingsPaths.iconFile(dataDir, id);
        Path materials = dataDir.resolve("Server/Aetherhaven/Buildings/PrefabMaterials/" + id + ".json");
        Files.createDirectories(building.getParent());
        Files.createDirectories(prefab.getParent());
        Files.createDirectories(icon.getParent());
        Files.createDirectories(materials.getParent());
        Files.writeString(building, "{}");
        Files.writeString(prefab, "{\"blocks\":[]}");
        Files.writeString(icon, "png");
        Files.writeString(materials, "{}");

        LocalBuildingRemovalService.deleteLocalFiles(dataDir, id, null, null);

        assertFalse(Files.exists(building));
        assertFalse(Files.exists(prefab));
        assertFalse(Files.exists(icon));
        assertFalse(Files.exists(materials));
    }

    @Test
    void sanitizeWorldDirNameReplacesInvalidCharacters() {
        assertEquals("My_World_2", LocalBuildingRemovalService.sanitizeWorldDirName("My World#2"));
    }
}
