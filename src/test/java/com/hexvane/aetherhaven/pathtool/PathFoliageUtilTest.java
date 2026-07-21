package com.hexvane.aetherhaven.pathtool;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PathFoliageUtilTest {
    @Test
    void plantGrassAndBushIds() {
        assertTrue(PathFoliageUtil.isPlantGrassId("Plant_Grass_Lush_Tall"));
        assertTrue(PathFoliageUtil.isPlantBushId("Plant_Bush_Green"));
        assertFalse(PathFoliageUtil.isPlantBushId("Plant_Grass_Lush"));
    }
}
