package com.hexvane.aetherhaven.autonomy;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@org.junit.jupiter.api.Tag("autonomy")
class VillagerCustomVoiceTest {
    @TempDir Path temp;

    @Test void addOnRecordingSurvivesResolutionSelectionAndReload() throws Exception {
        Path manifest = temp.resolve("custom.json");
        Files.writeString(manifest, """
            {"Example_Automaton_Talk":[
              {"clip":"Example_Automaton_Talk_1","audioMs":1200},
              {"clip":"Example_Automaton_Talk_2","audioMs":1400,"faces":{},"actionsId":"Example_Actions"}
            ]}
            """);
        try {
            VillagerLifeSpeech.reloadFromFiles(List.of(manifest));
            assertEquals("Example_Automaton", VillagerVoiceProfile.resolve("example_automaton", "male", "machine").id());
            var first = VillagerLifeSpeech.select("Example_Automaton", "Talk", 0);
            assertEquals("Example_Automaton_Talk_1", first.clip());
            var second = VillagerLifeSpeech.selectExcept("Example_Automaton", "Talk", 0, first.clip());
            assertEquals("Example_Automaton_Talk_2", second.clip());
            assertEquals("Example_Actions", second.actionsId());
            assertNotNull(VillagerLifeSpeech.select("WarmMale", "Talk", 0));
            assertNull(VillagerLifeSpeech.select("Example_Automaton", "Yawn", 0));
            var lower = VillagerLifeSpeech.select("Example_AutomatonLower", "Talk", 1);
            assertEquals("Example_Actions_Lower", lower.actionsId());
            assertEquals(Math.ceil(1400 / lower.pitch()), lower.audioMs());
        } finally {
            VillagerLifeSpeech.reloadFromFiles(List.of());
        }
        assertEquals("WarmMale", VillagerVoiceProfile.resolve("Example_Automaton", "male", "machine").id());
    }

    @Test void malformedTimingsAndEmptyCategoriesAreRejected() {
        for (String json : List.of("{\"Example_Talk\":[]}",
                "{\"Example_Talk\":[{\"clip\":\"Example_Talk_1\",\"audioMs\":0}]}",
                "{\"Example_Unknown\":[{\"clip\":\"Example_1\",\"audioMs\":100}]}")) {
            assertThrows(IllegalArgumentException.class, () -> VillagerLifeSpeech.parse(
                com.google.gson.JsonParser.parseString(json).getAsJsonObject()));
        }
    }
}
