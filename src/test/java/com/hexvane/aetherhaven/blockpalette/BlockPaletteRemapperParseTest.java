package com.hexvane.aetherhaven.blockpalette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
public final class BlockPaletteRemapperParseTest {
    @Test
    void emptySelectionsLeaveIdUnchanged() {
        assertEquals(
            "Wood_Village_Wall_Blue_Full",
            BlockPaletteRemapper.remapBlockTypeId("Wood_Village_Wall_Blue_Full", java.util.Map.of())
        );
    }

    @Test
    void unknownBlockUnchanged() {
        assertEquals(
            "Furniture_Chair",
            BlockPaletteRemapper.remapBlockTypeId("Furniture_Chair", java.util.Map.of("walls", "walls_red"))
        );
    }

    @Test
    void wallsParseConnectedAndMiddleForms() {
        assertWall("Wood_Village_Wall_Full_State_Definitions_Middle", "", "Full_State_Definitions_Middle");
        assertWall("Wood_Village_Wall_Blue_Full_State_Definitions_Top", "Blue", "Full_State_Definitions_Top");
        assertWall("Wood_Village_Wall_Blue_Middle", "Blue", "Middle");
        assertWall("Wood_Village_Wall_Blue", "Blue", "");
        assertWall("Wood_Village_Wall_N_Blue", "Blue", "N_Blue");
        assertWall("Wood_Village_Wall_U_RedDark", "RedDark", "U_RedDark");
    }

    @Test
    void starredStateIdsParseLikeBare() {
        assertWall("*Wood_Village_Wall_Full_State_Definitions_Bottom", "", "Full_State_Definitions_Bottom");
        assertWall("*Wood_Village_Wall_Blue_Full_State_Definitions_Top", "Blue", "Full_State_Definitions_Top");
        var roof = BlockPaletteRemapper.parse("*Wood_Softwood_Roof_State_Definitions_Corner_Left");
        assertNotNull(roof);
        assertEquals("wood:Softwood", roof.familyKey());
        assertEquals("State_Definitions_Corner_Left", roof.suffix());
        var stairs = BlockPaletteRemapper.parse("*Wood_Softwood_Stairs_State_Definitions_Corner_Right");
        assertNotNull(stairs);
        assertEquals(BlockPaletteConstants.CATEGORY_PLANKS, stairs.category());
        assertEquals("Softwood", stairs.familyKey());
        assertEquals("Stairs_State_Definitions_Corner_Right", stairs.suffix());
    }

    @Test
    void wallsRebuildPreservesConnectedSuffix() {
        var parsed = BlockPaletteRemapper.parse("Wood_Village_Wall_Green_Full_State_Definitions_Bottom");
        assertNotNull(parsed);
        assertEquals(
            "Wood_Village_Wall_Red_Full_State_Definitions_Bottom",
            BlockPaletteRemapper.rebuild(parsed, "Red")
        );
        var natural = BlockPaletteRemapper.parse("Wood_Village_Wall_Full_State_Definitions_Top");
        assertNotNull(natural);
        assertEquals(
            "Wood_Village_Wall_Blue_Full_State_Definitions_Top",
            BlockPaletteRemapper.rebuild(natural, "Blue")
        );
        var starred = BlockPaletteRemapper.parse("*Wood_Village_Wall_Full_State_Definitions_Middle");
        assertNotNull(starred);
        assertEquals(
            "Wood_Village_Wall_Orange_Full_State_Definitions_Middle",
            BlockPaletteRemapper.rebuild(starred, "Orange")
        );
    }

    @Test
    void roofsPreserveCornerStateSuffix() {
        var parsed = BlockPaletteRemapper.parse("Wood_Softwood_Roof_Hollow_State_Definitions_Corner_Left");
        assertNotNull(parsed);
        assertEquals("wood:Softwood", parsed.familyKey());
        assertEquals("Hollow_State_Definitions_Corner_Left", parsed.suffix());
        assertEquals(
            "Wood_Hardwood_Roof_Hollow_State_Definitions_Corner_Left",
            BlockPaletteRemapper.rebuild(parsed, "wood:Hardwood")
        );
    }

    @Test
    void solidRoofsSwapAcrossWoodCobbleAndBrick() {
        var cobble = BlockPaletteRemapper.parse("Rock_Basalt_Cobble_Roof");
        assertNotNull(cobble);
        assertEquals(BlockPaletteConstants.CATEGORY_ROOFS, cobble.category());
        assertEquals(
            "Wood_Softwood_Roof",
            BlockPaletteRemapper.rebuild(cobble, "wood:Softwood")
        );
        assertEquals(
            "Rock_Stone_Brick_Roof",
            BlockPaletteRemapper.rebuild(cobble, "brick_roof:Stone")
        );
        var hollow = BlockPaletteRemapper.parse("Rock_Basalt_Cobble_Roof_Hollow");
        assertNotNull(hollow);
        assertEquals(
            "Wood_Hardwood_Roof_Hollow",
            BlockPaletteRemapper.rebuild(hollow, "wood:Hardwood")
        );
        var wood = BlockPaletteRemapper.parse("Wood_Softwood_Roof_Flat");
        assertNotNull(wood);
        assertEquals(
            "Rock_Stone_Cobble_Roof_Flat",
            BlockPaletteRemapper.rebuild(wood, "cobble_roof:Stone")
        );
    }

    @Test
    void clothRoofsDoNotSwapWithSolidRoofs() {
        var cloth = BlockPaletteRemapper.parse("Cloth_Roof_Green");
        assertNotNull(cloth);
        assertNull(BlockPaletteRemapper.rebuild(cloth, "wood:Softwood"));
        assertNull(BlockPaletteRemapper.rebuild(cloth, "cobble_roof:Stone"));
        assertEquals("Cloth_Roof_Blue", BlockPaletteRemapper.rebuild(cloth, "cloth:Blue"));

        var wood = BlockPaletteRemapper.parse("Wood_Softwood_Roof");
        assertNotNull(wood);
        assertNull(BlockPaletteRemapper.rebuild(wood, "cloth:Green"));

        var cobble = BlockPaletteRemapper.parse("Rock_Basalt_Cobble_Roof");
        assertNotNull(cobble);
        assertNull(BlockPaletteRemapper.rebuild(cobble, "cloth:Hide"));
    }

    @Test
    void cobbleAndPlanksCategoriesDoNotClaimRoofs() {
        assertEquals(
            BlockPaletteConstants.CATEGORY_ROOFS,
            BlockPaletteRemapper.parse("Rock_Basalt_Cobble_Roof").category()
        );
        assertEquals(
            BlockPaletteConstants.CATEGORY_COBBLE,
            BlockPaletteRemapper.parse("Rock_Basalt_Cobble").category()
        );
        assertEquals(
            BlockPaletteConstants.CATEGORY_ROOFS,
            BlockPaletteRemapper.parse("Wood_Softwood_Roof").category()
        );
        assertEquals(
            BlockPaletteConstants.CATEGORY_PLANKS,
            BlockPaletteRemapper.parse("Wood_Softwood_Planks").category()
        );
    }

    @Test
    void fencesPreserveStateSuffix() {
        var parsed = BlockPaletteRemapper.parse("Wood_Hardwood_Fence_State_Definitions_Corner");
        assertNotNull(parsed);
        assertEquals(BlockPaletteConstants.CATEGORY_PLANKS, parsed.category());
        assertEquals("Hardwood", parsed.familyKey());
        assertEquals("Fence_State_Definitions_Corner", parsed.suffix());
        assertEquals(
            "Wood_Softwood_Fence_State_Definitions_Corner",
            BlockPaletteRemapper.rebuild(parsed, "Softwood")
        );
    }

    @Test
    void stripStateDefinitionSegments() {
        assertEquals(
            "Wood_Village_Wall_Blue_Full",
            BlockPaletteRemapper.stripStateDefinitionSegments(
                "Wood_Village_Wall_Blue_Full_State_Definitions_Middle"
            )
        );
        assertEquals(
            "Wood_Softwood_Stairs",
            BlockPaletteRemapper.stripStateDefinitionSegments(
                "Wood_Softwood_Stairs_State_Definitions_Corner_Right"
            )
        );
        assertEquals(
            "Wood_Village_Wall_Full",
            BlockPaletteRemapper.stripStateDefinitionSegments("Wood_Village_Wall_Full_State_Definition_Bottom")
        );
    }

    @Test
    void stripMossySegments() {
        assertEquals("Rock_Basalt_Cobble", BlockPaletteRemapper.stripMossySegments("Rock_Basalt_Cobble_Mossy"));
        assertEquals(
            "Rock_Basalt_Cobble_Half", BlockPaletteRemapper.stripMossySegments("Rock_Basalt_Cobble_Mossy_Half"));
    }

    @Test
    void azureTrunkParses() {
        var parsed = BlockPaletteRemapper.parse("Wood_Azure_Trunk");
        assertNotNull(parsed);
        assertEquals(BlockPaletteConstants.CATEGORY_TRUNKS, parsed.category());
        assertEquals("Azure", parsed.familyKey());
        assertEquals("Wood_Oak_Trunk", BlockPaletteRemapper.rebuild(parsed, "Oak"));
    }

    @Test
    void numberedWallsAreIgnored() {
        assertNull(BlockPaletteRemapper.parse("Wood_Village_Wall_02_Top"));
    }

    private static void assertWall(String id, String family, String suffix) {
        var parsed = BlockPaletteRemapper.parse(id);
        assertNotNull(parsed, id);
        assertEquals(BlockPaletteConstants.CATEGORY_WALLS, parsed.category());
        assertEquals(family, parsed.familyKey());
        assertEquals(suffix, parsed.suffix());
    }
}
