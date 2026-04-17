package com.hexvane.aetherhaven.town;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class TownPlayerLookup {
    private TownPlayerLookup() {}

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
