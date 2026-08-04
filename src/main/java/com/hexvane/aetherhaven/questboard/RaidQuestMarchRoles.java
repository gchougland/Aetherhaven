package com.hexvane.aetherhaven.questboard;

import javax.annotation.Nonnull;

/** Maps quest-board raid roster roles to {@code Aetherhaven_Raid_<RoleId>} spawn variants. */
public final class RaidQuestMarchRoles {
    private static final String RAID_PREFIX = "Aetherhaven_Raid_";

    /** Smaug and other flying raid roster roles spawned above the ground approach ring. */
    public static final double FLY_RAID_SPAWN_LIFT = 12.0;

    private static final java.util.Set<String> FLYING_ROSTER_ROLES = java.util.Set.of("Dragon_Smaug");

    private RaidQuestMarchRoles() {}

    public static boolean isFlyingRosterRole(@Nonnull String rosterRoleId) {
        return FLYING_ROSTER_ROLES.contains(rosterRoleId);
    }

    @Nonnull
    public static String spawnRoleFor(@Nonnull String rosterRoleId) {
        return RAID_PREFIX + rosterRoleId;
    }

    public static boolean isRaidSpawnRole(@Nonnull String roleId) {
        return roleId.startsWith(RAID_PREFIX);
    }
}
