package com.hexvane.aetherhaven.economy;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import javax.annotation.Nonnull;

/**
 * Spend town treasury gold coins and/or the same item id from the player inventory. Treasury is debited first; the
 * remainder is removed from {@code inventory} stacks in chunks.
 *
 * <p>Features that accept town treasury and/or player gold coins should use this type so availability checks and spend
 * order stay consistent.
 */
public final class GoldCoinPayment {
    private GoldCoinPayment() {}

    @Nonnull
    public static String coinItemId() {
        return AetherhavenConstants.ITEM_GOLD_COIN;
    }

    public static long totalAvailable(@Nonnull TownRecord town, @Nonnull CombinedItemContainer inventory) {
        return Math.addExact(town.getTreasuryGoldCoinCount(), InventoryMaterials.count(inventory, coinItemId()));
    }

    public static boolean canAfford(@Nonnull TownRecord town, @Nonnull CombinedItemContainer inventory, long cost) {
        if (cost <= 0L) {
            return true;
        }
        return totalAvailable(town, inventory) >= cost;
    }

    /**
     * Debits treasury first, then removes coin stacks from {@code inventory}. Does not persist the town; caller must
     * {@code TownManager.updateTown} after success. Rolls back treasury if inventory removal fails partway.
     */
    public static boolean trySpend(@Nonnull TownRecord town, @Nonnull CombinedItemContainer inventory, long cost) {
        if (cost <= 0L) {
            return true;
        }
        long treasuryBefore = town.getTreasuryGoldCoinCount();
        long invCount = InventoryMaterials.count(inventory, coinItemId());
        if (Math.addExact(treasuryBefore, invCount) < cost) {
            return false;
        }
        long fromTreasury = Math.min(treasuryBefore, cost);
        long remainder = cost - fromTreasury;
        if (fromTreasury > 0L) {
            town.addTreasuryGoldCoins(-fromTreasury);
        }
        if (remainder <= 0L) {
            return true;
        }
        long left = remainder;
        while (left > 0L) {
            int haveNow = InventoryMaterials.count(inventory, coinItemId());
            if (haveNow <= 0) {
                town.setTreasuryGoldCoinCount(treasuryBefore);
                return false;
            }
            int chunk = (int) Math.min(left, Math.min(haveNow, Integer.MAX_VALUE));
            ItemStackTransaction tx = inventory.removeItemStack(new ItemStack(coinItemId(), chunk));
            if (!tx.succeeded()) {
                town.setTreasuryGoldCoinCount(treasuryBefore);
                return false;
            }
            left -= chunk;
        }
        return true;
    }
}
