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

    public static ItemStack withItemId(ItemStack stack, String itemId) {
        var data = ItemStack.CODEC.encode(stack, new ExtraInfo()).asDocument().clone();
        data.put("Id", new BsonString(itemId));
        return ItemStack.CODEC.decode(data, new ExtraInfo());
    }
}
