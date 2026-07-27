package com.hexvane.aetherhaven.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hexvane.aetherhaven.villager.data.VillagerDefinitionCatalog;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
class VillagerSchedulePatchApplierTest {

    private final Gson gson = new Gson();

    @Test
    void appendsTransitionsToFileSchedule() {
        VillagerScheduleDefinition schedule = scheduleWith("work");
        Map<String, VillagerScheduleDefinition> schedules = new LinkedHashMap<>();
        schedules.put("Aetherhaven_Merchant", schedule);

        VillagerSchedulePatchDefinition patch = gson.fromJson(
            """
            {
              "schemaVersion": 1,
              "targetScheduleRoleId": "Aetherhaven_Merchant",
              "addTransitions": [
                {
                  "id": "wed_fishing",
                  "dayOfWeek": "WEDNESDAY",
                  "hour": 14,
                  "minute": 0,
                  "location": "fishing_dock"
                }
              ]
            }
            """,
            VillagerSchedulePatchDefinition.class
        );

        assertTrue(
            VillagerSchedulePatchApplier.applyPatch(
                schedules,
                VillagerDefinitionCatalog.empty(),
                locationCatalog("fishing_dock"),
                patch,
                "test"
            )
        );
        assertEquals(2, schedules.get("Aetherhaven_Merchant").getTransitions().size());
        assertEquals("fishing_dock", schedules.get("Aetherhaven_Merchant").getTransitions().get(1).getLocation());
    }

    @Test
    void replacesTransitionById() {
        VillagerScheduleDefinition schedule = gson.fromJson(
            """
            {
              "schemaVersion": 1,
              "transitions": [
                {
                  "id": "wed_slot",
                  "dayOfWeek": "WEDNESDAY",
                  "hour": 14,
                  "minute": 0,
                  "location": "park"
                }
              ]
            }
            """,
            VillagerScheduleDefinition.class
        );
        Map<String, VillagerScheduleDefinition> schedules = new LinkedHashMap<>();
        schedules.put("Aetherhaven_Merchant", schedule);

        VillagerSchedulePatchDefinition patch = gson.fromJson(
            """
            {
              "schemaVersion": 1,
              "targetScheduleRoleId": "Aetherhaven_Merchant",
              "addTransitions": [
                {
                  "id": "wed_slot",
                  "dayOfWeek": "WEDNESDAY",
                  "hour": 14,
                  "minute": 0,
                  "location": "fishing_dock"
                }
              ]
            }
            """,
            VillagerSchedulePatchDefinition.class
        );

        VillagerSchedulePatchApplier.applyPatch(
            schedules,
            VillagerDefinitionCatalog.empty(),
            locationCatalog("fishing_dock"),
            patch,
            "test"
        );
        assertEquals(1, schedule.getTransitions().size());
        assertEquals("fishing_dock", schedule.getTransitions().get(0).getLocation());
    }

    @Test
    void removesTransitionByTime() {
        VillagerScheduleDefinition schedule = gson.fromJson(
            """
            {
              "schemaVersion": 1,
              "transitions": [
                { "dayOfWeek": "TUESDAY", "hour": 17, "minute": 0, "location": "park" },
                { "dayOfWeek": "WEDNESDAY", "hour": 14, "minute": 0, "location": "work" }
              ]
            }
            """,
            VillagerScheduleDefinition.class
        );
        Map<String, VillagerScheduleDefinition> schedules = new LinkedHashMap<>();
        schedules.put("Aetherhaven_Merchant", schedule);

        VillagerSchedulePatchDefinition patch = gson.fromJson(
            """
            {
              "schemaVersion": 1,
              "targetScheduleRoleId": "Aetherhaven_Merchant",
              "removeTransitions": [
                { "dayOfWeek": "TUESDAY", "hour": 17, "minute": 0 }
              ]
            }
            """,
            VillagerSchedulePatchDefinition.class
        );

        VillagerSchedulePatchApplier.applyPatch(
            schedules,
            VillagerDefinitionCatalog.empty(),
            ScheduleLocationCatalog.empty(),
            patch,
            "test"
        );
        assertEquals(1, schedule.getTransitions().size());
        assertEquals("work", schedule.getTransitions().get(0).getLocation());
    }

    @Test
    void patchesEmbeddedScheduleOnMatchingVillager() {
        VillagerDefinition villager = gson.fromJson(
            """
            {
              "npcRoleId": "YourMod_Fisherman",
              "weeklySchedule": {
                "schemaVersion": 1,
                "transitions": [
                  { "dayOfWeek": "MONDAY", "hour": 6, "minute": 0, "location": "work" }
                ]
              }
            }
            """,
            VillagerDefinition.class
        );
        Map<String, VillagerDefinition> byRole = new LinkedHashMap<>();
        byRole.put("YourMod_Fisherman", villager);
        VillagerDefinitionCatalog catalog = VillagerDefinitionCatalog.forTests(byRole);

        VillagerSchedulePatchDefinition patch = gson.fromJson(
            """
            {
              "schemaVersion": 1,
              "targetScheduleRoleId": "YourMod_Fisherman",
              "addTransitions": [
                { "dayOfWeek": "FRIDAY", "hour": 12, "minute": 0, "location": "inn" }
              ]
            }
            """,
            VillagerSchedulePatchDefinition.class
        );

        VillagerSchedulePatchApplier.applyPatch(
            new LinkedHashMap<>(),
            catalog,
            ScheduleLocationCatalog.empty(),
            patch,
            "test"
        );
        assertNotNull(villager.getWeeklySchedule());
        assertEquals(2, villager.getWeeklySchedule().getTransitions().size());
    }

    @Test
    void rejectsPatchWithoutTargetRole() {
        VillagerSchedulePatchDefinition patch = gson.fromJson(
            """
            { "schemaVersion": 1, "addTransitions": [] }
            """,
            VillagerSchedulePatchDefinition.class
        );
        assertFalse(
            VillagerSchedulePatchApplier.applyPatch(
                new LinkedHashMap<>(),
                VillagerDefinitionCatalog.empty(),
                ScheduleLocationCatalog.empty(),
                patch,
                "test"
            )
        );
    }

    private static VillagerScheduleDefinition scheduleWith(@Nonnull String location) {
        VillagerScheduleDefinition def = new VillagerScheduleDefinition();
        VillagerScheduleTransition t = new VillagerScheduleTransition();
        t.setDayOfWeek("MONDAY");
        t.setHour(6);
        t.setMinute(0);
        t.setLocation(location);
        def.appendTransition(t);
        return def;
    }

    private static ScheduleLocationCatalog locationCatalog(@Nonnull String symbol) {
        ScheduleLocationDefinition loc = new ScheduleLocationDefinition();
        loc.setConstructionId("plot_fishing_shop");
        Map<String, ScheduleLocationDefinition> map = new LinkedHashMap<>();
        map.put(symbol, loc);
        return ScheduleLocationCatalog.forTests(map);
    }
}
