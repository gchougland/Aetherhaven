package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import com.hexvane.aetherhaven.entity.EntityRotationUtil;
import com.hexvane.aetherhaven.villager.AetherhavenNpcTeleport;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3i;

/**
 * Getting a villager out of the way of a building site, and how long a site waits before it does.
 *
 * <p>A piece with someone standing in it is left for later and other pieces go first. Only when a step can place
 * nothing but such pieces does the site start to wait, and after {@link #WAIT_MS} it moves this mod's villagers who are
 * in the way to the nearest safe spot. Players are never moved: their cell waits for them.
 */
public final class SiteRefuge {
    private SiteRefuge() {}

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    /** Villagers whose move has been asked for and has not yet been handed to the game. */
    private static final java.util.Set<Ref<EntityStore>> ON_THE_WAY = ConcurrentHashMap.newKeySet();

    /** How long a site that can place nothing else waits for villagers to step out by themselves. */
    public static final long WAIT_MS = 5_000L;
    /** How far a safe spot is looked for: columns out from where the villager is, and cells up and down. */
    static final int SEARCH_OUT = 8, SEARCH_UP = 3, SEARCH_DOWN = 6;

    /** A cell someone could stand in, feet at {@code feetY}. */
    @FunctionalInterface
    public interface Standable {
        boolean at(int x, int feetY, int z);
    }

    /**
     * The nearest standable cell to a start cell, within {@code out} columns sideways and {@code up} and {@code down}
     * cells in height. A cell of height counts double: someone buried in a wall is put beside it, on the level they
     * were on, rather than on top of it.
     *
     * @return {x, feetY, z}, or null when nothing in reach can be stood in
     */
    @Nullable
    public static int[] nearest(int cx, int cy, int cz, int out, int up, int down, @Nonnull Standable standable) {
        int[] best = null;
        long bestCost = Long.MAX_VALUE;
        for (int ring = 0; ring <= out; ring++) {
            // Nothing this far out can be nearer than what is already found.
            if (best != null && (long) ring * ring >= bestCost) {
                break;
            }
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    for (int dy = -down; dy <= up; dy++) {
                        long cost = (long) dx * dx + (long) dz * dz + 4L * dy * dy;
                        if (cost < bestCost && standable.at(cx + dx, cy + dy, cz + dz)) {
                            bestCost = cost;
                            best = new int[] {cx + dx, cy + dy, cz + dz};
                        }
                    }
                }
            }
        }
        return best;
    }

    /**
     * Whether a body can stand with its feet in this cell: live terrain, something to stand on, clear for the body's
     * whole height (the engine's bounding box, so a tall townsfolk needs three clear cells), and no part of it in a
     * cell that is still to be filled.
     */
    public static boolean canStand(@Nonnull World world, int x, int feetY, int z, @Nonnull Box body, @Nonnull LongSet forbidden) {
        if (!VillagerBlockUtil.isNpcStandColumn(world, x, feetY, z)) {
            return false;
        }
        double feet = VillagerBlockUtil.resolveFeetYForStandCell(world, x, feetY, z);
        // The stand test covers the feet cell and the one above. A taller body needs the cells above those as well.
        int top = (int) Math.floor(feet + body.max.y + SiteBodies.MARGIN - SiteBodies.TOUCH);
        for (int y = feetY + 2; y <= top; y++) {
            if (!passable(world, x, y, z)) {
                return false;
            }
        }
        return !SiteCells.bodyIn(forbidden, x + 0.5, feet, z + 0.5, body);
    }

    private static boolean passable(World world, int x, int y, int z) {
        BlockType type = com.hexvane.aetherhaven.world.ChunkSectionBlockUtil.blockType(world, x, y, z);
        return type == null || type == BlockType.EMPTY || type.getMaterial() != com.hypixel.hytale.protocol.BlockMaterial.Solid;
    }

    /**
     * The nearest place this body can safely stand, looking outwards from where it is: supported, clear, and in no cell
     * of {@code forbidden}. Null when there is none within {@link #SEARCH_OUT} columns.
     */
    @Nullable
    public static Vector3d safeSpot(@Nonnull World world, @Nonnull Vector3dc from, @Nonnull Box body, @Nonnull LongSet forbidden) {
        int cx = (int) Math.floor(from.x()), cy = (int) Math.floor(from.y() + 0.001), cz = (int) Math.floor(from.z());
        int[] cell = nearest(cx, cy, cz, SEARCH_OUT, SEARCH_UP, SEARCH_DOWN, (x, y, z) -> canStand(world, x, y, z, body, forbidden));
        if (cell == null) {
            return null;
        }
        return new Vector3d(cell[0] + 0.5, VillagerBlockUtil.resolveFeetYForStandCell(world, cell[0], cell[1], cell[2]), cell[2] + 0.5);
    }

    /**
     * Moves one of this mod's villagers to the nearest safe spot. The move is handed to the world's task queue, so it is
     * safe to ask for from inside a tick; it lands a tick or two later.
     *
     * @param alsoForbidden cells besides the unbuilt ones the villager must not be put into, or null
     * @return where the villager is being sent, or null when they are a player, gone, or no safe spot was found
     */
    @Nullable
    public static Vector3d moveOut(@Nonnull World world, @Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull SiteBodies.Body body,
                                   @Nullable LongSet alsoForbidden, @Nonnull String why) {
        Ref<EntityStore> ref = body.ref();
        if (body.player() || ref == null || !ref.isValid() || !isOurs(accessor, ref)) {
            return null;
        }
        TransformComponent transform = accessor.getComponent(ref, TransformComponent.getComponentType());
        if (transform == null) {
            return null;
        }
        // A move already asked for lands on the next tick. Until then they are still seen where they were.
        if (!ON_THE_WAY.add(ref)) {
            return null;
        }
        LongSet forbidden = SiteCells.unbuiltSolid(world);
        if (alsoForbidden != null && !alsoForbidden.isEmpty()) {
            LongOpenHashSet both = new LongOpenHashSet(forbidden);
            both.addAll(alsoForbidden);
            forbidden = both;
        }
        Vector3d from = new Vector3d(transform.getPosition());
        Vector3d spot = safeSpot(world, from, SiteBodies.localBox(accessor, ref), forbidden);
        if (spot == null) {
            ON_THE_WAY.remove(ref);
            LOGGER.atWarning().log("No safe spot within %d blocks of %.1f %.1f %.1f for a villager who is %s", SEARCH_OUT, from.x, from.y, from.z, why);
            return null;
        }
        Rotation3f rotation = EntityRotationUtil.repair(transform.getRotation());
        world.execute(() -> {
            try {
                if (ref.isValid() && world.getEntityStore() != null) {
                    Store<EntityStore> store = world.getEntityStore().getStore();
                    AetherhavenNpcTeleport.apply(ref, store, Teleport.createExact(new Vector3d(spot), rotation));
                }
            } finally {
                ON_THE_WAY.remove(ref);
            }
        });
        MOVED.incrementAndGet();
        LOGGER.atInfo().log("Moved a villager who was %s from %.1f %.1f %.1f to %.1f %.1f %.1f", why, from.x, from.y, from.z, spot.x, spot.y, spot.z);
        return spot;
    }

    /** Only this mod's own villagers, townsfolk and guards are ever moved. */
    public static boolean isOurs(@Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull Ref<EntityStore> ref) {
        return accessor.getComponent(ref, com.hexvane.aetherhaven.villager.TownVillagerBinding.getComponentType()) != null;
    }

    /**
     * Before a pass that writes blocks whatever stands there (the finishing pass, a rebuild, a moved building): every
     * villager of this mod standing where one of these cells will be solid is sent to the nearest safe spot first.
     * Players are left alone.
     *
     * @param cells the pass's cells, relative to {@code origin}
     * @param from first index of {@code cells} about to be written, inclusive
     * @param to last index, exclusive
     * @param finished every cell that is solid once the pass is done, so nobody is sent into one; null to work it out
     *     from {@code cells}
     * @return how many villagers were moved
     */
    public static int clearTheWay(@Nonnull World world, @Nonnull ComponentAccessor<EntityStore> accessor, @Nonnull Vector3i origin,
                                  @Nonnull List<PendingBlock> cells, int from, int to, @Nullable LongSet finished, @Nonnull String why) {
        int end = Math.min(cells.size(), to);
        if (from >= end) {
            return 0;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = from; i < end; i++) {
            PendingBlock cell = cells.get(i);
            minX = Math.min(minX, cell.x()); maxX = Math.max(maxX, cell.x());
            minY = Math.min(minY, cell.y()); maxY = Math.max(maxY, cell.y());
            minZ = Math.min(minZ, cell.z()); maxZ = Math.max(maxZ, cell.z());
        }
        double hx = (maxX - minX + 1) / 2.0, hy = (maxY - minY + 1) / 2.0, hz = (maxZ - minZ + 1) / 2.0;
        List<SiteBodies.Body> bodies = SiteBodies.near(accessor, origin.x + minX + hx, origin.y + minY + hy, origin.z + minZ + hz,
            Math.sqrt(hx * hx + hy * hy + hz * hz) + 3.0);
        bodies.removeIf(SiteBodies.Body::player);
        if (bodies.isEmpty()) {
            return 0;
        }
        BlockTypeAssetMap<String, BlockType> types = BlockType.getAssetMap();
        LongSet solid = finished;
        int moved = 0;
        for (int i = from; i < end && !bodies.isEmpty(); i++) {
            PendingBlock cell = cells.get(i);
            if (!SiteCells.solidPiece(cell, types)) {
                continue;
            }
            Box box = cell.filler() != com.hypixel.hytale.server.core.util.FillerBlockUtil.NO_FILLER ? Box.UNIT : SiteBodies.solidBox(types.getAsset(cell.blockId()), cell.blockRotation());
            if (box == null) {
                continue;
            }
            int x = origin.x + cell.x(), y = origin.y + cell.y(), z = origin.z + cell.z();
            for (int b = bodies.size() - 1; b >= 0; b--) {
                SiteBodies.Body body = bodies.get(b);
                if (!body.overlapsBlock(x, y, z, box)) {
                    continue;
                }
                if (solid == null) {
                    solid = SiteCells.solidWhenFinished(origin, cells, index -> SiteCells.solidPiece(cells.get(index), types));
                }
                if (moveOut(world, accessor, body, solid, why) != null) {
                    moved++;
                }
                // Asked once, whether or not a spot was found: the pass does not wait.
                bodies.remove(b);
            }
        }
        return moved;
    }

    // ---- How long a site has been able to place nothing but pieces with someone in them ----

    private static final Map<UUID, Long> WAITING_SINCE = new ConcurrentHashMap<>();

    /**
     * A step of this site placed nothing because someone stood in every piece it could place.
     *
     * @return true once that has gone on for {@link #WAIT_MS}; the wait then starts again, so villagers are asked to
     *     move at most once per wait
     */
    public static boolean waitedOut(@Nonnull UUID plotId, long nowMs) {
        Long since = WAITING_SINCE.putIfAbsent(plotId, nowMs);
        if (since == null || nowMs - since < WAIT_MS) {
            return false;
        }
        WAITING_SINCE.put(plotId, nowMs);
        return true;
    }

    /** The site placed something, or has nothing waiting on anyone: no wait is running. */
    public static void notWaiting(@Nonnull UUID plotId) {
        WAITING_SINCE.remove(plotId);
    }

    // ---- Counts, for the server check and for anyone reading the log ----

    private static final AtomicLong DEFERRED = new AtomicLong(), MOVED = new AtomicLong();

    /** A piece was left for later because someone stood in it. */
    static void countDeferred() {
        DEFERRED.incrementAndGet();
    }

    /** Pieces left for later since the server started. */
    public static long deferred() {
        return DEFERRED.get();
    }

    /** Villagers moved out of the way since the server started. */
    public static long moved() {
        return MOVED.get();
    }
}
