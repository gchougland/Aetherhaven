package com.hexvane.aetherhaven.blockpalette;

import javax.annotation.Nonnull;

/** Asset-pack paths for block palette JSON. */
public final class BlockPalettePaths {
    /** Relative to pack root: recursive JSON under this folder. */
    public static final String PACK_RELATIVE = "Server/Aetherhaven/BlockPalettes";

    private BlockPalettePaths() {}

    @Nonnull
    public static String packPrefix() {
        return PACK_RELATIVE + "/";
    }
}
