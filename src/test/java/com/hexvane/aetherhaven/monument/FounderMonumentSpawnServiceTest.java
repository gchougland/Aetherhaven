package com.hexvane.aetherhaven.monument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAttachment;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
            assertEquals("Characters/Player.blockymodel", model.get("Model").getAsString());
            assertEquals(AetherhavenConstants.FOUNDER_MONUMENT_STATUE_BASE_TEXTURE, model.get("Texture").getAsString());
            assertNull(model.get("GradientSet"));
            assertNull(model.get("GradientId"));
            assertTrue(model.getAsJsonArray("DefaultAttachments").isEmpty());
        }
    }

    @Test
    void stonePrepareUsesOnlyPackagedTextures() {
        ModelAttachment[] source = {
            new ModelAttachment(
                "Characters/Clothes/Example.blockymodel",
                "Characters/Clothes/Example.png",
                null,
                null,
                1.0
            )
        };
        FounderMonumentStoneTextures.Prepared prepared =
            FounderMonumentStoneTextures.prepare("Characters/Some/Skin.png", source);
        assertEquals(AetherhavenConstants.FOUNDER_MONUMENT_STATUE_TEXTURE, prepared.baseTexture());
        assertEquals(1, prepared.attachments().length);
        assertEquals("Characters/Clothes/Example.blockymodel", prepared.attachments()[0].getModel());
        assertEquals(AetherhavenConstants.FOUNDER_MONUMENT_STATUE_TEXTURE, prepared.attachments()[0].getTexture());
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

    @Test
    void pedestalBlockMatchesSpawnOffset() {
        var pedestal = FounderMonumentSpawnService.pedestalBlockFromStatuePosition(12.5, 41.05, -3.5);
        assertEquals(12, pedestal.x);
        assertEquals(40, pedestal.y);
        assertEquals(-4, pedestal.z);
    }

    @Test
    void playerBodyIsNotTreatedAsAFinishedStatue() {
        assertTrue(!FounderMonumentStatueRestoreSystem.isStoneStatueMesh(null));
        assertTrue(!FounderMonumentStatueRestoreSystem.isClientVisibleStatue(null, null));
    }

    @Test
    void pedestalRecoversWhenStatueMeshIsMissing() {
        assertTrue(FounderMonumentStatueRestoreSystem.needsBlockRecovery("town", "{\"id\":\"skin\"}", "Ada", false));
        assertTrue(FounderMonumentStatueRestoreSystem.needsBlockRecovery("town", "", "", false));
        assertTrue(FounderMonumentStatueRestoreSystem.needsBlockRecovery("", "{\"id\":\"skin\"}", "", false));
        assertTrue(FounderMonumentStatueRestoreSystem.needsBlockRecovery("", "", "", false));
        assertTrue(!FounderMonumentStatueRestoreSystem.needsBlockRecovery("town", "{\"id\":\"skin\"}", "Ada", true));
        assertTrue(FounderMonumentStatueRestoreSystem.needsBlockRecovery("{\"id\":\"skin\"}", "uuid", false));
        assertTrue(FounderMonumentStatueRestoreSystem.needsBlockRecovery("", "uuid", false));
        assertTrue(FounderMonumentStatueRestoreSystem.needsBlockRecovery("", "", false));
    }

    @Test
    void emptyPipeCellsAreNotTreatedAsPlacedPedestals() {
        assertTrue(!FounderMonumentStatueRestoreSystem.isPlacedPedestal("", "", ""));
        assertTrue(FounderMonumentStatueRestoreSystem.isPlacedPedestal("town", "", ""));
        assertTrue(FounderMonumentStatueRestoreSystem.isPlacedPedestal("", "{\"id\":\"skin\"}", ""));
        assertTrue(FounderMonumentStatueRestoreSystem.isPlacedPedestal("", "", "Ada"));
    }

    @Test
    void mergeKeepsSavedFacingAndSkin() {
        var zero = new com.hypixel.hytale.math.vector.Rotation3f(0f, 0f, 0f);
        var facing = new com.hypixel.hytale.math.vector.Rotation3f(0f, (float) Math.PI, 0f);
        assertTrue(FounderMonumentStatueRestoreSystem.isIdentityRotation(zero));
        assertTrue(!FounderMonumentStatueRestoreSystem.isIdentityRotation(facing));
        assertEquals((float) Math.PI, FounderMonumentStatueRestoreSystem.mergeRotation(zero, facing).yaw());
        assertEquals((float) Math.PI, FounderMonumentStatueRestoreSystem.mergeRotation(facing, zero).yaw());
        assertEquals("skin", FounderMonumentStatueRestoreSystem.firstNonBlank("", "skin"));
        assertEquals("skin", FounderMonumentStatueRestoreSystem.firstNonBlank("skin", "other"));
    }

    @Test
    void savedStatueModelStaysTheNormalPlayerBody() {
        var saved = FounderMonumentSpawnService.bootSafePersistentModel(1.25f);
        assertEquals("Player", saved.getModelReference().getModelAssetId());
        assertEquals(1.25f, saved.getModelReference().getScale());
        assertTrue(saved.getModelReference().isStaticModel());
        assertTrue(!FounderMonumentSpawnService.isStatuePersistentModel(saved));
        assertTrue(
            FounderMonumentSpawnService.isStatuePersistentModel(
                new com.hypixel.hytale.server.core.modules.entity.component.PersistentModel(
                    new com.hypixel.hytale.server.core.asset.type.model.config.Model.ModelReference(
                        AetherhavenConstants.FOUNDER_MONUMENT_STATUE_MODEL_ID,
                        1.0f,
                        null,
                        true
                    )
                )
            )
        );
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
