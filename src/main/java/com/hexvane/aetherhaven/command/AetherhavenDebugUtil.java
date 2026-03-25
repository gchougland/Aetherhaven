package com.hexvane.aetherhaven.command;

import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

final class AetherhavenDebugUtil {
    private AetherhavenDebugUtil() {}

    static boolean requireDebug(@Nullable AetherhavenPlugin plugin, @Nonnull PlayerRef playerRef) {
        if (plugin == null || !plugin.getConfig().get().isDebugCommandsEnabled()) {
            playerRef.sendMessage(Message.raw("Aetherhaven debug commands are disabled (set DebugCommandsEnabled in config.json)."));
            return false;
        }
        return true;
    }
}
