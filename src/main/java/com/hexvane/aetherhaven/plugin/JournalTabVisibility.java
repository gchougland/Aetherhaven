package com.hexvane.aetherhaven.plugin;

import javax.annotation.Nonnull;

/** Gates quest-journal town sub-panels when optional subplugins are unloaded. */
public final class JournalTabVisibility {
    private JournalTabVisibility() {}

    public static boolean reputationTab() {
        return AetherhavenFeatures.isLoaded(AetherhavenPluginIds.REPUTATION);
    }

    public static boolean patrolRoutesTab() {
        return AetherhavenFeatures.isLoaded(AetherhavenPluginIds.PATROL_ROUTES);
    }

    public static boolean rtsTuningTab() {
        return AetherhavenFeatures.isLoaded(AetherhavenPluginIds.RTS);
    }

    public static boolean plotCreatorTab() {
        return AetherhavenFeatures.isLoaded(AetherhavenPluginIds.PLOT_CREATOR);
    }

    public static boolean jewelryTab() {
        return AetherhavenFeatures.isLoaded(AetherhavenPluginIds.JEWELRY);
    }

    public static boolean difficultyTab() {
        return AetherhavenFeatures.isLoaded(AetherhavenPluginIds.ADMIN_TOOLS);
    }

    public static boolean questBoardContent() {
        return AetherhavenFeatures.isLoaded(AetherhavenPluginIds.QUESTS);
    }
}
