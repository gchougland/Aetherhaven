package com.hexvane.aetherhaven.autonomy;

import com.hexvane.aetherhaven.npc.NpcStandStill;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch;
import com.hexvane.aetherhaven.workplace.NativeExcavation;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import org.joml.Vector3d;

/**
 * Stuck recovery for every townsfolk journey to work: benches, furnaces, farms, mines, deliveries, shop counters and
 * the clinic. Villagers walking between places of interest have had this for a long time; workers had nothing.
 *
 * <p>A worker is sent straight to a stand. When the game's pathfinder gives up on the route it says nothing, and the
 * worker waits wherever they were until a long timeout, then tries again from the same place. This watches each
 * journey and, after a few seconds without progress, brings the worker to the stand they were walking to.
 *
 * <p>Arriving still has to be earned: each service checks the worker's real position before any work or delivery is
 * done. A worker is brought to a stand once per journey, and never onto water, unloaded ground or a spot with no
 * footing. If that does not get them there, the service's own timeout picks another stand as before.
 */
public final class WorkTravelRecovery {
    /** How long without progress before a worker is brought to their stand. */
    public static final long BRING_AFTER_MS = 4000L;
    /** Moving this far from where the wait started counts as progress, even if it is not closer yet. */
    static final double MOVED = 2.0;
    /** Getting this much closer to the stand counts as progress. Measured in three dimensions, so stairs count. */
    static final double CLOSER = 0.35;
    /** A watch not touched for this long belongs to an earlier journey. */
    static final long STALE_MS = 2000L;
    private static final int PRUNE_ABOVE = 512;
    private static final long FORGET_MS = 120_000L;

    private static final Map<UUID, Watch> WATCHES = new ConcurrentHashMap<>();

    /** One worker's current journey. */
    static final class Watch {
        double goalX = Double.NaN, goalY, goalZ;
        double anchorX, anchorZ, distance;
        long since, seen;
        boolean brought;
    }

    private WorkTravelRecovery() {}

    /**
     * Records where a travelling worker is.
     *
     * @return true once they have neither moved away nor come closer for {@link #BRING_AFTER_MS} on this journey
     */
    static boolean stalled(@Nonnull Watch watch, @Nonnull Vector3d position, @Nonnull Vector3d stand, long now) {
        double distance = position.distance(stand);
        boolean sameGoal = Math.abs(watch.goalX - stand.x) < 0.25 && Math.abs(watch.goalY - stand.y) < 0.25 && Math.abs(watch.goalZ - stand.z) < 0.25;
        boolean fresh = !sameGoal || now - watch.seen > STALE_MS;
        watch.seen = now;
        if (fresh) {
            watch.goalX = stand.x;
            watch.goalY = stand.y;
            watch.goalZ = stand.z;
            watch.brought = false;
        }
        double dx = position.x - watch.anchorX, dz = position.z - watch.anchorZ;
        if (fresh || dx * dx + dz * dz >= MOVED * MOVED || watch.distance - distance >= CLOSER) {
            watch.anchorX = position.x;
            watch.anchorZ = position.z;
            watch.distance = distance;
            watch.since = now;
            return false;
        }
        return now - watch.since >= BRING_AFTER_MS;
    }

    /** A stand is only safe to be brought to when it is in live terrain, has footing, and is not under water. */
    static boolean safeStand(boolean loaded, boolean footing, boolean feetDry, boolean headDry) {
        return loaded && footing && feetDry && headDry;
    }

    /**
     * Call each time a worker is told to keep walking to a stand. Brings them there when they are stuck.
     *
     * @return true when the worker was brought to the stand on this call
     */
    public static boolean keepMoving(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store,
                                     @Nonnull CommandBuffer<EntityStore> buffer, @Nonnull NPCEntity npc,
                                     @Nonnull Vector3d position, @Nonnull Vector3d stand, long now) {
        UUIDComponent id = store.getComponent(ref, UUIDComponent.getComponentType());
        if (id == null) {
            return false;
        }
        if (WATCHES.size() > PRUNE_ABOVE) {
            WATCHES.values().removeIf(old -> now - old.seen > FORGET_MS);
        }
        Watch watch = WATCHES.computeIfAbsent(id.getUuid(), ignored -> new Watch());
        if (!stalled(watch, position, stand, now) || watch.brought) {
            return false;
        }
        // Someone getting up from a seat is not stuck.
        if (store.getComponent(ref, com.hypixel.hytale.builtin.mounts.MountedComponent.getComponentType()) != null) {
            return false;
        }
        World world = store.getExternalData().getWorld();
        var cell = new WarehouseBatch.Point((int) Math.floor(stand.x), (int) Math.floor(stand.y), (int) Math.floor(stand.z));
        if (!safeStand(CourierWorkService.loaded(world, cell),
                VillagerBlockUtil.isNpcStandColumn(world, cell.x(), cell.y(), cell.z()),
                !NativeExcavation.observe(world, cell).protectedCell(),
                !NativeExcavation.observe(world, new WarehouseBatch.Point(cell.x(), cell.y() + 1, cell.z())).protectedCell())) {
            return false;
        }
        // Never into a cell of a building site that is still to be filled: the next piece would go where they stand.
        if (com.hexvane.aetherhaven.construction.assembly.SiteCells.bodyIn(
                com.hexvane.aetherhaven.construction.assembly.SiteCells.unbuiltSolid(world), stand.x, stand.y, stand.z,
                com.hexvane.aetherhaven.construction.assembly.SiteBodies.localBox(store, ref))) {
            return false;
        }
        watch.brought = true;
        watch.since = now;
        NpcStandStill.clearResidualMotion(store, ref, buffer);
        AutonomyStuckTeleportRecovery.teleportNpc(ref, buffer, store, new Vector3d(stand), npc);
        return true;
    }
}
