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
        for (String folder : new String[]{"Sounds/Aetherhaven/Life", "Particles/Aetherhaven/Life", "Particles/Aetherhaven/Emotions", "Items/Animations/Aetherhaven/Life", "Characters/Animations/Aetherhaven/Life", "Characters/Animations/Aetherhaven/ProwlFaces", "Characters/Animations/Aetherhaven/CreatureFaces"}) {
            if (!Files.isDirectory(RES.resolve("Common").resolve(folder))) continue;
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
        try (var files = Files.list(RES.resolve("Server/Particles/Aetherhaven/Emotions"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".particlespawner")).toList()) {
                SPAWNERS.add(file.getFileName().toString().replace(".particlespawner", ""), decode(ParticleSpawner.CODEC, Files.readString(file), file));
            }
        }
        try (var files = Files.list(RES.resolve("Server/Particles/Aetherhaven/Life"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".particlespawner")).toList()) {
                var decoded = decode(ParticleSpawner.CODEC, Files.readString(file), file);
                SPAWNERS.add(file.getFileName().toString().replace(".particlespawner", ""), decoded);
            }
        }
    }

    private static <T> T decode(com.hypixel.hytale.codec.Codec<T> codec, String json, Path file) {
        if (codec == com.hypixel.hytale.server.core.asset.type.itemanimation.config.ItemPlayerAnimations.CODEC) {
            try { json = LifeAssetJson.read(file).toString(); } catch (Exception e) { throw new RuntimeException(e); }
        }
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

    @Test void newEmotionEffectsMatchTheWorkingLovedGiftHeartsExceptTextureAndTint() throws Exception {
        for (String name : new String[]{"Surprise", "Confusion", "Question", "Shock", "Gloom"}) {
            Path folder = RES.resolve("Server/Particles/Aetherhaven/Emotions");
            Path system = folder.resolve("Aetherhaven_Emotion_" + name + ".particlesystem");
            decode(ParticleSystem.CODEC, Files.readString(system), system);
            var spawn = JsonParser.parseString(Files.readString(folder.resolve("Aetherhaven_Emotion_" + name + ".particlespawner"))).getAsJsonObject();
            Path nativeDir = Path.of("../HytaleSourceCode/hytale-shared-source/HytaleAssets/Server/Particles/NPC/Emotions");
            var expected = JsonParser.parseString(Files.readString(nativeDir.resolve("Spawners/Hearts.particlespawner"))).getAsJsonObject();
            expected.getAsJsonObject("Particle").addProperty("Texture", "Particles/Aetherhaven/Emotions/" + name + ".png");
            var frames = expected.getAsJsonObject("Particle");
            frames.getAsJsonObject("InitialAnimationFrame").addProperty("Color", "#ffffff");
            for (var frame : frames.getAsJsonObject("Animation").entrySet()) {
                var obj = frame.getValue().getAsJsonObject();
                if (obj.has("Color")) obj.addProperty("Color", "#ffffff");
            }
            assertEquals(expected, spawn, "Preserve the known-working native rendering configuration");
            var expectedSystem = JsonParser.parseString(Files.readString(nativeDir.resolve("Hearts.particlesystem"))).getAsJsonObject();
            expectedSystem.getAsJsonArray("Spawners").get(0).getAsJsonObject().addProperty("SpawnerId", "Aetherhaven_Emotion_" + name);
            assertEquals(expectedSystem, JsonParser.parseString(Files.readString(system)));
            var image = javax.imageio.ImageIO.read(RES.resolve("Common/" + spawn.getAsJsonObject("Particle").get("Texture").getAsString()).toFile());
            assertEquals(64, image.getWidth());
            assertEquals(64, image.getHeight());
            assertEquals(0, image.getRGB(0, 0) >>> 24);
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
            if (entry.getKey().startsWith("Aetherhaven_Life_Mouth_")) continue;
            count++;
            var binding = entry.getValue().getAsJsonObject().getAsJsonArray("Animations").get(0).getAsJsonObject();
            assertEquals((entry.getKey().endsWith("_Mix") || entry.getKey().endsWith("_Sweep")), binding.get("Looping").getAsBoolean());
            var anim = JsonParser.parseString(Files.readString(RES.resolve("Common").resolve(binding.get("Animation").getAsString()))).getAsJsonObject();
            assertEquals((entry.getKey().endsWith("_Mix") || entry.getKey().endsWith("_Sweep")), anim.get("holdLastKeyframe").getAsBoolean());
            for (var node : anim.getAsJsonObject("nodeAnimations").entrySet()) {
                var frames = node.getValue().getAsJsonObject().getAsJsonArray("orientation");
                var last = frames.get(frames.size()-1).getAsJsonObject();
                assertEquals(anim.get("duration").getAsInt(), last.get("time").getAsInt());
                if ((entry.getKey().endsWith("_Mix") || entry.getKey().endsWith("_Sweep"))) assertEquals(frames.get(0).getAsJsonObject().get("delta"), last.get("delta"));
                else assertEquals(1, last.getAsJsonObject("delta").get("w").getAsDouble());
                for (var frame : frames) {
                    var q = frame.getAsJsonObject().getAsJsonObject("delta");
                    double norm = 0;
                    for (String axis : new String[]{"x", "y", "z", "w"}) norm += Math.pow(q.get(axis).getAsDouble(), 2);
                    assertEquals(1, norm, .00001);
                }
            }
        }
        assertEquals(21, count);
    }

    @Test void heldActivitiesUseOriginalItemsAndOnlyTheUniqueSpoonShips() throws Exception {
        assertEquals("Halloween_Broomstick", VillagerLifeProps.itemFor("Sweep", "merchant"));
        assertEquals("Tool_Hammer_Iron", VillagerLifeProps.itemFor("Craft", "builder"));
        assertEquals("Plant_Flower_Bushy_Blue", VillagerLifeProps.itemFor("Tend", "florist"));
        assertEquals("Weapon_Spellbook_Grimoire_Brown", VillagerLifeProps.itemFor("Read", "builder"));
        assertEquals("Food_Salad_Caesar", VillagerLifeProps.SALAD);
        assertEquals("Food_Salad_Caesar", VillagerLifeProps.itemFor("Mix", "chef"));
        assertNull(VillagerLifeProps.itemFor("Laugh", "chef"));
        try (var files = Files.list(RES.resolve("Server/Item/Items/Aetherhaven/Life"))) {
            assertEquals(java.util.List.of("Aetherhaven_Life_Prop_Spoon.json"),
                files.map(p -> p.getFileName().toString()).sorted().toList());
        }
        var read = JsonParser.parseString(Files.readString(RES.resolve("Common/Characters/Animations/Aetherhaven/Life/ReadLoop.blockyanim")))
            .getAsJsonObject().getAsJsonObject("nodeAnimations");
        for (String hinge : new String[]{"Book-Top", "Book-Bot"}) {
            assertTrue(Math.abs(read.getAsJsonObject(hinge).getAsJsonArray("orientation").get(0)
                .getAsJsonObject().getAsJsonObject("delta").get("z").getAsDouble()) > .5,
                "The original book must stay open during reading");
        }
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
                        assertTrue(face.durationMs() >= Math.ceil(decoded.getDurationMillis()));
                        assertFalse(clip.mouthCues().isEmpty());
                        var mouth = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("nodeAnimations").getAsJsonObject("Mouth").getAsJsonArray("shapeUvOffset");
                        assertTrue(mouth.size() >= 3);
                        var last = mouth.get(mouth.size()-1).getAsJsonObject().getAsJsonObject("delta");
                        assertEquals(0, last.get("x").getAsInt());
                        assertEquals(0, last.get("y").getAsInt());
                    }
                }
            }
        }
        assertEquals(1111, animations);
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

    @Test void heldBookAnimatesItsOwnMeshAndOffhandSpoonUsesNativeUtilityRules() throws Exception {
        var book = JsonParser.parseString(Files.readString(RES.resolve("Server/Item/Items/Weapon/Spellbook/Weapon_Spellbook_Grimoire_Brown.json"))).getAsJsonObject();
        assertFalse(book.has("UsePlayerAnimations"), "Do not replace the item's intrinsic animation with NPC character tracks");
        var animationPath = book.get("Animation").getAsString();
        ExtraInfo validation = new ExtraInfo();
        com.hypixel.hytale.server.core.asset.common.CommonAssetValidator.ANIMATION_ITEM_BLOCK
                .accept(animationPath, validation.getValidationResults());
        assertFalse(validation.getValidationResults().hasFailed(),
                "Item.Animation must pass the engine's path and existence validator: " + validation.getValidationResults().getResults());
        ExtraInfo oldPathValidation = new ExtraInfo();
        assertThrows(com.hypixel.hytale.codec.exception.CodecValidationException.class,
                () -> com.hypixel.hytale.server.core.asset.common.CommonAssetValidator.ANIMATION_ITEM_BLOCK
                        .accept("Characters/Animations/Aetherhaven/Life/Held/ReadLoop.blockyanim", oldPathValidation.getValidationResults()),
                "The old character-animation path must be rejected");
        var intrinsic = JsonParser.parseString(Files.readString(RES.resolve("Common/" + book.get("Animation").getAsString()))).getAsJsonObject();
        decode(com.hypixel.hytale.server.core.asset.common.BlockyAnimationCache.BlockyAnimation.CODEC,
                intrinsic.toString(), RES.resolve("Common/" + animationPath));
        assertEquals(java.util.Set.of("Book-Top", "Book-Bot"), intrinsic.getAsJsonObject("nodeAnimations").keySet());
        for (var node : intrinsic.getAsJsonObject("nodeAnimations").entrySet()) {
            var frames = node.getValue().getAsJsonObject().getAsJsonArray("orientation");
            var open = frames.get(0).getAsJsonObject().getAsJsonObject("delta");
            assertTrue(Math.abs(open.get("z").getAsDouble()) > .5, "The intrinsic book pose must be open immediately");
            for (var frame : frames) for (String axis : new String[]{"x", "y", "z", "w"})
                assertEquals(open.get(axis).getAsDouble(),
                        frame.getAsJsonObject().getAsJsonObject("delta").get(axis).getAsDouble(), 1e-7);
        }
        var nativePath = Path.of("../HytaleSourceCode/hytale-shared-source/HytaleAssets/Server/Item/Items/Weapon/Spellbook/Weapon_Spellbook_Grimoire_Brown.json");
        var original = JsonParser.parseString(Files.readString(nativePath)).getAsJsonObject();
        book.remove("Animation");
        assertEquals(original, book, "The original book's grip, appearance and gameplay must stay unchanged");
        var spoon = BsonDocument.parse(Files.readString(RES.resolve("Server/Item/Items/Aetherhaven/Life/Aetherhaven_Life_Prop_Spoon.json")));
        var utility = com.hypixel.hytale.server.core.asset.type.item.config.ItemUtility.CODEC.decode(spoon.getDocument("Utility"), new ExtraInfo());
        assertTrue(utility.isUsable());
        var mesh = JsonParser.parseString(Files.readString(RES.resolve("Common/Items/Aetherhaven/Life/Spoon.blockymodel"))).getAsJsonObject();
        assertEquals("L-Attachment", mesh.getAsJsonArray("nodes").get(0).getAsJsonObject().get("name").getAsString());
    }

    @Test void prowlMouthIsAnAttachablePieceUsingTheSharedAtlas() throws Exception {
        var model = JsonParser.parseString(Files.readString(RES.resolve("Server/Models/Townsfolk/Prowl.json"))).getAsJsonObject();
        var attachment = model.getAsJsonArray("DefaultAttachments").get(0).getAsJsonObject();
        assertEquals("Skin", attachment.get("GradientSet").getAsString());
        assertEquals("09", attachment.get("GradientId").getAsString());
        var mesh = JsonParser.parseString(Files.readString(RES.resolve("Common/NPC/Prowl/Player_Mouth.blockymodel"))).getAsJsonObject();
        var root = mesh.getAsJsonArray("nodes").get(0).getAsJsonObject();
        assertEquals("Mouth-Attachment", root.get("name").getAsString());
        assertTrue(root.getAsJsonObject("shape").getAsJsonObject("settings").get("isPiece").getAsBoolean());
        var mouth = root.getAsJsonArray("children").get(0).getAsJsonObject();
        assertEquals("Mouth", mouth.get("name").getAsString());
        var size = mouth.getAsJsonObject("shape").getAsJsonObject("settings").getAsJsonObject("size");
        assertEquals(20, size.get("x").getAsInt());
        assertEquals(10, size.get("y").getAsInt());
    }

    @Test void bubblesFollowTheEntityAndAreClearedWhenItDespawns() {
        var particle = VillagerLifeVisuals.bubbleParticle("Aetherhaven_Life_Thought_Food", 2.9f);
        assertEquals(com.hypixel.hytale.protocol.EntityPart.Entity, particle.targetEntityPart);
        assertFalse(particle.detachedFromModel);
        assertTrue(particle.clearParticlesOnRemove);
        assertNull(particle.targetNodeName, "Head tilts must not rotate the billboard offset");
        assertEquals(new org.joml.Vector3f(0, 2.9f, 0), particle.positionOffset);
    }

    @Test void readingSuppliesTheNativeHeldItemChannelForBothSilentAndVoicedActions() throws Exception {
        var table = LifeAssetJson.read(RES.resolve("Server/Item/Animations/Aetherhaven_Life_Actions.json")).getAsJsonObject("Animations");
        for (String gesture : new String[]{"Read", "ReadLoop", "Read_Speech", "ReadLoop_Speech"}) {
            var action = table.getAsJsonObject(gesture);
            assertTrue(action.has("FirstPerson"), "The held item's hinges need their own animation channel");
            Path heldFile = RES.resolve("Common/" + action.get("FirstPerson").getAsString());
            var held = JsonParser.parseString(Files.readString(heldFile)).getAsJsonObject();
            var body = LifeAssetJson.read(RES.resolve("Common/" + action.get("ThirdPerson").getAsString()));
            decode(com.hypixel.hytale.server.core.asset.common.BlockyAnimationCache.BlockyAnimation.CODEC, held.toString(), heldFile);
            assertEquals(body.get("duration"), held.get("duration"));
            assertEquals(java.util.Set.of("Book-Top", "Book-Bot"), held.getAsJsonObject("nodeAnimations").keySet());
            for (String hinge : new String[]{"Book-Top", "Book-Bot"})
                assertEquals(body.getAsJsonObject("nodeAnimations").get(hinge), held.getAsJsonObject("nodeAnimations").get(hinge));
        }
    }

    @Test void everyPlayerRigResidentInheritsTheSharedMouthBindingsAndPosesDoNotAnimateBrows() throws Exception {
        for (String folder : new String[]{"Villager", "Townsfolk"}) {
            try (var paths = Files.list(RES.resolve("Server/Models/" + folder))) {
                for (Path path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                    var model = LifeAssetJson.read(path);
                    assertNotEquals("Player", model.has("Parent") ? model.get("Parent").getAsString() : "", path.toString());
                }
            }
        }
        var sets = LifeAssetJson.read(RES.resolve("Server/Models/Human/Aetherhaven_Human.json")).getAsJsonObject("AnimationSets");
        for (String shape : new String[]{"A", "B", "C", "D", "E", "F", "Smile_B", "Smile_C", "Smile_D"}) {
            var binding = sets.getAsJsonObject("Aetherhaven_Life_Mouth_"+shape).getAsJsonArray("Animations").get(0).getAsJsonObject();
            var pose = LifeAssetJson.read(RES.resolve("Common/"+binding.get("Animation").getAsString()));
            assertEquals(java.util.Set.of("Mouth"), pose.getAsJsonObject("nodeAnimations").keySet());
            assertTrue(pose.get("duration").getAsInt()/60.0 > binding.get("BlendingDuration").getAsDouble());
            for (var track : pose.getAsJsonObject("nodeAnimations").getAsJsonObject("Mouth").entrySet()) {
                var keys = track.getValue().getAsJsonArray();
                if (!keys.isEmpty()) assertEquals(keys.get(0).getAsJsonObject().get("delta"), keys.get(keys.size()-1).getAsJsonObject().get("delta"));
            }
        }
    }

    @Test void expressiveFaceTracksReturnToNeutralAndUseEngineFrameRate() throws Exception {
        Path dir = RES.resolve("Common/Characters/Animations/Aetherhaven/Life/Faces");
        try (var files = Files.list(dir)) {
            var all = files.toList();
            assertEquals(32, all.size());
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
            assertTrue(animations.size() < 150, "Voice aliases must reuse identical body timelines");
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
        assertEquals(46, asset.getAnimations().size());
        for (var entry : asset.getAnimations().entrySet()) {
            var action = entry.getValue();
            assertTrue(Files.isRegularFile(RES.resolve("Common").resolve(action.thirdPerson)), entry.getKey());
            assertEquals(action.thirdPerson, action.thirdPersonMoving);
            assertNotNull(action.thirdPersonFace, "Action-priority faces must accompany body playback");
            var face = JsonParser.parseString(Files.readString(RES.resolve("Common").resolve(action.thirdPersonFace))).getAsJsonObject();
            var body = JsonParser.parseString(Files.readString(RES.resolve("Common").resolve(action.thirdPerson))).getAsJsonObject();
            assertEquals(body.get("duration").getAsInt(), face.get("duration").getAsInt());
            assertEquals((entry.getKey().replace("_Speech", "").equals("ReadLoop") || entry.getKey().replace("_Speech", "").equals("Mix") || entry.getKey().replace("_Speech", "").equals("Sweep")), action.looping);
            assertEquals(1f, action.speed);
            if (entry.getKey().endsWith("_Speech")) assertFalse(face.getAsJsonObject("nodeAnimations").has("Mouth"), "Only the Face slot may drive speech mouth shapes");
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
                assertEquals(64, icon.getWidth());
                assertEquals(64, icon.getHeight());
                int minX=64,minY=64,maxX=-1,maxY=-1;
                for (int y=0;y<64;y++) for (int x=0;x<64;x++) {
                    if ((icon.getRGB(x,y) >>> 24) < 16) continue;
                    minX=Math.min(minX,x);maxX=Math.max(maxX,x);
                    minY=Math.min(minY,y);maxY=Math.max(maxY,y);
                }
                assertEquals(32, (minX+maxX+1)/2.0, 1.5, texture);
                assertEquals(32, (minY+maxY+1)/2.0, 1.5, texture);
                assertTrue(maxX-minX+1<=52 && maxY-minY+1<=48, texture);
            }
        }
    }

    @Test void activitiesPreserveNativeGripsExceptTheApprovedSweepGrip() throws Exception {
        for (String gesture : new String[]{"Read", "Sweep", "Craft", "Mix", "Tend"}) {
            var anim = JsonParser.parseString(Files.readString(RES.resolve("Common/Characters/Animations/Aetherhaven/Life/"
                + gesture + ".blockyanim"))).getAsJsonObject();
            var attachment = anim.getAsJsonObject("nodeAnimations").getAsJsonObject("R-Attachment");
            assertNotNull(attachment);
            var frames = attachment.getAsJsonArray("position");
            assertEquals(0, frames.get(0).getAsJsonObject().get("time").getAsInt());
            assertEquals(anim.get("duration").getAsInt(), frames.get(frames.size()-1).getAsJsonObject().get("time").getAsInt());
            double[] expected = gesture.equals("Sweep") ? new double[]{0, 0, -4}
                : gesture.equals("Craft") ? new double[]{0, 0, 0} : new double[]{2, -2, 2.1};
            for (var frame : frames) for (int axis = 0; axis < 3; axis++)
                assertEquals(expected[axis], frame.getAsJsonObject().getAsJsonObject("delta")
                    .get(new String[]{"x", "y", "z"}[axis]).getAsDouble(), .00001);
        }
    }

    @Test void thoughtTailTipUsesTheBillboardPivot() throws Exception {
        var icon = javax.imageio.ImageIO.read(RES.resolve("Common/Particles/Aetherhaven/Life/Thought.png").toFile());
        assertEquals(128, icon.getWidth());
        assertEquals(128, icon.getHeight());
        assertTrue((icon.getRGB(64, 122) >>> 24) > 128);
        assertTrue((icon.getRGB(92, 122) >>> 24) < 16);
        assertTrue((icon.getRGB(64, 64) >>> 24) > 128);
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
                assertTrue(Files.isRegularFile(RES.resolve("Common/" + action.thirdPersonFace)));
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
                assertEquals(46, table.getAnimations().size());
            }
        }
    }

    @Test void continuousReadingHasAClosedSeamAndVoicedBeatsKeepTheBookRaised() throws Exception {
        Path base = RES.resolve("Common/Characters/Animations/Aetherhaven/Life");
        var loop = JsonParser.parseString(Files.readString(base.resolve("ReadLoop.blockyanim"))).getAsJsonObject();
        assertEquals("Weapon_Spellbook_Grimoire_Brown", VillagerLifeProps.itemFor("ReadLoop", "builder"));
        for (var node : loop.getAsJsonObject("nodeAnimations").entrySet()) {
            for (var channel : node.getValue().getAsJsonObject().entrySet()) {
                var keys = channel.getValue().getAsJsonArray();
                if (keys.isEmpty()) continue;
                assertEquals(keys.get(0).getAsJsonObject().get("delta"), keys.get(keys.size()-1).getAsJsonObject().get("delta"), node.getKey());
            }
        }
        var clip = VillagerLifeSpeech.select("BrightFemale", "Thinking", 0);
        var action = clip.faces().get("ReadLoop");
        var table = JsonParser.parseString(Files.readString(RES.resolve("Server/Item/Animations/Aetherhaven_Life_Actions.json")))
            .getAsJsonObject().getAsJsonObject("Animations");
        var bodyPath = table.getAsJsonObject(action.actionId()).get("ThirdPerson").getAsString();
        var voiced = JsonParser.parseString(Files.readString(RES.resolve("Common").resolve(bodyPath))).getAsJsonObject();
        assertTrue(voiced.get("holdLastKeyframe").getAsBoolean());
        assertEquals(loop.getAsJsonObject("nodeAnimations"), voiced.getAsJsonObject("nodeAnimations"));
    }
}
