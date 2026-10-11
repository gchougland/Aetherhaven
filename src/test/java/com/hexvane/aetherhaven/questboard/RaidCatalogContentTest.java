package com.hexvane.aetherhaven.questboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** The shipped raid list must stay playable at every town rank. */
class RaidCatalogContentTest {
    private static final Path QUESTS = Path.of("subplugin-assets/Quests/Server");
    private static final List<String> RANKS = List.of("E", "D", "C", "B", "A", "S", "SS", "SSS");

    private static List<JsonObject> raids() throws Exception {
        JsonObject board = JsonParser.parseString(Files.readString(QUESTS.resolve("Aetherhaven/quest_board.json"))).getAsJsonObject();
        List<JsonObject> raids = new ArrayList<>();
        for (var villager : board.getAsJsonObject("villagers").entrySet()) {
            JsonArray entries = villager.getValue().getAsJsonObject().getAsJsonArray("raidEntries");
            if (entries != null) for (JsonElement entry : entries) raids.add(entry.getAsJsonObject());
        }
        return raids;
    }

    private static long gold(JsonObject raid) {
        long gold = 0;
        for (JsonElement reward : raid.getAsJsonArray("rewards")) {
            JsonObject r = reward.getAsJsonObject();
            if ("item".equals(r.get("kind").getAsString()) && "Aetherhaven_Gold_Coin".equals(r.get("itemId").getAsString()))
                gold += r.get("count").getAsInt();
        }
        return gold;
    }

    @Test
    void everyTownRankIsOfferedAtLeastTwoRaids() throws Exception {
        for (int town = 0; town < RANKS.size(); town++) {
            int offered = 0;
            for (JsonObject raid : raids()) {
                int min = RANKS.indexOf(raid.get("minRank").getAsString()), max = RANKS.indexOf(raid.get("maxRank").getAsString());
                assertTrue(min >= 0 && max >= min, raid.get("id") + " has an invalid rank window");
                if (town >= min && town <= max) offered++;
            }
            assertTrue(offered >= 2, "Town rank " + RANKS.get(town) + " is offered only " + offered + " raids");
        }
    }

    @Test
    void everyTownRankCanDrawAHarderOrderOrRaidThatPaysGaiaTokens() throws Exception {
        JsonObject board = JsonParser.parseString(Files.readString(QUESTS.resolve("Aetherhaven/quest_board.json"))).getAsJsonObject();
        boolean[] offered = new boolean[RANKS.size()];
        for (var villager : board.getAsJsonObject("villagers").entrySet()) {
            for (String kind : List.of("fetchEntries", "huntEntries", "raidEntries")) {
                JsonArray entries = villager.getValue().getAsJsonObject().getAsJsonArray(kind);
                if (entries == null) continue;
                for (JsonElement element : entries) {
                    JsonObject quest = element.getAsJsonObject();
                    String rewards = quest.getAsJsonArray("rewards").toString();
                    if (!rewards.contains("Aetherhaven_Shard_Of_Gaia") && !rewards.contains("Aetherhaven_Verdant_Catalyst")) continue;
                    // The owner finds hunts tedious, so Gaia tokens come only from deliveries and raids.
                    assertFalse("huntEntries".equals(kind), quest.get("id") + " is a hunt that pays Gaia tokens");
                    for (int town = RANKS.indexOf(quest.get("minRank").getAsString()); town <= RANKS.indexOf(quest.get("maxRank").getAsString()); town++)
                        offered[town] = true;
                }
            }
        }
        for (int town = 0; town < RANKS.size(); town++) assertTrue(offered[town], "Town rank " + RANKS.get(town) + " has no quest that pays Gaia tokens");
    }

    @Test
    void rescuesNameACampAndTheirTownsfolkGainTwoDifferentSkills() throws Exception {
        int rescues = 0;
        for (JsonObject raid : raids()) {
            if (!raid.has("rescueCampPrefabs")) continue;
            rescues++;
            assertFalse(raid.getAsJsonArray("rescueCampPrefabs").isEmpty(), raid.get("id") + " has no camp");
            assertTrue(raid.get("titleLangKey").getAsString().contains("rescue_"));
        }
        assertTrue(rescues >= 3, "Goblins, trorks and outlanders each need a rescue");
        // Higher rank rescues bring townsfolk who are better at more things.
        assertEquals(2, RescueCampService.boostCount("E"));
        assertEquals(3, RescueCampService.boostCount("C"));
        assertEquals(4, RescueCampService.boostCount("A"));
        assertEquals(5, RescueCampService.boostCount("SS"));
        assertEquals(5, RescueCampService.boostCount("SSS"));
        for (long seed = 0; seed < 50; seed++) {
            for (int count = 2; count <= 5; count++) {
                var boosts = RescueCampService.pickBoosts(new Random(seed), count);
                assertEquals(count, boosts.size());
                assertEquals(count, new java.util.HashSet<>(boosts).size(), "A skill must not be picked twice");
            }
        }
        for (JsonObject raid : raids()) {
            if (!raid.has("rescueCampPrefabs")) continue;
            String rank = raid.get("rank").getAsString();
            int attackers = raid.getAsJsonArray("raidSets").get(0).getAsJsonObject().getAsJsonObject("mobCountsByRank").get(rank).getAsInt();
            assertTrue(attackers >= 5, raid.get("id") + " is too easy with " + attackers + " attackers");
        }
        assertTrue(RescueCampService.levelColumn(70, 68));
        assertFalse(RescueCampService.levelColumn(70, 66));
        var slot = new QuestBoardSlotRecord();
        slot.setQuestType("raid");
        assertFalse(slot.isRescueQuest());
        slot.setRescueCharacterId("briar_mosscap");
        slot.setRescueSkillBoosts(List.of("farming", "mining"));
        slot.setRescueUntied(true);
        var saved = new com.google.gson.Gson().fromJson(new com.google.gson.Gson().toJson(slot), QuestBoardSlotRecord.class);
        assertTrue(saved.isRescueQuest());
        assertTrue(saved.isRescueUntied());
        assertEquals(List.of("farming", "mining"), saved.rescueSkillBoostsOrEmpty());
        saved.clearToEmpty();
        assertFalse(saved.isRescueQuest());
        assertFalse(saved.isRescueUntied());
    }

    @Test
    void harderRaidsPayMoreGold() throws Exception {
        Map<String, Long> lowest = new HashMap<>(), highest = new HashMap<>();
        for (JsonObject raid : raids()) {
            String rank = raid.get("rank").getAsString();
            assertTrue(gold(raid) > 0, raid.get("id") + " pays no gold");
            // A rescue's real reward is the new resident, so its gold is not part of the ladder.
            if (raid.has("rescueCampPrefabs")) continue;
            lowest.merge(rank, gold(raid), Math::min);
            highest.merge(rank, gold(raid), Math::max);
        }
        for (int i = 1; i < RANKS.size(); i++)
            assertTrue(lowest.get(RANKS.get(i)) > highest.get(RANKS.get(i - 1)),
                "Rank " + RANKS.get(i) + " raids must pay more than rank " + RANKS.get(i - 1));
    }

    @Test
    void everyRaiderHasASpawnRoleAndEveryRosterFillsItsRank() throws Exception {
        for (JsonObject raid : raids()) {
            String rank = raid.get("rank").getAsString();
            for (JsonElement element : raid.getAsJsonArray("raidSets")) {
                JsonObject set = element.getAsJsonObject();
                List<String> roles = new ArrayList<>();
                int leaders = 0;
                for (JsonElement mob : set.getAsJsonArray("mobPool")) roles.add(mob.getAsJsonObject().get("roleId").getAsString());
                if (set.has("guaranteedMobs")) for (JsonElement mob : set.getAsJsonArray("guaranteedMobs")) {
                    roles.add(mob.getAsJsonObject().get("roleId").getAsString());
                    leaders += mob.getAsJsonObject().get("count").getAsInt();
                }
                assertFalse(roles.isEmpty(), raid.get("id") + " has no raiders");
                for (String role : roles)
                    assertTrue(Files.isRegularFile(QUESTS.resolve("NPC/Roles/Aetherhaven/Raid/" + RaidQuestMarchRoles.spawnRoleFor(role) + ".json")),
                        raid.get("id") + " uses " + role + " without a raid spawn role");
                int count = set.getAsJsonObject("mobCountsByRank").get(rank).getAsInt();
                assertTrue(leaders < count, raid.get("id") + " has no room for ordinary raiders beside its leaders");
                var parsed = new com.google.gson.Gson().fromJson(set, com.hexvane.aetherhaven.questboard.data.QuestBoardRaidSetJson.class);
                assertEquals(count, RaidQuestBoardHandler.buildMobRoster(parsed, rank, new Random(7)).size(), raid.get("id") + " roster size");
            }
        }
    }
    @Test
    void captorsAreOnlyPlacedWhenAPlayerReachesTheCampAndMissingOnesComeBack() {
        org.joml.Vector3d camp = new org.joml.Vector3d(100.5, 65, 200.5);
        assertTrue(RescueArrivalSystem.near(new org.joml.Vector3d(100.5 + RescueCampService.ARRIVAL_RADIUS - 1, 66, 200.5), camp));
        assertFalse(RescueArrivalSystem.near(new org.joml.Vector3d(100.5 + RescueCampService.ARRIVAL_RADIUS + 1, 66, 200.5), camp),
            "Nobody is placed while the player is still at the quest board");
        assertFalse(RescueArrivalSystem.near(new org.joml.Vector3d(100.5, 65 + RescueCampService.ARRIVAL_RADIUS + 5, 200.5), camp));

        // Five captors, none placed yet: all five are owed. Kills and captors still standing reduce what is owed.
        assertEquals(5, RescueCampService.captorsOwed(5, 0, 0));
        assertEquals(0, RescueCampService.captorsOwed(5, 0, 5));
        assertEquals(1, RescueCampService.captorsOwed(5, 2, 2), "One who vanished is owed again");
        assertEquals(0, RescueCampService.captorsOwed(5, 5, 0), "Nobody comes back once all are defeated");
        assertEquals(0, RescueCampService.captorsOwed(5, 3, 4));

        // Someone unseen is given a moment before being replaced, and the wait starts over once they are seen.
        String key = "test-" + java.util.UUID.randomUUID();
        assertFalse(RescueCampService.missingLongEnough(key, true, 1000));
        assertFalse(RescueCampService.missingLongEnough(key, true, 1000 + RescueCampService.MISSING_GRACE_MS - 1));
        assertTrue(RescueCampService.missingLongEnough(key, true, 1000 + RescueCampService.MISSING_GRACE_MS));
        assertFalse(RescueCampService.missingLongEnough(key, false, 20000));
        assertFalse(RescueCampService.missingLongEnough(key, true, 20001));
    }
    @Test
    void theInteractionCutsTheRopesOnlyOnceTheCaptorsAreDefeated() {
        assertEquals(RescueCampService.CutResult.CAPTORS_REMAIN, RescueCampService.cutResult(true, true, 4, 5));
        assertEquals(RescueCampService.CutResult.FREED, RescueCampService.cutResult(true, true, 5, 5));
        assertEquals(RescueCampService.CutResult.NOT_A_MEMBER, RescueCampService.cutResult(true, false, 5, 5));
        assertEquals(RescueCampService.CutResult.GONE, RescueCampService.cutResult(false, true, 5, 5), "Already free: talk as usual");
        assertEquals(RescueCampService.CutResult.CAPTORS_REMAIN, RescueCampService.cutResult(true, true, 0, 0),
            "Nobody has been placed yet, so nobody has been defeated");
    }

    @Test
    void aCaptorWhoDiesOnArrivalIsNotCountedAndComesBack() {
        long soon = RescueCampService.ARRIVAL_DEATH_MS - 1;
        assertTrue(RescueCampService.diedOnArrival(200, false, 0), "Dead at once, and no player did it");
        assertTrue(RescueCampService.diedOnArrival(soon, false, 3));
        assertFalse(RescueCampService.diedOnArrival(200, true, 0), "A quick kill by a player still counts");
        assertFalse(RescueCampService.diedOnArrival(RescueCampService.ARRIVAL_DEATH_MS, false, 0), "After that, any death counts");
        assertFalse(RescueCampService.diedOnArrival(-1, false, 0), "A captor from before a restart is not known to be new");
        assertFalse(RescueCampService.diedOnArrival(200, false, RescueCampService.MAX_REPLACEMENTS),
            "Replacements stop, so a contract can always be finished");
        assertTrue(RescueCampService.ARRIVAL_SHIELD_MS < RescueCampService.ARRIVAL_DEATH_MS);
    }
}
