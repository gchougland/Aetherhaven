package com.hexvane.aetherhaven.worldnpc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.dialogue.data.DialogueChoiceDefinition;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Hides town-construction dialogue choices when the speaker is a world / hub NPC.
 * Town trees reuse the same main_hub nodes; forge/house offers must not appear on hubs.
 */
public final class WorldNpcDialogueChoiceFilter {
    private WorldNpcDialogueChoiceFilter() {}

    public static boolean shouldHideForWorldNpc(
        @Nonnull DialogueChoiceDefinition choice,
        @Nonnull AetherhavenPlugin plugin
    ) {
        if (actionsStartOrCompleteTownQuest(choice.getActions(), plugin)) {
            return true;
        }
        return conditionGatesOnTownOnlyQuest(choice.getCondition(), plugin)
            || conditionGatesOnTownOnlyQuest(choice.getVisibilityCondition(), plugin);
    }

    private static boolean actionsStartOrCompleteTownQuest(
        @Nullable List<JsonObject> actions,
        @Nonnull AetherhavenPlugin plugin
    ) {
        if (actions == null || actions.isEmpty()) {
            return false;
        }
        for (JsonObject action : actions) {
            if (action == null) {
                continue;
            }
            String type = stringOrEmpty(action, "type").toLowerCase(Locale.ROOT);
            if (!"start_quest".equals(type)
                && !"complete_quest".equals(type)
                && !"abandon_quest".equals(type)) {
                continue;
            }
            String questId = stringOrEmpty(action, "id");
            if (questId.isEmpty()) {
                questId = stringOrEmpty(action, "questId");
            }
            if (isTownOnlyQuest(plugin, questId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean conditionGatesOnTownOnlyQuest(
        @Nullable JsonObject condition,
        @Nonnull AetherhavenPlugin plugin
    ) {
        if (condition == null) {
            return false;
        }
        String type = stringOrEmpty(condition, "type").toLowerCase(Locale.ROOT);
        if ("town_quest_active".equals(type)
            || "town_quest_completed".equals(type)
            || "town_quest_entity_kills_met".equals(type)) {
            return isTownOnlyQuest(plugin, stringOrEmpty(condition, "questId"));
        }
        if ("not".equals(type)) {
            JsonObject inner = condition.has("condition") && condition.get("condition").isJsonObject()
                ? condition.getAsJsonObject("condition")
                : null;
            return conditionGatesOnTownOnlyQuest(inner, plugin);
        }
        if ("and".equals(type) || "or".equals(type)) {
            JsonArray arr = condition.getAsJsonArray("conditions");
            if (arr == null) {
                return false;
            }
            for (JsonElement el : arr) {
                if (el != null && el.isJsonObject() && conditionGatesOnTownOnlyQuest(el.getAsJsonObject(), plugin)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isTownOnlyQuest(@Nonnull AetherhavenPlugin plugin, @Nonnull String questId) {
        String id = questId != null ? questId.trim() : "";
        if (id.isEmpty()) {
            return false;
        }
        QuestDefinition def = plugin.getQuestCatalog().get(id);
        return def != null && !WorldQuestProgressionService.isWorldQuest(def);
    }

    @Nonnull
    private static String stringOrEmpty(@Nonnull JsonObject o, @Nonnull String key) {
        if (!o.has(key) || o.get(key) == null || o.get(key).isJsonNull() || !o.get(key).isJsonPrimitive()) {
            return "";
        }
        return o.get(key).getAsString().trim();
    }
}
