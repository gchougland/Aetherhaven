package com.hexvane.aetherhaven.blockpalette;

import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Dedicated shop item ids for block palettes sold without BSON metadata ({@code Aetherhaven_Block_Palette_Shop_*}).
 * The generic {@link BlockPaletteConstants#ITEM_ID} still needs metadata; these thin parents do not.
 */
public final class BlockPaletteShopItemIds {
    private static final String PREFIX = "Aetherhaven_Block_Palette_";

    private BlockPaletteShopItemIds() {}

    /** {@code modern_cloth_roofs_red} → {@code Aetherhaven_Block_Palette_Modern_Cloth_Roofs_Red}. */
    @Nonnull
    public static String forPaletteId(@Nonnull String paletteId) {
        String[] parts = paletteId.trim().split("_+");
        StringBuilder sb = new StringBuilder(PREFIX);
        boolean any = false;
        for (String p : parts) {
            if (p.isBlank()) {
                continue;
            }
            if (any) {
                sb.append('_');
            }
            sb.append(Character.toUpperCase(p.charAt(0)));
            if (p.length() > 1) {
                sb.append(p.substring(1).toLowerCase(Locale.ROOT));
            }
            any = true;
        }
        return sb.toString();
    }

    /**
     * Inverse of {@link #forPaletteId}: {@code Aetherhaven_Block_Palette_Trunks_Jungle} → {@code trunks_jungle}.
     * Returns null for the generic palette item, virtual icon ids, or unrelated ids.
     */
    @Nullable
    public static String paletteIdFromItemId(@Nullable String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        if (BlockPaletteConstants.ITEM_ID.equals(itemId)
            || BlockPaletteVirtualItemRegistry.isVirtualId(itemId)) {
            return null;
        }
        if (!itemId.startsWith(PREFIX)) {
            return null;
        }
        String rest = itemId.substring(PREFIX.length());
        return rest.isBlank() ? null : rest.toLowerCase(Locale.ROOT);
    }
}
