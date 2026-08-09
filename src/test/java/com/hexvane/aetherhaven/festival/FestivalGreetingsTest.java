package com.hexvane.aetherhaven.festival;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Festival greetings: the lines townsfolk swap in while their town is celebrating. */
@Tag("town")
final class FestivalGreetingsTest {
    private static final Gson GSON = new Gson();

    /** Every permanent resident who lives in town and can be talked to. */
    private static final List<String> TOWN_VILLAGER_KINDS = List.of(
        TownVillagerBinding.KIND_ELDER,
        TownVillagerBinding.KIND_INNKEEPER,
        TownVillagerBinding.KIND_MERCHANT,
        TownVillagerBinding.KIND_CHEF,
        TownVillagerBinding.KIND_FARMER,
        TownVillagerBinding.KIND_BLACKSMITH,
        TownVillagerBinding.KIND_PRIESTESS,
        TownVillagerBinding.KIND_MINER,
        TownVillagerBinding.KIND_LOGGER,
        TownVillagerBinding.KIND_RANCHER,
        TownVillagerBinding.KIND_CRYSTAL_KEEPER,
        TownVillagerBinding.KIND_PYROTECHNIC,
        TownVillagerBinding.KIND_FLORIST,
        TownVillagerBinding.KIND_BUILDER,
        TownVillagerBinding.KIND_GUILD_MASTER,
        TownVillagerBinding.KIND_BARD
    );

    @Test
    void villagersWithNoLinesOfTheirOwnUseTheDefaultOnes() {
        FestivalDefinition def = parse(
            "{\"id\":\"x\",\"greetings\":{\"Farmer\":[\"a.one\"],\"default\":[\"a.two\"]}}"
        );

        assertEquals(List.of("a.one"), def.getGreetingLangKeys("farmer"));
        assertEquals(List.of("a.one"), def.getGreetingLangKeys("FARMER"), "kinds are matched without case");
        assertEquals(List.of("a.two"), def.getGreetingLangKeys("miner"));
        assertEquals(List.of("a.two"), def.getGreetingLangKeys(null));
    }

    @Test
    void aFestivalCanShipWithoutAnyGreetingsAtAll() {
        FestivalDefinition bare = parse("{\"id\":\"x\"}");
        assertTrue(bare.getGreetings().isEmpty());
        assertTrue(bare.getGreetingLangKeys("farmer").isEmpty());

        FestivalDefinition noFallback = parse("{\"id\":\"x\",\"greetings\":{\"farmer\":[\"a.one\"]}}");
        assertTrue(noFallback.getGreetingLangKeys("miner").isEmpty(), "no default means everyone else stays normal");
    }

    @Test
    void blankKindsAndBlankKeysAreDropped() {
        FestivalDefinition def = parse(
            "{\"id\":\"x\",\"greetings\":{\"  \":[\"a.one\"],\"farmer\":[\"\",\"  a.two  \"],\"chef\":[]}}"
        );

        assertEquals(Set.of("farmer"), def.getGreetings().keySet());
        assertEquals(List.of("a.two"), def.getGreetingLangKeys("farmer"));
    }

    @Test
    void everyTownVillagerHasSomethingToSayAtTheNewLifeFestival() {
        FestivalDefinition def = newLife();
        Set<String> kinds = def.getGreetings().keySet();

        for (String kind : TOWN_VILLAGER_KINDS) {
            assertTrue(kinds.contains(kind), "New Life has no greeting written for the " + kind);
        }
        assertTrue(
            kinds.contains(FestivalDefinition.GREETING_DEFAULT_KIND),
            "New Life needs a default line so townsfolk and visitors join in too"
        );
    }

    @Test
    void everyNewLifeGreetingKeyHasEnglishText() {
        Map<String, Set<String>> loadedBundles = new HashMap<>();
        Set<String> seen = new LinkedHashSet<>();

        for (Map.Entry<String, List<String>> entry : newLife().getGreetings().entrySet()) {
            for (String fullKey : entry.getValue()) {
                assertTrue(seen.add(fullKey), "greeting key used twice: " + fullKey);
                int split = fullKey.indexOf('.');
                assertTrue(split > 0, "greeting key needs a lang bundle prefix: " + fullKey);
                String bundle = fullKey.substring(0, split);
                String lineKey = fullKey.substring(split + 1);
                Set<String> keys = loadedBundles.computeIfAbsent(bundle, FestivalGreetingsTest::readLangKeys);
                assertTrue(keys.contains(lineKey), "no English line for " + fullKey);
            }
        }
        assertFalse(seen.isEmpty());
    }

    private static FestivalDefinition newLife() {
        try (InputStream in =
            FestivalGreetingsTest.class.getResourceAsStream("/Server/Aetherhaven/Festivals/new_life.json")) {
            assertNotNull(in, "missing new_life.json");
            FestivalDefinition def =
                GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), FestivalDefinition.class);
            assertNotNull(def);
            return def;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Set<String> readLangKeys(String bundle) {
        String path = "/Server/Languages/en-US/" + bundle + ".lang";
        Set<String> keys = new LinkedHashSet<>();
        try (InputStream in = FestivalGreetingsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing lang bundle " + path);
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                int eq = trimmed.indexOf('=');
                if (trimmed.isEmpty() || trimmed.startsWith("#") || eq <= 0) {
                    continue;
                }
                keys.add(trimmed.substring(0, eq).trim());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return keys;
    }

    private static FestivalDefinition parse(String json) {
        FestivalDefinition def = GSON.fromJson(json, FestivalDefinition.class);
        assertNotNull(def);
        return def;
    }
}
