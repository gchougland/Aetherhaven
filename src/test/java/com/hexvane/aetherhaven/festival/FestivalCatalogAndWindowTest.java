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

    @Test
    void hallowsEvePrefabHasNoIceEssenceMarkers() throws Exception {
        try (var in = FestivalCatalogAndWindowTest.class.getResourceAsStream(
            "/Server/Prefabs/Festivals/Festival_Hallows_Eve.prefab.json"
        )) {
            assertNotNull(in);
            String json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            assertFalse(json.contains("Ingredient_Ice_Essence"));
        }
    }

    @Test
    void shippedHallowsEveFestivalIsWiredUp() {
        FestivalDefinition def = parseResource("/Server/Aetherhaven/Festivals/hallows_eve.json");

        assertEquals("hallows_eve", def.getId());
        assertEquals(Season.AUTUMN, def.getSeason());
        assertEquals(15, def.getStartHour());
        assertEquals(0, def.getEndHour());
        assertEquals("Festivals/Festival_Hallows_Eve.prefab.json", def.getPrefabPath());
        assertEquals("hallows_eve", def.getMechanicId());
        assertEquals("UI/Custom/maze.png", def.getCalendarIconPath());
        assertEquals(5, def.getSpots().size());
        assertTrue(def.getSpots().stream().anyMatch(s -> s.getResidentKind().equals("priestess")));
        assertTrue(def.getSpots().stream().anyMatch(s -> s.getResidentKind().equals("elder")));
        assertTrue(def.getSpots().stream().anyMatch(s -> s.getResidentKind().equals("innkeeper")));
        assertTrue(def.getSpots().stream().anyMatch(s -> s.getResidentKind().equals("bard")));
        assertEquals(1, def.getNpcs().size());
        assertEquals("Aetherhaven_Festival_Hallows_Eve_Merchant", def.getNpcs().get(0).getNpcRoleId());
        assertNotNull(def.getMazeStartLocal());
        assertEquals(-1, def.getMazeStartLocal().getLocalX());
        assertEquals(6, def.getMazeStartLocal().getLocalY());
        assertEquals(14, def.getMazeStartLocal().getLocalZ());
        assertEquals(176.62329, def.getMazeStartLocal().getYawDegrees(), 0.001);
        assertNotNull(def.getCenterpieceLocalExact());
        assertEquals(1.0, def.getCenterpieceLocalExact()[0], 1e-9);
        assertEquals(7.0, def.getCenterpieceLocalExact()[1], 1e-9);
        assertEquals(1.0, def.getCenterpieceLocalExact()[2], 1e-9);
        assertEquals(25, def.getOrbSpawns().size());
        assertEquals(8, def.getTouristSpots().size());
        assertFalse(def.getGreetingLangKeys("default").isEmpty());
    }

    @Test
    void shippedMarketFestivalIsWiredUp() {
        FestivalDefinition def = parseResource("/Server/Aetherhaven/Festivals/market.json");

        assertEquals("market", def.getId());
        assertEquals(Season.AUTUMN, def.getSeason());
        assertEquals(8, def.getStartHour());
        assertEquals(20, def.getEndHour());
        assertEquals("Festivals/Festival_Market.prefab.json", def.getPrefabPath());
        assertEquals("market", def.getMechanicId());
        assertEquals("UI/Custom/market.png", def.getCalendarIconPath());
        assertEquals(4, def.getSpots().size());
        assertTrue(def.getSpots().stream().anyMatch(s -> s.getResidentKind().equals("elder")));
        assertEquals(3, def.getSpots().stream().filter(s -> s.getResidentKind().equals("market_shop")).count());
        assertTrue(def.getNpcs().isEmpty());
        assertEquals(4, def.getMarketStands().size());
        assertEquals(9, def.getMarketDisplaySlots().size());
        assertEquals(8, def.getTouristSpots().size());
        assertFalse(def.getGreetingLangKeys("default").isEmpty());
        assertFalse(def.getGreetingLangKeys("elder").isEmpty());
    }

    @Test
    void shippedSnowballFestivalIsWiredUp() {
        FestivalDefinition def = parseResource("/Server/Aetherhaven/Festivals/snowball.json");

        assertEquals("snowball", def.getId());
        assertEquals(Season.WINTER, def.getSeason());
        assertEquals(7, def.getDayOfSeason());
        assertEquals(8, def.getStartHour());
        assertEquals(20, def.getEndHour());
        assertEquals("Festivals/Festival_Snowball.prefab.json", def.getPrefabPath());
        assertEquals("snowball", def.getMechanicId());
        assertEquals("UI/Custom/winter.png", def.getCalendarIconPath());
        assertEquals(4, def.getSpots().size());
        assertTrue(def.getSpots().stream().anyMatch(s -> s.getResidentKind().equals("elder")));
        assertTrue(def.getSpots().stream().anyMatch(s -> s.getResidentKind().equals("innkeeper")));
        assertTrue(def.getSpots().stream().anyMatch(s -> s.getResidentKind().equals("farmer")));
        assertTrue(def.getSpots().stream().anyMatch(s -> s.getResidentKind().equals("florist")));
        assertEquals(1, def.getNpcs().size());
        assertEquals("Aetherhaven_Festival_Snowball_Merchant", def.getNpcs().get(0).getNpcRoleId());
        assertEquals(8, def.getTouristSpots().size());
        assertEquals(10, def.getSnowballPileSpots().size());
        assertEquals(4, def.getSnowballTeamASpots().size());
        assertEquals(4, def.getSnowballTeamBSpots().size());
        assertNotNull(def.getSnowballOutLocal());
        assertFalse(def.getGreetingLangKeys("default").isEmpty());
        assertFalse(def.getGreetingLangKeys("elder").isEmpty());
        assertFalse(def.getGreetingLangKeys("clown").isEmpty());
        assertFalse(def.getGreetingLangKeys("guard").isEmpty());
    }

    @Test
    void snowballCalendarUsesWinterIconEvenFromTheOldSnowflakePath() {
        FestivalDefinition leftover =
            parse("{\"id\":\"snowball\",\"mechanicId\":\"snowball\",\"calendarIconPath\":\"UI/Custom/snowflake.png\"}");
        FestivalDefinition missing = parse("{\"id\":\"snowball\",\"mechanicId\":\"snowball\"}");

        assertEquals("UI/Custom/winter.png", leftover.getCalendarIconPath());
        assertEquals("UI/Custom/winter.png", missing.getCalendarIconPath());
    }

    @Test
    void shippedWintertideFestivalIsWiredUp() {
        FestivalDefinition def = parseResource("/Server/Aetherhaven/Festivals/wintertide.json");

        assertEquals("wintertide", def.getId());
        assertEquals(Season.WINTER, def.getSeason());
        assertEquals(21, def.getDayOfSeason());
        assertEquals(8, def.getStartHour());
        assertEquals(20, def.getEndHour());
        assertEquals("Festivals/Festival_Wintertide.prefab.json", def.getPrefabPath());
        assertEquals("wintertide", def.getMechanicId());
        assertEquals("UI/Custom/christmas-tree.png", def.getCalendarIconPath());
        assertEquals(17, def.getSpots().size());
        assertTrue(def.getSpots().stream().anyMatch(s -> s.getResidentKind().equals("elder")));
        assertTrue(def.getSpots().stream().anyMatch(s -> s.getResidentKind().equals("innkeeper")));
        assertTrue(def.getSpots().stream().anyMatch(s -> s.getResidentKind().equals("florist")));
        assertTrue(def.getSpots().stream().anyMatch(s -> s.getResidentKind().equals("bard")));
        assertTrue(def.getSpots().stream().anyMatch(s -> s.getResidentKind().equals("chef")));
        assertTrue(def.getSpots().stream().anyMatch(s -> s.getResidentKind().equals("miner")));
        assertTrue(def.getSpots().stream().anyMatch(s -> s.getResidentKind().equals("blacksmith")));
        assertTrue(def.getSpots().stream().anyMatch(s -> s.getResidentKind().equals("guild_master")));
        assertEquals(1, def.getNpcs().size());
        assertEquals("Aetherhaven_Festival_Wintertide_Merchant", def.getNpcs().get(0).getNpcRoleId());
        assertEquals(8, def.getTouristSpots().size());
        assertFalse(def.getGreetingLangKeys("default").isEmpty());
        assertFalse(def.getGreetingLangKeys("clown").isEmpty());
        assertFalse(def.getGreetingLangKeys("guard").isEmpty());
    }

    @Test
    void shippedMarketItemCatalogHasCategoryBonusAndBaseGameGoods() throws Exception {
        try (var in = FestivalCatalogAndWindowTest.class.getResourceAsStream(
            "/Server/Aetherhaven/Market/market_items.json"
        )) {
            assertNotNull(in);
            String json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            com.google.gson.JsonObject root =
                GSON.fromJson(json, com.google.gson.JsonObject.class);
            assertEquals(10, root.get("categoryBonus").getAsInt());
            com.google.gson.JsonObject items = root.getAsJsonObject("items");
            assertNotNull(items);
            assertTrue(items.size() > 100);
            assertTrue(items.has("Weapon_Sword_Iron"));
            assertTrue(items.has("Weapon_Sword_Adamantite"));
            assertTrue(items.has("Plant_Crop_Carrot_Item"));
            assertTrue(items.has("Rock_Gem_Ruby"));
            assertFalse(items.has("Weapon_Arrow_Iron"));
            assertFalse(items.has("Food_Egg"));
            assertFalse(items.has("Food_Vegetable_Cooked"));
            assertEquals(8, items.getAsJsonObject("Rock_Gem_Ruby").get("points").getAsInt());
            assertEquals(8, items.getAsJsonObject("Rock_Gem_Diamond").get("points").getAsInt());
            assertEquals(7, items.getAsJsonObject("Rock_Gem_Emerald").get("points").getAsInt());
            assertEquals(5, items.getAsJsonObject("Fish_Minnow_Item").get("points").getAsInt());
            assertEquals(7, items.getAsJsonObject("Fish_Salmon_Item").get("points").getAsInt());
            assertEquals(9, items.getAsJsonObject("Fish_Frostgill_Item").get("points").getAsInt());
            assertEquals(12, items.getAsJsonObject("Fish_Whale_Humpback_Item").get("points").getAsInt());
            assertEquals(5, items.getAsJsonObject("Plant_Crop_Wheat_Item").get("points").getAsInt());
            assertEquals(6, items.getAsJsonObject("Plant_Crop_Carrot_Item").get("points").getAsInt());
            assertEquals(11, items.getAsJsonObject("Plant_Crop_Potato_Item").get("points").getAsInt());
            assertEquals(4, items.getAsJsonObject("Food_Fish_Grilled").get("points").getAsInt());
            assertEquals(6, items.getAsJsonObject("Food_Bread").get("points").getAsInt());
            assertEquals(8, items.getAsJsonObject("Food_Kebab_Meat").get("points").getAsInt());
            assertEquals(10, items.getAsJsonObject("Food_Salad_Caesar").get("points").getAsInt());
            assertEquals(12, items.getAsJsonObject("Food_Pie_Apple").get("points").getAsInt());
            int adamantite = items.getAsJsonObject("Weapon_Sword_Adamantite").get("points").getAsInt();
            int mithril = items.getAsJsonObject("Weapon_Sword_Mithril").get("points").getAsInt();
            int onyxium = items.getAsJsonObject("Weapon_Sword_Onyxium").get("points").getAsInt();
            int prisma = items.getAsJsonObject("Weapon_Mace_Prisma").get("points").getAsInt();
            assertTrue(adamantite < mithril);
            assertTrue(mithril < onyxium);
            assertTrue(onyxium < prisma);
            assertTrue(prisma <= 12);
            assertEquals(
                items.getAsJsonObject("Weapon_Sword_Cobalt").get("points").getAsInt(),
                items.getAsJsonObject("Weapon_Sword_Thorium").get("points").getAsInt()
            );
        }
    }

    @Test
    void shippedMarketVendorShopsHaveAtLeastSixDifferentItems() throws Exception {
        String[] shops = {
            "Bard",
            "Blacksmith",
            "Builder",
            "Chef",
            "Crystal_Keeper",
            "Farmer",
            "Florist",
            "Innkeeper",
            "Logger",
            "Merchant",
            "Miner",
            "Priestess",
            "Pyrotechnic",
            "Rancher"
        };
        for (String shop : shops) {
            String path = "/Server/BarterShops/Aetherhaven_Festival_Market_" + shop + ".json";
            try (var in = FestivalCatalogAndWindowTest.class.getResourceAsStream(path)) {
                assertNotNull(in, "missing shop " + path);
                String json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                com.google.gson.JsonObject root = GSON.fromJson(json, com.google.gson.JsonObject.class);
                java.util.Set<String> ids = new java.util.LinkedHashSet<>();
                for (com.google.gson.JsonElement slotEl : root.getAsJsonArray("TradeSlots")) {
                    com.google.gson.JsonObject output =
                        slotEl.getAsJsonObject().getAsJsonObject("Trade").getAsJsonObject("Output");
                    ids.add(output.get("ItemId").getAsString());
                }
                assertTrue(ids.size() >= 6, shop + " has " + ids.size() + " items: " + ids);
            }
        }
    }

    @Test
    void festivalOnIgnoresLooksEvenWhenTheyShareADay() {
        FestivalCatalog catalog = FestivalCatalog.forTests(
            List.of(
                parse("{\"id\":\"carnival\",\"season\":\"Summer\",\"dayOfSeason\":21}"),
                parse(
                    "{\"id\":\"carnival_neon\",\"displayName\":\"Neon Carnival\",\"season\":\"Summer\",\"dayOfSeason\":21,\"festivalVariant\":true,\"countsAsFestivalId\":\"carnival\"}"
                )
            )
        );

        FestivalDefinition today = catalog.festivalOn(Season.SUMMER, 21);
        assertNotNull(today);
        assertEquals("carnival", today.getId());
        assertEquals("carnival", FestivalCalendarIndex.fromCatalog(catalog).festivalOn(Season.SUMMER, 21).getId());
        assertEquals(1, catalog.listBases().size());
        assertEquals(2, catalog.list().size());
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
