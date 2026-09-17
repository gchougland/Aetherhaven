package com.hexvane.aetherhaven.festival.hallowseve;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.festival.FestivalPrefabSize;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class HallowsEvePrefabBoundsTest {
    @Test
    void allMazeBlocksAndEntityOriginsFitReservedSquare() throws Exception {
        var prefab = prefab();
        for (var entry : prefab.getAsJsonArray("blocks")) {
            var block = entry.getAsJsonObject();
            inside(block.get("x").getAsDouble(), block.get("y").getAsDouble(), block.get("z").getAsDouble());
        }
        for (var entry : prefab.getAsJsonArray("entities")) {
            var position = entry.getAsJsonObject().getAsJsonObject("Components")
                .getAsJsonObject("Transform").getAsJsonObject("Position");
            inside(position.get("X").getAsDouble(), position.get("Y").getAsDouble(), position.get("Z").getAsDouble());
        }
    }

    @Test
    void onlyIntendedHallowsEveMusicAndWeatherTriggerRemain() throws Exception {
        int triggers = 0;
        for (var entry : prefab().getAsJsonArray("entities")) {
            var components = entry.getAsJsonObject().getAsJsonObject("Components");
            if (!components.has("TriggerVolume")) continue;
            triggers++;
            var trigger = components.getAsJsonObject("TriggerVolume");
            String effects = trigger.getAsJsonArray("Effects").toString();
            assertTrue(effects.contains("Track_Aetherhaven_Aliens"));
            assertTrue(effects.contains("Zone4_Spooky"));
            assertFalse(effects.contains("Track_Aetherhaven_NostalgiaMachine"));
            var pos = components.getAsJsonObject("Transform").getAsJsonObject("Position");
            for (String corner : new String[] {"Min", "Max"}) {
                var offset = trigger.getAsJsonObject("Shape").getAsJsonObject(corner);
                inside(pos.get("X").getAsDouble() + offset.get("X").getAsDouble(),
                    pos.get("Y").getAsDouble() + offset.get("Y").getAsDouble(),
                    pos.get("Z").getAsDouble() + offset.get("Z").getAsDouble());
            }
        }
        assertEquals(1, triggers);
    }

    private static void inside(double x, double y, double z) {
        assertNull(FestivalPrefabSize.contentOutsideReservedReason(
            (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z),
            (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z)), "Outside festival square: " + x + "," + y + "," + z);
    }

    private JsonObject prefab() throws Exception {
        try (var in = getClass().getClassLoader().getResourceAsStream("Server/Prefabs/Festivals/Festival_Hallows_Eve.prefab.json")) {
            assertNotNull(in);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
