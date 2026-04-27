package com.hexvane.aetherhaven.autonomy;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentRegistryProxy;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class VillagerAutonomyState implements Component<EntityStore> {
    public static final int PHASE_IDLE = 0;
    public static final int PHASE_TRAVEL = 1;
    public static final int PHASE_USE = 2;

    @Nonnull
    public static final BuilderCodec<VillagerAutonomyState> CODEC =
        BuilderCodec.builder(VillagerAutonomyState.class, VillagerAutonomyState::new)
            .append(new KeyedCodec<>("Phase", Codec.INTEGER), (v, x) -> v.phase = x, v -> v.phase)
            .add()
            .append(new KeyedCodec<>("TargetPoiId", Codec.STRING), (v, x) -> v.targetPoiId = x, v -> v.targetPoiId)
            .add()
            .append(new KeyedCodec<>("TargetX", Codec.DOUBLE), (v, x) -> v.targetX = x, v -> v.targetX)
            .add()
            .append(new KeyedCodec<>("TargetY", Codec.DOUBLE), (v, x) -> v.targetY = x, v -> v.targetY)
            .add()
            .append(new KeyedCodec<>("TargetZ", Codec.DOUBLE), (v, x) -> v.targetZ = x, v -> v.targetZ)
            .add()
            .append(new KeyedCodec<>("PhaseEndMs", Codec.LONG), (v, x) -> v.phaseEndEpochMs = x, v -> v.phaseEndEpochMs)
            .add()
            .append(new KeyedCodec<>("NextPickMs", Codec.LONG), (v, x) -> v.nextDecisionEpochMs = x, v -> v.nextDecisionEpochMs)
            .add()
            .append(
                new KeyedCodec<>("PathFailureReason", Codec.STRING),
                (v, x) -> v.pathFailureReason = x != null ? x : "",
                v -> v.pathFailureReason
            )
            .add()
            .append(new KeyedCodec<>("TravelStuckTicks", Codec.INTEGER), (v, x) -> v.travelStuckTicks = x, v -> v.travelStuckTicks)
            .add()
            .append(
                new KeyedCodec<>("PendingDoors", Codec.STRING),
                (v, x) -> v.decodePendingDoors(x),
                v -> v.encodePendingDoors()
            )
            .add()
            .append(
                new KeyedCodec<>("LastFeastGatherDeadlineAttended", Codec.LONG),
                (v, x) -> v.lastFeastGatherDeadlineAttended = x != null ? x : 0L,
                v -> v.lastFeastGatherDeadlineAttended
            )
            .add()
            .append(
                new KeyedCodec<>("TravelWaypoints", Codec.STRING),
                (v, x) -> v.decodeTravelWaypoints(x),
                v -> v.encodeTravelWaypoints()
            )
            .add()
            .append(
                new KeyedCodec<>("TravelWaypointIndex", Codec.INTEGER),
                (v, x) -> v.travelWaypointIndex = x != null ? Math.max(0, x) : 0,
                v -> v.travelWaypointIndex
            )
            .add()
            .append(
                new KeyedCodec<>("TravelWaypointStartedMs", Codec.LONG),
                (v, x) -> v.travelWaypointStartedMs = x != null ? Math.max(0L, x) : 0L,
                v -> v.travelWaypointStartedMs
            )
            .add()
            .append(
                new KeyedCodec<>("TravelWaypointStartedIndex", Codec.INTEGER),
                (v, x) -> v.travelWaypointStartedIndex = x != null ? Math.max(0, x) : 0,
                v -> v.travelWaypointStartedIndex
            )
            .add()
            .build();

    @Nullable
    private static volatile ComponentType<EntityStore, VillagerAutonomyState> componentType;

    public static void register(@Nonnull ComponentRegistryProxy<EntityStore> registry) {
        componentType = registry.registerComponent(
            VillagerAutonomyState.class,
            "AetherhavenVillagerAutonomyState",
            VillagerAutonomyState.CODEC
        );
    }

    @Nonnull
    public static ComponentType<EntityStore, VillagerAutonomyState> getComponentType() {
        ComponentType<EntityStore, VillagerAutonomyState> t = componentType;
        if (t == null) {
            throw new IllegalStateException("VillagerAutonomyState not registered");
        }
        return t;
    }

    private int phase = PHASE_IDLE;
    @Nullable
    private String targetPoiId;
    private double targetX;
    private double targetY;
    private double targetZ;
    private long phaseEndEpochMs;
    private long nextDecisionEpochMs;
    @Nonnull
    private String pathFailureReason = "";
    private int travelStuckTicks;
    /**
     * {@link com.hexvane.aetherhaven.town.TownRecord#getFeastGatherDeadlineEpochMs} for the gather session this
     * villager already finished eating at; another trip is not started until a new feast sets a new deadline.
     */
    private long lastFeastGatherDeadlineAttended;
    private final ArrayList<Vector3d> travelWaypoints = new ArrayList<>();
    private int travelWaypointIndex;
    private long travelWaypointStartedMs;
    private int travelWaypointStartedIndex;
    /** Doors opened by autonomy this trip; closed when the NPC passes through toward the leash. */
    @Nonnull
    private final ArrayList<int[]> pendingOpenDoors = new ArrayList<>();

    public VillagerAutonomyState() {}

    @Nonnull
    public static VillagerAutonomyState fresh(long nowMs) {
        VillagerAutonomyState s = new VillagerAutonomyState();
        s.nextDecisionEpochMs = nowMs;
        return s;
    }

    public int getPhase() {
        return phase;
    }

    public void setPhase(int phase) {
        this.phase = phase;
    }

    @Nullable
    public UUID getTargetPoiUuid() {
        if (targetPoiId == null || targetPoiId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(targetPoiId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setTargetPoiUuid(@Nullable UUID id) {
        this.targetPoiId = id != null ? id.toString() : null;
    }

    /** Clears POI id, travel coordinates, and pending door bookkeeping (e.g. after a rescue teleport). */
    public void clearTravelAndPoiState() {
        this.targetPoiId = null;
        this.targetX = 0.0;
        this.targetY = 0.0;
        this.targetZ = 0.0;
        this.travelWaypoints.clear();
        this.travelWaypointIndex = 0;
        this.clearPendingDoorClose();
    }

    public double getTargetX() {
        return targetX;
    }

    public double getTargetY() {
        return targetY;
    }

    public double getTargetZ() {
        return targetZ;
    }

    public void setTravelTarget(double x, double y, double z, @Nonnull UUID poiId) {
        this.targetX = x;
        this.targetY = y;
        this.targetZ = z;
        this.targetPoiId = poiId.toString();
        clearPendingDoorClose();
    }

    public void setTravelWaypoints(@Nonnull List<Vector3d> points) {
        travelWaypoints.clear();
        travelWaypointIndex = 0;
        travelWaypoints.addAll(points);
        travelWaypointStartedMs = 0L;
        travelWaypointStartedIndex = 0;
    }

    @Nullable
    public Vector3d getCurrentTravelWaypoint() {
        if (travelWaypointIndex < 0 || travelWaypointIndex >= travelWaypoints.size()) {
            return null;
        }
        return travelWaypoints.get(travelWaypointIndex);
    }

    public boolean advanceTravelWaypoint() {
        if (travelWaypointIndex + 1 < travelWaypoints.size()) {
            travelWaypointIndex++;
            travelWaypointStartedMs = 0L;
            travelWaypointStartedIndex = travelWaypointIndex;
            return true;
        }
        travelWaypointIndex = travelWaypoints.size();
        travelWaypointStartedMs = 0L;
        travelWaypointStartedIndex = travelWaypointIndex;
        return false;
    }

    public void clearTravelWaypoints() {
        travelWaypoints.clear();
        travelWaypointIndex = 0;
        travelWaypointStartedMs = 0L;
        travelWaypointStartedIndex = 0;
    }

    /**
     * Marks progress on current waypoint index for timeout tracking. Call every travel tick while waypoints are active.
     */
    public void markTravelWaypointProgress(long nowMs) {
        if (travelWaypoints.isEmpty()) {
            travelWaypointStartedMs = 0L;
            travelWaypointStartedIndex = 0;
            return;
        }
        if (travelWaypointStartedMs <= 0L || travelWaypointStartedIndex != travelWaypointIndex) {
            travelWaypointStartedMs = Math.max(0L, nowMs);
            travelWaypointStartedIndex = travelWaypointIndex;
        }
    }

    public boolean isCurrentWaypointTimedOut(long nowMs, long timeoutMs) {
        if (timeoutMs <= 0L || travelWaypoints.isEmpty() || travelWaypointStartedMs <= 0L) {
            return false;
        }
        return nowMs - travelWaypointStartedMs >= timeoutMs;
    }

    /** Doors we opened and should close once the NPC is past them (toward the leash). */
    @Nonnull
    ArrayList<int[]> getPendingOpenDoorsMutable() {
        return pendingOpenDoors;
    }

    public void addPendingDoorOpened(int x, int y, int z) {
        for (int[] d : pendingOpenDoors) {
            if (d[0] == x && d[1] == y && d[2] == z) {
                return;
            }
        }
        pendingOpenDoors.add(new int[] { x, y, z });
    }

    public void clearPendingDoorClose() {
        pendingOpenDoors.clear();
    }

    private void decodePendingDoors(@Nullable String raw) {
        pendingOpenDoors.clear();
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String part : raw.split(";")) {
            part = part.trim();
            if (part.isEmpty()) {
                continue;
            }
            String[] xyz = part.split(",");
            if (xyz.length != 3) {
                continue;
            }
            try {
                int bx = Integer.parseInt(xyz[0].trim());
                int by = Integer.parseInt(xyz[1].trim());
                int bz = Integer.parseInt(xyz[2].trim());
                pendingOpenDoors.add(new int[] { bx, by, bz });
            } catch (NumberFormatException ignored) {
                // ignore
            }
        }
    }

    @Nonnull
    private String encodePendingDoors() {
        if (pendingOpenDoors.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pendingOpenDoors.size(); i++) {
            int[] d = pendingOpenDoors.get(i);
            if (i > 0) {
                sb.append(';');
            }
            sb.append(d[0]).append(',').append(d[1]).append(',').append(d[2]);
        }
        return sb.toString();
    }

    private void decodeTravelWaypoints(@Nullable String raw) {
        travelWaypoints.clear();
        travelWaypointIndex = 0;
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String part : raw.split(";")) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            String[] xyz = p.split(",");
            if (xyz.length != 3) {
                continue;
            }
            try {
                double x = Double.parseDouble(xyz[0].trim());
                double y = Double.parseDouble(xyz[1].trim());
                double z = Double.parseDouble(xyz[2].trim());
                travelWaypoints.add(new Vector3d(x, y, z));
            } catch (NumberFormatException ignored) {
                // ignore malformed entries
            }
        }
    }

    @Nonnull
    private String encodeTravelWaypoints() {
        if (travelWaypoints.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < travelWaypoints.size(); i++) {
            Vector3d p = travelWaypoints.get(i);
            if (i > 0) {
                sb.append(';');
            }
            sb.append(p.getX()).append(',').append(p.getY()).append(',').append(p.getZ());
        }
        return sb.toString();
    }

    @Nonnull
    public String getPathFailureReason() {
        return pathFailureReason;
    }

    public void setPathFailureReason(@Nonnull String pathFailureReason) {
        this.pathFailureReason = pathFailureReason;
    }

    public int getTravelStuckTicks() {
        return travelStuckTicks;
    }

    public void setTravelStuckTicks(int travelStuckTicks) {
        this.travelStuckTicks = travelStuckTicks;
    }

    public long getPhaseEndEpochMs() {
        return phaseEndEpochMs;
    }

    public void setPhaseEndEpochMs(long phaseEndEpochMs) {
        this.phaseEndEpochMs = phaseEndEpochMs;
    }

    public long getNextDecisionEpochMs() {
        return nextDecisionEpochMs;
    }

    public void setNextDecisionEpochMs(long nextDecisionEpochMs) {
        this.nextDecisionEpochMs = nextDecisionEpochMs;
    }

    /** Wall-time {@link com.hexvane.aetherhaven.town.TownRecord#getFeastGatherDeadlineEpochMs} of the last completed feast table visit. */
    public long getLastFeastGatherDeadlineAttended() {
        return lastFeastGatherDeadlineAttended;
    }

    public void setLastFeastGatherDeadlineAttended(long lastFeastGatherDeadlineAttended) {
        this.lastFeastGatherDeadlineAttended = lastFeastGatherDeadlineAttended;
    }

    @Nullable
    @Override
    public Component<EntityStore> clone() {
        VillagerAutonomyState c = new VillagerAutonomyState();
        c.phase = phase;
        c.targetPoiId = targetPoiId;
        c.targetX = targetX;
        c.targetY = targetY;
        c.targetZ = targetZ;
        c.phaseEndEpochMs = phaseEndEpochMs;
        c.nextDecisionEpochMs = nextDecisionEpochMs;
        c.pathFailureReason = pathFailureReason;
        c.travelStuckTicks = travelStuckTicks;
        c.lastFeastGatherDeadlineAttended = lastFeastGatherDeadlineAttended;
        c.travelWaypointIndex = travelWaypointIndex;
        c.travelWaypointStartedMs = travelWaypointStartedMs;
        c.travelWaypointStartedIndex = travelWaypointStartedIndex;
        c.travelWaypoints.addAll(travelWaypoints);
        for (int[] d : pendingOpenDoors) {
            c.pendingOpenDoors.add(new int[] { d[0], d[1], d[2] });
        }
        return c;
    }
}
