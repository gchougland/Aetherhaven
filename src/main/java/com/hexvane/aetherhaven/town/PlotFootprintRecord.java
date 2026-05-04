package com.hexvane.aetherhaven.town;

import com.google.gson.annotations.SerializedName;

/** Axis-aligned footprint for overlap checks (inclusive integer bounds). */
public final class PlotFootprintRecord {
    @SerializedName("minX")
    private int minX;

    @SerializedName("minY")
    private int minY;

    @SerializedName("minZ")
    private int minZ;

    @SerializedName("maxX")
    private int maxX;

    @SerializedName("maxY")
    private int maxY;

    @SerializedName("maxZ")
    private int maxZ;

    public PlotFootprintRecord() {}

    public PlotFootprintRecord(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public boolean intersects(PlotFootprintRecord o) {
        return minX <= o.maxX && maxX >= o.minX && minY <= o.maxY && maxY >= o.minY && minZ <= o.maxZ && maxZ >= o.minZ;
    }

    /** True if this inclusive axis-aligned box contains the given block cell. */
    public boolean containsBlock(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }
}
