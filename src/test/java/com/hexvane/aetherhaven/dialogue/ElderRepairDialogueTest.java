package com.hexvane.aetherhaven.dialogue;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class ElderRepairDialogueTest {
    @Test void everyRepairRequiresAnExplicitConfirmationAndTownAccess() throws Exception {
        try (var stream = getClass().getResourceAsStream("/Server/Aetherhaven/Dialogue/aetherhaven_elder.json");
             var langStream = getClass().getResourceAsStream("/Server/Languages/en-US/aetherhaven_repairs.lang")) {
            assertNotNull(stream); assertNotNull(langStream);
            var tree = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            String lang = new String(langStream.readAllBytes(), StandardCharsets.UTF_8);
            var nodes = tree.getAsJsonObject("nodes");
            var menu = nodes.getAsJsonObject("repair_menu").getAsJsonArray("choices");
            assertEquals(5, menu.size());
            for (String action : List.of("villagers", "plots", "inn", "support")) {
                var choice = java.util.stream.StreamSupport.stream(menu.spliterator(), false)
                    .map(e -> e.getAsJsonObject()).filter(c -> c.has("next") && c.get("next").getAsString().equals("repair_confirm_" + action)).findFirst().orElseThrow();
                assertFalse(choice.has("actions"), "Choosing a repair must only open its confirmation");
                var confirm = nodes.getAsJsonObject(choice.get("next").getAsString());
                assertTrue(lang.contains("aetherhaven.repairs." + action + ".confirm = Are you sure?"));
                var yes = confirm.getAsJsonArray("choices").get(0).getAsJsonObject();
                assertEquals("elder_can_repair_town", yes.getAsJsonObject("condition").get("type").getAsString());
                assertEquals(action, yes.getAsJsonArray("actions").get(0).getAsJsonObject().get("repair").getAsString());
                var no = confirm.getAsJsonArray("choices").get(1).getAsJsonObject();
                assertFalse(no.has("actions"));
                assertEquals("repair_menu", no.get("next").getAsString());
            }
            assertTrue(lang.contains("uploads Aetherhaven save data and recent server logs"));
        }
    }
}
