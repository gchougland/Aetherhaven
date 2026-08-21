package com.hexvane.aetherhaven.blockpalette;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.jewelry.AetherhavenBsonCodecs;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bson.BsonDocument;
import org.bson.BsonString;

/** BSON metadata on {@link BlockPaletteConstants#ITEM_ID} stacks. */
public final class BlockPaletteItemMetadata {
    public static final String BSON_KEY = "AetherhavenBlockPalette";
    public static final String FIELD_PALETTE_ID = "paletteId";
    public static final String FIELD_DISPLAY_NAME = "displayName";
    public static final String INSTANCE_TRANSLATION_PROPERTIES_KEY = "TranslationProperties";

    private static final String LANG_NAME = "aetherhaven_items.items.Aetherhaven_Block_Palette.instance.name";
    private static final String LANG_DESC = "aetherhaven_items.items.Aetherhaven_Block_Palette.instance.description";

    private BlockPaletteItemMetadata() {}

    @Nonnull
    public static ItemStack createStack(@Nonnull BlockPaletteDefinition def) {
        return createStack(def, 1);
    }

    @Nonnull
    public static ItemStack createStack(@Nonnull BlockPaletteDefinition def, int amount) {
        ItemStack base = new ItemStack(BlockPaletteConstants.ITEM_ID, Math.max(1, amount));
        return withPalette(base, def.getId(), def.getDisplayName());
    }

    @Nonnull
    public static ItemStack withPalette(@Nonnull ItemStack base, @Nonnull String paletteId, @Nullable String displayName) {
        return withPalette(base, paletteId, displayName, null);
    }

    @Nonnull
    public static ItemStack withPalette(
        @Nonnull ItemStack base,
        @Nonnull String paletteId,
        @Nullable String displayName,
        @Nullable String language
    ) {
        BsonDocument root = new BsonDocument();
        root.put(FIELD_PALETTE_ID, new BsonString(paletteId.trim()));
        String resolvedName = resolveLabel(displayName, paletteId);
        if (resolvedName != null && !resolvedName.isBlank()) {
            root.put(FIELD_DISPLAY_NAME, new BsonString(resolvedName.trim()));
        }
        BsonDocument meta = new BsonDocument();
        meta.put(BSON_KEY, root);
        return applyInstanceTooltip(base.withMetadata(meta), resolvedName, language);
    }

    @Nullable
    public static String readPaletteId(@Nullable ItemStack stack) {
        BsonDocument root = readRoot(stack);
        if (root == null) {
            return null;
        }
        var v = root.get(FIELD_PALETTE_ID);
        if (v == null || !v.isString()) {
            return null;
        }
        String s = v.asString().getValue();
        return s != null && !s.isBlank() ? s.trim() : null;
    }

    @Nonnull
    private static ItemStack applyInstanceTooltip(
        @Nonnull ItemStack stack,
        @Nullable String label,
        @Nullable String language
    ) {
        if (label == null || label.isBlank()) {
            return stack;
        }
        String lang = language != null && !language.isBlank() ? language : "en-US";
        String namePlain = resolveLang(lang, LANG_NAME, label.trim());
        String descPlain = resolveLang(lang, LANG_DESC, label.trim());
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
    private static String resolveLang(@Nonnull String language, @Nonnull String key, @Nonnull String label) {
        I18nModule i18n = I18nModule.get();
        String text = i18n != null ? i18n.getMessage(language, key) : null;
        if (text == null || text.isBlank()) {
            text = key;
        }
        return text.replace("{palette}", label);
    }

    @Nullable
    private static String resolveLabel(@Nullable String displayName, @Nonnull String paletteId) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin != null) {
            BlockPaletteDefinition def = plugin.getBlockPaletteCatalog().get(paletteId.trim());
            if (def != null && !def.getDisplayName().isBlank()) {
                return def.getDisplayName();
            }
        }
        return paletteId.trim();
    }

    @Nullable
    private static BsonDocument readRoot(@Nullable ItemStack stack) {
        if (ItemStack.isEmpty(stack)) {
            return null;
        }
        return stack.getFromMetadataOrNull(BSON_KEY, AetherhavenBsonCodecs.BSON_DOCUMENT);
    }
}
