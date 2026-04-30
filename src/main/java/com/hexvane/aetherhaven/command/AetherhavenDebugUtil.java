package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class AetherhavenDebugUtil {
    private AetherhavenDebugUtil() {}

    /** Debug subcommands are always registered; access is enforced with normal command permissions. */
    static boolean requireDebug(@Nullable AetherhavenPlugin plugin, @SuppressWarnings("unused") @Nonnull PlayerRef playerRef) {
        return plugin != null;
    }
}
