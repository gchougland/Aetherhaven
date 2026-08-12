package com.hexvane.aetherhaven.festival.firework;

/** Ids and tuneables for carnival fireworks. */
public final class FireworkIds {
    public static final String ITEM_ID = "Aetherhaven_Festival_Firework";
    public static final String MODEL_ID = "Aetherhaven_Festival_Firework";

    public static final String PARTICLE_TRAIL = "Aetherhaven_Firework_Trail";
    public static final String PARTICLE_BURST_RED = "Aetherhaven_Firework_Burst_Red";
    public static final String PARTICLE_BURST_PURPLE = "Aetherhaven_Firework_Burst_Purple";
    public static final String PARTICLE_BURST_YELLOW = "Aetherhaven_Firework_Burst_Yellow";
    public static final String PARTICLE_BURST_BLUE = "Aetherhaven_Firework_Burst_Blue";
    public static final String PARTICLE_BURST_GREEN = "Aetherhaven_Firework_Burst_Green";

    public static final String[] BURST_PARTICLE_SYSTEMS = {
        PARTICLE_BURST_RED,
        PARTICLE_BURST_PURPLE,
        PARTICLE_BURST_YELLOW,
        PARTICLE_BURST_BLUE,
        PARTICLE_BURST_GREEN
    };

    /** Soft boom; falls back in effects if missing. */
    public static final String SOUND_EXPLODE = "SFX_Bomb_Fire_Goblin_Death";
    public static final String SOUND_EXPLODE_FALLBACK = "SFX_Mushroom_Harvest";

    public static final float RISE_SPEED = 8.0f;
    public static final float FUSE_MIN_SECONDS = 2.0f;
    public static final float FUSE_MAX_SECONDS = 4.0f;
    public static final float TRAIL_INTERVAL_SECONDS = 0.08f;
    /** World-space scale for the burst particle system. */
    public static final float BURST_SCALE = 1.35f;
    /** Clearance above the top face of the targeted block so the rocket does not start inside it. */
    public static final double SPAWN_ABOVE_TOP = 0.35;
    /** How far above the model origin the tip sits for block-hit checks. */
    public static final double ROCKET_TIP_HEIGHT = 0.9;
    /** Skip block hits briefly after launch so the placement block never pops it. */
    public static final float COLLISION_GRACE_SECONDS = 0.15f;
    /** Max blocks to climb when searching for open air above the target. */
    public static final int SPAWN_AIR_SEARCH_UP = 8;

    private FireworkIds() {}
}
