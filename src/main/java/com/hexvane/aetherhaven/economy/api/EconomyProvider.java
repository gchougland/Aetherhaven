package com.hexvane.aetherhaven.economy.api;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import java.util.OptionalLong;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Where a player's gold lives. Aetherhaven ships {@code ItemCoinEconomy} (the gold coin item); an economy mod
 * registers its own through {@link AetherhavenEconomy#register}.
 *
 * <p>Every amount is in Aetherhaven gold coins, never negative: a provider with another unit converts. The town
 * treasury and the shop safes are not covered, they stay Aetherhaven's own ledgers. The provider persists its
 * balances itself, Aetherhaven persists nothing on its behalf.
 */
public interface EconomyProvider {
    /** Stable id for logs, e.g. {@code "aetherhaven:coins"}. */
    @Nonnull
    String id();

    /** The player's account, or null when the entity is not a player. */
    @Nullable
    GoldAccount account(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store);

    /**
     * Items Aetherhaven places for {@code amount} gold of loot (a dungeon chest, a broken pot). {@code itemId} is what
     * the server configured for that loot source. The built-in economy returns that item split by its max stack; a
     * provider may return its own items instead, or nothing. No player is involved: chests are filled by chunk
     * systems.
     *
     * @return stacks ready to place in a container or to drop; empty means no gold loot.
     */
    @Nonnull
    List<ItemStack> lootItems(@Nonnull String itemId, long amount);

    /**
     * An amount a player typed, in the provider's own unit (what {@link #amount} writes), as Aetherhaven gold coins
     * rounded down. A whole number of coins by default. Empty when the text is not an amount at all.
     */
    @Nonnull
    default OptionalLong parseAmount(@Nonnull String text) {
        try {
            long coins = Long.parseLong(text.trim());
            return coins < 0L ? OptionalLong.empty() : OptionalLong.of(coins);
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    /**
     * An amount as text, for every sentence Aetherhaven prints one in: "5 gold" by default. Passed as a
     * {@link Message#param(String, Message)} parameter, so a provider may return a translation, a raw string, or
     * formatted text with its own images.
     */
    @Nonnull
    Message amount(long amount);

    /**
     * Draws an amount into {@code selector}, an empty group of a page (the HUD, a price tag). Aetherhaven clears the
     * group before calling, so the provider only appends. An icon and a number by default.
     */
    void show(@Nonnull UICommandBuilder builder, @Nonnull String selector, long amount);
}
