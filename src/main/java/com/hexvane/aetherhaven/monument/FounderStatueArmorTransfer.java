package com.hexvane.aetherhaven.monument;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Moves real armor pieces between a player's pack and a founder statue. Every step either moves a piece whole or
 * changes nothing, and a piece that cannot go back into the pack is handed to {@code overflow} (dropped at the
 * statue) instead of being discarded.
 */
public final class FounderStatueArmorTransfer {
    public enum Result {
        /** The piece moved. */
        DONE,
        /** The statue slot was already empty. */
        NOTHING_THERE,
        /** The pack slot no longer holds the piece the player chose. Nothing changed. */
        PACK_CHANGED,
        /** The chosen item is not armor for this slot. Nothing changed. */
        WRONG_PIECE
    }

    private FounderStatueArmorTransfer() {}

    /**
     * Takes one piece out of {@code pack} at {@code packSlot} and puts it on the statue. The piece it replaces goes
     * back through {@code returnTo}, or to {@code overflow} when there is no room.
     *
     * @param expectedItemId the item the player saw in that pack slot when they chose it
     * @param fitsSlot       whether an item is armor for this statue slot
     */
    @Nonnull
    public static Result wear(
        @Nonnull FounderStatueLoadout loadout,
        @Nonnull FounderStatueLoadout.Slot slot,
        @Nonnull ItemContainer pack,
        short packSlot,
        @Nullable String expectedItemId,
        @Nonnull Predicate<ItemStack> fitsSlot,
        @Nonnull ItemContainer returnTo,
        @Nonnull Consumer<ItemStack> overflow
    ) {
        if (packSlot < 0 || packSlot >= pack.getCapacity()) {
            return Result.PACK_CHANGED;
        }
        ItemStack inPack = pack.getItemStack(packSlot);
        if (ItemStack.isEmpty(inPack) || expectedItemId == null || !expectedItemId.equals(inPack.getItemId())) {
            return Result.PACK_CHANGED;
        }
        if (!fitsSlot.test(inPack)) {
            return Result.WRONG_PIECE;
        }
        // One piece only: the copy keeps the stack's durability and metadata.
        ItemStack piece = inPack.withQuantity(1);
        if (ItemStack.isEmpty(piece) || !pack.removeItemStackFromSlot(packSlot, 1).succeeded()) {
            return Result.PACK_CHANGED;
        }
        ItemStack replaced = loadout.put(slot, piece);
        if (replaced != null) {
            giveBack(replaced, returnTo, overflow);
        }
        return Result.DONE;
    }

    /** Takes the piece off the statue and gives it back, or hands it to {@code overflow} when there is no room. */
    @Nonnull
    public static Result takeOff(
        @Nonnull FounderStatueLoadout loadout,
        @Nonnull FounderStatueLoadout.Slot slot,
        @Nonnull ItemContainer returnTo,
        @Nonnull Consumer<ItemStack> overflow
    ) {
        ItemStack piece = loadout.take(slot);
        if (piece == null) {
            return Result.NOTHING_THERE;
        }
        giveBack(piece, returnTo, overflow);
        return Result.DONE;
    }

    /**
     * Empties the statue piece by piece. A piece leaves the statue only after {@code accept} has taken it, so a piece
     * that cannot be set down (for example an item the game no longer knows) stays where it is, and no piece is ever
     * offered twice.
     *
     * @return how many pieces were handed over
     */
    public static int release(@Nonnull FounderStatueLoadout loadout, @Nonnull Predicate<ItemStack> accept) {
        int released = 0;
        for (FounderStatueLoadout.Slot slot : FounderStatueLoadout.Slot.values()) {
            ItemStack piece = loadout.get(slot);
            if (piece != null && accept.test(piece)) {
                loadout.take(slot);
                released++;
            }
        }
        return released;
    }

    /** Whatever does not fit in {@code returnTo} goes to {@code overflow}; no part of the stack is discarded. */
    static void giveBack(@Nonnull ItemStack stack, @Nonnull ItemContainer returnTo, @Nonnull Consumer<ItemStack> overflow) {
        var added = returnTo.addItemStack(stack);
        if (!added.succeeded()) {
            overflow.accept(stack);
            return;
        }
        ItemStack remainder = added.getRemainder();
        if (!ItemStack.isEmpty(remainder)) {
            overflow.accept(remainder);
        }
    }
}
