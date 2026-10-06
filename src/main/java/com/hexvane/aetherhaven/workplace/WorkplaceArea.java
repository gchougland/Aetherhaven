package com.hexvane.aetherhaven.workplace;

/** Block aligned, half open footprint. The first ground click determines a five block work band. */
public record WorkplaceArea(int minX, int floorY, int minZ, int maxX, int maxZ) {
    public static final int HEIGHT = 5;
    public static final int MAX_SIDE = 64;

    public static WorkplaceArea corners(int x1, int groundY, int z1, int x2, int z2) {
        return new WorkplaceArea(Math.min(x1, x2), Math.addExact(groundY, 1), Math.min(z1, z2),
            Math.addExact(Math.max(x1, x2), 1), Math.addExact(Math.max(z1, z2), 1));
    }

    public boolean valid() {
        long width = (long) maxX - minX, depth = (long) maxZ - minZ;
        return width >= 2 && depth >= 2 && width <= MAX_SIDE && depth <= MAX_SIDE
            && floorY >= 0 && floorY <= 315;
    }

    public boolean overlaps(WorkplaceArea other) {
        return minX < other.maxX && maxX > other.minX && minZ < other.maxZ && maxZ > other.minZ
            && (long) floorY < (long) other.floorY + HEIGHT && (long) floorY + HEIGHT > other.floorY;
    }

    public boolean contains(double x, double y, double z) {
        return x >= minX && x < maxX && z >= minZ && z < maxZ && y >= floorY && y < floorY + HEIGHT;
    }
}
