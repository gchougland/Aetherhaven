package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * Growth frontier for plot assembly: paint any cell touching (6-neighbor) an already-placed prefab cell. Multiple
 * disconnected solids fall back to lowest-Y seeds among remaining blocks.
 */
public final class PlotAssemblyFrontier {
    private PlotAssemblyFrontier() {}

    public static boolean areAdjacent6(@Nonnull PendingBlock a, @Nonnull PendingBlock b) {
        int dx = Math.abs(a.x() - b.x());
        int dy = Math.abs(a.y() - b.y());
        int dz = Math.abs(a.z() - b.z());
        return dx + dy + dz == 1;
    }

    /**
     * Unplaced indices available for the next placement. Starts from the global lowest-Y layer; thereafter expands by
     * face adjacency to placed cells. If adjacency yields none but blocks remain, seeds again at lowest Y among
     * unplaced (disconnected volumes).
     */
    @Nonnull
    public static IntArrayList frontierIndices(@Nonnull List<PendingBlock> pending, @Nonnull IntSet placed) {
        int n = pending.size();
        IntArrayList out = new IntArrayList();
        if (placed.size() >= n) {
            return out;
        }

        if (placed.isEmpty()) {
            int minY = Integer.MAX_VALUE;
            for (PendingBlock pb : pending) {
                minY = Math.min(minY, pb.y());
            }
            for (int i = 0; i < n; i++) {
                if (!placed.contains(i) && pending.get(i).y() == minY) {
                    out.add(i);
                }
            }
            return out;
        }

        for (int i = 0; i < n; i++) {
            if (placed.contains(i)) {
                continue;
            }
            PendingBlock pi = pending.get(i);
            IntIterator it = placed.iterator();
            while (it.hasNext()) {
                int j = it.nextInt();
                if (areAdjacent6(pi, pending.get(j))) {
                    out.add(i);
                    break;
                }
            }
        }

        if (!out.isEmpty()) {
            return out;
        }

        int minYUnplaced = Integer.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            if (placed.contains(i)) {
                continue;
            }
            minYUnplaced = Math.min(minYUnplaced, pending.get(i).y());
        }
        if (minYUnplaced == Integer.MAX_VALUE) {
            return out;
        }
        for (int i = 0; i < n; i++) {
            if (!placed.contains(i) && pending.get(i).y() == minYUnplaced) {
                out.add(i);
            }
        }
        return out;
    }

    public static boolean frontierContains(@Nonnull IntArrayList frontier, int placementIndex) {
        for (int k = 0; k < frontier.size(); k++) {
            if (frontier.getInt(k) == placementIndex) {
                return true;
            }
        }
        return false;
    }

    /** Deterministic passive choice: smallest prefab sequence index on the frontier. */
    public static int smallestPlacementIndex(@Nonnull IntArrayList frontier) {
        if (frontier.isEmpty()) {
            return -1;
        }
        int best = frontier.getInt(0);
        for (int k = 1; k < frontier.size(); k++) {
            int v = frontier.getInt(k);
            if (v < best) {
                best = v;
            }
        }
        return best;
    }
}
