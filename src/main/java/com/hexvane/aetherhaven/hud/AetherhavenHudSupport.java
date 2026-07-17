package com.hexvane.aetherhaven.hud;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

/** Small lifecycle adapter suitable for both {@code PlayerReadyEvent} and player tick systems. */
public final class AetherhavenHudSupport {
    private AetherhavenHudSupport() {}

    @Nonnull
    public static AetherhavenHud obtain(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        CustomUIHud existing = player.getHudManager().getCustomHud(AetherhavenHud.HUD_KEY);
        if (existing instanceof AetherhavenHud hud) {
            return hud;
        }
        AetherhavenHud created = new AetherhavenHud(playerRef);
        player.getHudManager().addCustomHud(playerRef, created);
        return created;
    }

    @Nonnull
    public static AetherhavenHud reconcile(
        @Nonnull Player player,
        @Nonnull PlayerRef playerRef,
        @Nonnull AetherhavenHudSnapshot snapshot
    ) {
        AetherhavenHud hud = obtain(player, playerRef);
        hud.refresh(snapshot);
        return hud;
    }

    public static void remove(@Nonnull Player player, @Nonnull PlayerRef playerRef) {
        player.getHudManager().removeCustomHud(playerRef, AetherhavenHud.HUD_KEY);
    }

    public static boolean isActive(@Nonnull Player player) {
        return player.getHudManager().getCustomHud(AetherhavenHud.HUD_KEY) instanceof AetherhavenHud;
    }
}
