package com.hexvane.aetherhaven.economy.api;

import com.hypixel.hytale.server.core.Message;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * What {@link EconomyProvider#transfer} did.
 *
 * @param outcome what happened
 * @param moved what went across, written as {@link EconomyProvider#amount} writes it, for the confirmation; null unless
 *     {@link Outcome#MOVED}
 */
public record Transfer(@Nonnull Outcome outcome, @Nullable Message moved) {
    public enum Outcome {
        /** The amount went across. */
        MOVED,
        /** The text is not an amount, or is zero. */
        NOT_AN_AMOUNT,
        /** The source does not hold that much. Nothing moved. */
        NOT_AVAILABLE,
        /** The destination could not take it (no room for coin items). Nothing moved. */
        NO_ROOM
    }

    public static final Transfer NOT_AN_AMOUNT = new Transfer(Outcome.NOT_AN_AMOUNT, null);
    public static final Transfer NOT_AVAILABLE = new Transfer(Outcome.NOT_AVAILABLE, null);
    public static final Transfer NO_ROOM = new Transfer(Outcome.NO_ROOM, null);

    @Nonnull
    public static Transfer moved(@Nonnull Message amount) {
        return new Transfer(Outcome.MOVED, amount);
    }
}
