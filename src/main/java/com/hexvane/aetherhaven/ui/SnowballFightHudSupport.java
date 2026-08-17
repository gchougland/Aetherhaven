package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.festival.snowball.SnowballIds;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

public final class SnowballFightHudSupport {
    private SnowballFightHudSupport() {}

    @Nonnull
    public static SnowballFightHud obtainHud(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        CustomUIHud existing = player.getHudManager().getCustomHud(SnowballIds.HUD_KEY);
        if (existing instanceof SnowballFightHud h) {
            return h;
        }
        SnowballFightHud created = new SnowballFightHud(playerRef);
        player.getHudManager().addCustomHud(playerRef, created);
        return created;
    }

    public static boolean isActive(@Nonnull Player player) {
        return player.getHudManager().getCustomHud(SnowballIds.HUD_KEY) instanceof SnowballFightHud;
    }

    public static void removeHud(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        player.getHudManager().removeCustomHud(playerRef, SnowballIds.HUD_KEY);
    }
}
