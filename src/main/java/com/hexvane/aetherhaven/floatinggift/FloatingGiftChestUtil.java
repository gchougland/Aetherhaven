package com.hexvane.aetherhaven.floatinggift;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.blocktype.component.BlockPhysics;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.entity.entities.player.windows.WindowManager;
import com.hypixel.hytale.server.core.modules.block.BlockEntity;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
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
     * Ensures the gift chest has a live {@link ItemContainerBlock}. Chests placed with
     * {@code NO_UPDATE_STATE} (or in a non-ticking section) can exist as a block with no entity.
     */
    @Nullable
    public static ItemContainerBlock ensureItemContainer(@Nonnull World world, int x, int y, int z) {
        Ref<ChunkStore> blockRef = ChunkSectionBlockUtil.blockEntityRefAt(world, x, y, z);
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        if (blockRef != null && blockRef.isValid()) {
            ItemContainerBlock existing = chunkStore.getComponent(blockRef, ItemContainerBlock.getComponentType());
            if (existing != null) {
                return existing;
            }
        }

        BlockType blockType = ChunkSectionBlockUtil.blockType(world, x, y, z);
        if (blockType == null || !isGiftChestBlockType(blockType)) {
            return null;
        }
        Holder<ChunkStore> template = blockType.getBlockEntity();
        if (template == null) {
            return null;
        }

        Ref<ChunkStore> sectionRef = world.getChunkStore().getChunkSectionReferenceAtBlock(x, y, z);
        if (sectionRef == null || !sectionRef.isValid()) {
            return null;
        }
        BlockComponentSection components = chunkStore.getComponent(sectionRef, BlockComponentSection.getComponentType());
        BlockSection section = chunkStore.getComponent(sectionRef, BlockSection.getComponentType());
        if (components == null || section == null) {
            return null;
        }
        int rotation = section.getRotationIndex(ChunkUtil.indexBlock(x, y, z));
        BlockEntity.setBlockEntity(
            chunkStore,
            sectionRef,
            components,
            x,
            y,
            z,
            blockType,
            rotation,
            template.clone()
        );

        blockRef = BlockModule.getBlockEntity(chunkStore, sectionRef, x, y, z);
        if (blockRef == null || !blockRef.isValid()) {
            // Non-ticking sections keep holders only — force a normal place so the entity is live.
            String id = blockType.getId();
            if (id == null || !ChunkSectionBlockUtil.setBlockByKey(world, x, y, z, id, SetBlockSettings.NONE)) {
                return null;
            }
            markDecoPlaced(world, x, y, z);
            blockRef = ChunkSectionBlockUtil.blockEntityRefAt(world, x, y, z);
        }
        if (blockRef == null || !blockRef.isValid()) {
            return null;
        }
        return chunkStore.getComponent(blockRef, ItemContainerBlock.getComponentType());
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
        BlockType blockType = ChunkSectionBlockUtil.blockType(world, pos.x, pos.y, pos.z);
        if (blockType == null || blockType == BlockType.EMPTY) {
            return;
        }
        if (!isGiftChestBlockType(blockType)) {
            return;
        }

        Ref<ChunkStore> blockRef = ChunkSectionBlockUtil.blockEntityRefAt(world, pos.x, pos.y, pos.z);
        if (blockRef == null || !blockRef.isValid()) {
            return;
        }
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        ItemContainerBlock chest = chunkStore.getComponent(blockRef, ItemContainerBlock.getComponentType());
        if (chest == null || chest.getItemContainer() == null) {
            return;
        }
        if (!isContainerEmpty(chest.getItemContainer())) {
            return;
        }
        WindowManager.closeAndRemoveAll(chest.getWindows());

        ChunkSectionBlockUtil.breakBlock(world, pos.x, pos.y, pos.z, SetBlockSettings.NO_DROP_ITEMS);
    }

    /**
     * Marks a placed gift chest as decorative so block physics skips support checks (same as player placing a plot sign).
     */
    public static void markDecoPlaced(@Nonnull World world, int x, int y, int z) {
        BlockType blockType = ChunkSectionBlockUtil.blockType(world, x, y, z);
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
