package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.construction.ConstructionPasteOps.PendingBlock;
import com.hexvane.aetherhaven.town.PlotInstance;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.joml.Vector3i;

/**
 * Splits prefab-local space into a grid of assembly sections (one section finished at a time). Each axis may use a
 * different division count.
 */
public final class AssemblySectionMapper {
    private static final int MAX_AXIS = 16;

    private final int nx;
    private final int ny;
    private final int nz;
    private final int minX;
    private final int maxX;
    private final int minY;
    private final int maxY;
    private final int minZ;
    private final int maxZ;

    private AssemblySectionMapper(
        int nx,
        int ny,
        int nz,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ
    ) {
        this.nx = nx;
        this.ny = ny;
        this.nz = nz;
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

    public static int divisionsForSpan(int span, int chunkSize) {
        int size = Math.max(1, chunkSize);
        int s = Math.max(1, span);
        if (s <= size) {
            return 1;
        }
        return clampAxisDivisions((s + size - 1) / size);
    }

    @Nonnull
    public static AssemblySectionMapper create(
        @Nonnull List<PendingBlock> pending,
        int divisionsX,
        int divisionsY,
        int divisionsZ
    ) {
        int nx = clampAxisDivisions(divisionsX);
        int ny = clampAxisDivisions(divisionsY);
        int nz = clampAxisDivisions(divisionsZ);
        if (nx <= 1 && ny <= 1 && nz <= 1) {
            throw new IllegalArgumentException("at least one axis division must be >= 2");
        }
        Bounds b = scanBounds(pending);
        return new AssemblySectionMapper(nx, ny, nz, b.minX, b.maxX, b.minY, b.maxY, b.minZ, b.maxZ);
    }

    @Nonnull
    public static AssemblySectionMapper createAuto(@Nonnull List<PendingBlock> pending, int chunkSize) {
        Bounds b = scanBounds(pending);
        int nx = divisionsForSpan(b.maxX - b.minX + 1, chunkSize);
        int ny = divisionsForSpan(b.maxY - b.minY + 1, chunkSize);
        int nz = divisionsForSpan(b.maxZ - b.minZ + 1, chunkSize);
        return create(pending, nx, ny, nz);
    }

    @Nullable
    public static AssemblySectionMapper tryCreateAuto(@Nonnull List<PendingBlock> pending, int chunkSize) {
        Bounds b = scanBounds(pending);
        int nx = divisionsForSpan(b.maxX - b.minX + 1, chunkSize);
        int ny = divisionsForSpan(b.maxY - b.minY + 1, chunkSize);
        int nz = divisionsForSpan(b.maxZ - b.minZ + 1, chunkSize);
        if (nx <= 1 && ny <= 1 && nz <= 1) {
            return null;
        }
        return create(pending, nx, ny, nz);
    }

    /** First flat index {@code 0..sectionCount()-1} that contains at least one pending cell. */
    public static int firstOccupiedFlatSection(@Nonnull List<PendingBlock> pending, @Nonnull AssemblySectionMapper mapper) {
        int vol = mapper.sectionCount();
        for (int s = 0; s < vol; s++) {
            if (mapper.sectionHasAnyCell(pending, s)) {
                return s;
            }
        }
        return 0;
    }

    public int divisionsX() {
        return nx;
    }

    public int divisionsY() {
        return ny;
    }

    public int divisionsZ() {
        return nz;
    }

    public int sectionCount() {
        return nx * ny * nz;
    }

    public int flatSectionFor(@Nonnull PendingBlock pb) {
        return flatSectionFor(pb.x(), pb.y(), pb.z());
    }

    public int flatSectionFor(int x, int y, int z) {
        int sx = axisBin(x, minX, maxX, nx);
        int sy = axisBin(y, minY, maxY, ny);
        int sz = axisBin(z, minZ, maxZ, nz);
        return sx + sy * nx + sz * nx * ny;
    }

    public boolean isCellInSection(@Nonnull PendingBlock pb, int flatSection) {
        return flatSectionFor(pb) == flatSection;
    }

    public int flatSectionForWorldCell(@Nonnull Vector3i anchor, int wx, int wy, int wz) {
        return flatSectionFor(wx - anchor.x, wy - anchor.y, wz - anchor.z);
    }

    public boolean isWorldCellInSection(@Nonnull Vector3i anchor, int wx, int wy, int wz, int flatSection) {
        return flatSectionForWorldCell(anchor, wx, wy, wz) == flatSection;
    }

    /** First flat index that contains at least one obstructed world cell. */
    public static int firstOccupiedFlatSectionForWorldCells(
        @Nonnull AssemblySectionMapper mapper,
        @Nonnull Vector3i anchor,
        @Nonnull List<Vector3i> worldCells
    ) {
        int vol = mapper.sectionCount();
        for (int s = 0; s < vol; s++) {
            for (int i = 0; i < worldCells.size(); i++) {
                Vector3i c = worldCells.get(i);
                if (mapper.isWorldCellInSection(anchor, c.x, c.y, c.z, s)) {
                    return s;
                }
            }
        }
        return 0;
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

    private static int axisBin(int value, int lo, int hi, int divisionsOnAxis) {
        int n = Math.max(1, divisionsOnAxis);
        int span = Math.max(1, hi - lo + 1);
        int rel = value - lo;
        if (rel <= 0) {
            return 0;
        }
        int bin = (int) ((long) rel * n / span);
        return Math.min(n - 1, bin);
    }

    private static Bounds scanBounds(@Nonnull List<PendingBlock> pending) {
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
        return new Bounds(minX, maxX, minY, maxY, minZ, maxZ);
    }

    private record Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {}
}
