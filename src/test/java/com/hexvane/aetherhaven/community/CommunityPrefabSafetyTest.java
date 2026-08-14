package com.hexvane.aetherhaven.community;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import com.hexvane.aetherhaven.prefab.PrefabJsonStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
class CommunityPrefabSafetyTest {
    @Test
    void acceptsVanillaOnlyPrefab() {
        CommunityPrefabSafety.Result result = validate(prefab("Stone", "Water"), Set.of("Stone"), Set.of("Water"));

        assertTrue(result.isSafe());
        assertTrue(result.unresolvedAssets().isEmpty());
    }

    @Test
    void acceptsInstalledModdedBlock() {
        CommunityPrefabSafety.Result result =
            validate(prefab("Dejans_Ancient_Barrel_Seatable", null), Set.of("Dejans_Ancient_Barrel_Seatable"), Set.of());

        assertTrue(result.isSafe());
    }

    @Test
    void refusesMissingOrSyntheticUnknownBlock() {
        CommunityPrefabSafety.Result result =
            validate(prefab("Dejans_Ancient_Barrel_Seatable", null), Set.of(), Set.of());

        assertEquals(CommunityPrefabSafety.Status.UNRESOLVED_ASSETS, result.status());
        assertEquals(Set.of("Dejans_Ancient_Barrel_Seatable"), Set.copyOf(result.unresolvedAssets()));
    }

    @Test
    void refusesMalformedPrefab() {
        CommunityPrefabSafety.Result result = validate("{\"version\":8,\"blocks\":[{\"x\":0}]}".getBytes(StandardCharsets.UTF_8), Set.of(), Set.of());

        assertEquals(CommunityPrefabSafety.Status.MALFORMED, result.status());
        assertEquals("Prefab blocks[0] has no string name", result.detail());
        assertFalse(result.isSafe());
    }

    @Test
    void skipsCoordinatesAndStillValidates() {
        byte[] json =
            """
            {
              "version": 8,
              "blockIdVersion": 8,
              "blocks": [
                { "x": 1, "y": 2, "z": 3, "name": "Stone" },
                { "x": 4, "y": 5, "z": 6, "name": "Empty" }
              ],
              "fluids": [
                { "x": 0, "y": 0, "z": 0, "name": "Water", "level": 0 }
              ]
            }
            """
                .getBytes(StandardCharsets.UTF_8);
        CommunityPrefabSafety.Result result = validate(json, Set.of("Stone", "Empty"), Set.of("Water"));

        assertTrue(result.isSafe());
        assertEquals(List.of("Stone", "Empty"), result.referencedBlocks());
        assertEquals(List.of("Water"), result.referencedFluids());
    }

    @Test
    void validatePathStreamsDiskPrefab() throws Exception {
        Path file = Files.createTempFile("aetherhaven-prefab-safety", ".json");
        try {
            Files.writeString(
                file,
                """
                {"version":8,"blockIdVersion":8,"blocks":[{"name":"Stone","x":0,"y":0,"z":0}],"fluids":[]}
                """
            );
            PrefabJsonStream.Scan scan = PrefabJsonStream.scan(file);
            CommunityPrefabSafety.Result result =
                CommunityPrefabSafety.validate(
                    scan,
                    versioned -> versioned.key(),
                    Set.of("Stone")::contains,
                    ignored -> false
                );
            assertTrue(result.isSafe());
            assertEquals(List.of("Stone"), result.referencedBlocks());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void normalizesChancePrefixBeforeLookup() {
        CommunityPrefabSafety.Result result = validate(prefab("25%Stone", null), Set.of("Stone"), Set.of());

        assertTrue(result.isSafe());
        assertEquals("Stone", result.referencedBlocks().getFirst());
    }

    @Test
    void appliesBlockMigrationBeforeLookup() {
        byte[] json = prefab("Old_Stone", null);
        CommunityPrefabSafety.Result result =
            CommunityPrefabSafety.validate(
                json,
                versioned -> versioned.key().equals("Old_Stone") ? "Stone" : versioned.key(),
                Set.of("Stone")::contains,
                ignored -> false
            );

        assertTrue(result.isSafe());
        assertEquals("Stone", result.referencedBlocks().getFirst());
    }

    @Test
    void refusesUnsupportedPrefabVersion() {
        byte[] json = "{\"version\":9,\"blocks\":[]}".getBytes(StandardCharsets.UTF_8);
        CommunityPrefabSafety.Result result = validate(json, Set.of(), Set.of());

        assertEquals(CommunityPrefabSafety.Status.UNSUPPORTED_VERSION, result.status());
    }

    @Test
    void externalOverrideOfVanillaAssetIsNotARequiredMod() {
        assertFalse(CommunityRequiredMods.shouldRequirePack("MrLoop:ConnectedWindows", true));
        assertTrue(CommunityRequiredMods.shouldRequirePack("DejansMods:Dejans_Deko", false));
    }

    private static CommunityPrefabSafety.Result validate(byte[] json, Set<String> blocks, Set<String> fluids) {
        return CommunityPrefabSafety.validate(json, versioned -> versioned.key(), blocks::contains, fluids::contains);
    }

    private static byte[] prefab(String block, String fluid) {
        String blocks = block == null ? "[]" : "[{\"name\":\"" + block + "\"}]";
        String fluids = fluid == null ? "[]" : "[{\"name\":\"" + fluid + "\"}]";
        return ("{\"version\":8,\"blockIdVersion\":8,\"blocks\":" + blocks + ",\"fluids\":" + fluids + "}")
            .getBytes(StandardCharsets.UTF_8);
    }
}
