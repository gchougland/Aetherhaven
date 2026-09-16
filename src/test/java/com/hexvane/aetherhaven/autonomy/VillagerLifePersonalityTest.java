package com.hexvane.aetherhaven.autonomy;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkPersonalityDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Tag("autonomy")
class VillagerLifePersonalityTest {
    private static final Path RES = Path.of("src/main/resources");
    private TownsfolkPersonalityDefinition trait(String id) throws Exception {
        return new Gson().fromJson(Files.readString(RES.resolve("Server/Aetherhaven/Personalities/" + id + ".json")), TownsfolkPersonalityDefinition.class);
    }

    @Test void mixedPersonalitiesAndFavoriteGiftsAllContribute() throws Exception {
        var weights = VillagerLifePersonality.topicWeights(List.of(trait("stingy"), trait("bookworm")), List.of("Food_Bread", "Missing_Mod_Item"));
        assertEquals(8, weights.get("Item_Aetherhaven_Gold_Coin"));
        assertEquals(8, weights.get("Item_Deco_Scrap_Book_Pile_Small"));
        assertEquals(2, weights.get("Item_Food_Bread"));
        assertFalse(weights.containsKey("Item_Missing_Mod_Item"));
        assertTrue(weights.get("Item_Aetherhaven_Gold_Coin") > weights.get("Home"));
    }

    @Test void oldPersonalityDataAndMalformedWeightsHaveSafeFallbacks() {
        var legacy = new Gson().fromJson("{\"id\":\"legacy\"}", TownsfolkPersonalityDefinition.class);
        assertTrue(legacy.getThoughtItemWeights().isEmpty());
        assertTrue(legacy.getIdleEmoteWeights().isEmpty());
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("bad", Double.NaN); weights.put("zero", 0.0); weights.put("negative", -1.0); weights.put("null", null);
        assertEquals("Home", VillagerLifePersonality.choose(weights, .5, "Home"));
        weights.put("Good", 1.0);
        for (double sample : new double[]{0, .5, 1}) assertEquals("Good", VillagerLifePersonality.choose(weights, sample, "Home"));
    }

    @Test void existingVoicePreferencesSelectStableHumanProfiles() {
        UUID id = new UUID(10, 20);
        assertEquals("GravelyFemale", VillagerLifePersonality.voiceFor("low", "female", id));
        assertEquals("BrightMale", VillagerLifePersonality.voiceFor("sharp", "male", id));
        assertEquals("MellowFemale", VillagerLifePersonality.voiceFor("soft", "female", id));
        assertEquals("WarmMale", VillagerLifePersonality.voiceFor("mid", "male", id));
        assertEquals("BrightFemale", VillagerLifePersonality.voiceFor("Bright Female", "male", id));
        assertEquals(VillagerLifePersonality.voiceFor(null, id), VillagerLifePersonality.voiceFor("unknown", id));
    }

    @Test void everyAuthoredInterestAndGestureHasShippedAssets() throws Exception {
        var model = JsonParser.parseString(Files.readString(RES.resolve("Server/Models/Human/Aetherhaven_Human.json"))).getAsJsonObject();
        try (var files = Files.list(RES.resolve("Server/Aetherhaven/Personalities"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                var trait = new Gson().fromJson(Files.readString(file), TownsfolkPersonalityDefinition.class);
                assertFalse(trait.getThoughtItemWeights().isEmpty(), file.toString());
                for (String item : trait.getThoughtItemWeights().keySet()) {
                    assertTrue(item.equals("Music") || VillagerLifePersonality.ITEMS.contains(item), item);
                }
                for (var gestures : List.of(trait.getIdleEmoteWeights(), trait.getSocialEmoteWeights())) {
                    for (String name : gestures.keySet()) assertTrue(model.getAsJsonObject("AnimationSets").has("Aetherhaven_Life_" + name), name);
                }
            }
        }
        for (String item : VillagerLifePersonality.ITEMS) {
            assertTrue(Files.isRegularFile(RES.resolve("Common/Particles/Aetherhaven/Life/Items/Centered/" + item + ".png")), item);
            for (String bubble : List.of("Speech", "Thought")) {
                assertTrue(Files.isRegularFile(RES.resolve("Server/Particles/Aetherhaven/Life/Aetherhaven_Life_" + bubble + "_Item_" + item + ".particlesystem")), item);
            }
        }
    }

    @Test void expiredEmoteKeepsItsHoldUntilDeferredCleanupRuns() {
        var state = new VillagerLifeState();
        assertFalse(state.ownsActivity(1000));
        state.emoteUntilMs = 500;
        assertTrue(state.ownsActivity(1000));
        state.emoteUntilMs = 0;
        assertFalse(state.ownsActivity(1000));
    }
}
