package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.List;
import javax.annotation.Nonnull;

public final class FestivalActivityRosterHudSupport {
    private FestivalActivityRosterHudSupport() {}

    @Nonnull
    public static FestivalActivityRosterHud obtainHud(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        CustomUIHud existing =
            player.getHudManager().getCustomHud(AetherhavenConstants.FESTIVAL_ACTIVITY_ROSTER_HUD_KEY);
        if (existing instanceof FestivalActivityRosterHud h) {
            return h;
        }
        FestivalActivityRosterHud created = new FestivalActivityRosterHud(playerRef);
        player.getHudManager().addCustomHud(playerRef, created);
        return created;
    }

    public static boolean isActive(@Nonnull Player player) {
        return player.getHudManager().getCustomHud(AetherhavenConstants.FESTIVAL_ACTIVITY_ROSTER_HUD_KEY)
            instanceof FestivalActivityRosterHud;
    }

    public static void removeHud(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        player.getHudManager().removeCustomHud(playerRef, AetherhavenConstants.FESTIVAL_ACTIVITY_ROSTER_HUD_KEY);
    }

    /** Convenience for tick systems: build or reuse the roster and push the current participant list. */
    public static void show(
        @Nonnull Player player,
        @Nonnull PlayerRef playerRef,
        @Nonnull Message title,
        @Nonnull List<FestivalActivityRosterHud.Row> rows,
        @Nonnull Message emptyText
    ) {
        obtainHud(player, playerRef).refresh(title, rows, emptyText);
    }

    /** Removes the roster only when this player actually has one, so bystanders are never touched. */
    public static void hide(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        if (isActive(player)) {
            removeHud(player, playerRef);
        }
    }
}
