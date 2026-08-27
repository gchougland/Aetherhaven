package com.hexvane.aetherhaven.festival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.Gson;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
final class FestivalGreetingPickerMergeTest {
    private static final Gson GSON = new Gson();
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID NPC = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void discoveredMechanicLinesWinWhenJsonHasNoBucket() throws Exception {
        FestivalDefinition festival = parse("{\"id\":\"wintertide\"}");
        FestivalGreetingLangIndex.Builder builder = FestivalGreetingLangIndex.builder();
        FestivalGreetingLangIndex.parseLangContent(
            builder,
            "aetherhaven_dialogue_festival_wintertide",
            """
            aetherhaven.dialogue.festival.wintertide.greeting.mechanic.0=Mechanic line
            aetherhaven.dialogue.festival.wintertide.greeting.default.0=Default line
            """
        );
        FestivalGreetingLangIndex index = builder.build();

        List<String> keys = FestivalGreetingPicker.mergedGreetingLangKeys(festival, index, "mechanic");
        assertEquals(
            List.of("aetherhaven_dialogue_festival_wintertide.aetherhaven.dialogue.festival.wintertide.greeting.mechanic.0"),
            keys
        );
        assertNotNull(
            FestivalGreetingPicker.pickMessage(festival, index, "mechanic", PLAYER, NPC, 42L)
        );
    }

    @Test
    void jsonAndDiscoveredLinesMergeForSameKind() throws Exception {
        FestivalDefinition festival =
            parse(
                "{\"id\":\"wintertide\",\"greetings\":{\"farmer\":[\"json.farmer.0\"],\"default\":[\"json.default.0\"]}}"
            );
        FestivalGreetingLangIndex.Builder builder = FestivalGreetingLangIndex.builder();
        FestivalGreetingLangIndex.parseLangContent(
            builder,
            "mod_bundle",
            "mod.dialogue.festival.wintertide.greeting.farmer.0=Discovered farmer\n"
        );
        FestivalGreetingLangIndex index = builder.build();

        assertEquals(
            List.of("json.farmer.0", "mod_bundle.mod.dialogue.festival.wintertide.greeting.farmer.0"),
            FestivalGreetingPicker.mergedGreetingLangKeys(festival, index, "farmer")
        );
    }

    @Test
    void unknownKindFallsBackToJsonAndDiscoveredDefault() throws Exception {
        FestivalDefinition festival =
            parse("{\"id\":\"wintertide\",\"greetings\":{\"default\":[\"json.default.0\"]}}");
        FestivalGreetingLangIndex.Builder builder = FestivalGreetingLangIndex.builder();
        FestivalGreetingLangIndex.parseLangContent(
            builder,
            "mod_bundle",
            "mod.dialogue.festival.wintertide.greeting.default.1=Discovered default\n"
        );
        FestivalGreetingLangIndex index = builder.build();

        assertEquals(
            List.of("json.default.0", "mod_bundle.mod.dialogue.festival.wintertide.greeting.default.1"),
            FestivalGreetingPicker.mergedGreetingLangKeys(festival, index, "mechanic")
        );
    }

    @Test
    void emptyJsonAndIndexReturnsNullMessage() {
        FestivalDefinition festival = parse("{\"id\":\"wintertide\"}");
        assertNull(
            FestivalGreetingPicker.pickMessage(
                festival,
                FestivalGreetingLangIndex.empty(),
                "mechanic",
                PLAYER,
                NPC,
                1L
            )
        );
    }

    private static FestivalDefinition parse(String json) {
        FestivalDefinition def = GSON.fromJson(json, FestivalDefinition.class);
        assertNotNull(def);
        return def;
    }
}
