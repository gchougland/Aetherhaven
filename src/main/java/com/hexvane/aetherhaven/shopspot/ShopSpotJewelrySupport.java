package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.jewelry.JewelryItemIds;
import com.hexvane.aetherhaven.jewelry.JewelryMetadata;
import com.hexvane.aetherhaven.jewelry.JewelryRarity;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Shop spot jewelry: appraised stock rolls, persisted metadata, and rarity-scaled catalog pricing. */
public final class ShopSpotJewelrySupport {
    private ShopSpotJewelrySupport() {}

    public static boolean isJewelryListing(@Nullable String itemId) {
        return JewelryItemIds.isJewelry(itemId);
    }

    /** Rolls appraised jewelry for NPC shop stock and stores metadata on the record. */
    public static void prepareNpcJewelryListing(
        @Nonnull ShopSpotRecord record,
        @Nonnull String itemId,
        @Nonnull AetherhavenPlugin plugin
    ) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        AetherhavenPluginConfig cfg = plugin.getConfig().get();
        JewelryRarity rarity = JewelryRarity.roll(rnd, cfg);
        ItemStack stack = JewelryMetadata.rollCraftedAppraised(itemId, rarity, rnd);
        record.setJewelryMetaJson(JewelryMetadata.exportJewelryRootJson(stack));
    }

    /** Copies appraised jewelry metadata from a player hand stack onto the listing record. */
    public static boolean capturePlayerJewelryListing(@Nonnull ShopSpotRecord record, @Nonnull ItemStack hand) {
        if (!JewelryItemIds.isJewelry(hand.getItemId())) {
            record.setJewelryMetaJson(null);
            return true;
        }
        if (!JewelryMetadata.hasJewelryMeta(hand)) {
            return false;
        }
        if (!JewelryMetadata.isAppraised(hand)) {
            return false;
        }
        String json = JewelryMetadata.exportJewelryRootJson(hand);
        if (json == null || json.isBlank()) {
            return false;
        }
        record.setJewelryMetaJson(json);
        return true;
    }

    public static void clearJewelryListing(@Nonnull ShopSpotRecord record) {
        record.setJewelryMetaJson(null);
    }

    /**
     * The stack a stall hands over. A listing that kept its whole stack gives exactly that stack back. Only rolled
     * NPC stock and listings from older saves are rebuilt from the item id.
     */
    @Nonnull
    public static ItemStack buildListingStack(@Nonnull String itemId, int quantity, @Nonnull ShopSpotRecord record) {
        ItemStack whole = itemId.equals(record.getItemId()) ? ShopListing.stack(record, quantity) : null;
        if (whole != null) {
            return whole;
        }
        ItemStack stack = new ItemStack(itemId, quantity);
        String meta = record.getJewelryMetaJson();
        if (meta != null && !meta.isBlank()) {
            stack = JewelryMetadata.applyJewelryRootJson(stack, meta);
        } else if (JewelryItemIds.isJewelry(itemId)) {
            stack =
                JewelryMetadata.rollCraftedAppraised(
                    itemId,
                    JewelryRarity.COMMON,
                    ThreadLocalRandom.current()
                );
        }
        return stack;
    }

    @Nonnull
    public static ItemStack buildDisplayStack(@Nonnull String itemId, @Nonnull ShopSpotRecord record) {
        ItemStack stack = buildListingStack(itemId, 1, record);
        stack.setOverrideDroppedItemAnimation(true);
        return stack;
    }

    @Nullable
    public static JewelryRarity listingRarity(@Nonnull ShopSpotRecord record) {
        JewelryRarity fromJson = JewelryMetadata.readRarityFromRootJson(record.getJewelryMetaJson());
        if (fromJson != null) {
            return fromJson;
        }
        String itemId = record.getItemId();
        if (!JewelryItemIds.isJewelry(itemId)) {
            return null;
        }
        return JewelryRarity.COMMON;
    }

    public static long scaleCatalogGold(
        @Nonnull AetherhavenPlugin plugin,
        @Nullable String itemId,
        @Nonnull ShopSpotRecord record,
        long catalogGoldPerBatch
    ) {
        if (catalogGoldPerBatch <= 0L || !JewelryItemIds.isJewelry(itemId)) {
            return catalogGoldPerBatch;
        }
        JewelryRarity rarity = listingRarity(record);
        if (rarity == null) {
            return catalogGoldPerBatch;
        }
        double mult = plugin.getConfig().get().getJewelryShopPriceMultiplier(rarity);
        if (mult <= 0.0) {
            return catalogGoldPerBatch;
        }
        return Math.max(1L, Math.round(catalogGoldPerBatch * mult));
    }

    @Nonnull
    public static String listingDisplaySignature(@Nonnull String itemId, @Nonnull ShopSpotRecord record) {
        String meta = record.getJewelryMetaJson();
        return itemId + "|" + (meta != null ? meta.hashCode() : 0) + "|" + ShopListing.appearance(record);
    }
}
