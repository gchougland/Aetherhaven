package com.hexvane.aetherhaven.shopspot;

import com.hypixel.hytale.protocol.ItemWithAllMetadata;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bson.BsonDocument;
import org.bson.BsonValue;

/** Injects shop price footers into inventory packets and custom UI item stack BSON. */
public final class ShopPriceTooltipWire {

    private ShopPriceTooltipWire() {}

    /**
     * Returns a detached copy with the shop-price footer, or {@code null} if the packet item should be left as-is.
     * Never mutates {@code item} — inventory packets reuse {@code ItemStack.cachedPacket}.
     */
    @Nullable
    public static ItemWithAllMetadata copyWithFooter(
        @Nonnull ItemWithAllMetadata item,
        @Nonnull ShopPriceCatalog catalog
    ) {
        if (item.itemId.isEmpty()) {
            return null;
        }
        String before = item.metadata;
        BsonDocument meta = parseMetadata(before);
        BsonDocument merged = ShopPriceTooltipMessages.mergeFooterIntoMetadata(meta, item.itemId, catalog);
        String after = merged.toJson();
        if (Objects.equals(before, after)) {
            return null;
        }
        if ((before == null || before.isBlank()) && merged.isEmpty()) {
            return null;
        }
        ItemWithAllMetadata copy = new ItemWithAllMetadata(item);
        copy.metadata = after;
        return copy;
    }

    public static boolean applyToItemStackDocument(@Nonnull BsonDocument stackDoc, @Nonnull ShopPriceCatalog catalog) {
        BsonValue idVal = stackDoc.get("Id");
        if (idVal == null || !idVal.isString()) {
            return false;
        }
        String itemId = idVal.asString().getValue();
        BsonValue metaVal = stackDoc.get("Metadata");
        BsonDocument meta =
            metaVal != null && metaVal.isDocument() ? metaVal.asDocument().clone() : new BsonDocument();
        BsonDocument merged = ShopPriceTooltipMessages.mergeFooterIntoMetadata(meta, itemId, catalog);
        if (merged.equals(meta)) {
            return false;
        }
        stackDoc.put("Metadata", merged);
        return true;
    }

    @Nonnull
    private static BsonDocument parseMetadata(@Nullable String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return new BsonDocument();
        }
        try {
            return BsonDocument.parse(metadata);
        } catch (Exception e) {
            return new BsonDocument();
        }
    }
}
