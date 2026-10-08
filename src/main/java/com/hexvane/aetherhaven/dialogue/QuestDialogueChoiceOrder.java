package com.hexvane.aetherhaven.dialogue;

import com.hexvane.aetherhaven.dialogue.data.*;
import java.util.*;
import com.google.gson.JsonElement;

/** Stable quest-first ordering, including offers that lead through an explanation node. */
public final class QuestDialogueChoiceOrder {
    private QuestDialogueChoiceOrder() {}
    public static void promote(List<DialogueChoiceDefinition> choices, DialogueTreeDefinition tree) {
        choices.sort(Comparator.comparingInt(choice -> related(choice, tree, new HashSet<>()) ? 0 : goodbye(choice) ? 3 : choice.getActions().isEmpty() && questCondition(choice.getCondition()) ? 2 : 1));
    }
    private static boolean goodbye(DialogueChoiceDefinition choice) {
        return choice.endsConversation() && choice.getActions().stream().allMatch(action -> action == null
            || action.has("type") && "close".equalsIgnoreCase(action.get("type").getAsString()));
    }
    private static boolean questCondition(JsonElement value) {
        if(value==null||value.isJsonNull())return false;
        if(value.isJsonArray()){for(var child:value.getAsJsonArray())if(questCondition(child))return true;return false;}
        if(!value.isJsonObject())return false;
        var object=value.getAsJsonObject();var type=object.get("type");
        if(type!=null&&type.isJsonPrimitive()&&type.getAsString().equals("town_quest_completed"))return true;
        return questCondition(object.get("condition"))||questCondition(object.get("conditions"));
    }
    private static boolean related(DialogueChoiceDefinition choice, DialogueTreeDefinition tree, Set<String> seen) {
        if (choice.isQuestOfferChoice() || choice.isQuestProgressChoice()
            || choice.hasAction("abandon_quest") || choice.hasAction("deliver_tourist_move_in_items")) return true;
        String next = choice.getNext();
        if (tree == null || next == null || next.equals("main_hub") || next.equals(tree.entryOrDefault()) || !seen.add(next)) return false;
        var node = tree.getNode(next);
        return node != null && node.getChoices().stream().anyMatch(child -> related(child, tree, seen));
    }
}
