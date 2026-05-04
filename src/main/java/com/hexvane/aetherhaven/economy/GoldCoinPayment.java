package com.hexvane.aetherhaven.economy;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Spend town treasury gold coins and/or the same item id from the player inventory. Treasury is debited first; the
 * remainder is removed from {@code inventory} stacks in chunks.
 *
 * <p>Features that accept town treasury and/or player gold coins should use this type so availability checks and spend
 * order stay consistent.
 */
public final class GoldCoinPayment {
    private GoldCoinPayment() {}

    /** How much was taken from treasury vs inventory on a successful {@link #trySpendReturningBreakdown}. */
    public record SpendBreakdown(long fromTreasury, long fromInventory) {}

    @Nonnull
    public static String coinItemId() {
        return AetherhavenConstants.ITEM_GOLD_COIN;
    }

    public static long totalAvailable(@Nullable TownRecord town, @Nonnull CombinedItemContainer inventory) {
        long treas = town == null ? 0L : town.getTreasuryGoldCoinCount();
        return Math.addExact(treas, InventoryMaterials.count(inventory, coinItemId()));
    }

    /** When treasury spend is not allowed, only inventory coins count toward affordability. */
    public static long totalAvailable(
        @Nullable TownRecord town,
        @Nonnull CombinedItemContainer inventory,
        boolean allowTreasuryDebit
    ) {
        if (!allowTreasuryDebit || town == null) {
            return InventoryMaterials.count(inventory, coinItemId());
        }
        return totalAvailable(town, inventory);
    }

    public static boolean canAfford(@Nullable TownRecord town, @Nonnull CombinedItemContainer inventory, long cost) {
        if (cost <= 0L) {
            return true;
        }
        return totalAvailable(town, inventory) >= cost;
    }

    public static boolean canAfford(
        @Nullable TownRecord town,
        @Nonnull CombinedItemContainer inventory,
        long cost,
        boolean allowTreasuryDebit
    ) {
        if (cost <= 0L) {
            return true;
        }
        return totalAvailable(town, inventory, allowTreasuryDebit) >= cost;
    }

    /**
     * Debits treasury first, then removes coin stacks from {@code inventory}. Does not persist the town; caller must
     * {@code TownManager.updateTown} after success. Rolls back treasury if inventory removal fails partway.
     */
    public static boolean trySpend(@Nullable TownRecord town, @Nonnull CombinedItemContainer inventory, long cost) {
        return trySpendReturningBreakdown(town, inventory, cost, true) != null;
    }

    /**
     * @param allowTreasuryDebit when false, only removes coins from {@code inventory} (treasury is never read or
     *     modified).
     */
    public static boolean trySpend(
        @Nullable TownRecord town,
        @Nonnull CombinedItemContainer inventory,
        long cost,
        boolean allowTreasuryDebit
    ) {
        return trySpendReturningBreakdown(town, inventory, cost, allowTreasuryDebit) != null;
    }

    /**
     * Same as {@link #trySpend(TownRecord, CombinedItemContainer, long, boolean)} but returns how much left treasury vs
     * inventory so callers can {@link #refund} if a later step fails.
     */
    @Nullable
    public static SpendBreakdown trySpendReturningBreakdown(
        @Nullable TownRecord town,
        @Nonnull CombinedItemContainer inventory,
        long cost,
        boolean allowTreasuryDebit
    ) {
        if (cost <= 0L) {
            return new SpendBreakdown(0L, 0L);
        }
        if (town == null || !allowTreasuryDebit) {
            if (!spendInventoryOnly(inventory, cost)) {
                return null;
            }
            return new SpendBreakdown(0L, cost);
        }
        long treasuryBefore = town.getTreasuryGoldCoinCount();
        long invCount = InventoryMaterials.count(inventory, coinItemId());
        if (Math.addExact(treasuryBefore, invCount) < cost) {
            return null;
        }
        long fromTreasury = Math.min(treasuryBefore, cost);
        long remainder = cost - fromTreasury;
        if (fromTreasury > 0L) {
            town.addTreasuryGoldCoins(-fromTreasury);
        }
        if (remainder <= 0L) {
            return new SpendBreakdown(fromTreasury, 0L);
        }
        long fromInventory = 0L;
        long left = remainder;
        while (left > 0L) {
            int haveNow = InventoryMaterials.count(inventory, coinItemId());
            if (haveNow <= 0) {
                town.setTreasuryGoldCoinCount(treasuryBefore);
                return null;
            }
            int chunk = (int) Math.min(left, Math.min(haveNow, Integer.MAX_VALUE));
            ItemStackTransaction tx = inventory.removeItemStack(new ItemStack(coinItemId(), chunk));
            if (!tx.succeeded()) {
                town.setTreasuryGoldCoinCount(treasuryBefore);
                return null;
            }
            fromInventory += chunk;
            left -= chunk;
        }
        return new SpendBreakdown(fromTreasury, fromInventory);
    }

    /**
     * Reverses a successful {@link #trySpendReturningBreakdown}: restores treasury, then returns inventory coins via
     * {@link Player#giveItem}.
     */
    public static void refund(
        @Nullable TownRecord town,
        @Nonnull Player player,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull SpendBreakdown breakdown
    ) {
        if (breakdown.fromTreasury() > 0L && town != null) {
            town.addTreasuryGoldCoins(breakdown.fromTreasury());
        }
        long invRefund = breakdown.fromInventory();
        while (invRefund > 0L) {
            int chunk = (int) Math.min(invRefund, Integer.MAX_VALUE);
            ItemStackTransaction tx = player.giveItem(new ItemStack(coinItemId(), chunk), ref, store);
            if (!tx.succeeded()) {
                if (town != null) {
                    town.addTreasuryGoldCoins(invRefund);
                }
                break;
            }
            invRefund -= chunk;
        }
    }

    private static boolean spendInventoryOnly(@Nonnull CombinedItemContainer inventory, long cost) {
        long left = cost;
        while (left > 0L) {
            int haveNow = InventoryMaterials.count(inventory, coinItemId());
            if (haveNow <= 0) {
                return false;
            }
            int chunk = (int) Math.min(left, Math.min(haveNow, Integer.MAX_VALUE));
            ItemStackTransaction tx = inventory.removeItemStack(new ItemStack(coinItemId(), chunk));
            if (!tx.succeeded()) {
                return false;
            }
            left -= chunk;
        }
        return true;
    }
}
