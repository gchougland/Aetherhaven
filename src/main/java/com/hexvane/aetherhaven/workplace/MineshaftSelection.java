package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;

/** Converts an accessible entrance marker into the existing saved workplace geometry. */
public final class MineshaftSelection {
    public enum Direction { NORTH, EAST, SOUTH, WEST }

    public static final int MINIMUM_LENGTH = 3;

    private MineshaftSelection() {}

    public static int minimumLength() { return MINIMUM_LENGTH; }

    /** Number of excavated main-passage rows, excluding the three clear approach rows. */
    public static int maximumLength(ExcavationPlan.Layout layout) {
        if (layout == null) throw new IllegalArgumentException("Choose a tunnel layout.");
        return layout == ExcavationPlan.Layout.BRANCHES ? 16 : 32;
    }

    /** The existing plan names the edge entered from, rather than the direction of travel. */
    public static ExcavationPlan.Entrance entrance(Direction travel) {
        if (travel == null) throw new IllegalArgumentException("Choose a digging direction.");
        return switch (travel) {
            case NORTH -> ExcavationPlan.Entrance.SOUTH;
            case EAST -> ExcavationPlan.Entrance.WEST;
            case SOUTH -> ExcavationPlan.Entrance.NORTH;
            case WEST -> ExcavationPlan.Entrance.EAST;
        };
    }

    public static Direction direction(ExcavationPlan.Entrance entrance) {
        if (entrance == null) throw new IllegalArgumentException("Choose a tunnel entrance.");
        return switch (entrance) {
            case NORTH -> Direction.SOUTH;
            case EAST -> Direction.WEST;
            case SOUTH -> Direction.NORTH;
            case WEST -> Direction.EAST;
        };
    }

    /** Recovers the ground marker from an existing plan, including wider legacy workplaces. */
    public static Point ground(WorkplaceArea area, ExcavationPlan.Entrance entrance, ExcavationPlan.Layout layout) {
        direction(entrance);
        maximumLength(layout);
        if (area == null || !area.valid()) throw new IllegalArgumentException("Choose a valid workplace.");
        boolean alongZ = entrance == ExcavationPlan.Entrance.NORTH || entrance == ExcavationPlan.Entrance.SOUTH;
        long depth = alongZ ? (long) area.maxZ() - area.minZ() : (long) area.maxX() - area.minX();
        if (depth < 3) throw new IllegalArgumentException("This workplace has no clear mineshaft entrance.");
        long x = (long) area.minX() + ((long) area.maxX() - area.minX() - 1) / 2;
        long z = (long) area.minZ() + ((long) area.maxZ() - area.minZ() - 1) / 2;
        switch (entrance) {
            case NORTH -> z = (long) area.minZ() + 2;
            case EAST -> x = (long) area.maxX() - 3;
            case SOUTH -> z = (long) area.maxZ() - 3;
            case WEST -> x = (long) area.minX() + 2;
        }
        int groundY = area.floorY() - 1 + (layout == ExcavationPlan.Layout.DEEP_STAIRS_DOWN ? 7 : layout == ExcavationPlan.Layout.STAIRS_DOWN ? 2 : 0);
        return new Point(coordinate(x), groundY, coordinate(z));
    }

    /**
     * The selected ground is the last clear approach row. Digging begins one block ahead;
     * the other two clear rows extend behind it. Stairs down place the five-block work
     * band two blocks lower so the approach still starts on the selected ground.
     *
     * @throws IllegalArgumentException when the selection cannot fit the existing world band
     *     or integer-coordinate workplace representation
     */
    public static WorkplaceArea area(int x, int groundY, int z, Direction travel, int length,
            ExcavationPlan.Layout layout) {
        entrance(travel);
        int maximum = maximumLength(layout);
        boolean deep=layout==ExcavationPlan.Layout.DEEP_STAIRS_UP||layout==ExcavationPlan.Layout.DEEP_STAIRS_DOWN;
        if(layout==ExcavationPlan.Layout.ROOM&&length>=MINIMUM_LENGTH&&length<=maximum)length=9;
        if (length < (deep?9:MINIMUM_LENGTH) || length > maximum) {
            throw new IllegalArgumentException("Choose a tunnel length from " + MINIMUM_LENGTH + " to " + maximum + " blocks.");
        }
        long floor = (long) groundY + 1 - (layout == ExcavationPlan.Layout.DEEP_STAIRS_DOWN ? 7 : layout == ExcavationPlan.Layout.STAIRS_DOWN ? 2 : 0);
        if (groundY < 0 || groundY >= 320 || floor < 0 || floor > 320 - (deep?11:layout==ExcavationPlan.Layout.STAIRS_UP||layout==ExcavationPlan.Layout.STAIRS_DOWN?6:WorkplaceArea.HEIGHT)) {
            throw new IllegalArgumentException("This tunnel is too high or too low in the world.");
        }
        int halfWidth = layout == ExcavationPlan.Layout.BRANCHES || layout == ExcavationPlan.Layout.ROOM ? 4 : 2;
        long minX = (long) x - halfWidth, maxX = (long) x + halfWidth + 1;
        long minZ = (long) z - halfWidth, maxZ = (long) z + halfWidth + 1;
        switch (travel) {
            case NORTH -> { minZ = (long) z - length; maxZ = (long) z + 3; }
            case EAST -> { minX = (long) x - 2; maxX = (long) x + length + 1; }
            case SOUTH -> { minZ = (long) z - 2; maxZ = (long) z + length + 1; }
            case WEST -> { minX = (long) x - length; maxX = (long) x + 3; }
        }
        return new WorkplaceArea(coordinate(minX), (int) floor, coordinate(minZ),
            coordinate(maxX), coordinate(maxZ),deep?11:layout==ExcavationPlan.Layout.STAIRS_UP||layout==ExcavationPlan.Layout.STAIRS_DOWN?6:5);
    }

    private static int coordinate(long value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("This tunnel would extend beyond the world coordinates.");
        }
        return (int) value;
    }
}
