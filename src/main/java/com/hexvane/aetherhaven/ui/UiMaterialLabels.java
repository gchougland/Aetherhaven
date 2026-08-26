package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.construction.MaterialRequirement;
import com.hypixel.hytale.server.core.Message;
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

    /** Resolved item name for Custom UI / HUD {@link Message} params (mod and vanilla items). */
    @Nonnull
    public static Message itemNameMessage(@Nonnull String itemId) {
        Item item = Item.getAssetMap().getAsset(itemId);
        if (item != null && item.getTranslationKey() != null && !item.getTranslationKey().isBlank()) {
            return Message.translation(item.getTranslationKey());
        }
        return Message.translation("server.items." + itemId + ".name");
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
            return id;
        }
        String itemId = m.getItemId();
        return itemId != null && !itemId.isBlank() ? itemLabelForUi(language, itemId) : "?";
    }

    @Nonnull
    public static String displayLabelFor(@Nonnull MaterialRequirement line) {
        return materialLabelForUi("en-US", line);
    }

    /** True when {@code server.resourceType.<id>.name} resolves for the given language. */
    public static boolean hasResourceTypeLangLabel(@Nonnull String resourceTypeId) {
        return hasResourceTypeLangLabel("en-US", resourceTypeId);
    }

    public static boolean hasResourceTypeLangLabel(@Nullable String language, @Nonnull String resourceTypeId) {
        String id = resourceTypeId.trim();
        if (id.isBlank()) {
            return false;
        }
        String lang = language != null ? language : "en-US";
        String resolved = I18nModule.get().getMessage(lang, "server.resourceType." + id + ".name");
        return resolved != null && !resolved.isBlank();
    }
}
