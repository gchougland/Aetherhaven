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
import org.bson.BsonArray;
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
        assertTrue(
            childrenZeroDepth(descriptionDocument(twice)) <= 8,
            "idempotent merge must not grow Description.Children[0] nesting"
        );
    }

    @Test
    void mergeFooter_repeatedMergesStayShallow() {
        BsonDocument meta = metadataWithDisplay(CUSTOM_TEXT, null);

        BsonDocument current = meta;
        for (int i = 0; i < 80; i++) {
            current = ShopPriceTooltipMessages.mergeFooterIntoMetadata(current.clone(), ITEM_ID, pricedCatalog);
        }

        assertTrue(descriptionPlainText(current).contains("Sharpness III"));
        assertEquals(1, countMessageId(current, PRICE_KEY), "expected single price footer node after repeated merges");
        int depth = childrenZeroDepth(descriptionDocument(current));
        assertTrue(depth <= 8, () -> "expected Children[0] depth <= 8 after 80 merges, got " + depth);
    }

    @Test
    void mergeFooter_unwrapsLeftoverJoinWrappers() {
        BsonDocument inner = Message.CODEC.encode(Message.raw(CUSTOM_TEXT), new ExtraInfo()).asDocument();
        for (int i = 0; i < 20; i++) {
            BsonDocument wrap = new BsonDocument();
            BsonArray children = new BsonArray();
            children.add(inner);
            wrap.put("Children", children);
            inner = wrap;
        }
        BsonDocument meta = new BsonDocument();
        BsonDocument display = new BsonDocument();
        display.put("Description", inner);
        meta.put(ItemDisplayMetadata.KEY, display);

        BsonDocument merged = ShopPriceTooltipMessages.mergeFooterIntoMetadata(meta, ITEM_ID, pricedCatalog);

        assertTrue(descriptionPlainText(merged).contains("Sharpness III"));
        assertEquals(1, countMessageId(merged, PRICE_KEY));
        int depth = childrenZeroDepth(descriptionDocument(merged));
        assertTrue(depth <= 8, () -> "expected unwrap of leftover join roots, got Children[0] depth " + depth);
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

    @Test
    void mergeFooter_priceIsSiblingOfBodyNotChildOfDescription() {
        BsonDocument meta = metadataWithTranslationDescription(
            "aetherhaven_items.items.Widget.description",
            "Can be smelted at a furnace."
        );

        BsonDocument merged = ShopPriceTooltipMessages.mergeFooterIntoMetadata(meta, ITEM_ID, pricedCatalog);

        BsonDocument description = descriptionDocument(merged);
        BsonDocument price = findNodeWithMessageId(description, PRICE_KEY);
        BsonDocument body = findNodeWithMessageId(description, "aetherhaven_items.items.Widget.description");
        assertTrue(hasPriceFooter(merged));
        assertTrue(body != null, "expected description translation node");
        assertTrue(price != null, "expected shop price footer node");
        assertFalse(containsMessageId(body, PRICE_KEY), "price must not be nested inside the description translation");
        assertTrue(isDirectChild(description, price), "price should be a sibling under the joined root");
    }

    @Test
    void mergeFooter_enablesMarkupOnDescriptionButNotPrice() {
        BsonDocument meta = metadataWithTranslationDescription(
            "aetherhaven_items.items.Widget.description",
            "Can be smelted at a <item is=\"Bench_Furnace\"/>."
        );

        BsonDocument merged = ShopPriceTooltipMessages.mergeFooterIntoMetadata(meta, ITEM_ID, pricedCatalog);

        BsonDocument description = descriptionDocument(merged);
        BsonDocument body = findNodeWithMessageId(description, "aetherhaven_items.items.Widget.description");
        BsonDocument price = findNodeWithMessageId(description, PRICE_KEY);
        assertTrue(body != null && isMarkupEnabled(body), "description translation should keep markup");
        assertTrue(price != null && !isMarkupEnabled(price), "price footer must not enable markup");
    }

    @Test
    void mergeFooter_preservesCustomColorAndBold() {
        Message colored = Message.raw("Sharpness III").color("#C76CFF").bold(true);
        BsonDocument meta = new BsonDocument();
        BsonDocument display = new BsonDocument();
        display.put("Description", Message.CODEC.encode(colored, new ExtraInfo()));
        meta.put(ItemDisplayMetadata.KEY, display);

        BsonDocument merged = ShopPriceTooltipMessages.mergeFooterIntoMetadata(meta, ITEM_ID, pricedCatalog);

        BsonDocument description = descriptionDocument(merged);
        BsonDocument sharpness = findNodeWithRawText(description, "Sharpness III");
        assertTrue(sharpness != null, "expected custom colored line");
        assertEquals("#C76CFF", sharpness.getString("Color").getValue());
        assertTrue(sharpness.getBoolean("Bold").getValue());
        assertTrue(hasPriceFooter(merged));
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

    @Nonnull
    private static BsonDocument metadataWithTranslationDescription(@Nonnull String messageId, @Nonnull String rawText) {
        Message body = Message.join(Message.translation(messageId), Message.raw(rawText));
        BsonDocument meta = new BsonDocument();
        BsonDocument display = new BsonDocument();
        display.put("Description", Message.CODEC.encode(body, new ExtraInfo()));
        meta.put(ItemDisplayMetadata.KEY, display);
        return meta;
    }

    @Nonnull
    private static BsonDocument descriptionDocument(@Nonnull BsonDocument metadata) {
        return metadata.getDocument(ItemDisplayMetadata.KEY).getDocument("Description");
    }

    /** Length of the {@code Children[0].Children[0]...} chain (client JSON max depth is 64). */
    private static int childrenZeroDepth(@Nonnull BsonDocument node) {
        int depth = 0;
        BsonDocument current = node;
        while (true) {
            BsonValue children = current.get("Children");
            if (children == null || !children.isArray() || children.asArray().isEmpty()) {
                return depth;
            }
            BsonValue first = children.asArray().get(0);
            if (!first.isDocument()) {
                return depth;
            }
            depth++;
            current = first.asDocument();
        }
    }

    private static boolean isMarkupEnabled(@Nonnull BsonDocument node) {
        BsonValue flag = node.get("MarkupEnabled");
        return flag != null && flag.isBoolean() && flag.asBoolean().getValue();
    }

    private static boolean isDirectChild(@Nonnull BsonDocument parent, @Nonnull BsonDocument child) {
        BsonValue children = parent.get("Children");
        if (children == null || !children.isArray()) {
            return false;
        }
        for (BsonValue value : children.asArray()) {
            if (value.isDocument() && value.asDocument().equals(child)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static BsonDocument findNodeWithMessageId(@Nonnull BsonDocument node, @Nonnull String messageId) {
        BsonValue id = node.get("MessageId");
        if (id != null && id.isString() && messageId.equals(id.asString().getValue())) {
            return node;
        }
        BsonValue children = node.get("Children");
        if (children != null && children.isArray()) {
            for (BsonValue value : children.asArray()) {
                if (value.isDocument()) {
                    BsonDocument found = findNodeWithMessageId(value.asDocument(), messageId);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    private static BsonDocument findNodeWithRawText(@Nonnull BsonDocument node, @Nonnull String rawText) {
        BsonValue raw = node.get("RawText");
        if (raw != null && raw.isString() && rawText.equals(raw.asString().getValue())) {
            return node;
        }
        BsonValue children = node.get("Children");
        if (children != null && children.isArray()) {
            for (BsonValue value : children.asArray()) {
                if (value.isDocument()) {
                    BsonDocument found = findNodeWithRawText(value.asDocument(), rawText);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }

    private static boolean containsMessageId(@Nonnull BsonDocument node, @Nonnull String messageId) {
        BsonValue children = node.get("Children");
        if (children == null || !children.isArray()) {
            return false;
        }
        for (BsonValue value : children.asArray()) {
            if (!value.isDocument()) {
                continue;
            }
            BsonDocument child = value.asDocument();
            if (findNodeWithMessageId(child, messageId) != null) {
                return true;
            }
        }
        return false;
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
