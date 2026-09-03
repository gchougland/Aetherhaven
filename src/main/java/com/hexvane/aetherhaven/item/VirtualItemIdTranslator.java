package com.hexvane.aetherhaven.item;

import com.hexvane.aetherhaven.blockpalette.BlockPaletteVirtualItemRegistry;
import com.hexvane.aetherhaven.jewelry.JewelryVirtualItemRegistry;
import com.hexvane.aetherhaven.plot.PlotTokenVirtualItemRegistry;
import com.hexvane.aetherhaven.prop.PropVirtualItemRegistry;
import javax.annotation.Nullable;

/**
 * Maps a client-facing virtual item id back to the real item id the server stores on the {@code ItemStack}.
 *
 * <p>Props, plot tokens, block palettes and jewelry are all sent to the client as per-variant clones of one real item
 * so each can carry its own icon. The client then echoes those ids back in interaction packets, and
 * {@code InteractionManager} cancels the chain when they do not equal the server's held item id, so every inbound
 * packet carrying an item id has to be mapped back first.
 */
public final class VirtualItemIdTranslator {
    private VirtualItemIdTranslator() {}

    /** Returns the real server item id for {@code itemId}, or {@code itemId} unchanged if it is not virtual. */
    @Nullable
    public static String toBaseItemId(@Nullable String itemId) {
        if (itemId == null || itemId.isEmpty()) {
            return itemId;
        }
        if (PropVirtualItemRegistry.isVirtualId(itemId)) {
            return PropVirtualItemRegistry.getBaseItemId(itemId);
        }
        if (BlockPaletteVirtualItemRegistry.isVirtualId(itemId)) {
            return BlockPaletteVirtualItemRegistry.getBaseItemId(itemId);
        }
        if (PlotTokenVirtualItemRegistry.isVirtualId(itemId)) {
            return orSelf(PlotTokenVirtualItemRegistry.getBaseItemId(itemId), itemId);
        }
        // Checked last: jewelry ids are "<base>__ah_r_<rarity>", so the match is a plain separator search.
        if (JewelryVirtualItemRegistry.isVirtualId(itemId)) {
            return orSelf(JewelryVirtualItemRegistry.getBaseItemId(itemId), itemId);
        }
        return itemId;
    }

    private static String orSelf(@Nullable String baseId, String itemId) {
        return baseId != null ? baseId : itemId;
    }
}
