package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.pathtool.PathDebugPreviewUtil;
import com.hexvane.aetherhaven.placement.PlotFootprintOverlayRefresh;
import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Per-player {@link PathDebugPreviewUtil} ghost cubes for assembly frontier cells (same approach as the path tool’s
 * planned voxels — no world blocks). Cells being filled with the building staff grow from half to full size over half a second.
 */
public final class PlotAssemblyPreviewSystem extends EntityTickingSystem<EntityStore> {
    /**
     * Last stable geometry signature (frontier cells + brush center). Used to skip redundant redraws, and to redraw growing
     * cubes without {@link PathDebugPreviewUtil#clear} so brush resize does not flash.
     */
    private static final ConcurrentHashMap<UUID, Long> LAST_ASSEMBLY_GEOM_SIG = new ConcurrentHashMap<>();

    /** Same family as path-tool “replaceable” tint, shifted green. */
    private static final Vector3f NEXT_CELL_COLOR = new Vector3f(0.22f, 0.92f, 0.48f);

    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();
    @SuppressWarnings("unused")
    private final AetherhavenPlugin plugin;

    public PlotAssemblyPreviewSystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return dependencies;
    }

    @Nonnull
    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(Player.getComponentType());
    }

    @Override
    public void tick(
        float dt,
        int index,
        @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> ref = archetypeChunk.getReferenceTo(index);
        World world = store.getExternalData().getWorld();
        AetherhavenPlugin p = AetherhavenPlugin.get();
        if (p == null) {
            return;
        }
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr == null) {
            return;
        }
        UUIDComponent uuidComp = store.getComponent(ref, UUIDComponent.getComponentType());
        UUID playerUuid = uuidComp != null ? uuidComp.getUuid() : null;
        ItemStack hand = InventoryComponent.getItemInHand(commandBuffer, ref);
        if (hand != null
            && !hand.isEmpty()
            && AetherhavenConstants.PATH_TOOL_ITEM_ID.equals(hand.getItemId())) {
            if (playerUuid != null && LAST_ASSEMBLY_GEOM_SIG.remove(playerUuid) != null) {
                clearDebugShapesThenRestoreFootprintUi(pr, ref, store);
            }
            return;
        }
        boolean staffInHand =
            hand != null
                && !hand.isEmpty()
                && AetherhavenConstants.BUILDING_STAFF_ITEM_ID.equals(hand.getItemId());
        if (!staffInHand) {
            if (playerUuid != null && LAST_ASSEMBLY_GEOM_SIG.remove(playerUuid) != null) {
                clearDebugShapesThenRestoreFootprintUi(pr, ref, store);
            }
            return;
        }
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }
        Vector3d ppos = tc.getPosition();
        List<Vector3i> cellsInRange = new ArrayList<>();
        AssemblyFrontierWorldCells.collectWithinDefaultRange(world, p, ppos, cellsInRange);
        if (cellsInRange.isEmpty()) {
            if (playerUuid != null && LAST_ASSEMBLY_GEOM_SIG.remove(playerUuid) != null) {
                clearDebugShapesThenRestoreFootprintUi(pr, ref, store);
            }
            return;
        }
        cellsInRange.sort(
            Comparator
                .comparingInt((Vector3i v) -> v.x)
                .thenComparingInt(v -> v.y)
                .thenComparingInt(v -> v.z)
        );
        long nowNs = System.nanoTime();
        BuildingStaffAssemblyChannelComponent channel = store.getComponent(ref, BuildingStaffAssemblyChannelComponent.getComponentType());
        long cellHash = hashAssemblyCellList(cellsInRange);
        long geomSig = assemblyGeomSignature(cellHash, staffInHand, channel, nowNs);
        boolean brushVisualActive =
            staffInHand && channel != null && channel.hasActiveTarget() && channel.isFresh(nowNs);
        double grow01Active = brushVisualActive ? channel.channelGrow01(nowNs) : 0.0;
        boolean sizingBrush =
            brushVisualActive && grow01Active > 1e-9 && grow01Active < 1.0 - 1e-9;
        if (playerUuid != null) {
            Long prevGeom = LAST_ASSEMBLY_GEOM_SIG.get(playerUuid);
            if (!sizingBrush && prevGeom != null && prevGeom == geomSig) {
                return;
            }
            if (sizingBrush && prevGeom != null && prevGeom == geomSig) {
                redrawAssemblyFrontierCells(pr, world, cellsInRange, staffInHand, channel, nowNs);
                return;
            }
            LAST_ASSEMBLY_GEOM_SIG.put(playerUuid, geomSig);
        }
        clearDebugShapesThenRestoreFootprintUi(pr, ref, store);
        redrawAssemblyFrontierCells(pr, world, cellsInRange, staffInHand, channel, nowNs);
    }

    private static void clearDebugShapesThenRestoreFootprintUi(
        @Nonnull PlayerRef pr,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        PathDebugPreviewUtil.clear(pr);
        PlotFootprintOverlayRefresh.afterClearDebugShapes(ref, store);
    }

    private static void redrawAssemblyFrontierCells(
        @Nonnull PlayerRef pr,
        @Nonnull World world,
        @Nonnull List<Vector3i> cellsInRange,
        boolean staffInHand,
        @Nullable BuildingStaffAssemblyChannelComponent channel,
        long nowNs
    ) {
        for (Vector3i cell : cellsInRange) {
            double grow01 =
                staffInHand
                    && channel != null
                    && channel.cellMatchesBrush(cell.x, cell.y, cell.z)
                    && channel.isFresh(nowNs)
                    ? channel.channelGrow01(nowNs)
                    : 0.0;
            PathDebugPreviewUtil.drawAssemblyFrontierCellCube(pr, cell.x, cell.y, cell.z, NEXT_CELL_COLOR, world, grow01);
        }
    }

    private static long hashAssemblyCellList(@Nonnull List<Vector3i> sortedCells) {
        long h = 0x243F6A8885A308D3L;
        for (int i = 0; i < sortedCells.size(); i++) {
            Vector3i c = sortedCells.get(i);
            h ^= Long.rotateLeft((long) c.x * 0x9E3779B97F4A7C15L + (long) c.y + ((long) c.z << 20), i % 64);
        }
        return h;
    }

    /** Frontier cell set plus brush anchor when channeling; excludes fill progress so growth can redraw without a global clear. */
    private static long assemblyGeomSignature(
        long cellHash,
        boolean staffInHand,
        @Nullable BuildingStaffAssemblyChannelComponent channel,
        long nowNs
    ) {
        if (!staffInHand || channel == null || !channel.hasActiveTarget() || !channel.isFresh(nowNs)) {
            return cellHash;
        }
        long cx = channel.getBrushCenterX();
        long cy = channel.getBrushCenterY();
        long cz = channel.getBrushCenterZ();
        return cellHash ^ (cx * 0x9E3779B1L) ^ (cy * 0x85EBCA77L) ^ (cz * 0xC2B2AE3DL);
    }
}
