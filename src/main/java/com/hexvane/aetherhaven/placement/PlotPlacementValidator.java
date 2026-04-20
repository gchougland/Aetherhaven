package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.math.vector.Vector3i;
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
        @Nonnull Vector3i signPosition,
        @Nonnull Rotation prefabYaw,
        @Nonnull ConstructionDefinition def,
        @Nonnull AetherhavenPlugin plugin
    ) {
        return validate(world, townManager, town, ownerUuid, signPosition, prefabYaw, def, plugin, null);
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
        if (!town.playerHasBuildPermission(ownerUuid)) {
            return "You do not have permission to place buildings for this town.";
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
            PlotFootprintRecord fp = PlotFootprintUtil.computeFootprint(prefabOrigin, prefabYaw, buf);
            for (int x = fp.getMinX(); x <= fp.getMaxX(); x++) {
                for (int z = fp.getMinZ(); z <= fp.getMaxZ(); z++) {
                    if (!townManager.isInsideTerritory(town, x, z)) {
                        return "Part of this building would sit outside your town territory.";
                    }
                }
            }
            PlotFootprintRecord overlap = town.findOverlappingPlot(fp, excludePlotId);
            if (overlap != null) {
                return "This plot overlaps another registered plot in your town.";
            }
            return null;
        } finally {
            buf.release();
        }
    }
}
