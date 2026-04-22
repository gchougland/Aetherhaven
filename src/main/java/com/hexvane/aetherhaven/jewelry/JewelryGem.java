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
        int u = itemId.lastIndexOf('_');
        if (u < 0 || u >= itemId.length() - 1) {
            return null;
        }
        String suffix = itemId.substring(u + 1);
        try {
            return JewelryGem.valueOf(suffix.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
