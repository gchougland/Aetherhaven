package com.hexvane.aetherhaven.quest;

import com.google.gson.*;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.dialogue.DialogueCatalog;
import com.hexvane.aetherhaven.dialogue.data.*;
import com.hexvane.aetherhaven.inn.InnVisitorShopPromotion;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.villager.data.VillagerDefinitionCatalog;
import java.util.*;

/** Every core resident must have a playable housing quest, including residents added by asset packs. */
public final class CoreVillagerHousing {
    private CoreVillagerHousing() {}
    private static final Gson GSON = new Gson();
    private static final String LANG = "aetherhaven_housing.";

    /** Run after all three catalogs load. Keep authored quests and dialogue; fill omissions deterministically. */
    public static QuestCatalog ensure(VillagerDefinitionCatalog villagers, ConstructionCatalog buildings,
                                      QuestCatalog quests, DialogueCatalog dialogues) {
        var result = new LinkedHashMap<>(quests.all());
        for (var villager : villagers.allByNpcRoleId().values()) {
            String role = villager.getNpcRoleId();
            var tree = villager.getResidentTreeId() == null ? null : dialogues.get(villager.getResidentTreeId());
            if (tree == null || tree.getNode(tree.entryOrDefault()) == null)
                throw new IllegalStateException("Core villager has no resident dialogue for housing: " + role);
            var house = result.values().stream().filter(q -> isHousingFor(q, role)).findFirst().orElse(null);
            String work = villager.getWorkConstructionId();
            String recruitment = work == null ? null : InnVisitorShopPromotion.findShopQuestId(quests, buildings, villager, work);
            // A role's housing quest is not its recruitment quest.
            if (recruitment != null && isHousingFor(quests.get(recruitment), role)) recruitment = null;
            if (house == null) {
                String id = "q_house_" + villager.getDialogueVillagerKind();
                if (result.containsKey(id)) throw new IllegalStateException("Housing quest id collision: " + id);
                var json = JsonParser.parseString("""
                    {"schemaVersion":1,"category":"housing","grantPlotTokenConstructionId":"plot_house",
                     "objectives":[
                       {"id":"token","kind":"plot_token_received","text":"Accept the house plot token."},
                       {"id":"build","kind":"construction_built","constructionId":"plot_house","text":"Build a house, or use an existing house with room."},
                       {"id":"assign","kind":"assign_house_resident","text":"Assign this villager at the house's Town Records Shelf."},
                       {"id":"talk","kind":"dialogue_turn_in","text":"Return to the villager to finish their house quest."}],
                     "rewards":[{"kind":"reputation","amount":10,"grantTo":"quest_beneficiary_npc"},
                                {"kind":"item","itemId":"Aetherhaven_Gold_Coin","count":16,"grantTo":"player"}],
                     "repeat":{"mode":"none"}}
                    """).getAsJsonObject();
                json.addProperty("id", id);
                json.addProperty("title", villager.getDisplayName() + "'s home");
                json.addProperty("description", "With a place in town, " + villager.getDisplayName() + " needs a home. Build a house or assign a room in an existing one, then return to them.");
                json.addProperty("assignNpcRoleId", role);
                json.getAsJsonArray("objectives").get(2).getAsJsonObject().addProperty("npcRoleId", role);
                json.getAsJsonArray("rewards").get(0).getAsJsonObject().addProperty("npcRoleId", role);
                if (recruitment != null) json.add("prerequisites", GSON.toJsonTree(Map.of("completedQuestIds", List.of(recruitment))));
                house = GSON.fromJson(json, QuestDefinition.class);
                result.put(id, house);
            }
            String id = house.idOrEmpty();
            if (!hasAction(tree, "start_quest", id) || !hasAction(tree, "complete_quest", id))
                addDialogue(tree, house, work);
            if (!hasAction(tree, "start_quest", id) || !hasAction(tree, "complete_quest", id))
                throw new IllegalStateException("Core villager housing is not playable: " + role);
        }
        return QuestCatalog.of(result);
    }

    public static boolean isHousingFor(QuestDefinition quest, String role) {
        return quest != null && role.equals(quest.assignNpcRoleId()) && !quest.assignByEntity()
            && "plot_house".equals(quest.grantPlotTokenConstructionId())
            && quest.objectivesOrEmpty().stream().anyMatch(o -> "assign_house_resident".equals(o.kind()));
    }

    public static boolean hasAction(DialogueTreeDefinition tree, String action, String quest) {
        var pending = new ArrayDeque<String>();
        var seen = new HashSet<String>();
        pending.add(tree.entryOrDefault());
        while (!pending.isEmpty()) {
            String id = pending.remove();
            if (!seen.add(id)) continue;
            var node = tree.getNode(id);
            if (node == null) continue;
            for (var a : node.getActions()) if (matches(a, action, quest)) return true;
            for (var choice : node.getChoices()) {
                for (var a : choice.getActions()) if (matches(a, action, quest)) return true;
                if (choice.getNext() != null && !choice.getNext().isBlank()) pending.add(choice.getNext());
            }
        }
        return false;
    }
    private static boolean matches(JsonObject a, String action, String quest) {
        return a.has("type") && action.equals(a.get("type").getAsString()) && a.has("id") && quest.equals(a.get("id").getAsString());
    }
    private static JsonObject condition(String type, String key, String value) {
        var c = new JsonObject(); c.addProperty("type", type);
        if (key != null) c.addProperty(key, value);
        return c;
    }
    private static JsonObject not(JsonObject c) {
        var result = condition("not", null, null); result.add("condition", c); return result;
    }
    private static JsonObject and(List<JsonObject> conditions) {
        var result = condition("and", null, null); result.add("conditions", GSON.toJsonTree(conditions)); return result;
    }
    private static JsonObject choice(String id, String text, String next, JsonObject condition, String action, String quest) {
        var c = new JsonObject(); c.addProperty("id", id); c.addProperty("text", LANG + text); c.addProperty("next", next);
        if (condition != null) { c.add("condition", condition); c.addProperty("whenFalse", "hide"); }
        if (action != null) c.add("actions", GSON.toJsonTree(List.of(Map.of("type", action, "id", quest))));
        return c;
    }
    private static void node(DialogueTreeDefinition tree, String id, String text, JsonObject... choices) {
        var n = new JsonObject(); n.addProperty("speaker", tree.getNode(tree.entryOrDefault()).getSpeaker());
        n.addProperty("text", LANG + text); n.add("choices", GSON.toJsonTree(choices));
        tree.putNode(id, GSON.fromJson(n, DialogueNodeDefinition.class));
    }
    private static void addDialogue(DialogueTreeDefinition tree, QuestDefinition quest, String work) {
        String id = quest.idOrEmpty(), prefix = "housing_" + id;
        var offer = new ArrayList<JsonObject>();
        offer.add(not(condition("npc_binding_is_visitor", null, null)));
        offer.add(not(condition("town_quest_active", "questId", id)));
        offer.add(not(condition("town_quest_completed", "questId", id)));
        if (work != null) offer.add(condition("town_has_complete_plot", "constructionId", work));
        for (String prerequisite : quest.prerequisitesOrEmpty().completedQuestIdsOrEmpty())
            offer.add(condition("town_quest_completed", "questId", prerequisite));
        var hub = tree.getNode(tree.entryOrDefault());
        if (!hasAction(tree,"start_quest",id)) {
            hub.addOrReplaceChoice(GSON.fromJson(choice(prefix+"_offer","offer",prefix+"_offer",and(offer),null,id), DialogueChoiceDefinition.class));
            node(tree,prefix+"_offer","offerBody",choice("accept","accept",prefix+"_remind",and(offer),"start_quest",id),
                choice("later","later",tree.entryOrDefault(),null,null,id));
        }
        var active = condition("town_quest_active","questId",id);
        var assigned = condition("town_npc_home_resident_house",null,null);
        hub.addOrReplaceChoice(GSON.fromJson(choice(prefix+"_remind","remind",prefix+"_remind",and(List.of(active,not(assigned))),null,id),DialogueChoiceDefinition.class));
        node(tree,prefix+"_remind","remindBody",choice("back","back",tree.entryOrDefault(),null,null,id));
        if (!hasAction(tree,"complete_quest",id)) {
            hub.addOrReplaceChoice(GSON.fromJson(choice(prefix+"_finish","finish",prefix+"_finish",and(List.of(active,assigned)),"complete_quest",id),DialogueChoiceDefinition.class));
            node(tree,prefix+"_finish","finishBody",choice("back","back",tree.entryOrDefault(),null,null,id));
        }
    }
}
