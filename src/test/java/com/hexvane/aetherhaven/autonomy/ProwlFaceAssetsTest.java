package com.hexvane.aetherhaven.autonomy;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.npc.NpcFaceVisuals;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Tag("autonomy")
class ProwlFaceAssetsTest {
    private static final Path RES = Path.of("src/main/resources");
    private static JsonObject json(Path p) throws Exception { return JsonParser.parseString(Files.readString(p)).getAsJsonObject(); }

    @Test void customRigIsSupportedAndSelectsItsOwnFaceTables() {
        String model = "NPC/Prowl/prowl_hytale.blockymodel";
        assertTrue(NpcFaceVisuals.supportsFaceModel(model));
        assertTrue(NpcFaceVisuals.supportsFaceModel("Characters/Player.blockymodel"));
        assertFalse(NpcFaceVisuals.supportsFaceModel("NPC/Dragon.blockymodel"));
        for (String suffix : new String[]{"", "_Lower", "_Higher"}) {
            String original = "Aetherhaven_Life_Actions" + suffix;
            assertEquals(original + "_Prowl", NpcFaceVisuals.itemAnimationsForModel(model, original));
            assertEquals(original, NpcFaceVisuals.itemAnimationsForModel("Characters/Player.blockymodel", original));
        }
        assertEquals("Item", NpcFaceVisuals.itemAnimationsForModel(model, "Item"));
    }

    private static JsonObject bone(com.google.gson.JsonArray nodes, String name) {
        for (var entry : nodes) {
            var node = entry.getAsJsonObject();
            if (node.get("name").getAsString().equals(name)) return node;
            if (node.has("children")) {
                var found = bone(node.getAsJsonArray("children"), name);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Test void mouthUsesPlayerAtlasAtProwlsOriginalPlacement() throws Exception {
        var body = json(RES.resolve("Common/NPC/Prowl/prowl_hytale.blockymodel"));
        assertNull(bone(body.getAsJsonArray("nodes"), "Mouth"), "No old embedded mouth may overlay the shared atlas");
        var anchor = bone(body.getAsJsonArray("nodes"), "Mouth-Attachment");
        var mesh = json(RES.resolve("Common/NPC/Prowl/Player_Mouth.blockymodel"));
        var attached = bone(mesh.getAsJsonArray("nodes"), "Mouth-Attachment");
        assertEquals(anchor.get("position"), attached.get("position"));
        assertEquals(anchor.get("orientation"), attached.get("orientation"));
        var mouth = bone(mesh.getAsJsonArray("nodes"), "Mouth");
        var shape = mouth.getAsJsonObject("shape");
        assertEquals(20, shape.getAsJsonObject("settings").getAsJsonObject("size").get("x").getAsInt());
        assertEquals(10, shape.getAsJsonObject("settings").getAsJsonObject("size").get("y").getAsInt());
        assertEquals(.9, shape.getAsJsonObject("stretch").get("x").getAsDouble());
        assertEquals(.8, shape.getAsJsonObject("stretch").get("y").getAsDouble());
        var offset = shape.getAsJsonObject("textureLayout").getAsJsonObject("front").getAsJsonObject("offset");
        assertEquals(0, offset.get("x").getAsInt());
        assertEquals(0, offset.get("y").getAsInt());
        var model = json(RES.resolve("Server/Models/Townsfolk/Prowl.json"));
        var attachment = model.getAsJsonArray("DefaultAttachments").get(0).getAsJsonObject();
        assertEquals("NPC/Prowl/Player_Mouth.blockymodel", attachment.get("Model").getAsString());
        assertEquals("Characters/Body_Attachments/Mouths/Mouth1_Textures/Default_Greyscale.png", attachment.get("Texture").getAsString());
    }

    @Test void allExpressionsAndActionsAreInheritedFromTheHumanRig() throws Exception {
        var model = json(RES.resolve("Server/Models/Townsfolk/Prowl.json"));
        assertEquals("Aetherhaven_Human", model.get("Parent").getAsString());
        var prowl = model.getAsJsonObject("AnimationSets");
        for (String name : new String[]{"Talk","Grin","Frown","Wave","PonderDismissive","Yawn","Laugh","DanceBoogie","DancePop","Aetherhaven_Life_Mouth_D","Aetherhaven_Life_Face_Read"})
            assertFalse(prowl.has(name), "Prowl must inherit shared player facial animations");
        var human = LifeAssetJson.read(RES.resolve("Server/Item/Animations/Aetherhaven_Life_Actions.json"));
        for (String suffix : new String[]{"", "_Lower", "_Higher"})
            assertEquals(human, LifeAssetJson.read(RES.resolve("Server/Item/Animations/Aetherhaven_Life_Actions"+suffix+"_Prowl.json")));
        Path old = RES.resolve("Common/Characters/Animations/Aetherhaven/ProwlFaces");
        if (Files.exists(old)) try (var paths = Files.walk(old)) {
            assertEquals(0, paths.filter(p -> p.toString().endsWith(".blockyanim")).count());
        }
    }
}
