package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.festival.treeclimb.TreeClimbIds;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

public final class TreeClimbRaceHudSupport {
    private TreeClimbRaceHudSupport() {}

    @Nonnull
    public static TreeClimbRaceHud obtainHud(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        CustomUIHud existing = player.getHudManager().getCustomHud(TreeClimbIds.HUD_KEY);
        if (existing instanceof TreeClimbRaceHud h) {
            return h;
        }
        TreeClimbRaceHud created = new TreeClimbRaceHud(playerRef);
        player.getHudManager().addCustomHud(playerRef, created);
        return created;
    }

    public static boolean isActive(@Nonnull Player player) {
        return player.getHudManager().getCustomHud(TreeClimbIds.HUD_KEY) instanceof TreeClimbRaceHud;
    }

    public static void removeHud(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        player.getHudManager().removeCustomHud(playerRef, TreeClimbIds.HUD_KEY);
    }
}
