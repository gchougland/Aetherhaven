package com.hexvane.aetherhaven.rts.ui;

import com.hexvane.aetherhaven.ui.ToolHudHotkeyRows;
import com.hexvane.aetherhaven.ui.ToolKeybindSlot;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Hotkey hint rows for guard command HUD tool help and camera controls. */
public final class RtsHudHotkeyHints {
    private static final String P = "aetherhaven_rts.aetherhaven.rts";

    private RtsHudHotkeyHints() {}

    public static void appendToolHelpRows(
        @Nonnull UICommandBuilder builder,
        @Nonnull String containerSelector,
        @Nonnull String toolHelpKey,
        @Nullable PlayerRef playerRef
    ) {
        builder.clear(containerSelector);
        int i = 0;
        if (toolHelpKey.endsWith(".helpFlag")) {
            i = ToolHudHotkeyRows.appendHotkeyRow(builder, containerSelector, i, ToolKeybindSlot.SECONDARY, P + ".helpFlag.send", playerRef);
            i = ToolHudHotkeyRows.appendHotkeyRow(builder, containerSelector, i, ToolKeybindSlot.ABILITY1, P + ".helpFlag.orderMode", playerRef);
            ToolHudHotkeyRows.appendHotkeyRow(builder, containerSelector, i, ToolKeybindSlot.ABILITY2, P + ".helpFlag.stop", playerRef);
            return;
        }
        if (toolHelpKey.endsWith(".helpSword")) {
            ToolHudHotkeyRows.appendHotkeyRow(builder, containerSelector, i, ToolKeybindSlot.SECONDARY, P + ".helpSword.focus", playerRef);
            return;
        }
        if (toolHelpKey.endsWith(".helpStance")) {
            ToolHudHotkeyRows.appendHotkeyRow(builder, containerSelector, i, ToolKeybindSlot.ABILITY1, P + ".helpStance.cycle", playerRef);
            return;
        }
        if (toolHelpKey.endsWith(".helpFree")) {
            ToolHudHotkeyRows.appendHotkeyRow(builder, containerSelector, i, ToolKeybindSlot.SECONDARY, P + ".helpFree.release", playerRef);
            return;
        }
        String infoKey = switch (suffix(toolHelpKey)) {
            case ".helpSelectAll" -> P + ".helpSelectAll";
            case ".helpSelectType" -> P + ".helpSelectType";
            case ".helpExit" -> P + ".helpExit";
            default -> P + ".helpSelectAll";
        };
        ToolHudHotkeyRows.appendInfoRow(builder, containerSelector, i, infoKey);
    }

    public static void appendControlsRows(
        @Nonnull UICommandBuilder builder,
        @Nonnull String containerSelector,
        @Nullable PlayerRef playerRef
    ) {
        builder.clear(containerSelector);
        int i = 0;
        i = ToolHudHotkeyRows.appendHotkeyRow(builder, containerSelector, i, ToolKeybindSlot.MOVEMENT, P + ".hudControls.pan", playerRef);
        i = ToolHudHotkeyRows.appendHotkeyRow(builder, containerSelector, i, ToolKeybindSlot.SPACE, P + ".hudControls.flyUp", playerRef);
        i = ToolHudHotkeyRows.appendHotkeyRow(builder, containerSelector, i, ToolKeybindSlot.CTRL, P + ".hudControls.flyDown", playerRef);
        i = ToolHudHotkeyRows.appendInfoRowIndex(builder, containerSelector, i, P + ".hudControls.switchTools");
        i = ToolHudHotkeyRows.appendInfoRowIndex(builder, containerSelector, i, P + ".hudControls.selectClick");
        i = ToolHudHotkeyRows.appendHotkeyRow(builder, containerSelector, i, ToolKeybindSlot.SHIFT, P + ".hudControls.shiftClick", playerRef);
        i = ToolHudHotkeyRows.appendInfoRowIndex(builder, containerSelector, i, P + ".hudControls.boxSelect");
        ToolHudHotkeyRows.appendInfoRowIndex(builder, containerSelector, i, P + ".hudControls.exitTool");
    }

    @Nonnull
    private static String suffix(@Nonnull String toolHelpKey) {
        int dot = toolHelpKey.lastIndexOf('.');
        return dot >= 0 ? toolHelpKey.substring(dot) : toolHelpKey;
    }
}
