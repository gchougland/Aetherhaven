package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.ui.ToolHudHotkeyRows;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Hint rows for the shop spot look-at HUD.
 *
 * <p>The hints say what using the stall does. They name the action, not a key: a custom HUD cannot show the player's
 * own key for the game's use input.
 */
public final class ShopSpotHudHotkeyHints {
    private static final String MSG = "aetherhaven_shop.aetherhaven.shop.hud";

    private ShopSpotHudHotkeyHints() {}

    public static void appendHintRows(
        @Nonnull UICommandBuilder builder,
        @Nonnull String containerSelector,
        @Nullable String hintTranslationKey,
        @Nullable PlayerRef playerRef
    ) {
        builder.clear(containerSelector);
        if (hintTranslationKey == null) {
            return;
        }
        if (hintTranslationKey.endsWith(".hintPlayerEmpty")) {
            ToolHudHotkeyRows.appendInfoRow(builder, containerSelector, 0, hintTranslationKey);
            return;
        }
        String descKey =
            switch (suffix(hintTranslationKey)) {
                case ".hintNpc" -> MSG + ".hintNpc.desc";
                case ".hintPlayerBuy" -> MSG + ".hintPlayerBuy.desc";
                case ".hintPlayerList" -> MSG + ".hintPlayerList.desc";
                case ".hintPlayerOwn" -> MSG + ".hintPlayerOwn.desc";
                default -> MSG + ".hintPlayerBuy.desc";
            };
        ToolHudHotkeyRows.appendInfoRow(builder, containerSelector, 0, descKey);
        if (hintTranslationKey.endsWith(".hintNpc")) {
            ToolHudHotkeyRows.appendInfoRow(builder, containerSelector, 1, MSG + ".hintNpc.restock");
        }
    }

    @Nonnull
    private static String suffix(@Nonnull String hintTranslationKey) {
        int dot = hintTranslationKey.lastIndexOf('.');
        return dot >= 0 ? hintTranslationKey.substring(dot) : hintTranslationKey;
    }
}
