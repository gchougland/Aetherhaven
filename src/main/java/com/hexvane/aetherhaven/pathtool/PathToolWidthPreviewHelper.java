package com.hexvane.aetherhaven.pathtool;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hexvane.aetherhaven.config.PathToolStyleDefinition;
import com.hexvane.aetherhaven.ui.ItemAssetImagePath;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.util.Random;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Width page cross-section block ids and UI icon paths. */
public final class PathToolWidthPreviewHelper {
    private PathToolWidthPreviewHelper() {}

    @Nullable
    public static String assetPathForBlockId(@Nullable String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return null;
        }
        BlockType bt = BlockType.getAssetMap().getAsset(blockId.trim());
        if (bt == null) {
            return null;
        }
        Item item = bt.getItem();
        if (item != null) {
            String id = item.getId();
            if (id != null && !id.isBlank()) {
                return ItemAssetImagePath.forItem(item, id);
            }
        }
        return ItemAssetImagePath.forItem(null, blockId.trim());
    }

    @Nonnull
    public static String blockIdForPreviewCell(
        @Nonnull AetherhavenPluginConfig cfg,
        int pathStyleIndex,
        int pathWidthBlocks,
        int lateralIndex,
        @Nonnull Random random
    ) {
        int w = Math.max(1, Math.min(PathToolStyleDefinition.MAX_PATH_WIDTH_BLOCKS, pathWidthBlocks));
        int lat = Math.max(0, Math.min(w - 1, lateralIndex));
        var styles = cfg.getPathToolStyleDefinitions();
        PathToolStyleDefinition style = null;
        if (!styles.isEmpty()) {
            style = styles.get(Math.floorMod(pathStyleIndex, styles.size()));
        }
        if (style != null && style.hasColumnLayout()) {
            return style.pickBlockForPathCell(lat, w, random);
        }
        PathPlannedCell.CellRole role = lateralRole(lat, w);
        if (role == PathPlannedCell.CellRole.Outline) {
            return random.nextBoolean()
                ? AetherhavenConstants.PATH_BLOCK_GRASS
                : AetherhavenConstants.PATH_BLOCK_GRASS_DEEP;
        }
        if (style != null && style.isUsable()) {
            var centers = style.getCenterBlockIds();
            if (!centers.isEmpty()) {
                return centers.get(random.nextInt(centers.size()));
            }
        }
        return random.nextBoolean()
            ? AetherhavenConstants.PATH_BLOCK_PATHWAY
            : AetherhavenConstants.PATH_BLOCK_MUD_DRY;
    }

    @Nonnull
    private static PathPlannedCell.CellRole lateralRole(int lateralIndex, int pathWidthBlocks) {
        if (pathWidthBlocks < 3) {
            return PathPlannedCell.CellRole.Center;
        }
        return lateralIndex == 0 || lateralIndex == pathWidthBlocks - 1
            ? PathPlannedCell.CellRole.Outline
            : PathPlannedCell.CellRole.Center;
    }

    public static long previewSeed(int pathStyleIndex, int width, int rowIndex) {
        return 31L * pathStyleIndex + 17L * width + rowIndex * 9973L;
    }
}
