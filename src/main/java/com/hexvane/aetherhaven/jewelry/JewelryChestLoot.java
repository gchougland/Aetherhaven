package com.hexvane.aetherhaven.jewelry;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.world.WorldZoneIndex;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;

/** Chest jewelry rolls: optional glow-ring artifacts (any zone), then unidentified enchanted jewelry (zone-capped rarity). */
public final class JewelryChestLoot {
    private JewelryChestLoot() {}

    @Nonnull
    public static ItemStack rollForChest(@Nonnull ThreadLocalRandom rnd, @Nonnull AetherhavenPluginConfig cfg) {
        return rollForChest(rnd, cfg, WorldZoneIndex.UNKNOWN_DEFAULT);
    }

    @Nonnull
    public static ItemStack rollForChest(
        @Nonnull ThreadLocalRandom rnd,
        @Nonnull AetherhavenPluginConfig cfg,
        int adventureZoneIndex
    ) {
        double c = cfg.getJewelryRarityWeightCommon();
        double u = cfg.getJewelryRarityWeightUncommon();
        double r = cfg.getJewelryRarityWeightRare();
        double m = cfg.getJewelryRarityWeightMythic();
        double l = cfg.getJewelryRarityWeightLegendary();
        double total = c + u + r + m + l;
        if (total <= 0.0) {
            return rollArtifactFallback(rnd, cfg, adventureZoneIndex);
        }
        double p = rnd.nextDouble() * total;
        if (p < l) {
            return artifactStack(AetherhavenConstants.ITEM_RING_LARGE_GLOW);
        }
        p -= l;
        if (p < m) {
            return artifactStack(AetherhavenConstants.ITEM_RING_GLOW);
        }
        ItemStack stack = UnidentifiedJewelry.rollEnchantedStack(rnd);
        return JewelryMetadata.ensureRolledForLootChest(stack, rnd, cfg, adventureZoneIndex);
    }

    @Nonnull
    private static ItemStack rollArtifactFallback(
        @Nonnull ThreadLocalRandom rnd,
        @Nonnull AetherhavenPluginConfig cfg,
        int adventureZoneIndex
    ) {
        int t = rnd.nextInt(100);
        if (t < 1) {
            return artifactStack(AetherhavenConstants.ITEM_RING_LARGE_GLOW);
        }
        if (t < 5) {
            return artifactStack(AetherhavenConstants.ITEM_RING_GLOW);
        }
        ItemStack stack = UnidentifiedJewelry.rollEnchantedStack(rnd);
        return JewelryMetadata.ensureRolledForLootChest(stack, rnd, cfg, adventureZoneIndex);
    }

    @Nonnull
    private static ItemStack artifactStack(@Nonnull String itemId) {
        return new ItemStack(itemId, 1);
    }
}
