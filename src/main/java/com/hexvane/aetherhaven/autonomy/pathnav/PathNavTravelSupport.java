package com.hexvane.aetherhaven.autonomy.pathnav;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;

/**
 * Shared path-nav travel helpers for villager and tourist autonomy: relaxed intermediate waypoint arrival, timeout
 * skips, and position-stall recovery when NPCs wedge on path corners or doorway approaches.
 */
public final class PathNavTravelSupport {
    /** Looser than POI leash arrive so NPCs do not pause on every closely spaced path node. Must be <= Leash sensor Range (1.5). */
    public static final double INTERMEDIATE_WAYPOINT_ARRIVE_HORIZONTAL = 1.25;
    public static final double INTERMEDIATE_WAYPOINT_ARRIVE_SQ =
        INTERMEDIATE_WAYPOINT_ARRIVE_HORIZONTAL * INTERMEDIATE_WAYPOINT_ARRIVE_HORIZONTAL;
    public static final long WAYPOINT_TIMEOUT_MS = 12_000L;
    public static final int POSITION_STALL_TICKS = 45;
    public static final double POSITION_STALL_MIN_MOVE = 0.1;

    public enum WaypointTickAction {
        NONE,
        ADVANCED,
        CLEARED_TO_FINAL
    }

    /** Mutable path-nav progress on an autonomy state component. */
    public interface TravelWaypoints {
        boolean hasTravelWaypoints();

        boolean hasMoreTravelWaypoints();

        @Nullable
        Vector3d getCurrentTravelWaypoint();

        boolean advanceTravelWaypoint();

        void clearTravelWaypoints();

        void markTravelWaypointProgress(long nowMs);

        boolean isCurrentWaypointTimedOut(long nowMs, long timeoutMs);

        double getTravelSampleX();

        double getTravelSampleZ();

        int getTravelProgressStallTicks();

        void setTravelSamplePosition(double x, double z);

        void setTravelProgressStallTicks(int ticks);

        void resetTravelProgressTracking();
    }

    private PathNavTravelSupport() {}

    public static double resolveArriveSq(boolean hasMoreWaypoints, double finalArriveSq) {
        return hasMoreWaypoints ? INTERMEDIATE_WAYPOINT_ARRIVE_SQ : finalArriveSq;
    }

    /**
     * Advances along path waypoints when the NPC arrives, times out, or stops making progress toward the leash.
     *
     * @return {@link WaypointTickAction#ADVANCED} when the leash should move to the next waypoint;
     *     {@link WaypointTickAction#CLEARED_TO_FINAL} when all waypoints are done and the caller should leash to the
     *     final travel target
     */
    @Nonnull
    public static WaypointTickAction tickTravelWaypoints(
        @Nonnull TravelWaypoints wp,
        @Nonnull Vector3d pos,
        double leashX,
        double leashZ,
        double finalArriveSq,
        long nowMs
    ) {
        if (!wp.hasTravelWaypoints()) {
            return WaypointTickAction.NONE;
        }

        wp.markTravelWaypointProgress(nowMs);
        updatePositionStall(wp, pos);

        if (wp.isCurrentWaypointTimedOut(nowMs, WAYPOINT_TIMEOUT_MS)) {
            return skipCurrentWaypoint(wp);
        }
        if (wp.getTravelProgressStallTicks() >= POSITION_STALL_TICKS) {
            wp.setTravelProgressStallTicks(0);
            wp.resetTravelProgressTracking();
            return skipCurrentWaypoint(wp);
        }

        double dx = pos.x - leashX;
        double dz = pos.z - leashZ;
        double horizSq = dx * dx + dz * dz;
        double arriveSq = resolveArriveSq(wp.hasMoreTravelWaypoints(), finalArriveSq);
        if (horizSq <= arriveSq) {
            return skipCurrentWaypoint(wp);
        }
        return WaypointTickAction.NONE;
    }

    @Nonnull
    private static WaypointTickAction skipCurrentWaypoint(@Nonnull TravelWaypoints wp) {
        if (wp.advanceTravelWaypoint()) {
            return WaypointTickAction.ADVANCED;
        }
        wp.clearTravelWaypoints();
        return WaypointTickAction.CLEARED_TO_FINAL;
    }

    private static void updatePositionStall(@Nonnull TravelWaypoints wp, @Nonnull Vector3d pos) {
        double sx = wp.getTravelSampleX();
        double sz = wp.getTravelSampleZ();
        if (!Double.isFinite(sx) || !Double.isFinite(sz)) {
            wp.setTravelSamplePosition(pos.x, pos.z);
            wp.setTravelProgressStallTicks(0);
            return;
        }
        double dx = pos.x - sx;
        double dz = pos.z - sz;
        if (dx * dx + dz * dz < POSITION_STALL_MIN_MOVE * POSITION_STALL_MIN_MOVE) {
            wp.setTravelProgressStallTicks(wp.getTravelProgressStallTicks() + 1);
        } else {
            wp.setTravelSamplePosition(pos.x, pos.z);
            wp.setTravelProgressStallTicks(0);
        }
    }
}
