package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.jewelry.JewelryMetadata;
import com.hexvane.aetherhaven.jewelry.JewelryVirtualItemRegistry;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.protocol.FormattedMessage;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.util.MessageUtil;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bson.BsonArray;
import org.bson.BsonBoolean;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.BsonValue;

/** Shop-value footer appended to item tooltips (display-only via {@link ShopPriceTooltipPacketAdapter}). */
public final class ShopPriceTooltipMessages {
    private static final String MSG = "aetherhaven_shop.aetherhaven.shop.items";
    private static final String PRICE_KEY = MSG + ".tooltipPrice";
    private static final String BATCH_KEY = MSG + ".tooltipPriceBatch";
    /** Legacy wrapper key; stripped from old packet metadata. */
    private static final String TOOLTIP_WITH_PRICE_KEY = MSG + ".tooltipWithPrice";
    /** Bright gold for the shop-value line. */
    private static final String FOOTER_COLOR = "#f5d565";
    private static final String SERVER_ITEM_DESC_PREFIX = "server.items.";
    private static final String SERVER_ITEM_DESC_SUFFIX = ".description";

    private static final ConcurrentHashMap<String, Message> FOOTER_CACHE = new ConcurrentHashMap<>();

    private ShopPriceTooltipMessages() {}

    public static void clearCache() {
        FOOTER_CACHE.clear();
    }

    /**
     * Merges the catalog shop-value footer into packet/UI metadata. Only items with an explicit catalog entry are
     * changed. Idempotent: if a shop footer is already present the document is returned unchanged; otherwise any prior
     * footer is stripped (and leftover join wrappers unwrapped) before appending once.
     */
    @Nonnull
    public static BsonDocument mergeFooterIntoMetadata(
        @Nullable BsonDocument metadata,
        @Nonnull String itemId,
        @Nonnull ShopPriceCatalog catalog
    ) {
        String baseId = resolveBaseItemId(itemId);
        if (!catalog.hasExplicitPrice(baseId)) {
            return metadata != null ? metadata : new BsonDocument();
        }
        Message footer = footerFor(baseId, catalog);
        if (footer == null) {
            return metadata != null ? metadata : new BsonDocument();
        }
        if (metadata != null && hasShopPriceFooter(metadata)) {
            return metadata;
        }
        BsonDocument meta = metadata != null ? metadata.clone() : new BsonDocument();
        Message body = resolveBaseDescription(meta, baseId);
        Message merged = buildMergedDescription(meta, baseId, body, footer);
        writeDisplayDescription(meta, merged);
        return meta;
    }

    @Nonnull
    private static Message buildMergedDescription(
        @Nonnull BsonDocument metadata,
        @Nonnull String itemId,
        @Nonnull Message body,
        @Nonnull Message footer
    ) {
        if (!shouldIncludeDescriptionBody(metadata, itemId, body)) {
            return footer;
        }
        return Message.join(body, Message.raw("\n"), footer);
    }

    /** Skip missing/blank vanilla description keys (e.g. blocks with no {@code server.items.*.description} line). */
    private static boolean shouldIncludeDescriptionBody(
        @Nonnull BsonDocument metadata,
        @Nonnull String itemId,
        @Nonnull Message body
    ) {
        if (isEmptyMessage(body)) {
            return false;
        }
        if (hasNonEmptyItemDisplayDescription(metadata)) {
            return hasMeaningfulCustomBody(body);
        }
        Item item = resolveItem(itemId);
        if (item != null && hasLangText(item.getDescriptionTranslationKey())) {
            return true;
        }
        if (metadata.containsKey(JewelryMetadata.BSON_KEY)) {
            return hasMeaningfulCustomBody(body);
        }
        return false;
    }

    /** True when stack metadata already carries a custom {@code ItemDisplay.Description} (e.g. Simple Enchantments). */
    private static boolean hasNonEmptyItemDisplayDescription(@Nonnull BsonDocument metadata) {
        ItemDisplayMetadata display = decodeDisplay(metadata);
        if (display == null || display.getDescription() == null) {
            return false;
        }
        return !isEmptyMessage(stripShopPriceFooter(display.getDescription()));
    }

    private static boolean hasMeaningfulCustomBody(@Nonnull Message body) {
        String messageId = body.getMessageId();
        if (messageId != null && hasLangText(messageId)) {
            return true;
        }
        if (messageId != null && isMissingServerItemDescriptionKey(messageId)) {
            return false;
        }
        String plain = MessageUtil.formatMessageToPlainString(body.getFormattedMessage());
        if (plain == null || plain.isBlank()) {
            return false;
        }
        if (messageId != null && plain.trim().equals(messageId)) {
            return false;
        }
        return true;
    }

    private static boolean isMissingServerItemDescriptionKey(@Nonnull String messageId) {
        return messageId.startsWith(SERVER_ITEM_DESC_PREFIX)
            && messageId.endsWith(SERVER_ITEM_DESC_SUFFIX)
            && !hasLangText(messageId);
    }

    private static boolean hasLangText(@Nonnull String key) {
        I18nModule i18n = I18nModule.get();
        if (i18n == null) {
            return false;
        }
        String text = i18n.getMessage("en-US", key);
        return text != null && !text.isBlank();
    }

    @Nullable
    private static Message footerFor(@Nonnull String itemId, @Nonnull ShopPriceCatalog catalog) {
        ShopPriceEntry entry = catalog.getEntry(itemId);
        String cacheKey = itemId + "|" + entry.getGoldPerBatch() + "|" + entry.getBatchSize();
        return FOOTER_CACHE.computeIfAbsent(cacheKey, k -> buildFooter(entry));
    }

    @Nonnull
    private static Message buildFooter(@Nonnull ShopPriceEntry entry) {
        if (entry.isBatched()) {
            return Message.translation(BATCH_KEY)
                .param("amount", String.valueOf(entry.getGoldPerBatch()))
                .param("count", String.valueOf(entry.getBatchSize()))
                .color(FOOTER_COLOR);
        }
        return Message.translation(PRICE_KEY)
            .param("amount", String.valueOf(entry.getGoldPerBatch()))
            .color(FOOTER_COLOR);
    }

    /**
     * Prefer existing per-stack {@code ItemDisplay.Description} (Simple Enchantments, jewelry, etc.); fall back to the
     * item asset description when no custom display text is present.
     */
    @Nonnull
    private static Message resolveBaseDescription(@Nonnull BsonDocument metadata, @Nonnull String itemId) {
        ItemDisplayMetadata display = decodeDisplay(metadata);
        if (display != null && display.getDescription() != null) {
            Message fromDisplay = stripShopPriceFooter(display.getDescription());
            if (!isEmptyMessage(fromDisplay)) {
                return fromDisplay;
            }
        }
        Item item = resolveItem(itemId);
        if (item != null) {
            return item.getDescriptionTranslationMessage();
        }
        return Message.raw("");
    }

    @Nullable
    private static Item resolveItem(@Nonnull String itemId) {
        var store = Item.getAssetStore();
        if (store == null) {
            return null;
        }
        var assetMap = store.getAssetMap();
        return assetMap != null ? assetMap.getAsset(itemId) : null;
    }

    private static boolean hasShopPriceFooter(@Nonnull BsonDocument metadata) {
        ItemDisplayMetadata display = decodeDisplay(metadata);
        if (display == null || display.getDescription() == null) {
            return false;
        }
        return containsFooterMessageId(display.getDescription().getFormattedMessage());
    }

    private static boolean containsFooterMessageId(@Nonnull FormattedMessage node) {
        if (isFooterFormatted(node)) {
            return true;
        }
        if (node.children != null) {
            for (FormattedMessage child : node.children) {
                if (containsFooterMessageId(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Nonnull
    private static Message stripShopPriceFooter(@Nonnull Message message) {
        FormattedMessage root = message.getFormattedMessage().clone();
        stripTrailingFooterNodes(root);
        root = unwrapTrivialJoinRoots(root);
        if (isFormattedEmpty(root)) {
            return Message.raw("");
        }
        return new Message(root);
    }

    /** Drop empty join roots left after stripping a trailing footer ({@code Children: [body]}). */
    @Nonnull
    private static FormattedMessage unwrapTrivialJoinRoots(@Nonnull FormattedMessage node) {
        while (isTrivialSingleChildWrapper(node)) {
            node = node.children[0];
        }
        return node;
    }

    private static boolean isTrivialSingleChildWrapper(@Nonnull FormattedMessage node) {
        if (node.children == null || node.children.length != 1) {
            return false;
        }
        if (node.messageId != null && !node.messageId.isBlank()) {
            return false;
        }
        if (node.rawText != null && !node.rawText.isEmpty()) {
            return false;
        }
        if (node.color != null && !node.color.isBlank()) {
            return false;
        }
        if (Boolean.TRUE.equals(node.bold)
            || Boolean.TRUE.equals(node.italic)
            || Boolean.TRUE.equals(node.monospace)
            || Boolean.TRUE.equals(node.underlined)) {
            return false;
        }
        if (node.link != null && !node.link.isBlank()) {
            return false;
        }
        if (node.image != null) {
            return false;
        }
        if (node.params != null && !node.params.isEmpty()) {
            return false;
        }
        if (node.messageParams != null && !node.messageParams.isEmpty()) {
            return false;
        }
        return true;
    }

    private static void stripTrailingFooterNodes(@Nonnull FormattedMessage node) {
        while (node.children != null && node.children.length > 0) {
            int lastIdx = node.children.length - 1;
            FormattedMessage last = node.children[lastIdx];
            if (isFooterFormatted(last) || isIgnorableSeparator(last)) {
                node.children = lastIdx == 0 ? null : Arrays.copyOf(node.children, lastIdx);
                continue;
            }
            stripTrailingFooterNodes(last);
            return;
        }
    }

    private static boolean isFooterFormatted(@Nonnull FormattedMessage node) {
        if (PRICE_KEY.equals(node.messageId) || BATCH_KEY.equals(node.messageId)) {
            return true;
        }
        if (TOOLTIP_WITH_PRICE_KEY.equals(node.messageId)) {
            return true;
        }
        if (node.rawText != null) {
            return isPriceShapedGoldCoinsLine(node.rawText);
        }
        return false;
    }

    /** Trailing flattened shop line only, e.g. {@code 42 Gold Coins} or {@code 42 Gold Coins per 8}. */
    private static boolean isPriceShapedGoldCoinsLine(@Nonnull String rawText) {
        String t = rawText.trim();
        return t.matches("\\d+ Gold Coins(?: per \\d+)?");
    }

    private static boolean isIgnorableSeparator(@Nonnull FormattedMessage node) {
        if (node.messageId != null && !node.messageId.isBlank()) {
            return false;
        }
        if (node.children != null && node.children.length > 0) {
            return false;
        }
        return node.rawText == null || node.rawText.trim().isEmpty();
    }

    private static boolean isFormattedEmpty(@Nonnull FormattedMessage node) {
        if (isFooterFormatted(node)) {
            return true;
        }
        if (node.messageId != null && !node.messageId.isBlank()) {
            return false;
        }
        if (node.rawText != null && !node.rawText.trim().isEmpty()) {
            return false;
        }
        if (node.children == null || node.children.length == 0) {
            return true;
        }
        for (FormattedMessage child : node.children) {
            if (!isFormattedEmpty(child)) {
                return false;
            }
        }
        return true;
    }

    private static void writeDisplayDescription(@Nonnull BsonDocument metadata, @Nonnull Message description) {
        BsonDocument display;
        BsonValue existing = metadata.get(ItemDisplayMetadata.KEY);
        if (existing != null && existing.isDocument()) {
            display = existing.asDocument().clone();
        } else {
            display = new BsonDocument();
        }
        BsonValue encoded = Message.CODEC.encode(description, new ExtraInfo());
        if (encoded.isDocument()) {
            patchMarkupEnabledSelective(encoded.asDocument());
            display.put("Description", encoded);
        } else {
            display.put("Description", encoded);
        }
        metadata.put(ItemDisplayMetadata.KEY, display);
    }

    /**
     * {@link Message#CODEC} omits {@code markupEnabled}. Enable it on translation nodes so {@code <item/>} and italics
     * resolve; skip shop-price keys and colored nodes so {@code Color} still applies.
     */
    private static void patchMarkupEnabledSelective(@Nonnull BsonDocument node) {
        if (shouldEnableMarkup(node)) {
            node.put("MarkupEnabled", BsonBoolean.TRUE);
        }
        BsonValue children = node.get("Children");
        if (children instanceof BsonArray arr) {
            for (BsonValue child : arr) {
                if (child.isDocument()) {
                    patchMarkupEnabledSelective(child.asDocument());
                }
            }
        }
        BsonValue messageParams = node.get("MessageParams");
        if (messageParams instanceof BsonDocument params) {
            for (Map.Entry<String, BsonValue> entry : params.entrySet()) {
                if (entry.getValue().isDocument()) {
                    patchMarkupEnabledSelective(entry.getValue().asDocument());
                }
            }
        }
    }

    private static boolean shouldEnableMarkup(@Nonnull BsonDocument node) {
        BsonValue messageIdVal = node.get("MessageId");
        if (!(messageIdVal instanceof BsonString messageId) || messageId.getValue().isBlank()) {
            return false;
        }
        String id = messageId.getValue();
        if (PRICE_KEY.equals(id) || BATCH_KEY.equals(id) || TOOLTIP_WITH_PRICE_KEY.equals(id)) {
            return false;
        }
        BsonValue color = node.get("Color");
        return !(color instanceof BsonString colorText) || colorText.getValue().isBlank();
    }

    @Nullable
    private static ItemDisplayMetadata decodeDisplay(@Nonnull BsonDocument metadata) {
        BsonValue raw = metadata.get(ItemDisplayMetadata.KEY);
        if (raw == null || !raw.isDocument()) {
            return null;
        }
        return ItemDisplayMetadata.CODEC.decode(raw.asDocument(), new ExtraInfo());
    }

    @Nonnull
    private static String resolveBaseItemId(@Nonnull String itemId) {
        if (JewelryVirtualItemRegistry.isVirtualId(itemId)) {
            String base = JewelryVirtualItemRegistry.getBaseItemId(itemId);
            if (base != null) {
                return base;
            }
        }
        return itemId;
    }

    private static boolean isEmptyMessage(@Nonnull Message message) {
        String raw = message.getRawText();
        if (raw != null && !raw.isBlank()) {
            return false;
        }
        if (message.getMessageId() != null && !message.getMessageId().isBlank()) {
            return false;
        }
        return message.getChildren().isEmpty();
    }

    @Nullable
    public static ShopPriceCatalog catalogOrEmpty() {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        return plugin != null ? plugin.getShopPriceCatalog() : ShopPriceCatalog.empty();
    }
}
