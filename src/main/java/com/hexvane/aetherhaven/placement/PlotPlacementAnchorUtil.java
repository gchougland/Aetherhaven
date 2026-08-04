package com.hexvane.aetherhaven.placement;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.protocol.BlockPosition;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

/** Shared plot sign anchor resolution for placement staff open and snap to player. */
public final class PlotPlacementAnchorUtil {
    private PlotPlacementAnchorUtil() {}

    @Nonnull
    public static Vector3i pickAnchor(@Nonnull World world, @Nonnull BlockPosition tb) {
        Vector3i above = new Vector3i(tb.x, tb.y + 1, tb.z);
        Vector3i picked;
        if (isReplaceable(world, above.x, above.y, above.z)) {
            picked = above;
        } else {
            Vector3i on = new Vector3i(tb.x, tb.y, tb.z);
            if (isReplaceable(world, on.x, on.y, on.z)) {
                picked = on;
            } else {
                picked = above;
            }
        }
        return new Vector3i(
            picked.x,
            picked.y + AetherhavenConstants.PLOT_SIGN_BLOCK_Y_ABOVE_LOGICAL_ANCHOR,
            picked.z
        );
    }

    private static boolean isReplaceable(@Nonnull World world, int x, int y, int z) {
        BlockType t = world.getBlockType(x, y, z);
        return t == null || t.getMaterial() == BlockMaterial.Empty;
    }
}
