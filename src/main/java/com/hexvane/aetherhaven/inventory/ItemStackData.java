package com.hexvane.aetherhaven.inventory;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import org.bson.BsonDocument;
import org.bson.BsonString;

/** Full serialized copies for item conversion and native material predicates.
 * Known metadata keys should instead use ItemStack.getFromMetadataOrNull.
 */
public final class ItemStackData {
    private ItemStackData() {}

    public static BsonDocument metadata(ItemStack stack) {
        return ItemStack.CODEC.encode(stack, new ExtraInfo()).asDocument().clone()
            .getDocument("Metadata", null);
    }

    /** One stack as text, in the form the engine's own containers save it. Null for an empty stack. */
    public static String toText(ItemStack stack) {
        if (ItemStack.isEmpty(stack)) return null;
        var encoded = ItemStack.CODEC.encode(stack, new ExtraInfo());
        return encoded != null && encoded.isDocument() ? encoded.asDocument().toJson() : null;
    }

    /** Reads {@link #toText}. Null when the text is missing or is not a stack; never an exception. */
    public static ItemStack fromText(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            var stack = ItemStack.CODEC.decode(BsonDocument.parse(text), new ExtraInfo());
            return ItemStack.isEmpty(stack) ? null : stack;
        } catch (RuntimeException unreadable) {
            return null;
        }
    }

    public static ItemStack withItemId(ItemStack stack, String itemId) {
        var data = ItemStack.CODEC.encode(stack, new ExtraInfo()).asDocument().clone();
        data.put("Id", new BsonString(itemId));
        return ItemStack.CODEC.decode(data, new ExtraInfo());
    }
}
