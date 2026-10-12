package com.hexvane.aetherhaven.festival;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import com.hexvane.aetherhaven.placement.PlotBuildingRelocation;
import com.hexvane.aetherhaven.placement.PrefabFootprintClearUtil;
import com.hexvane.aetherhaven.placement.PrefabTriggerVolumeCleanup;
import com.hexvane.aetherhaven.prefab.ConstructionAnimator;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.prop.PropPlotTeardown;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotFootprintChunkUtil;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Replaces what stands on a festival square plot: the everyday prefab from its construction definition, or a festival's
 * own prefab for the length of the festival. Every festival reserves the same {@link FestivalPrefabSize} volume, so the
 * swap clears that box from the paved floor up at the plot's stored anchor and pastes the new prefab at the same anchor
 * and yaw.
 *
 * <p>Under the floor the swap only writes what the incoming layout lists there. A festival layout brings its own
 * foundation (and the Market its pond), which the game lays by itself over whatever ground is there, so nobody digs
 * for it. The everyday square lists nothing under its floor, so ending a festival leaves the festival's fill hidden
 * under the paving: nothing is cleared to air and no cavity is left. See {@link #plan}.
 *
 * <p>Anything a player left inside the square is removed by the swap. Festival prefabs omit empty air; clearing uses the
 * fixed reserved box rather than listed prefab cells.
 */
public final class FestivalPrefabSwapService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    private static final int INSTANT_BLOCKS_PER_BATCH = 500_000;
    private static final long INSTANT_BATCH_DELAY_MS = 1L;

    private FestivalPrefabSwapService() {}

    /**
     * Swaps the plot to {@code targetPrefabPath}. Runs the clear and paste on the world thread, so this is safe to call
     * from a game time tick handler.
     *
     * @param onPasted runs on the world thread once the new prefab is fully in place
     * @return false when the swap could not be started (missing prefab or plot data)
     */
    public static boolean swap(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull String currentPrefabPath,
        @Nonnull String targetPrefabPath,
        @Nullable Runnable onPasted
    ) {
        ConstructionDefinition def = plugin.getConstructionCatalog().get(plot.getConstructionId());
        if (def == null) {
            LOGGER.atWarning().log("Festival square plot %s has unknown construction %s", plot.getPlotId(), plot.getConstructionId());
            return false;
        }
        // Only the target prefab is pasted; the reserved box is cleared regardless of the current prefab contents.
        IPrefabBuffer targetBuffer = PrefabResolveUtil.resolvePrefabBuffer(targetPrefabPath);
        if (targetBuffer == null) {
            LOGGER.atWarning().log("Could not swap festival square prefab: missing %s", targetPrefabPath);
            return false;
        }
        Rotation yaw = plot.resolvePrefabYaw();
        org.joml.Vector3i anchor = plot.resolvePrefabAnchorWorld(def);
        UUID townId = town.getTownId();
        UUID plotId = plot.getPlotId();
        boolean preserveWater = def.isPreserveWater();

        world.execute(() -> {
            var entityStore = world.getEntityStore();
            if (entityStore == null) {
                return;
            }
            Store<EntityStore> store = entityStore.getStore();
            TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
            TownRecord live = tm.getTown(townId);
            PlotInstance livePlot = live != null ? live.findPlotById(plotId) : null;
            if (live == null || livePlot == null) {
                return;
            }
            // Always clear the reserved festival volume from the floor up (airless prefabs no longer list Empty
            // cells). Under the floor only the cells the incoming layout lists are cleared, then the paste writes them.
            // The stored plot box keeps its full height.
            PlotFootprintRecord reservedBox = FestivalPrefabSize.footprintAt(anchor, yaw);
            SwapPlan plan =
                plan(anchor, yaw, livePlot.toFootprint(), ConstructionPasteOps.buildSequence(targetBuffer, yaw).pendingBlocks());
            PlotFootprintRecord occupied = plan.occupied();
            // Corner props live in edge chunks that are often unloaded when the swap runs from a game-time tick.
            PlotFootprintChunkUtil.ensureFootprintChunksLoaded(world, occupied);
            PropPlotTeardown.packageIntersecting(world, plugin, occupied, null, store);
            PlotBuildingRelocation.relocateTownNpcsOutOfFootprint(store, live, occupied);
            int removed = PrefabFootprintClearUtil.removePrefabOnlyEntitiesInFootprint(store, occupied, live);
            PrefabFootprintClearUtil.clearFootprint(world, plan.emptied(), true);
            // Not the square's own chests: if a player buried one here before the first festival, its items drop.
            PrefabFootprintClearUtil.clearListedCells(world, anchor, plan.underFloorWritten(), false);
            PrefabTriggerVolumeCleanup.removeVolumesInFootprint(store, occupied);
            LOGGER.atInfo().log(
                "Festival square swap cleared %d entities, emptied box [%d,%d,%d]-[%d,%d,%d], rewrites %d cells under"
                    + " the floor (%s -> %s)",
                removed,
                plan.emptied().getMinX(),
                plan.emptied().getMinY(),
                plan.emptied().getMinZ(),
                plan.emptied().getMaxX(),
                plan.emptied().getMaxY(),
                plan.emptied().getMaxZ(),
                plan.underFloorWritten().size(),
                currentPrefabPath,
                targetPrefabPath
            );

            // Keep the reserved box on the plot so the next swap clears the same volume, not only solid blocks.
            livePlot.applySignAndFootprint(livePlot.getSignX(), livePlot.getSignY(), livePlot.getSignZ(), reservedBox);
            livePlot.setPrefabWorldPlacement(anchor.x, anchor.y, anchor.z, yaw);
            tm.updateTown(live);

            // Everyday square (and festival layouts) keep the plot's saved block palette selections.
            ConstructionAnimator.start(
                plugin,
                world,
                anchor,
                yaw,
                true,
                preserveWater,
                targetBuffer,
                store,
                INSTANT_BLOCKS_PER_BATCH,
                INSTANT_BATCH_DELAY_MS,
                onPasted,
                livePlot.getBlockPaletteSelections()
            );
        });
        return true;
    }

    /**
     * What one swap does to the world, worked out before any block changes.
     *
     * @param emptied box cleared to air before the paste: the reserved volume from the paved floor up, never lower
     * @param underFloorWritten prefab-local cells the incoming layout lists under the floor (its foundation). They are
     *     the only cells under the floor a swap clears and rewrites. Empty for the everyday square.
     * @param occupied everything the incoming layout writes into: {@code emptied}, reaching down to the bottom of the
     *     plot box when the layout brings a foundation. Props, town villagers and loose entities are moved out of it.
     */
    record SwapPlan(
        @Nonnull PlotFootprintRecord emptied,
        @Nonnull List<PendingBlock> underFloorWritten,
        @Nonnull PlotFootprintRecord occupied
    ) {}

    /**
     * Plans a swap to a layout whose cells are {@code incomingCells} (as {@link ConstructionPasteOps#buildSequence}
     * lists them, already rotated). Cells under the floor that the layout does not list are in neither part of the
     * plan, so they stay exactly as the world has them: ending a festival digs nothing and leaves no cavity.
     */
    @Nonnull
    static SwapPlan plan(
        @Nonnull org.joml.Vector3i anchor,
        @Nonnull Rotation yaw,
        @Nonnull PlotFootprintRecord storedPlotBox,
        @Nonnull List<PendingBlock> incomingCells
    ) {
        PlotFootprintRecord whole = PlotFootprintRecord.union(FestivalPrefabSize.footprintAt(anchor, yaw), storedPlotBox);
        PlotFootprintRecord emptied = FestivalPrefabSize.fromFloorUp(whole, anchor.y);
        List<PendingBlock> underFloor = new ArrayList<>();
        for (PendingBlock cell : incomingCells) {
            if (cell.y() < FestivalPrefabSize.FLOOR_LOCAL_Y) {
                underFloor.add(cell);
            }
        }
        return new SwapPlan(emptied, List.copyOf(underFloor), underFloor.isEmpty() ? emptied : whole);
    }

    /** World position of a festival spot given in prefab-local cells. Centers on the block. */
    @Nonnull
    public static org.joml.Vector3d spotWorldPosition(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PlotInstance plot,
        int localX,
        int localY,
        int localZ
    ) {
        org.joml.Vector3i anchor = resolvePrefabAnchor(plugin, plot);
        Rotation yaw = plot.resolvePrefabYaw();
        org.joml.Vector3i local = new org.joml.Vector3i(localX, localY, localZ);
        com.hypixel.hytale.server.core.prefab.PrefabRotation.fromRotation(yaw).rotate(local);
        return new org.joml.Vector3d(anchor.x + local.x + 0.5, anchor.y + local.y, anchor.z + local.z + 0.5);
    }

    /**
     * World position of a prefab-local point that already includes entity-precise decimals. Rotates around the same
     * 0.5 xz pivot prefab paste uses, so a rotated square does not shift spots by a block.
     */
    @Nonnull
    public static org.joml.Vector3d spotWorldPositionExact(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PlotInstance plot,
        double localX,
        double localY,
        double localZ
    ) {
        org.joml.Vector3i anchor = resolvePrefabAnchor(plugin, plot);
        Rotation yaw = plot.resolvePrefabYaw();
        org.joml.Vector3d local = new org.joml.Vector3d(localX, localY, localZ);
        local.sub(0.5, 0.0, 0.5);
        com.hypixel.hytale.server.core.prefab.PrefabRotation.fromRotation(yaw).rotate(local);
        local.add(0.5, 0.0, 0.5);
        return new org.joml.Vector3d(anchor.x + local.x, anchor.y + local.y, anchor.z + local.z);
    }

    @Nonnull
    private static org.joml.Vector3i resolvePrefabAnchor(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PlotInstance plot
    ) {
        ConstructionDefinition def = plugin.getConstructionCatalog().get(plot.getConstructionId());
        return def != null
            ? plot.resolvePrefabAnchorWorld(def)
            : new org.joml.Vector3i(plot.getSignX(), plot.getSignY(), plot.getSignZ());
    }
}
