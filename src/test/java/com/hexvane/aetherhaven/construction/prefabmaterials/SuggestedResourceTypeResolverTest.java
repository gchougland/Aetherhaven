package com.hexvane.aetherhaven.construction.prefabmaterials;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hexvane.aetherhaven.construction.prefabmaterials.SuggestedResourceTypeResolver.Target;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("construction")
class SuggestedResourceTypeResolverTest {
    private static PrefabMaterialConversionTable conversions;

    @BeforeAll
    static void loadConversions() {
        conversions = PrefabMaterialConversionTable.loadFromClasspath(SuggestedResourceTypeResolverTest.class.getClassLoader());
    }

    @Test
    void rockStoneCobbleMapsToRock() {
        assertEquals("Rock", SuggestedResourceTypeResolver.resolveCanonicalResourceType("Rock_Stone_Cobble", null));
    }

    @Test
    void woodPlanksAndTrunkMapToWoodAll() {
        assertEquals("Wood_All", SuggestedResourceTypeResolver.resolveCanonicalResourceType("Wood_Softwood_Planks", null));
        assertEquals("Wood_All", SuggestedResourceTypeResolver.resolveCanonicalResourceType("Wood_Beech_Trunk", null));
    }

    @Test
    void rubbleMapsToRubble() {
        assertEquals("Rubble", SuggestedResourceTypeResolver.resolveCanonicalResourceType("Rubble_Stone", null));
    }

    @Test
    void benchIsSpecialtyItem() {
        Target target = SuggestedResourceTypeResolver.resolve("Bench_Armour", conversions);
        assertInstanceOf(Target.SpecialtyItem.class, target);
        assertEquals("Bench_Armour", ((Target.SpecialtyItem) target).itemId());
    }

    @Test
    void aetherhavenManagementBlockSkips() {
        Target target = SuggestedResourceTypeResolver.resolve("Aetherhaven_Management_Block", conversions);
        assertInstanceOf(Target.Skip.class, target);
    }

    @Test
    void clothBlockSkipsWithoutResourceType() {
        Target target = SuggestedResourceTypeResolver.resolve("Cloth_Block_Wool_White", conversions);
        assertInstanceOf(Target.Skip.class, target);
    }

    @Test
    void canonicalizeResourceTypeIdPrefersBroadTypes() {
        assertEquals("Wood_All", SuggestedResourceTypeResolver.canonicalizeResourceTypeId("Wood_Trunk"));
        assertEquals("Rock", SuggestedResourceTypeResolver.canonicalizeResourceTypeId("Rock_Stone"));
    }

    @Test
    void generatorCountsBenchAndSkipsFiller() {
        SuggestedResourceMaterialsGenerator generator = new SuggestedResourceMaterialsGenerator(conversions);
        String prefabJson =
            """
            {
              "blocks": [
                { "name": "Rock_Stone_Cobble" },
                { "name": "Wood_Softwood_Planks", "filler": 1 },
                { "name": "Bench_Armour" },
                { "name": "Aetherhaven_Management_Block" }
              ]
            }
            """;
        List<MaterialRequirement> materials = generator.generateFromPrefabJson(prefabJson);
        assertEquals(2, materials.size());
        assertTrue(
            materials.stream().anyMatch(m -> "Rock".equals(m.getResourceTypeId()) && m.getCount() == 1)
        );
        assertTrue(
            materials.stream().anyMatch(m -> "Bench_Armour".equals(m.getItemId()) && m.getCount() == 1)
        );
    }
}
