package com.hexvane.aetherhaven.ui;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Appends tool hint rows into a dynamic {@link com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud}.
 *
 * <p>A row is "action name, then what it does here". It never contains a key name: the game lists the player's own
 * key beside the same action name at the bottom right of the screen. See {@link ToolKeyPrompts}.
 *
 * <p>Callers that know which tool the row is for pass its {@link ToolKeyPrompts}, and the row shows that tool's own
 * action name. The older methods without one still work: their rows show the game's own name for the input.
 */
public final class ToolHudHotkeyRows {
    /** Row with an action name and a description. */
    public static final String ROW_UI = "Aetherhaven/ToolHudHotkeyRow.ui";

    /** Row for an action used while crouching: "Crouch +", the action name, and a description. */
    public static final String MODIFIER_ROW_UI = "Aetherhaven/ToolHudModifierHotkeyRow.ui";

    /** Row with one line of plain text. */
    public static final String INFO_ROW_UI = "Aetherhaven/ToolHudInfoRow.ui";

    private ToolHudHotkeyRows() {}

    /** Appends "action name of {@code slot} on this tool, then {@code description}". */
    public static void appendRow(
        @Nonnull UICommandBuilder builder,
        @Nonnull String containerSelector,
        int index,
        @Nonnull ToolKeyPrompts tool,
        @Nonnull ToolKeybindSlot slot,
        @Nonnull Message description
    ) {
        String base = containerSelector + "[" + index + "]";
        if (slot.isCrouchChord()) {
            builder.append(containerSelector, MODIFIER_ROW_UI);
            builder.set(base + " #ModifierLabel.TextSpans", ToolKeyPrompts.crouchModifierLabel());
        } else {
            builder.append(containerSelector, ROW_UI);
        }
        builder.set(base + " #KeyLabel.TextSpans", tool.label(slot));
        builder.set(base + " #DescLabel.TextSpans", description);
    }

    public static void appendRow(
        @Nonnull UICommandBuilder builder,
        @Nonnull String containerSelector,
        int index,
        @Nonnull ToolKeyPrompts tool,
        @Nonnull ToolKeybindSlot slot,
        @Nonnull String descriptionLangKey
    ) {
        appendRow(builder, containerSelector, index, tool, slot, Message.translation(descriptionLangKey));
    }

    /**
     * Row for a caller that does not say which tool it is for: labelled with the game's own name for the input.
     * The player reference is not needed, the label is the same for every player.
     */
    public static void appendRow(
        @Nonnull UICommandBuilder builder,
        @Nonnull String containerSelector,
        int index,
        @Nonnull ToolKeybindSlot slot,
        @Nonnull Message description,
        @Nullable PlayerRef playerRef
    ) {
        appendRow(builder, containerSelector, index, ToolKeyPrompts.GENERIC, slot, description);
    }

    public static void appendRow(
        @Nonnull UICommandBuilder builder,
        @Nonnull String containerSelector,
        int index,
        @Nonnull ToolKeybindSlot slot,
        @Nonnull String descriptionLangKey,
        @Nullable PlayerRef playerRef
    ) {
        appendRow(builder, containerSelector, index, ToolKeyPrompts.GENERIC, slot, Message.translation(descriptionLangKey));
    }

    public static void appendInfoRow(
        @Nonnull UICommandBuilder builder,
        @Nonnull String containerSelector,
        int index,
        @Nonnull Message description
    ) {
        String base = containerSelector + "[" + index + "]";
        builder.append(containerSelector, INFO_ROW_UI);
        builder.set(base + " #InfoLabel.TextSpans", description);
    }

    public static void appendInfoRow(
        @Nonnull UICommandBuilder builder,
        @Nonnull String containerSelector,
        int index,
        @Nonnull String descriptionLangKey
    ) {
        appendInfoRow(builder, containerSelector, index, Message.translation(descriptionLangKey));
    }

    /** Appends the line that tells the player where their keys are listed. Returns the next row index. */
    public static int appendWhereKeysRow(
        @Nonnull UICommandBuilder builder,
        @Nonnull String containerSelector,
        int index
    ) {
        appendInfoRow(builder, containerSelector, index, ToolKeyPrompts.WHERE_KEYS_KEY);
        return index + 1;
    }

    /** As {@link #appendRow(UICommandBuilder, String, int, ToolKeyPrompts, ToolKeybindSlot, String)}, returning the next index. */
    public static int appendHotkeyRow(
        @Nonnull UICommandBuilder builder,
        @Nonnull String containerSelector,
        int index,
        @Nonnull ToolKeyPrompts tool,
        @Nonnull ToolKeybindSlot slot,
        @Nonnull String descriptionLangKey
    ) {
        appendRow(builder, containerSelector, index, tool, slot, descriptionLangKey);
        return index + 1;
    }

    /** As {@link #appendRow(UICommandBuilder, String, int, ToolKeybindSlot, String, PlayerRef)}, returning the next index. */
    public static int appendHotkeyRow(
        @Nonnull UICommandBuilder builder,
        @Nonnull String containerSelector,
        int index,
        @Nonnull ToolKeybindSlot slot,
        @Nonnull String descriptionLangKey,
        @Nullable PlayerRef playerRef
    ) {
        appendRow(builder, containerSelector, index, ToolKeyPrompts.GENERIC, slot, descriptionLangKey);
        return index + 1;
    }

    public static int appendInfoRowIndex(
        @Nonnull UICommandBuilder builder,
        @Nonnull String containerSelector,
        int index,
        @Nonnull String descriptionLangKey
    ) {
        appendInfoRow(builder, containerSelector, index, descriptionLangKey);
        return index + 1;
    }
}
