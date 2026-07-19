package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

public final class PlotCreatorHudSupport {
    private PlotCreatorHudSupport() {}

    /** Ensures status, progress, and checklist HUDs exist and refreshes all three. */
    public static void refreshAll(@Nonnull Player player, @Nonnull PlayerRef playerRef, @Nonnull PlotCreatorSession session) {
        obtainStatusHud(player, playerRef).refresh(session);
        obtainProgressHud(player, playerRef).refresh(session);
        obtainChecklistHud(player, playerRef).refresh(session);
    }

    @Nonnull
    public static PlotCreatorStatusHud obtainHud(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        return obtainStatusHud(player, playerRef);
    }

    @Nonnull
    public static PlotCreatorStatusHud obtainStatusHud(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        CustomUIHud existing = player.getHudManager().getCustomHud(AetherhavenConstants.PLOT_CREATOR_HUD_KEY);
        if (existing instanceof PlotCreatorStatusHud h) {
            return h;
        }
        PlotCreatorStatusHud created = new PlotCreatorStatusHud(playerRef);
        player.getHudManager().addCustomHud(playerRef, created);
        return created;
    }

    @Nonnull
    public static PlotCreatorProgressHud obtainProgressHud(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        CustomUIHud existing = player.getHudManager().getCustomHud(AetherhavenConstants.PLOT_CREATOR_PROGRESS_HUD_KEY);
        if (existing instanceof PlotCreatorProgressHud h) {
            return h;
        }
        PlotCreatorProgressHud created = new PlotCreatorProgressHud(playerRef);
        player.getHudManager().addCustomHud(playerRef, created);
        return created;
    }

    @Nonnull
    public static PlotCreatorChecklistHud obtainChecklistHud(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        CustomUIHud existing = player.getHudManager().getCustomHud(AetherhavenConstants.PLOT_CREATOR_CHECKLIST_HUD_KEY);
        if (existing instanceof PlotCreatorChecklistHud h) {
            return h;
        }
        PlotCreatorChecklistHud created = new PlotCreatorChecklistHud(playerRef);
        player.getHudManager().addCustomHud(playerRef, created);
        return created;
    }

    public static boolean isActive(@Nonnull Player player) {
        return player.getHudManager().getCustomHud(AetherhavenConstants.PLOT_CREATOR_HUD_KEY) instanceof PlotCreatorStatusHud;
    }

    public static void removeHud(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        player.getHudManager().removeCustomHud(playerRef, AetherhavenConstants.PLOT_CREATOR_HUD_KEY);
        player.getHudManager().removeCustomHud(playerRef, AetherhavenConstants.PLOT_CREATOR_PROGRESS_HUD_KEY);
        player.getHudManager().removeCustomHud(playerRef, AetherhavenConstants.PLOT_CREATOR_CHECKLIST_HUD_KEY);
    }
}
