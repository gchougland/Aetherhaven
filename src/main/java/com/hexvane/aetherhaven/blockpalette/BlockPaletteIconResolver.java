package com.hexvane.aetherhaven.blockpalette;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Resolves palette icon block ids, with trunk fallbacks when the configured block does not exist. */
public final class BlockPaletteIconResolver {
    private BlockPaletteIconResolver() {}

    @Nullable
    public static String resolveIconBlockId(@Nonnull BlockPaletteDefinition def) {
        String configured = def.getIconBlockId();
        if (!configured.isBlank() && assetExists(configured)) {
            return configured;
        }
        if (BlockPaletteConstants.CATEGORY_TRUNKS.equals(def.getCategory())) {
            String familyKey = def.getFamilyKey();
            if (!familyKey.isBlank()) {
                return firstExisting(
                    "Wood_" + familyKey + "_Trunk",
                    "Wood_" + familyKey + "_Planks",
                    "Wood_" + familyKey + "_Roof"
                );
            }
        }
        return configured.isBlank() || !assetExists(configured) ? null : configured;
    }

    /** UI / inventory icon path for a palette (block item icon when available). */
    @Nonnull
    public static String resolveIconAssetPath(@Nonnull BlockPaletteDefinition def) {
        String blockId = resolveIconBlockId(def);
        if (blockId == null || blockId.isBlank()) {
            return "Icons/ItemsGenerated/Furniture_Village_Crate.png";
        }
        BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
        if (blockType != null) {
            Item item = blockType.getItem();
            if (item != null) {
                String icon = item.getIcon();
                if (icon != null && !icon.isBlank()) {
                    return icon.trim();
                }
            }
        }
        return "Icons/ItemsGenerated/" + blockId.trim() + ".png";
    }

    @Nullable
    private static String firstExisting(@Nonnull String... ids) {
        for (String id : ids) {
            if (assetExists(id)) {
                return id;
            }
        }
        return null;
    }

    private static boolean assetExists(@Nonnull String blockTypeId) {
        return BlockType.getAssetMap().getAsset(blockTypeId) != null;
    }
}
