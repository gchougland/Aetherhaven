package com.hexvane.aetherhaven.dialogue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.dialogue.data.DialoguePatchDefinition;
import com.hexvane.aetherhaven.dialogue.data.DialogueTreeDefinition;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
class DialoguePatchApplierTest {

    private final Gson gson = new Gson();

    @Test
    void injectsNodesAndChoices() {
        DialogueTreeDefinition tree = gson.fromJson(
            """
            {
              "id": "aetherhaven_merchant",
              "entry": "main_hub",
              "nodes": {
                "main_hub": {
                  "text": "hub",
                  "choices": [
                    { "id": "leave", "text": "bye", "actions": [{ "type": "close" }] }
                  ]
                }
              }
            }
            """,
            DialogueTreeDefinition.class
        );
        Map<String, DialogueTreeDefinition> trees = new LinkedHashMap<>();
        trees.put("aetherhaven_merchant", tree);

        DialoguePatchDefinition patch = gson.fromJson(
            """
            {
              "schemaVersion": 1,
              "targetTreeId": "aetherhaven_merchant",
              "addNodes": {
                "fish_hub": {
                  "text": "fish",
                  "choices": [ { "text": "back", "next": "main_hub" } ]
                }
              },
              "nodePatches": [
                {
                  "nodeId": "main_hub",
                  "addChoices": [
                    {
                      "id": "fish_talk",
                      "text": "about fish",
                      "next": "fish_hub"
                    }
                  ]
                }
              ]
            }
            """,
            DialoguePatchDefinition.class
        );

        assertTrue(DialoguePatchApplier.applyPatch(trees, patch, "test"));
        DialogueTreeDefinition patched = trees.get("aetherhaven_merchant");
        assertNotNull(patched.getNode("fish_hub"));
        assertEquals(2, patched.getNode("main_hub").getChoices().size());
        assertEquals("fish_talk", patched.getNode("main_hub").getChoices().get(1).getId());
        assertEquals("fish_hub", patched.getNode("main_hub").getChoices().get(1).getNext());
    }

    @Test
    void replacesChoiceWithSameId() {
        DialogueTreeDefinition tree = gson.fromJson(
            """
            {
              "id": "t",
              "entry": "main_hub",
              "nodes": {
                "main_hub": {
                  "text": "hub",
                  "choices": [
                    { "id": "fish_talk", "text": "old", "next": "old_node" }
                  ]
                }
              }
            }
            """,
            DialogueTreeDefinition.class
        );
        Map<String, DialogueTreeDefinition> trees = new LinkedHashMap<>();
        trees.put("t", tree);
        DialoguePatchDefinition patch = gson.fromJson(
            """
            {
              "targetTreeId": "t",
              "nodePatches": [
                {
                  "nodeId": "main_hub",
                  "addChoices": [
                    { "id": "fish_talk", "text": "new", "next": "new_node" }
                  ]
                }
              ]
            }
            """,
            DialoguePatchDefinition.class
        );
        assertTrue(DialoguePatchApplier.applyPatch(trees, patch, "test"));
        assertEquals(1, trees.get("t").getNode("main_hub").getChoices().size());
        assertEquals("new", trees.get("t").getNode("main_hub").getChoices().get(0).getText());
        assertEquals("new_node", trees.get("t").getNode("main_hub").getChoices().get(0).getNext());
    }
}
