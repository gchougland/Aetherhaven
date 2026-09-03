package com.hexvane.aetherhaven.jewelry;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ItemQuality;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nonnull;

/** Resolves item-quality index (tooltip border, slot art) from rolled {@link JewelryRarity}. */
public final class JewelryItemQualityIndex {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final AtomicBoolean LOGGED_MISSING_QUALITY = new AtomicBoolean();

    private JewelryItemQualityIndex() {}

    public static int forRarity(@Nonnull JewelryRarity rarity, @Nonnull String baseItemId) {
        String qualityId = rarity.itemQualityId();
        int index = ItemQuality.getAssetMap().getIndex(qualityId);
        if (index >= 0) {
            return index;
        }
        if (LOGGED_MISSING_QUALITY.compareAndSet(false, true)) {
            LOGGER
                .atWarning()
                .log(
                    "Jewelry rarity border: ItemQuality '%s' not loaded yet; falling back until qualities are ready.",
                    qualityId
                );
        }
        Item item = Item.getAssetMap().getAsset(baseItemId);
        return item != null ? item.getQualityIndex() : ItemQuality.DEFAULT_INDEX;
    }

    /** Resolve the item-quality index for a jewelry stack. */
    public static int forStack(@Nonnull ItemStack stack) {
        if (ItemStack.isEmpty(stack) || !JewelryItemIds.isJewelry(stack.getItemId())) {
            return stack.getItem().getQualityIndex();
        }
        String baseId = JewelryVirtualItemRegistry.getBaseItemId(stack.getItemId());
        if (baseId == null) {
            baseId = stack.getItemId();
        }
        JewelryRarity r = JewelryMetadata.readRarity(stack);
        if (r == null) {
            Item item = Item.getAssetMap().getAsset(baseId);
            return item != null ? item.getQualityIndex() : stack.getItem().getQualityIndex();
        }
        return forRarity(r, baseId);
    }
}
