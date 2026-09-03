package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.difficulty.DifficultyResolver;
import com.hexvane.aetherhaven.difficulty.TownDifficultySettings;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.TownRecord;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class ShopSpotPricing {
    private ShopSpotPricing() {}

    @Nonnull
    public static ShopPriceEntry catalogEntry(@Nonnull AetherhavenPlugin plugin, @Nonnull String itemId) {
        return plugin.getShopPriceCatalog().getEntry(itemId);
    }

    /**
     * Gold per purchase lot after difficulty buy multiplier, jewelry scaling, and (for player spots) sell
     * margin percent.
     */
    public static long goldPerBatch(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull ShopSpotRecord record,
        @Nonnull String itemId
    ) {
        ShopPriceEntry entry = catalogEntry(plugin, itemId);
        long catalogGold = entry.getGoldPerBatch();
        catalogGold = ShopSpotJewelrySupport.scaleCatalogGold(plugin, itemId, record, catalogGold);
        TownDifficultySettings difficulty = DifficultyResolver.effectiveForTown(resolveTown(record));
        catalogGold = applyBuyPriceMultiplier(catalogGold, difficulty);
        if (!record.isPlayerControlled()) {
            return catalogGold;
        }
        return playerListingGoldPerBatch(catalogGold, difficulty);
    }

    /** Uses plugin config percent (journal / config.json). Prefer the difficulty overload for shops. */
    public static long playerListingGoldPerBatch(long catalogGoldPerBatch, @Nonnull AetherhavenPluginConfig cfg) {
        if (catalogGoldPerBatch <= 0L) {
            return 0L;
        }
        int percent = cfg.getShopSpotPlayerListingPricePercent();
        long scaled = (catalogGoldPerBatch * (long) percent) / 100L;
        return Math.max(1L, scaled);
    }

    public static long playerListingGoldPerBatch(
        long catalogGoldPerBatch,
        @Nonnull TownDifficultySettings difficulty
    ) {
        if (catalogGoldPerBatch <= 0L) {
            return 0L;
        }
        int percent = TownDifficultySettings.clampSellProfitMarginPercent(difficulty.getSellProfitMarginPercent());
        long scaled = (catalogGoldPerBatch * (long) percent) / 100L;
        return Math.max(1L, scaled);
    }

    public static long totalCost(long goldPerBatch, int batchCount) {
        return goldPerBatch * Math.max(0, batchCount);
    }

    private static long applyBuyPriceMultiplier(long catalogGold, @Nonnull TownDifficultySettings difficulty) {
        if (catalogGold <= 0L) {
            return 0L;
        }
        double mult = difficulty.getBuyPriceMultiplier();
        if (Double.isNaN(mult) || mult <= 0.0 || Math.abs(mult - 1.0) < 0.0001) {
            return catalogGold;
        }
        return Math.max(1L, Math.round(catalogGold * mult));
    }

    @Nullable
    private static TownRecord resolveTown(@Nonnull ShopSpotRecord record) {
        return AetherhavenWorldRegistries.getTownAcrossWorlds(record.getTownId(), null);
    }
}
