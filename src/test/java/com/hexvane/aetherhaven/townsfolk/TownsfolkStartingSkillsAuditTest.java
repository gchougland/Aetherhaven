package com.hexvane.aetherhaven.townsfolk;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterDefinition;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class TownsfolkStartingSkillsAuditTest {
    private static final Gson GSON = new Gson();

    @Test void everyAuthoredResidentHasIndividualStrengthsAndMostlyBasicSkills() throws Exception {
        int count = 0, incapableCount = 0;
        var validSkills = new HashSet<String>();
        for (var skill : TownsfolkSkill.values()) validSkills.add(skill.id);
        try (var paths = Files.list(Path.of("src/main/resources/Server/Aetherhaven/Townsfolk"))) {
            for (var path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                var character = GSON.fromJson(Files.readString(path), TownsfolkCharacterDefinition.class);
                var authored = character.getStartingSkills();
                var progress = TownsfolkProgressService.startingProgress(character);
                assertFalse(authored.isEmpty(), character.getId());
                assertTrue(validSkills.containsAll(authored.keySet()), character.getId());
                assertTrue(authored.values().stream().allMatch(v -> v >= 0 && v <= 10), character.getId());
                long good = authored.values().stream().filter(v -> v >= 4).count();
                long moderate = authored.values().stream().filter(v -> v == 2 || v == 3).count();
                assertTrue(good >= 1 && good <= 2, character.getId());
                assertTrue(moderate >= 1 && moderate <= 2, character.getId());
                for (var skill : TownsfolkSkill.values())
                    assertEquals(authored.getOrDefault(skill.id, 1).intValue(), progress.skill(skill), character.getId() + ": " + skill);
                if (authored.containsValue(0)) incapableCount++;
                count++;
            }
        }
        assertEquals(67, count);
        assertTrue(incapableCount > 0 && incapableCount < count / 3, "Incapable professions are occasional");
    }

    @Test void characterJsonAloneControlsStatsRegardlessOfPersonalityOrEquipment() {
        var json = GSON.fromJson("{\"personalityIds\":[\"bookworm\",\"crafty\"],\"equipmentProfileId\":\"guard_mage\",\"startingSkills\":{\"mining\":5,\"cooking\":3,\"alchemy\":0},\"startingAttributes\":{\"strength\":7}}", com.google.gson.JsonObject.class);
        var first = TownsfolkProgressService.startingProgress(GSON.fromJson(json, TownsfolkCharacterDefinition.class));
        json.add("personalityIds", GSON.toJsonTree(List.of("sporty", "lazy")));
        json.addProperty("equipmentProfileId", "guard_knight");
        var second = TownsfolkProgressService.startingProgress(GSON.fromJson(json, TownsfolkCharacterDefinition.class));
        assertEquals(GSON.toJson(first), GSON.toJson(second));
        assertEquals(5, first.skill(TownsfolkSkill.MINING));
        assertEquals(3, first.skill(TownsfolkSkill.COOKING));
        assertEquals(1, first.skill(TownsfolkSkill.CRAFTING));
        assertEquals(0, first.skill(TownsfolkSkill.ALCHEMY));
        assertFalse(first.train(TownsfolkSkill.ALCHEMY));
        assertEquals(7, first.attribute(TownsfolkAttribute.STRENGTH));
        assertEquals(5, first.attribute(TownsfolkAttribute.INTELLIGENCE));
    }

    @Test void existingEarnedProgressIsNotReinitializedFromChangedStartingStats() {
        var town = new TownRecord();
        var progress = new TownsfolkProgress();
        progress.setSkill(TownsfolkSkill.MINING, 8);
        town.getTownsfolkProgress().put("mertie", progress);
        assertSame(progress, TownsfolkProgressService.getOrCreate(town, null, null, "Mertie"));
        assertEquals(8, progress.skill(TownsfolkSkill.MINING));
    }
}
