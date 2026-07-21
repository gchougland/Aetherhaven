package com.hexvane.aetherhaven.pathtool;

import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Single-chest grid of block items = allowed replace targets for the path tool. */
public final class PathToolReplaceFilterEditorHelper {
    public static final short CAPACITY = 27;

    /** Shown in the replace-filter chest when the player has not saved a custom list (matches default replace rules). */
    private static final List<String> DEFAULT_DISPLAY_BLOCK_IDS = List.of(
        "Soil_Grass",
        "Soil_Grass_Deep",
        "Soil_Grass_Sunny",
        "Soil_Grass_Full",
        "Soil_Grass_Dry"
    );

    private PathToolReplaceFilterEditorHelper() {}

    @Nonnull
    public static Set<String> defaultDisplayBlockIds() {
        return new LinkedHashSet<>(DEFAULT_DISPLAY_BLOCK_IDS);
    }

    @Nonnull
    public static SimpleItemContainer createContainer() {
        return new SimpleItemContainer(CAPACITY);
    }

    public static void loadBlockIdsIntoContainer(@Nonnull SimpleItemContainer container, @Nonnull Set<String> blockIds) {
        container.clear();
        short slot = 0;
        for (String blockId : blockIds) {
            if (blockId == null || blockId.isBlank() || slot >= CAPACITY) {
                continue;
            }
            @Nullable
            String itemId = itemIdForBlock(blockId.trim());
            if (itemId == null) {
                continue;
            }
            container.setItemStackForSlot(slot++, new ItemStack(itemId, 1));
        }
    }

    @Nonnull
    public static Set<String> snapshotContainer(@Nonnull SimpleItemContainer container) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (short slot = 0; slot < CAPACITY; slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) {
                continue;
            }
            @Nullable
            String blockId = blockIdFromStack(stack);
            if (blockId != null) {
                out.add(blockId);
            }
        }
        return out;
    }

    /** Block type id for world replace checks ({@link PathToolReplacePredicate}), not the inventory item id. */
    @Nullable
    public static String blockIdFromStack(@Nonnull ItemStack stack) {
        String itemId = stack.getItemId();
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        Item item = Item.getAssetMap().getAsset(itemId);
        if (item != null && item.hasBlockType()) {
            String blockId = item.getBlockId();
            if (blockId != null && !blockId.isBlank()) {
                return blockId.trim();
            }
        }
        com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType blockOnly =
            com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.getAssetMap().getAsset(itemId);
        if (blockOnly != null && blockOnly.getId() != null && !blockOnly.getId().isBlank()) {
            return blockOnly.getId().trim();
        }
        return null;
    }

    @Nullable
    private static String itemIdForBlock(@Nonnull String blockId) {
        Item item = Item.getAssetMap().getAsset(blockId);
        if (item != null && item.hasBlockType()) {
            return item.getId();
        }
        return blockId;
    }
}
