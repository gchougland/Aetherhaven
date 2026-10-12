package com.hexvane.aetherhaven.questboard;

/**
 * What the raid spawn search may know about the land. Every answer comes from terrain that is already in memory:
 * an implementation must never load a chunk or wake a sleeping one to answer.
 */
interface RaidSpawnTerrain {
    /** Returned by {@link #surface} when the column has no known surface. */
    int NO_SURFACE = Integer.MIN_VALUE;

    /** True when the column is in memory and usable. Anything else about a column is only asked when this is true. */
    boolean live(int x, int z);

    /** Highest opaque block of the column, or {@link #NO_SURFACE}. Standing above it is never underground. */
    int surface(int x, int z);

    /**
     * True when a creature placed in this cell stays in the world. The game puts anything added to a sleeping part
     * of the world straight back into storage, so a column being awake is not enough.
     */
    boolean liveCell(int x, int y, int z);

    /** A block that can be stood on and that blocks movement. */
    boolean solid(int x, int y, int z);

    /** Air, or something a creature can stand in such as grass. Water counts as open; ask {@link #fluid} as well. */
    boolean open(int x, int y, int z);

    boolean fluid(int x, int y, int z);

    /** Inside the footprint of a town building. Raiders never appear there. */
    boolean building(int x, int z);

    /** Inside land claimed by any town. */
    boolean claimed(int x, int z);
}
