package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hexvane.aetherhaven.town.TownManager;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.PrefabBufferUtil;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import java.nio.file.Path;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class PlotPlacementValidator {
    private PlotPlacementValidator() {}

    @Nullable
    public static String validate(
        @Nonnull TownManager townManager,
        @Nonnull TownRecord town,
        @Nonnull UUID ownerUuid,
        @Nonnull Vector3i signPosition,
        @Nonnull Rotation prefabYaw,
        @Nonnull ConstructionDefinition def,
        @Nonnull AetherhavenPlugin plugin
    ) {
        if (!town.getOwnerUuid().equals(ownerUuid)) {
            return "You do not own this town.";
        }
        if (!townManager.isInsideTerritory(town, signPosition.x, signPosition.z)) {
            return "Plot sign position is outside your town territory.";
        }
        Path prefabPath = resolvePrefabPath(def.getPrefabPath());
        if (prefabPath == null) {
            return "Prefab not found for construction: " + def.getId();
        }
        int[] o = def.getPlotAnchorOffset();
        Vector3i prefabOrigin = new Vector3i(signPosition.x + o[0], signPosition.y + o[1], signPosition.z + o[2]);
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
            PlotFootprintRecord overlap = town.findOverlappingPlot(fp);
            if (overlap != null) {
                return "This plot overlaps another registered plot in your town.";
            }
            return null;
        } finally {
            buf.release();
        }
    }

    @Nullable
    private static Path resolvePrefabPath(@Nonnull String key) {
        String k = key.trim();
        var ps = com.hypixel.hytale.server.core.prefab.PrefabStore.get();
        Path p = ps.findAssetPrefabPath(k);
        if (p != null) {
            return p;
        }
        if (!k.endsWith(".prefab.json")) {
            p = ps.findAssetPrefabPath(k + ".prefab.json");
            if (p != null) {
                return p;
            }
        }
        String dotted = k.replace('.', '/');
        if (!dotted.equals(k)) {
            p = ps.findAssetPrefabPath(dotted);
            if (p != null) {
                return p;
            }
            if (!dotted.endsWith(".prefab.json")) {
                p = ps.findAssetPrefabPath(dotted + ".prefab.json");
                if (p != null) {
                    return p;
                }
            }
        }
        return null;
    }
}
