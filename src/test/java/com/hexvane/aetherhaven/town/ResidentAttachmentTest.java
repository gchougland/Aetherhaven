package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.cosmetics.PlayerSkinModelExporter;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class ResidentAttachmentTest {
    @Test void exportedHumanEarsUseRealAccessoryAnchorsWithoutChangingAppearance() {
        var ears=new ModelAttachment("Characters/Body_Attachments/Ears/Ears1.blockymodel","custom/ears.png","Skin","08",.75);
        var elf=new ModelAttachment("Cosmetics/Head/Elf_Ear_Large_Up.blockymodel","custom/elf.png","Skin","12",1);
        var attachments=new ArrayList<>(List.of(ears,elf));
        PlayerSkinModelExporter.sanitizeAttachmentList(attachments);
        var exported=attachments.getFirst();
        assertEquals("Characters/Aetherhaven/Resident_Ears.blockymodel",exported.getModel());
        assertEquals(ears.getTexture(),exported.getTexture());assertEquals(ears.getGradientSet(),exported.getGradientSet());
        assertEquals(ears.getGradientId(),exported.getGradientId());assertEquals(ears.getWeight(),exported.getWeight());
        assertSame(elf,attachments.getLast());
        PlayerSkinModelExporter.sanitizeAttachmentList(attachments);assertSame(exported,attachments.getFirst());
    }
}
