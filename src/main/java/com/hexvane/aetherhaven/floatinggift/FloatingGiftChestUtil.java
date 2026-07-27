package com.hexvane.aetherhaven.floatinggift;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.blocktype.component.BlockPhysics;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.entity.entities.player.windows.WindowManager;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class FloatingGiftChestUtil {
    public static final String CHEST_WHITE = "Aetherhaven_Floating_Gift_Chest_White";
    public static final String CHEST_GREEN = "Aetherhaven_Floating_Gift_Chest_Green";
    public static final String CHEST_RED = "Aetherhaven_Floating_Gift_Chest_Red";

    private FloatingGiftChestUtil() {}

    public static boolean isGiftChestBlockId(@Nullable String blockTypeId) {
        if (blockTypeId == null || blockTypeId.isBlank()) {
            return false;
        }
        return CHEST_WHITE.equals(blockTypeId)
            || CHEST_GREEN.equals(blockTypeId)
            || CHEST_RED.equals(blockTypeId);
    }

    /** True for base block or OpenWindow / CloseWindow interaction states. */
    public static boolean isGiftChestBlockType(@Nullable BlockType blockType) {
        if (blockType == null || blockType == BlockType.EMPTY) {
            return false;
        }
        String base = blockType.getDefaultStateKey();
        if (base == null || base.isBlank()) {
            base = blockType.getId();
        }
        return isGiftChestBlockId(base);
    }

    public static boolean isContainerEmpty(@Nonnull ItemContainer container) {
        return container.countItemStacks(stack -> stack != null && !ItemStack.isEmpty(stack)) == 0;
    }

    /**
     * Removes a floating gift chest when its inventory is empty. Does not drop the chest block item.
     */
    public static void removeEmptyChest(@Nonnull World world, @Nonnull Vector3i pos) {
        Runnable action = () -> removeEmptyChestOnWorldThread(world, pos);
        if (world.isInThread()) {
            action.run();
        } else {
            world.execute(action);
        }
    }

    private static void removeEmptyChestOnWorldThread(@Nonnull World world, @Nonnull Vector3i pos) {
        BlockType blockType = world.getBlockType(pos.x, pos.y, pos.z);
        if (blockType == null || blockType == BlockType.EMPTY) {
            return;
        }
        if (!isGiftChestBlockType(blockType)) {
            return;
        }

        long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z);
        WorldChunk chunk = world.getChunkIfInMemory(chunkIndex);
        if (chunk == null) {
            return;
        }
        var chunkRef = world.getChunkStore().getChunkReference(chunkIndex);
        if (chunkRef == null || !chunkRef.isValid()) {
            return;
        }
        var chunkStore = world.getChunkStore().getStore();
        var blockComponentChunk = chunkStore.getComponent(chunkRef, BlockComponentChunk.getComponentType());
        if (blockComponentChunk == null) {
            return;
        }
        int columnIndex = ChunkUtil.indexBlockInColumn(pos.x, pos.y, pos.z);
        var blockRef = blockComponentChunk.getEntityReference(columnIndex);
        if (blockRef == null || !blockRef.isValid()) {
            return;
        }
        ItemContainerBlock chest = chunkStore.getComponent(blockRef, ItemContainerBlock.getComponentType());
        if (chest == null || chest.getItemContainer() == null) {
            return;
        }
        if (!isContainerEmpty(chest.getItemContainer())) {
            return;
        }
        WindowManager.closeAndRemoveAll(chest.getWindows());

        chunk.breakBlock(pos.x, pos.y, pos.z, SetBlockSettings.NO_DROP_ITEMS);
    }

    /**
     * Marks a placed gift chest as decorative so block physics skips support checks (same as player placing a plot sign).
     */
    public static void markDecoPlaced(@Nonnull World world, int x, int y, int z) {
        BlockType blockType = world.getBlockType(x, y, z);
        if (blockType == null || !isGiftChestBlockType(blockType)) {
            return;
        }
        var chunkStore = world.getChunkStore();
        Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReferenceAtBlock(x, y, z);
        if (sectionRef == null || !sectionRef.isValid()) {
            return;
        }
        BlockPhysics.markDeco(chunkStore.getStore(), sectionRef, x, y, z);
    }
}
