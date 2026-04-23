package com.hexvane.aetherhaven.jewelry;

import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import javax.annotation.Nonnull;

/**
 * Resolves the Hytale item-quality index (tooltip border, slot art, name tier) for a jewelry stack from rolled
 * {@link JewelryRarity} in instance metadata, aligned with {@link JewelryRarity#itemQualityId()} and
 * {@link com.hypixel.hytale.server.core.asset.type.item.config.Item} static quality as fallback.
 */
public final class JewelryItemQualityIndex {

    private JewelryItemQualityIndex() {}

    /**
     * Quality index for DynamicTooltipsLib / {@code ItemVisualOverrides#qualityIndex}; virtual items use this to match
     * rolled jewelry tier instead of the item JSON’s static {@code Quality} (e.g. all rings default to {@code Rare}).
     */
    public static int forStack(@Nonnull ItemStack stack) {
        if (ItemStack.isEmpty(stack) || !JewelryItemIds.isJewelry(stack.getItemId())) {
            return stack.getItem().getQualityIndex();
        }
        JewelryRarity r = JewelryMetadata.readRarity(stack);
        if (r == null) {
            return stack.getItem().getQualityIndex();
        }
        return ItemQuality.getAssetMap().getIndexOrDefault(r.itemQualityId(), stack.getItem().getQualityIndex());
    }
}
