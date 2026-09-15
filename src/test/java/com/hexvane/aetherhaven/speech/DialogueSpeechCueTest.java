package com.hexvane.aetherhaven.speech;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.dialogue.data.DialogueNodeDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Tag("entity")
class DialogueSpeechCueTest {
    @Test void choiceOverridesCanBeAuthoredAlongsideNodeDefaults() {
        var node = new Gson().fromJson("""
            {"speechClip":"Question","choices":[{"text":"Accept","next":"thanks","speechClip":"Agree"}]}
            """, DialogueNodeDefinition.class);
        assertEquals("Question", node.getSpeechClip());
        assertEquals("Agree", node.getChoices().getFirst().getSpeechClip());
        assertEquals("Agree", DialogueSpeechCue.forNode(node.getChoices().getFirst().getSpeechClip(), "quest_offer"));
        assertEquals("Question", DialogueSpeechCue.forNode(null, "quest_offer"));
        assertEquals("Agree", DialogueSpeechCue.forNode(null, "quest_complete"));
        assertEquals("Greet", DialogueSpeechCue.forNode(null, "main_hub"));
        assertEquals("Talk", DialogueSpeechCue.forNode(null, "ordinary_chat"));
    }

    @Test void everyDialogueCategoryHasARealClipAndMatchingAction() throws Exception {
        var playback = JsonParser.parseString(Files.readString(Path.of("src/main/resources/defaults/villager_life_playback.json"))).getAsJsonObject();
        for (String category : new String[]{"Talk","Question","Thinking","Laugh","Gasp","Grumble","Groan","Yawn","Sigh","Idle","Work","Greet","Agree"}) {
            var cue = DialogueSpeechCue.resolve(category);
            var clips = playback.getAsJsonArray("BrightFemale_" + cue.clip());
            assertNotNull(clips, category);
            for (var clip : clips) assertTrue(clip.getAsJsonObject().getAsJsonObject("faces").has(cue.gesture()), category);
        }
        assertEquals("None", DialogueSpeechCue.resolve("silent").clip());
        assertEquals("Ponder", DialogueSpeechCue.resolve("thinking").gesture());
    }

    @Test void questOffersAndResponsesAreExplicitlyVoiced() throws Exception {
        int offers = 0;
        try (var files = Files.list(Path.of("src/main/resources/Server/Aetherhaven/Dialogue"))) {
            for (var file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                var tree = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                for (var entry : tree.getAsJsonObject("nodes").entrySet()) {
                    var node = entry.getValue().getAsJsonObject();
                    if (!node.has("choices")) continue;
                    for (var value : node.getAsJsonArray("choices")) {
                        var choice = value.getAsJsonObject();
                        if (!choice.has("actions")) continue;
                        for (var action : choice.getAsJsonArray("actions")) {
                            if (!"start_quest".equals(action.getAsJsonObject().get("type").getAsString())) continue;
                            assertEquals("Question", node.get("speechClip").getAsString(), file + ":" + entry.getKey());
                            assertEquals("Agree", choice.get("speechClip").getAsString());
                            offers++;
                        }
                    }
                }
            }
        }
        assertEquals(42, offers);
    }
}
