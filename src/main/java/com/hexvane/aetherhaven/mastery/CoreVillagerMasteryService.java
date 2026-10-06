package com.hexvane.aetherhaven.mastery;

import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import java.util.*;

public final class CoreVillagerMasteryService {
    private CoreVillagerMasteryService() {}
    private static final Set<String> KINDS = Set.of("miner", "logger", "farmer", "rancher", "druid", "alchemist");
    public static boolean supports(String kind) { return KINDS.contains(kind == null ? "" : kind); }
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
    public static String equip(CoreVillagerMastery progress, String kind, ItemContainer source, short slot,
                               ItemStack expected, ItemStack expectedTool) {
        if (source == null || slot < 0 || slot >= source.getCapacity() || ItemStack.isEmpty(expected)
            || expected.getQuantity() != 1 || !expected.equals(source.getItemStack(slot))
            || !Objects.equals(expectedTool, progress.tool())) return "Equipment changed. Choose a tool again.";
        if (toolTier(kind, expected.getItemId()) < 0) return "Choose the correct kind of tool.";
        if (expected.isBroken()) return "Repair this tool before equipping it.";
        // Validate serialization before the inventory transaction.
        com.hexvane.aetherhaven.townsfolk.TownsfolkProgress.encodeProfessionKit(expected);
        if (!source.replaceItemStackInSlot(slot, expected, expectedTool).succeeded()) return "That tool moved. Choose it again.";
        progress.setTool(expected);
        return null;
    }
}
