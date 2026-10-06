package com.hexvane.aetherhaven.patrol;

/** Escort aggression is independent of movement orders and never permits friendly targets. */
public enum GuardEscortStance {
    DEFENSIVE,
    OFFENSIVE;

    public static GuardEscortStance fromSaved(String value) {
        return "DEFENSIVE".equalsIgnoreCase(value) ? DEFENSIVE : OFFENSIVE;
    }

    public boolean permits(boolean waiting, boolean safeTarget, boolean attackedProtectedPlayer, boolean hostileToPlayer) {
        return !waiting && safeTarget
            && (attackedProtectedPlayer || (this == OFFENSIVE && hostileToPlayer));
    }
}
