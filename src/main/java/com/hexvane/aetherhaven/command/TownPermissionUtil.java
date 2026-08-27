package com.hexvane.aetherhaven.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

public final class TownPermissionUtil {
    private TownPermissionUtil() {}

    /** Creative mode or explicit admin permission — can target towns by name without being owner. */
    public static boolean canAdministerForeignTowns(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        return player.getGameMode() == GameMode.Creative
            || playerRef.hasPermission(com.hexvane.aetherhaven.AetherhavenConstants.PERMISSION_TOWN_ADMIN, false);
    }
}
