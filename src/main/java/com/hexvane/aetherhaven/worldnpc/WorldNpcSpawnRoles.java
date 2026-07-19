package com.hexvane.aetherhaven.worldnpc;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Maps logical villager role ids (picker / placement) to stand-still {@code Aetherhaven_World_*} spawn roles.
 * Placement records keep the logical id so dialogue and portraits resolve against town villager defs.
 */
public final class WorldNpcSpawnRoles {
    public static final String TOWN_PREFIX = "Aetherhaven_";
    public static final String WORLD_PREFIX = "Aetherhaven_World_";

    private WorldNpcSpawnRoles() {}

    /**
     * Role id passed to {@code spawnNPC} for a world placement. Falls back to the logical id when no
     * dedicated world idle role exists (custom / third-party roles).
     */
    @Nonnull
    public static String toSpawnRoleId(@Nullable String logicalOrSpawnRoleId) {
        String id = logicalOrSpawnRoleId != null ? logicalOrSpawnRoleId.trim() : "";
        if (id.isEmpty()) {
            return "";
        }
        if (id.startsWith(WORLD_PREFIX)) {
            return id;
        }
        if (id.startsWith(TOWN_PREFIX)) {
            return WORLD_PREFIX + id.substring(TOWN_PREFIX.length());
        }
        return id;
    }

    /** Logical role id for catalogs / portraits (strips {@code Aetherhaven_World_} if present). */
    @Nonnull
    public static String toLogicalRoleId(@Nullable String roleId) {
        String id = roleId != null ? roleId.trim() : "";
        if (id.isEmpty()) {
            return "";
        }
        if (id.startsWith(WORLD_PREFIX)) {
            return TOWN_PREFIX + id.substring(WORLD_PREFIX.length());
        }
        return id;
    }

    /** True when {@code liveRoleName} matches the expected spawn role for this placement logical id. */
    public static boolean matchesSpawnedRole(@Nullable String logicalRoleId, @Nullable String liveRoleName) {
        String live = liveRoleName != null ? liveRoleName.trim() : "";
        if (live.isEmpty()) {
            return false;
        }
        String logical = toLogicalRoleId(logicalRoleId);
        String expected = toSpawnRoleId(logical);
        if (expected.isEmpty()) {
            return false;
        }
        if (live.equals(expected)) {
            return true;
        }
        // Custom / third-party roles have no World_ twin and spawn as the logical id.
        return expected.equals(logical) && live.equals(logical);
    }
}
