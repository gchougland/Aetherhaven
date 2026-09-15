package com.hexvane.aetherhaven.autonomy;

import java.util.UUID;

/** Pure balance and identity rules, shared by runtime and regression tests. */
public final class VillagerLifePolicy {
    public static final double SEARCH_RADIUS = 18;
    public static final double TALK_RADIUS = 2.8;
    public static final long APPROACH_TIMEOUT_MS = 15_000;
    public static final long CONVERSATION_MS = 24_000;
    public static final long COOLDOWN_MS = 45_000;
    public static final float FUN_PER_SECOND = 3;
    static final String[] VOICES = {"BrightFemale", "BrightMale", "WarmFemale", "WarmMale",
        "MellowFemale", "MellowMale", "GravelyMale", "GravelyFemale"};
    private static final String[] TOPICS = {"Food", "Work", "Home", "Music", "Flower", "Rain"};

    private VillagerLifePolicy() {}

    public static String ambientMood(boolean working, boolean useWorkVoice) {
        return working && useWorkVoice ? "Work" : "Idle";
    }

    public static String voice(UUID id) { return VOICES[Math.floorMod(id.hashCode(), VOICES.length)]; }
    public static String topic(UUID id, long now) { return TOPICS[Math.floorMod(id.hashCode() + (int)(now / 30_000), TOPICS.length)]; }

    public static boolean canSocialize(float hunger, float energy) {
        return hunger >= 50 && energy >= 30;
    }

    public static boolean wantsCompany(float hunger, float energy, float fun) {
        return canSocialize(hunger, energy) && fun < 40;
    }

    public static boolean chooseConversation(double randomRoll, boolean recreation) {
        return randomRoll >= 0 && randomRoll < (recreation ? .65 : .4);
    }

    public static boolean inTalkingRange(double distanceSquared, double heightDifference) {
        return distanceSquared >= .16 && distanceSquared <= TALK_RADIUS * TALK_RADIUS && Math.abs(heightDifference) < 1.25;
    }

    public static float refill(float fun, double seconds) {
        // Never award offline progress or a stall-sized lump of fun.
        if (!Double.isFinite(seconds) || seconds <= 0) return fun;
        return Math.min(100, fun + (float)Math.min(.5, seconds) * FUN_PER_SECOND);
    }

    public static String needEmote(float hunger, float energy, float fun) {
        if (hunger < 50 && hunger <= energy && hunger <= fun) return "Hungry";
        if (energy < 40 && energy <= fun) return "Sleepy";
        if (fun < 40) return "Bored";
        if (hunger < 50) return "Hungry";
        return null;
    }
}
