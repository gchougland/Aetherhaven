package com.hexvane.aetherhaven.prop;

import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Dedicated shop item ids for props sold without BSON metadata ({@code Aetherhaven_Prop_&lt;Name&gt;}). The generic
 * {@link PropItemMetadata#PROP_ITEM_ID} still needs metadata; these thin parents do not.
 */
public final class PropShopItemIds {
    private static final String PREFIX = "Aetherhaven_Prop_";

    private PropShopItemIds() {}

    /** {@code cabbage_trough} → {@code Aetherhaven_Prop_Cabbage_Trough}. */
    @Nonnull
    public static String forPropId(@Nonnull String propId) {
        String[] parts = propId.trim().split("_+");
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
     * Inverse of {@link #forPropId}: {@code Aetherhaven_Prop_Cabbage_Trough} → {@code cabbage_trough}. Returns null for
     * the generic crate item (needs metadata) or unrelated ids.
     */
    @Nullable
    public static String propIdFromItemId(@Nullable String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        if (PropItemMetadata.PROP_ITEM_ID.equals(itemId)) {
            return null;
        }
        if (!itemId.startsWith(PREFIX)) {
            return null;
        }
        String rest = itemId.substring(PREFIX.length());
        return rest.isBlank() ? null : rest.toLowerCase(Locale.ROOT);
    }
}
