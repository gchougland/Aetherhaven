package com.hexvane.aetherhaven.production;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("town") class MiningNodeBlocksTest {
    @Test void nativeBlocksAndLegacyOwnershipResolveWithoutDuplicateAssets(){
        assertEquals("Ore_Copper_Stone",MiningNodeBlocks.blockFor("Ore_Copper"));
        assertEquals("Ore_Thorium_Sandstone",MiningNodeBlocks.blockFor("Ore_Thorium"));
        assertEquals("Ore_Cobalt_Slate",MiningNodeBlocks.blockFor("Ore_Cobalt"));
        assertEquals("Rock_Marble_Cobble",MiningNodeBlocks.blockFor("Rock_Marble_Cobble"));
        assertEquals("Ore_Copper_Stone",MiningNodeBlocks.nativeOwned("Aetherhaven_Mining_Node_Ore_Copper"));
        assertEquals("Rock_Stone",MiningNodeBlocks.nativeOwned("Aetherhaven_Mining_Node_Depleted"));
        assertEquals("Ore_Iron_Stone",MiningNodeBlocks.nativeOwned("Ore_Iron_Stone"));
        assertTrue(MiningNodeBlocks.ownedMatches("Aetherhaven_Mining_Node_Ore_Copper","Ore_Copper_Stone"));
        assertFalse(MiningNodeBlocks.ownedMatches("Aetherhaven_Mining_Node_Ore_Copper","Rock_Stone"));
        assertFalse(java.nio.file.Files.exists(java.nio.file.Path.of("src/main/resources/Server/Item/Items/Aetherhaven/Mining_Nodes/Aetherhaven_Mining_Node_Ore_Copper.json")));
    }
    @Test void loadConversionPreservesOtherBlocksAndRotationAndIsIdempotent(){
        var section=new com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection();
        section.set(1,42,3,0);section.set(2,42,0,0);section.set(3,99,2,0);
        assertEquals(2,MiningNodeLegacyBlocks.convert(section,id->id==42?43:id));
        assertEquals(43,section.get(1));assertEquals(3,section.getRotationIndex(1));
        assertEquals(99,section.get(3));assertEquals(2,section.getRotationIndex(3));
        assertEquals(0,MiningNodeLegacyBlocks.convert(section,id->id==42?43:id));
    }
}
