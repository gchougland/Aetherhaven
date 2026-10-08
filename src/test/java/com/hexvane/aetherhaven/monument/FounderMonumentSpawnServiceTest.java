package com.hexvane.aetherhaven.monument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.protocol.PlayerSkin;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("entity")
class FounderMonumentSpawnServiceTest {

    @Test
    void legacyModelInheritsNormalPlayerAppearanceWithoutOverrides() throws Exception {
        try (var stream = getClass().getResourceAsStream("/Server/Models/Aetherhaven/Founder_Monument_Statue.json")) {
            assertNotNull(stream);
            JsonObject model = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("Player", model.get("Parent").getAsString());
            for (String override : new String[] { "Model", "Texture", "GradientSet", "GradientId", "DefaultAttachments" }) {
                assertFalse(model.has(override), "Legacy saves must inherit native Player appearance: " + override);
            }
        }
    }

    @Test
    void nativeSkinRetainsEveryCosmeticThroughSaveAndRestore() throws Exception {
        PlayerSkin original = new PlayerSkin();
        // Include every protocol appearance slot, including future String slots, without resolving or replacing any.
        for (var field : PlayerSkin.class.getFields()) {
            if (field.getType() == String.class && !java.lang.reflect.Modifier.isStatic(field.getModifiers())) {
                field.set(original, field.getName() + ".original.variant");
            }
        }
        var stored = FounderMonumentStatueSkin.fromProtocol(original);
        PlayerSkin restored = stored.tryToProtocol();
        assertNotNull(restored);
        var nativeSkin = FounderMonumentSpawnService.createStatueSkin(restored);
        assertEquals(new Gson().toJson(original), new Gson().toJson(nativeSkin.getPlayerSkin()));
        assertNotSame(restored, nativeSkin.getPlayerSkin());
        restored.haircut = "Changed after placement";
        assertEquals(original.haircut, nativeSkin.getPlayerSkin().haircut);
        assertTrue(nativeSkin.consumeNetworkOutdated(), "Native tracker must send the skin to viewers");
        assertFalse(nativeSkin.consumeNetworkOutdated());
        assertTrue(FounderMonumentStatueRestoreSystem.hasAppliedStatueCosmetics(nativeSkin));
    }

    @Test
    void stoneAssetsAreNotPackaged() {
        for (String asset : new String[] {
            "Founder_Monument_Statue_Stone.png", "Founder_Monument_Stone_Base_256x128.png",
            "Founder_Monument_Player_Body.blockymodel"
        }) {
            assertNull(getClass().getResource("/Common/Characters/Aetherhaven/" + asset));
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
        assertTrue(!FounderMonumentStatueRestoreSystem.hasAppliedStatueCosmetics(null));
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

}
