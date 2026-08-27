package com.hexvane.aetherhaven.rootremover;

import com.hexvane.aetherhaven.world.ChunkSectionBlockUtil;

import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.HarvestingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.PhysicsDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.SoftBlockDropType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Clears stump and buried tree wood after a chop. Scans the full connected log structure (trunk, branches, roots),
 * finds the highest wood cell touching soil or rock, and only replaces wood at or below that ground line.
 */
public final class RootRemoverService {
    private static final String RESOURCE_WOOD_TRUNK = "Wood_Trunk";
    private static final String BLOCK_DIRT = "Soil_Dirt";
    /** Large oaks can span hundreds of log cells; cap avoids runaway scans on accidental merges. */
    private static final int MAX_TREE_WOOD_BLOCKS = 1024;
    private static final int SET_BLOCK = 10;

    private static final int[] DX6 = { 1, -1, 0, 0, 0, 0 };
    private static final int[] DY6 = { 0, 0, 1, -1, 0, 0 };
    private static final int[] DZ6 = { 0, 0, 0, 0, 1, -1 };

    private RootRemoverService() {}

    /**
     * @return true when at least one block was cleared
     */
    public static boolean clearRoots(
        @Nonnull World world,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Vector3i targetBlock
    ) {
        BlockType clicked = ChunkSectionBlockUtil.blockType(world, targetBlock.x(), targetBlock.y(), targetBlock.z());
        if (!isTrunkBlock(clicked)) {
            return false;
        }

        Set<Vector3i> treeWood = collectTreeWoodComponent(world, targetBlock);
        if (treeWood.isEmpty()) {
            return false;
        }

        int groundContactY = findHighestGroundContactY(world, treeWood);
        if (groundContactY == Integer.MIN_VALUE) {
            return false;
        }

        // Standing trees still have log structure above the soil line; only clear after the upper trunk is gone.
        for (Vector3i p : treeWood) {
            if (p.y() > groundContactY) {
                return false;
            }
        }

        Set<Vector3i> removable = new HashSet<>();
        for (Vector3i p : treeWood) {
            if (p.y() <= groundContactY) {
                removable.add(p);
            }
        }
        if (removable.isEmpty()) {
            return false;
        }

        List<Vector3i> ordered = new ArrayList<>(removable);
        ordered.sort(Comparator.comparingInt(Vector3i::y));

        List<ItemStack> allDrops = new ArrayList<>();
        int dirtIdx = BlockType.getAssetMap().getIndex(BLOCK_DIRT);
        BlockType dirtType = BlockType.getAssetMap().getAsset(BLOCK_DIRT);
        if (dirtType == null || dirtIdx == BlockType.EMPTY_ID) {
            return false;
        }

        int cleared = 0;
        for (Vector3i pos : ordered) {
            BlockType blockType = ChunkSectionBlockUtil.blockType(world, pos.x(), pos.y(), pos.z());
            if (!isTreeWoodStructure(blockType)) {
                continue;
            }
            allDrops.addAll(resolveDrops(blockType));
            if (ChunkSectionBlockUtil.worldChunkIfInMemory(world, ChunkUtil.indexChunkFromBlock(pos.x(), pos.z())) == null) {
                continue;
            }
            ChunkSectionBlockUtil.setBlock(
                world, pos.x(), pos.y(), pos.z(), dirtIdx, dirtType, 0, 0, SET_BLOCK
            );
            cleared++;
        }

        if (cleared == 0) {
            return false;
        }

        List<ItemStack> merged = mergeStacks(allDrops);
        if (!merged.isEmpty()) {
            spawnDropsAtPlayer(commandBuffer, playerRef, merged);
        }
        return true;
    }

    /** Right click target must be a trunk segment (not branches or leaves). */
    public static boolean isTrunkBlock(@Nullable BlockType blockType) {
        if (blockType == null || blockType == BlockType.EMPTY) {
            return false;
        }
        String id = blockType.getId();
        return id.endsWith("_Trunk") || id.endsWith("_Trunk_Full");
    }

    /**
     * Log-like tree blocks used for connectivity and replacement: trunk, trunk full, branches, roots. Excludes leaves.
     */
    static boolean isTreeWoodStructure(@Nullable BlockType blockType) {
        if (blockType == null || blockType == BlockType.EMPTY || isLeafBlock(blockType)) {
            return false;
        }
        String id = blockType.getId();
        if (id.endsWith("_Roots")) {
            return true;
        }
        if (id.endsWith("_Trunk") || id.endsWith("_Trunk_Full")) {
            return true;
        }
        if (id.contains("_Branch_")) {
            return true;
        }
        Item item = blockType.getItem();
        return item != null && InventoryMaterials.itemHasResourceType(item, RESOURCE_WOOD_TRUNK);
    }

    private static boolean isLeafBlock(@Nullable BlockType blockType) {
        if (blockType == null || blockType == BlockType.EMPTY) {
            return false;
        }
        String id = blockType.getId();
        return id.contains("Leaves") || id.contains("_Leaf_");
    }

    private static boolean isPassableForGroundNeighbor(@Nullable BlockType blockType) {
        if (blockType == null) {
            return true;
        }
        if (isTreeWoodStructure(blockType) || isLeafBlock(blockType)) {
            return true;
        }
        if ("Empty".equals(blockType.getId())) {
            return true;
        }
        return blockType.getMaterial() == BlockMaterial.Empty;
    }

    /** Soil, stone, grass, and other solid terrain; not air, fluids, leaves, or log wood. */
    private static boolean isSolidTerrain(@Nonnull World world, int x, int y, int z) {
        if (y < ChunkUtil.MIN_Y || y > ChunkUtil.HEIGHT_MINUS_1) {
            return false;
        }
        BlockType t = ChunkSectionBlockUtil.blockType(world, x, y, z);
        return t != null && !isPassableForGroundNeighbor(t);
    }

    private static boolean touchesSolidTerrain(@Nonnull World world, @Nonnull Vector3i pos) {
        for (int i = 0; i < 6; i++) {
            if (isSolidTerrain(world, pos.x() + DX6[i], pos.y() + DY6[i], pos.z() + DZ6[i])) {
                return true;
            }
        }
        return false;
    }

    /**
     * Highest Y among tree-wood cells that touch solid terrain (soil, rock, etc.). Defines the ground line for stump and
     * root removal.
     */
    private static int findHighestGroundContactY(@Nonnull World world, @Nonnull Set<Vector3i> treeWood) {
        int highest = Integer.MIN_VALUE;
        for (Vector3i p : treeWood) {
            if (touchesSolidTerrain(world, p)) {
                highest = Math.max(highest, p.y());
            }
        }
        return highest;
    }

    @Nonnull
    private static Set<Vector3i> collectTreeWoodComponent(@Nonnull World world, @Nonnull Vector3i start) {
        HashSet<Vector3i> component = new HashSet<>();
        ArrayDeque<Vector3i> queue = new ArrayDeque<>();
        BlockType startType = ChunkSectionBlockUtil.blockType(world, start.x(), start.y(), start.z());
        if (!isTreeWoodStructure(startType)) {
            return component;
        }
        Vector3i startKey = new Vector3i(start);
        component.add(startKey);
        queue.add(startKey);
        while (!queue.isEmpty() && component.size() < MAX_TREE_WOOD_BLOCKS) {
            Vector3i c = queue.poll();
            for (int i = 0; i < 6; i++) {
                int nx = c.x() + DX6[i];
                int ny = c.y() + DY6[i];
                int nz = c.z() + DZ6[i];
                if (ny < ChunkUtil.MIN_Y || ny > ChunkUtil.HEIGHT_MINUS_1) {
                    continue;
                }
                Vector3i n = new Vector3i(nx, ny, nz);
                if (component.contains(n)) {
                    continue;
                }
                BlockType t = ChunkSectionBlockUtil.blockType(world, nx, ny, nz);
                if (!isTreeWoodStructure(t)) {
                    continue;
                }
                component.add(n);
                if (component.size() >= MAX_TREE_WOOD_BLOCKS) {
                    break;
                }
                queue.add(n);
            }
        }
        return component;
    }

    @Nonnull
    private static List<ItemStack> resolveDrops(@Nonnull BlockType blockType) {
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
                quantity = breaking.getQuantity();
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
        return BlockHarvestUtils.getDrops(blockType, quantity, itemId, dropListId);
    }

    @Nonnull
    private static List<ItemStack> mergeStacks(@Nonnull List<ItemStack> drops) {
        Map<String, Integer> counts = new HashMap<>();
        for (ItemStack stack : drops) {
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            counts.merge(stack.getItemId(), stack.getQuantity(), Integer::sum);
        }
        List<ItemStack> merged = new ArrayList<>(counts.size());
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            merged.add(new ItemStack(e.getKey(), e.getValue()));
        }
        return merged;
    }

    private static void spawnDropsAtPlayer(
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull List<ItemStack> stacks
    ) {
        if (stacks.isEmpty()) {
            return;
        }
        TransformComponent tc = commandBuffer.getComponent(playerRef, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }
        Vector3d dropPos = new Vector3d(tc.getPosition());
        Holder<EntityStore>[] holders = ItemComponent.generateItemDrops(commandBuffer, stacks, dropPos, Rotation3f.ZERO);
        if (holders.length > 0) {
            commandBuffer.addEntities(holders, AddReason.SPAWN);
        }
    }
}
