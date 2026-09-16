package com.hexvane.aetherhaven.autonomy;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.npc.NpcFaceVisuals;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Tag("autonomy")
class CreatureFaceAssetsTest {
    private static final Path RES = Path.of("src/main/resources");
    private static JsonObject json(Path path) throws Exception {
        return LifeAssetJson.read(path);
    }

    @Test void machinariaRobotRoutesBothDialogueAndActivityFacesAtEveryPitch() {
        String model = "NPC/Gear/Robot.blockymodel";
        assertTrue(NpcFaceVisuals.supportsFaceModel(model));
        for (String resident : new String[]{"Machinaria_Mechanic", "Copper_Pin", "Reginald_Volt"}) {
            for (String pitch : new String[]{"", "_Lower", "_Higher"}) {
                String source = "Aetherhaven_Life_Actions" + pitch;
                assertEquals(source + "_MachinariaRobot",
                    NpcFaceVisuals.itemAnimationsForModelAsset(resident, model, source));
            }
        }
        assertEquals("Machinaria_CustomActions",
            NpcFaceVisuals.itemAnimationsForModel(model, "Machinaria_CustomActions"));
        assertFalse(NpcFaceVisuals.supportsFaceModel("NPC/Gear/Clockwork_Golem.blockymodel"));
    }

    @Test void eachResidentUsesNativeFacesInBothAnimationSlotsAndAtEveryPitch() throws Exception {
        var rigs = json(RES.resolve("defaults/villager_creature_faces.json"));
        assertEquals(11, rigs.size());
        for (var entry : rigs.entrySet()) {
            var profile = entry.getValue().getAsJsonObject();
            String rig = profile.get("rig").getAsString();
            String model = profile.get("model").getAsString();
            assertTrue(NpcFaceVisuals.supportsFaceModel(model));
            var bindings = json(RES.resolve("Server/Models/Townsfolk/" + entry.getKey() + ".json")).getAsJsonObject("AnimationSets");
            for (String pitch : new String[]{"", "_Lower", "_Higher"}) {
                String original = "Aetherhaven_Life_Actions" + pitch;
                String selected = NpcFaceVisuals.itemAnimationsForModelAsset(entry.getKey(), model, original);
                assertEquals(original + "_" + rig, selected);
                var source = json(RES.resolve("Server/Item/Animations/" + original + ".json")).getAsJsonObject("Animations");
                var table = json(RES.resolve("Server/Item/Animations/" + selected + ".json")).getAsJsonObject("Animations");
                assertEquals(source.keySet(), table.keySet());
                for (var action : table.entrySet()) {
                    var actual = action.getValue().getAsJsonObject();
                    var previous = source.getAsJsonObject(action.getKey());
                    for (String key : new String[]{"ThirdPerson", "ThirdPersonMoving", "Looping", "Speed", "BlendingDuration"})
                        assertEquals(previous.get(key), actual.get(key), entry.getKey() + ":" + key);
                    assertTrue(Files.exists(RES.resolve("Common/" + actual.get("ThirdPersonFace").getAsString())));
                    if (rig.equals("Outlander"))
                        assertEquals(previous.get("ThirdPersonFace"), actual.get("ThirdPersonFace"),
                            "The player skeleton must reuse human expressions");
                }
                var face = bindings.getAsJsonObject("Aetherhaven_Life_Mouth_D")
                    .getAsJsonArray("Animations").get(0).getAsJsonObject();
                assertTrue(Files.isRegularFile(RES.resolve("Common/" + face.get("Animation").getAsString())));
                assertTrue(face.get("Looping").getAsBoolean());
            }
            for (String mood : new String[]{"Talk", "Talk2", "Talk3", "Talk4", "Talk5", "Frown", "Grin"})
                assertTrue(bindings.has(mood), entry.getKey() + ":" + mood);
        }
    }

    @Test void jawsActuallyArticulateAndCloseWithoutHumanMouthUvsOrBodyTracks() throws Exception {
        for (String rig : new String[]{"Trork", "Feran", "Klops", "Slothian", "Skeleton"}) {
            var table = json(RES.resolve("Server/Item/Animations/Aetherhaven_Life_Actions_" + rig + ".json")).getAsJsonObject("Animations");
            var paths = new java.util.HashSet<Path>();
            for (var action : table.entrySet())
                paths.add(RES.resolve("Common/" + action.getValue().getAsJsonObject().get("ThirdPersonFace").getAsString()));
            {
                for (var path : paths) {
                    var data = json(path);
                    var nodes = data.getAsJsonObject("nodeAnimations");
                    assertFalse(nodes.has("Mouth"), path.toString());
                    assertFalse(nodes.has("Head"), "Face must not override body head acting");
                    if (!nodes.has("Jaw")) continue; // Speech action owns eyes only; mouth is a separate slot.
                    for (var bone : nodes.entrySet()) {
                        for (String channel : new String[]{"position", "orientation", "shapeStretch", "shapeVisible", "shapeUvOffset"})
                            assertTrue(bone.getValue().getAsJsonObject().get(channel).isJsonArray(), path + ":" + channel);
                    }
                    var jaw = nodes.getAsJsonObject("Jaw").getAsJsonArray("orientation");
                    int previousTime = -1;
                    for (var element : jaw) {
                        var frame = element.getAsJsonObject();
                        int time = frame.get("time").getAsInt();
                        assertTrue(time > previousTime && time <= data.get("duration").getAsInt(), path.toString());
                        previousTime = time;
                        var q = frame.getAsJsonObject("delta");
                        double x = q.get("x").getAsDouble(), w = q.get("w").getAsDouble();
                        assertEquals(1, x*x+w*w, .000001);
                        assertTrue(x >= 0 && x <= Math.sin(Math.toRadians(13.01)), path.toString());
                    }
                    if (path.toString().contains("LipSync")) {
                        assertEquals(0, jaw.get(jaw.size()-1).getAsJsonObject().getAsJsonObject("delta").get("x").getAsDouble(), .00001);
                    }
                }
            }
            var profiles = json(RES.resolve("defaults/villager_creature_faces.json"));
            String resident = profiles.entrySet().stream().filter(e -> e.getValue().getAsJsonObject().get("rig").getAsString().equals(rig))
                .findFirst().orElseThrow().getKey();
            var bindings = json(RES.resolve("Server/Models/Townsfolk/" + resident + ".json")).getAsJsonObject("AnimationSets");
            var apertures = new java.util.HashSet<Double>();
            for (String shape : new String[]{"A", "B", "C", "D", "E", "F"}) {
                var binding = bindings.getAsJsonObject("Aetherhaven_Life_Mouth_" + shape).getAsJsonArray("Animations").get(0).getAsJsonObject();
                var pose = json(RES.resolve("Common/" + binding.get("Animation").getAsString()));
                var jaw = pose.getAsJsonObject("nodeAnimations").getAsJsonObject("Jaw").getAsJsonArray("orientation");
                double opening = jaw.get(0).getAsJsonObject().getAsJsonObject("delta").get("x").getAsDouble();
                apertures.add(opening);
                if (shape.equals("A")) assertEquals(0, opening, .000001);
            }
            assertTrue(apertures.size() >= 4, rig + " must have varied speech apertures");
            var spoken = json(RES.resolve("Common/" + table.getAsJsonObject("Explain_Speech")
                .get("ThirdPersonFace").getAsString())).getAsJsonObject("nodeAnimations");
            assertFalse(spoken.has("Jaw"), "Body expressions must leave speech jaw playback alone");
            if (rig.equals("Klops")) {
                assertTrue(spoken.has("Eye") && spoken.has("Eyelid-Top") && spoken.has("Eyebrow"));
                assertFalse(spoken.has("L-Eye") || spoken.has("R-Eye"));
            }
        }
    }

    @Test void allTownIdleBindingsAreSilentAndKlopsRetainsItsSlowIdle() throws Exception {
        for (String folder : new String[]{"Townsfolk", "Villager", "Human"}) {
            try (var files = Files.list(RES.resolve("Server/Models/" + folder))) {
                for (var path : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                    var model = json(path);
                    if (!model.has("AnimationSets")) continue;
                    for (var binding : model.getAsJsonObject("AnimationSets").entrySet()) {
                        if (!binding.getKey().toLowerCase(java.util.Locale.ROOT).contains("idle")) continue;
                        for (var animation : binding.getValue().getAsJsonObject().getAsJsonArray("Animations"))
                            assertFalse(animation.getAsJsonObject().has("SoundEventId"), path + ":" + binding.getKey());
                    }
                }
            }
        }
        for (String name : new String[]{"Nell_Clinkjar", "Pippin_Geargrin"}) {
            var idle = json(RES.resolve("Server/Models/Townsfolk/" + name + ".json"))
                .getAsJsonObject("AnimationSets").getAsJsonObject("Idle").getAsJsonArray("Animations").get(0).getAsJsonObject();
            assertEquals(.5, idle.get("Speed").getAsDouble());
            assertTrue(idle.get("Animation").getAsString().endsWith("Klops/Animations/Default/Idle.blockyanim"));
        }
    }
}
