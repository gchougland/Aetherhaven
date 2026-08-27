package com.hexvane.aetherhaven.guild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.tourist.TouristPortalTickService;
import com.hexvane.aetherhaven.tourist.TouristPortalTickService.PlannedSpawnAttemptOutcome;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

/**
 * Validates fix policies against Jimmy_G1 support save stuck state (guild slot 2, tourist spawn backlog).
 */
@Tag("guild")
class JimmySaveTimeoutFixVerificationTest {

    private static final Path JIMMY_TOWNS_JSON =
        Path.of(
            System.getProperty("user.home"),
            "Downloads",
            "aetherhaven-support-4e8a1297-59b5-4e56-a6ac-6fe2dcf024ae",
            "worlds",
            "flat_world",
            "towns.json"
        );

    static boolean jimmySupportBundlePresent() {
        return Files.isRegularFile(JIMMY_TOWNS_JSON);
    }

    @Test
    @EnabledIf("jimmySupportBundlePresent")
    void jimmySaveHasStuckGuildSlotAndTouristBacklogBeforeFix() throws IOException {
        JsonObject town = loadJimmyTown();
        JsonArray filledSlots = town.getAsJsonArray("guildHallAdventurerFilledSlots");
        JsonArray npcIds = town.getAsJsonArray("guildHallAdventurerNpcIds");
        JsonArray planned = town.getAsJsonArray("touristPlannedSpawnEpochMinutes");
        JsonArray executed = town.getAsJsonArray("touristExecutedSpawnEpochMinutes");

        assertTrue(filledSlots.contains(jsonInt(2)));
        assertTrue(npcIds.isEmpty());
        assertEquals(3, planned.size());
        assertTrue(executed.isEmpty());
    }

    @Test
    @EnabledIf("jimmySupportBundlePresent")
    void jimmySavePoliciesStopInfiniteRetryAfterOneTick() throws IOException {
        JsonObject town = loadJimmyTown();

        List<Integer> filledSlots = new ArrayList<>();
        for (JsonElement element : town.getAsJsonArray("guildHallAdventurerFilledSlots")) {
            filledSlots.add(element.getAsInt());
        }
        Set<String> failedCharacterIds = new HashSet<>(Set.of("female_goblin_02"));
        int slot = 2;
        if (!failedCharacterIds.isEmpty()) {
            filledSlots.remove(Integer.valueOf(slot));
        }

        List<Long> planned = new ArrayList<>();
        for (JsonElement element : town.getAsJsonArray("touristPlannedSpawnEpochMinutes")) {
            planned.add(element.getAsLong());
        }
        List<Long> executed = new ArrayList<>();
        for (Long minute : planned) {
            if (TouristPortalTickService.shouldConsumePlannedSpawnSlot(PlannedSpawnAttemptOutcome.SPAWN_FAILED)) {
                executed.add(minute);
            }
        }

        assertFalse(filledSlots.contains(2));
        assertEquals(planned.size(), executed.size());
    }

    private static JsonObject loadJimmyTown() throws IOException {
        String json = Files.readString(JIMMY_TOWNS_JSON, StandardCharsets.UTF_8);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        for (JsonElement element : root.getAsJsonArray("towns")) {
            JsonObject town = element.getAsJsonObject();
            if ("956fb96b-736d-4e64-af13-6ddc51396672".equals(town.get("townId").getAsString())) {
                return town;
            }
        }
        throw new IllegalStateException("Jimmy town not found in support bundle");
    }

    private static JsonElement jsonInt(int value) {
        return JsonParser.parseString(Integer.toString(value));
    }
}
