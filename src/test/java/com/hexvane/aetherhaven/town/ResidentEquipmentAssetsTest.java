package com.hexvane.aetherhaven.town;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class ResidentEquipmentAssetsTest {
    private static final Path ROOT=Path.of("src/main/resources");
    @Test void humanEarringsHaveRealSocketsInsteadOfMissingAttachmentReferences() throws Exception {
        var ears=JsonParser.parseString(Files.readString(ROOT.resolve("Common/Characters/Aetherhaven/Resident_Ears.blockymodel"))).getAsJsonObject();
        for(var root:ears.getAsJsonArray("nodes")) {
            var parent=root.getAsJsonObject();
            String side=parent.get("name").getAsString().substring(0,1);
            for(String suffix:List.of("EarAccessory","EarAccessoryTilt")) {
                var socket=parent.getAsJsonArray("children").asList().stream().map(JsonElement::getAsJsonObject)
                    .filter(n->n.get("name").getAsString().equals(side+"-Ear-"+suffix)).findFirst().orElseThrow();
                assertEquals("none",socket.getAsJsonObject("shape").get("type").getAsString());
                assertFalse(socket.getAsJsonObject("shape").getAsJsonObject("settings").get("isPiece").getAsBoolean());
            }
        }
    }
    @Test void rewardEquipmentUsesNativeIconCellSizeAndCannotBePlayerCrafted() throws Exception {
        for(String stem:List.of("Courier_Kit","Smith_Kit","CourierPack_Kit","Field_Ledger","Botanist_Kit","Alchemist_Kit")) {
            var item=JsonParser.parseString(Files.readString(ROOT.resolve("Server/Item/Items/Aetherhaven/Aetherhaven_"+stem+".json"))).getAsJsonObject();
            assertFalse(item.has("Recipe"),stem);
            assertEquals(1,item.get("MaxStack").getAsInt(),stem);
            var image=ImageIO.read(ROOT.resolve("Common").resolve(item.get("Icon").getAsString()).toFile());
            assertEquals(64,image.getWidth(),stem);assertEquals(64,image.getHeight(),stem);
            assertTrue(Files.isRegularFile(ROOT.resolve("Common").resolve(item.get("Model").getAsString())));
        }
    }
}
