package com.hexvane.aetherhaven.geode;

import com.hexvane.aetherhaven.config.AetherhavenPluginConfig;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Whether a broken block should count as an ore for geode extra drops. */
public final class OreGeodeEligibility {
    private static final String BLOCKS_ORES = "Blocks.Ores";

    private OreGeodeEligibility() {}

    public static boolean isOreBlockForGeode(@Nonnull BlockType blockType, @Nonnull AetherhavenPluginConfig cfg) {
        if (blockType == BlockType.EMPTY) {
            return false;
        }
        String id = blockType.getId();
        if (id != null && cfg.geodeExtraOreBlockTypeIdSet().contains(id.trim())) {
            return true;
        }

        // Prefer BlockType.getItem(): for variants / states the block id may not match an Item asset key.
        Item item = blockType.getItem();
        if (item == null && id != null) {
            item = Item.getAssetMap().getAsset(id);
        }
        Set<String> oreSubs = cfg.geodeOreSubcategorySet();
        Set<String> excluded = cfg.geodeOreExcludedSubcategorySet();

        if (item != null) {
            String sub = item.getSubCategory();
            if (sub != null && oreSubs.contains(sub)) {
                return true;
            }
            if (cfg.isGeodeOreUseBlocksOresCategory() && hasCategory(item, BLOCKS_ORES)) {
                if (sub == null || !excluded.contains(sub)) {
                    return true;
                }
            }
        }

        BlockGathering gathering = resolveGathering(blockType, item);
        if (gathering != null && gathering.getBreaking() != null) {
            String gt = gathering.getBreaking().getGatherType();
            if (gt != null) {
                if (gt.startsWith("Ore")) {
                    return true;
                }
                if (cfg.geodeExtraOreGatherTypeSet().contains(gt)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Some placed block variants expose breaking rules on the root block type for the backing item.
     */
    @Nullable
    private static BlockGathering resolveGathering(@Nonnull BlockType blockType, @Nullable Item item) {
        BlockGathering g = blockType.getGathering();
        if (g != null) {
            return g;
        }
        if (item == null) {
            return null;
        }
        BlockType fromItem = BlockType.getAssetMap().getAsset(item.getId());
        return fromItem != null ? fromItem.getGathering() : null;
    }

    private static boolean hasCategory(@Nonnull Item item, @Nonnull String needle) {
        String[] cats = item.getCategories();
        if (cats == null) {
            return false;
        }
        for (String c : cats) {
            if (needle.equals(c)) {
                return true;
            }
        }
        return false;
    }
}
