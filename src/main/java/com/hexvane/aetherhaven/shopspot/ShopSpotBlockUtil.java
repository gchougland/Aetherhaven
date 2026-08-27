package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

public final class ShopSpotBlockUtil {
    private ShopSpotBlockUtil() {}

    public static boolean isShopSpotBlock(@Nullable BlockType type) {
        return type != null && AetherhavenConstants.SHOP_SPOT_BLOCK_TYPE_ID.equals(type.getId());
    }

    @Nullable
    public static ShopSpotBlock getBlockComponent(@Nonnull World world, @Nonnull Vector3i pos) {
        if (ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(pos.x, pos.z)) == null) {
            return null;
        }
        Ref<ChunkStore> blockRef = ChunkSectionBlockUtil.blockEntityRefAt(world, pos.x, pos.y, pos.z);
        if (blockRef == null) {
            return null;
        }
        return blockRef.getStore().getComponent(blockRef, ShopSpotBlock.getComponentType());
    }

    @Nullable
    public static UUID spotIdAt(@Nonnull World world, @Nonnull Vector3i pos) {
        ShopSpotBlock block = getBlockComponent(world, pos);
        if (block == null || block.getSpotId().isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(block.getSpotId().trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** @return false if the chunk or block entity is not ready yet (caller may retry on the world thread). */
    public static boolean writeBlockComponent(
        @Nonnull World world,
        @Nonnull Vector3i pos,
        @Nonnull String spotId,
        @Nonnull String townId,
        @Nonnull String plotId
    ) {
        return writeBlockComponent(world, pos, new ShopSpotBlock(spotId, townId, plotId, false, "", false));
    }

    /** @return false if the chunk or block entity is not ready yet (caller may retry on the world thread). */
    public static boolean writeBlockComponent(@Nonnull World world, @Nonnull Vector3i pos, @Nonnull ShopSpotBlock block) {
        if (ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(pos.x, pos.z)) == null) {
            return false;
        }
        Ref<ChunkStore> blockRef = ChunkSectionBlockUtil.blockEntityRefAt(world, pos.x, pos.y, pos.z);
        if (blockRef == null) {
            return false;
        }
        Store<ChunkStore> cs = blockRef.getStore();
        cs.putComponent(blockRef, ShopSpotBlock.getComponentType(), copyBlock(block));
        return true;
    }

    @Nonnull
    private static ShopSpotBlock copyBlock(@Nonnull ShopSpotBlock block) {
        return new ShopSpotBlock(
            block.getSpotId(),
            block.getTownId(),
            block.getPlotId(),
            block.isPlayerControlled(),
            block.getLootTableId(),
            block.isConfigured()
        );
    }

    public static void syncConfigToBlock(@Nonnull World world, @Nonnull Vector3i pos, @Nonnull ShopSpotRecord record) {
        ShopSpotBlock existing = getBlockComponent(world, pos);
        ShopSpotBlock block =
            existing != null
                ? copyBlock(existing)
                : new ShopSpotBlock(
                    record.getSpotId().toString(),
                    record.getTownId().toString(),
                    record.getPlotId().toString(),
                    false,
                    "",
                    false
                );
        block.applyRecord(record);
        if (!writeBlockComponent(world, pos, block)) {
            world.execute(() -> writeBlockComponent(world, pos, block));
        }
    }

    public static void breakBlock(@Nonnull World world, @Nonnull Vector3i pos) {
        ChunkSectionBlockUtil.breakBlock(world, pos.x, pos.y, pos.z, 0);
    }
}
