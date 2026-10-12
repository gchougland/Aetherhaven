package com.hexvane.aetherhaven.shopspot;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bson.BsonDocument;
import org.bson.BsonValue;

/**
 * What a stall has for sale, kept as the whole engine stack and not as an item id and a count. Everything the engine
 * holds on a stack travels with the listing: metadata, durability, maximum durability and quality. So a filled Alchemy
 * Flask, an appraised ring, a worn tool or a packed backpack comes out of a shop exactly as it went in.
 *
 * <p>The stack is saved with {@link ItemStack#CODEC}, the codec the engine's own containers save their slots with, so
 * a field the engine adds to a stack later is carried too.
 *
 * <p>One stall holds one stack. Nothing merges two listings, so two stacks the engine would not stack (a different
 * mixture, a different durability) can only ever be two listings on two stalls.
 */
public final class ShopListing {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private ShopListing() {}

    /** One stack in the engine's saved form, or null when there is nothing to save. */
    @Nullable
    public static String encode(@Nullable ItemStack stack) {
        if (ItemStack.isEmpty(stack)) {
            return null;
        }
        BsonValue encoded = ItemStack.CODEC.encode(stack, new ExtraInfo());
        return encoded != null && encoded.isDocument() ? encoded.asDocument().toJson() : null;
    }

    /** Reads {@link #encode}. Null when the text is missing or is not a stack, never an exception. */
    @Nullable
    public static ItemStack decode(@Nullable String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        try {
            ItemStack stack = ItemStack.CODEC.decode(BsonDocument.parse(data), new ExtraInfo());
            return ItemStack.isEmpty(stack) ? null : stack;
        } catch (RuntimeException unreadable) {
            LOGGER.atWarning().withCause(unreadable).log("Unreadable shop listing stack; falling back to its item id");
            return null;
        }
    }

    /**
     * Puts {@code quantity} of the offered stack on a stall. The caller has already taken them from the seller.
     *
     * @param offered the stack in the seller's hand; its own quantity does not matter
     * @param quote the catalog price the listing goes up at. A listing keeps this price when the catalog changes.
     */
    public static void list(
        @Nonnull ShopSpotRecord record,
        @Nonnull ItemStack offered,
        int quantity,
        @Nullable ShopPriceEntry quote
    ) {
        ItemStack one = single(offered);
        record.setItemId(offered.getItemId());
        record.setStock(quantity);
        record.setStackData(encode(one));
        record.setStackCache(one);
        record.setQuote(quote);
    }

    /** True when the stall keeps its goods as a whole stack (every listing made since stacks were kept whole). */
    public static boolean keepsWholeStack(@Nonnull ShopSpotRecord record) {
        return carried(record) != null;
    }

    /**
     * The listed stack at the given quantity, or null when this stall only knows an item id (rolled NPC stock, or a
     * listing from an older save). Always a new stack: the caller may hand it to a player or change it.
     */
    @Nullable
    public static ItemStack stack(@Nonnull ShopSpotRecord record, int quantity) {
        ItemStack one = carried(record);
        if (one == null || quantity <= 0) {
            return null;
        }
        ItemStack sized = one.withQuantity(quantity);
        // withQuantity returns the same object when the quantity already matches; never hand out the kept one.
        return sized == one ? copy(one) : sized;
    }

    /** Takes sold items off the stall. The stall is emptied, and its seller released, once nothing is left. */
    public static void sold(@Nonnull ShopSpotRecord record, int quantity) {
        record.setStock(record.getStock() - Math.max(0, quantity));
        if (record.getStock() <= 0) {
            clear(record);
        }
    }

    /** Empties the stall: no item, no stack, no recorded price, no seller. */
    public static void clear(@Nonnull ShopSpotRecord record) {
        record.setItemId(null);
        record.setStock(0);
        record.setSellerUuid(null);
    }

    /** Changes with anything a buyer could see, so a relisted stack with other contents gets a fresh display. */
    public static int appearance(@Nonnull ShopSpotRecord record) {
        String data = record.getStackData();
        return data != null ? data.hashCode() : 0;
    }

    @Nullable
    private static ItemStack carried(@Nonnull ShopSpotRecord record) {
        String itemId = record.getItemId();
        String data = record.getStackData();
        if (itemId == null || data == null) {
            return null;
        }
        if (record.getStackCache() instanceof ItemStack cached) {
            return cached;
        }
        ItemStack decoded = decode(data);
        // A stack that names another item is not this listing's. Keep selling the item the stall says it sells.
        if (decoded == null || !itemId.equals(decoded.getItemId())) {
            return null;
        }
        ItemStack one = single(decoded);
        record.setStackCache(one);
        return one;
    }

    @Nonnull
    private static ItemStack single(@Nonnull ItemStack stack) {
        ItemStack one = stack.withQuantity(1);
        return one == null || one == stack ? copy(stack) : one;
    }

    /** Same item, quantity, durability, quality and metadata in a new stack. */
    @Nonnull
    private static ItemStack copy(@Nonnull ItemStack stack) {
        return stack.cleanCopy();
    }
}
