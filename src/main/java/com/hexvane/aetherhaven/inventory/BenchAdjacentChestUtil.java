package com.hexvane.aetherhaven.inventory;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.spatial.SpatialResource;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.asset.type.blockhitbox.BlockBoundingBoxes;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Resolves the same adjacent {@link ItemContainerBlock} inventories vanilla crafting benches use
 * ({@code CraftingManager.getContainersAroundBench}): {@link com.hypixel.hytale.server.core.asset.type.gameplay.CraftingConfig}
 * radii, chest limit, and item-container spatial query from the anchor block's hitbox.
 */
public final class BenchAdjacentChestUtil {
    private BenchAdjacentChestUtil() {}

    /**
     * Player {@link InventoryComponent#EVERYTHING} plus chest inventories near {@code (bx, by, bz)}, in vanilla bench
     * order (spatial query order, capped by config). Removal and counts behave like {@link CombinedItemContainer}.
     */
    @Nonnull
    public static CombinedItemContainer combinedPlayerAndAdjacentChestsForBlock(
        @Nonnull World world,
        @Nonnull Store<EntityStore> store,
        @Nonnull Ref<EntityStore> ref,
        int bx,
        int by,
        int bz
    ) {
        CombinedItemContainer player = InventoryComponent.getCombined(store, ref, InventoryComponent.EVERYTHING);
        BlockType blockType = world.getBlockType(bx, by, bz);
        if (blockType == null) {
            return player;
        }
        int rotationIndex = blockRotationIndexAt(world, bx, by, bz);
        List<ItemContainer> chests = chestContainersAroundBlock(world, bx, by, bz, blockType, rotationIndex);
        if (chests.isEmpty()) {
            return player;
        }
        ItemContainer[] parts = new ItemContainer[1 + chests.size()];
        parts[0] = player;
        for (int i = 0; i < chests.size(); i++) {
            parts[1 + i] = chests.get(i);
        }
        return new CombinedItemContainer(parts);
    }

    /**
     * Rotation for a world block via the chunk section entity's {@link BlockSection}, matching
     * {@code BlockInspectRotationCommand} / section-based reads (not {@code WorldChunk} {@code BlockAccessor} helpers).
     */
    private static int blockRotationIndexAt(@Nonnull World world, int bx, int by, int bz) {
        if (by < 0 || by >= 320) {
            return 0;
        }
        ChunkStore chunkStore = world.getChunkStore();
        Ref<ChunkStore> sectionRef = chunkStore.getChunkSectionReferenceAtBlock(bx, by, bz);
        if (sectionRef == null || !sectionRef.isValid()) {
            return 0;
        }
        BlockSection section = chunkStore.getStore().getComponent(sectionRef, BlockSection.getComponentType());
        return section == null ? 0 : section.getRotationIndex(bx, by, bz);
    }

    @Nonnull
    private static List<ItemContainer> chestContainersAroundBlock(
        @Nonnull World world,
        int x,
        int y,
        int z,
        @Nonnull BlockType blockType,
        int rotationIndex
    ) {
        List<ItemContainer> containers = new ObjectArrayList<>();
        Store<ChunkStore> store = world.getChunkStore().getStore();
        int limit = world.getGameplayConfig().getCraftingConfig().getBenchMaterialChestLimit();
        double horizontalRadius = world.getGameplayConfig().getCraftingConfig().getBenchMaterialHorizontalChestSearchRadius();
        double verticalRadius = world.getGameplayConfig().getCraftingConfig().getBenchMaterialVerticalChestSearchRadius();
        Vector3d blockPos = new Vector3d(x, y, z);
        BlockBoundingBoxes hitboxAsset = BlockBoundingBoxes.getAssetMap().getAsset(blockType.getHitboxTypeIndex());
        if (hitboxAsset == null) {
            return containers;
        }
        BlockBoundingBoxes.RotatedVariantBoxes rotatedHitbox = hitboxAsset.get(rotationIndex);
        Box boundingBox = rotatedHitbox.getBoundingBox();
        double benchWidth = boundingBox.width();
        double benchHeight = boundingBox.height();
        double benchDepth = boundingBox.depth();
        double extraSearchRadius = Math.max(benchWidth, Math.max(benchDepth, benchHeight)) - 1.0;
        SpatialResource<Ref<ChunkStore>, ChunkStore> blockStateSpatialStructure =
            store.getResource(BlockModule.get().getItemContainerSpatialResourceType());
        List<Ref<ChunkStore>> results = SpatialResource.getThreadLocalReferenceList();
        blockStateSpatialStructure
            .getSpatialStructure()
            .ordered3DAxis(
                blockPos,
                horizontalRadius + extraSearchRadius,
                verticalRadius + extraSearchRadius,
                horizontalRadius + extraSearchRadius,
                results
            );
        if (results.isEmpty()) {
            return containers;
        }
        int benchMinBlockX = (int) Math.floor(boundingBox.min.x);
        int benchMinBlockY = (int) Math.floor(boundingBox.min.y);
        int benchMinBlockZ = (int) Math.floor(boundingBox.min.z);
        int benchMaxBlockX = (int) Math.ceil(boundingBox.max.x) - 1;
        int benchMaxBlockY = (int) Math.ceil(boundingBox.max.y) - 1;
        int benchMaxBlockZ = (int) Math.ceil(boundingBox.max.z) - 1;
        double minX = blockPos.x + benchMinBlockX - horizontalRadius;
        double minY = blockPos.y + benchMinBlockY - verticalRadius;
        double minZ = blockPos.z + benchMinBlockZ - horizontalRadius;
        double maxX = blockPos.x + benchMaxBlockX + horizontalRadius;
        double maxY = blockPos.y + benchMaxBlockY + verticalRadius;
        double maxZ = blockPos.z + benchMaxBlockZ + horizontalRadius;

        for (Ref<ChunkStore> ref : results) {
            if (!ref.isValid()) {
                continue;
            }
            ItemContainerBlock chest = store.getComponent(ref, ItemContainerBlock.getComponentType());
            if (chest == null) {
                continue;
            }
            BlockModule.BlockStateInfo blockStateInfo = store.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
            if (blockStateInfo == null) {
                continue;
            }
            WorldChunk wchunk = store.getComponent(blockStateInfo.getChunkRef(), WorldChunk.getComponentType());
            if (wchunk == null) {
                continue;
            }
            int cx = ChunkUtil.worldCoordFromLocalCoord(wchunk.getX(), ChunkUtil.xFromBlockInColumn(blockStateInfo.getIndex()));
            int cy = ChunkUtil.yFromBlockInColumn(blockStateInfo.getIndex());
            int cz = ChunkUtil.worldCoordFromLocalCoord(wchunk.getZ(), ChunkUtil.zFromBlockInColumn(blockStateInfo.getIndex()));
            if (cx >= minX && cx <= maxX && cy >= minY && cy <= maxY && cz >= minZ && cz <= maxZ) {
                containers.add(chest.getItemContainer());
                if (containers.size() >= limit) {
                    break;
                }
            }
        }
        return containers;
    }
}
