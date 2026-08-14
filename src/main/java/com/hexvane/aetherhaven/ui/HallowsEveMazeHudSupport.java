package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.festival.hallowseve.HallowsEveIds;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

public final class HallowsEveMazeHudSupport {
    private HallowsEveMazeHudSupport() {}

    @Nonnull
    public static HallowsEveMazeHud obtainHud(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        CustomUIHud existing = player.getHudManager().getCustomHud(HallowsEveIds.HUD_KEY);
        if (existing instanceof HallowsEveMazeHud h) {
            return h;
        }
        HallowsEveMazeHud created = new HallowsEveMazeHud(playerRef);
        player.getHudManager().addCustomHud(playerRef, created);
        return created;
    }

    public static void removeHud(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        player.getHudManager().removeCustomHud(playerRef, HallowsEveIds.HUD_KEY);
    }
}
