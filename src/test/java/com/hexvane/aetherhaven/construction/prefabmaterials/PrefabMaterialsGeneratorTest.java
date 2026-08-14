package com.hexvane.aetherhaven.construction.prefabmaterials;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.construction.MaterialRequirement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("construction")
class PrefabMaterialsGeneratorTest {
    private static PrefabMaterialsGenerator generator;

    @BeforeAll
    static void loadGenerator() {
        generator = PrefabMaterialsGenerator.fromClasspath(PrefabMaterialsGeneratorTest.class.getClassLoader());
    }

    @Test
    void skipsFillerAndEmptyBlocks() {
        String prefabJson =
            """
            {
              "blocks": [
                { "name": "Rock_Stone_Cobble", "x": 0, "y": 1, "z": 2 },
                { "name": "Empty" },
                { "name": "Wood_Softwood_Planks", "filler": 1 },
                { "name": "Bench_Armour" }
              ],
              "fluids": [
                { "name": "Water", "x": 9, "y": 9, "z": 9, "level": 0 }
              ]
            }
            """;
        List<MaterialRequirement> materials = generator.generateFromPrefabJson(prefabJson);
        assertEquals(2, materials.size());
        assertTrue(materials.stream().anyMatch(m -> "Rock_Stone_Cobble".equals(m.getItemId()) && m.getCount() == 1)
            || materials.stream().anyMatch(m -> "Rock".equals(m.getResourceTypeId()) && m.getCount() == 1));
        assertTrue(materials.stream().anyMatch(m -> "Bench_Armour".equals(m.getItemId()) && m.getCount() == 1));
    }

    @Test
    void missingBlocksArrayThrows() {
        assertThrows(IllegalArgumentException.class, () -> generator.generateFromPrefabJson("{\"version\":8}"));
    }

    @Test
    void extraFieldsAndFluidsDoNotChangeCounts(@TempDir Path dir) throws Exception {
        Path prefab = dir.resolve("tiny.prefab.json");
        Files.writeString(
            prefab,
            """
            {
              "version": 8,
              "blocks": [
                { "name": "Bench_Armour", "x": 1, "y": 2, "z": 3 },
                { "name": "Bench_Armour", "x": 4, "y": 5, "z": 6 }
              ],
              "fluids": [
                { "name": "Water", "x": 0, "y": 0, "z": 0, "level": 0 },
                { "name": "Water", "x": 1, "y": 0, "z": 0, "level": 0 }
              ]
            }
            """
        );
        List<MaterialRequirement> materials = generator.generateFromPrefabPath(prefab);
        assertEquals(1, materials.size());
        assertEquals("Bench_Armour", materials.getFirst().getItemId());
        assertEquals(2, materials.getFirst().getCount());
    }
}
