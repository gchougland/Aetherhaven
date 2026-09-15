package com.hexvane.aetherhaven.autonomy;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Tag("autonomy")
class VillagerVoiceProfilesTest {
    private static final Path RES = Path.of("src/main/resources");

    @Test void everyCharacterHasAnExplicitValidProfileMatchingTheirAuthoredGender() throws Exception {
        int count = 0;
        for (String directory : new String[]{"Villagers", "Townsfolk"}) {
            try (var files = Files.list(RES.resolve("Server/Aetherhaven/" + directory))) {
                for (var path : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                    var data = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                    String assigned = data.get("speechVoiceId").getAsString();
                    String gender = data.has("gender") ? data.get("gender").getAsString() : null;
                    String race = data.has("race") ? data.get("race").getAsString() : null;
                    var profile = VillagerVoiceProfile.resolve(assigned, gender, race);
                    assertEquals(assigned, profile.id(), path.toString());
                    if (gender != null) assertTrue(profile.recording().endsWith(gender.equals("female") ? "Female" : "Male"), path.toString());
                    assertNotNull(VillagerLifeSpeech.select(assigned, "Talk", 0), path.toString());
                    for (int i = 0; i < 8; i++) assertEquals(assigned, VillagerLifePersonality.voiceFor(assigned, gender, new UUID(i, i*317)));
                    count++;
                }
            }
        }
        assertEquals(80, count);
        assertEquals("GravelyFemaleHigher", VillagerVoiceProfile.resolve("BrightFemaleHigher", "female", "goblin").id());
        assertEquals("GravelyMale", VillagerVoiceProfile.resolve(null, "male", "goblin").id());
    }

    @Test void requestedNamedVoicesArePreserved() throws Exception {
        for (var entry : java.util.Map.of("Logger","WarmFemale","Rancher","MellowMale","Crystal_Keeper","MellowFemale",
            "Clown","GravelyMale","Miner","GravelyMaleLower","Pyrotechnic","GravelyMaleHigher","Blacksmith","WarmMaleLower").entrySet()) {
            var data = JsonParser.parseString(Files.readString(RES.resolve("Server/Aetherhaven/Villagers/Aetherhaven_" + entry.getKey() + ".json"))).getAsJsonObject();
            assertEquals(entry.getValue(), data.get("speechVoiceId").getAsString());
        }
    }

    @Test void legacyAndMissingProfilesDoNotDependOnWorldEntityIds() {
        for (String legacy : new String[]{"soft","mid","low","high","sharp","unknown"}) {
            String female = VillagerLifePersonality.voiceFor(legacy, "female", new UUID(1,2));
            assertEquals(female, VillagerLifePersonality.voiceFor(legacy, "female", new UUID(900,473)));
            assertTrue(female.endsWith("Female"));
        }
        assertEquals("WarmMale", VillagerLifePersonality.voiceFor(null, new UUID(0,0)));
        assertEquals("WarmMale", VillagerLifePersonality.voiceFor(null, new UUID(314,227)));
        assertEquals("MellowFemaleHigher", VillagerVoiceProfile.resolve("mellow female higher", null, null).id());
    }

    @Test void consecutiveDialogueSelectionsNeverRepeatTheSameRecording() {
        for (String profile : VillagerLifePolicy.VOICES) for (String suffix : new String[]{"","Lower","Higher"}) {
            for (String mood : new String[]{"Talk","Question","Laugh","Gasp","Grumble","Groan","Yawn","Sigh","Idle","Work"}) {
                String previous = null;
                for (int choice = -20; choice < 20; choice++) {
                    var clip = VillagerLifeSpeech.selectExcept(profile + suffix, mood, choice, previous);
                    assertNotNull(clip);
                    assertNotEquals(previous, clip.clip());
                    previous = clip.clip();
                }
            }
        }
        var thinking = VillagerLifeSpeech.select("MellowMale", "Thinking", 0);
        assertNull(VillagerLifeSpeech.selectExcept("MellowMale", "Thinking", 0, thinking.clip()));
    }

    @Test void pitchedVariantsReuseAudioAndKeepFaceBodyAndTimingInStep() throws Exception {
        var model = JsonParser.parseString(Files.readString(RES.resolve("Server/Models/Human/Aetherhaven_Human.json"))).getAsJsonObject();
        for (String profile : VillagerLifePolicy.VOICES) for (String variant : new String[]{"Lower","Higher"}) {
            var base = VillagerLifeSpeech.select(profile, "Talk", 0);
            var shifted = VillagerLifeSpeech.select(profile + variant, "Talk", 0);
            assertEquals(base.clip(), shifted.clip());
            assertEquals(Math.ceil(base.audioMs()/shifted.pitch()), shifted.audioMs());
            var table = JsonParser.parseString(Files.readString(RES.resolve("Server/Item/Animations/" + shifted.actionsId() + ".json"))).getAsJsonObject().getAsJsonObject("Animations");
            for (var entry : shifted.faces().entrySet()) {
                var face = entry.getValue();
                var binding = model.getAsJsonObject("AnimationSets").getAsJsonObject(face.id()).getAsJsonArray("Animations").get(0).getAsJsonObject();
                var action = table.getAsJsonObject(face.actionId());
                assertEquals(shifted.pitch(), binding.get("Speed").getAsFloat(), .000001);
                assertEquals(shifted.pitch(), action.get("Speed").getAsFloat(), .000001);
                assertEquals(binding.get("Animation").getAsString(), action.get("ThirdPersonFace").getAsString());
                assertEquals(Math.ceil(base.faces().get(entry.getKey()).durationMs()/shifted.pitch()), face.durationMs());
            }
        }
    }
}
