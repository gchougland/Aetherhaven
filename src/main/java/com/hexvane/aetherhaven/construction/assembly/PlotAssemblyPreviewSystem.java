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
import org.joml.Vector3d;
import org.joml.Vector3i;
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
 * Per-player assembly preview markers (building and destruction models) while the building staff is held.
 *
 * <p>Markers are owner-only entities, not debug shapes, so plot footprint wireframes do not wipe them.</p>
 */
public final class PlotAssemblyPreviewSystem extends EntityTickingSystem<EntityStore> {
    /**
     * Quantize feet position before sphere tests so frontier cells sitting near the range limit do not pop in/out from
     * sub-voxel movement between ticks.
     */
    private static final double PREVIEW_OBSERVER_SNAP_GRID = 0.25;
    private static final int PREVIEW_RECOMPUTE_INTERVAL_TICKS = 3;

    /** {@code true} while this player is actively showing staff + non-empty assembly preview. */
    private static final ConcurrentHashMap<UUID, Boolean> ASSEMBLY_FRONTIER_PREVIEW_ACTIVE = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, Integer> PREVIEW_TICK_COUNTER = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, PreviewCells> CACHED_PREVIEW_CELLS = new ConcurrentHashMap<>();

    @Nonnull
    private final Set<Dependency<EntityStore>> dependencies = RootDependency.firstSet();
    @SuppressWarnings("unused")
    private final AetherhavenPlugin plugin;

    public PlotAssemblyPreviewSystem(@Nonnull AetherhavenPlugin plugin) {
        this.plugin = plugin;
    }

    /** Called when the building staff commits one assembly block for this player. */
    public static void markStaffAssemblyBlockPlaced(@Nullable UUID staffActor) {
        if (staffActor == null) {
            return;
        }
        PREVIEW_TICK_COUNTER.remove(staffActor);
        CACHED_PREVIEW_CELLS.remove(staffActor);
    }

    /**
     * Re-sync assembly markers immediately after footprint UI clears debug shapes so markers are not missing until the
     * next entity tick.
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
        ItemStack hand = InventoryComponent.getItemInHand(store, ref);
        if (hand != null
            && !hand.isEmpty()
            && AetherhavenConstants.PATH_TOOL_ITEM_ID.equals(hand.getItemId())) {
            return;
        }
        boolean staffInHand = hand != null && !hand.isEmpty() && BuildingStaffTiers.isBuildingStaff(hand.getItemId());
        if (!staffInHand) {
            return;
        }
        World world = store.getExternalData().getWorld();
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }
        long nowNs = System.nanoTime();
        BuildingStaffAssemblyChannelComponent channel =
            store.getComponent(ref, BuildingStaffAssemblyChannelComponent.getComponentType());
        if (channel != null && hand != null) {
            channel.setBrushChebyshevRadius(BuildingStaffTiers.assemblyBrushChebyshevRadius(hand.getItemId()));
        }
        PreviewCells cells = collectPreviewCells(world, p, tc.getPosition(), channel, nowNs);
        if (cells.isEmpty()) {
            return;
        }
        UUID ownerUuid = AssemblyMarkerPreviewSync.requireOwnerEntityUuid(store, ref);
        List<AssemblyMarkerPreviewSync.DesiredMarker> desired =
            AssemblyMarkerPreviewSync.buildDesiredFromCells(
                world,
                p,
                cells.obstruction(),
                cells.frontier(),
                true,
                channel,
                nowNs
            );
        world.execute(
            () ->
                AssemblyMarkerPreviewSync.syncMarkersImmediate(
                    world,
                    p,
                    ref,
                    store,
                    ownerUuid,
                    desired,
                    true,
                    true
                )
        );
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
        PlotAssemblyService.ensureAssemblyJobsForAssemblingPlots(world, p, store);
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
                PREVIEW_TICK_COUNTER.remove(previewCacheKey);
                CACHED_PREVIEW_CELLS.remove(previewCacheKey);
                exitPreview(world, pr, ref, store, commandBuffer);
            }
            return;
        }
        boolean staffInHand = hand != null && !hand.isEmpty() && BuildingStaffTiers.isBuildingStaff(hand.getItemId());
        if (!staffInHand) {
            if (ASSEMBLY_FRONTIER_PREVIEW_ACTIVE.remove(previewCacheKey) != null) {
                PREVIEW_TICK_COUNTER.remove(previewCacheKey);
                CACHED_PREVIEW_CELLS.remove(previewCacheKey);
                exitPreview(world, pr, ref, store, commandBuffer);
            }
            return;
        }
        TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }
        long nowNs = System.nanoTime();
        BuildingStaffAssemblyChannelComponent channel =
            store.getComponent(ref, BuildingStaffAssemblyChannelComponent.getComponentType());
        BuildingStaffAssemblyChannelComponent channelForDraw =
            commandBuffer.getComponent(ref, BuildingStaffAssemblyChannelComponent.getComponentType());
        if (channelForDraw == null) {
            channelForDraw = channel;
        }
        int brushR = BuildingStaffTiers.assemblyBrushChebyshevRadius(hand.getItemId());
        if (channel != null) {
            channel.setBrushChebyshevRadius(brushR);
        }
        if (channelForDraw != null) {
            channelForDraw.setBrushChebyshevRadius(brushR);
        }
        if (channelForDraw != null && channelForDraw.hasBrushLock()) {
            if (BuildingStaffAssemblyChannelExecutor.tryExecuteChargedBrush(
                ref, store, commandBuffer, world, p, channelForDraw, previewCacheKey, nowNs
            )) {
                PREVIEW_TICK_COUNTER.put(previewCacheKey, 0);
                CACHED_PREVIEW_CELLS.remove(previewCacheKey);
                channel = store.getComponent(ref, BuildingStaffAssemblyChannelComponent.getComponentType());
                channelForDraw = commandBuffer.getComponent(ref, BuildingStaffAssemblyChannelComponent.getComponentType());
                if (channelForDraw == null) {
                    channelForDraw = channel;
                }
            }
        }
        Vector3d snappedPos = snapObserverForAssemblyPreview(tc.getPosition());
        int tickCount = PREVIEW_TICK_COUNTER.merge(previewCacheKey, 1, Integer::sum);
        PreviewCells cells = CACHED_PREVIEW_CELLS.get(previewCacheKey);
        if (cells == null || tickCount >= PREVIEW_RECOMPUTE_INTERVAL_TICKS) {
            PREVIEW_TICK_COUNTER.put(previewCacheKey, 0);
            cells = collectPreviewCells(world, p, snappedPos, channelForDraw, nowNs);
            CACHED_PREVIEW_CELLS.put(previewCacheKey, cells);
        }
        if (cells.isEmpty()) {
            if (ASSEMBLY_FRONTIER_PREVIEW_ACTIVE.remove(previewCacheKey) != null) {
                PREVIEW_TICK_COUNTER.remove(previewCacheKey);
                CACHED_PREVIEW_CELLS.remove(previewCacheKey);
                exitPreview(world, pr, ref, store, commandBuffer);
            }
            return;
        }
        ASSEMBLY_FRONTIER_PREVIEW_ACTIVE.put(previewCacheKey, Boolean.TRUE);

        UUID ownerUuid = AssemblyMarkerPreviewSync.requireOwnerEntityUuid(store, ref);
        List<AssemblyMarkerPreviewSync.DesiredMarker> desired =
            AssemblyMarkerPreviewSync.buildDesiredFromCells(
                world,
                p,
                cells.obstruction(),
                cells.frontier(),
                staffInHand,
                channelForDraw,
                nowNs
            );
        AssemblyMarkerPreviewSync.syncMarkers(
            world,
            p,
            ref,
            store,
            commandBuffer,
            ownerUuid,
            desired,
            !cells.obstruction().isEmpty(),
            !cells.frontier().isEmpty()
        );
    }

    private static void exitPreview(
        @Nonnull World world,
        @Nonnull PlayerRef pr,
        @Nonnull Ref<EntityStore> ref,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        AssemblyMarkerPreviewSync.clearAllMarkers(world, ref, store, commandBuffer);
        PathDebugPreviewUtil.clear(pr);
        PlotFootprintOverlayRefresh.afterClearDebugShapes(ref, store);
    }

    @Nonnull
    private static PreviewCells collectPreviewCells(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull Vector3d observerFeet,
        @Nullable BuildingStaffAssemblyChannelComponent channel,
        long nowNs
    ) {
        Vector3d obsForRange = snapObserverForAssemblyPreview(observerFeet);
        boolean hasClearing = AssemblyWorldRegistry.anyJobInPhase(world, PlotAssemblyPhase.CLEARING);
        boolean hasPlacing = AssemblyWorldRegistry.anyJobInPhase(world, PlotAssemblyPhase.PLACING);
        List<Vector3i> obstructionCells = hasClearing ? new ArrayList<>() : List.of();
        if (hasClearing) {
            AssemblyObstructionWorldCells.collectWithinDefaultRange(world, plugin, obsForRange, obstructionCells);
        }
        List<Vector3i> frontierCells = hasPlacing ? new ArrayList<>() : List.of();
        if (hasPlacing) {
            AssemblyFrontierWorldCells.collectWithinDefaultRange(world, plugin, obsForRange, frontierCells);
        }
        if (obstructionCells.isEmpty() && frontierCells.isEmpty()) {
            return new PreviewCells(List.of(), List.of());
        }
        if (hasPlacing) {
            frontierCells.sort(
                Comparator
                    .comparingInt((Vector3i v) -> v.x)
                    .thenComparingInt(v -> v.y)
                    .thenComparingInt(v -> v.z)
            );
        }
        int clearingMaxDraw = AetherhavenConstants.BUILDING_STAFF_CLEARING_PREVIEW_MAX_GHOST_CELLS;
        int frontierMaxDraw = AetherhavenConstants.BUILDING_STAFF_ASSEMBLY_PREVIEW_MAX_GHOST_CELLS;
        List<Vector3i> drawObstruction =
            obstructionCells.isEmpty()
                ? List.of()
                : obstructionCells.size() <= clearingMaxDraw
                    ? obstructionCells
                    : cappedObstructionPreviewCells(obstructionCells, clearingMaxDraw, channel, nowNs, obsForRange);
        List<Vector3i> drawFrontier =
            frontierCells.isEmpty()
                ? List.of()
                : frontierCells.size() <= frontierMaxDraw
                    ? frontierCells
                    : cappedPreviewCells(frontierCells, frontierMaxDraw, channel, nowNs, obsForRange);
        return new PreviewCells(drawObstruction, drawFrontier);
    }

    private record PreviewCells(@Nonnull List<Vector3i> obstruction, @Nonnull List<Vector3i> frontier) {
        boolean isEmpty() {
            return obstruction.isEmpty() && frontier.isEmpty();
        }
    }

    /**
     * Like {@link #cappedPreviewCells} but fills overflow with evenly spaced cells so large footprints still show
     * distant obstructions during clearing (not only the nearest subset to the player).
     */
    @Nonnull
    private static List<Vector3i> cappedObstructionPreviewCells(
        @Nonnull List<Vector3i> sortedFull,
        int maxDraw,
        @Nullable BuildingStaffAssemblyChannelComponent channel,
        long nowNs,
        @Nonnull Vector3d ppos
    ) {
        if (sortedFull.size() <= maxDraw) {
            return sortedFull;
        }
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
        ArrayList<Vector3i> out = new ArrayList<>(maxDraw);
        out.addAll(priority);
        int slots = maxDraw - out.size();
        int n = sortedFull.size();
        for (int s = 0; s < slots; s++) {
            int idx = (s * n) / slots;
            Vector3i c = sortedFull.get(Math.min(idx, n - 1));
            if (!cellOccursIn(c, out)) {
                out.add(c);
            }
        }
        if (out.size() >= maxDraw) {
            return out;
        }
        ArrayList<Vector3i> rest = new ArrayList<>(sortedFull.size());
        for (int i = 0; i < sortedFull.size(); i++) {
            Vector3i c = sortedFull.get(i);
            if (!cellOccursIn(c, out)) {
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
        for (int i = 0; i < rest.size() && out.size() < maxDraw; i++) {
            out.add(rest.get(i));
        }
        return out;
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
