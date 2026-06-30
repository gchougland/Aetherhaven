package com.hexvane.aetherhaven.plot;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.packets.player.SetGameMode;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

/**
 * The client routes {@code BuilderToolPrefabPreview} through {@code PrefabPreviewModule} inside
 * {@code BuilderToolsModule}, which only accepts preview data while the client believes it is in Creative.
 * Adventure players keep the real server game mode; we temporarily spoof Creative on the client only.
 */
public final class PlotCraftingPrefabPreviewClientMode {
    private PlotCraftingPrefabPreviewClientMode() {}

    public static void ensureClientCreativeForPreview(@Nonnull PlayerRef playerRef, @Nonnull GameMode serverGameMode) {
        if (serverGameMode != GameMode.Creative) {
            playerRef.getPacketHandler().writeNoCache(new SetGameMode(GameMode.Creative));
        }
    }

    public static void restoreClientGameMode(@Nonnull PlayerRef playerRef, @Nonnull GameMode serverGameMode) {
        if (serverGameMode != GameMode.Creative) {
            playerRef.getPacketHandler().writeNoCache(new SetGameMode(serverGameMode));
        }
    }
}
