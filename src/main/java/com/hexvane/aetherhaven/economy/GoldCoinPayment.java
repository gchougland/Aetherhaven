package com.hexvane.aetherhaven.economy;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.economy.api.AetherhavenEconomy;
import com.hexvane.aetherhaven.economy.api.Balance;
import com.hexvane.aetherhaven.economy.api.GoldAccount;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Spend town treasury gold coins and/or the player's own gold. Treasury is debited first; the remainder is withdrawn
 * from the player's {@link GoldAccount}, which the active economy provider opened for them
 * ({@code AetherhavenEconomy.account(ref, store)}). The treasury is the provider's account too
 * ({@code AetherhavenEconomy.townAccount(town)}).
 *
 * <p>Features that accept town treasury and/or player gold should use this type so availability checks and spend
 * order stay consistent.
 */
public final class GoldCoinPayment {
    private GoldCoinPayment() {}

    /** How much was taken from treasury vs the player's account on a successful {@link #trySpendReturningBreakdown}. */
    public record SpendBreakdown(long fromTreasury, long fromPlayer) {}

    @Nonnull
    public static String coinItemId() {
        return AetherhavenConstants.ITEM_GOLD_COIN;
    }

    /** When treasury spend is not allowed, only the player's gold counts toward affordability. */
    public static long totalAvailable(@Nullable TownRecord town, @Nonnull GoldAccount account, boolean allowTreasuryDebit) {
        if (!allowTreasuryDebit || town == null) {
            return account.balance();
        }
        return Math.addExact(AetherhavenEconomy.townAccount(town).balance(), account.balance());
    }

    /** What {@link #totalAvailable} counts, exact, to draw or to write. */
    @Nonnull
    public static Balance available(@Nullable TownRecord town, @Nonnull GoldAccount account, boolean allowTreasuryDebit) {
        if (!allowTreasuryDebit || town == null) {
            return AetherhavenEconomy.provider().balance(account);
        }
        return AetherhavenEconomy.provider().balance(AetherhavenEconomy.townAccount(town), account);
    }

    public static boolean canAfford(
        @Nullable TownRecord town,
        @Nonnull GoldAccount account,
        long cost,
        boolean allowTreasuryDebit
    ) {
        if (cost <= 0L) {
            return true;
        }
        return totalAvailable(town, account, allowTreasuryDebit) >= cost;
    }

    /**
     * Debits treasury first, then withdraws the remainder from {@code account}. Does not persist the town; caller must
     * {@code TownManager.updateTown} after success. Rolls back treasury if the withdrawal fails.
     *
     * @param allowTreasuryDebit when false, only the player's gold is taken (treasury is never read or modified).
     */
    public static boolean trySpend(
        @Nullable TownRecord town,
        @Nonnull GoldAccount account,
        long cost,
        boolean allowTreasuryDebit
    ) {
        return trySpendReturningBreakdown(town, account, cost, allowTreasuryDebit) != null;
    }

    /**
     * Same as {@link #trySpend(TownRecord, GoldAccount, long, boolean)} but returns how much left treasury vs the
     * player so callers can {@link #refund} if a later step fails.
     */
    @Nullable
    public static SpendBreakdown trySpendReturningBreakdown(
        @Nullable TownRecord town,
        @Nonnull GoldAccount account,
        long cost,
        boolean allowTreasuryDebit
    ) {
        if (cost <= 0L) {
            return new SpendBreakdown(0L, 0L);
        }
        if (town == null || !allowTreasuryDebit) {
            return account.withdraw(cost) ? new SpendBreakdown(0L, cost) : null;
        }
        GoldAccount treasury = AetherhavenEconomy.townAccount(town);
        long treasuryBefore = treasury.balance();
        if (Math.addExact(treasuryBefore, account.balance()) < cost) {
            return null;
        }
        long fromTreasury = Math.min(treasuryBefore, cost);
        long remainder = cost - fromTreasury;
        if (fromTreasury > 0L && !treasury.withdraw(fromTreasury)) {
            return null;
        }
        if (remainder <= 0L) {
            return new SpendBreakdown(fromTreasury, 0L);
        }
        if (!account.withdraw(remainder)) {
            treasury.deposit(fromTreasury);
            return null;
        }
        return new SpendBreakdown(fromTreasury, remainder);
    }

    /**
     * Reverses a successful {@link #trySpendReturningBreakdown}: restores treasury, then deposits the player's part
     * back. If the account refuses the deposit (no room for coin items), that part goes to the treasury instead.
     */
    public static void refund(@Nullable TownRecord town, @Nonnull GoldAccount account, @Nonnull SpendBreakdown breakdown) {
        if (breakdown.fromTreasury() > 0L && town != null) {
            AetherhavenEconomy.townAccount(town).deposit(breakdown.fromTreasury());
        }
        long playerRefund = breakdown.fromPlayer();
        if (playerRefund > 0L && !account.deposit(playerRefund) && town != null) {
            AetherhavenEconomy.townAccount(town).deposit(playerRefund);
        }
    }

    /** Gives {@code amount} gold to the player (a refund, a payout). False when the account could not take it. */
    public static boolean give(@Nonnull GoldAccount account, long amount) {
        return amount <= 0L || account.deposit(amount);
    }

    /**
     * Hands an item reward to the player (a quest, a reputation unlock): gold through the account when the item is
     * the coin, the item itself otherwise. With the built-in economy the coins land in the inventory as they always
     * did, and overflow the same way when it is full.
     */
    public static void giveItemReward(
        @Nonnull Player player,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull String itemId,
        int count
    ) {
        if (AetherhavenConstants.ITEM_GOLD_COIN.equals(itemId)) {
            GoldAccount account = AetherhavenEconomy.account(ref, store);
            if (account != null && give(account, count)) {
                return;
            }
        }
        player.giveItem(new ItemStack(itemId, count), ref, store);
    }
}
