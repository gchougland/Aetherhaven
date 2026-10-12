package com.hexvane.aetherhaven.ui;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import javax.annotation.Nonnull;

/**
 * Tip rows for the plot placement page.
 *
 * <p>Tips name actions, never keys: a custom page cannot show the player's own key for an input.
 */
public final class PlotPlacementHotkeyTips {
    private static final String MSG = "aetherhaven_plot_move.aetherhaven.ui.plotplacement";

    private PlotPlacementHotkeyTips() {}

    public static void appendTipsRows(@Nonnull UICommandBuilder builder, @Nonnull String containerSelector) {
        builder.clear(containerSelector);
        int i = 0;
        i = ToolHudHotkeyRows.appendInfoRowIndex(builder, containerSelector, i, MSG + ".tipsClosePanel");
        i = ToolHudHotkeyRows.appendInfoRowIndex(builder, containerSelector, i, MSG + ".tipsCancelPreview");
        i = ToolHudHotkeyRows.appendInfoRowIndex(builder, containerSelector, i, MSG + ".tipsReopenPanel");
        i = ToolHudHotkeyRows.appendInfoRowIndex(builder, containerSelector, i, MSG + ".tipsBlockNoMove");
        ToolHudHotkeyRows.appendInfoRowIndex(builder, containerSelector, i, MSG + ".cameraHint");
    }

    public static void appendCharterTipsRows(@Nonnull UICommandBuilder builder, @Nonnull String containerSelector) {
        builder.clear(containerSelector);
        ToolHudHotkeyRows.appendInfoRow(builder, containerSelector, 0, "aetherhaven_plot_move.aetherhaven.ui.charterrelocation.tips");
        ToolHudHotkeyRows.appendInfoRow(builder, containerSelector, 1, MSG + ".cameraHint");
    }
}
