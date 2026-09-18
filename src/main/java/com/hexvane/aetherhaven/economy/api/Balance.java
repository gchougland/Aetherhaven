package com.hexvane.aetherhaven.economy.api;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import javax.annotation.Nonnull;

/**
 * What one or more accounts hold, taken when {@link EconomyProvider#balance} was called, exact in the provider's unit:
 * a treasury on its page, the coins a player may spend (their own and, when the charter lets them, their town's), the
 * HUD's sum. A price is a count of Aetherhaven coins the provider converts; a balance is the provider's own and is shown
 * as it is, never rounded to a coin.
 *
 * <p>Two balances are equal when what they hold is: a page refreshed on a timer redraws only when its balance changed.
 */
public interface Balance {
    /**
     * Draws the balance into {@code selector}, an empty group of a page, as {@link EconomyProvider#show} draws an
     * amount: at the size of the line, into a cleared group ({@link AetherhavenEconomy#show}).
     */
    void show(@Nonnull UICommandBuilder builder, @Nonnull String selector, int fontSize);

    /**
     * Draws as {@link #show(UICommandBuilder, String, int)} does, the pictures after the digits when
     * {@code pictureAfter} ({@link EconomyProvider#show(UICommandBuilder, String, long, int, boolean)}). Ignored
     * by default.
     */
    default void show(@Nonnull UICommandBuilder builder, @Nonnull String selector, int fontSize, boolean pictureAfter) {
        show(builder, selector, fontSize);
    }

    /** The balance as text, for a tooltip or a chat line, as {@link EconomyProvider#amount} writes an amount. */
    @Nonnull
    Message message();

    /** The default: a whole number of Aetherhaven coins, shown as any amount is. */
    record Whole(@Nonnull EconomyProvider provider, long coins) implements Balance {
        @Override
        public void show(@Nonnull UICommandBuilder builder, @Nonnull String selector, int fontSize) {
            provider.show(builder, selector, coins, fontSize);
        }

        @Override
        public void show(@Nonnull UICommandBuilder builder, @Nonnull String selector, int fontSize, boolean pictureAfter) {
            provider.show(builder, selector, coins, fontSize, pictureAfter);
        }

        @Nonnull
        @Override
        public Message message() {
            return provider.amount(coins);
        }
    }
}
