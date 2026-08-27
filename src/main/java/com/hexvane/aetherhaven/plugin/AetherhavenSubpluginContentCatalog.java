package com.hexvane.aetherhaven.plugin;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.common.plugin.PluginIdentifier;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Maps Aetherhaven item ids to the subplugin that owns them (reward filtering, diagnostics). */
public final class AetherhavenSubpluginContentCatalog {
    private static final List<Rule> RULES =
        List.of(
            rule(AetherhavenPluginIds.REPUTATION_UNLOCKS, AetherhavenConstants.ITEM_PURIFICATION_POWDER),
            rule(AetherhavenPluginIds.REPUTATION_UNLOCKS, AetherhavenConstants.ITEM_ROOT_REMOVER),
            rule(AetherhavenPluginIds.REPUTATION_UNLOCKS, AetherhavenConstants.ITEM_GROWTH_SERUM),
            rule(AetherhavenPluginIds.REPUTATION_UNLOCKS, AetherhavenConstants.ITEM_HUNTING_KNIFE),
            rule(AetherhavenPluginIds.REPUTATION_UNLOCKS, AetherhavenConstants.ITEM_GEODE_ANVIL),
            rule(AetherhavenPluginIds.REPUTATION_UNLOCKS, AetherhavenConstants.ITEM_GAIAS_DRAUGHT),
            rule(AetherhavenPluginIds.REPUTATION_UNLOCKS, AetherhavenConstants.ITEM_SHARD_OF_GAIA),
            rule(AetherhavenPluginIds.REPUTATION_UNLOCKS, AetherhavenConstants.ITEM_VERDANT_CATALYST),
            rule(AetherhavenPluginIds.REPUTATION_UNLOCKS, "Aetherhaven_Sprinkler_Iron"),
            rule(AetherhavenPluginIds.REPUTATION_UNLOCKS, "Aetherhaven_Sprinkler_Cobalt"),
            rule(AetherhavenPluginIds.REPUTATION_UNLOCKS, "Aetherhaven_Sprinkler_Thorium"),
            rule(AetherhavenPluginIds.REPUTATION_UNLOCKS, "Aetherhaven_Sprinkler_Adamantite"),
            rule(AetherhavenPluginIds.REPUTATION_UNLOCKS, "Aetherhaven_Firewood"),
            rule(AetherhavenPluginIds.PATH_DESIGNER, AetherhavenConstants.PATH_TOOL_ITEM_ID),
            rule(AetherhavenPluginIds.PATROL_ROUTES, AetherhavenConstants.PATROL_WAND_ITEM_ID),
            rule(AetherhavenPluginIds.ADMIN_TOOLS, AetherhavenConstants.POI_TOOL_ITEM_ID),
            rule(AetherhavenPluginIds.ADMIN_TOOLS, AetherhavenConstants.NPC_DEBUG_STICK_ITEM_ID),
            rule(AetherhavenPluginIds.PLOT_CREATOR, AetherhavenConstants.PLOT_CREATOR_STAFF_ITEM_ID),
            rule(AetherhavenPluginIds.PLOT_CREATOR, AetherhavenConstants.PLOT_CREATOR_STAFF_BOUNDS_ITEM_ID),
            rule(AetherhavenPluginIds.PLOT_CREATOR, AetherhavenConstants.BUILDING_EDITOR_STAFF_ITEM_ID),
            rule(AetherhavenPluginIds.PLOT_CREATOR, AetherhavenConstants.PLOT_PLACEMENT_TOOL_ITEM_ID),
            rule(AetherhavenPluginIds.PLOT_CREATOR, AetherhavenConstants.WALL_WAND_ITEM_ID),
            rule(AetherhavenPluginIds.RTS, AetherhavenConstants.COMMAND_POST_ITEM_ID),
            rule(AetherhavenPluginIds.RTS, AetherhavenConstants.RTS_FLAG_ITEM_ID),
            rule(AetherhavenPluginIds.RTS, AetherhavenConstants.RTS_SWORD_ITEM_ID),
            rule(AetherhavenPluginIds.RTS, AetherhavenConstants.RTS_SELECT_ALL_ITEM_ID),
            rule(AetherhavenPluginIds.RTS, AetherhavenConstants.RTS_SELECT_KNIGHT_ITEM_ID),
            rule(AetherhavenPluginIds.RTS, AetherhavenConstants.RTS_SELECT_ARCHER_ITEM_ID),
            rule(AetherhavenPluginIds.RTS, AetherhavenConstants.RTS_SELECT_MAGE_ITEM_ID),
            rule(AetherhavenPluginIds.RTS, AetherhavenConstants.RTS_STANCE_BANNER_ITEM_ID),
            rule(AetherhavenPluginIds.RTS, AetherhavenConstants.RTS_FREE_ITEM_ID),
            rule(AetherhavenPluginIds.RTS, AetherhavenConstants.RTS_EXIT_ITEM_ID),
            rule(AetherhavenPluginIds.JEWELRY, AetherhavenConstants.ITEM_HAND_MIRROR),
            rule(AetherhavenPluginIds.JEWELRY, AetherhavenConstants.ITEM_APPRAISAL_BENCH),
            rule(AetherhavenPluginIds.JEWELRY, AetherhavenConstants.ITEM_JEWELRY_CRAFTING_BENCH),
            rule(AetherhavenPluginIds.JEWELRY, AetherhavenConstants.ITEM_RING_GLOW),
            rule(AetherhavenPluginIds.JEWELRY, AetherhavenConstants.ITEM_RING_LARGE_GLOW),
            rule(AetherhavenPluginIds.ECONOMY, AetherhavenConstants.TREASURY_BLOCK_TYPE_ID),
            rule(AetherhavenPluginIds.ECONOMY, AetherhavenConstants.SHOP_SAFE_ITEM_ID),
            rule(AetherhavenPluginIds.COMMERCE, AetherhavenConstants.SHOP_SPOT_ITEM_ID),
            rule(AetherhavenPluginIds.COMMERCE, AetherhavenConstants.TOURIST_PORTAL_ITEM_ID),
            rule(AetherhavenPluginIds.COMMERCE, AetherhavenConstants.ITEM_BANQUET_TABLE),
            rule(AetherhavenPluginIds.COMMERCE, AetherhavenConstants.INN_BELL_BLOCK_TYPE_ID),
            rule(AetherhavenPluginIds.QUESTS, AetherhavenConstants.QUEST_BOARD_ITEM_ID),
            rule(AetherhavenPluginIds.CONSTRUCTION, AetherhavenConstants.BUILDING_STAFF_ITEM_ID),
            rule(AetherhavenPluginIds.CONSTRUCTION, "Aetherhaven_Building_Staff_Iron"),
            rule(AetherhavenPluginIds.CONSTRUCTION, "Aetherhaven_Building_Staff_Cobalt"),
            rule(AetherhavenPluginIds.CONSTRUCTION, "Aetherhaven_Building_Staff_Thorium"),
            rule(AetherhavenPluginIds.CONSTRUCTION, "Aetherhaven_Building_Staff_Adamantite"),
            rule(AetherhavenPluginIds.CONSTRUCTION, AetherhavenConstants.WOOD_SCAFFOLD_ITEM_ID),
            rule(AetherhavenPluginIds.CONSTRUCTION, AetherhavenConstants.CHARTER_ITEM_ID),
            rule(AetherhavenPluginIds.CONSTRUCTION, AetherhavenConstants.MANAGEMENT_BLOCK_TYPE_ID),
            rule(AetherhavenPluginIds.CONSTRUCTION, AetherhavenConstants.PLOT_SIGN_ITEM_ID),
            rule(AetherhavenPluginIds.CONSTRUCTION, AetherhavenConstants.ITEM_FOUNDER_MONUMENT),
            rule(AetherhavenPluginIds.CONSTRUCTION, AetherhavenConstants.ITEM_CHARTER_AMENDMENTS_TABLE),
            rule(AetherhavenPluginIds.CONSTRUCTION, AetherhavenConstants.PLOT_CRAFTING_BENCH_ITEM_ID),
            rule(AetherhavenPluginIds.CONSTRUCTION, AetherhavenConstants.BLOCK_PRODUCTION_STORAGE),
            rule(AetherhavenPluginIds.CONSTRUCTION, "Aetherhaven_Town_Planning_Desk"),
            rule(AetherhavenPluginIds.CONSTRUCTION, "Aetherhaven_Smokestack"),
            rule(AetherhavenPluginIds.CONSTRUCTION, AetherhavenConstants.STATUE_OF_GAIA_BLOCK_TYPE_ID),
            rule(AetherhavenPluginIds.CONSTRUCTION, "Aetherhaven_Statue_Of_Gaia")
        );

    private AetherhavenSubpluginContentCatalog() {}

    @Nullable
    public static PluginIdentifier ownerSubplugin(@Nonnull String itemId) {
        String id = itemId.trim();
        for (Rule rule : RULES) {
            if (rule.itemId.equalsIgnoreCase(id)) {
                return rule.subplugin;
            }
        }
        if (isJewelryCraftedId(id)) {
            return AetherhavenPluginIds.JEWELRY;
        }
        return null;
    }

    public static boolean isItemAvailable(@Nonnull String itemId) {
        return Item.getAssetMap().getAsset(itemId.trim()) != null;
    }

    public static boolean isRewardItemAvailable(@Nullable String itemId, @Nullable String learnRecipeItemId) {
        if (learnRecipeItemId != null && !learnRecipeItemId.isBlank()) {
            return isItemAvailable(learnRecipeItemId.trim());
        }
        if (itemId == null || itemId.isBlank() || itemId.startsWith("Rock_") || itemId.startsWith("Ingredient_")) {
            return true;
        }
        return isItemAvailable(itemId.trim());
    }

    private static boolean isJewelryCraftedId(@Nonnull String itemId) {
        return itemId.startsWith("Aetherhaven_Ring_") || itemId.startsWith("Aetherhaven_Necklace_");
    }

    @Nonnull
    private static Rule rule(@Nonnull PluginIdentifier subplugin, @Nonnull String itemId) {
        return new Rule(subplugin, itemId);
    }

    private record Rule(@Nonnull PluginIdentifier subplugin, @Nonnull String itemId) {}
}
