package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.math.util.ChunkUtil;
import org.joml.Vector3d;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.ChunkColumn;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.FluidSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import javax.annotation.Nonnull;

/**
 * Clears an axis-aligned footprint: removes non-town entities inside the volume, breaks blocks, and clears fluid cells
 * (liquids are not always removed by {@link World#breakBlock} alone).
 */
public final class PrefabFootprintClearUtil {
    private static final int BREAK_SETTINGS = 10;
    private static final UUID NIL_UUID = new UUID(0L, 0L);
    /** Wardrobe props can extend slightly above the stored plot AABB. */
    private static final int PRODUCTION_STORAGE_Y_PAD = 1;

    private PrefabFootprintClearUtil() {}

    /**
     * Removes entities inside {@code fp} so the old building site can be cleared. Prefab decor sometimes lacks
     * {@link com.hypixel.hytale.server.core.modules.entity.component.FromPrefab}, so we match any entity with a
     * transform instead of only prefab-tagged entities.
     *
     * <p>Safety: does not remove {@link Player}s; does not remove entities with {@link TownVillagerBinding}; does not
     * remove UUIDs listed by {@link TownRecord#collectTrackedNpcEntityUuids}.
     */
    public static void removePrefabOnlyEntitiesInFootprint(
        @Nonnull Store<EntityStore> store,
        @Nonnull PlotFootprintRecord fp,
        @Nonnull TownRecord townForNpcAllowlist
    ) {
        Set<UUID> npcAllowlist = new HashSet<>();
        townForNpcAllowlist.collectTrackedNpcEntityUuids(npcAllowlist);

        List<Ref<EntityStore>> toRemove = new ArrayList<>();
        BiConsumer<ArchetypeChunk<EntityStore>, CommandBuffer<EntityStore>> collectFootprintEntities =
            (archetypeChunk, commandBuffer) -> {
                for (int i = 0; i < archetypeChunk.size(); i++) {
                    Ref<EntityStore> r = archetypeChunk.getReferenceTo(i);
                    if (r == null || !r.isValid()) {
                        continue;
                    }
                    if (store.getComponent(r, Player.getComponentType()) != null) {
                        continue;
                    }
                    if (store.getComponent(r, TownVillagerBinding.getComponentType()) != null) {
                        continue;
                    }
                    UUIDComponent uuidComp = store.getComponent(r, UUIDComponent.getComponentType());
                    if (uuidComp != null) {
                        UUID id = uuidComp.getUuid();
                        if (id != null && !NIL_UUID.equals(id) && npcAllowlist.contains(id)) {
                            continue;
                        }
                    }
                    TransformComponent tc = archetypeChunk.getComponent(i, TransformComponent.getComponentType());
                    if (tc == null) {
                        continue;
                    }
                    Vector3d p = tc.getPosition();
                    if (!footprintContainsEntityBlockColumn(fp, p.x, p.y, p.z)) {
                        continue;
                    }
                    toRemove.add(r);
                }
            };
        store.forEachChunk(Query.and(TransformComponent.getComponentType()), collectFootprintEntities);
        for (Ref<EntityStore> r : toRemove) {
            if (r.isValid()) {
                store.removeEntity(r, RemoveReason.REMOVE);
            }
        }
    }

    private static boolean footprintContainsEntityBlockColumn(
        @Nonnull PlotFootprintRecord fp,
        double x,
        double y,
        double z
    ) {
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        return bx >= fp.getMinX()
            && bx <= fp.getMaxX()
            && by >= fp.getMinY()
            && by <= fp.getMaxY()
            && bz >= fp.getMinZ()
            && bz <= fp.getMaxZ();
    }

    /**
     * Clears fluid at (block coords) the same way {@link com.hexvane.aetherhaven.prefab.ConstructionAnimator} writes
     * prefab fluids: {@link FluidSection#setFluid} with id/level 0.
     */
    private static void clearFluidAtColumn(
        @Nonnull Store<ChunkStore> fluidStore,
        @Nonnull WorldChunk chunk,
        int x,
        int y,
        int z
    ) {
        Ref<ChunkStore> section = sectionRefForBlockY(chunk, y);
        if (section == null) {
            return;
        }
        FluidSection fluidSection = fluidStore.ensureAndGetComponent(section, FluidSection.getComponentType());
        fluidSection.setFluid(x, y, z, 0, (byte) 0);
    }

    @SuppressWarnings("deprecation")
    private static Ref<ChunkStore> sectionRefForBlockY(@Nonnull WorldChunk chunk, int blockY) {
        Ref<ChunkStore> columnRef = chunk.getReference();
        Store<ChunkStore> store = columnRef.getStore();
        ChunkColumn column = store.getComponent(columnRef, ChunkColumn.getComponentType());
        return column == null ? null : column.getSection(ChunkUtil.chunkCoordinate(blockY));
    }

    public static void clearFootprint(@Nonnull World world, @Nonnull PlotFootprintRecord fp) {
        Store<ChunkStore> fluidStore = world.getChunkStore().getStore();
        for (int x = fp.getMinX(); x <= fp.getMaxX(); x++) {
            for (int y = fp.getMinY(); y <= fp.getMaxY(); y++) {
                for (int z = fp.getMinZ(); z <= fp.getMaxZ(); z++) {
                    world.breakBlock(x, y, z, BREAK_SETTINGS);
                    WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
                    if (chunk != null) {
                        clearFluidAtColumn(fluidStore, chunk, x, y, z);
                    }
                }
            }
        }
        scrubProductionStorageInFootprint(world, fp);
    }

    /**
     * Force-clears multi-block {@link AetherhavenConstants#BLOCK_PRODUCTION_STORAGE} props by base cell so filler voxels
     * do not survive plot teardown.
     */
    public static void scrubProductionStorageInFootprint(@Nonnull World world, @Nonnull PlotFootprintRecord fp) {
        Set<Long> clearedBases = new HashSet<>();
        int minY = Math.max(0, fp.getMinY() - PRODUCTION_STORAGE_Y_PAD);
        int maxY = Math.min(319, fp.getMaxY() + PRODUCTION_STORAGE_Y_PAD);
        for (int x = fp.getMinX(); x <= fp.getMaxX(); x++) {
            for (int z = fp.getMinZ(); z <= fp.getMaxZ(); z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockType bt = world.getBlockType(x, y, z);
                    if (bt == null || !isProductionStorageBlockTypeId(bt.getId())) {
                        continue;
                    }
                    BlockPosition base = productionStorageBaseBlock(world, x, y, z);
                    long key = packBlockPos(base.x, base.y, base.z);
                    if (!clearedBases.add(key)) {
                        continue;
                    }
                    forceClearBlockCell(world, base.x, base.y, base.z);
                }
            }
        }
    }

    /** Clears the wardrobe base (and attached filler voxels) at a world cell, if any production storage is present. */
    public static void forceClearProductionStorageAt(@Nonnull World world, int wx, int wy, int wz) {
        BlockType bt = world.getBlockType(wx, wy, wz);
        if (bt == null || !isProductionStorageBlockTypeId(bt.getId())) {
            return;
        }
        BlockPosition base = productionStorageBaseBlock(world, wx, wy, wz);
        forceClearBlockCell(world, base.x, base.y, base.z);
    }

    public static boolean isProductionStorageBlockTypeId(@Nonnull String blockTypeId) {
        return blockTypeIdMatches(AetherhavenConstants.BLOCK_PRODUCTION_STORAGE, blockTypeId);
    }

    private static boolean blockTypeIdMatches(@Nonnull String expectedId, @Nonnull String actualId) {
        if (expectedId.equals(actualId) || expectedId.equalsIgnoreCase(actualId)) {
            return true;
        }
        BlockTypeAssetMap<String, BlockType> map = BlockType.getAssetMap();
        int expectedIndex = map.getIndex(expectedId);
        if (expectedIndex < 0) {
            return false;
        }
        return expectedIndex == map.getIndex(actualId);
    }

    @Nonnull
    @SuppressWarnings({ "deprecation", "removal" })
    private static BlockPosition productionStorageBaseBlock(@Nonnull World world, int wx, int y, int wz) {
        return world.getBaseBlock(new BlockPosition(wx, y, wz));
    }

    private static long packBlockPos(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) y & 0xFFFL) << 26 | (long) z & 0x3FFFFFFL;
    }

    private static void forceClearBlockCell(@Nonnull World world, int x, int y, int z) {
        WorldChunk chunk = world.getChunkIfInMemory(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return;
        }
        chunk.setBlock(
            x,
            y,
            z,
            BlockType.EMPTY_ID,
            BlockType.EMPTY,
            0,
            0,
            ConstructionPasteOps.SET_BLOCK_SETTINGS_CLEAR
        );
    }
}
