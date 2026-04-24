package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.ResourceType;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Shared item / material line labels for Custom UI (plot construction, feasts, etc.). */
public final class UiMaterialLabels {
    private UiMaterialLabels() {}

    @Nonnull
    public static String itemLabelForUi(@Nullable String language, @Nonnull String itemId) {
        Item item = Item.getAssetMap().getAsset(itemId);
        if (item == null) {
            return itemId;
        }
        String trKey = item.getTranslationKey();
        String lang = language != null ? language : "en-US";
        String resolved = I18nModule.get().getMessage(lang, trKey);
        return resolved != null ? resolved : itemId;
    }

    @Nonnull
    public static String materialLabelForUi(@Nullable String language, @Nonnull MaterialRequirement m) {
        String rt = m.getResourceTypeId();
        if (rt != null && !rt.isBlank()) {
            String id = rt.trim();
            String lang = language != null ? language : "en-US";
            String key = "server.resourceType." + id + ".name";
            String resolved = I18nModule.get().getMessage(lang, key);
            if (resolved != null) {
                return resolved;
            }
            ResourceType asset = ResourceType.getAssetMap().getAsset(id);
            if (asset != null) {
                String n = asset.getName();
                if (n != null && !n.isBlank()) {
                    return n;
                }
            }
            return id;
        }
        String itemId = m.getItemId();
        return itemId != null && !itemId.isBlank() ? itemLabelForUi(language, itemId) : "?";
    }
}
