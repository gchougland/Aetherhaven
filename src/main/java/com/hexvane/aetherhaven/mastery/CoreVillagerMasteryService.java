package com.hexvane.aetherhaven.mastery;

import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import java.util.*;

public final class CoreVillagerMasteryService {
    private CoreVillagerMasteryService() {}
    private static final Set<String> KINDS = Set.of("miner", "logger", "farmer", "rancher", "druid", "alchemist", BuilderMastery.KIND);
    public static boolean supports(String kind) { return KINDS.contains(kind == null ? "" : kind); }
    /**
     * Mastery XP for each item a barn animal makes. Field, mine and grove work earn one XP for each item put away,
     * and those workers put away around a hundred items a day. A pen makes about ten, so each one counts for more.
     */
    public static final int RANCH_XP_PER_ITEM = 5;
    /** XP for what the herd made in one update. At most a hundred items count at once. */
    public static long ranchXp(long itemsMade) {
        return itemsMade <= 0 ? 0 : Math.min(100, itemsMade) * RANCH_XP_PER_ITEM;
    }
    public static CoreVillagerMastery progress(TownRecord town, String kind) {
        if (!supports(kind)) throw new IllegalArgumentException("This villager has no Mastery");
        return town.getCoreVillagerMastery().computeIfAbsent(kind, ignored -> new CoreVillagerMastery());
    }
    public static int level(TownRecord town, String kind) {
        if (town == null || !supports(kind)) return 1;
        var progress = town.getCoreVillagerMastery().get(kind);
        return progress == null ? 1 : progress.level();
    }
    public static String toolFamily(String kind) {
        return "miner".equals(kind) ? "Tool_Pickaxe_" : "logger".equals(kind) ? "Tool_Hatchet_" : null;
    }
    public static String toolId(TownRecord town, String kind) {
        String family = toolFamily(kind);
        if (family == null) return null;
        var saved = town.getCoreVillagerMastery().get(kind);
        var tool = saved == null ? null : saved.tool();
        return ItemStack.isEmpty(tool) ? family + "Crude" : tool.getItemId();
    }
    public static int toolTier(String kind, String item) {
        String family = toolFamily(kind);
        if (family == null || item == null || !item.startsWith(family)) return -1;
        return switch (item.substring(family.length())) {
            case "Crude" -> 0;
            case "Copper" -> 1;
            case "Iron" -> 2;
            case "Thorium", "Cobalt" -> 3;
            case "Adamantite" -> 4;
            case "Mithril" -> 5;
            default -> -1;
        };
    }
    /** Exact-slot exchange preserves durability/metadata and works with a full inventory. Starter tools are not minted. */
    public static final String TOOL_GONE = "You no longer carry that tool. Nothing was changed.";
    /**
     * Swaps the chosen tool with whatever the villager holds at this moment. The tool is the exact item the press
     * named, found in the slot it was drawn in or wherever it sits in the pack now. A tool the villager was handed
     * since the page was drawn simply comes back in its place, so nothing is lost and nothing is refused for it.
     */
    public static String equip(CoreVillagerMastery progress, String kind, ItemContainer source, short slot, ItemStack expected) {
        short at = com.hexvane.aetherhaven.equipment.ProfessionKitService.locate(source, slot, expected);
        if (at < 0) return TOOL_GONE;
        if (toolTier(kind, expected.getItemId()) < 0) return "Choose the correct kind of tool.";
        if (expected.isBroken()) return "Repair this tool before equipping it.";
        // Validate serialization before the inventory transaction.
        com.hexvane.aetherhaven.townsfolk.TownsfolkProgress.encodeProfessionKit(expected);
        if (!source.replaceItemStackInSlot(at, expected, progress.tool()).succeeded()) return TOOL_GONE;
        progress.setTool(expected);
        return null;
    }
}
