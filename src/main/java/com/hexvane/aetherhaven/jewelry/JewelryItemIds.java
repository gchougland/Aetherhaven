package com.hexvane.aetherhaven.jewelry;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class JewelryItemIds {
    private static final String PREFIX_RING = "Aetherhaven_Ring_";
    private static final String PREFIX_NECKLACE = "Aetherhaven_Necklace_";

    private JewelryItemIds() {}

    public static boolean isJewelry(@Nullable String itemId) {
        return isRing(itemId) || isNecklace(itemId);
    }

    public static boolean isRing(@Nullable String itemId) {
        return itemId != null && itemId.startsWith(PREFIX_RING);
    }

    public static boolean isNecklace(@Nullable String itemId) {
        return itemId != null && itemId.startsWith(PREFIX_NECKLACE);
    }

    /** @return 0 ring1, 1 ring2, 2 necklace, or -1 if invalid */
    public static int targetSlotFor(@Nonnull String itemId) {
        if (isRing(itemId)) {
            return 0;
        }
        if (isNecklace(itemId)) {
            return 2;
        }
        return -1;
    }

    /** Rings may use ring1 or ring2; caller picks first free or chosen slot. */
    public static boolean canPlaceInRingSlot(@Nonnull String itemId) {
        return isRing(itemId);
    }
}
