package com.hexvane.aetherhaven.festival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
final class FestivalGreetingLangIndexTest {
    @Test
    void parsesGreetingKeysFromLangContent() throws IOException {
        String content =
            """
            # Wintertide crossmod
            aetherhaven.dialogue.festival.wintertide.greeting.mechanic.0=Line zero
            aetherhaven.dialogue.festival.wintertide.greeting.mechanic.1=Line one
            aetherhaven.dialogue.festival.wintertide.greeting.default.0=Default line
            unrelated.key=skip
            """;
        FestivalGreetingLangIndex.Builder builder = FestivalGreetingLangIndex.builder();
        FestivalGreetingLangIndex.parseLangContent(
            builder,
            "aetherhaven_dialogue_festival_wintertide",
            content
        );
        FestivalGreetingLangIndex index = builder.build();

        assertEquals(
            List.of(
                "aetherhaven_dialogue_festival_wintertide.aetherhaven.dialogue.festival.wintertide.greeting.mechanic.0",
                "aetherhaven_dialogue_festival_wintertide.aetherhaven.dialogue.festival.wintertide.greeting.mechanic.1"
            ),
            index.keysForKindOnly("wintertide", "mechanic")
        );
        assertEquals(
            List.of(
                "aetherhaven_dialogue_festival_wintertide.aetherhaven.dialogue.festival.wintertide.greeting.default.0"
            ),
            index.keysForDefault("wintertide")
        );
        assertTrue(index.keysForKindOnly("wintertide", "miner").isEmpty());
    }

    @Test
    void sortsIndicesNumerically() throws IOException {
        String content =
            """
            aetherhaven.dialogue.festival.new_life.greeting.farmer.2=Second
            aetherhaven.dialogue.festival.new_life.greeting.farmer.0=First
            aetherhaven.dialogue.festival.new_life.greeting.farmer.10=Tenth
            """;
        FestivalGreetingLangIndex.Builder builder = FestivalGreetingLangIndex.builder();
        FestivalGreetingLangIndex.parseLangContent(builder, "test_bundle", content);
        FestivalGreetingLangIndex index = builder.build();

        assertEquals(
            List.of(
                "test_bundle.aetherhaven.dialogue.festival.new_life.greeting.farmer.0",
                "test_bundle.aetherhaven.dialogue.festival.new_life.greeting.farmer.2",
                "test_bundle.aetherhaven.dialogue.festival.new_life.greeting.farmer.10"
            ),
            index.keysForKindOnly("new_life", "farmer")
        );
    }

    @Test
    void loadsFromClasspathLangFiles() {
        FestivalGreetingLangIndex index = FestivalGreetingLangIndex.load(FestivalGreetingLangIndexTest.class.getClassLoader());
        assertFalse(index.keysForKindOnly("wintertide", "elder").isEmpty());
    }
}
