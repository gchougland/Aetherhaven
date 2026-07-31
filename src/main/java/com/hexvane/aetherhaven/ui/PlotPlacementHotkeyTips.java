package com.hexvane.aetherhaven.ui;

import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import javax.annotation.Nonnull;

/** Hotkey hint rows for plot placement page tips. */
public final class PlotPlacementHotkeyTips {
    private static final String MSG = "aetherhaven_plot_move.aetherhaven.ui.plotplacement";

    private PlotPlacementHotkeyTips() {}

    public static void appendTipsRows(@Nonnull UICommandBuilder builder, @Nonnull String containerSelector) {
        builder.clear(containerSelector);
        int i = 0;
        i = ToolHudHotkeyRows.appendHotkeyRow(builder, containerSelector, i, ToolKeybindSlot.ESCAPE, MSG + ".tipsClosePanel", null);
        i = ToolHudHotkeyRows.appendInfoRowIndex(builder, containerSelector, i, MSG + ".tipsCancelPreview");
        i = ToolHudHotkeyRows.appendHotkeyRow(builder, containerSelector, i, ToolKeybindSlot.SECONDARY, MSG + ".tipsReopenPanel", null);
        i = ToolHudHotkeyRows.appendHotkeyRow(builder, containerSelector, i, ToolKeybindSlot.SECONDARY, MSG + ".tipsBlockNoMove", null);
        ToolHudHotkeyRows.appendInfoRowIndex(builder, containerSelector, i, MSG + ".cameraHint");
    }

    public static void appendCharterTipsRows(@Nonnull UICommandBuilder builder, @Nonnull String containerSelector) {
        builder.clear(containerSelector);
        ToolHudHotkeyRows.appendInfoRow(builder, containerSelector, 0, "aetherhaven_plot_move.aetherhaven.ui.charterrelocation.tips");
        ToolHudHotkeyRows.appendInfoRow(builder, containerSelector, 1, MSG + ".cameraHint");
    }
}
