package com.hexvane.aetherhaven.economy.api;

/**
 * One player's spendable gold, counted in Aetherhaven gold coins.
 *
 * <p>Amounts are never negative. A provider whose own unit differs converts, and rounds {@link #balance()} down.
 * Calls happen on the world thread and never concurrently for the same player.
 */
public interface GoldAccount {
    /** Coins the player can spend right now. */
    long balance();

    /**
     * Takes {@code amount} coins, all or nothing.
     *
     * @return false when the balance does not cover it; nothing moved.
     */
    boolean withdraw(long amount);

    /**
     * Gives {@code amount} coins.
     *
     * @return false when they could not be given (no room for coin items, for instance); nothing moved.
     */
    boolean deposit(long amount);
}
