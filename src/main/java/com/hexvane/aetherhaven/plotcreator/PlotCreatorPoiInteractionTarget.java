package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.construction.PrefabYaw;
import com.hexvane.aetherhaven.plot.PlotBlockRotationUtil;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * Prefab-local stand cell and facing for a placed POI. Prefer the placing player's body yaw; fall back to block
 * yaw when player transform is unavailable.
 */
public final class PlotCreatorPoiInteractionTarget {
    private PlotCreatorPoiInteractionTarget() {}

    public static void applyFromPlayerFacing(
        @Nonnull PlotCreatorDraft draft,
        @Nonnull Ref<EntityStore> playerRef,
        @Nonnull Store<EntityStore> store,
        @Nonnull World world,
        @Nonnull Vector3i blockWorldPos,
        @Nonnull int[] poiLocal,
        @Nonnull PlotCreatorPoiDraft poi
    ) {
        TransformComponent playerTc = store.getComponent(playerRef, TransformComponent.getComponentType());
        if (playerTc == null) {
            applyFromBlockFacing(world, blockWorldPos, poiLocal, poi);
            return;
        }
        Rotation placement = PlotCreatorPrefabCoords.placementYaw(draft);
        float worldYaw = playerTc.getRotation().yaw();
        float prefabYaw = PrefabYaw.prefabFromWorld(placement, worldYaw);
        applyFacingFromPrefabYaw(prefabYaw, poiLocal, poi);
    }

    /**
     * Uses the seat or bed forward direction for mount POIs so villagers face the front of the chair or bench.
     */
    public static void applyFromSeatFacing(
        @Nonnull PlotCreatorDraft draft,
        float worldSeatYawRadians,
        @Nonnull int[] poiLocal,
        @Nonnull PlotCreatorPoiDraft poi
    ) {
        Rotation placement = PlotCreatorPrefabCoords.placementYaw(draft);
        float prefabYaw = PrefabYaw.prefabFromWorld(placement, worldSeatYawRadians);
        applyFacingFromPrefabYaw(prefabYaw, poiLocal, poi);
    }

    private static void applyFacingFromPrefabYaw(
        float prefabYaw,
        @Nonnull int[] poiLocal,
        @Nonnull PlotCreatorPoiDraft poi
    ) {
        poi.setInteractionTargetYawDegrees(normalizeDegrees((float) Math.toDegrees(prefabYaw)));
        int[] forward = horizontalForwardLocal(prefabYaw);
        // Stand one cell behind the facing direction so the villager looks toward the POI block.
        poi.setInteractionTargetLocal(poiLocal[0] - forward[0], poiLocal[1], poiLocal[2] - forward[2]);
    }

    public static void applyFromBlockFacing(
        @Nonnull World world,
        @Nonnull Vector3i blockWorldPos,
        @Nonnull int[] poiLocal,
        @Nonnull PlotCreatorPoiDraft poi
    ) {
        Rotation yaw = PlotBlockRotationUtil.readBlockYaw(world, blockWorldPos);
        int[] forward = horizontalForwardLocal(yaw);
        poi.setInteractionTargetLocal(poiLocal[0] + forward[0], poiLocal[1], poiLocal[2] + forward[2]);
        // Best-effort facing toward the POI from that stand cell (look back at the block).
        Float deg = degreesLookingBackAtPoi(forward);
        if (deg != null) {
            poi.setInteractionTargetYawDegrees(deg);
        }
    }

    /** Prefab-local horizontal forward for a body yaw (same units as autonomy body yaw). */
    @Nonnull
    static int[] horizontalForwardLocal(float prefabYawRadians) {
        int fx = (int) Math.round(-Math.sin(prefabYawRadians));
        int fz = (int) Math.round(-Math.cos(prefabYawRadians));
        if (fx == 0 && fz == 0) {
            return new int[] {0, 0, -1};
        }
        if (Math.abs(fx) + Math.abs(fz) > 1) {
            if (Math.abs(fx) >= Math.abs(fz)) {
                return new int[] {Integer.signum(fx), 0, 0};
            }
            return new int[] {0, 0, Integer.signum(fz)};
        }
        return new int[] {fx, 0, fz};
    }

    /**
     * Prefab-local horizontal step from POI cell toward the stand (older plot creator / block-yaw path).
     */
    @Nonnull
    private static int[] horizontalForwardLocal(@Nonnull Rotation yaw) {
        return switch (yaw) {
            case None -> new int[] {0, 0, -1};
            case Ninety -> new int[] {1, 0, 0};
            case OneEighty -> new int[] {0, 0, 1};
            case TwoSeventy -> new int[] {-1, 0, 0};
            default -> new int[] {0, 0, -1};
        };
    }

    /** Degrees for looking from stand cell back toward the POI (opposite the stand offset). */
    @Nullable
    private static Float degreesLookingBackAtPoi(@Nonnull int[] standOffsetFromPoi) {
        int dx = -standOffsetFromPoi[0];
        int dz = -standOffsetFromPoi[2];
        if (dx == 0 && dz == 0) {
            return null;
        }
        return normalizeDegrees((float) Math.toDegrees(Math.atan2(dx, dz) + Math.PI));
    }

    private static float normalizeDegrees(float degrees) {
        float d = degrees;
        while (d > 180f) {
            d -= 360f;
        }
        while (d <= -180f) {
            d += 360f;
        }
        return d;
    }
}
