package com.hexvane.aetherhaven.hunter;

import com.hexvane.aetherhaven.construction.PrefabLocalOffset;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.Rotation;
import javax.annotation.Nonnull;
import org.joml.Vector3d;
import org.joml.Vector3i;

/**
 * Where a hunt's pieces stand around the Hunt Beacon.
 *
 * <p>The beacon is a block of the lodge, so it turns with the building. The Hunter's mark, the portal and the place a
 * creature steps out are measured from it along the lodge's own axes and turned by the same quarter turns, so the
 * portal stands in front of the same row of seats and looks at the beacon however the lodge was placed. Nothing here
 * changes for older lodges without a saved author facing; their original layout needs no repair.
 *
 * @param hunterStand where the Hunter waits for the portal, and where he is put back after a defeat
 * @param portal where the portal opens
 * @param creatureSpawn where each creature steps out, one block in front of the portal
 * @param yawTowardBeacon body yaw that looks from the portal to the beacon; an unturned lodge gives 0
 */
public record HuntArenaLayout(
    @Nonnull Vector3d hunterStand,
    @Nonnull Vector3d portal,
    @Nonnull Vector3d creatureSpawn,
    float yawTowardBeacon
) {
    /** Blocks from the beacon along the lodge's own Z axis, as the lodge was built before any turning. */
    static final int HUNTER_LOCAL_Z = -3;
    static final int PORTAL_LOCAL_Z = 4;
    static final int CREATURE_LOCAL_Z = 3;

    /**
     * @param beacon the standing point on top of the Hunt Beacon
     * @param lodgeYaw how the lodge was turned when it was placed
     */
    @Nonnull
    public static HuntArenaLayout of(@Nonnull Vector3d beacon, @Nonnull Rotation lodgeYaw) {
        Vector3d portal = turned(beacon, lodgeYaw, PORTAL_LOCAL_Z);
        return new HuntArenaLayout(
            turned(beacon, lodgeYaw, HUNTER_LOCAL_Z),
            portal,
            turned(beacon, lodgeYaw, CREATURE_LOCAL_Z),
            yawLooking(beacon.x - portal.x, beacon.z - portal.z)
        );
    }

    /** Saved author facing points from the beacon toward the portal. Missing data keeps older lodges unchanged. */
    public static HuntArenaLayout of(Vector3d beacon, Rotation lodgeYaw, Float localFacingDegrees) {
        if (localFacingDegrees == null || !Float.isFinite(localFacingDegrees)) return of(beacon, lodgeYaw);
        float yaw = com.hexvane.aetherhaven.construction.PrefabYaw.worldFromPrefabLocal(lodgeYaw,
            (float) Math.toRadians(localFacingDegrees));
        double dx = -Math.sin(yaw), dz = -Math.cos(yaw);
        return new HuntArenaLayout(new Vector3d(beacon).add(dx * HUNTER_LOCAL_Z, 0, dz * HUNTER_LOCAL_Z),
            new Vector3d(beacon).add(dx * PORTAL_LOCAL_Z, 0, dz * PORTAL_LOCAL_Z),
            new Vector3d(beacon).add(dx * CREATURE_LOCAL_Z, 0, dz * CREATURE_LOCAL_Z), yawLooking(-dx, -dz));
    }

    public static Float savedFacing(com.hexvane.aetherhaven.construction.ConstructionDefinition definition) {
        if (definition == null) return null;
        for (var poi : definition.getPois()) if (HuntService.BEACON.equals(poi.getBlockTypeId())
            && poi.getTags().contains(com.hexvane.aetherhaven.plotcreator.CoreBuildingSpecialties.TAG))
            return poi.getInteractionTargetYawDegrees();
        return null;
    }

    /** The rotation given to the portal and to a creature as it steps out: both look at the beacon. */
    @Nonnull
    Rotation3f facing() {
        return new Rotation3f(0f, yawTowardBeacon, 0f);
    }

    @Nonnull
    private static Vector3d turned(@Nonnull Vector3d beacon, @Nonnull Rotation lodgeYaw, int localZ) {
        Vector3i offset = PrefabLocalOffset.rotate(lodgeYaw, 0, 0, localZ);
        return new Vector3d(beacon).add(offset.x, offset.y, offset.z);
    }

    /** Body yaw that looks along the given horizontal direction. The game's forward is (-sin yaw, -cos yaw). */
    static float yawLooking(double dx, double dz) {
        // Adding 0.0 turns a negative zero into a plain zero, so an unturned lodge gives exactly 0.
        return (float) Math.atan2(0.0 - dx, 0.0 - dz) + 0f;
    }
}
