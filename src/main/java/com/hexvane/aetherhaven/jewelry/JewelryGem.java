package com.hexvane.aetherhaven.jewelry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public enum JewelryGem {
    ZEPHYR,
    TOPAZ,
    EMERALD,
    DIAMOND,
    SAPPHIRE,
    RUBY,
    VOIDSTONE;

    @Nullable
    public static JewelryGem fromItemId(@Nonnull String itemId) {
        String resolveId = itemId;
        String baseId = JewelryVirtualItemRegistry.getBaseItemId(itemId);
        if (baseId != null) {
            resolveId = baseId;
        }
        int u = resolveId.lastIndexOf('_');
        if (u < 0 || u >= resolveId.length() - 1) {
            return null;
        }
        String suffix = resolveId.substring(u + 1);
        try {
            return JewelryGem.valueOf(suffix.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
