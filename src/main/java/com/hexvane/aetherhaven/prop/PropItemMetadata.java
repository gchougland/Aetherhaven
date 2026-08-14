package com.hexvane.aetherhaven.prop;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.jewelry.AetherhavenBsonCodecs;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.modules.item.ItemModule;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bson.BsonDocument;
import org.bson.BsonString;

/**
 * BSON metadata on {@code Aetherhaven_Prop_Item} stacks identifying which {@link PropDefinition} they place. Mirrors
 * {@link com.hexvane.aetherhaven.plot.PlotTokenMetadata}.
 */
public final class PropItemMetadata {
    /** Hardcoded to match the future {@code AetherhavenConstants} field of the same name. */
    public static final String PROP_ITEM_ID = "Aetherhaven_Prop_Item";

    public static final String BSON_KEY = "AetherhavenPropItem";
    public static final String FIELD_PROP_ID = "propId";
    public static final String FIELD_DISPLAY_NAME = "displayName";

    /** Plain resolved strings for inventory tooltips (same key vanilla {@code TranslationProperties} use). */
    public static final String INSTANCE_TRANSLATION_PROPERTIES_KEY = "TranslationProperties";

    private static final String LANG_NAME = "aetherhaven_items.items.Aetherhaven_Prop_Item.instance.name";
    private static final String LANG_DESC = "aetherhaven_items.items.Aetherhaven_Prop_Item.instance.description";

    private PropItemMetadata() {}

    /** Shop SKU when one exists, otherwise the generic crate; always applies the instance tooltip. */
    @Nonnull
    public static ItemStack createStack(@Nonnull PropDefinition def) {
        return createStack(def, 1);
    }

    @Nonnull
    public static ItemStack createStack(@Nonnull PropDefinition def, int amount) {
        int qty = Math.max(1, amount);
        String shopItemId = PropShopItemIds.forPropId(def.getId());
        ItemStack base =
            ItemModule.exists(shopItemId)
                ? new ItemStack(shopItemId, qty)
                : new ItemStack(PROP_ITEM_ID, qty);
        return withProp(base, def.getId(), def.getDisplayName());
    }

    @Nonnull
    public static ItemStack withProp(@Nonnull ItemStack base, @Nonnull String propId, @Nullable String displayName) {
        return withProp(base, propId, displayName, null);
    }

    @Nonnull
    public static ItemStack withProp(
        @Nonnull ItemStack base,
        @Nonnull String propId,
        @Nullable String displayName,
        @Nullable String language
    ) {
        BsonDocument root = new BsonDocument();
        root.put(FIELD_PROP_ID, new BsonString(propId.trim()));
        String resolvedName = resolveLabel(displayName, propId);
        if (resolvedName != null && !resolvedName.isBlank()) {
            root.put(FIELD_DISPLAY_NAME, new BsonString(resolvedName.trim()));
        }
        BsonDocument meta = new BsonDocument();
        meta.put(BSON_KEY, root);
        ItemStack stack = base.withMetadata(meta);
        return applyInstanceTooltip(stack, resolvedName, language);
    }

    @Nullable
    public static String readPropId(@Nullable ItemStack stack) {
        BsonDocument root = readRoot(stack);
        if (root == null) {
            return null;
        }
        var v = root.get(FIELD_PROP_ID);
        if (v == null || !v.isString()) {
            return null;
        }
        String s = v.asString().getValue();
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    @Nullable
    public static String readDisplayName(@Nullable ItemStack stack) {
        BsonDocument root = readRoot(stack);
        if (root == null) {
            return null;
        }
        var v = root.get(FIELD_DISPLAY_NAME);
        if (v == null || !v.isString()) {
            return null;
        }
        String s = v.asString().getValue();
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    public static boolean matchesProp(@Nullable ItemStack stack, @Nonnull String propId) {
        String onStack = readPropId(stack);
        return onStack != null && onStack.equals(propId.trim());
    }

    @Nonnull
    private static ItemStack applyInstanceTooltip(
        @Nonnull ItemStack stack,
        @Nullable String propLabel,
        @Nullable String language
    ) {
        if (propLabel == null || propLabel.isBlank()) {
            return stack;
        }
        String label = propLabel.trim();
        String lang = language != null && !language.isBlank() ? language : "en-US";
        String namePlain = resolveLang(lang, LANG_NAME, label);
        String descPlain = resolveLang(lang, LANG_DESC, label);

        BsonDocument tp = new BsonDocument();
        tp.put("Name", new BsonString(namePlain));
        tp.put("Description", new BsonString(descPlain));
        stack = stack.withMetadata(INSTANCE_TRANSLATION_PROPERTIES_KEY, tp);
        return stack.withMetadata(
            ItemDisplayMetadata.KEYED_CODEC,
            new ItemDisplayMetadata(Message.raw(namePlain), Message.raw(descPlain))
        );
    }

    @Nonnull
    private static String resolveLang(@Nonnull String language, @Nonnull String key, @Nonnull String propLabel) {
        I18nModule i18n = I18nModule.get();
        String text = i18n != null ? i18n.getMessage(language, key) : null;
        if (text == null || text.isBlank()) {
            text = key;
        }
        return text.replace("{prop}", propLabel);
    }

    @Nullable
    private static String resolveLabel(@Nullable String displayName, @Nonnull String propId) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin != null) {
            PropDefinition def = plugin.getPropCatalog().get(propId.trim());
            if (def != null && !def.getDisplayName().isBlank()) {
                return def.getDisplayName();
            }
        }
        return propId.trim();
    }

    @Nullable
    private static BsonDocument readRoot(@Nullable ItemStack stack) {
        if (ItemStack.isEmpty(stack)) {
            return null;
        }
        return stack.getFromMetadataOrNull(BSON_KEY, AetherhavenBsonCodecs.BSON_DOCUMENT);
    }
}
