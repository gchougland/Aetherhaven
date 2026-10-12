package com.hexvane.aetherhaven.builder;

import com.hexvane.aetherhaven.autonomy.VillagerBlockUtil;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import com.hexvane.aetherhaven.construction.assembly.PlotAssemblyJob;
import com.hexvane.aetherhaven.construction.assembly.SiteCells;
import com.hexvane.aetherhaven.construction.assembly.SiteRefuge;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.server.core.universe.world.World;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Where the core Builder stands to work at a site: on the ground just outside the building, never in a cell that is
 * still to be filled. He used to walk to where the plot sign had been, which is often inside the building, and stood
 * there while the blocks went up around and through him.
 */
public final class BuilderSiteStand {
    private BuilderSiteStand() {}

    /** How many blocks out from the building's edge a stand is looked for. */
    static final int RINGS = 4;
    /** How far above and below the building's lowest layer the ground outside may be. */
    static final int ABOVE = 4, BELOW = 3;

    /**
     * The standable cell nearest to {@code from} on the first ring around a rectangle that has one. Ring 1 touches the
     * rectangle's edge from outside; a cell inside the rectangle is never chosen.
     *
     * @return {x, feetY, z}, or null when no ring up to {@code rings} has a standable cell
     */
    @Nullable
    static int[] outside(int minX, int minZ, int maxX, int maxZ, int baseY, int above, int below, int rings,
                         double fromX, double fromY, double fromZ, @Nonnull SiteRefuge.Standable standable) {
        for (int ring = 1; ring <= rings; ring++) {
            int[] best = null;
            double bestDistance = Double.MAX_VALUE;
            for (int x = minX - ring; x <= maxX + ring; x++) {
                for (int z = minZ - ring; z <= maxZ + ring; z++) {
                    boolean edge = x == minX - ring || x == maxX + ring || z == minZ - ring || z == maxZ + ring;
                    if (!edge) {
                        continue;
                    }
                    for (int y = baseY + above; y >= baseY - below; y--) {
                        if (!standable.at(x, y, z)) {
                            continue;
                        }
                        double dx = x + 0.5 - fromX, dy = y - fromY, dz = z + 0.5 - fromZ;
                        double distance = dx * dx + dy * dy + dz * dz;
                        if (distance < bestDistance) {
                            bestDistance = distance;
                            best = new int[] {x, y, z};
                        }
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    /**
     * The Builder's stand for a site, nearest to where he is now: outside the building's own cells, with footing, clear
     * for his whole body and in no cell of any running site that is still to be filled. Null when the building is
     * hemmed in on every side for {@link #RINGS} blocks.
     */
    @Nullable
    public static Vector3d choose(@Nonnull World world, @Nonnull PlotAssemblyJob job, @Nonnull Vector3dc from, @Nonnull Box body) {
        List<PendingBlock> cells = job.pendingBlocks();
        if (cells.isEmpty()) {
            return null;
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < cells.size(); i++) {
            PendingBlock cell = cells.get(i);
            minX = Math.min(minX, cell.x()); maxX = Math.max(maxX, cell.x());
            minY = Math.min(minY, cell.y());
            minZ = Math.min(minZ, cell.z()); maxZ = Math.max(maxZ, cell.z());
        }
        LongSet forbidden = SiteCells.unbuiltSolid(world);
        int[] cell = outside(
            job.anchor().x + minX, job.anchor().z + minZ, job.anchor().x + maxX, job.anchor().z + maxZ,
            job.anchor().y + minY, ABOVE, BELOW, RINGS, from.x(), from.y(), from.z(),
            (x, y, z) -> SiteRefuge.canStand(world, x, y, z, body, forbidden)
        );
        return cell == null ? null : feet(world, cell);
    }

    /** Whether he can still stand where he was sent: the ground may have been dug away or built on since. */
    public static boolean stillGood(@Nonnull World world, double x, double y, double z, @Nonnull Box body) {
        int cx = (int) Math.floor(x), cy = (int) Math.floor(y + 0.001), cz = (int) Math.floor(z);
        return SiteRefuge.canStand(world, cx, cy, cz, body, SiteCells.unbuiltSolid(world));
    }

    /**
     * When there is no stand outside the building: the nearest safe spot to where the plot sign was, which is where he
     * used to go. Null when even that has none; the caller then keeps the old spot, and the site still refuses to place
     * a block into him.
     */
    @Nullable
    public static Vector3d nearSign(@Nonnull World world, int signX, int signY, int signZ, @Nonnull Box body) {
        return SiteRefuge.safeSpot(world, new Vector3d(signX + 0.5, signY, signZ + 0.5), body, SiteCells.unbuiltSolid(world));
    }

    @Nonnull
    private static Vector3d feet(World world, int[] cell) {
        return new Vector3d(cell[0] + 0.5, VillagerBlockUtil.resolveFeetYForStandCell(world, cell[0], cell[1], cell[2]), cell[2] + 0.5);
    }
}
