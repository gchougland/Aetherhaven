package com.hexvane.aetherhaven.prop;

import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import com.hypixel.hytale.server.core.prefab.selection.buffer.impl.IPrefabBuffer;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/** While holding the packaging wand: blue cubes on nearby props, a brighter cube on the one under the crosshair. */
public final class PropPackagingOverlay {
    private static final double NEARBY_RADIUS = 24.0;
    /**
     * Must exceed {@link PropPackagingWandTickSystem}'s refresh interval with margin. Each show clears then redraws;
     * if hold expires before the next refresh the cubes vanish until the next tick redraw.
     */
    private static final float HOLD_SECONDS = 2.5f;
    private static final float NEARBY_R = 0.35f;
    private static final float NEARBY_G = 0.55f;
    private static final float NEARBY_B = 1.0f;
    private static final float NEARBY_A = 0.22f;

    private static final float LOOKED_AT_R = 0.35f;
    private static final float LOOKED_AT_G = 1.0f;
    private static final float LOOKED_AT_B = 0.55f;
    private static final float LOOKED_AT_A = 0.42f;

    private PropPackagingOverlay() {}

    public static void clearFor(@Nullable PlayerRef player) {
        PropDebugCubeUtil.clearFor(player);
    }

    public static void show(
        @Nonnull PlayerRef player,
        @Nonnull PropRegistry registry,
        @Nonnull PropCatalog catalog,
        @Nonnull Vector3d playerPosition,
        @Nullable PropInstance lookedAt
    ) {
        clearFor(player);
        double radiusSq = NEARBY_RADIUS * NEARBY_RADIUS;
        for (PropInstance instance : registry.all()) {
            double dx = instance.getAnchorX() - playerPosition.x;
            double dy = instance.getAnchorY() - playerPosition.y;
            double dz = instance.getAnchorZ() - playerPosition.z;
            if (dx * dx + dy * dy + dz * dz > radiusSq) {
                continue;
            }
            IPrefabBuffer buffer = PropLookupUtil.resolveBuffer(catalog, instance);
            if (buffer == null) {
                continue;
            }
            PlotFootprintRecord fp = PropPrefabOps.footprint(instance.getAnchor(), instance.getYaw(), buffer);
            boolean isLookedAt = lookedAt != null && sameInstance(lookedAt, instance);
            // Padding is visual-only (pick + clarity). Entity teardown uses exact footprint elsewhere.
            double pad = isLookedAt ? PropBoundsUtil.PROP_BOUNDS_PADDING : PropBoundsUtil.PROP_BOUNDS_PADDING * 0.5;
            if (isLookedAt) {
                PropDebugCubeUtil.sendFootprintCube(
                    player, fp, pad, LOOKED_AT_R, LOOKED_AT_G, LOOKED_AT_B, LOOKED_AT_A, HOLD_SECONDS
                );
            } else {
                PropDebugCubeUtil.sendFootprintCube(
                    player, fp, pad, NEARBY_R, NEARBY_G, NEARBY_B, NEARBY_A, HOLD_SECONDS
                );
            }
        }
    }

    private static boolean sameInstance(@Nonnull PropInstance a, @Nonnull PropInstance b) {
        UUID idA = a.getInstanceId();
        UUID idB = b.getInstanceId();
        return idA.equals(idB);
    }
}
