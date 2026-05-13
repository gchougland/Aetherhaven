package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import com.hexvane.aetherhaven.town.PlotInstance;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Splits prefab-local space into an {@code N×N×N} grid of assembly sections (one section finished at a time).
 */
public final class AssemblySectionMapper {
    private static final int MAX_AXIS = 16;

    private final int n;
    private final int minX;
    private final int maxX;
    private final int minY;
    private final int maxY;
    private final int minZ;
    private final int maxZ;

    private AssemblySectionMapper(int n, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        this.n = n;
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.minZ = minZ;
        this.maxZ = maxZ;
    }

    public static int clampAxisDivisions(int raw) {
        if (raw <= 1) {
            return 1;
        }
        return Math.min(MAX_AXIS, raw);
    }

    @Nonnull
    public static AssemblySectionMapper create(@Nonnull List<PendingBlock> pending, int sectionsPerAxis) {
        int n = clampAxisDivisions(sectionsPerAxis);
        if (n <= 1) {
            throw new IllegalArgumentException("sectionsPerAxis must be >= 2");
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (PendingBlock pb : pending) {
            int x = pb.x();
            int y = pb.y();
            int z = pb.z();
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        if (minX == Integer.MAX_VALUE) {
            minX = maxX = minY = maxY = minZ = maxZ = 0;
        }
        return new AssemblySectionMapper(n, minX, maxX, minY, maxY, minZ, maxZ);
    }

    /** First flat index {@code 0..n³-1} that contains at least one pending cell. */
    public static int firstOccupiedFlatSection(@Nonnull List<PendingBlock> pending, int sectionsPerAxis) {
        int n = clampAxisDivisions(sectionsPerAxis);
        if (n <= 1) {
            return 0;
        }
        AssemblySectionMapper m = create(pending, n);
        int vol = m.sectionCount();
        for (int s = 0; s < vol; s++) {
            if (m.sectionHasAnyCell(pending, s)) {
                return s;
            }
        }
        return 0;
    }

    public int sectionsPerAxis() {
        return n;
    }

    public int sectionCount() {
        return n * n * n;
    }

    public int flatSectionFor(@Nonnull PendingBlock pb) {
        return flatSectionFor(pb.x(), pb.y(), pb.z());
    }

    public int flatSectionFor(int x, int y, int z) {
        int sx = axisBin(x, minX, maxX);
        int sy = axisBin(y, minY, maxY);
        int sz = axisBin(z, minZ, maxZ);
        return sx + sy * n + sz * n * n;
    }

    public boolean isCellInSection(@Nonnull PendingBlock pb, int flatSection) {
        return flatSectionFor(pb) == flatSection;
    }

    public boolean sectionHasAnyCell(@Nonnull List<PendingBlock> pending, int flatSection) {
        for (int i = 0; i < pending.size(); i++) {
            if (flatSectionFor(pending.get(i)) == flatSection) {
                return true;
            }
        }
        return false;
    }

    /** True when every pending cell in {@code flatSection} is recorded as placed on {@code plot}. */
    public boolean isSectionComplete(@Nonnull List<PendingBlock> pending, @Nonnull PlotInstance plot, int flatSection) {
        if (!sectionHasAnyCell(pending, flatSection)) {
            return false;
        }
        IntOpenHashSet placed = new IntOpenHashSet();
        plot.fillAssemblyPlacedSet(placed, pending.size());
        for (int i = 0; i < pending.size(); i++) {
            if (flatSectionFor(pending.get(i)) != flatSection) {
                continue;
            }
            if (!placed.contains(i)) {
                return false;
            }
        }
        return true;
    }

    private int axisBin(int value, int lo, int hi) {
        int span = Math.max(1, hi - lo + 1);
        int rel = value - lo;
        if (rel <= 0) {
            return 0;
        }
        int bin = (int) ((long) rel * n / span);
        return Math.min(n - 1, bin);
    }

    @Nullable
    public static AssemblySectionMapper tryCreate(@Nonnull List<PendingBlock> pending, int sectionsPerAxis) {
        int n = clampAxisDivisions(sectionsPerAxis);
        if (n <= 1) {
            return null;
        }
        return create(pending, n);
    }
}
