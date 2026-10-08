package com.hexvane.aetherhaven.workplace;

/** Block aligned, half open footprint. The first ground click determines a five block work band. */
public record WorkplaceArea(int minX, int floorY, int minZ, int maxX, int maxZ, int height) {
    public WorkplaceArea(int minX,int floorY,int minZ,int maxX,int maxZ){this(minX,floorY,minZ,maxX,maxZ,HEIGHT);}
    @Override public int height(){return height==0?HEIGHT:height;}
    public WorkplaceArea {if(height==0)height=5;}
    public static final int HEIGHT = 5;
    public static final int MAX_SIDE = 64;

    public static WorkplaceArea corners(int x1, int groundY, int z1, int x2, int z2) {
        return new WorkplaceArea(Math.min(x1, x2), Math.addExact(groundY, 1), Math.min(z1, z2),
            Math.addExact(Math.max(x1, x2), 1), Math.addExact(Math.max(z1, z2), 1));
    }

    public boolean valid() {
        long width = (long) maxX - minX, depth = (long) maxZ - minZ;
        return width >= 2 && depth >= 2 && width <= MAX_SIDE && depth <= MAX_SIDE
            && height()>=3 && height()<=16 && floorY >= 0 && floorY <= 320-height();
    }

    public boolean overlaps(WorkplaceArea other) {
        return minX < other.maxX && maxX > other.minX && minZ < other.maxZ && maxZ > other.minZ
            && (long) floorY < (long) other.floorY + other.height() && (long) floorY + height() > other.floorY;
    }

    public boolean contains(double x, double y, double z) {
        return x >= minX && x < maxX && z >= minZ && z < maxZ && y >= floorY && y < floorY + height();
    }
}
