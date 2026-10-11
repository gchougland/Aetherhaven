package com.hexvane.aetherhaven.production;

/** The player-placed stone a lumbermill grows one tree on. Its bindings live in {@link TimberProductionState}. */
public final class Rootstone {
    private Rootstone() {}
    public static final String BLOCK = "Aetherhaven_Rootstone";
    public static final String PAGE = "AetherhavenRootstone";
    public static final String SONG_PARTICLE = "Aetherhaven_Grove_Song";
    public static final String SONG_SOUND = "Aetherhaven_Grove_Song";

    public static boolean is(String blockId) { return BLOCK.equals(blockId); }
}
