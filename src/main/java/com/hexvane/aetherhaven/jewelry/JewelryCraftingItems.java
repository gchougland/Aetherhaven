package com.hexvane.aetherhaven.jewelry;

import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Vanilla gem item ids and craft output item id construction. */
public final class JewelryCraftingItems {
    public static final String GEM_ZEPHYR = "Rock_Gem_Zephyr";
    public static final String GEM_TOPAZ = "Rock_Gem_Topaz";
    public static final String GEM_EMERALD = "Rock_Gem_Emerald";
    public static final String GEM_DIAMOND = "Rock_Gem_Diamond";
    public static final String GEM_SAPPHIRE = "Rock_Gem_Sapphire";
    public static final String GEM_RUBY = "Rock_Gem_Ruby";
    public static final String GEM_VOIDSTONE = "Rock_Gem_Voidstone";

    public static final List<String> ALL_GEM_ITEM_IDS = List.of(
        GEM_ZEPHYR,
        GEM_TOPAZ,
        GEM_EMERALD,
        GEM_DIAMOND,
        GEM_SAPPHIRE,
        GEM_RUBY,
        GEM_VOIDSTONE
    );

    private JewelryCraftingItems() {}

    @Nullable
    public static JewelryGem gemFromRockItemId(@Nullable String rockGemItemId) {
        if (rockGemItemId == null || rockGemItemId.isBlank()) {
            return null;
        }
        int u = rockGemItemId.lastIndexOf('_');
        if (u < 0 || u >= rockGemItemId.length() - 1) {
            return null;
        }
        String suffix = rockGemItemId.substring(u + 1);
        try {
            return JewelryGem.valueOf(suffix.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nonnull
    public static String outputItemId(boolean necklace, boolean gold, @Nonnull JewelryGem gem) {
        String kind = necklace ? "Aetherhaven_Necklace_" : "Aetherhaven_Ring_";
        String metal = gold ? "Gold_" : "Silver_";
        return kind + metal + gem.name().charAt(0) + gem.name().substring(1).toLowerCase();
    }
}
