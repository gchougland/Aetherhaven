package com.hexvane.aetherhaven.rts.ui;

import com.hexvane.aetherhaven.ui.ToolHudHotkeyRows;
import com.hexvane.aetherhaven.ui.ToolKeyPrompts;
import com.hexvane.aetherhaven.ui.ToolKeybindSlot;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Hint rows for the guard command HUD: what the held command tool does, and how to move the view.
 *
 * <p>Rows name actions, never keys. The game lists the player's own keys for the held tool at the bottom right of the
 * screen, and the view is moved with the game's own movement, fly and sprint inputs, which the player may have bound
 * to anything.
 */
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
        switch (suffix(toolHelpKey)) {
            case ".helpFlag" -> {
                i = ToolHudHotkeyRows.appendHotkeyRow(
                    builder, containerSelector, i, ToolKeyPrompts.RTS_FLAG, ToolKeybindSlot.SECONDARY, P + ".helpFlag.send");
                i = ToolHudHotkeyRows.appendHotkeyRow(
                    builder, containerSelector, i, ToolKeyPrompts.RTS_FLAG, ToolKeybindSlot.ABILITY1, P + ".helpFlag.orderMode");
                ToolHudHotkeyRows.appendHotkeyRow(
                    builder, containerSelector, i, ToolKeyPrompts.RTS_FLAG, ToolKeybindSlot.ABILITY2, P + ".helpFlag.stop");
            }
            case ".helpSword" -> ToolHudHotkeyRows.appendHotkeyRow(
                builder, containerSelector, i, ToolKeyPrompts.RTS_SWORD, ToolKeybindSlot.SECONDARY, P + ".helpSword.focus");
            case ".helpStance" -> ToolHudHotkeyRows.appendHotkeyRow(
                builder, containerSelector, i, ToolKeyPrompts.RTS_STANCE, ToolKeybindSlot.ABILITY1, P + ".helpStance.cycle");
            case ".helpFree" -> ToolHudHotkeyRows.appendHotkeyRow(
                builder, containerSelector, i, ToolKeyPrompts.RTS_FREE, ToolKeybindSlot.SECONDARY, P + ".helpFree.release");
            case ".helpSelectType" -> ToolHudHotkeyRows.appendHotkeyRow(
                builder, containerSelector, i, ToolKeyPrompts.RTS_SELECT, ToolKeybindSlot.PRIMARY, P + ".helpSelectType");
            case ".helpExit" -> ToolHudHotkeyRows.appendHotkeyRow(
                builder, containerSelector, i, ToolKeyPrompts.RTS_EXIT, ToolKeybindSlot.PRIMARY, P + ".helpExit");
            default -> ToolHudHotkeyRows.appendHotkeyRow(
                builder, containerSelector, i, ToolKeyPrompts.RTS_SELECT, ToolKeybindSlot.PRIMARY, P + ".helpSelectAll");
        }
    }

    /**
     * The Command Flag carried in hand, away from the command post. Every input the flag binds has a row, so the
     * panel says what each action will do before it is used. Rows carry the flag's action names. The game lists the
     * player's own keys for them at the bottom right of the screen.
     */
    public static void appendFieldFlagRows(
        @Nonnull UICommandBuilder builder,
        @Nonnull String containerSelector,
        @Nullable PlayerRef playerRef
    ) {
        builder.clear(containerSelector);
        int i = 0;
        i = ToolHudHotkeyRows.appendHotkeyRow(
            builder, containerSelector, i, ToolKeyPrompts.RTS_FLAG, ToolKeybindSlot.SECONDARY, P + ".helpFlag.send");
        i = ToolHudHotkeyRows.appendHotkeyRow(
            builder, containerSelector, i, ToolKeyPrompts.RTS_FLAG, ToolKeybindSlot.ABILITY1, P + ".helpFlag.orderMode");
        i = ToolHudHotkeyRows.appendHotkeyRow(
            builder, containerSelector, i, ToolKeyPrompts.RTS_FLAG, ToolKeybindSlot.ABILITY2, P + ".helpFlag.fieldStop");
        i = ToolHudHotkeyRows.appendHotkeyRow(
            builder, containerSelector, i, ToolKeyPrompts.RTS_FLAG, ToolKeybindSlot.ABILITY3, P + ".helpFlag.fieldRelease");
        ToolHudHotkeyRows.appendHotkeyRow(
            builder, containerSelector, i, ToolKeyPrompts.RTS_FLAG, ToolKeybindSlot.PRIMARY, P + ".helpFlag.fieldReady");
    }

    public static void appendControlsRows(
        @Nonnull UICommandBuilder builder,
        @Nonnull String containerSelector,
        @Nullable PlayerRef playerRef
    ) {
        builder.clear(containerSelector);
        int i = 0;
        i = ToolHudHotkeyRows.appendInfoRowIndex(builder, containerSelector, i, P + ".hudControls.pan");
        i = ToolHudHotkeyRows.appendInfoRowIndex(builder, containerSelector, i, P + ".hudControls.zoom");
        i = ToolHudHotkeyRows.appendInfoRowIndex(builder, containerSelector, i, P + ".hudControls.switchTools");
        i = ToolHudHotkeyRows.appendInfoRowIndex(builder, containerSelector, i, P + ".hudControls.selectClick");
        i = ToolHudHotkeyRows.appendInfoRowIndex(builder, containerSelector, i, P + ".hudControls.addToSelection");
        i = ToolHudHotkeyRows.appendInfoRowIndex(builder, containerSelector, i, P + ".hudControls.boxSelect");
        i = ToolHudHotkeyRows.appendInfoRowIndex(builder, containerSelector, i, P + ".hudControls.exitTool");
        ToolHudHotkeyRows.appendWhereKeysRow(builder, containerSelector, i);
    }

    @Nonnull
    private static String suffix(@Nonnull String toolHelpKey) {
        int dot = toolHelpKey.lastIndexOf('.');
        return dot >= 0 ? toolHelpKey.substring(dot) : toolHelpKey;
    }
}
