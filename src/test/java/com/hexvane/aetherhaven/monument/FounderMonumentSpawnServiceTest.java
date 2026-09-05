package com.hexvane.aetherhaven.monument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.AetherhavenConstants;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("entity")
class FounderMonumentSpawnServiceTest {

    @Test
    void dedicatedModelAssetIsAnAttachmentFreeStoneFallback() throws Exception {
        try (
            var stream = getClass()
                .getResourceAsStream("/Server/Models/Aetherhaven/Founder_Monument_Statue.json")
        ) {
            assertTrue(stream != null, "founder statue model asset must be packaged");
            JsonObject model = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();

            assertEquals("Player", model.get("Parent").getAsString());
            assertEquals(AetherhavenConstants.FOUNDER_MONUMENT_STATUE_BODY_MODEL, model.get("Model").getAsString());
            assertEquals(AetherhavenConstants.FOUNDER_MONUMENT_STATUE_BASE_TEXTURE, model.get("Texture").getAsString());
            assertNull(model.get("GradientSet"));
            assertNull(model.get("GradientId"));
            assertTrue(model.getAsJsonArray("DefaultAttachments").isEmpty());
        }
    }

    @Test
    void generatedStoneTexturesPreserveCanvasAndStayOpaque() throws Exception {
        BufferedImage stoneSource = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        stoneSource.setRGB(0, 0, 0xFF112233);
        stoneSource.setRGB(1, 0, 0xFF445566);
        stoneSource.setRGB(0, 1, 0xFF778899);
        stoneSource.setRGB(1, 1, 0xFFAABBCC);
        for (int[] size : new int[][] { { 32, 32 }, { 64, 96 }, { 256, 128 } }) {
            BufferedImage stone = ImageIO.read(
                new ByteArrayInputStream(
                    FounderMonumentStoneTextures.generateStonePng(size[0], size[1], stoneSource)
                )
            );
            assertEquals(size[0], stone.getWidth());
            assertEquals(size[1], stone.getHeight());
            assertEquals(0xFF112233, stone.getRGB(0, 0));
            assertEquals(0xFF445566, stone.getRGB(1, 0));
            assertEquals(0xFF778899, stone.getRGB(0, 1));
        }
    }

    @Test
    void dedicatedBodyIntegratesTheOtherwiseDetachedFaceQuad() throws Exception {
        try (
            var stream = getClass()
                .getResourceAsStream("/Common/Characters/Aetherhaven/Founder_Monument_Player_Body.blockymodel")
        ) {
            assertNotNull(stream);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                .getAsJsonObject();
            JsonObject face = findNode(root.getAsJsonArray("nodes"), "Founder-Face");
            assertNotNull(face, "statue body must fill the player head's intentionally missing front face");
            assertEquals("quad", face.getAsJsonObject("shape").get("type").getAsString());
            assertEquals("+Z", face.getAsJsonObject("shape").getAsJsonObject("settings").get("normal").getAsString());
        }
    }

    @Test
    void malformedPersistedSkinIsRejected() {
        FounderMonumentStatueSkin stored = new Gson()
            .fromJson("{\"skinJson\":\"not valid player skin json\"}", FounderMonumentStatueSkin.class);

        assertNull(stored.tryToProtocol());
    }

    @Test
    void invalidPersistenceScalesUseSafeDefault() {
        assertEquals(1.0f, FounderMonumentSpawnService.safePersistScale(0));
        assertEquals(1.0f, FounderMonumentSpawnService.safePersistScale(Float.NaN));
        assertEquals(1.0f, FounderMonumentSpawnService.safePersistScale(Float.POSITIVE_INFINITY));
        assertEquals(1.25f, FounderMonumentSpawnService.safePersistScale(1.25f));
    }

    private static JsonObject findNode(com.google.gson.JsonArray nodes, String name) {
        for (var element : nodes) {
            JsonObject node = element.getAsJsonObject();
            if (name.equals(node.get("name").getAsString())) {
                return node;
            }
            JsonObject child = findNode(node.getAsJsonArray("children"), name);
            if (child != null) {
                return child;
            }
        }
        return null;
    }
}
