package com.hexvane.aetherhaven.shopspot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import com.hypixel.hytale.server.core.util.MessageUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bson.BsonDocument;
import org.bson.BsonValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
class ShopPriceTooltipMessagesTest {
    private static final String ITEM_ID = "Weapon_Sword_Iron";
    private static final String CUSTOM_TEXT = "Sharpness III\n+5 damage";
    private static final String DISPLAY_NAME = "Enchanted Blade";
    private static final String PRICE_KEY = "aetherhaven_shop.aetherhaven.shop.items.tooltipPrice";

    private ShopPriceCatalog pricedCatalog;

    @BeforeEach
    void setUp() {
        ShopPriceTooltipMessages.clearCache();
        pricedCatalog =
            ShopPriceCatalog.parseJson(
                """
                {
                  "catalogRevision": 1,
                  "defaultGoldPrice": 5,
                  "defaultBatchSize": 1,
                  "prices": {
                    "Weapon_Sword_Iron": 42
                  }
                }
                """
            );
    }

    @Test
    void mergeFooter_preservesCustomItemDisplayAndAppendsPrice() {
        BsonDocument meta = metadataWithDisplay(CUSTOM_TEXT, DISPLAY_NAME);

        BsonDocument merged = ShopPriceTooltipMessages.mergeFooterIntoMetadata(meta, ITEM_ID, pricedCatalog);

        String plain = descriptionPlainText(merged);
        assertTrue(plain.contains("Sharpness III"), () -> "expected custom display text, got: " + plain);
        assertTrue(plain.contains("+5 damage"), () -> "expected custom display text, got: " + plain);
        assertTrue(hasPriceFooter(merged), "expected shop price footer message");
        assertEquals(DISPLAY_NAME, displayNamePlainText(merged));
    }

    @Test
    void mergeFooter_isIdempotent() {
        BsonDocument meta = metadataWithDisplay(CUSTOM_TEXT, null);

        BsonDocument once = ShopPriceTooltipMessages.mergeFooterIntoMetadata(meta, ITEM_ID, pricedCatalog);
        BsonDocument twice =
            ShopPriceTooltipMessages.mergeFooterIntoMetadata(once.clone(), ITEM_ID, pricedCatalog);

        String plain = descriptionPlainText(twice);
        assertTrue(plain.contains("Sharpness III"));
        assertTrue(hasPriceFooter(twice));
        assertEquals(1, countMessageId(twice, PRICE_KEY), "expected single price footer node");
    }

    @Test
    void mergeFooter_withoutExplicitPrice_leavesMetadataUnchanged() {
        BsonDocument meta = metadataWithDisplay(CUSTOM_TEXT, null);
        ShopPriceCatalog emptyPrices =
            ShopPriceCatalog.parseJson(
                """
                {
                  "catalogRevision": 1,
                  "defaultGoldPrice": 99,
                  "defaultBatchSize": 1,
                  "prices": {}
                }
                """
            );

        BsonDocument merged = ShopPriceTooltipMessages.mergeFooterIntoMetadata(meta, ITEM_ID, emptyPrices);

        assertEquals(meta, merged);
        assertFalse(hasPriceFooter(merged));
    }

    @Test
    void mergeFooter_withoutItemDisplay_writesPriceFooterOnly() {
        BsonDocument meta = new BsonDocument();

        BsonDocument merged = ShopPriceTooltipMessages.mergeFooterIntoMetadata(meta, ITEM_ID, pricedCatalog);

        assertTrue(merged.containsKey(ItemDisplayMetadata.KEY));
        assertTrue(hasPriceFooter(merged));
        assertFalse(descriptionPlainText(merged).contains(CUSTOM_TEXT));
    }

    @Nonnull
    private static BsonDocument metadataWithDisplay(@Nonnull String descriptionText, @Nullable String nameText) {
        BsonDocument meta = new BsonDocument();
        BsonDocument display = new BsonDocument();
        display.put("Description", Message.CODEC.encode(Message.raw(descriptionText), new ExtraInfo()));
        if (nameText != null) {
            display.put("Name", Message.CODEC.encode(Message.raw(nameText), new ExtraInfo()));
        }
        meta.put(ItemDisplayMetadata.KEY, display);
        return meta;
    }

    private static boolean hasPriceFooter(@Nonnull BsonDocument metadata) {
        return countMessageId(metadata, PRICE_KEY) > 0;
    }

    private static int countMessageId(@Nonnull BsonDocument metadata, @Nonnull String messageId) {
        ItemDisplayMetadata display = decodeDisplay(metadata);
        if (display == null || display.getDescription() == null) {
            return 0;
        }
        return countMessageId(display.getDescription().getFormattedMessage(), messageId);
    }

    private static int countMessageId(@Nonnull FormattedMessage node, @Nonnull String messageId) {
        int count = messageId.equals(node.messageId) ? 1 : 0;
        if (node.children != null) {
            for (FormattedMessage child : node.children) {
                count += countMessageId(child, messageId);
            }
        }
        return count;
    }

    @Nonnull
    private static String descriptionPlainText(@Nonnull BsonDocument metadata) {
        ItemDisplayMetadata display = decodeDisplay(metadata);
        if (display == null || display.getDescription() == null) {
            return "";
        }
        return MessageUtil.formatMessageToPlainString(display.getDescription().getFormattedMessage());
    }

    @Nullable
    private static String displayNamePlainText(@Nonnull BsonDocument metadata) {
        ItemDisplayMetadata display = decodeDisplay(metadata);
        if (display == null || display.getName() == null) {
            return null;
        }
        return MessageUtil.formatMessageToPlainString(display.getName().getFormattedMessage());
    }

    @Nullable
    private static ItemDisplayMetadata decodeDisplay(@Nonnull BsonDocument metadata) {
        BsonValue raw = metadata.get(ItemDisplayMetadata.KEY);
        if (raw == null || !raw.isDocument()) {
            return null;
        }
        return ItemDisplayMetadata.CODEC.decode(raw.asDocument(), new ExtraInfo());
    }
}
