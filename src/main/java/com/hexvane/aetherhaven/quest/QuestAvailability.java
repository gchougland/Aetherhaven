package com.hexvane.aetherhaven.quest;

import com.hexvane.aetherhaven.plugin.AetherhavenFeatures;
import com.hexvane.aetherhaven.plugin.AetherhavenPluginIds;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Whether a quest definition is available on the current server (subplugin gates, etc.). */
public final class QuestAvailability {
    private QuestAvailability() {}

    public static boolean isEnabled(@Nullable QuestDefinition def) {
        if (def == null) {
            return false;
        }
        String raw = def.requiresSubpluginName();
        if (raw == null || raw.isBlank()) {
            return true;
        }
        return AetherhavenFeatures.isLoaded(AetherhavenPluginIds.id(raw.trim()));
    }

    public static boolean isEnabled(@Nonnull QuestCatalog catalog, @Nonnull String questId) {
        return isEnabled(catalog.get(questId.trim()));
    }
}
