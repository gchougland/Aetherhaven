package com.hexvane.aetherhaven.economy.api;

import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Where gold lives: a player's, a town treasury's, a shop safe's. Aetherhaven ships {@code ItemCoinEconomy} (the gold
 * coin item, counts in {@code TownRecord}); an economy mod registers its own through
 * {@link AetherhavenEconomy#register}.
 *
 * <p>Every {@code long} is in Aetherhaven gold coins, never negative, and stands for something Aetherhaven owns: a
 * price, a loot roll, the tithe, a refund. A provider with another unit converts those. What is stored (a balance)
 * is the provider's, in its own unit, shown as it is ({@link #balance}), and moving a typed amount between two
 * balances is the provider's too ({@link #transfer}): Aetherhaven never holds a converted amount. The provider
 * persists its balances itself, Aetherhaven persists nothing on its behalf.
 */
public interface EconomyProvider {
    /** Stable id for logs, e.g. {@code "aetherhaven:coins"}. */
    @Nonnull
    String id();

    /** The player's account, or null when the entity is not a player. */
    @Nullable
    GoldAccount account(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store);

    /**
     * The town's treasury. The built-in economy keeps it in the record's coin count. Reached through
     * {@link AetherhavenEconomy#townAccount}, which moves a count left by the built-in economy into this account first.
     */
    @Nonnull
    GoldAccount townAccount(@Nonnull TownRecord town);

    /**
     * A player's shop safe in that town, credited by their sales. The built-in economy keeps it in the record's
     * per-player count. Reached through {@link AetherhavenEconomy#shopSafe}, same migration.
     */
    @Nonnull
    GoldAccount shopSafe(@Nonnull TownRecord town, @Nonnull UUID player);

    /**
     * Items Aetherhaven hands out for {@code amount} gold from {@code source}: the loot of a dungeon chest or a broken
     * pot, the output of a recipe that gives coins. {@code itemId} is what the server configured or wrote for that
     * source. The built-in economy returns that item split by its max stack; a provider may return its own items
     * instead (one token worth the amount), or nothing, source by source. No player is involved: chests are filled by
     * chunk systems and recipes are rewritten as they load. Each stack returned to a chest is spread over its free
     * slots in random piles, so a token of quantity one lands whole.
     *
     * @return stacks ready to place, to drop or to craft; empty means no gold as items from that source (a recipe
     *     is then hidden).
     */
    @Nonnull
    List<ItemStack> goldItems(@Nonnull GoldSource source, @Nonnull String itemId, long amount);

    /**
     * Moves what a player typed from one of this provider's accounts to another (a treasury deposit, a safe emptied).
     * The text is in the provider's own unit, what {@link #amount} writes, and moves at the provider's own precision:
     * nothing is rounded to a coin. Blank text moves everything {@code from} holds.
     *
     * <p>By default a whole number of coins: parsed, withdrawn, deposited, the withdrawal undone if the deposit is
     * refused.
     */
    @Nonnull
    default Transfer transfer(@Nonnull GoldAccount from, @Nonnull GoldAccount to, @Nullable String text) {
        long coins;
        if (text == null || text.isBlank()) {
            coins = from.balance();
            if (coins <= 0L) {
                return Transfer.NOT_AVAILABLE;
            }
        } else {
            try {
                coins = Long.parseLong(text.trim());
            } catch (NumberFormatException e) {
                return Transfer.NOT_AN_AMOUNT;
            }
            if (coins <= 0L) {
                return Transfer.NOT_AN_AMOUNT;
            }
        }
        if (!from.withdraw(coins)) {
            return Transfer.NOT_AVAILABLE;
        }
        if (!to.deposit(coins)) {
            from.deposit(coins);
            return Transfer.NO_ROOM;
        }
        return Transfer.moved(amount(coins));
    }

    /**
     * An amount as text, for the places that hold text alone (a tooltip, a notification, a chat line): "5 gold"
     * by default. Passed as a {@link Message#param(String, Message)} parameter, so a provider may return a
     * translation, a raw string, or coloured spans. Pages and dialogues draw their amounts with {@link #show}.
     */
    @Nonnull
    Message amount(long amount);

    /**
     * Draws an amount into {@code selector}, an empty group of a page (a price tag, a column of the tithe sheet, a
     * dialogue choice). {@code fontSize} is that of the text next to the group: the provider draws its digits at
     * that size and its pictures to the height of such a line. Aetherhaven clears the group before calling
     * ({@link AetherhavenEconomy#show}), so the provider only appends. The coin icon and a number by default.
     */
    void show(@Nonnull UICommandBuilder builder, @Nonnull String selector, long amount, int fontSize);

    /**
     * Draws as {@link #show(UICommandBuilder, String, long, int)} does, the pictures after the digits when
     * {@code pictureAfter}: the amount sits against a right edge (the HUD on the right side of the screen) and its
     * picture stays on the outside. A provider that draws one way only leaves this default, which ignores the flag.
     */
    default void show(
        @Nonnull UICommandBuilder builder,
        @Nonnull String selector,
        long amount,
        int fontSize,
        boolean pictureAfter
    ) {
        show(builder, selector, amount, fontSize);
    }

    /**
     * What {@code accounts} hold together, taken now, exact in the provider's unit, to draw or to write. No account
     * holds nothing. The sum of {@link GoldAccount#balance()} by default, a whole number of coins: a negative
     * balance counts as nothing and the sum stops at {@link Long#MAX_VALUE}, the HUD draws it every half second.
     */
    @Nonnull
    default Balance balance(@Nonnull GoldAccount... accounts) {
        long sum = 0L;
        for (GoldAccount account : accounts) {
            long held = Math.max(0L, account.balance());
            sum = held > Long.MAX_VALUE - sum ? Long.MAX_VALUE : sum + held;
        }
        return new Balance.Whole(this, sum);
    }
}
