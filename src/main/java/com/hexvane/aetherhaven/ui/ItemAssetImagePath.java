package com.hexvane.aetherhaven.ui;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Resolves UI {@code AssetImage.AssetPath} from item data so grids can avoid {@code ItemSlot} (and its built-in item tooltips). */
public final class ItemAssetImagePath {
    private ItemAssetImagePath() {}

    /**
     * Uses {@link Item#getIcon()} when present; otherwise the same default as the asset editor preview:
     * {@code Icons/ItemsGenerated/&lt;itemId&gt;.png}.
     */
    @Nonnull
    public static String forItem(@Nullable Item item, @Nonnull String itemId) {
        if (item != null) {
            String icon = item.getIcon();
            if (icon != null && !icon.isBlank()) {
                return icon.trim();
            }
        }
        return "Icons/ItemsGenerated/" + itemId.trim() + ".png";
    }
}
