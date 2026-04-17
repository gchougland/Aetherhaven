package com.hexvane.aetherhaven.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.entity.entities.Player;
import javax.annotation.Nonnull;

final class TownPermissionUtil {
    private TownPermissionUtil() {}

    /** Creative mode or explicit admin permission — can target towns by name without being owner. */
    static boolean canAdministerForeignTowns(@Nonnull Player player) {
        return player.getGameMode() == GameMode.Creative
            || player.hasPermission(com.hexvane.aetherhaven.AetherhavenConstants.PERMISSION_TOWN_ADMIN, false);
    }
}
