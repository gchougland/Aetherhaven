package com.hexvane.aetherhaven.world;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RotationTuple;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.ChunkAccessor;
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
            world.getChunkStore().getChunkReferenceAsync(chunkIndex, GetChunkFlags.SET_TICKING).join();
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
        BlockChunk chunk = blockChunkAt(world, x, z);
        if (chunk == null || y < ChunkUtil.MIN_Y || y >= ChunkUtil.HEIGHT) {
            return BlockType.EMPTY_ID;
        }
        return chunk.getBlock(x, y, z);
    }

    @Nullable
    public static BlockType blockType(@Nonnull World world, int x, int y, int z) {
        return BlockType.getAssetMap().getAsset(blockId(world, x, y, z));
    }

    public static int rotationIndex(@Nonnull World world, int x, int y, int z) {
        BlockSection section = blockSectionAt(world, x, y, z);
        if (section == null) {
            return RotationTuple.NONE_INDEX;
        }
        return section.getRotationIndex(ChunkUtil.indexBlock(x, y, z));
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
            RotationTuple.NONE_INDEX,
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

    /**
     * {@link ChunkAccessor} adapter for {@link World} after U6 removed {@code World implements ChunkAccessor}.
     */
    @Nonnull
    public static ChunkAccessor chunkAccessor(@Nonnull World world) {
        return new WorldChunkAccessor(world);
    }

    public static final class WorldChunkAccessor implements ChunkAccessor {
        private final World world;

        public WorldChunkAccessor(@Nonnull World world) {
            this.world = world;
        }

        @Override
        @Nullable
        public WorldChunk getChunkIfInMemory(long index) {
            return worldChunkIfInMemory(world, index);
        }

        @Override
        @Nullable
        public WorldChunk loadChunkIfInMemory(long index) {
            WorldChunk chunk = getChunkIfInMemory(index);
            if (chunk != null) {
                chunk.setFlag(ChunkFlag.TICKING, true);
            }
            return chunk;
        }

        @Override
        @Nullable
        public WorldChunk getChunkIfLoaded(long index) {
            WorldChunk chunk = getChunkIfInMemory(index);
            return chunk != null && chunk.is(ChunkFlag.TICKING) ? chunk : null;
        }

        @Override
        @Nullable
        public WorldChunk getChunkIfNonTicking(long index) {
            return worldChunkIfNonTicking(world, index);
        }

        @Override
        @Nullable
        public WorldChunk getChunk(long index) {
            int blockX = ChunkUtil.xOfChunkIndex(index) * 16 + 8;
            int blockZ = ChunkUtil.zOfChunkIndex(index) * 16 + 8;
            return resolveTickingChunk(world, blockX, blockZ);
        }

        /**
         * Matches {@link com.hypixel.hytale.server.core.universe.world.IWorldChunks#getNonTickingChunk}: any
         * in-memory column (including ticking). Name is historical; do not filter on {@link ChunkFlag#TICKING}.
         */
        @Override
        @Nullable
        public WorldChunk getNonTickingChunk(long index) {
            WorldChunk inMemory = worldChunkIfInMemory(world, index);
            if (inMemory != null) {
                return inMemory;
            }
            if (!world.isInThread()) {
                return CompletableFuture.supplyAsync(() -> getNonTickingChunk(index), world).join();
            }
            Ref<ChunkStore> loaded = world.getChunkStore().getChunkReferenceAsync(index).join();
            if (loaded == null || !loaded.isValid()) {
                return null;
            }
            return world.getChunkStore().getStore().getComponent(loaded, WorldChunk.getComponentType());
        }
    }
}
