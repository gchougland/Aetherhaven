package com.hexvane.aetherhaven.autonomy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.equipment.data.EquipmentProfileCatalog;
import com.hexvane.aetherhaven.equipment.data.EquipmentProfileDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Builder townsfolk hold the native hammer and repeat a hammering clip while they raise a project. */
@Tag("autonomy")
@Tag("town")
class BuilderWorkVisualsTest {
    private static final Path ROOT = Path.of("src/main/resources");
    private static final Set<String> CHANNELS =
        Set.of("position", "orientation", "shapeStretch", "shapeVisible", "shapeUvOffset");

    private static EquipmentProfileCatalog catalog() throws Exception {
        return EquipmentProfileCatalog.of(List.of(new Gson().fromJson(
            Files.readString(ROOT.resolve("Server/Aetherhaven/Equipment/work_builder.json")),
            EquipmentProfileDefinition.class)));
    }

    @Test void buildersHoldTheNativeHammerNamedByTheirProfile() throws Exception {
        assertEquals("Tool_Hammer_Iron", BuilderWorkVisuals.hammerItemId(catalog()));
        // The base game owns this item. A pack copy would replace its model, scale or grip.
        try (Stream<Path> files = Files.walk(ROOT.resolve("Server/Item/Items"))) {
            assertEquals(List.of(), files.filter(p -> p.getFileName().toString().equals("Tool_Hammer_Iron.json")).toList());
        }
        // A pack that removes the profile still gets a hammer rather than an empty hand.
        assertEquals("Tool_Hammer_Iron", BuilderWorkVisuals.hammerItemId(EquipmentProfileCatalog.empty()));
        assertEquals("Tool_Hammer_Iron", BuilderWorkVisuals.hammerItemId(null));
    }

    @Test void theProfileNamesAClipThatExistsAndTheStrikesFollowOn() throws Exception {
        EquipmentProfileCatalog catalog = catalog();
        String set = BuilderWorkVisuals.animationsId(catalog);
        String id = BuilderWorkVisuals.animationId(catalog);
        assertEquals("Aetherhaven_Work_Actions", set);
        assertEquals("Hammer", id);
        JsonObject binding = JsonParser.parseString(
            Files.readString(ROOT.resolve("Server/Item/Animations/" + set + ".json")))
            .getAsJsonObject().getAsJsonObject("Animations").getAsJsonObject(id);
        assertNotNull(binding, "the work animation set must bind " + id);
        // Each pass is started by the server, so the clip itself must not loop.
        assertFalse(binding.get("Looping").getAsBoolean());
        JsonObject clip = clip(binding);
        long clipMs = clip.get("duration").getAsLong() * 1000L / 60L;
        assertEquals(clipMs, BuilderWorkVisuals.strikeIntervalMs(catalog),
            "a new pass must start as the last one ends, or the arm drops between strikes");
        // The quickest piece takes 1.25 seconds, so even that shows a whole pass.
        assertTrue(clipMs <= 1250L);
        assertEquals(1000L, BuilderWorkVisuals.strikeIntervalMs(EquipmentProfileCatalog.empty()));
    }

    @Test void theClipMovesTheArmsAroundTheHammersOwnGrip() throws Exception {
        JsonObject clip = clip(JsonParser.parseString(
            Files.readString(ROOT.resolve("Server/Item/Animations/Aetherhaven_Work_Actions.json")))
            .getAsJsonObject().getAsJsonObject("Animations").getAsJsonObject("Hammer"));
        int duration = clip.get("duration").getAsInt();
        JsonObject nodes = clip.getAsJsonObject("nodeAnimations");
        // Standing work: legs and hips stay with the idle stance, as in the other work clips.
        assertEquals(Set.of("R-Arm", "R-Forearm", "R-Hand", "L-Arm", "L-Forearm", "Chest", "Head", "R-Attachment"),
            nodes.keySet());
        for (Map.Entry<String, JsonElement> node : nodes.entrySet()) {
            JsonObject tracks = node.getValue().getAsJsonObject();
            // The client rejects a node that lacks any of the five channels.
            assertEquals(CHANNELS, tracks.keySet(), node.getKey());
            for (String channel : CHANNELS) {
                for (JsonElement key : tracks.getAsJsonArray(channel)) {
                    double time = key.getAsJsonObject().get("time").getAsDouble();
                    assertEquals(Math.rint(time), time, 0.0, node.getKey() + " key times are whole frames");
                    assertTrue(time >= 0 && time <= duration, node.getKey());
                }
            }
            // A restarted pass begins exactly where the last one ended.
            JsonArray turns = tracks.getAsJsonArray("orientation");
            assertEquals(0, turns.get(0).getAsJsonObject().get("time").getAsInt(), node.getKey());
            assertEquals(duration, turns.get(turns.size() - 1).getAsJsonObject().get("time").getAsInt(), node.getKey());
            assertEquals(turns.get(0).getAsJsonObject().get("delta"),
                turns.get(turns.size() - 1).getAsJsonObject().get("delta"), node.getKey());
        }
        // The hammer keeps its native place in the hand for the whole clip: only the arms move.
        JsonObject grip = nodes.getAsJsonObject("R-Attachment");
        for (JsonElement key : grip.getAsJsonArray("position")) {
            JsonObject delta = key.getAsJsonObject().getAsJsonObject("delta");
            for (String axis : new String[] {"x", "y", "z"}) assertEquals(0.0, delta.get(axis).getAsDouble(), 1e-9);
        }
        for (JsonElement key : grip.getAsJsonArray("orientation")) {
            JsonObject delta = key.getAsJsonObject().getAsJsonObject("delta");
            for (String axis : new String[] {"x", "y", "z"}) assertEquals(0.0, delta.get(axis).getAsDouble(), 1e-9);
            assertEquals(1.0, delta.get("w").getAsDouble(), 1e-9);
        }
        // It is a swing, not a fidget: the upper arm travels well over thirty degrees.
        double least = 1, most = -1;
        for (JsonElement key : nodes.getAsJsonObject("R-Arm").getAsJsonArray("orientation")) {
            double w = key.getAsJsonObject().getAsJsonObject("delta").get("w").getAsDouble();
            least = Math.min(least, w);
            most = Math.max(most, w);
        }
        assertTrue(Math.toDegrees(2 * (Math.acos(least) - Math.acos(most))) > 30);
    }

    @Test void theCarriedMarkerCanNeverBeMistakenForAClockTime() {
        assertTrue(BuilderWorkVisuals.CARRIED_STRIKE_DUE > 0, "zero means nothing is shown yet");
        assertTrue(BuilderWorkVisuals.CARRIED_STRIKE_DUE < System.currentTimeMillis(), "a strike is due on arrival");
    }

    private static JsonObject clip(JsonObject binding) throws Exception {
        Path file = ROOT.resolve("Common").resolve(binding.get("ThirdPerson").getAsString());
        assertTrue(Files.isRegularFile(file), file.toString());
        String text = Files.readString(file);
        assertFalse(text.startsWith("﻿"), "no byte order mark");
        return JsonParser.parseString(text).getAsJsonObject();
    }
}
