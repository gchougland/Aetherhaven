package com.hexvane.aetherhaven.town;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Which Town Level each newer core villager first visits at. */
@Tag("town")
class TownRecruitmentLevelsTest {
    private static final Path ROOT = Path.of("src/main/resources");
    private static JsonObject villager(String role) throws Exception {
        return JsonParser.parseString(Files.readString(ROOT.resolve("Server/Aetherhaven/Villagers/Aetherhaven_" + role + ".json"))).getAsJsonObject();
    }
    private static String guide(String name) throws Exception {
        return Files.readString(ROOT.resolve("Server/Aetherhaven/GuideTopics/en-US/" + name + ".md")).replace("\r\n", "\n");
    }

    @Test void theWarehouseManagerArrivesAtTownLevelTwoAndTheCrafterStillComesAfterHer() throws Exception {
        int mara = villager("Warehouse_Manager").get("minimumTownLevel").getAsInt();
        int tobin = villager("Crafter").get("minimumTownLevel").getAsInt();
        assertEquals(2, mara);
        assertEquals(3, tobin);
        assertTrue(tobin > mara, "The Crafter's visit depends on the Warehouse Manager");
        assertTrue(mara > 1, "Level 1 visitors are the Farmer and Rancher only");
        // The built in table is used when a villager's file states no level. It must agree.
        assertEquals(mara, TownRecruitment.fallbackLevel("Aetherhaven_Warehouse_Manager"));
        assertEquals(tobin, TownRecruitment.fallbackLevel("Aetherhaven_Crafter"));
        assertEquals(2, TownRecruitment.fallbackLevel("Aetherhaven_Someone_New"));
    }

    @Test void theDoctorArrivesAtTownLevelFourAndEveryNewerVillagerHasOneAgreedLevel() throws Exception {
        var expected = new java.util.LinkedHashMap<String, Integer>();
        expected.put("Warehouse_Manager", 2);
        expected.put("Crafter", 3);
        expected.put("Doctor", 4); expected.put("Druid", 4); expected.put("Leatherworker", 4); expected.put("Monster_Hunter", 4);
        expected.put("Alchemist", 5);
        for (var entry : expected.entrySet()) {
            assertEquals(entry.getValue().intValue(), villager(entry.getKey()).get("minimumTownLevel").getAsInt(), entry.getKey());
            assertEquals(entry.getValue().intValue(), TownRecruitment.fallbackLevel("Aetherhaven_" + entry.getKey()), entry.getKey() + " in the built in table");
        }
        assertTrue(guide("villager_doctor").contains("at the Inn at **Town Level 4**."));
        String population = guide("mechanic_population");
        String three = population.substring(population.indexOf("### Town Level 3"), population.indexOf("### Town Level 4"));
        String four = population.substring(population.indexOf("### Town Level 4"), population.indexOf("### Town Level 5"));
        assertFalse(three.contains("Doctor"));
        assertTrue(four.contains("Doctor"));
    }

    @Test void theGuideNamesTheSameLevelForTheWarehouseManager() throws Exception {
        assertTrue(guide("villager_warehouse_manager").contains("Mara can visit from **Town Level 2**."));
        String population = guide("mechanic_population");
        String two = population.substring(population.indexOf("### Town Level 2"), population.indexOf("### Town Level 3"));
        String three = population.substring(population.indexOf("### Town Level 3"), population.indexOf("### Town Level 4"));
        assertTrue(two.contains("Warehouse Manager"));
        assertFalse(three.contains("Warehouse Manager"));
        assertTrue(three.contains("Crafter"));
    }
}
