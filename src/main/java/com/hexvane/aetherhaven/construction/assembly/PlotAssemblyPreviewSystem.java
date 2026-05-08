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
 * Per-player {@link PathDebugPreviewUtil} ghost cubes for assembly frontier cells.
 *
 * <p>{@link com.hypixel.hytale.protocol.packets.player.ClearDebugShapes} only on starting preview, swapping away from staff,
 * or after a {@linkplain #markStaffAssemblyBlockPlaced staff-driven} placement — otherwise autobuild would blank every
 * debug overlay and flash in sync.</p>
 *
 * <p>Every remaining tick redraws capped frontier cells without clearing so cube lifetimes refresh and brush “grow”
 * animation advances.</p>
 */
public final class PlotAssemblyPreviewSystem extends EntityTickingSystem<EntityStore> {
    /**
     * Quantize feet position before sphere tests so frontier cells sitting near the range limit do not pop in/out from
     * sub-voxel movement between ticks.
     */
    private static final double PREVIEW_OBSERVER_SNAP_GRID = 0.25;

    /** {@code true} while this player is actively showing staff + non-empty frontier preview (for enter/exit clear). */
    private static final ConcurrentHashMap<UUID, Boolean> ASSEMBLY_FRONTIER_PREVIEW_ACTIVE = new ConcurrentHashMap<>();

    /** Players who need {@link PathDebugPreviewUtil#clear} + redraw after committing a staff assembly block this tick. */
    private static final Set<UUID> STAFF_ASSEMBLY_FRONTIER_REFRESH = ConcurrentHashMap.newKeySet();

    /** Same family as path-tool “replaceable” tint, shifted green. */
    private static final Vector3f NEXT_CELL_COLOR = new Vector3f(0.22f, 0.92f, 0.48f);

    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();
    @SuppressWarnings("unused")
    private final AetherhavenPlugin plugin;

    public PlotAssemblyPreviewSystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    /** Called when the building staff commits one assembly block for this player. */
    public static void markStaffAssemblyBlockPlaced(@Nullable UUID staffActor) {
        if (staffActor != null) {
            STAFF_ASSEMBLY_FRONTIER_REFRESH.add(staffActor);
        }
    }

    /**
     * After {@link com.hexvane.aetherhaven.placement.PlotPlacementWireframeOverlay#send} issues {@link
     * com.hypixel.hytale.protocol.packets.player.ClearDebugShapes}, re-paint assembly frontier cubes in the same task so
     * they are not missing until the next entity tick (plot/charter UI refreshes run on the world queue).
     */
    public static void repaintFrontierAfterExternalDebugClear(
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store
    ) {
        if (!ref.isValid()) {
            return;
        }
        AetherhavenPlugin p = AetherhavenPlugin.get();
        if (p == null) {
            return;
        }
        PlayerRef pr = store.getComponent(ref, PlayerRef.getComponentType());
        if (pr == null) {
            return;
        }
        UUID id = pr.getUuid();
        if (id == null) {
            return;
        }
        ItemStack hand = InventoryComponent.getItemInHand(store, ref);
        if (hand != null
            && !hand.isEmpty()
            && AetherhavenConstants.PATH_TOOL_ITEM_ID.equals(hand.getItemId())) {
            return;
        }
        boolean staffInHand =
            hand != null
                && !hand.isEmpty()
                && AetherhavenConstants.BUILDING_STAFF_ITEM_ID.equals(hand.getItemId());
        if (!staffInHand) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }
        Vector3d obsForRange = snapObserverForAssemblyPreview(tc.getPosition());
        List<Vector3i> cellsInRange = new ArrayList<>();
        AssemblyFrontierWorldCells.collectWithinDefaultRange(world, p, obsForRange, cellsInRange);
        if (cellsInRange.isEmpty()) {
            return;
        }
        cellsInRange.sort(
            Comparator
                .comparingInt((Vector3i v) -> v.x)
                .thenComparingInt(v -> v.y)
                .thenComparingInt(v -> v.z)
        );
        long nowNs = System.nanoTime();
        BuildingStaffAssemblyChannelComponent channel =
            store.getComponent(ref, BuildingStaffAssemblyChannelComponent.getComponentType());
        int maxDraw = AetherhavenConstants.BUILDING_STAFF_ASSEMBLY_PREVIEW_MAX_GHOST_CELLS;
        List<Vector3i> drawCells =
            cellsInRange.size() <= maxDraw
                ? cellsInRange
                : cappedPreviewCells(cellsInRange, maxDraw, channel, nowNs, obsForRange);
        drawCells.sort(
            Comparator
                .comparingInt((Vector3i v) -> v.x)
                .thenComparingInt(v -> v.y)
                .thenComparingInt(v -> v.z)
        );
        redrawAssemblyFrontierCells(pr, world, drawCells, true, channel, nowNs);
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
        UUID previewCacheKey = pr.getUuid();
        if (previewCacheKey == null) {
            return;
        }
        ItemStack hand = InventoryComponent.getItemInHand(commandBuffer, ref);
        if (hand != null
            && !hand.isEmpty()
            && AetherhavenConstants.PATH_TOOL_ITEM_ID.equals(hand.getItemId())) {
            if (ASSEMBLY_FRONTIER_PREVIEW_ACTIVE.remove(previewCacheKey) != null) {
                STAFF_ASSEMBLY_FRONTIER_REFRESH.remove(previewCacheKey);
                clearDebugShapesThenRestoreFootprintUi(pr, ref, store);
            }
            return;
        }
        boolean staffInHand =
            hand != null
                && !hand.isEmpty()
                && AetherhavenConstants.BUILDING_STAFF_ITEM_ID.equals(hand.getItemId());
        if (!staffInHand) {
            if (ASSEMBLY_FRONTIER_PREVIEW_ACTIVE.remove(previewCacheKey) != null) {
                STAFF_ASSEMBLY_FRONTIER_REFRESH.remove(previewCacheKey);
                clearDebugShapesThenRestoreFootprintUi(pr, ref, store);
            }
            return;
        }
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }
        Vector3d ppos = tc.getPosition();
        Vector3d obsForRange = snapObserverForAssemblyPreview(ppos);
        List<Vector3i> cellsInRange = new ArrayList<>();
        AssemblyFrontierWorldCells.collectWithinDefaultRange(world, p, obsForRange, cellsInRange);
        if (cellsInRange.isEmpty()) {
            if (ASSEMBLY_FRONTIER_PREVIEW_ACTIVE.remove(previewCacheKey) != null) {
                STAFF_ASSEMBLY_FRONTIER_REFRESH.remove(previewCacheKey);
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
        BuildingStaffAssemblyChannelComponent channel =
            store.getComponent(ref, BuildingStaffAssemblyChannelComponent.getComponentType());
        BuildingStaffAssemblyChannelComponent channelForDraw =
            commandBuffer.getComponent(ref, BuildingStaffAssemblyChannelComponent.getComponentType());
        if (channelForDraw == null) {
            channelForDraw = channel;
        }
        int maxDraw = AetherhavenConstants.BUILDING_STAFF_ASSEMBLY_PREVIEW_MAX_GHOST_CELLS;
        List<Vector3i> drawCells =
            cellsInRange.size() <= maxDraw
                ? cellsInRange
                : cappedPreviewCells(cellsInRange, maxDraw, channelForDraw, nowNs, obsForRange);
        drawCells.sort(
            Comparator
                .comparingInt((Vector3i v) -> v.x)
                .thenComparingInt(v -> v.y)
                .thenComparingInt(v -> v.z)
        );

        boolean entering = ASSEMBLY_FRONTIER_PREVIEW_ACTIVE.put(previewCacheKey, Boolean.TRUE) == null;
        boolean staffRefresh = STAFF_ASSEMBLY_FRONTIER_REFRESH.remove(previewCacheKey);

        if (entering || staffRefresh) {
            clearDebugShapesThenRestoreFootprintUi(pr, ref, store);
        }
        redrawAssemblyFrontierCells(pr, world, drawCells, staffInHand, channelForDraw, nowNs);
    }

    /**
     * Keeps every cell in the active brush volume (for growth tint) plus nearest other frontier cells to the player up
     * to {@code maxDraw}. Without this, a pure “nearest N” cap can omit the aimed brush region entirely.
     */
    @Nonnull
    private static List<Vector3i> cappedPreviewCells(
        @Nonnull List<Vector3i> sortedFull,
        int maxDraw,
        @Nullable BuildingStaffAssemblyChannelComponent channel,
        long nowNs,
        @Nonnull Vector3d ppos
    ) {
        ArrayList<Vector3i> priority = new ArrayList<>();
        if (channel != null && channel.hasActiveTarget() && channel.isFresh(nowNs)) {
            for (int i = 0; i < sortedFull.size(); i++) {
                Vector3i c = sortedFull.get(i);
                if (channel.cellMatchesBrush(c.x, c.y, c.z)) {
                    priority.add(c);
                }
            }
        }
        if (priority.size() >= maxDraw) {
            priority.sort(
                Comparator
                    .comparingInt((Vector3i v) -> v.x)
                    .thenComparingInt(v -> v.y)
                    .thenComparingInt(v -> v.z)
            );
            return new ArrayList<>(priority.subList(0, maxDraw));
        }
        ArrayList<Vector3i> rest = new ArrayList<>(sortedFull.size());
        for (int i = 0; i < sortedFull.size(); i++) {
            Vector3i c = sortedFull.get(i);
            if (!cellOccursIn(c, priority)) {
                rest.add(c);
            }
        }
        rest.sort(
            Comparator.comparingDouble((Vector3i c) -> {
                double dx = c.x + 0.5 - ppos.x;
                double dy = c.y + 0.5 - ppos.y;
                double dz = c.z + 0.5 - ppos.z;
                return dx * dx + dy * dy + dz * dz;
            })
        );
        ArrayList<Vector3i> out = new ArrayList<>(maxDraw);
        out.addAll(priority);
        for (int i = 0; i < rest.size() && out.size() < maxDraw; i++) {
            out.add(rest.get(i));
        }
        return out;
    }

    private static boolean cellOccursIn(@Nonnull Vector3i cell, @Nonnull ArrayList<Vector3i> list) {
        for (int i = 0; i < list.size(); i++) {
            Vector3i o = list.get(i);
            if (o.x == cell.x && o.y == cell.y && o.z == cell.z) {
                return true;
            }
        }
        return false;
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

    @Nonnull
    private static Vector3d snapObserverForAssemblyPreview(@Nonnull Vector3d feetWorld) {
        double g = PREVIEW_OBSERVER_SNAP_GRID;
        return new Vector3d(
            Math.round(feetWorld.x / g) * g,
            Math.round(feetWorld.y / g) * g,
            Math.round(feetWorld.z / g) * g
        );
    }
}
