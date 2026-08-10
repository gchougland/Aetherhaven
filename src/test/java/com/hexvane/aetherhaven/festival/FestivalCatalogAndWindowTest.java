package com.hexvane.aetherhaven.festival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar;
import com.hexvane.aetherhaven.hud.AetherhavenCalendar.Season;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
final class FestivalCatalogAndWindowTest {
    private static final Gson GSON = new Gson();

    @Test
    void laterFestivalWithSameIdReplacesEarlierOne() {
        FestivalDefinition first = parse("{\"id\":\"harvest\",\"displayName\":\"Old Harvest\",\"season\":\"Autumn\",\"dayOfSeason\":5}");
        FestivalDefinition second = parse("{\"id\":\"harvest\",\"displayName\":\"New Harvest\",\"season\":\"Autumn\",\"dayOfSeason\":9}");

        FestivalCatalog catalog = FestivalCatalog.forTests(List.of(first, second));

        assertEquals(1, catalog.list().size());
        assertEquals("New Harvest", catalog.get("harvest").getDisplayName());
        assertEquals(9, catalog.get("harvest").getDayOfSeason());
    }

    @Test
    void calendarIndexMapsFestivalsToTheirDay() {
        FestivalCatalog catalog = FestivalCatalog.forTests(
            List.of(
                parse("{\"id\":\"new_life\",\"season\":\"Spring\",\"dayOfSeason\":3}"),
                parse("{\"id\":\"lantern\",\"season\":\"Autumn\",\"dayOfSeason\":21}")
            )
        );
        FestivalCalendarIndex index = FestivalCalendarIndex.fromCatalog(catalog);

        assertEquals("new_life", index.festivalOn(Season.SPRING, 3).getId());
        assertEquals("lantern", index.festivalOn(Season.AUTUMN, 21).getId());
        assertNull(index.festivalOn(Season.SPRING, 4));
        assertNull(index.festivalOn(Season.SPRING, 0));
        assertNull(index.festivalOn(Season.SPRING, AetherhavenCalendar.DAYS_PER_SEASON + 1));
    }

    @Test
    void daytimeWindowIsOpenOnlyBetweenStartAndEnd() {
        FestivalDefinition def =
            parse("{\"id\":\"new_life\",\"season\":\"Spring\",\"dayOfSeason\":3,\"startHour\":8,\"endHour\":20}");
        LocalDateTime day = dayOf(Season.SPRING, 3);

        assertFalse(FestivalWindow.isActive(def, day.withHour(7)));
        assertTrue(FestivalWindow.isActive(def, day.withHour(8)));
        assertTrue(FestivalWindow.isActive(def, day.withHour(19).withMinute(59)));
        assertFalse(FestivalWindow.isActive(def, day.withHour(20)));
        assertFalse(FestivalWindow.isActive(def, day.plusDays(1).withHour(12)));
    }

    @Test
    void allDayWindowCoversTheWholeFestivalDay() {
        FestivalDefinition def = parse("{\"id\":\"quiet\",\"season\":\"Winter\",\"dayOfSeason\":1,\"allDay\":true}");
        LocalDateTime day = dayOf(Season.WINTER, 1);

        assertTrue(FestivalWindow.isActive(def, day.withHour(0)));
        assertTrue(FestivalWindow.isActive(def, day.withHour(23).withMinute(59)));
        assertFalse(FestivalWindow.isActive(def, day.plusDays(1).withHour(0)));
    }

    @Test
    void overnightWindowStaysOpenIntoTheNextMorning() {
        FestivalDefinition def =
            parse("{\"id\":\"lantern\",\"season\":\"Autumn\",\"dayOfSeason\":21,\"startHour\":18,\"endHour\":4}");
        LocalDateTime day = dayOf(Season.AUTUMN, 21);

        assertFalse(FestivalWindow.isActive(def, day.withHour(17)));
        assertTrue(FestivalWindow.isActive(def, day.withHour(18)));
        assertTrue(FestivalWindow.isActive(def, day.withHour(23)));
        assertTrue(FestivalWindow.isActive(def, day.plusDays(1).withHour(3)));
        assertFalse(FestivalWindow.isActive(def, day.plusDays(1).withHour(4)));
        assertFalse(FestivalWindow.isActive(def, day.plusDays(1).withHour(18)));
    }

    @Test
    void festivalSquarePrefabNameAndIdAreReserved() {
        assertTrue(CustomFestivalPaths.isReserved("festival_square"));
        assertTrue(CustomFestivalPaths.isReserved("Festival_Square.prefab.json"));
        assertTrue(CustomFestivalPaths.isReserved("Festivals/Festival_Square.prefab.json"));
        assertTrue(CustomFestivalPaths.isReserved("  "));
        assertFalse(CustomFestivalPaths.isReserved("new_life"));
        assertEquals("Festivals/Festival_new_life.prefab.json", CustomFestivalPaths.prefabPathKey("new_life"));
    }

    @Test
    void shippedNewLifeFestivalIsWiredUp() {
        FestivalDefinition def = parseResource("/Server/Aetherhaven/Festivals/new_life.json");

        assertEquals("new_life", def.getId());
        assertEquals(Season.SPRING, def.getSeason());
        assertEquals("Festivals/Festival_New_Life.prefab.json", def.getPrefabPath());
        assertEquals("new_life", def.getMechanicId());
        assertEquals(3, def.getSpots().size());
        assertTrue(def.getSpots().stream().anyMatch(s -> s.getResidentKind().equals("priestess")));
        assertTrue(def.getSpots().stream().anyMatch(s -> s.getResidentKind().equals("farmer")));
        assertTrue(def.getSpots().stream().anyMatch(s -> s.getResidentKind().equals("elder")));
        assertFalse(def.getBurstItemIds().isEmpty());
        assertEquals(1, def.getNpcs().size());
        assertEquals("Aetherhaven_Festival_Seed_Seller", def.getNpcs().get(0).getNpcRoleId());
    }

    @Test
    void shippedPigRaceFestivalIsWiredUp() {
        FestivalDefinition def = parseResource("/Server/Aetherhaven/Festivals/pig_race.json");

        assertEquals("pig_race", def.getId());
        assertEquals(Season.SPRING, def.getSeason());
        assertEquals(20, def.getDayOfSeason());
        assertEquals("Festivals/Festival_Pig_Race.prefab.json", def.getPrefabPath());
        assertEquals("pig_race", def.getMechanicId());
        assertEquals("UI/Custom/pig.png", def.getCalendarIconPath());
        assertEquals(4, def.getSpots().size());
        assertTrue(def.getSpots().stream().anyMatch(s -> s.getResidentKind().equals("elder")));
        assertTrue(def.getSpots().stream().anyMatch(s -> s.getResidentKind().equals("innkeeper")));
        assertTrue(def.getSpots().stream().anyMatch(s -> s.getResidentKind().equals("blacksmith")));
        assertTrue(def.getSpots().stream().anyMatch(s -> s.getResidentKind().equals("rancher")));
        assertEquals(1, def.getNpcs().size());
        assertEquals("Aetherhaven_Festival_Pig_Race_Merchant", def.getNpcs().get(0).getNpcRoleId());
        assertEquals(180.0, def.getNpcs().get(0).getYawDegrees(), 0.001);
        assertEquals(8, def.getTouristSpots().size());
        assertFalse(def.getGreetingLangKeys("default").isEmpty());
    }

    @Test
    void pigRaceRacerRolesAreSpawnableGenerics() throws Exception {
        String[] roles = {
            "Aetherhaven_Festival_Pig_Race_Pink",
            "Aetherhaven_Festival_Pig_Race_Boar",
            "Aetherhaven_Festival_Pig_Race_Undead",
            "Aetherhaven_Festival_Pig_Race_Wild"
        };
        for (String role : roles) {
            try (var in = FestivalCatalogAndWindowTest.class.getResourceAsStream(
                "/Server/NPC/Roles/Aetherhaven/" + role + ".json"
            )) {
                assertNotNull(in, role);
                String json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(json.contains("\"Type\": \"Generic\""), role);
                assertTrue(json.contains("NameTranslationKey"), role);
                assertTrue(json.contains("\"Type\": \"Nothing\""), role);
            }
        }
    }

    @Test
    void pigRacePrefabHasNoMarkerBeams() throws Exception {
        try (var in = FestivalCatalogAndWindowTest.class.getResourceAsStream(
            "/Server/Prefabs/Festivals/Festival_Pig_Race.prefab.json"
        )) {
            assertNotNull(in);
            String json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertFalse(json.contains("Rock_Chalk_Brick_Beam"));
            assertFalse(json.contains("Rock_Gold_Brick_Beam"));
            assertFalse(json.contains("Rock_Aqua_Beam"));
        }
    }

    private static LocalDateTime dayOf(Season season, int dayOfSeason) {
        LocalDateTime start = LocalDateTime.of(2000, 1, 1, 0, 0);
        for (int i = 0; i < AetherhavenCalendar.DAYS_PER_SEASON * Season.values().length; i++) {
            LocalDateTime candidate = start.plusDays(i);
            AetherhavenCalendar.CalendarDate date = AetherhavenCalendar.from(candidate);
            if (date.season() == season && date.dayOfSeason() == dayOfSeason) {
                return candidate;
            }
        }
        throw new IllegalStateException("No calendar day for " + season + " " + dayOfSeason);
    }

    private static FestivalDefinition parse(String json) {
        FestivalDefinition def = GSON.fromJson(json, FestivalDefinition.class);
        assertNotNull(def);
        return def;
    }

    private static FestivalDefinition parseResource(String resourcePath) {
        try (var in = FestivalCatalogAndWindowTest.class.getResourceAsStream(resourcePath)) {
            assertNotNull(in, "missing resource " + resourcePath);
            FestivalDefinition def =
                GSON.fromJson(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8), FestivalDefinition.class);
            assertNotNull(def);
            return def;
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }
}
