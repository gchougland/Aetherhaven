package com.hexvane.aetherhaven.plugin;

import com.hexvane.aetherhaven.generated.FeatureItemIds;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;

/**
 * Removes item assets for optional features that are disabled in server mod config, so they do not appear in
 * creative / give / the item registry. Other assets for those features may still be loaded.
 */
public final class DisabledFeatureItemPurge {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    /** Pack folder name (under {@code subplugin-assets/}) → plugin id name segment. */
    private static final Map<String, PluginIdentifier> PACK_FOLDER_TO_FEATURE =
        Map.ofEntries(
            Map.entry("ReputationUnlocks", AetherhavenPluginIds.REPUTATION_UNLOCKS),
            Map.entry("Jewelry", AetherhavenPluginIds.JEWELRY),
            Map.entry("FloatingGifts", AetherhavenPluginIds.FLOATING_GIFTS),
            Map.entry("PathDesigner", AetherhavenPluginIds.PATH_DESIGNER),
            Map.entry("Bard", AetherhavenPluginIds.BARD),
            Map.entry("AdminTools", AetherhavenPluginIds.ADMIN_TOOLS),
            Map.entry("Rts", AetherhavenPluginIds.RTS),
            Map.entry("PatrolRoutes", AetherhavenPluginIds.PATROL_ROUTES),
            Map.entry("PlotCreator", AetherhavenPluginIds.PLOT_CREATOR),
            Map.entry("Quests", AetherhavenPluginIds.QUESTS),
            Map.entry("Economy", AetherhavenPluginIds.ECONOMY),
            Map.entry("Commerce", AetherhavenPluginIds.COMMERCE),
            Map.entry("Guild", AetherhavenPluginIds.GUILD)
        );

    private DisabledFeatureItemPurge() {}

    public static void purgeDisabledFeatures() {
        List<String> toRemove = new ArrayList<>();
        for (Map.Entry<String, PluginIdentifier> e : PACK_FOLDER_TO_FEATURE.entrySet()) {
            if (AetherhavenFeatures.isEnabledInServerConfig(e.getValue())) {
                continue;
            }
            List<String> ids = FeatureItemIds.forPackFolder(e.getKey());
            if (ids.isEmpty()) {
                continue;
            }
            toRemove.addAll(ids);
            LOGGER.atInfo().log("Purging %d item(s) for disabled feature %s", ids.size(), e.getValue());
        }
        if (toRemove.isEmpty()) {
            return;
        }
        Set<String> removed = Item.getAssetStore().removeAssets(toRemove);
        LOGGER.atInfo().log("Removed %d item asset(s) for disabled features", removed.size());
    }
}
