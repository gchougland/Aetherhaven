package com.hexvane.aetherhaven.pathtool;

import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import javax.annotation.Nullable;

/** Foliage the path tool may strip above a walk surface or ignore when grounding. */
public final class PathFoliageUtil {
    private PathFoliageUtil() {}

    public static boolean isPlantGrassId(@Nullable String blockId) {
        return blockId != null && blockId.contains("Plant_Grass");
    }

    public static boolean isPlantBushId(@Nullable String blockId) {
        return blockId != null && blockId.startsWith("Plant_Bush");
    }

    public static boolean isFoliageBlock(@Nullable BlockType blockType) {
        if (blockType == null || blockType == BlockType.EMPTY) {
            return false;
        }
        String id = blockType.getId();
        if (isPlantGrassId(id) || isPlantBushId(id)) {
            return true;
        }
        return blockType.getMaterial() != BlockMaterial.Solid;
    }

    /**
     * Blocks in the cell above a solid foot block that should not prevent choosing that foot as the path surface.
     */
    public static boolean isWalkSurfaceOverlay(@Nullable BlockType blockAbove) {
        if (blockAbove == null || blockAbove == BlockType.EMPTY) {
            return true;
        }
        if (PathRubbleUtil.isRubble(blockAbove)) {
            return true;
        }
        String id = blockAbove.getId();
        if (isPlantGrassId(id) || isPlantBushId(id)) {
            return true;
        }
        return blockAbove.getMaterial() != BlockMaterial.Solid;
    }

    /** Foliage the path may remove from the column above a placed surface cell. */
    public static boolean isClearableAbovePath(@Nullable BlockType blockType) {
        if (blockType == null || blockType == BlockType.EMPTY) {
            return false;
        }
        if (PathRubbleUtil.isRubble(blockType)) {
            return true;
        }
        String id = blockType.getId();
        return isPlantGrassId(id) || isPlantBushId(id);
    }
}
