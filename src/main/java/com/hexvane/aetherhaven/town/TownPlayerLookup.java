package com.hexvane.aetherhaven.town;

import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class TownPlayerLookup {
    private TownPlayerLookup() {}

    /**
     * UUID string or online username (this world first, then any online player).
     */
    @Nullable
    public static UUID resolvePlayerUuid(@Nonnull World world, @Nonnull String raw) {
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(trimmed);
        } catch (IllegalArgumentException ignored) {
            PlayerRef inWorld = findOnlinePlayerByUsername(world, trimmed);
            if (inWorld != null) {
                return inWorld.getUuid();
            }
            PlayerRef global = Universe.get().getPlayerByUsername(trimmed, NameMatching.EXACT_IGNORE_CASE);
            return global != null ? global.getUuid() : null;
        }
    }

    @Nullable
    public static PlayerRef findOnlinePlayerByUsername(@Nonnull World world, @Nonnull String username) {
        String want = username.trim();
        if (want.isEmpty()) {
            return null;
        }
        for (PlayerRef pr : world.getPlayerRefs()) {
            if (pr.getUsername().equalsIgnoreCase(want)) {
                return pr;
            }
        }
        return null;
    }

    @Nonnull
    public static String displayNameForUuid(@Nonnull World world, @Nonnull UUID id) {
        for (PlayerRef pr : world.getPlayerRefs()) {
            if (pr.getUuid().equals(id)) {
                return pr.getUsername();
            }
        }
        String s = id.toString();
        return s.length() > 12 ? s.substring(0, 8) + "…" : s;
    }
}
