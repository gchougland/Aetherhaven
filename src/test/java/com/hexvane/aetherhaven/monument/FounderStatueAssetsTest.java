package com.hexvane.aetherhaven.monument;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.AnimationSlot;
import com.hypixel.hytale.server.core.modules.entity.component.ActiveAnimationComponent;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The data a founder statue's screen, key prompt and poses depend on is present and well formed. */
@Tag("entity")
class FounderStatueAssetsTest {
    private static final List<String> CHANNELS =
        List.of("position", "orientation", "shapeStretch", "shapeVisible", "shapeUvOffset");

    private JsonObject json(String resource) throws IOException {
        try (InputStream stream = getClass().getResourceAsStream(resource)) {
            assertNotNull(stream, "Missing " + resource);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private Map<String, String> lang() throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        try (InputStream stream = getClass().getResourceAsStream("/Server/Languages/en-US/aetherhaven_monument.lang")) {
            assertNotNull(stream, "Missing the statue lang file");
            for (String line : new String(stream.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                int at = line.indexOf('=');
                if (line.isBlank() || line.startsWith("#") || at < 0) {
                    continue;
                }
                out.put("aetherhaven_monument." + line.substring(0, at).trim(), line.substring(at + 1).trim());
            }
        }
        return out;
    }

    @Test
    void poseIdsAreStableAndUnknownOnesReadAsStanding() {
        Set<String> ids = new HashSet<>();
        Set<String> sets = new HashSet<>();
        for (FounderStatuePose pose : FounderStatuePose.values()) {
            assertTrue(ids.add(pose.id()), "Duplicate pose id " + pose.id());
            assertSame(pose, FounderStatuePose.byId(pose.id()));
            assertSame(pose, FounderStatuePose.byId(pose.savedId()));
            if (pose.animationSetId() != null) {
                assertTrue(sets.add(pose.animationSetId()), "Two poses share " + pose.animationSetId());
            }
        }
        assertEquals("", FounderStatuePose.STANDING.savedId(), "Statues saved before poses existed must read as standing");
        assertSame(FounderStatuePose.STANDING, FounderStatuePose.byId(null));
        assertSame(FounderStatuePose.STANDING, FounderStatuePose.byId(""));
        assertSame(FounderStatuePose.STANDING, FounderStatuePose.byId("a_pose_removed_later"));
        assertEquals(6, FounderStatuePose.values().length, "The statue screen lays out six pose buttons");
    }

    @Test
    void everyPoseIsAStillClipBakedFromANativeAnimation() throws IOException {
        JsonObject model = json("/Server/Models/Aetherhaven/" + FounderStatuePose.POSES_MODEL_ID + ".json");
        assertEquals("Player", model.get("Parent").getAsString());
        JsonObject sets = model.getAsJsonObject("AnimationSets");
        for (FounderStatuePose pose : FounderStatuePose.values()) {
            if (pose.animationSetId() == null) {
                continue;
            }
            JsonObject set = sets.getAsJsonObject(pose.animationSetId());
            assertNotNull(set, "No animation set for pose " + pose.id());
            JsonArray animations = set.getAsJsonArray("Animations");
            assertEquals(1, animations.size(), "A pose is one clip, never a random pick");
            String path = animations.get(0).getAsJsonObject().get("Animation").getAsString();
            assertTrue(path.startsWith("Characters/Animations/Aetherhaven/Statue/"), path);

            JsonObject clip = json("/Common/" + path);
            double duration = clip.get("duration").getAsDouble();
            assertTrue(duration >= 1 && duration == Math.rint(duration), "The client needs a whole clip length");
            JsonObject nodes = clip.getAsJsonObject("nodeAnimations");
            assertTrue(nodes.size() > 0, "Pose " + pose.id() + " moves nothing");
            for (Map.Entry<String, JsonElement> node : nodes.entrySet()) {
                JsonObject channels = node.getValue().getAsJsonObject();
                for (String channel : CHANNELS) {
                    // The client throws on a node that leaves a channel out.
                    assertTrue(channels.has(channel), path + " " + node.getKey() + " lacks " + channel);
                    JsonArray keys = channels.getAsJsonArray(channel);
                    // One key at the start means the same value on every frame: the statue cannot move.
                    assertTrue(keys.size() <= 1, path + " " + node.getKey() + " " + channel + " is not still");
                    if (keys.size() == 1) {
                        assertEquals(0, keys.get(0).getAsJsonObject().get("time").getAsInt());
                    }
                }
            }
        }
    }

    @Test
    void posesKeepDistinctIdsWithoutInheritedMovementAnimations() {
        ModelAsset.AnimationSet proud = new ModelAsset.AnimationSet();
        Map<String, ModelAsset.AnimationSet> sets = new HashMap<>();
        sets.put(FounderStatuePose.PROUD.animationSetId(), proud);
        sets.put("Walk", new ModelAsset.AnimationSet());

        Map<String, ModelAsset.AnimationSet> posed =
            FounderMonumentSpawnService.poseAnimations(sets);
        assertEquals(Set.of("StatueProud"), posed.keySet(), "A statue has no walking, falling or other animations to slip into");
        assertSame(proud, posed.get("StatueProud"));

        assertTrue(FounderMonumentSpawnService.poseAnimations(null).isEmpty());
    }

    @Test
    void everyPoseTransitionUpdatesCurrentAndFutureViewersIncludingStandingAndRepeatClicks() throws IOException {
        Map<String, ModelAsset.AnimationSet> sets = new HashMap<>();
        JsonObject shipped = json("/Server/Models/Aetherhaven/" + FounderStatuePose.POSES_MODEL_ID + ".json")
            .getAsJsonObject("AnimationSets");
        for (FounderStatuePose pose : FounderStatuePose.values()) {
            if (pose.animationSetId() == null) continue;
            String id = pose.animationSetId();
            var definition = shipped.getAsJsonObject(id);
            String clip = definition.getAsJsonArray("Animations").get(0).getAsJsonObject().get("Animation").getAsString();
            var path = java.nio.file.Path.of("src/main/resources/Common").resolve(clip);
            String pack = "FounderPoseRegressionTest";
            var common = new com.hypixel.hytale.server.core.asset.common.asset.FileCommonAsset(
                path, clip, java.nio.file.Files.readAllBytes(path));
            ModelAsset.AnimationSet set;
            com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry.addCommonAsset(pack, common);
            try {
                set = ModelAsset.AnimationSet.CODEC.decode(
                    org.bson.BsonDocument.parse(definition.toString()), new com.hypixel.hytale.codec.ExtraInfo());
            } finally {
                com.hypixel.hytale.server.core.asset.common.CommonAssetRegistry.removeCommonAssetByName(pack, clip);
            }
            var packet = set.toPacket(id);
            assertEquals(id, packet.id);
            assertEquals(1, packet.animations.length);
            assertTrue(packet.animations[0].looping);
            assertEquals(0, packet.animations[0].blendingDuration);
            assertEquals(clip, packet.animations[0].name);
            sets.put(id, set);
        }
        var available = FounderMonumentSpawnService.poseAnimations(sets);
        var active = new ActiveAnimationComponent();
        for (FounderStatuePose before : FounderStatuePose.values()) {
            for (FounderStatuePose after : FounderStatuePose.values()) {
                var oldPacket = FounderMonumentSpawnService.applyPose(active, before, available);
                var update = FounderMonumentSpawnService.applyPose(active, after, available);
                assertEquals(before.animationSetId(), oldPacket.activeAnimations[AnimationSlot.Status.ordinal()],
                    "A subsequent click must not mutate an already queued update");
                assertEquals(after.animationSetId(), update.activeAnimations[AnimationSlot.Status.ordinal()]);
                assertEquals(after.animationSetId(), active.getActiveAnimations()[AnimationSlot.Status.ordinal()],
                    "New viewers must receive the same pose as existing viewers");
                for (AnimationSlot slot : AnimationSlot.VALUES) {
                    if (slot != AnimationSlot.Status) assertEquals(null, update.activeAnimations[slot.ordinal()]);
                }
            }
        }
        var missing = FounderMonumentSpawnService.applyPose(active, FounderStatuePose.SIT, Map.of());
        assertTrue(java.util.Arrays.stream(missing.activeAnimations).allMatch(java.util.Objects::isNull),
            "Missing clips clear the old pose and fall back to standing");
    }

    @Test
    void aStatueIsToldToStandStillAndOffersItsScreenOnUse() {
        var states = FounderMonumentSpawnService.standingStill().getMovementStates();
        assertTrue(states.idle && states.horizontalIdle && states.onGround);
        assertFalse(states.walking || states.falling || states.sitting);

        var interactions = FounderMonumentSpawnService.statueInteractions();
        assertEquals(
            FounderMonumentSpawnService.STATUE_USE_ROOT_INTERACTION,
            interactions.getInteractionId(InteractionType.Use)
        );
        assertEquals(FounderMonumentSpawnService.STATUE_USE_HINT, interactions.getInteractionHint());
    }

    @Test
    void thePedestalAndTheStatueBothOpenTheScreenWithANativeKeyPrompt() throws IOException {
        JsonObject block = json("/Server/Item/Items/Aetherhaven/Aetherhaven_Founder_Monument.json").getAsJsonObject("BlockType");
        assertEquals(FounderMonumentSpawnService.STATUE_USE_HINT, block.get("InteractionHint").getAsString());
        JsonObject blockUse =
            block.getAsJsonObject("Interactions").getAsJsonObject("Use").getAsJsonArray("Interactions").get(0).getAsJsonObject();
        assertEquals("OpenCustomUI", blockUse.get("Type").getAsString());
        assertEquals(FounderStatuePage.PAGE_ID, blockUse.getAsJsonObject("Page").get("Id").getAsString());

        JsonObject statueUse =
            json("/Server/Item/RootInteractions/Aetherhaven/" + FounderMonumentSpawnService.STATUE_USE_ROOT_INTERACTION + ".json")
                .getAsJsonArray("Interactions")
                .get(0)
                .getAsJsonObject();
        assertEquals("OpenCustomUI", statueUse.get("Type").getAsString());
        assertEquals(FounderStatuePage.PAGE_ID, statueUse.getAsJsonObject("Page").get("Id").getAsString());

        String prompt = lang().get(FounderMonumentSpawnService.STATUE_USE_HINT);
        assertNotNull(prompt, "The key prompt has no text");
        assertTrue(prompt.contains("{key}"), "A custom key prompt must name the key: " + prompt);
    }

    @Test
    void everyTextTheScreenShowsExistsAndReadsPlainly() throws IOException {
        Map<String, String> lang = lang();
        Set<String> needed = new HashSet<>(Set.of(
            "title", "intro", "introNoName", "armorHeading", "poseHeading", "empty", "unknownPiece", "takeOff",
            "fromPack", "noneCarried", "moreCarried", "wearTooltip", "chosen", "hint.default", "hint.worn",
            "hint.swapped", "hint.swappedSetDown", "hint.takenOff", "hint.setDown", "hint.packChanged",
            "hint.wrongPiece", "hint.pose", "noPermission", "townGone", "townGoneArmor"
        ));
        for (FounderStatueLoadout.Slot slot : FounderStatueLoadout.Slot.values()) {
            needed.add("slot." + slot.key().toLowerCase());
        }
        for (String key : needed) {
            assertTrue(lang.containsKey(FounderStatuePage.LANG + key), "Missing text " + FounderStatuePage.LANG + key);
        }
        for (FounderStatuePose pose : FounderStatuePose.values()) {
            assertTrue(lang.containsKey(pose.nameKey()), "Missing " + pose.nameKey());
            assertTrue(lang.containsKey(pose.descriptionKey()), "Missing " + pose.descriptionKey());
        }
        for (Map.Entry<String, String> line : lang.entrySet()) {
            String text = line.getValue();
            assertFalse(text.isBlank(), line.getKey() + " is empty");
            assertFalse(
                text.contains(" - ") || text.contains("—") || text.contains("–"),
                line.getKey() + " uses a dash as a pause"
            );
        }
        assertTrue(lang.get(FounderStatuePage.LANG + "intro").contains("{name}"));
        assertTrue(lang.get(FounderStatuePage.LANG + "hint.swapped").contains("{item}"));
        assertTrue(lang.get(FounderStatuePage.LANG + "hint.swapped").contains("{old}"));
        assertTrue(lang.get(FounderStatuePage.LANG + "hint.pose").contains("{pose}"));
        assertTrue(lang.get(FounderStatuePage.LANG + "moreCarried").contains("{shown}"));
        assertTrue(lang.get(FounderStatuePage.LANG + "moreCarried").contains("{count}"));
    }
}
