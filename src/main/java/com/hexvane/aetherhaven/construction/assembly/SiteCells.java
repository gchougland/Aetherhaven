package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import com.hexvane.aetherhaven.town.AetherhavenWorldRegistries;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.assetstore.map.BlockTypeAssetMap;
import com.hypixel.hytale.math.shape.Box;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.universe.world.World;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntPredicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * The cells of running building sites that are still to be filled with something solid. Nobody is sent to stand in
 * one, and nobody is moved into one: a worker's stand, a rescue and the Builder's own spot all ask here first.
 */
public final class SiteCells {
    private SiteCells() {}

    /** How long one count of a world's unbuilt cells is reused. Cells placed meanwhile stay listed, which is the safe side. */
    static final long REUSE_MS = 250L;

    private record Counted(LongSet cells, long atMs) {}

    private static final ConcurrentHashMap<String, Counted> BY_WORLD = new ConcurrentHashMap<>();

    public static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

    /**
     * World cells of one site that will hold a solid block and do not yet.
     *
     * @param placed indices of {@code pending} already placed
     * @param solid whether the piece at an index of {@code pending} is solid once the building stands
     */
    @Nonnull
    public static LongOpenHashSet unbuiltSolid(@Nonnull Vector3i anchor, @Nonnull List<PendingBlock> pending,
                                               @Nonnull IntSet placed, @Nonnull IntPredicate solid) {
        LongOpenHashSet out = new LongOpenHashSet();
        for (int i = 0; i < pending.size(); i++) {
            if (placed.contains(i) || !solid.test(i)) {
                continue;
            }
            PendingBlock piece = pending.get(i);
            out.add(key(anchor.x + piece.x(), anchor.y + piece.y(), anchor.z + piece.z()));
        }
        return out;
    }

    /** World cells that are solid in the finished building, placed or not, furniture included. */
    @Nonnull
    public static LongOpenHashSet solidWhenFinished(@Nonnull Vector3i anchor, @Nonnull List<PendingBlock> cells, @Nonnull IntPredicate solid) {
        return unbuiltSolid(anchor, cells, new IntOpenHashSet(), solid);
    }

    /** Whether the piece will be something solid in the finished building. The far cells of furniture count. */
    public static boolean solidPiece(@Nonnull PendingBlock piece, @Nonnull BlockTypeAssetMap<String, BlockType> types) {
        if (piece.blockId() == 0) {
            return false;
        }
        BlockType type = types.getAsset(piece.blockId());
        return type != null && type != BlockType.EMPTY && type.getMaterial() == BlockMaterial.Solid;
    }

    /** Whether a body with this bounding box, feet at the given point, would be in any of the cells. */
    public static boolean bodyIn(@Nonnull LongSet cells, double x, double y, double z, @Nonnull Box body) {
        if (cells.isEmpty()) {
            return false;
        }
        double m = SiteBodies.MARGIN, t = SiteBodies.TOUCH;
        int x0 = (int) Math.floor(x + body.min.x - m + t), x1 = (int) Math.floor(x + body.max.x + m - t);
        int y0 = (int) Math.floor(y + body.min.y + t), y1 = (int) Math.floor(y + body.max.y + m - t);
        int z0 = (int) Math.floor(z + body.min.z - m + t), z1 = (int) Math.floor(z + body.max.z + m - t);
        for (int cx = x0; cx <= x1; cx++) {
            for (int cy = y0; cy <= y1; cy++) {
                for (int cz = z0; cz <= z1; cz++) {
                    if (cells.contains(key(cx, cy, cz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Every cell of every running site in this world that is still to be filled with something solid. Counted at most
     * every {@link #REUSE_MS}; the result must not be changed.
     */
    @Nonnull
    public static LongSet unbuiltSolid(@Nonnull World world) {
        long now = System.currentTimeMillis();
        Counted known = BY_WORLD.get(world.getName());
        if (known != null && now - known.atMs() >= 0 && now - known.atMs() < REUSE_MS) {
            return known.cells();
        }
        LongSet counted = count(world);
        BY_WORLD.put(world.getName(), new Counted(counted, now));
        return counted;
    }

    /** Forget the count, for a caller that has just changed a site and must see it at once. */
    public static void forget(@Nonnull World world) {
        BY_WORLD.remove(world.getName());
    }

    @Nonnull
    private static LongSet count(@Nonnull World world) {
        var jobs = AssemblyWorldRegistry.jobs(world);
        if (jobs.isEmpty()) {
            return LongSets.EMPTY_SET;
        }
        BlockTypeAssetMap<String, BlockType> types = BlockType.getAssetMap();
        LongOpenHashSet all = new LongOpenHashSet();
        for (PlotAssemblyJob job : jobs) {
            List<PendingBlock> pending = job.pendingBlocks();
            IntOpenHashSet placed = new IntOpenHashSet();
            PlotInstance plot = plot(world, job.plotId());
            if (plot != null) {
                plot.fillAssemblyPlacedSet(placed, pending.size());
            }
            all.addAll(unbuiltSolid(job.anchor(), pending, placed, i -> solidPiece(pending.get(i), types)));
        }
        return all;
    }

    @Nullable
    private static PlotInstance plot(@Nonnull World world, @Nonnull UUID plotId) {
        AetherhavenPlugin plugin = AetherhavenPlugin.get();
        UUID townId = AssemblyWorldRegistry.townId(world, plotId);
        if (plugin == null || townId == null) {
            return null;
        }
        TownRecord town = AetherhavenWorldRegistries.getOrCreateTownManager(world, plugin).getTown(townId);
        return town == null ? null : town.findPlotById(plotId);
    }
}
