package com.hexvane.aetherhaven.blockpalette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
public final class BlockPaletteRemapGroupTest {
    @Test
    void longestPrefixWinsAndKeepsSuffix() {
        BlockPaletteRemapGroup group =
            BlockPaletteRemapGroup.builder("mymod_walls", "walls")
                .variant("walls_oak", "Oak", "MyMod_Oak_Wall", "MyMod_Oak_Wall")
                .variant("walls_birch", "Birch", "MyMod_Birch_Wall", "MyMod_Birch_Wall")
                .build();

        BlockPaletteRemapGroup.Match match = group.matchBlockTypeId("MyMod_Oak_Wall_Stairs");
        assertNotNull(match);
        assertEquals("walls_oak", match.variant().paletteId());
        assertEquals("_Stairs", match.suffix());

        String remapped = match.variant().blockPrefix().equals("MyMod_Oak_Wall")
            ? "MyMod_Birch_Wall" + match.suffix()
            : null;
        assertEquals("MyMod_Birch_Wall_Stairs", remapped);
    }

    @Test
    void unknownBlockReturnsNull() {
        BlockPaletteRemapGroup group =
            BlockPaletteRemapGroup.builder("mymod_walls", "walls")
                .variant("walls_oak", "Oak", "MyMod_Oak_Wall", "MyMod_Oak_Wall")
                .build();
        assertNull(group.matchBlockTypeId("Wood_Village_Wall_Full"));
    }
}
