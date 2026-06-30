package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.List;
import javax.annotation.Nonnull;
import org.joml.Vector3i;

/** O(1) prefab-footprint membership for assembly clearing hot paths. */
public final class AssemblyFootprintIndex {
    /** Unbiased prefab-local coords must fit 21 bits after bias (±~1M). */
    private static final int PREFAB_COORD_BIAS = 1 << 20;

    private final LongOpenHashSet prefabCells;

    private AssemblyFootprintIndex(@Nonnull LongOpenHashSet prefabCells) {
        this.prefabCells = prefabCells;
    }

    @Nonnull
    public static AssemblyFootprintIndex build(@Nonnull List<PendingBlock> footprintCells) {
        LongOpenHashSet set = new LongOpenHashSet(footprintCells.size());
        for (int i = 0; i < footprintCells.size(); i++) {
            PendingBlock pb = footprintCells.get(i);
            set.add(packPrefabCell(pb.x(), pb.y(), pb.z()));
        }
        return new AssemblyFootprintIndex(set);
    }

    public boolean containsPrefabCell(int rx, int ry, int rz) {
        return prefabCells.contains(packPrefabCell(rx, ry, rz));
    }

    public boolean containsWorldCell(@Nonnull Vector3i anchor, int wx, int wy, int wz) {
        return containsPrefabCell(wx - anchor.x, wy - anchor.y, wz - anchor.z);
    }

    public boolean containsWorldCell(@Nonnull Vector3i anchor, @Nonnull Vector3i cellWorld) {
        return containsWorldCell(anchor, cellWorld.x, cellWorld.y, cellWorld.z);
    }

    static long packPrefabCell(int x, int y, int z) {
        long px = (long) x + PREFAB_COORD_BIAS;
        long py = (long) y + PREFAB_COORD_BIAS;
        long pz = (long) z + PREFAB_COORD_BIAS;
        if (px != (px & 0x1FFFFFL) || py != (py & 0x1FFFFFL) || pz != (pz & 0x1FFFFFL)) {
            throw new IllegalStateException(
                "Prefab cell (" + x + "," + y + "," + z + ") out of incremental assembly packing range; prefab too large."
            );
        }
        return px | (py << 21) | (pz << 42);
    }
}
