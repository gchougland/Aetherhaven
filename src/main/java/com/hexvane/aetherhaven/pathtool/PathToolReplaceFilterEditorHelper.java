package com.hexvane.aetherhaven.pathtool;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;

import com.hypixel.hytale.math.util.ChunkUtil;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import java.util.Iterator;
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

    /** Safe during interactions; never loads chunks (see {@link PathToolReplacePredicate}). */
    @Nullable
    public static String resolveBlockIdAt(@Nonnull World world, @Nonnull Vector3i pos) {
        int x = pos.x;
        int y = pos.y;
        int z = pos.z;
        if (y < 0 || y >= 320) {
            return null;
        }
        WorldChunk chunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return null;
        }
        BlockType blockType = ChunkSectionBlockUtil.blockType(world, x, y, z);
        if (blockType == null || blockType == BlockType.EMPTY) {
            return null;
        }
        String id = blockType.getId();
        return id != null && !id.isBlank() ? id.trim() : null;
    }

    public static boolean containsBlockId(@Nonnull Set<String> stored, @Nonnull String worldBlockId) {
        for (String entry : stored) {
            if (storedMatchesWorldBlock(entry, worldBlockId)) {
                return true;
            }
        }
        return false;
    }

    public static boolean removeMatchingBlockId(@Nonnull LinkedHashSet<String> stored, @Nonnull String worldBlockId) {
        Iterator<String> it = stored.iterator();
        while (it.hasNext()) {
            if (storedMatchesWorldBlock(it.next(), worldBlockId)) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    /** Matches block ids, item ids, and block-only assets saved in the replace-filter chest. */
    private static boolean storedMatchesWorldBlock(@Nonnull String stored, @Nonnull String worldBlockId) {
        if (stored.equals(worldBlockId)) {
            return true;
        }
        @Nullable
        BlockType storedBlock = BlockType.getAssetMap().getAsset(stored);
        if (storedBlock != null && worldBlockId.equals(storedBlock.getId())) {
            return true;
        }
        @Nullable
        Item storedItem = Item.getAssetMap().getAsset(stored);
        if (storedItem != null && storedItem.hasBlockType()) {
            @Nullable
            String blockId = storedItem.getBlockId();
            if (blockId != null && worldBlockId.equals(blockId.trim())) {
                return true;
            }
        }
        @Nullable
        Item worldItem = Item.getAssetMap().getAsset(worldBlockId);
        return worldItem != null && stored.equals(worldItem.getId());
    }
}
