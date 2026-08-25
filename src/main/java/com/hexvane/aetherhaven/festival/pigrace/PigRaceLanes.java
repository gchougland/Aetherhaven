package com.hexvane.aetherhaven.festival.pigrace;

import java.util.List;
import javax.annotation.Nonnull;

/** Baked lane and payout data for the Pig Racing Festival (from chalk beam markers). */
public final class PigRaceLanes {
    public static final String MECHANIC_ID = "pig_race";
    public static final String FESTIVAL_ID = "pig_race";
    public static final String SPRING_TICKET_ITEM_ID = "Aetherhaven_Festival_Ticket_Spring";

    /** Prefab-local stand Y for pigs (on top of the track blocks). */
    public static final int STAND_Y = 6;

    public static final int[] BET_AMOUNTS = {10, 25, 50, 100};

    /** Base race speed in blocks per second before the per-pig multiplier. */
    public static final double BASE_SPEED_BLOCKS_PER_SEC = 0.875;

    /** Inclusive random multiplier range rolled once per pig per race (same for every lane). */
    public static final double SPEED_MULT_MIN = 0.9;
    public static final double SPEED_MULT_MAX = 1.1;

    /**
     * After Start the race is chosen, suspense music begins immediately and pigs wait this long before the whistle
     * and the run.
     */
    public static final long RACE_START_DELAY_MS = 4_000L;

    /** After every pig has reached the finish, keep the finish camera this long before resetting. */
    public static final long RESULTS_HOLD_MS = 2_000L;

    /** Switch to the finish top-down cam when a pig has this fraction of the track left. */
    public static final double FINISH_CAMERA_REMAINING_FRACTION = 0.15;

    private PigRaceLanes() {}

    public record Lane(
        int index,
        @Nonnull String npcRoleId,
        int startLocalX,
        int startLocalY,
        int startLocalZ,
        int finishLocalX,
        int finishLocalY,
        int finishLocalZ
    ) {}

    /** Built-in lane geometry used when a festival JSON does not define {@code raceLanes}. */
    @Nonnull
    public static List<Lane> defaultLanes() {
        return List.of(
            new Lane(0, "Aetherhaven_Festival_Pig_Race_Pink", -7, STAND_Y, -8, -7, STAND_Y, 8),
            new Lane(1, "Aetherhaven_Festival_Pig_Race_Boar", -3, STAND_Y, -8, -3, STAND_Y, 8),
            new Lane(2, "Aetherhaven_Festival_Pig_Race_Undead", 4, STAND_Y, -8, 4, STAND_Y, 8),
            new Lane(3, "Aetherhaven_Festival_Pig_Race_Wild", 8, STAND_Y, -8, 8, STAND_Y, 8)
        );
    }

    /** Fallback when no festival definition is available; prefer festival JSON {@code raceLanes} at runtime. */
    @Nonnull
    public static List<Lane> lanes() {
        return defaultLanes();
    }

    public static int ticketPayout(int betAmount) {
        return switch (betAmount) {
            case 10 -> 1;
            case 25 -> 3;
            case 50 -> 7;
            case 100 -> 15;
            default -> 0;
        };
    }

    public static boolean isAllowedBet(int amount) {
        for (int a : BET_AMOUNTS) {
            if (a == amount) {
                return true;
            }
        }
        return false;
    }

    @Nonnull
    public static String displayNameLangKey(int laneIndex) {
        return switch (laneIndex) {
            case 0 -> "aetherhaven_festivals.aetherhaven.festival.pig_race.pig.pink";
            case 1 -> "aetherhaven_festivals.aetherhaven.festival.pig_race.pig.boar";
            case 2 -> "aetherhaven_festivals.aetherhaven.festival.pig_race.pig.undead";
            case 3 -> "aetherhaven_festivals.aetherhaven.festival.pig_race.pig.fuzzy";
            default -> "aetherhaven_festivals.aetherhaven.festival.pig_race.pig.pink";
        };
    }

    /** Short pig name ("Pink Pig") for lists, where {@link #displayNameLangKey} reads as a sentence fragment. */
    @Nonnull
    public static String shortNameLangKey(int laneIndex) {
        return switch (laneIndex) {
            case 0 -> "aetherhaven_festivals.aetherhaven.festival.pig_race.npc.pink.name";
            case 1 -> "aetherhaven_festivals.aetherhaven.festival.pig_race.npc.boar.name";
            case 2 -> "aetherhaven_festivals.aetherhaven.festival.pig_race.npc.undead.name";
            case 3 -> "aetherhaven_festivals.aetherhaven.festival.pig_race.npc.wild.name";
            default -> "aetherhaven_festivals.aetherhaven.festival.pig_race.npc.pink.name";
        };
    }

    /** Yaw so the pig model faces along the race direction (Hytale forward is opposite atan2). */
    public static float facingYawRadians(double dx, double dz) {
        return (float) (Math.atan2(dx, dz) + Math.PI);
    }
}
