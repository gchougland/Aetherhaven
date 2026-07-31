package com.hexvane.aetherhaven.shopspot;

import com.hexvane.aetherhaven.ui.ToolHudHotkeyRows;
import com.hexvane.aetherhaven.ui.ToolKeybindSlot;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Hotkey hint rows for shop spot look-at HUD. */
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
        ToolHudHotkeyRows.appendHotkeyRow(builder, containerSelector, 0, ToolKeybindSlot.USE, descKey, playerRef);
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
