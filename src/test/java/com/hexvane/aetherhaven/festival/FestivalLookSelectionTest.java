package com.hexvane.aetherhaven.festival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.town.TownRecord;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
final class FestivalLookSelectionTest {
    private static final Gson GSON = new Gson();

    @Test
    void layoutUsesTheTownLookThenFallsBackToTheOriginal() {
        FestivalDefinition carnival =
            parse("{\"id\":\"carnival\",\"displayName\":\"Carnival Festival\",\"prefabPath\":\"Festivals/Festival_Carnival.prefab.json\"}");
        FestivalDefinition neon =
            parse(
                "{\"id\":\"carnival_neon\",\"displayName\":\"Neon Carnival\",\"prefabPath\":\"Festivals/Festival_Carnival_Neon.prefab.json\",\"festivalVariant\":true,\"countsAsFestivalId\":\"carnival\"}"
            );
        FestivalCatalog catalog = FestivalCatalog.forTests(List.of(carnival, neon));
        TownRecord town = new TownRecord();

        assertSame(carnival, FestivalLookSelection.layoutFor(catalog, town, carnival));

        town.setSelectedFestivalLookId("carnival", "carnival_neon");
        assertSame(neon, FestivalLookSelection.layoutFor(catalog, town, carnival));

        town.setSelectedFestivalLookId("carnival", null);
        assertSame(carnival, FestivalLookSelection.layoutFor(catalog, town, carnival));
        assertEquals(List.of(neon), FestivalLookSelection.looksOf(catalog, "carnival"));
    }

    private static FestivalDefinition parse(String json) {
        return GSON.fromJson(json, FestivalDefinition.class);
    }
}
