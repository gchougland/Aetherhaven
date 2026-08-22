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
 * Prefab-local facing for a placed POI. Stand cell is {@code localX/Y/Z} (the click); yaw alone encodes look
 * direction — no separate interaction-target cell.
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
            applyFromBlockFacing(world, blockWorldPos, poi);
            return;
        }
        Rotation placement = PlotCreatorPrefabCoords.placementYaw(draft);
        float worldYaw = playerTc.getRotation().yaw();
        float prefabYaw = PrefabYaw.prefabFromWorld(placement, worldYaw);
        applyFacingFromPrefabYaw(prefabYaw, poi);
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
        applyFacingFromPrefabYaw(prefabYaw, poi);
    }

    private static void applyFacingFromPrefabYaw(float prefabYaw, @Nonnull PlotCreatorPoiDraft poi) {
        poi.setInteractionTargetLocal(null, null, null);
        poi.setInteractionTargetYawDegrees(normalizeDegrees((float) Math.toDegrees(prefabYaw)));
    }

    public static void applyFromBlockFacing(
        @Nonnull World world,
        @Nonnull Vector3i blockWorldPos,
        @Nonnull PlotCreatorPoiDraft poi
    ) {
        poi.setInteractionTargetLocal(null, null, null);
        Rotation yaw = PlotBlockRotationUtil.readBlockYaw(world, blockWorldPos);
        Float deg = degreesFromBlockYaw(yaw);
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

    @Nullable
    private static Float degreesFromBlockYaw(@Nonnull Rotation yaw) {
        return switch (yaw) {
            case None -> 0f;
            case Ninety -> 90f;
            case OneEighty -> 180f;
            case TwoSeventy -> -90f;
            default -> null;
        };
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
