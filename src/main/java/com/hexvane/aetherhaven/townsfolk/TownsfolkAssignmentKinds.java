package com.hexvane.aetherhaven.townsfolk;

import javax.annotation.Nonnull;

/** Assignment roles for townsfolk checked out of the world pool. */
public final class TownsfolkAssignmentKinds {
    /** Stand and wander locally; placeholder until tourist/guard behaviors exist. */
    public static final String IDLE = "idle";
    public static final String TOURIST = "tourist";
    public static final String GUARD = "guard";

    /** Guild hall adventurer display pool (guard eligible townsfolk awaiting hire). */
    public static final String GUILD_ADVENTURER = "guild_adventurer";

    private TownsfolkAssignmentKinds() {}

    /** True when POI autonomy should not run (non hired guard adventurers and phase 1 kinds). */
    public static boolean usesIdleStandAround(@Nonnull String assignmentKind) {
        String k = assignmentKind.trim().toLowerCase();
        return IDLE.equals(k) || GUARD.equals(k) || GUILD_ADVENTURER.equals(k);
    }

    /** True when this townsfolk checkout is a cycling guild hall adventurer (not a hired guard). */
    public static boolean isGuildHallAdventurer(@Nonnull String assignmentKind) {
        return GUILD_ADVENTURER.equals(assignmentKind.trim().toLowerCase());
    }

    /** True when this townsfolk is a portal tourist (case-insensitive). */
    public static boolean isTourist(@Nonnull String assignmentKind) {
        return TOURIST.equalsIgnoreCase(assignmentKind.trim());
    }
}
