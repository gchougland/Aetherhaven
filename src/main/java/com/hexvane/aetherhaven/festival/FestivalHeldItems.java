package com.hexvane.aetherhaven.festival;

import com.google.gson.annotations.SerializedName;
import com.hexvane.aetherhaven.inventory.ItemStackData;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bson.BsonDocument;

/**
 * Players' own items that a festival is holding, saved with the town so a restart, a festival ending or a player
 * leaving can never lose them: the goods laid out in the Autumn Market stall, a Wintertide gift on its way from one
 * player to another, and anything that could not be handed back yet.
 *
 * <p>Every stack is kept whole, in the form the engine's own containers save ({@link ItemStack#CODEC},
 * {@link SimpleItemContainer#CODEC}): metadata, durability and quality travel with it. Nothing here merges stacks;
 * a container keeps its slots as the player filled them.
 *
 * <p>Plain data, read and written by Gson with the town. A town that holds nothing saves none of it.
 */
public final class FestivalHeldItems {
    /** The Autumn Market stall's slots, as the engine saves a container. */
    @Nullable
    @SerializedName("marketStall")
    private String marketStall;

    /** The town member who last opened the stall. Goods nobody collects go back to them when the market ends. */
    @Nullable
    @SerializedName("marketKeeper")
    private String marketKeeper;

    /** A Wintertide gift between two players that the receiver has not yet answered. */
    @Nullable
    @SerializedName("wintertideGift")
    private Gift wintertideGift;

    /** Stacks that go back to a player the next time they are in the town's world. */
    @Nullable
    @SerializedName("owed")
    private List<Owed> owed;

    public static final class Gift {
        @SerializedName("giver")
        private String giver;
        @SerializedName("receiver")
        private String receiver;
        @SerializedName("stack")
        private String stack;

        @Nullable
        public UUID giver() {
            return uuid(giver);
        }

        @Nullable
        public UUID receiver() {
            return uuid(receiver);
        }

        /** The gift itself, or null when the saved text cannot be read. */
        @Nullable
        public ItemStack stack() {
            return ItemStackData.fromText(stack);
        }
    }

    public static final class Owed {
        @SerializedName("player")
        private String player;
        @SerializedName("stack")
        private String stack;
    }

    public boolean isEmpty() {
        return marketStall == null && wintertideGift == null && (owed == null || owed.isEmpty());
    }

    // ---- Autumn Market stall

    /**
     * Records what the stall holds right now and who keeps it. An empty stall saves nothing.
     *
     * @param keeper the town member who last opened the stall, or null to leave the recorded one
     */
    public void saveMarketStall(@Nullable SimpleItemContainer stall, @Nullable UUID keeper) {
        marketStall = stall == null || stall.isEmpty() ? null : SimpleItemContainer.CODEC.encode(stall, new ExtraInfo()).asDocument().toJson();
        if (keeper != null) {
            marketKeeper = keeper.toString();
        }
        if (marketStall == null) {
            marketKeeper = null;
        }
    }

    /** True while the stall has saved goods. */
    public boolean hasMarketStall() {
        return marketStall != null;
    }

    /** The town member who last opened the stall, when goods are saved in it. */
    @Nullable
    public UUID marketKeeper() {
        return uuid(marketKeeper);
    }

    /** The stall as it was saved, slot for slot, in a container of the given size. Empty when nothing was saved. */
    @Nonnull
    public SimpleItemContainer restoreMarketStall(short slots) {
        SimpleItemContainer stall = new SimpleItemContainer(slots);
        List<ItemStack> unplaced = new ArrayList<>();
        SimpleItemContainer saved = decodeStall();
        for (short i = 0; saved != null && i < saved.getCapacity(); i++) {
            ItemStack stack = saved.getItemStack(i);
            if (ItemStack.isEmpty(stack)) {
                continue;
            }
            if (i < slots) {
                stall.setItemStackForSlot(i, stack, false);
            } else {
                unplaced.add(stack);
            }
        }
        // A stall that was saved with more slots than it has now still gives everything back.
        for (ItemStack stack : unplaced) {
            stall.addItemStack(stack);
        }
        return stall;
    }

    /**
     * The market is over. Whatever is still in the stall is set aside, stack by stack, for the member who kept the
     * stall, and handed to them the next time they are in the town's world. Nothing is left lying on the ground,
     * where it would be cleared away with the festival or fade.
     *
     * @param fallback who receives the goods when no keeper was recorded: the town's owner
     * @return how many stacks were set aside
     */
    public int endMarketStall(@Nonnull UUID fallback) {
        SimpleItemContainer saved = decodeStall();
        if (saved == null) {
            // Nothing saved, or text that cannot be read: that is left in place, deleting it never brings it back.
            return 0;
        }
        UUID keeper = marketKeeper() != null ? marketKeeper() : fallback;
        int moved = 0;
        for (short i = 0; i < saved.getCapacity(); i++) {
            ItemStack stack = saved.getItemStack(i);
            if (!ItemStack.isEmpty(stack)) {
                owe(keeper, stack);
                moved++;
            }
        }
        marketStall = null;
        marketKeeper = null;
        return moved;
    }

    @Nullable
    private SimpleItemContainer decodeStall() {
        if (marketStall == null || marketStall.isBlank()) {
            return null;
        }
        try {
            return SimpleItemContainer.CODEC.decode(BsonDocument.parse(marketStall), new ExtraInfo());
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    // ---- Wintertide gift between players

    /**
     * One item has left the giver's hand and waits for the receiver's answer. A gift that was still saved (its
     * session was lost before it was answered or returned) is never written over: it is set aside for its own giver.
     */
    public void holdWintertideGift(@Nonnull UUID giver, @Nonnull UUID receiver, @Nonnull ItemStack stack) {
        if (wintertideGift != null && wintertideGift.stack != null) {
            oweText(wintertideGift.giver, wintertideGift.stack);
        }
        Gift gift = new Gift();
        gift.giver = giver.toString();
        gift.receiver = receiver.toString();
        gift.stack = ItemStackData.toText(stack);
        wintertideGift = gift;
    }

    @Nullable
    public Gift wintertideGift() {
        return wintertideGift;
    }

    /** Call once the gift has really reached the receiver, or has been set aside for the giver with {@link #owe}. */
    public void clearWintertideGift() {
        wintertideGift = null;
    }

    // ---- handed back later

    /** Sets a stack aside for a player who is not here to take it. */
    public void owe(@Nonnull UUID player, @Nonnull ItemStack stack) {
        oweText(player.toString(), ItemStackData.toText(stack));
    }

    private void oweText(@Nullable String player, @Nullable String stackText) {
        if (stackText == null || stackText.isBlank()) {
            return;
        }
        Owed entry = new Owed();
        entry.player = player;
        entry.stack = stackText;
        if (owed == null) {
            owed = new ArrayList<>();
        }
        owed.add(entry);
    }

    /** Players something is set aside for. */
    @Nonnull
    public Set<UUID> owedPlayers() {
        Set<UUID> out = new LinkedHashSet<>();
        if (owed != null) {
            for (Owed entry : owed) {
                UUID id = uuid(entry.player);
                if (id != null) {
                    out.add(id);
                }
            }
        }
        return out;
    }

    /**
     * Takes everything set aside for the player out of the record. The caller hands each stack over, and puts back
     * with {@link #owe} any it could not.
     */
    @Nonnull
    public List<ItemStack> takeOwed(@Nonnull UUID player) {
        List<ItemStack> out = new ArrayList<>();
        if (owed == null) {
            return out;
        }
        String key = player.toString();
        for (Iterator<Owed> it = owed.iterator(); it.hasNext();) {
            Owed entry = it.next();
            if (!key.equalsIgnoreCase(entry.player)) {
                continue;
            }
            ItemStack stack = ItemStackData.fromText(entry.stack);
            // Text that cannot be read stays where it is: a later version may read it, deleting it never will.
            if (stack != null) {
                out.add(stack);
                it.remove();
            }
        }
        if (owed.isEmpty()) {
            owed = null;
        }
        return out;
    }

    @Nullable
    private static UUID uuid(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(text.trim());
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }
}
