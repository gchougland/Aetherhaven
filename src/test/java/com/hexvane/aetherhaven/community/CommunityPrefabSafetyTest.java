package com.hexvane.aetherhaven.community;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Set;
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
        assertFalse(result.isSafe());
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
