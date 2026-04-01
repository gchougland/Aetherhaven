package com.hexvane.aetherhaven.poi;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Validates POI cell against optional expected block type (same idea as {@link PoiExtractor}). */
public final class PoiMoveValidation {
    private PoiMoveValidation() {}

    /** When {@code expectedType} is null, any block is accepted. */
    public static boolean matchesExpectedBlock(
        @Nonnull World world,
        int x,
        int y,
        int z,
        @Nullable String expectedType
    ) {
        if (expectedType == null || expectedType.isBlank()) {
            return true;
        }
        BlockType bt = world.getBlockType(x, y, z);
        return bt != null && expectedType.equals(bt.getId());
    }
}
