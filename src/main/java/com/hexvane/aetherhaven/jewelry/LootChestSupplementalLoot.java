package com.hexvane.aetherhaven.jewelry;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.loot.LootChestPlotBlueprintLoot;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import javax.annotation.Nonnull;

/** Whether plot blueprint / Gaia supplemental chest rolls can run (catalog pool, item assets). */
public final class LootChestSupplementalLoot {
    private LootChestSupplementalLoot() {}

    /**
     * @return false when supplemental rolls should be deferred (e.g. empty blueprint pool or missing item assets)
     */
    public static boolean isReadyToRoll(@Nonnull AetherhavenPlugin plugin, @Nonnull AetherhavenPluginConfig cfg) {
        if (needsPlotBlueprintRoll(cfg) && !canRollPlotBlueprint(plugin.getConstructionCatalog())) {
            return false;
        }
        if (needsGaiaShardRoll(cfg) && !hasItemAsset(cfg.getLootChestGaiaShardItemId())) {
            return false;
        }
        if (needsGaiaCatalystRoll(cfg) && !hasItemAsset(cfg.getLootChestGaiaCatalystItemId())) {
            return false;
        }
        return true;
    }

    public static boolean needsPlotBlueprintRoll(@Nonnull AetherhavenPluginConfig cfg) {
        return cfg.getLootChestPlotBlueprintChance() > 0.0;
    }

    public static boolean needsGaiaShardRoll(@Nonnull AetherhavenPluginConfig cfg) {
        return cfg.getLootChestGaiaShardChance() > 0.0 && !cfg.getLootChestGaiaShardItemId().isBlank();
    }

    public static boolean needsGaiaCatalystRoll(@Nonnull AetherhavenPluginConfig cfg) {
        return cfg.getLootChestGaiaCatalystChance() > 0.0 && !cfg.getLootChestGaiaCatalystItemId().isBlank();
    }

    public static boolean canRollPlotBlueprint(@Nonnull ConstructionCatalog catalog) {
        return !LootChestPlotBlueprintLoot.listEligibleConstructionIds(catalog).isEmpty();
    }

    /** True when any configured supplemental roll needs a free inventory slot. */
    public static boolean anySupplementalRollConfigured(@Nonnull AetherhavenPluginConfig cfg) {
        return needsPlotBlueprintRoll(cfg) || needsGaiaShardRoll(cfg) || needsGaiaCatalystRoll(cfg);
    }

    private static boolean hasItemAsset(@Nonnull String itemId) {
        if (itemId.isBlank()) {
            return false;
        }
        return Item.getAssetMap().getAsset(itemId) != null;
    }
}
