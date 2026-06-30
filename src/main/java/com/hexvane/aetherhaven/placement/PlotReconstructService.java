package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCompleter;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.assembly.AssemblyWorldRegistry;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.prefab.ConstructionAnimator;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotFootprintChunkUtil;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.TownDissolutionService;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Path;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/** Clears a plot footprint and re-pastes its prefab, then runs {@link ConstructionCompleter#finishBuild}. */
public final class PlotReconstructService {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int SIGN_BREAK_SETTINGS = 10;
    private static final int INSTANT_BLOCKS_PER_BATCH = 500_000;
    private static final long INSTANT_BATCH_DELAY_MS = 1L;

    private PlotReconstructService() {}

    public enum ReconstructResult {
        OK,
        CHUNKS_UNLOADED,
        UNKNOWN_CONSTRUCTION,
        PREFAB_MISSING,
        WALL_OR_DECORATION,
        PREFAB_FAILED
    }

    @Nonnull
    public static ReconstructResult reconstruct(
        @Nonnull World world,
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull UUID actorUuid,
        @Nonnull Store<EntityStore> entityStore
    ) {
        if (!PlotFootprintChunkUtil.isPlotFullyLoaded(world, plot)) {
            return ReconstructResult.CHUNKS_UNLOADED;
        }
        ConstructionDefinition def = plugin.getConstructionCatalog().get(plot.getConstructionId());
        if (def == null) {
            return ReconstructResult.UNKNOWN_CONSTRUCTION;
        }
        if (def.isWallSegment() || def.isDecorationPlot()) {
            return ReconstructResult.WALL_OR_DECORATION;
        }
        Path prefabPath = PrefabResolveUtil.resolvePrefabPath(def.getPrefabPath());
        if (prefabPath == null) {
            return ReconstructResult.PREFAB_MISSING;
        }

        UUID plotId = plot.getPlotId();
        LOGGER.atInfo().log(
            "Plot reconstruct town=%s plot=%s construction=%s actor=%s",
            town.getTownId(),
            plotId,
            plot.getConstructionId(),
            actorUuid
        );

        if (plot.getState() == PlotInstanceState.ASSEMBLING) {
            AssemblyWorldRegistry.remove(world, plotId);
        }

        PlotFootprintRecord oldFootprint = plot.toFootprint();
        PoiRegistry poiReg = AetherhavenWorldRegistries.getOrCreatePoiRegistry(world, plugin);
        PlotBuildingRelocation.relocateTownNpcsOutOfFootprint(entityStore, town, oldFootprint);
        TownDissolutionService.clearPlotFromWorld(world, plugin, town, plot, entityStore, poiReg);
        plot.clearAssemblyPersistence();

        Vector3i anchor = plot.resolvePrefabAnchorWorld(def);
        Rotation yaw = plot.resolvePrefabYaw();
        if (anchor == null) {
            Vector3i sign = new Vector3i(plot.getSignX(), plot.getSignY(), plot.getSignZ());
            anchor = def.resolvePrefabAnchorWorld(sign, yaw);
        }

        IPrefabBuffer buffer = PrefabBufferUtil.getCached(prefabPath);
        PlotFootprintRecord newFp = PlotFootprintUtil.computeFootprint(anchor, yaw, buffer);
        plot.applySignAndFootprint(plot.getSignX(), plot.getSignY(), plot.getSignZ(), newFp);
        plot.setPrefabWorldPlacement(anchor.x, anchor.y, anchor.z, yaw);

        TownManager tm = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin);
        tm.updateTown(town);

        Vector3i finalAnchor = anchor;
        Runnable onComplete =
            () -> {
                if (plot.getState() == PlotInstanceState.BLUEPRINTING) {
                    world.breakBlock(plot.getSignX(), plot.getSignY(), plot.getSignZ(), SIGN_BREAK_SETTINGS);
                }
                ConstructionCompleter.finishBuild(world, plugin, actorUuid, plotId, finalAnchor, yaw);
            };
        ConstructionAnimator.start(
            plugin,
            world,
            anchor,
            yaw,
            true,
            buffer,
            entityStore,
            INSTANT_BLOCKS_PER_BATCH,
            INSTANT_BATCH_DELAY_MS,
            onComplete
        );
        return ReconstructResult.OK;
    }
}
