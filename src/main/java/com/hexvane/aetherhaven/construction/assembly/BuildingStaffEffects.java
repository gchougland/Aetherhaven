package com.hexvane.aetherhaven.construction.assembly;

import com.hexvane.aetherhaven.AetherhavenConstants;
import java.util.List;
import javax.annotation.Nonnull;

/**
 * The Building Staff effects that are spawned on their own at a place in the world, and the rule each must meet.
 *
 * <p>Nothing owns an effect spawned this way: the server sends it once and the client keeps it until its own data
 * ends it. A spawner that streams with no particle count and no life span never ends, so every use of the staff
 * would leave particles in the world for good. The conduit is not listed: it is attached to the held secondary
 * interaction, which the client clears when the interaction ends, the way the base game's bow charge is. The mint
 * stars at the tip were once spawned from here every 72 ms; they are part of the conduit now, so that they leave the
 * staff from the same point as the cone in first and third person.
 */
public final class BuildingStaffEffects {
    /** Every system the staff spawns free in the world. Each must end by itself. */
    @Nonnull
    public static final List<String> FREE_STANDING_SYSTEM_IDS = List.of(
        AetherhavenConstants.BUILDING_STAFF_GUIDE_TRAIL_PARTICLE_SYSTEM_ID,
        AetherhavenConstants.BUILDING_STAFF_MATERIAL_BEAD_PARTICLE_SYSTEM_ID,
        AetherhavenConstants.BUILDING_STAFF_STEP_PARTICLE_SYSTEM_ID,
        AetherhavenConstants.BUILDING_STAFF_PLACE_PUFF_PARTICLE_SYSTEM_ID
    );

    /** The system that streams while secondary is held, and the interaction and item node that hold it. */
    @Nonnull
    public static final String HELD_SYSTEM_ID = "Aetherhaven_Building_Staff_Conduit";
    @Nonnull
    public static final String HELD_INTERACTION_ID = "Aetherhaven_BuildingStaff_Secondary_Op";
    @Nonnull
    public static final String HELD_ANCHOR_NODE = "Handle";

    /** Longest one spawn of any of them may stay on screen. */
    public static final float MAX_SECONDS = 2.0F;

    /** What the client uses when a spawner leaves the value out. */
    public static final float DEFAULT_SPAWN_RATE = 1.0F;
    public static final float DEFAULT_PARTICLE_LIFE_SECONDS = 2.0F;
    public static final int MAX_PARTICLE_SLOTS = 512;
    private static final float UPDATES_PER_SECOND = 60.0F;

    private BuildingStaffEffects() {}

    /**
     * One spawner entry of a system, reduced to what decides when it stops.
     *
     * @param totalParticlesMax most particles it ever makes; below zero when the asset sets no total (no limit)
     * @param spawnRateMin slowest rate: particles per second for a stream, per update for a burst
     * @param maxConcurrent particles alive at once; outside 1..511 the client uses 512
     * @param spawnerLifeSpan seconds the spawner emits for; zero or less means no limit
     * @param groupLifeSpanMax seconds the system gives this spawner; zero or less means no limit
     */
    public record Spawner(
        @Nonnull String id,
        float startDelay,
        int totalParticlesMax,
        float spawnRateMin,
        boolean burst,
        int maxConcurrent,
        float spawnerLifeSpan,
        float groupLifeSpanMax,
        float particleLifeSpanMax
    ) {
        /** Seconds after the system starts at which this spawner makes its last particle; infinite when it never stops. */
        public float emissionEndSeconds(float systemLifeSpan) {
            float end = Float.POSITIVE_INFINITY;
            if (systemLifeSpan > 0.0F) {
                end = Math.min(end, systemLifeSpan);
            }
            if (spawnerLifeSpan > 0.0F) {
                end = Math.min(end, startDelay + spawnerLifeSpan);
            }
            if (groupLifeSpanMax > 0.0F) {
                end = Math.min(end, startDelay + groupLifeSpanMax);
            }
            if (totalParticlesMax == 0) {
                return Math.min(end, startDelay);
            }
            if (totalParticlesMax > 0 && spawnRateMin > 0.0F) {
                float emitting = burst
                    ? (float) Math.ceil(totalParticlesMax / spawnRateMin) / UPDATES_PER_SECOND
                    : totalParticlesMax / spawnRateMin;
                // With fewer slots than particles the rest wait for the first ones to die.
                int slots = maxConcurrent >= 1 && maxConcurrent < MAX_PARTICLE_SLOTS ? maxConcurrent : MAX_PARTICLE_SLOTS;
                int refills = (totalParticlesMax - 1) / slots;
                end = Math.min(end, startDelay + emitting + refills * particleLifeSpanMax);
            }
            return end;
        }
    }

    /**
     * Seconds until the last particle of one spawn is gone, or infinity when the effect never ends by itself.
     * Particles already alive finish their lives after emission stops.
     */
    public static float secondsUntilGone(float systemLifeSpan, @Nonnull List<Spawner> spawners) {
        float gone = 0.0F;
        for (Spawner spawner : spawners) {
            float end = spawner.emissionEndSeconds(systemLifeSpan);
            if (Float.isInfinite(end)) {
                return Float.POSITIVE_INFINITY;
            }
            gone = Math.max(gone, end + spawner.particleLifeSpanMax());
        }
        return gone;
    }

    /** True when one spawn ends by itself within {@link #MAX_SECONDS}. */
    public static boolean endsInTime(float systemLifeSpan, @Nonnull List<Spawner> spawners) {
        return !spawners.isEmpty() && secondsUntilGone(systemLifeSpan, spawners) <= MAX_SECONDS;
    }
}
