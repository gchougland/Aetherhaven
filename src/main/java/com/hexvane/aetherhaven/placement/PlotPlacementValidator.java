package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hexvane.aetherhaven.prefab.PrefabResolveUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import java.nio.file.Path;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PlotPlacementValidator {
    private PlotPlacementValidator() {}

    @Nullable
    public static String validate(
        @Nonnull World world,
        @Nonnull TownManager townManager,
        @Nonnull TownRecord town,
        @Nonnull UUID ownerUuid,
        @Nonnull Vector3i previewSignAnchor,
        @Nonnull Rotation prefabYaw,
        @Nonnull ConstructionDefinition def,
        @Nonnull AetherhavenPlugin plugin
    ) {
        return validate(world, townManager, town, ownerUuid, previewSignAnchor, prefabYaw, def, plugin, null);
    }

    /**
     * Validates footprint at the preview building anchor while checking sign territory at the grounded sign cell.
     */
    @Nullable
    public static String validateWithResolvedHeights(
        @Nonnull World world,
        @Nonnull TownManager townManager,
        @Nonnull TownRecord town,
        @Nonnull UUID ownerUuid,
        @Nonnull Vector3i previewSignAnchor,
        @Nonnull Vector3i groundedSignCell,
        @Nonnull Vector3i buildingPrefabAnchor,
        @Nonnull Rotation prefabYaw,
        @Nonnull ConstructionDefinition def,
        @Nonnull AetherhavenPlugin plugin,
        @Nullable UUID excludePlotId
    ) {
        if (!town.playerCanPlacePlots(ownerUuid)) {
            return "You do not have permission to place buildings for this town.";
        }
        String uniqueErr = uniqueFestivalSquareReason(town, def, plugin, excludePlotId);
        if (uniqueErr != null) {
            return uniqueErr;
        }
        if (!townManager.isInsideTerritory(town, groundedSignCell.x, groundedSignCell.z)) {
            return "Plot sign position is outside your town territory.";
        }
        Path prefabPath = PrefabResolveUtil.resolvePrefabPath(def.getPrefabPath());
        if (prefabPath == null) {
            return "Prefab not found for construction: " + def.getId();
        }
        IPrefabBuffer buf = PrefabBufferUtil.getCached(prefabPath);
        try {
            PlotFootprintRecord fp = PlotFootprintUtil.computeFootprint(buildingPrefabAnchor, prefabYaw, buf, def.getPrefabPath());
            for (int x = fp.getMinX(); x <= fp.getMaxX(); x++) {
                for (int z = fp.getMinZ(); z <= fp.getMaxZ(); z++) {
                    if (!townManager.isInsideTerritory(town, x, z)) {
                        return "Part of this building would sit outside your town territory.";
                    }
                }
            }
            if (fp.containsBlock(town.getCharterX(), town.getCharterY(), town.getCharterZ())) {
                return "This plot would overlap the town charter.";
            }
            if (def.isWallSegment()) {
                PlotFootprintRecord overlap = town.findOverlappingNonWallPlot(fp, excludePlotId);
                if (overlap != null) {
                    return "This wall would overlap another building in your town.";
                }
                return null;
            }
            PlotFootprintRecord overlap = town.findOverlappingPlot(fp, excludePlotId);
            if (overlap != null) {
                return "This plot overlaps another registered plot in your town.";
            }
            return null;
        } finally {
        }
    }

    /**
     * @param excludePlotId when relocating, the plot being moved is ignored for overlap checks.
     */
    @Nullable
    public static String validate(
        @Nonnull World world,
        @Nonnull TownManager townManager,
        @Nonnull TownRecord town,
        @Nonnull UUID ownerUuid,
        @Nonnull Vector3i signPosition,
        @Nonnull Rotation prefabYaw,
        @Nonnull ConstructionDefinition def,
        @Nonnull AetherhavenPlugin plugin,
        @Nullable UUID excludePlotId
    ) {
        if (!town.playerCanPlacePlots(ownerUuid)) {
            return "You do not have permission to place buildings for this town.";
        }
        String uniqueErr = uniqueFestivalSquareReason(town, def, plugin, excludePlotId);
        if (uniqueErr != null) {
            return uniqueErr;
        }
        if (!townManager.isInsideTerritory(town, signPosition.x, signPosition.z)) {
            return "Plot sign position is outside your town territory.";
        }
        Path prefabPath = PrefabResolveUtil.resolvePrefabPath(def.getPrefabPath());
        if (prefabPath == null) {
            return "Prefab not found for construction: " + def.getId();
        }
        Vector3i prefabOrigin = def.resolvePrefabAnchorWorld(signPosition, prefabYaw);
        IPrefabBuffer buf = PrefabBufferUtil.getCached(prefabPath);
        try {
            PlotFootprintRecord fp = PlotFootprintUtil.computeFootprint(prefabOrigin, prefabYaw, buf, def.getPrefabPath());
            for (int x = fp.getMinX(); x <= fp.getMaxX(); x++) {
                for (int z = fp.getMinZ(); z <= fp.getMaxZ(); z++) {
                    if (!townManager.isInsideTerritory(town, x, z)) {
                        return "Part of this building would sit outside your town territory.";
                    }
                }
            }
            if (fp.containsBlock(town.getCharterX(), town.getCharterY(), town.getCharterZ())) {
                return "This plot would overlap the town charter.";
            }
            if (def.isWallSegment()) {
                PlotFootprintRecord overlap = town.findOverlappingNonWallPlot(fp, excludePlotId);
                if (overlap != null) {
                    return "This wall would overlap another building in your town.";
                }
                return null;
            }
            PlotFootprintRecord overlap = town.findOverlappingPlot(fp, excludePlotId);
            if (overlap != null) {
                return "This plot overlaps another registered plot in your town.";
            }
            return null;
        } finally {
        }
    }

    /** Towns may only have one festival square; relocating the existing one is allowed via {@code excludePlotId}. */
    @Nullable
    private static String uniqueFestivalSquareReason(
        @Nonnull TownRecord town,
        @Nonnull ConstructionDefinition def,
        @Nonnull AetherhavenPlugin plugin,
        @Nullable UUID excludePlotId
    ) {
        if (!plugin.getConstructionCatalog()
            .matchesGameplayConstruction(def.getId(), AetherhavenConstants.CONSTRUCTION_PLOT_FESTIVAL_SQUARE)) {
            return null;
        }
        for (PlotInstance existing : town.getPlotInstances()) {
            if (excludePlotId != null && existing.getPlotId().equals(excludePlotId)) {
                continue;
            }
            if (plugin.getConstructionCatalog()
                .matchesGameplayConstruction(
                    existing.getConstructionId(),
                    AetherhavenConstants.CONSTRUCTION_PLOT_FESTIVAL_SQUARE
                )) {
                return "Your town already has a festival square.";
            }
        }
        return null;
    }
}
