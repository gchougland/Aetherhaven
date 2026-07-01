package com.hexvane.aetherhaven.guild;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hexvane.aetherhaven.poi.PoiEntry;
import com.hexvane.aetherhaven.poi.PoiRegistry;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Resolves the bard's guild hall work POI, including prefab-local fallback for older halls. */
public final class BardWorkPoiResolver {
    private BardWorkPoiResolver() {}

    @Nullable
    public static PoiEntry findOnPlot(@Nonnull PoiRegistry reg, @Nonnull UUID townId, @Nonnull UUID plotId) {
        return findForTownPlot(reg, townId, plotId);
    }

    @Nullable
    public static PoiEntry findForTownPlot(
        @Nonnull PoiRegistry reg,
        @Nonnull UUID townId,
        @Nonnull UUID plotId
    ) {
        for (PoiEntry e : reg.listByTown(townId)) {
            if (plotId.equals(e.getPlotId()) && e.getTags().contains(AetherhavenConstants.POI_TAG_BARD)) {
                return e;
            }
        }
        return null;
    }

    /**
     * World position for bard placement: registered BARD POI, or construction fallback locals rotated from plot anchor.
     */
    @Nullable
    public static BardPlacementTarget resolvePlacement(
        @Nonnull AetherhavenPlugin plugin,
        @Nonnull TownRecord town,
        @Nonnull PlotInstance plot,
        @Nonnull PoiRegistry reg
    ) {
        PoiEntry poi = findOnPlot(reg, town.getTownId(), plot.getPlotId());
        if (poi != null) {
            return targetFromPoi(poi);
        }
        ConstructionDefinition def = plugin.getConstructionCatalog().get(plot.getConstructionId());
        if (def == null || def.getBardWorkPoiLocal() == null) {
            def = plugin.getConstructionCatalog().get(AetherhavenConstants.CONSTRUCTION_PLOT_GUILD_HALL);
        }
        if (def == null) {
            return null;
        }
        int[] local = def.getBardWorkPoiLocal();
        if (local == null) {
            return null;
        }
        Rotation yaw = plot.resolvePrefabYaw();
        var anchor = plot.resolvePrefabAnchorWorld(def);
        var rotated = PrefabLocalOffset.rotate(yaw, local[0], local[1], local[2]);
        double x = anchor.x + rotated.x + 0.5;
        double y = anchor.y + rotated.y + 0.02;
        double z = anchor.z + rotated.z + 0.5;
        int[] targetLocal = def.getBardWorkPoiInteractionTargetLocal();
        if (targetLocal != null) {
            var rt = PrefabLocalOffset.rotate(yaw, targetLocal[0], targetLocal[1], targetLocal[2]);
            return new BardPlacementTarget(x, y, z, anchor.x + rt.x, anchor.y + rt.y, anchor.z + rt.z);
        }
        return new BardPlacementTarget(x, y, z, x, y, z);
    }

    @Nullable
    private static BardPlacementTarget targetFromPoi(@Nonnull PoiEntry poi) {
        double x = poi.getX() + 0.5;
        double y = poi.getY() + 0.02;
        double z = poi.getZ() + 0.5;
        if (poi.hasInteractionTarget()) {
            Double tx = poi.getInteractionTargetX();
            Double ty = poi.getInteractionTargetY();
            Double tz = poi.getInteractionTargetZ();
            if (tx != null && ty != null && tz != null) {
                return new BardPlacementTarget(x, y, z, tx, ty, tz);
            }
        }
        return new BardPlacementTarget(x, y, z, x, y, z);
    }

    public record BardPlacementTarget(
        double standX,
        double standY,
        double standZ,
        double faceX,
        double faceY,
        double faceZ
    ) {}
}
