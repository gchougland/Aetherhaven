package com.hexvane.aetherhaven.plot;

import com.hexvane.aetherhaven.AetherhavenConstants;
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

/** BSON metadata on {@link AetherhavenConstants#PLOT_TOKEN_UNLOCK_PAGE} stacks. */
public final class PlotTokenUnlockPageMetadata {
    public static final String BSON_KEY = "AetherhavenPlotTokenUnlock";
    public static final String FIELD_CONSTRUCTION_ID = "constructionId";

    public static final String INSTANCE_TRANSLATION_PROPERTIES_KEY = "TranslationProperties";

    private static final String LANG_NAME = "aetherhaven_items.items.Aetherhaven_Plot_Token_Unlock_Page.instance.name";
    private static final String LANG_DESC = "aetherhaven_items.items.Aetherhaven_Plot_Token_Unlock_Page.instance.description";

    private PlotTokenUnlockPageMetadata() {}

    @Nonnull
    public static ItemStack createGenericStack() {
        return new ItemStack(AetherhavenConstants.PLOT_TOKEN_UNLOCK_PAGE, 1);
    }

    @Nonnull
    public static ItemStack createStack(@Nonnull String constructionId) {
        return createStack(constructionId, null);
    }

    @Nonnull
    public static ItemStack createStack(@Nonnull String constructionId, @Nullable String language) {
        ItemStack base = new ItemStack(AetherhavenConstants.PLOT_TOKEN_UNLOCK_PAGE, 1);
        BsonDocument root = new BsonDocument();
        root.put(FIELD_CONSTRUCTION_ID, new BsonString(constructionId.trim()));
        BsonDocument meta = new BsonDocument();
        meta.put(BSON_KEY, root);
        ItemStack stack = base.withMetadata(meta);
        String label = resolveBuildingLabel(constructionId);
        return applyInstanceTooltip(stack, label, language);
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

    @Nonnull
    private static ItemStack applyInstanceTooltip(
        @Nonnull ItemStack stack,
        @Nonnull String buildingLabel,
        @Nullable String language
    ) {
        String lang = language != null && !language.isBlank() ? language : "en-US";
        String namePlain = resolveLang(lang, LANG_NAME, buildingLabel);
        String descPlain = resolveLang(lang, LANG_DESC, buildingLabel);

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
    private static String resolveLang(@Nonnull String language, @Nonnull String key, @Nonnull String buildingLabel) {
        I18nModule i18n = I18nModule.get();
        String text = i18n != null ? i18n.getMessage(language, key) : null;
        if (text == null || text.isBlank()) {
            text = key;
        }
        return text.replace("{building}", buildingLabel);
    }

    @Nonnull
    private static String resolveBuildingLabel(@Nonnull String constructionId) {
        String label = PlotTokenUnlockService.displayNameFor(constructionId);
        return label != null && !label.isBlank() ? label : constructionId.trim();
    }

    @Nullable
    private static BsonDocument readRoot(@Nullable ItemStack stack) {
        if (ItemStack.isEmpty(stack)) {
            return null;
        }
        if (!AetherhavenConstants.PLOT_TOKEN_UNLOCK_PAGE.equals(stack.getItemId())) {
            return null;
        }
        return stack.getFromMetadataOrNull(BSON_KEY, AetherhavenBsonCodecs.BSON_DOCUMENT);
    }
}
