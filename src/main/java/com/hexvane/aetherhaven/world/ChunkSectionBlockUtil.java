package com.hexvane.aetherhaven.world;

import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkFlag;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockComponentSection;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3ic;

/**
 * Chunk column and section block access without deprecated {@link World} chunk helpers or
 * {@link WorldChunk#setBlock}.
 */
public final class ChunkSectionBlockUtil {

    private ChunkSectionBlockUtil() {}

    @Nullable
    public static Ref<ChunkStore> chunkRefIfInMemory(@Nonnull World world, long chunkIndex) {
        Ref<ChunkStore> ref = world.getChunkStore().getChunkReference(chunkIndex);
        if (ref == null || !ref.isValid()) {
            return null;
        }
        return ref;
    }

    @Nullable
    public static Ref<ChunkStore> chunkRefIfInMemory(@Nonnull World world, int blockX, int blockZ) {
        return chunkRefIfInMemory(world, ChunkUtil.indexChunkFromBlock(blockX, blockZ));
    }

    @Nullable
    public static WorldChunk worldChunkIfInMemory(@Nonnull World world, long chunkIndex) {
        Ref<ChunkStore> ref = chunkRefIfInMemory(world, chunkIndex);
        if (ref == null) {
            return null;
        }
        return world.getChunkStore().getStore().getComponent(ref, WorldChunk.getComponentType());
    }

    @Nullable
    public static WorldChunk worldChunkIfInMemory(@Nonnull World world, int blockX, int blockZ) {
        return worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(blockX, blockZ));
    }

    /**
     * Chunk column in memory that is not simulating ({@link ChunkFlag#TICKING} clear). Matches vanilla
     * {@code getChunkIfNonTicking} — not {@code getNonTickingChunk}, which returns any in-memory column.
     *
     * <p>Near players, columns are almost always ticking; use {@link #worldChunkIfInMemory} for place/read
     * paths that previously called {@code World#getNonTickingChunk}.
     */
    @Nullable
    public static WorldChunk worldChunkIfNonTicking(@Nonnull World world, long chunkIndex) {
        WorldChunk chunk = worldChunkIfInMemory(world, chunkIndex);
        if (chunk == null || chunk.is(ChunkFlag.TICKING)) {
            return null;
        }
        return chunk;
    }

    @Nullable
    public static WorldChunk worldChunkIfNonTicking(@Nonnull World world, int blockX, int blockZ) {
        return worldChunkIfNonTicking(world, ChunkUtil.indexChunkFromBlock(blockX, blockZ));
    }

    public static boolean isChunkInMemory(@Nonnull World world, int blockX, int blockZ) {
        return chunkRefIfInMemory(world, blockX, blockZ) != null;
    }

    @Nullable
    public static WorldChunk worldChunkIfTicking(@Nonnull World world, long chunkIndex) {
        WorldChunk chunk = worldChunkIfInMemory(world, chunkIndex);
        return chunk != null && chunk.is(ChunkFlag.TICKING) ? chunk : null;
    }

    @Nullable
    public static WorldChunk worldChunkIfTicking(@Nonnull World world, int blockX, int blockZ) {
        return worldChunkIfTicking(world, ChunkUtil.indexChunkFromBlock(blockX, blockZ));
    }

    /**
     * Returns a ticking chunk column, promoting in-memory chunks or loading asynchronously when needed.
     * Must run on the world thread for the load path; off-thread callers are dispatched onto the world.
     */
    @Nullable
    public static WorldChunk resolveTickingChunk(@Nonnull World world, int blockX, int blockZ) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(blockX, blockZ);
        WorldChunk ticking = worldChunkIfTicking(world, chunkIndex);
        if (ticking != null) {
            return ticking;
        }
        Ref<ChunkStore> inMemory = chunkRefIfInMemory(world, chunkIndex);
        if (inMemory != null) {
            WorldChunk chunk =
                world.getChunkStore().getStore().getComponent(inMemory, WorldChunk.getComponentType());
            if (chunk != null) {
                chunk.setFlag(ChunkFlag.TICKING, true);
                if (chunk.is(ChunkFlag.TICKING)) {
                    return chunk;
                }
            }
        }
        if (!world.isInThread()) {
            return CompletableFuture.supplyAsync(() -> resolveTickingChunk(world, blockX, blockZ), world).join();
        }
        Ref<ChunkStore> loaded =
            awaitChunkReference(world, world.getChunkStore().getChunkReferenceAsync(chunkIndex, GetChunkFlags.SET_TICKING));
        if (loaded == null) {
            return null;
        }
        return world.getChunkStore().getStore().getComponent(loaded, WorldChunk.getComponentType());
    }

    @Nullable
    public static BlockChunk blockChunkAt(@Nonnull World world, int blockX, int blockZ) {
        Ref<ChunkStore> ref = chunkRefIfInMemory(world, blockX, blockZ);
        if (ref == null) {
            return null;
        }
        return world.getChunkStore().getStore().getComponent(ref, BlockChunk.getComponentType());
    }

    @Nullable
    public static BlockSection blockSectionAt(@Nonnull World world, int worldX, int worldY, int worldZ) {
        if (worldY < ChunkUtil.MIN_Y || worldY > ChunkUtil.HEIGHT_MINUS_1) {
            return null;
        }
        Ref<ChunkStore> sectionRef = world.getChunkStore().getChunkSectionReferenceAtBlock(worldX, worldY, worldZ);
        if (sectionRef == null || !sectionRef.isValid()) {
            return null;
        }
        return world.getChunkStore().getStore().getComponent(sectionRef, BlockSection.getComponentType());
    }

    @Nullable
    public static BlockComponentSection blockComponentSectionAt(
        @Nonnull World world,
        int worldX,
        int worldY,
        int worldZ
    ) {
        if (worldY < ChunkUtil.MIN_Y || worldY > ChunkUtil.HEIGHT_MINUS_1) {
            return null;
        }
        Ref<ChunkStore> sectionRef = world.getChunkStore().getChunkSectionReferenceAtBlock(worldX, worldY, worldZ);
        if (sectionRef == null || !sectionRef.isValid()) {
            return null;
        }
        return world.getChunkStore().getStore().getComponent(sectionRef, BlockComponentSection.getComponentType());
    }

    @Nullable
    public static Ref<ChunkStore> blockEntityRefAt(@Nonnull World world, int worldX, int worldY, int worldZ) {
        BlockComponentSection section = blockComponentSectionAt(world, worldX, worldY, worldZ);
        if (section == null) {
            return null;
        }
        int index = ChunkUtil.indexBlock(worldX, worldY, worldZ);
        return section.getBlockReference(index);
    }

    public static int blockId(@Nonnull World world, int x, int y, int z) {
        BlockSection section = blockSectionAt(world, x, y, z);
        if (section == null) {
            return BlockType.EMPTY_ID;
        }
        return section.get(x, y, z);
    }

    @Nullable
    public static BlockType blockType(@Nonnull World world, int x, int y, int z) {
        int id = blockId(world, x, y, z);
        return BlockType.getAssetMap().getAsset(id);
    }

    @Nullable
    public static BlockType blockType(@Nonnull World world, @Nonnull Vector3ic pos) {
        return blockType(world, pos.x(), pos.y(), pos.z());
    }

    public static int rotationIndex(@Nonnull World world, int x, int y, int z) {
        BlockSection section = blockSectionAt(world, x, y, z);
        if (section == null) {
            return RotationTuple.NONE_INDEX;
        }
        return section.getRotationIndex(x, y, z);
    }

    public static int filler(@Nonnull World world, int x, int y, int z) {
        BlockSection section = blockSectionAt(world, x, y, z);
        if (section == null) {
            return FillerBlockUtil.NO_FILLER;
        }
        return section.getFiller(x, y, z);
    }

    public static boolean setTicking(@Nonnull World world, int x, int y, int z, boolean ticking) {
        BlockSection section = blockSectionAt(world, x, y, z);
        if (section == null) {
            return false;
        }
        return section.setTicking(x, y, z, ticking);
    }

    /**
     * Copy of a parked or live block-entity holder at the cell, matching the old
     * {@code WorldChunk#getBlockComponentHolder} behavior.
     */
    @Nullable
    public static Holder<ChunkStore> blockEntityHolderAt(@Nonnull World world, int worldX, int worldY, int worldZ) {
        BlockComponentSection section = blockComponentSectionAt(world, worldX, worldY, worldZ);
        if (section == null) {
            return null;
        }
        int index = ChunkUtil.indexBlock(worldX, worldY, worldZ);
        Ref<ChunkStore> reference = section.getBlockReference(index);
        if (reference != null && reference.isValid()) {
            return reference.getStore().copyEntity(reference);
        }
        Holder<ChunkStore> holder = section.getBlockHolder(index);
        return holder != null ? holder.clone() : null;
    }

    /**
     * Loads the column if needed (without forcing ticking) and returns its {@link BlockChunk}.
     */
    @Nullable
    public static BlockChunk loadBlockChunk(@Nonnull World world, int blockX, int blockZ) {
        BlockChunk inMemory = blockChunkAt(world, blockX, blockZ);
        if (inMemory != null) {
            return inMemory;
        }
        if (!world.isInThread()) {
            return CompletableFuture.supplyAsync(() -> loadBlockChunk(world, blockX, blockZ), world).join();
        }
        long chunkIndex = ChunkUtil.indexChunkFromBlock(blockX, blockZ);
        Ref<ChunkStore> loaded = awaitChunkReference(world, world.getChunkStore().getChunkReferenceAsync(chunkIndex));
        if (loaded == null || !loaded.isValid()) {
            return null;
        }
        return world.getChunkStore().getStore().getComponent(loaded, BlockChunk.getComponentType());
    }

    /**
     * Waits for an async chunk load without deadlocking the world thread. Chunk completion is posted back onto
     * the world task queue, so a bare {@code join()} from that thread never finishes — drain until done (same
     * pattern as {@code World} validation / boot loads).
     */
    @Nullable
    private static Ref<ChunkStore> awaitChunkReference(
        @Nonnull World world,
        @Nonnull CompletableFuture<Ref<ChunkStore>> future
    ) {
        while (!future.isDone()) {
            world.consumeTaskQueue();
        }
        return future.join();
    }

    /**
     * Highest non-transparent block Y in the column, or {@link ChunkUtil#MIN_Y}{@code - 1} if empty.
     */
    public static short columnHeight(@Nonnull World world, int blockX, int blockZ) {
        BlockChunk chunk = loadBlockChunk(world, blockX, blockZ);
        if (chunk == null) {
            return (short) (ChunkUtil.MIN_Y - 1);
        }
        return chunk.getHeight(blockX, blockZ);
    }

    public static boolean breakBlock(@Nonnull World world, int x, int y, int z, int settings) {
        return setBlockEmpty(world, x, y, z, settings);
    }

    /**
     * Marks the 3x3x3 neighborhood as ticking so support/block-entity state updates.
     * {@code allowPartialLoad} loads missing columns; otherwise unloaded neighbors are skipped.
     */
    public static boolean performBlockUpdate(
        @Nonnull World world,
        int x,
        int y,
        int z,
        boolean allowPartialLoad
    ) {
        boolean success = true;
        for (int ix = -1; ix < 2; ix++) {
            int wx = x + ix;
            for (int iz = -1; iz < 2; iz++) {
                int wz = z + iz;
                if (allowPartialLoad) {
                    if (loadBlockChunk(world, wx, wz) == null) {
                        success = false;
                        continue;
                    }
                } else if (chunkRefIfInMemory(world, wx, wz) == null) {
                    success = false;
                    continue;
                }
                for (int iy = -1; iy < 2; iy++) {
                    setTicking(world, wx, y + iy, wz, true);
                }
            }
        }
        return success;
    }

    public static boolean performBlockUpdate(@Nonnull World world, int x, int y, int z) {
        return performBlockUpdate(world, x, y, z, true);
    }

    public static boolean setBlock(
        @Nonnull World world,
        int x,
        int y,
        int z,
        @Nonnull BlockType blockType,
        int settings
    ) {
        String key = blockType.getId();
        if (key == null) {
            return false;
        }
        int index = BlockType.getAssetMap().getIndex(key);
        if (index < 0) {
            return false;
        }
        return setBlock(
            world,
            x,
            y,
            z,
            index,
            blockType,
            RotationTuple.NONE_INDEX,
            FillerBlockUtil.NO_FILLER,
            settings
        );
    }

    public static boolean setBlock(
        @Nonnull World world,
        int x,
        int y,
        int z,
        int blockId,
        @Nonnull BlockType blockType,
        int rotation,
        int filler,
        int settings
    ) {
        Ref<ChunkStore> sectionRef = world.getChunkStore().getChunkSectionReferenceAtBlock(x, y, z);
        if (sectionRef == null || !sectionRef.isValid()) {
            return false;
        }
        return BlockOperations.setBlock(
            world.getChunkStore(),
            sectionRef,
            x,
            y,
            z,
            blockId,
            blockType,
            rotation,
            filler,
            settings
        );
    }

    public static boolean setBlockEmpty(@Nonnull World world, int x, int y, int z, int settings) {
        return setBlock(
            world,
            x,
            y,
            z,
            BlockType.EMPTY_ID,
            BlockType.EMPTY,
            RotationTuple.NONE_INDEX,
            FillerBlockUtil.NO_FILLER,
            settings
        );
    }

    public static boolean setBlockByKey(
        @Nonnull World world,
        int x,
        int y,
        int z,
        @Nonnull String blockTypeKey,
        int settings
    ) {
        return setBlockByKey(world, x, y, z, blockTypeKey, RotationTuple.NONE_INDEX, settings);
    }

    /**
     * Rotation matters for more than just the look of the block: Hytale lays out the filler blocks of a block whose
     * hitbox leaves its own cell from the rotated hitbox, so a rotated block written as unrotated claims the wrong
     * cells.
     */
    public static boolean setBlockByKey(
        @Nonnull World world,
        int x,
        int y,
        int z,
        @Nonnull String blockTypeKey,
        int rotationIndex,
        int settings
    ) {
        int index = BlockType.getAssetMap().getIndex(blockTypeKey);
        if (index < 0) {
            return false;
        }
        BlockType blockType = BlockType.getAssetMap().getAsset(index);
        if (blockType == null) {
            return false;
        }
        return setBlock(
            world,
            x,
            y,
            z,
            index,
            blockType,
            rotationIndex,
            FillerBlockUtil.NO_FILLER,
            settings
        );
    }

    public static Store<ChunkStore> chunkStore(@Nonnull World world) {
        return world.getChunkStore().getStore();
    }

    @Nullable
    public static Ref<ChunkStore> sectionRefAt(@Nonnull World world, int worldX, int worldY, int worldZ) {
        if (worldY < ChunkUtil.MIN_Y || worldY > ChunkUtil.HEIGHT_MINUS_1) {
            return null;
        }
        Ref<ChunkStore> sectionRef = world.getChunkStore().getChunkSectionReferenceAtBlock(worldX, worldY, worldZ);
        if (sectionRef == null || !sectionRef.isValid()) {
            return null;
        }
        return sectionRef;
    }

}
