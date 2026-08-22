package com.hexvane.aetherhaven.blockpalette;

import javax.annotation.Nonnull;

/** Default gold prices for block palettes sold at Cap'n Clive's shop. */
public final class BlockPaletteShopPricing {
    private BlockPaletteShopPricing() {}

    public static long goldPriceFor(@Nonnull BlockPaletteDefinition def) {
        return switch (def.getCategory()) {
            case BlockPaletteConstants.CATEGORY_WALLS, BlockPaletteConstants.CATEGORY_CLOTH -> 45L;
            case BlockPaletteConstants.CATEGORY_TRUNKS, BlockPaletteConstants.CATEGORY_WINDOWS -> 55L;
            case BlockPaletteConstants.CATEGORY_ROOFS,
                BlockPaletteConstants.CATEGORY_CLOTH_ROOFS,
                BlockPaletteConstants.CATEGORY_MODERN_CLOTH_ROOFS -> 50L;
            case BlockPaletteConstants.CATEGORY_COBBLE, BlockPaletteConstants.CATEGORY_BRICKS -> 48L;
            case BlockPaletteConstants.CATEGORY_PLANKS -> 52L;
            default -> 50L;
        };
    }
}
