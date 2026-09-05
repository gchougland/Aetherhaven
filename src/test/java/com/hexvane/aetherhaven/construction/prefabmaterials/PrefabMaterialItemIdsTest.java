package com.hexvane.aetherhaven.construction.prefabmaterials;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hexvane.aetherhaven.construction.MaterialRequirement;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("construction")
class PrefabMaterialItemIdsTest {
    @Test
    void normalizeMapsTrunkFullToTrunk() {
        assertEquals("Wood_Beech_Trunk", PrefabMaterialItemIds.normalize("Wood_Beech_Trunk_Full"));
        assertEquals("Wood_Bamboo_Trunk_Deco", PrefabMaterialItemIds.normalize("Wood_Bamboo_Trunk_Full_Deco"));
    }

    @Test
    void mergeNormalizedCombinesTrunkFullWithTrunk() {
        List<MaterialRequirement> merged =
            PrefabMaterialItemIds.mergeNormalized(
                List.of(
                    MaterialRequirement.ofItem("Wood_Beech_Trunk", 35),
                    MaterialRequirement.ofItem("Wood_Beech_Trunk_Full", 9)
                )
            );
        assertEquals(1, merged.size());
        assertEquals("Wood_Beech_Trunk", merged.get(0).getItemId());
        assertEquals(44, merged.get(0).getCount());
    }

    @Test
    void mergeNormalizedMapsLargeChestToTwoSmall() {
        List<MaterialRequirement> merged =
            PrefabMaterialItemIds.mergeNormalized(
                List.of(
                    MaterialRequirement.ofItem("Furniture_Village_Chest_Large", 3),
                    MaterialRequirement.ofItem("Furniture_Village_Chest_Small", 1),
                    MaterialRequirement.ofItem("Furniture_Tavern_Chest_Large", 1)
                )
            );
        assertEquals(2, merged.size());
        assertEquals(
            7,
            merged.stream()
                .filter(m -> "Furniture_Village_Chest_Small".equals(m.getItemId()))
                .mapToInt(MaterialRequirement::getCount)
                .sum()
        );
        assertEquals(
            2,
            merged.stream()
                .filter(m -> "Furniture_Tavern_Chest_Small".equals(m.getItemId()))
                .mapToInt(MaterialRequirement::getCount)
                .sum()
        );
    }

    @Test
    void prefabBlockNormalizerMapsTrunkFullBlocks() {
        assertEquals("Wood_Oak_Trunk", PrefabBlockNormalizer.normalizeBlockToItemId("Wood_Oak_Trunk_Full"));
    }
}
