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
    void windowsParseAndRebuild() {
        var village = BlockPaletteRemapper.parse("Furniture_Village_Window");
        assertNotNull(village);
        assertEquals(BlockPaletteConstants.CATEGORY_WINDOWS, village.category());
        assertEquals("Village", village.familyKey());
        assertEquals("", village.suffix());
        assertEquals("Furniture_Tavern_Window", BlockPaletteRemapper.rebuild(village, "Tavern"));
        assertEquals(
            "Furniture_Temple_Wind_Window",
            BlockPaletteRemapper.rebuild(BlockPaletteRemapper.parse("Furniture_Human_Ruins_Window"), "Temple_Wind")
        );
        assertNull(BlockPaletteRemapper.parse("Furniture_Cybercity_Windows_Full"));
        assertNull(BlockPaletteRemapper.parse("Prototype_Window_Single"));
    }

    @Test
    void clothRoofsAreSeparateCategory() {
        var cloth = BlockPaletteRemapper.parse("Cloth_Roof_Green");
        assertNotNull(cloth);
        assertEquals(BlockPaletteConstants.CATEGORY_CLOTH_ROOFS, cloth.category());
        assertEquals("Green", cloth.familyKey());
        assertEquals("Cloth_Roof_Blue", BlockPaletteRemapper.rebuild(cloth, "Blue"));
        assertEquals(
            "Cloth_Roof_Hide_Flat",
            BlockPaletteRemapper.rebuild(BlockPaletteRemapper.parse("Cloth_Roof_Green_Flat"), "Hide")
        );

        var wood = BlockPaletteRemapper.parse("Wood_Softwood_Roof");
        assertNotNull(wood);
        assertEquals(BlockPaletteConstants.CATEGORY_ROOFS, wood.category());
        // Solid roof palettes use wood:/cobble_roof:/brick_roof: keys; a bare cloth color is not a solid roof.
        assertNull(BlockPaletteRemapper.rebuild(wood, "Green"));
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

    @Test
    void quartziteCobbleBrickAndRoofsParseAndRebuild() {
        var cobble = BlockPaletteRemapper.parse("Rock_Quartzite_Cobble");
        assertNotNull(cobble);
        assertEquals(BlockPaletteConstants.CATEGORY_COBBLE, cobble.category());
        assertEquals("Quartzite", cobble.familyKey());
        assertEquals("Rock_Marble_Cobble", BlockPaletteRemapper.rebuild(cobble, "Marble"));

        var brick = BlockPaletteRemapper.parse("Rock_Quartzite_Brick_Stairs");
        assertNotNull(brick);
        assertEquals(BlockPaletteConstants.CATEGORY_BRICKS, brick.category());
        assertEquals("Quartzite", brick.familyKey());
        assertEquals("Rock_Stone_Brick_Stairs", BlockPaletteRemapper.rebuild(brick, "Stone"));

        var cobbleRoof = BlockPaletteRemapper.parse("Rock_Quartzite_Cobble_Roof_Flat");
        assertNotNull(cobbleRoof);
        assertEquals(BlockPaletteConstants.CATEGORY_ROOFS, cobbleRoof.category());
        assertEquals("cobble_roof:Quartzite", cobbleRoof.familyKey());
        assertEquals(
            "Rock_Stone_Brick_Roof_Flat",
            BlockPaletteRemapper.rebuild(cobbleRoof, "brick_roof:Stone")
        );

        var brickRoof = BlockPaletteRemapper.parse("Rock_Quartzite_Brick_Roof");
        assertNotNull(brickRoof);
        assertEquals("brick_roof:Quartzite", brickRoof.familyKey());
        assertEquals("Rock_Stone_Cobble_Roof", BlockPaletteRemapper.rebuild(brickRoof, "cobble_roof:Stone"));
    }

    @Test
    void modernClothRoofsAreSeparateCategory() {
        var modern = BlockPaletteRemapper.parse("Cloth_Modern_Blue_Roof_Flat");
        assertNotNull(modern);
        assertEquals(BlockPaletteConstants.CATEGORY_MODERN_CLOTH_ROOFS, modern.category());
        assertEquals("Blue", modern.familyKey());
        assertEquals("Flat", modern.suffix());
        assertEquals(
            "Cloth_Modern_Yellow_Roof_Flat",
            BlockPaletteRemapper.rebuild(modern, "Yellow")
        );
        assertEquals(
            "Cloth_Modern_DarkGreen_Roof",
            BlockPaletteRemapper.rebuild(BlockPaletteRemapper.parse("Cloth_Modern_Blue_Roof"), "DarkGreen")
        );

        var classic = BlockPaletteRemapper.parse("Cloth_Roof_Blue_Flat");
        assertNotNull(classic);
        assertEquals(BlockPaletteConstants.CATEGORY_CLOTH_ROOFS, classic.category());
    }

    @Test
    void metalRoofsParseAndRebuildWithinRoofsCategory() {
        var bronze = BlockPaletteRemapper.parse("Metal_Bronze_Roof");
        assertNotNull(bronze);
        assertEquals(BlockPaletteConstants.CATEGORY_ROOFS, bronze.category());
        assertEquals("metal_roof:Bronze", bronze.familyKey());
        assertEquals("Metal_Copper_Roof", BlockPaletteRemapper.rebuild(bronze, "metal_roof:Copper"));
        assertEquals(
            "Metal_Iron_Roof_Flat",
            BlockPaletteRemapper.rebuild(BlockPaletteRemapper.parse("Metal_Bronze_Roof_Flat"), "metal_roof:Iron")
        );
        assertEquals(
            "Metal_Bronze_Roof",
            BlockPaletteRemapper.rebuild(BlockPaletteRemapper.parse("Wood_Softwood_Roof"), "metal_roof:Bronze")
        );
    }

    @Test
    void hollowRoofsMapToRegularWhenTargetHasNoHollow() {
        var hollow = BlockPaletteRemapper.parse("Wood_Softwood_Roof_Hollow");
        assertNotNull(hollow);
        assertEquals(
            "Metal_Bronze_Roof",
            BlockPaletteRemapper.rebuild(hollow, "metal_roof:Bronze")
        );
        var hollowCorner =
            BlockPaletteRemapper.parse("Wood_Softwood_Roof_Hollow_State_Definitions_Corner_Left");
        assertNotNull(hollowCorner);
        assertEquals(
            "Metal_Bronze_Roof_State_Definitions_Corner_Left",
            BlockPaletteRemapper.rebuild(hollowCorner, "metal_roof:Bronze")
        );
        assertEquals("State_Definitions_Corner_Left", BlockPaletteRemapper.stripHollowRoofShape("Hollow_State_Definitions_Corner_Left"));
    }

    @Test
    void legacyHollowRoofCornersNormalizeAndRemap() {
        var legacy = BlockPaletteRemapper.parse("Wood_Softwood_Roof_Hollow_Corner");
        assertNotNull(legacy);
        assertEquals("Hollow_State_Definitions_Corner_Right", legacy.suffix());
        assertEquals(
            "Wood_Hardwood_Roof_Hollow_State_Definitions_Corner_Right",
            BlockPaletteRemapper.rebuild(legacy, "wood:Hardwood")
        );
        assertEquals(
            "Wood_Hardwood_Roof_Hollow",
            BlockPaletteRemapper.rebuild(BlockPaletteRemapper.parse("Wood_Softwood_Roof_Hollow"), "wood:Hardwood")
        );
    }

    private static void assertWall(String id, String family, String suffix) {
        var parsed = BlockPaletteRemapper.parse(id);
        assertNotNull(parsed, id);
        assertEquals(BlockPaletteConstants.CATEGORY_WALLS, parsed.category());
        assertEquals(family, parsed.familyKey());
        assertEquals(suffix, parsed.suffix());
    }
}
