package com.hexvane.aetherhaven.festival;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.placement.PlotBuildingRelocation;
import com.hexvane.aetherhaven.placement.PrefabFootprintClearUtil;
import com.hexvane.aetherhaven.placement.PrefabTriggerVolumeCleanup;
import com.hexvane.aetherhaven.prefab.ConstructionAnimator;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
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
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Replaces what stands on a festival square plot: the everyday prefab from its construction definition, or a festival's
 * own prefab for the length of the festival. Every festival prefab reserves the same volume, so the swap clears the old
 * cells at the plot's stored anchor and pastes the new prefab at the same anchor and yaw.
 *
 * <p>Anything a player left inside the square is removed by the swap.
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
        IPrefabBuffer currentBuffer = PrefabResolveUtil.resolvePrefabBuffer(currentPrefabPath);
        IPrefabBuffer targetBuffer = PrefabResolveUtil.resolvePrefabBuffer(targetPrefabPath);
        if (currentBuffer == null || targetBuffer == null) {
            LOGGER.atWarning().log(
                "Could not swap festival square prefab: missing %s",
                currentBuffer == null ? currentPrefabPath : targetPrefabPath
            );
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
            // Festival prefabs reserve a full empty box; the solid AABB is shorter and misses props / volumes in air.
            PlotFootprintRecord currentBox = PrefabTriggerVolumeCleanup.prefabBox(anchor, yaw, currentBuffer);
            PlotFootprintRecord targetBox = PrefabTriggerVolumeCleanup.prefabBox(anchor, yaw, targetBuffer);
            // Union current + target + stored plot so leftover corner props (statues) from either side are covered.
            PlotFootprintRecord clearFootprint =
                PlotFootprintRecord.union(PlotFootprintRecord.union(currentBox, targetBox), livePlot.toFootprint());
            // Corner props live in edge chunks that are often unloaded when the swap runs from a game-time tick.
            PlotFootprintChunkUtil.ensureFootprintChunksLoaded(world, clearFootprint);
            PlotBuildingRelocation.relocateTownNpcsOutOfFootprint(store, live, clearFootprint);
            int removed =
                PrefabFootprintClearUtil.removePrefabOnlyEntitiesInFootprint(store, clearFootprint, live);
            PrefabFootprintClearUtil.clearPrefabCellsAtAnchor(world, anchor, yaw, currentBuffer, preserveWater);
            LOGGER.atInfo().log(
                "Festival square swap cleared %d entities in box [%d,%d,%d]-[%d,%d,%d] (%s -> %s)",
                removed,
                clearFootprint.getMinX(),
                clearFootprint.getMinY(),
                clearFootprint.getMinZ(),
                clearFootprint.getMaxX(),
                clearFootprint.getMaxY(),
                clearFootprint.getMaxZ(),
                currentPrefabPath,
                targetPrefabPath
            );

            // Keep the reserved box on the plot so the next swap clears the same volume, not only solid blocks.
            livePlot.applySignAndFootprint(livePlot.getSignX(), livePlot.getSignY(), livePlot.getSignZ(), targetBox);
            livePlot.setPrefabWorldPlacement(anchor.x, anchor.y, anchor.z, yaw);
            tm.updateTown(live);

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
                onPasted
            );
        });
        return true;
    }

    /** World position of a festival spot given in prefab-local cells. */
    @Nonnull
    public static org.joml.Vector3d spotWorldPosition(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull PlotInstance plot,
        int localX,
        int localY,
        int localZ
    ) {
        ConstructionDefinition def = plugin.getConstructionCatalog().get(plot.getConstructionId());
        Rotation yaw = plot.resolvePrefabYaw();
        org.joml.Vector3i anchor =
            def != null
                ? plot.resolvePrefabAnchorWorld(def)
                : new org.joml.Vector3i(plot.getSignX(), plot.getSignY(), plot.getSignZ());
        org.joml.Vector3i local = new org.joml.Vector3i(localX, localY, localZ);
        com.hypixel.hytale.server.core.prefab.PrefabRotation.fromRotation(yaw).rotate(local);
        return new org.joml.Vector3d(anchor.x + local.x + 0.5, anchor.y + local.y, anchor.z + local.z + 0.5);
    }
}
