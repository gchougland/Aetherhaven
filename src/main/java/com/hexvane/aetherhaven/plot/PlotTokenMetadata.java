package com.hexvane.aetherhaven.plot;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.jewelry.AetherhavenBsonCodecs;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bson.BsonDocument;
import org.bson.BsonString;

/** BSON metadata on {@link com.hexvane.aetherhaven.AetherhavenConstants#PLOT_TOKEN_UNIFIED} stacks. */
public final class PlotTokenMetadata {
    public static final String BSON_KEY = "AetherhavenPlotToken";
    public static final String FIELD_CONSTRUCTION_ID = "constructionId";
    public static final String FIELD_DISPLAY_NAME = "displayName";

    /** Plain resolved strings for inventory tooltips (same key as vanilla {@code TranslationProperties}). */
    public static final String INSTANCE_TRANSLATION_PROPERTIES_KEY = "TranslationProperties";

    private static final String LANG_NAME = "aetherhaven_items.items.Aetherhaven_Plot_Token.instance.name";
    private static final String LANG_DESC = "aetherhaven_items.items.Aetherhaven_Plot_Token.instance.description";

    private PlotTokenMetadata() {}

    @Nonnull
    public static ItemStack withConstruction(@Nonnull ItemStack base, @Nonnull String constructionId, @Nullable String displayName) {
        return withConstruction(base, constructionId, displayName, null);
    }

    @Nonnull
    public static ItemStack withConstruction(
        @Nonnull ItemStack base,
        @Nonnull String constructionId,
        @Nullable String displayName,
        @Nullable String language
    ) {
        BsonDocument root = new BsonDocument();
        root.put(FIELD_CONSTRUCTION_ID, new BsonString(constructionId.trim()));
        String resolvedName = resolveBuildingLabel(displayName, constructionId);
        if (resolvedName != null && !resolvedName.isBlank()) {
            root.put(FIELD_DISPLAY_NAME, new BsonString(resolvedName.trim()));
        }
        BsonDocument meta = new BsonDocument();
        meta.put(BSON_KEY, root);
        ItemStack stack = base.withMetadata(meta);
        return applyInstanceTooltip(stack, resolvedName, constructionId.trim(), language);
    }

    @Nullable
    public static String readConstructionId(@Nullable ItemStack stack) {
        BsonDocument root = readRoot(stack);
        if (root == null) {
            return null;
        }
        var v = root.get(FIELD_CONSTRUCTION_ID);
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

    public static boolean matchesConstruction(@Nullable ItemStack stack, @Nonnull String constructionId) {
        String onStack = readConstructionId(stack);
        return onStack != null && onStack.equals(constructionId.trim());
    }

    @Nonnull
    private static ItemStack applyInstanceTooltip(
        @Nonnull ItemStack stack,
        @Nullable String buildingLabel,
        @Nonnull String constructionId,
        @Nullable String language
    ) {
        if (buildingLabel == null || buildingLabel.isBlank()) {
            return stack;
        }
        String label = buildingLabel.trim();
        String lang = language != null && !language.isBlank() ? language : "en-US";
        String namePlain = resolveLang(lang, LANG_NAME, label);
        String descPlain = resolveDescription(lang, label, constructionId);

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
    private static String resolveDescription(
        @Nonnull String language,
        @Nonnull String buildingLabel,
        @Nonnull String constructionId
    ) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin != null) {
            ConstructionDefinition def = plugin.getConstructionCatalog().get(constructionId.trim());
            if (def != null) {
                String custom = def.getDescription();
                if (custom != null && !custom.isBlank()) {
                    return custom.trim();
                }
            }
        }
        return resolveLang(language, LANG_DESC, buildingLabel);
    }

    @Nonnull
    private static String resolveLang(@Nonnull String language, @Nonnull String key, @Nonnull String buildingLabel) {
        I18nModule i18n = I18nModule.get();
        String text = i18n != null ? i18n.getMessage(language, key) : null;
        if (text == null || text.isBlank()) {
            text = key;
        }
        return text.replace("{building}", buildingLabel);
    }

    @Nullable
    private static String resolveBuildingLabel(@Nullable String displayName, @Nonnull String constructionId) {
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        if (plugin != null) {
            ConstructionDefinition def = plugin.getConstructionCatalog().get(constructionId.trim());
            if (def != null && def.getDisplayName() != null && !def.getDisplayName().isBlank()) {
                return def.getDisplayName().trim();
            }
        }
        return constructionId.trim();
    }

    @Nullable
    private static BsonDocument readRoot(@Nullable ItemStack stack) {
        if (ItemStack.isEmpty(stack)) {
            return null;
        }
        return stack.getFromMetadataOrNull(BSON_KEY, AetherhavenBsonCodecs.BSON_DOCUMENT);
    }
}
