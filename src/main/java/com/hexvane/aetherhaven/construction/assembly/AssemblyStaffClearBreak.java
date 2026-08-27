package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;

import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.PhysicsDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.SoftBlockDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.HarvestingDropType;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
final class AssemblyStaffClearBreak {
    /** Same basis as {@link BlockHarvestUtils#naturallyRemoveBlockByPhysics}. */
    private static final int NATURAL_BREAK_SETTINGS = 32;

    private AssemblyStaffClearBreak() {}

    /**
     * @return {@code true} when a solid block was removed at {@code cellWorld}.
     */
    static boolean breakWithLoot(
        @Nonnull World world,
        @Nonnull Vector3i cellWorld,
        @Nonnull ComponentAccessor<EntityStore> entityAccessor
    ) {
        // Must not use World.getBlockType: it can loadChunkIfInMemory while the entity store is ticking
        // (PlotAssemblyTickSystem), causing "Store is currently processing!".
        WorldChunk worldChunk = ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(cellWorld.x, cellWorld.z));
        if (worldChunk == null) {
            return false;
        }
        BlockType blockType = ChunkSectionBlockUtil.blockType(world, cellWorld.x, cellWorld.y, cellWorld.z);
        if (blockType == null || blockType == BlockType.EMPTY) {
            return false;
        }
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        if (cellWorld.y < ChunkUtil.MIN_Y || cellWorld.y > ChunkUtil.HEIGHT_MINUS_1) {
            return false;
        }
        Ref<ChunkStore> sectionRef =
            world.getChunkStore().getChunkSectionReferenceAtBlock(cellWorld.x, cellWorld.y, cellWorld.z);
        if (sectionRef == null || !sectionRef.isValid()) {
            return false;
        }
        BlockSection section = chunkStore.getComponent(sectionRef, BlockSection.getComponentType());
        if (section == null) {
            return false;
        }
        int filler = section.getFiller(cellWorld.x, cellWorld.y, cellWorld.z);
        DropInfo drops = resolveDrops(blockType);
        // Must pass the section entity ref — column refs have no BlockSection component.
        BlockHarvestUtils.naturallyRemoveBlock(
            cellWorld,
            blockType,
            filler,
            drops.quantity,
            drops.itemId,
            drops.dropListId,
            NATURAL_BREAK_SETTINGS,
            sectionRef,
            entityAccessor,
            chunkStore
        );
        return true;
    }

    @Nonnull
    private static DropInfo resolveDrops(@Nonnull BlockType blockType) {
        int quantity = 1;
        String itemId = null;
        String dropListId = null;
        BlockGathering gathering = blockType.getGathering();
        if (gathering != null) {
            PhysicsDropType physics = gathering.getPhysics();
            BlockBreakingDropType breaking = gathering.getBreaking();
            SoftBlockDropType soft = gathering.getSoft();
            HarvestingDropType harvest = gathering.getHarvest();
            if (physics != null) {
                itemId = physics.getItemId();
                dropListId = physics.getDropListId();
            } else if (breaking != null) {
                quantity = Math.max(1, breaking.getQuantity());
                itemId = breaking.getItemId();
                dropListId = breaking.getDropListId();
            } else if (soft != null) {
                itemId = soft.getItemId();
                dropListId = soft.getDropListId();
            } else if (harvest != null) {
                itemId = harvest.getItemId();
                dropListId = harvest.getDropListId();
            }
        }
        return new DropInfo(quantity, itemId, dropListId);
    }

    private record DropInfo(int quantity, String itemId, String dropListId) {
        DropInfo(int quantity, String itemId, String dropListId) {
            this.quantity = Math.max(1, quantity);
            this.itemId = itemId;
            this.dropListId = dropListId;
        }
    }
}
