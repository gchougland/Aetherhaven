package com.hexvane.aetherhaven.festival.wintertide;

import com.hexvane.aetherhaven.jewelry.JewelryItemIds;
import com.hexvane.aetherhaven.jewelry.JewelryMetadata;
import com.hexvane.aetherhaven.jewelry.JewelryRarity;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** What a villager gives a player during Wintertide. */
public final class WintertideGifts {
    public record Stack(@Nonnull String itemId, int count) {}

    private static final String[] MITHRIL_WEAPONS = {
        "Weapon_Sword_Mithril",
        "Weapon_Longsword_Mithril",
        "Weapon_Battleaxe_Mithril",
        "Weapon_Daggers_Mithril",
        "Weapon_Mace_Mithril",
        "Weapon_Spear_Mithril",
        "Weapon_Shortbow_Mithril",
        "Weapon_Shield_Mithril",
        "Weapon_Club_Mithril",
        "Weapon_Axe_Mithril",
        "Weapon_Staff_Mithril"
    };

    private static final String[] MITHRIL_WEAPONS_AND_TOOLS = {
        "Weapon_Sword_Mithril",
        "Weapon_Longsword_Mithril",
        "Weapon_Battleaxe_Mithril",
        "Weapon_Daggers_Mithril",
        "Weapon_Mace_Mithril",
        "Weapon_Spear_Mithril",
        "Weapon_Shortbow_Mithril",
        "Weapon_Shield_Mithril",
        "Weapon_Club_Mithril",
        "Weapon_Axe_Mithril",
        "Weapon_Staff_Mithril",
        "Tool_Pickaxe_Mithril",
        "Tool_Hatchet_Mithril"
    };

    private static final String[] CRYSTALS = {
        "Ingredient_Crystal_Blue",
        "Ingredient_Crystal_Cyan",
        "Ingredient_Crystal_Green",
        "Ingredient_Crystal_Pink",
        "Ingredient_Crystal_Purple",
        "Ingredient_Crystal_Red",
        "Ingredient_Crystal_White",
        "Ingredient_Crystal_Yellow"
    };

    private static final String[] ORCHIDS = {
        "Plant_Flower_Orchid_Blue",
        "Plant_Flower_Orchid_Cyan",
        "Plant_Flower_Orchid_Orange",
        "Plant_Flower_Orchid_Pink",
        "Plant_Flower_Orchid_Purple",
        "Plant_Flower_Orchid_Red",
        "Plant_Flower_Orchid_White",
        "Plant_Flower_Orchid_Yellow"
    };

    private static final String[] NECKLACES = {
        "Aetherhaven_Necklace_Gold_Zephyr",
        "Aetherhaven_Necklace_Gold_Topaz",
        "Aetherhaven_Necklace_Gold_Emerald",
        "Aetherhaven_Necklace_Gold_Diamond",
        "Aetherhaven_Necklace_Gold_Sapphire",
        "Aetherhaven_Necklace_Gold_Ruby",
        "Aetherhaven_Necklace_Gold_Voidstone",
        "Aetherhaven_Necklace_Silver_Zephyr",
        "Aetherhaven_Necklace_Silver_Topaz",
        "Aetherhaven_Necklace_Silver_Emerald",
        "Aetherhaven_Necklace_Silver_Diamond",
        "Aetherhaven_Necklace_Silver_Sapphire",
        "Aetherhaven_Necklace_Silver_Ruby",
        "Aetherhaven_Necklace_Silver_Voidstone"
    };

    private WintertideGifts() {}

    @Nonnull
    public static List<Stack> pick(
        @Nullable String villagerKind,
        @Nullable VillagerDefinition def,
        @Nonnull Random rnd
    ) {
        String kind = villagerKind != null ? villagerKind.trim().toLowerCase(Locale.ROOT) : "";
        List<Stack> configured = configuredFor(kind, rnd);
        if (!configured.isEmpty()) {
            return configured;
        }
        if (def != null) {
            List<String> loves = def.getGiftLoves();
            if (!loves.isEmpty()) {
                String id = loves.get(rnd.nextInt(loves.size()));
                if (id != null && !id.isBlank()) {
                    return List.of(new Stack(id.trim(), 1));
                }
            }
        }
        String epic = randomEpicItem(rnd);
        if (epic != null) {
            return List.of(new Stack(epic, 1));
        }
        return List.of(new Stack("Food_Candy_Cane", 10));
    }

    @Nonnull
    static List<Stack> configuredFor(@Nonnull String kind, @Nonnull Random rnd) {
        return switch (kind) {
            case "miner" -> List.of(new Stack("Ore_Mithril", 10));
            case "blacksmith" -> List.of(new Stack(pick(MITHRIL_WEAPONS_AND_TOOLS, rnd), 1));
            case "rancher" -> List.of(new Stack("Ingredient_Hide_Storm", 10));
            case "logger" -> List.of(new Stack("Plant_Seeds_Potato_Eternal", 10));
            case "farmer" -> List.of(new Stack("Plant_Seeds_Wheat_Eternal", 10));
            case "chef" -> List.of(new Stack("Food_Pie_Apple", 8), new Stack("Food_Pie_Meat", 8));
            case "merchant" -> List.of(new Stack(pick(NECKLACES, rnd), 1));
            case "elder" -> List.of(new Stack("Aetherhaven_Heartberry", 1));
            case "innkeeper" -> List.of(new Stack("Food_Pie_Apple", 8), new Stack("Food_Pie_Pumpkin", 8));
            case "priestess" -> List.of(new Stack("Potion_Health_Greater", 5));
            case "bard" -> mixed(CRYSTALS, 20, rnd);
            case "crystal_keeper" -> List.of(new Stack("Plant_Sapling_Crystal", 3));
            case "florist" -> mixed(ORCHIDS, 16, rnd);
            case "builder" -> List.of(
                new Stack("Metal_Iron", 16),
                new Stack("Metal_Copper", 16),
                new Stack("Metal_Bronze", 16),
                new Stack("Metal_Zinc", 16)
            );
            case "pyrotechnic" -> List.of(new Stack("Aetherhaven_Mining_Bomb", 50));
            case "clown" -> List.of(new Stack("Deco_Kweebec_Plush", 1), new Stack("Food_Candy_Cane", 10));
            case "guild_master" -> List.of(new Stack(pick(MITHRIL_WEAPONS, rnd), 1));
            default -> List.of();
        };
    }

    @Nonnull
    private static List<Stack> mixed(@Nonnull String[] ids, int count, @Nonnull Random rnd) {
        List<Stack> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            out.add(new Stack(pick(ids, rnd), 1));
        }
        return List.copyOf(out);
    }

    @Nonnull
    public static List<ItemStack> toItemStacks(@Nonnull List<Stack> stacks) {
        List<ItemStack> out = new ArrayList<>();
        for (Stack stack : stacks) {
            out.add(toItemStack(stack));
        }
        return List.copyOf(out);
    }

    @Nonnull
    static ItemStack toItemStack(@Nonnull Stack stack) {
        if (JewelryItemIds.isJewelry(stack.itemId())) {
            return JewelryMetadata.rollCraftedAppraised(
                stack.itemId(),
                JewelryRarity.MYTHIC,
                ThreadLocalRandom.current()
            );
        }
        return new ItemStack(stack.itemId(), Math.max(1, stack.count()));
    }

    @Nonnull
    private static String pick(@Nonnull String[] ids, @Nonnull Random rnd) {
        return ids[rnd.nextInt(ids.length)];
    }

    @Nullable
    static String randomEpicItem(@Nonnull Random rnd) {
        int epicIndex = ItemQuality.getAssetMap().getIndexOrDefault("Epic", Integer.MIN_VALUE);
        if (epicIndex == Integer.MIN_VALUE) {
            return null;
        }
        List<String> ids = new ArrayList<>();
        for (String id : Item.getAssetMap().getAssetMap().keySet()) {
            if (id == null || id.isBlank()) {
                continue;
            }
            String lower = id.toLowerCase(Locale.ROOT);
            if (lower.contains("debug") || lower.contains("developer") || lower.contains("template")) {
                continue;
            }
            Item item = Item.getAssetMap().getAsset(id);
            if (item != null && item.getQualityIndex() == epicIndex) {
                ids.add(id);
            }
        }
        if (ids.isEmpty()) {
            return null;
        }
        return ids.get(rnd.nextInt(ids.size()));
    }
}
