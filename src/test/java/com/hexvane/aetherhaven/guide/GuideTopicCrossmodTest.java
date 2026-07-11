package com.hexvane.aetherhaven.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
class GuideTopicCrossmodTest {

    private final Gson gson = new Gson();

    @Test
    void parseTopicKey_localeAndId() {
        Path p = Path.of("pack", "Server", "Aetherhaven", "GuideTopics", "en-US", "villager_angler.md");
        GuideTopicPackOverlay.TopicKey key =
            GuideTopicPackOverlay.parseTopicKey(p, "Server/Aetherhaven/GuideTopics");
        assertNotNull(key);
        assertEquals("en-US", key.locale());
        assertEquals("villager_angler", key.topicId());
    }

    @Test
    void applyPatch_appendsSubTopicsWithoutDuplicates() {
        Map<String, List<String>> extras = new LinkedHashMap<>();
        GuidePatchDefinition patch = gson.fromJson(
            """
            {
              "schemaVersion": 1,
              "targetTopicId": "villagers",
              "addSubTopics": ["villager_angler", "villager_angler", "mechanic_fishing"]
            }
            """,
            GuidePatchDefinition.class
        );
        assertTrue(GuidePatchApplier.applyPatch(extras, patch, "test"));
        assertEquals(List.of("villager_angler", "mechanic_fishing"), extras.get("villagers"));
    }

    @Test
    void mergeSubTopics_keepsBaseOrderThenExtras() {
        List<String> merged =
            GuidePatchApplier.mergeSubTopics(
                List.of("villager_merchant", "villager_florist"),
                Map.of("villagers", List.of("villager_angler")),
                "villagers"
            );
        assertEquals(List.of("villager_merchant", "villager_florist", "villager_angler"), merged);
    }

    @Test
    void repository_walkIncludesPackTopicViaPatch() {
        GuideTopicRepository.clearCache();
        Map<String, Map<String, String>> overlayMap = new LinkedHashMap<>();
        Map<String, String> en = new LinkedHashMap<>();
        en.put(
            "welcome",
            """
            ---
            name: Welcome
            sub-topics:
              - villagers
            ---
            Body
            """
        );
        en.put(
            "villagers",
            """
            ---
            name: Villagers
            sub-topics:
              - villager_core
            ---
            Hub
            """
        );
        en.put(
            "villager_core",
            """
            ---
            name: Core
            ---
            Core body
            """
        );
        en.put(
            "villager_angler",
            """
            ---
            name: Reed Castwell
            npcRoleId: Mod_Angler
            ---
            Angler body
            """
        );
        overlayMap.put("en-US", en);
        GuideTopicPackOverlay overlay = GuideTopicPackOverlay.of(overlayMap);

        Map<String, List<String>> extras = new LinkedHashMap<>();
        extras.put("villagers", new ArrayList<>(List.of("villager_angler")));

        GuideTopicRepository repo =
            GuideTopicRepository.load(GuideTopicCrossmodTest.class.getClassLoader(), "en-US", overlay, extras);

        assertNotNull(repo.byId("villager_angler"));
        assertEquals("Reed Castwell", repo.byId("villager_angler").displayName());
        assertEquals("Mod_Angler", repo.byId("villager_angler").npcRoleId());
        assertTrue(repo.byId("villagers").subTopicIds().contains("villager_angler"));
        assertTrue(
            repo.navEntries().stream().anyMatch(e -> "villager_angler".equals(e.topicId()))
        );
    }
}
