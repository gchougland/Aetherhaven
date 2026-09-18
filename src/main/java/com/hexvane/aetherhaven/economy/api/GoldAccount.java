package com.hexvane.aetherhaven.economy.api;

/**
 * A balance Aetherhaven spends from or pays into (a player's gold, a town treasury, a shop safe), counted in
 * Aetherhaven gold coins.
 *
 * <p>Amounts are never negative. A provider whose own unit differs converts, and rounds {@link #balance()} down.
 * Calls happen on the world thread and never concurrently for the same account.
 */
public interface GoldAccount {
    /** Coins that can be spent right now. */
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
