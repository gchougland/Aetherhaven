package com.hexvane.aetherhaven.autonomy;

import static org.junit.jupiter.api.Assertions.*;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSpawner;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bson.BsonDocument;
import org.junit.jupiter.api.Test;

/** Decode server assets and check the client animation schema separately. */
@org.junit.jupiter.api.Tag("autonomy")
class VillagerLifeAssetsTest {
    private static final Path RES = Path.of("src/main/resources");
    private static final String PACK = "aetherhaven-life-test";
    private static final java.util.List<String> COMMON_NAMES = new java.util.ArrayList<>();
    private static final SpawnerMap SPAWNERS = new SpawnerMap();
    private static SpawnerStore spawnerStore;

    // Real reference validation without starting a world or registering file monitors.
    private static class SpawnerMap extends com.hypixel.hytale.assetstore.map.DefaultAssetMap<String, ParticleSpawner> {
        void add(String name, ParticleSpawner asset) {
            putAll(PACK, ParticleSpawner.CODEC, java.util.Map.of(name, asset), java.util.Map.of(), java.util.Map.of());
        }
    }
    private static class SpawnerStore extends com.hypixel.hytale.assetstore.AssetStore<String, ParticleSpawner, com.hypixel.hytale.assetstore.map.DefaultAssetMap<String, ParticleSpawner>> {
        private final com.hypixel.hytale.event.EventBus events = new com.hypixel.hytale.event.EventBus(false);
        SpawnerStore(Config config) { super(config); }
        @Override protected com.hypixel.hytale.event.IEventBus getEventBus() { return events; }
        @Override public void addFileMonitor(String pack, Path path) {}
        @Override public void removeFileMonitor(Path path) {}
        @Override protected void handleRemoveOrUpdate(java.util.Set<String> removed, java.util.Map<String, ParticleSpawner> updated, com.hypixel.hytale.assetstore.AssetUpdateQuery query) {}
        private static class Config extends Builder<String, ParticleSpawner, com.hypixel.hytale.assetstore.map.DefaultAssetMap<String, ParticleSpawner>, Config> {
            Config() { super(String.class, ParticleSpawner.class, SPAWNERS); setPath("Particles"); setCodec(ParticleSpawner.CODEC); setKeyFunction(ParticleSpawner::getId); }
            @Override public SpawnerStore build() { return new SpawnerStore(this); }
        }
    }

    @org.junit.jupiter.api.BeforeAll
    static void registerRealCommonAssets() throws Exception {
        for (String folder : new String[]{"Sounds/Aetherhaven/Life", "Particles/Aetherhaven/Life", "Characters/Animations/Aetherhaven/Life", "Characters/Animations/Aetherhaven/ProwlFaces", "Characters/Animations/Aetherhaven/CreatureFaces"}) {
            try (var files = Files.walk(RES.resolve("Common").resolve(folder))) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    String name = RES.resolve("Common").relativize(file).toString().replace('\\', '/');
                    com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry.addCommonAsset(PACK,
                        new com.hypixel.hytale.server.core.asset.common.asset.FileCommonAsset(file, name, Files.readAllBytes(file)));
                    COMMON_NAMES.add(name);
                }
            }
        }
        spawnerStore = com.hypixel.hytale.assetstore.AssetRegistry.register(new SpawnerStore.Config().build());
        try (var files = Files.list(RES.resolve("Server/Particles/Aetherhaven/Life"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".particlespawner")).toList()) {
                var decoded = decode(ParticleSpawner.CODEC, Files.readString(file), file);
                SPAWNERS.add(file.getFileName().toString().replace(".particlespawner", ""), decoded);
            }
        }
    }

    private static <T> T decode(com.hypixel.hytale.codec.Codec<T> codec, String json, Path file) {
        ExtraInfo extra = new ExtraInfo();
        T decoded = codec.decode(BsonDocument.parse(json), extra);
        assertNotNull(decoded, file.toString());
        assertFalse(extra.getValidationResults().hasFailed(), file + ": " + extra.getValidationResults().getResults());
        return decoded;
    }

    @org.junit.jupiter.api.AfterAll
    static void removeTestCommonAssets() throws Exception {
        for (String name : COMMON_NAMES) com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry.removeCommonAssetByName(PACK, name);
        COMMON_NAMES.clear();
        if (spawnerStore != null) {
            com.hypixel.hytale.assetstore.AssetRegistry.unregister(spawnerStore);
            var cached = ParticleSpawner.class.getDeclaredField("ASSET_STORE");
            cached.setAccessible(true);
            cached.set(null, null);
        }
    }

    @Test void particleCodecsAcceptEveryLayerAndLayerReferencesExist() throws Exception {
        Path dir = RES.resolve("Server/Particles/Aetherhaven/Life");
        try (var files = Files.list(dir)) {
            for (Path file : files.toList()) {
                String json = Files.readString(file);
                var obj = JsonParser.parseString(json).getAsJsonObject();
                if (file.toString().endsWith(".particlespawner")) {
                    decode(ParticleSpawner.CODEC, json, file);
                    var particle = obj.getAsJsonObject("Particle");
                    assertTrue(Files.isRegularFile(RES.resolve("Common").resolve(particle.get("Texture").getAsString())));
                    // Opacity keyframes MULTIPLY initial opacity; zero would hide every bubble.
                    assertTrue(particle.getAsJsonObject("InitialAnimationFrame").get("Opacity").getAsFloat() > 0);
                    assertEquals(1, obj.getAsJsonObject("TotalParticles").get("Max").getAsInt());
                } else {
                    decode(ParticleSystem.CODEC, json, file);
                    assertEquals(2, obj.getAsJsonArray("Spawners").size());
                    for (var layer : obj.getAsJsonArray("Spawners")) {
                        assertTrue(Files.isRegularFile(dir.resolve(layer.getAsJsonObject().get("SpawnerId").getAsString() + ".particlespawner")));
                    }
                }
            }
        }
    }

    @Test void voiceEventsDecodeAndOnlyUseOriginalLocalOggFiles() throws Exception {
        Path dir = RES.resolve("Server/Audio/SoundEvents/Aetherhaven/Life");
        int clips = 0;
        try (var files = Files.list(dir)) {
            for (Path file : files.toList()) {
                String json = Files.readString(file);
                decode(SoundEvent.CODEC, json, file);
                for (var layer : JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("Layers")) {
                    for (var clip : layer.getAsJsonObject().getAsJsonArray("Files")) {
                        String name = clip.getAsString();
                        assertTrue(name.startsWith("Sounds/Aetherhaven/Life/"));
                        byte[] data = Files.readAllBytes(RES.resolve("Common").resolve(name));
                        assertEquals("OggS", new String(data, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
                        clips++;
                    }
                }
            }
        }
        assertEquals(308, clips);
    }

    @Test void originalAnimationsAreBoundAndAlwaysReturnToNeutral() throws Exception {
        var model = JsonParser.parseString(Files.readString(RES.resolve("Server/Models/Human/Aetherhaven_Human.json"))).getAsJsonObject();
        int count = 0;
        for (var entry : model.getAsJsonObject("AnimationSets").entrySet()) {
            if (!entry.getKey().startsWith("Aetherhaven_Life_")) continue;
            if (entry.getKey().startsWith("Aetherhaven_Life_Face_")) continue;
            if (entry.getKey().startsWith("Aetherhaven_Life_Lip_")) continue;
            count++;
            var binding = entry.getValue().getAsJsonObject().getAsJsonArray("Animations").get(0).getAsJsonObject();
            assertFalse(binding.get("Looping").getAsBoolean());
            var anim = JsonParser.parseString(Files.readString(RES.resolve("Common").resolve(binding.get("Animation").getAsString()))).getAsJsonObject();
            assertFalse(anim.get("holdLastKeyframe").getAsBoolean());
            for (var node : anim.getAsJsonObject("nodeAnimations").entrySet()) {
                var frames = node.getValue().getAsJsonObject().getAsJsonArray("orientation");
                var last = frames.get(frames.size()-1).getAsJsonObject();
                assertEquals(anim.get("duration").getAsInt(), last.get("time").getAsInt());
                assertEquals(1, last.getAsJsonObject("delta").get("w").getAsDouble());
                for (var frame : frames) {
                    var q = frame.getAsJsonObject().getAsJsonObject("delta");
                    double norm = 0;
                    for (String axis : new String[]{"x", "y", "z", "w"}) norm += Math.pow(q.get(axis).getAsDouble(), 2);
                    assertEquals(1, norm, .00001);
                }
            }
        }
        assertEquals(19, count);
    }

    @Test void heldPropsExistAndTheirAnimationControlsTheAttachment() throws Exception {
        for (String gesture : new String[]{"Read", "Sweep", "Craft", "Inspect", "Tend"}) {
            String id = VillagerLifeProps.itemFor(gesture, "builder");
            var item = JsonParser.parseString(Files.readString(RES.resolve("Server/Item/Items/Aetherhaven/Life/" + id + ".json"))).getAsJsonObject();
            var model = JsonParser.parseString(Files.readString(RES.resolve("Common/" + item.get("Model").getAsString()))).getAsJsonObject();
            var root = model.getAsJsonArray("nodes").get(0).getAsJsonObject();
            assertEquals("R-Attachment", root.get("name").getAsString());
            assertEquals("LifePropRoot", root.getAsJsonArray("children").get(0).getAsJsonObject().get("name").getAsString());
            var anim = JsonParser.parseString(Files.readString(RES.resolve("Common/Characters/Animations/Aetherhaven/Life/" + gesture + ".blockyanim"))).getAsJsonObject();
            var track = anim.getAsJsonObject("nodeAnimations").getAsJsonObject("LifePropRoot");
            assertTrue(track.getAsJsonArray("orientation").size() > 10);
            assertTrue(track.getAsJsonArray("position").size() > 10);
        }
        assertEquals("Aetherhaven_Life_Prop_Spoon", VillagerLifeProps.itemFor("Craft", "chef"));
        assertNull(VillagerLifeProps.itemFor("Laugh", "chef"));
    }

    @Test void exactAudioSelectionsHaveMatchingSynchronizedExpressions() throws Exception {
        var model = JsonParser.parseString(Files.readString(RES.resolve("Server/Models/Human/Aetherhaven_Human.json"))).getAsJsonObject();
        int animations = 0;
        for (String profile : VillagerLifePolicy.VOICES) {
            for (String mood : new String[]{"Talk", "Question", "Laugh", "Gasp", "Grumble", "Groan", "Yawn", "Sigh", "Stomach", "Idle", "Work", "Thinking"}) {
                int variants = switch (mood) { case "Talk" -> 3; case "Stomach" -> 1; case "Idle", "Work" -> 4; default -> 2; };
                for (int i = 0; i < variants; i++) {
                    var clip = VillagerLifeSpeech.select(profile, mood, i);
                    assertNotNull(clip);
                    assertEquals(profile + "_" + mood + "_" + (i+1), clip.clip());
                    var sound = JsonParser.parseString(Files.readString(RES.resolve("Server/Audio/SoundEvents/Aetherhaven/Life/Aetherhaven_Life_" + clip.clip() + ".json"))).getAsJsonObject();
                    var files = sound.getAsJsonArray("Layers").get(0).getAsJsonObject().getAsJsonArray("Files");
                    assertEquals(1, files.size());
                    assertTrue(files.get(0).getAsString().endsWith(clip.clip() + ".ogg"));
                    assertEquals(mood.equals("Stomach"), clip.faces().isEmpty());
                    for (var face : clip.faces().values()) {
                        animations++;
                        assertTrue(face.durationMs() >= clip.audioMs());
                        var binding = model.getAsJsonObject("AnimationSets").getAsJsonObject(face.id()).getAsJsonArray("Animations").get(0).getAsJsonObject();
                        Path file = RES.resolve("Common/" + binding.get("Animation").getAsString());
                        String json = Files.readString(file);
                        var decoded = decode(com.hypixel.hytale.server.core.asset.common.BlockyAnimationCache.BlockyAnimation.CODEC, json, file);
                        assertEquals(face.durationMs(), Math.ceil(decoded.getDurationMillis()));
                        var mouth = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("nodeAnimations").getAsJsonObject("Mouth").getAsJsonArray("shapeUvOffset");
                        assertTrue(mouth.size() >= 3);
                        var last = mouth.get(mouth.size()-1).getAsJsonObject().getAsJsonObject("delta");
                        assertEquals(0, last.get("x").getAsInt());
                        assertEquals(0, last.get("y").getAsInt());
                    }
                }
            }
        }
        assertEquals(946, animations);
    }

    @Test void readingEyesScanLinesAndLaughKeepsAnAlertUpperLid() throws Exception {
        Path faces = RES.resolve("Common/Characters/Animations/Aetherhaven/Life/Faces");
        var read = JsonParser.parseString(Files.readString(faces.resolve("Read.blockyanim"))).getAsJsonObject().getAsJsonObject("nodeAnimations");
        var left = read.getAsJsonObject("L-Eye").getAsJsonArray("position");
        assertEquals(left, read.getAsJsonObject("R-Eye").getAsJsonArray("position"));
        int reversals = 0; double previous = 0;
        for (var entry : left) {
            double x = entry.getAsJsonObject().getAsJsonObject("delta").get("x").getAsDouble();
            if (x * previous < 0) reversals++;
            previous = x;
        }
        assertTrue(reversals >= 5);
        var laugh = JsonParser.parseString(Files.readString(faces.resolve("Laugh.blockyanim"))).getAsJsonObject().getAsJsonObject("nodeAnimations");
        for (var frame : laugh.getAsJsonObject("L-Eyelid").getAsJsonArray("shapeStretch"))
            assertTrue(frame.getAsJsonObject().getAsJsonObject("delta").get("y").getAsDouble() <= 1);
    }

    @Test void expressiveFaceTracksReturnToNeutralAndUseEngineFrameRate() throws Exception {
        Path dir = RES.resolve("Common/Characters/Animations/Aetherhaven/Life/Faces");
        try (var files = Files.list(dir)) {
            var all = files.toList();
            assertEquals(29, all.size());
            for (Path file : all) {
                String json = Files.readString(file);
                var anim = JsonParser.parseString(json).getAsJsonObject();
                String gesture = file.getFileName().toString().replace(".blockyanim", "");
                var engine = decode(com.hypixel.hytale.server.core.asset.common.BlockyAnimationCache.BlockyAnimation.CODEC, json, file);
                assertEquals(VillagerLifeTiming.durationMs(gesture), engine.getDurationMillis(), .01);
                var tracks = anim.getAsJsonObject("nodeAnimations");
                for (String bone : new String[]{"Mouth", "L-Eye", "R-Eye", "L-Eyebrow", "R-Eyebrow", "L-Eyelid", "R-Eyelid"}) assertTrue(tracks.has(bone), file + ": " + bone);
                for (var bone : tracks.entrySet()) for (var track : bone.getValue().getAsJsonObject().entrySet()) {
                    var frames = track.getValue().getAsJsonArray();
                    if (frames.isEmpty()) continue;
                    var last = frames.get(frames.size()-1).getAsJsonObject();
                    assertEquals(anim.get("duration").getAsInt(), last.get("time").getAsInt(), file.toString());
                    for (var axis : last.getAsJsonObject("delta").entrySet()) {
                        double neutral = track.getKey().equals("shapeStretch") || axis.getKey().equals("w") ? 1 : 0;
                        assertEquals(neutral, axis.getValue().getAsDouble(), .00001, bone.getKey()+"/"+track.getKey());
                    }
                }
            }
        }
        assertEquals(6000, VillagerLifeTiming.durationMs("Sleepy"));
        assertTrue(VillagerLifeTiming.durationMs("Surprise") < VillagerLifeTiming.durationMs("Read"));
    }

    @Test void everyAnimationIncludesUnusedChannelsRequiredByTheClient() throws Exception {
        try (var files = Files.walk(RES.resolve("Common/Characters/Animations/Aetherhaven/Life"))) {
            var animations = files.filter(p -> p.toString().endsWith(".blockyanim")).toList();
            assertEquals(1942, animations.size());
            for (Path file : animations) {
                var animation = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                for (var node : animation.getAsJsonObject("nodeAnimations").entrySet()) {
                    for (String channel : new String[]{"position", "orientation", "shapeStretch", "shapeVisible", "shapeUvOffset"}) {
                        var value = node.getValue().getAsJsonObject().get(channel);
                        assertNotNull(value, file + ": " + node.getKey() + "/" + channel);
                        assertTrue(value.isJsonArray(), file + ": " + node.getKey() + "/" + channel);
                    }
                }
            }
        }
    }

    @Test void bodyActionsBindFacesAtActionPriorityAndLeaveSeatedLegsUntouched() throws Exception {
        Path file = RES.resolve("Server/Item/Animations/" + VillagerLifeVisuals.BODY_ANIMATIONS + ".json");
        var asset = decode(com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations.CODEC,
            Files.readString(file), file);
        assertEquals(com.hypixel.hytale.protocol.AnimationSlot.Action, VillagerLifeVisuals.BODY_SLOT);
        assertEquals(967, asset.getAnimations().size());
        for (var entry : asset.getAnimations().entrySet()) {
            var action = entry.getValue();
            assertEquals("Characters/Animations/Aetherhaven/Life/" + (entry.getKey().contains("_") ? "Actions/" : "")
                + entry.getKey() + ".blockyanim", action.thirdPerson);
            assertEquals(action.thirdPerson, action.thirdPersonMoving);
            assertNotNull(action.thirdPersonFace, "Action-priority faces must accompany body playback");
            var face = JsonParser.parseString(Files.readString(RES.resolve("Common").resolve(action.thirdPersonFace))).getAsJsonObject();
            var body = JsonParser.parseString(Files.readString(RES.resolve("Common").resolve(action.thirdPerson))).getAsJsonObject();
            assertEquals(body.get("duration").getAsInt(), face.get("duration").getAsInt());
            assertEquals(entry.getKey().equals("ReadLoop"), action.looping);
            assertEquals(1f, action.speed);
            var tracks = JsonParser.parseString(Files.readString(RES.resolve("Common").resolve(action.thirdPerson)))
                .getAsJsonObject().getAsJsonObject("nodeAnimations");
            for (String untouched : new String[]{"Mouth", "L-Eye", "R-Eye", "Pelvis", "L-Thigh", "R-Thigh", "L-Calf", "R-Calf"})
                assertFalse(tracks.has(untouched), entry.getKey() + " overrides face or seated legs: " + untouched);
        }
    }

    @Test void bubbleIconsShareTheContentCenterAndScale() throws Exception {
        try (var files = Files.list(RES.resolve("Server/Particles/Aetherhaven/Life"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".particlespawner")).toList()) {
                var spawner = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                var particle = spawner.getAsJsonObject("Particle");
                assertEquals(.28, particle.getAsJsonObject("InitialAnimationFrame").getAsJsonObject("Scale")
                    .getAsJsonObject("X").get("Min").getAsDouble(), .00001);
                assertFalse(spawner.has("EmitOffset"));
                String texture = particle.get("Texture").getAsString();
                if (texture.endsWith("/Thought.png") || texture.endsWith("/Speech.png")) continue;
                var icon = javax.imageio.ImageIO.read(RES.resolve("Common").resolve(texture).toFile());
                assertEquals(256, icon.getWidth());
                assertEquals(256, icon.getHeight());
                int minX=256,minY=256,maxX=-1,maxY=-1;
                for (int y=0;y<256;y++) for (int x=0;x<256;x++) {
                    if ((icon.getRGB(x,y) >>> 24) < 16) continue;
                    minX=Math.min(minX,x);maxX=Math.max(maxX,x);
                    minY=Math.min(minY,y);maxY=Math.max(maxY,y);
                }
                assertEquals(157, (minX+maxX+1)/2.0, 1.5, texture);
                assertEquals(69, (minY+maxY+1)/2.0, 1.5, texture);
                assertTrue(maxX-minX+1<=52 && maxY-minY+1<=48, texture);
            }
        }
    }

    @Test void propActionsOverrideTheHeldItemIdleAttachmentOffset() throws Exception {
        for (String gesture : new String[]{"Read", "Sweep", "Craft", "Inspect", "Tend"}) {
            var anim = JsonParser.parseString(Files.readString(RES.resolve("Common/Characters/Animations/Aetherhaven/Life/"
                + gesture + ".blockyanim"))).getAsJsonObject();
            var attachment = anim.getAsJsonObject("nodeAnimations").getAsJsonObject("R-Attachment");
            assertNotNull(attachment);
            var frames = attachment.getAsJsonArray("position");
            assertEquals(0, frames.get(0).getAsJsonObject().get("time").getAsInt());
            assertEquals(anim.get("duration").getAsInt(), frames.get(frames.size()-1).getAsJsonObject().get("time").getAsInt());
            for (var frame : frames) for (String axis : new String[]{"x", "y", "z"})
                assertEquals(0, frame.getAsJsonObject().getAsJsonObject("delta").get(axis).getAsDouble());
        }
    }

    @Test void thoughtTailTipUsesTheBillboardPivot() throws Exception {
        var icon = javax.imageio.ImageIO.read(RES.resolve("Common/Particles/Aetherhaven/Life/Thought.png").toFile());
        int center = icon.getWidth()/2;
        assertTrue((icon.getRGB(center, center-2) >>> 24) > 128);
        // The last tail dot sits on the head's horizontal center, while the cloud is offset right.
        assertTrue((icon.getRGB(center+28, center-2) >>> 24) < 16);
        assertTrue((icon.getRGB(center+29, center-59) >>> 24) > 128);
    }

    @Test void prowlActionTablesDecodeAndRetainBodyTimingAndPitch() throws Exception {
        for (String variant : new String[]{"", "_Lower", "_Higher"}) {
            String name = VillagerLifeVisuals.BODY_ANIMATIONS + variant;
            Path original = RES.resolve("Server/Item/Animations/" + name + ".json");
            Path custom = RES.resolve("Server/Item/Animations/" + name + "_Prowl.json");
            var base = decode(com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations.CODEC, Files.readString(original), original);
            var prowl = decode(com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations.CODEC, Files.readString(custom), custom);
            assertEquals(base.getAnimations().keySet(), prowl.getAnimations().keySet());
            for (var entry : prowl.getAnimations().entrySet()) {
                var action = entry.getValue();
                var previous = base.getAnimations().get(entry.getKey());
                assertEquals(previous.thirdPerson, action.thirdPerson);
                assertEquals(previous.speed, action.speed);
                assertEquals(previous.looping, action.looping);
                assertTrue(action.thirdPersonFace.startsWith("Characters/Animations/Aetherhaven/ProwlFaces/"));
                var face = JsonParser.parseString(Files.readString(RES.resolve("Common/" + action.thirdPersonFace))).getAsJsonObject();
                var oldFace = JsonParser.parseString(Files.readString(RES.resolve("Common/" + previous.thirdPersonFace))).getAsJsonObject();
                assertEquals(oldFace.get("duration"), face.get("duration"));
            }
        }
    }

    @Test void nativeCreatureActionTablesDecodeWithRealClientAssetReferences() throws Exception {
        for (String rig : new String[]{"Trork", "Feran", "Klops", "Slothian", "Skeleton", "Kweebec", "KweebecSharp", "Outlander"}) {
            for (String pitch : new String[]{"", "_Lower", "_Higher"}) {
                Path path = RES.resolve("Server/Item/Animations/Aetherhaven_Life_Actions" + pitch + "_" + rig + ".json");
                var table = decode(com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations.CODEC, Files.readString(path), path);
                assertEquals(967, table.getAnimations().size());
            }
        }
    }

    @Test void continuousReadingHasAClosedSeamAndVoicedBeatsKeepTheBookRaised() throws Exception {
        Path base = RES.resolve("Common/Characters/Animations/Aetherhaven/Life");
        var loop = JsonParser.parseString(Files.readString(base.resolve("ReadLoop.blockyanim"))).getAsJsonObject();
        assertEquals("Aetherhaven_Life_Prop_OpenBook", VillagerLifeProps.itemFor("ReadLoop", "builder"));
        for (var node : loop.getAsJsonObject("nodeAnimations").entrySet()) {
            for (var channel : node.getValue().getAsJsonObject().entrySet()) {
                var keys = channel.getValue().getAsJsonArray();
                if (keys.isEmpty()) continue;
                assertEquals(keys.get(0).getAsJsonObject().get("delta"), keys.get(keys.size()-1).getAsJsonObject().get("delta"), node.getKey());
            }
        }
        var clip = VillagerLifeSpeech.select("BrightFemale", "Thinking", 0);
        var action = clip.faces().get("ReadLoop");
        var voiced = JsonParser.parseString(Files.readString(base.resolve("Actions/" + action.actionId() + ".blockyanim"))).getAsJsonObject();
        assertTrue(voiced.get("holdLastKeyframe").getAsBoolean());
        assertEquals(loop.getAsJsonObject("nodeAnimations"), voiced.getAsJsonObject("nodeAnimations"));
    }
}
