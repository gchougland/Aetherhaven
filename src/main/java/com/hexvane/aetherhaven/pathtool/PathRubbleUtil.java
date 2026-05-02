package com.hexvane.aetherhaven.pathtool;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import javax.annotation.Nullable;

/** Decorative rubble piles sit on terrain; path tooling treats them as clearable overlays, not the walk surface. */
public final class PathRubbleUtil {
    private PathRubbleUtil() {}

    public static boolean isRubble(@Nullable BlockType blockType) {
        if (blockType == null || blockType == BlockType.EMPTY) {
            return false;
        }
        String id = blockType.getId();
        return id != null && id.startsWith("Rubble_");
    }
}
