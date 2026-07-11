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

    /**
     * Spoofs Creative on the client when needed for prefab preview.
     *
     * @return {@code true} when a {@link SetGameMode} packet was sent this call
     */
    public static boolean ensureClientCreativeForPreview(
        @Nonnull PlayerRef playerRef,
        @Nonnull GameMode serverGameMode,
        boolean alreadySpoofed
    ) {
        if (serverGameMode == GameMode.Creative || alreadySpoofed) {
            return false;
        }
        playerRef.getPacketHandler().writeNoCache(new SetGameMode(GameMode.Creative));
        return true;
    }

    /** @deprecated Prefer {@link #ensureClientCreativeForPreview(PlayerRef, GameMode, boolean)} with sticky state. */
    @Deprecated
    public static void ensureClientCreativeForPreview(@Nonnull PlayerRef playerRef, @Nonnull GameMode serverGameMode) {
        ensureClientCreativeForPreview(playerRef, serverGameMode, false);
    }

    /**
     * Restores the real server game mode on the client after preview spoofing.
     *
     * @return {@code true} when a restore packet was sent
     */
    public static boolean restoreClientGameMode(
        @Nonnull PlayerRef playerRef,
        @Nonnull GameMode serverGameMode,
        boolean wasSpoofed
    ) {
        if (serverGameMode == GameMode.Creative || !wasSpoofed) {
            return false;
        }
        playerRef.getPacketHandler().writeNoCache(new SetGameMode(serverGameMode));
        return true;
    }

    public static void restoreClientGameMode(@Nonnull PlayerRef playerRef, @Nonnull GameMode serverGameMode) {
        restoreClientGameMode(playerRef, serverGameMode, true);
    }
}
