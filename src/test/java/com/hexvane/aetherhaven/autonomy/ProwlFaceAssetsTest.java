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

    @Test void everyMappedMouthFrameStaysInsideAnExistingProwlMouthCell() throws Exception {
        var atlas = javax.imageio.ImageIO.read(RES.resolve("Common/NPC/Prowl/prowl_hytale.png").toFile());
        int count = 0;
        try (var files = Files.walk(RES.resolve("Common/Characters/Animations/Aetherhaven/ProwlFaces"))) {
            for (var path : files.filter(p -> p.toString().endsWith(".blockyanim")).toList()) {
                var nodes = json(path).getAsJsonObject("nodeAnimations");
                for (var entry : nodes.entrySet()) for (String channel : new String[]{"position","orientation","shapeStretch","shapeVisible","shapeUvOffset"})
                    assertTrue(entry.getValue().getAsJsonObject().get(channel).isJsonArray(), path + ":" + channel);
                for (var key : nodes.getAsJsonObject("Mouth").getAsJsonArray("shapeUvOffset")) {
                    var uv = key.getAsJsonObject().getAsJsonObject("delta");
                    assertEquals(0, uv.get("x").getAsInt(), path.toString());
                    int y = 24 - uv.get("y").getAsInt();
                    assertTrue(y >= 0 && y <= 120 && y % 8 == 0, path + ":" + y);
                    assertTrue(494 + 18 <= atlas.getWidth() && y + 8 <= atlas.getHeight());
                    boolean visible = false;
                    for (int py=y;py<y+8;py++) for (int px=494;px<512;px++) visible |= (atlas.getRGB(px,py) >>> 24) > 0;
                    assertTrue(visible, "Empty mouth cell: " + path);
                }
                count++;
            }
        }
        assertEquals(714, count);
    }

    @Test void dialogueMoodAndLipTracksAllUseProwlAtlasWhilePreservingPitch() throws Exception {
        var human = json(RES.resolve("Server/Models/Human/Aetherhaven_Human.json")).getAsJsonObject("AnimationSets");
        var prowl = json(RES.resolve("Server/Models/Townsfolk/Prowl.json")).getAsJsonObject("AnimationSets");
        for (String name : new String[]{"Talk","Grin","Frown","Aetherhaven_Life_Lip_Explain_BrightMale_Talk_1_Lower"}) {
            var oldBinding = human.getAsJsonObject(name).getAsJsonArray("Animations").get(0).getAsJsonObject();
            var newBinding = prowl.getAsJsonObject(name).getAsJsonArray("Animations").get(0).getAsJsonObject();
            assertTrue(newBinding.get("Animation").getAsString().startsWith("Characters/Animations/Aetherhaven/ProwlFaces/"));
            assertEquals(oldBinding.get("Speed"), newBinding.get("Speed"));
        }
        var animation = json(RES.resolve("Common/Characters/Animations/Aetherhaven/ProwlFaces/LipSync/Explain_BrightMale_Talk_1.blockyanim"));
        var rows = new java.util.HashSet<Integer>();
        for (var key : animation.getAsJsonObject("nodeAnimations").getAsJsonObject("Mouth").getAsJsonArray("shapeUvOffset"))
            rows.add(key.getAsJsonObject().getAsJsonObject("delta").get("y").getAsInt());
        assertTrue(rows.size() >= 4, "The clip must visibly change mouth shapes");
        assertTrue(rows.contains(0), "Speech returns to Prowl's neutral mouth");
    }
}
