package com.hexvane.aetherhaven.startertown;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Marks non-housing construction quests represented by a generated starter town complete. */
final class StarterTownQuestService {
    private StarterTownQuestService() {}

    static int completeBuildingQuests(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull TownManager townManager,
        @Nonnull StarterTownLayoutPlan plan
    ) {
        Set<String> built = new HashSet<>();
        for (StarterTownLayoutPlan.Building building : plan.buildings()) {
            built.add(building.constructionId());
        }
        int completed = 0;
        for (QuestDefinition quest : plugin.getQuestCatalog().all().values()) {
            if (!shouldAutoComplete(quest)) {
                continue;
            }
            boolean matches = built.stream()
                .anyMatch(id -> matchesBuiltConstruction(plugin.getConstructionCatalog(), quest, id));
            if (!matches || town.hasQuestCompleted(quest.idOrEmpty())) {
                continue;
            }
            town.completeQuest(quest.idOrEmpty());
            completed++;
        }
        if (completed > 0) {
            townManager.updateTown(town);
        }
        return completed;
    }

    static boolean shouldAutoComplete(@Nonnull QuestDefinition quest) {
        String category = quest.category();
        if (category != null && "housing".equalsIgnoreCase(category.trim())) {
            return false;
        }
        return nonBlank(quest.grantPlotTokenConstructionId()) != null
            || nonBlank(quest.grantPlotBlueprintConstructionId()) != null;
    }

    static boolean matchesBuiltConstruction(
        @Nonnull ConstructionCatalog catalog,
        @Nonnull QuestDefinition quest,
        @Nonnull String builtConstructionId
    ) {
        return matches(catalog, builtConstructionId, quest.grantPlotTokenConstructionId())
            || matches(catalog, builtConstructionId, quest.grantPlotBlueprintConstructionId());
    }

    private static boolean matches(
        @Nonnull ConstructionCatalog catalog,
        @Nonnull String builtConstructionId,
        @Nullable String questConstructionId
    ) {
        String questId = nonBlank(questConstructionId);
        return questId != null
            && (catalog.matchesGameplayConstruction(builtConstructionId, questId)
                || catalog.matchesGameplayConstruction(questId, builtConstructionId));
    }

    @Nullable
    private static String nonBlank(@Nullable String value) {
        return value != null && !value.isBlank() ? value.trim() : null;
    }
}
