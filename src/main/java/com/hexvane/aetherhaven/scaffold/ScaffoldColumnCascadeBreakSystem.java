package com.hexvane.aetherhaven.scaffold;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.component.Archetype;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockBreakingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockGathering;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.HarvestingDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.PhysicsDropType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.SoftBlockDropType;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.modules.interaction.BlockHarvestUtils;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * When the lowest wood scaffold in a column is broken, removes every wood scaffold in the same 6-connected component that
 * no longer has support (no path through the component to a cell with solid non-scaffold directly beneath), so branches
 * fall with the base instead of only the cells above one column.
 */
public final class ScaffoldColumnCascadeBreakSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private static final ThreadLocal<Boolean> IN_MOD_CASCADE = ThreadLocal.withInitial(() -> false);

    private static final int[] DX6 = { 1, -1, 0, 0, 0, 0 };
    private static final int[] DY6 = { 0, 0, 1, -1, 0, 0 };
    private static final int[] DZ6 = { 0, 0, 0, 0, 1, -1 };

    public ScaffoldColumnCascadeBreakSystem() {
        super(BreakBlockEvent.class);
    }

    private static boolean isWoodScaffold(@Nullable BlockType t) {
        return t != null && AetherhavenConstants.WOOD_SCAFFOLD_ITEM_ID.equals(t.getId());
    }

    private static boolean isReplaceableAirOrFluid(@Nonnull BlockType t) {
        if ("Empty".equals(t.getId())) {
            return true;
        }
        return t.getMaterial() == BlockMaterial.Empty;
    }

    /**
     * True when the cell directly under {@code (x,y,z)} is inside the world and is solid support (not wood scaffold, not
     * empty/replaceable air).
     */
    private static boolean hasDirectStructuralGroundBelow(@Nonnull World world, int x, int y, int z) {
        int by = y - 1;
        if (by < ChunkUtil.MIN_Y || by > ChunkUtil.HEIGHT_MINUS_1) {
            return false;
        }
        BlockType below = world.getBlockType(x, by, z);
        if (below == null) {
            return false;
        }
        if (isWoodScaffold(below)) {
            return false;
        }
        return !isReplaceableAirOrFluid(below);
    }

    /** 6-connected wood scaffold cells reachable from the broken base cell (the broken position is already air). */
    @Nonnull
    private static Set<Vector3i> collectScaffoldComponent(@Nonnull World world, int rootX, int rootY, int rootZ) {
        HashSet<Vector3i> component = new HashSet<>();
        ArrayDeque<Vector3i> queue = new ArrayDeque<>();
        for (int i = 0; i < 6; i++) {
            int nx = rootX + DX6[i];
            int ny = rootY + DY6[i];
            int nz = rootZ + DZ6[i];
            if (ny < ChunkUtil.MIN_Y || ny > ChunkUtil.HEIGHT_MINUS_1) {
                continue;
            }
            BlockType t = world.getBlockType(nx, ny, nz);
            if (!isWoodScaffold(t)) {
                continue;
            }
            Vector3i v = new Vector3i(nx, ny, nz);
            if (component.add(v)) {
                queue.add(v);
            }
        }
        while (!queue.isEmpty()) {
            Vector3i c = queue.poll();
            for (int i = 0; i < 6; i++) {
                int nx = c.getX() + DX6[i];
                int ny = c.getY() + DY6[i];
                int nz = c.getZ() + DZ6[i];
                if (ny < ChunkUtil.MIN_Y || ny > ChunkUtil.HEIGHT_MINUS_1) {
                    continue;
                }
                BlockType t = world.getBlockType(nx, ny, nz);
                if (!isWoodScaffold(t)) {
                    continue;
                }
                Vector3i v = new Vector3i(nx, ny, nz);
                if (component.add(v)) {
                    queue.add(v);
                }
            }
        }
        return component;
    }

    /** Cells in {@code component} that still touch structural ground (directly or through other scaffolds in the set). */
    @Nonnull
    private static Set<Vector3i> collectGroundedInComponent(@Nonnull World world, @Nonnull Set<Vector3i> component) {
        HashSet<Vector3i> grounded = new HashSet<>();
        ArrayDeque<Vector3i> queue = new ArrayDeque<>();
        for (Vector3i p : component) {
            if (hasDirectStructuralGroundBelow(world, p.getX(), p.getY(), p.getZ()) && grounded.add(p)) {
                queue.add(p);
            }
        }
        while (!queue.isEmpty()) {
            Vector3i c = queue.poll();
            for (int i = 0; i < 6; i++) {
                int nx = c.getX() + DX6[i];
                int ny = c.getY() + DY6[i];
                int nz = c.getZ() + DZ6[i];
                Vector3i n = new Vector3i(nx, ny, nz);
                if (!component.contains(n)) {
                    continue;
                }
                if (grounded.add(n)) {
                    queue.add(n);
                }
            }
        }
        return grounded;
    }

    /**
     * Same semantics as {@link BlockHarvestUtils#naturallyRemoveBlockByPhysics}, which delegates to
     * {@link BlockHarvestUtils#naturallyRemoveBlock} after resolving physics-style drops from {@link BlockGathering}.
     */
    private static void naturallyRemoveBlockByPhysicsNonDeprecated(
        @Nonnull Vector3i blockPosition,
        @Nonnull BlockType blockType,
        int filler,
        int setBlockSettings,
        @Nonnull Ref<ChunkStore> chunkReference,
        @Nonnull ComponentAccessor<EntityStore> entityStore,
        @Nonnull ComponentAccessor<ChunkStore> chunkStore
    ) {
        int quantity = 1;
        String itemId = null;
        String dropListId = null;
        BlockGathering blockGathering = blockType.getGathering();
        if (blockGathering != null) {
            PhysicsDropType physics = blockGathering.getPhysics();
            BlockBreakingDropType breaking = blockGathering.getBreaking();
            SoftBlockDropType soft = blockGathering.getSoft();
            HarvestingDropType harvest = blockGathering.getHarvest();
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
        int settings = setBlockSettings | 32;
        BlockHarvestUtils.naturallyRemoveBlock(
            blockPosition,
            blockType,
            filler,
            quantity,
            itemId,
            dropListId,
            settings,
            chunkReference,
            entityStore,
            chunkStore
        );
    }

    private static void removeFloatingScaffold(
        @Nonnull World world,
        @Nonnull Store<EntityStore> entityStore,
        @Nonnull List<Vector3i> floatingDescendingY
    ) {
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        for (Vector3i p : floatingDescendingY) {
            BlockType t = world.getBlockType(p.getX(), p.getY(), p.getZ());
            if (!isWoodScaffold(t)) {
                continue;
            }
            long chunkIndex = ChunkUtil.indexChunkFromBlock(p.getX(), p.getZ());
            Ref<ChunkStore> chunkRef = chunkStore.getExternalData().getChunkReference(chunkIndex);
            if (chunkRef == null || !chunkRef.isValid()) {
                continue;
            }
            BlockSection section = ScaffoldBlockSync.blockSectionAtWorldBlock(world, chunkStore, p.getX(), p.getY(), p.getZ());
            if (section == null) {
                continue;
            }
            int filler = section.getFiller(p.getX(), p.getY(), p.getZ());
            naturallyRemoveBlockByPhysicsNonDeprecated(p, t, filler, 0, chunkRef, entityStore, chunkStore);
        }
    }

    @Override
    public void handle(
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer,
        @Nonnull BreakBlockEvent event
    ) {
        if (event.isCancelled() || IN_MOD_CASCADE.get()) {
            return;
        }
        BlockType bt = event.getBlockType();
        if (bt == null || !AetherhavenConstants.WOOD_SCAFFOLD_ITEM_ID.equals(bt.getId())) {
            return;
        }
        Vector3i pos = event.getTargetBlock();
        World world = store.getExternalData().getWorld();
        int low = ScaffoldPlacementResolver.lowestScaffoldY(world, pos.x, pos.z);
        if (low < 0 || pos.y != low) {
            return;
        }
        final int x = pos.x;
        final int lowY = low;
        final int z = pos.z;
        final Store<EntityStore> entityStore = store;
        world.execute(() -> {
            IN_MOD_CASCADE.set(true);
            try {
                Set<Vector3i> component = collectScaffoldComponent(world, x, lowY, z);
                if (component.isEmpty()) {
                    return;
                }
                Set<Vector3i> grounded = collectGroundedInComponent(world, component);
                List<Vector3i> floating = new ArrayList<>();
                for (Vector3i p : component) {
                    if (!grounded.contains(p)) {
                        floating.add(p);
                    }
                }
                floating.sort(Comparator.comparingInt(Vector3i::getY).reversed());
                removeFloatingScaffold(world, entityStore, floating);
            } finally {
                IN_MOD_CASCADE.set(false);
            }
        });
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return Archetype.empty();
    }
}
