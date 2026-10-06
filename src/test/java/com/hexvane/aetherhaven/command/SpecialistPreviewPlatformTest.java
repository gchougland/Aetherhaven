package com.hexvane.aetherhaven.command;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class SpecialistPreviewPlatformTest {
    @Test void nativeUnchangedPlatformCellCanSupportTheNextPreviewPaste() {
        var section=new BlockSection();int cell=100,stone=42;
        assertTrue(section.set(cell,stone,RotationTuple.NONE_INDEX,0));
        assertFalse(section.set(cell,stone,RotationTuple.NONE_INDEX,0),"Native unchanged writes report no modification");
        assertTrue(AetherhavenSpecialistPreviewCommand.platformCellMatches(section.get(cell)==stone?"Rock_Stone":null,
            section.getRotationIndex(cell),section.getFiller(cell)));
    }
    @Test void missingWrongRotatedOrFillerCellsDoNotHideAWriteFailure() {
        assertFalse(AetherhavenSpecialistPreviewCommand.platformCellMatches(null,RotationTuple.NONE_INDEX,0));
        assertFalse(AetherhavenSpecialistPreviewCommand.platformCellMatches("Soil_Dirt",RotationTuple.NONE_INDEX,0));
        assertFalse(AetherhavenSpecialistPreviewCommand.platformCellMatches("Rock_Stone",RotationTuple.NONE_INDEX+1,0));
        assertFalse(AetherhavenSpecialistPreviewCommand.platformCellMatches("Rock_Stone",RotationTuple.NONE_INDEX,1));
    }
}
