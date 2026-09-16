package com.hexvane.aetherhaven.community;

import static org.junit.jupiter.api.Assertions.*;
import com.hexvane.aetherhaven.plot.GaiaStatueAppearance;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@Tag("construction")
class GaiaStatueMarketplaceTest {
    @ParameterizedTest
    @EnumSource(GaiaStatueAppearance.class)
    void marketplacePreflightPreservesEachAppearanceId(GaiaStatueAppearance appearance) {
        byte[] prefab = ("{\"version\":8,\"blockIdVersion\":8,\"blocks\":[{\"name\":\""
            + appearance.blockTypeId() + "\",\"x\":-2,\"y\":3,\"z\":7,\"rotation\":1}],\"fluids\":[]}")
            .getBytes(StandardCharsets.UTF_8);
        var result = CommunityPrefabSafety.validate(prefab, key -> key.key(), id ->
            getClass().getClassLoader().getResource("Server/Item/Items/Aetherhaven/" + id + ".json") != null, id -> false);
        assertTrue(result.isSafe(), result.detail());
        assertEquals(List.of(appearance.blockTypeId()), result.referencedBlocks());
    }
}
