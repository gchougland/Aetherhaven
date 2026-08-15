package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.construction.ConstructionDefinition;
import org.joml.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.world.World;
import javax.annotation.Nonnull;

/**
 * Resolves plot sign cell (terrain-snapped at footprint center) and building prefab anchor (preview height)
 * independently at placement commit.
 */
public final class PlotPlacementHeights {

    public record ResolvedPlacement(@Nonnull Vector3i signCell, @Nonnull Vector3i buildingPrefabAnchor) {}

    private PlotPlacementHeights() {}

    /**
     * @param previewSignAnchor session anchor from the placement UI (includes Y nudge)
     */
    @Nonnull
    public static ResolvedPlacement resolve(
        @Nonnull World world,
        @Nonnull Vector3i previewSignAnchor,
        @Nonnull ConstructionDefinition def,
        @Nonnull Rotation prefabYaw,
        @Nonnull IPrefabBuffer buf
    ) {
        Vector3i buildingPrefabAnchor = def.resolvePrefabAnchorWorld(previewSignAnchor, prefabYaw);
        Vector3i signCell = PlotSignGrounding.resolveSignCell(world, previewSignAnchor, def, prefabYaw, buf);
        return new ResolvedPlacement(signCell, buildingPrefabAnchor);
    }

    /**
     * Placement for a wall piece: the sign stays exactly on the preview anchor. Wall chaining measures the next piece
     * from the stored sign of the previous one, so re-centring the sign on the footprint (which is off by a block for
     * any piece with an even span) would push every following piece out of line.
     */
    @Nonnull
    public static ResolvedPlacement resolveWallPiece(
        @Nonnull Vector3i previewSignAnchor, @Nonnull ConstructionDefinition def, @Nonnull Rotation prefabYaw
    ) {
        return new ResolvedPlacement(
            new Vector3i(previewSignAnchor), def.resolvePrefabAnchorWorld(previewSignAnchor, prefabYaw)
        );
    }
}
