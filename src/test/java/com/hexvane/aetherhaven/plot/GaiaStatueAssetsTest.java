package com.hexvane.aetherhaven.plot;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("construction")
class GaiaStatueAssetsTest {
    @Test
    void everyVariantInheritsGaiaBehaviorAndFootprint() throws Exception {
        JsonObject base = asset(GaiaStatueAppearance.LIGHT.blockTypeId());
        var block = base.getAsJsonObject("BlockType");
        assertEquals("Statue", block.get("HitboxType").getAsString());
        assertTrue(block.has("BlockEntity"));
        assertTrue(block.has("Interactions"));
        for (var look : GaiaStatueAppearance.values()) {
            JsonObject variant = asset(look.blockTypeId());
            assertEquals(look.iconPath(), variant.get("Icon").getAsString());
            if (look == GaiaStatueAppearance.LIGHT) continue;
            assertEquals(GaiaStatueAppearance.LIGHT.blockTypeId(), variant.get("Parent").getAsString());
            assertEquals(Set.of("CustomModel", "CustomModelTexture"), variant.getAsJsonObject("BlockType").keySet());
        }
    }

    @Test
    void appearancesCoverInstalledVanillaStatuesAndReferenceTheirActualVisuals() throws Exception {
        String appData = System.getenv("APPDATA");
        assumeTrue(appData != null, "Installed Hytale assets needed for integration check");
        Path assets = Path.of(appData, "Hytale/install/release/package/game/latest/Assets.zip");
        assumeTrue(Files.isRegularFile(assets), "Installed Hytale assets needed for integration check");
        try (ZipFile zip = new ZipFile(assets.toFile())) {
            var statues = zip.stream().filter(e -> e.getName().matches("Server/Item/Items/.*Statue.*\\.json"))
                .collect(Collectors.toMap(e -> Path.of(e.getName()).getFileName().toString().replace(".json", ""), e -> e));
            assertEquals(statues.keySet(), Arrays.stream(GaiaStatueAppearance.values())
                .map(GaiaStatueAppearance::vanillaItemId).collect(Collectors.toSet()));
            for (var look : GaiaStatueAppearance.values()) {
                JsonObject vanilla;
                try (var reader = new InputStreamReader(zip.getInputStream(statues.get(look.vanillaItemId())), StandardCharsets.UTF_8)) {
                    vanilla = JsonParser.parseReader(reader).getAsJsonObject();
                }
                JsonObject visual = asset(look.blockTypeId()).getAsJsonObject("BlockType");
                for (String field : Set.of("CustomModel", "CustomModelTexture")) {
                    assertEquals(vanilla.getAsJsonObject("BlockType").get(field), visual.get(field), look + " " + field);
                }
                assertNotNull(zip.getEntry("Common/" + visual.get("CustomModel").getAsString()));
                for (var texture : visual.getAsJsonArray("CustomModelTexture")) {
                    assertNotNull(zip.getEntry("Common/" + texture.getAsJsonObject().get("Texture").getAsString()));
                }
                assertNotNull(zip.getEntry("Common/" + look.iconPath()));
            }
        }
    }

    private JsonObject asset(String id) throws Exception {
        try (var in = getClass().getClassLoader().getResourceAsStream("Server/Item/Items/Aetherhaven/" + id + ".json")) {
            assertNotNull(in, id);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
