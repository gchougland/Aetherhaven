package com.hexvane.aetherhaven.ui;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Appends tool HUD hint rows into a dynamic {@link com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud}. */
public final class ToolHudHotkeyRows {
    private static final String ROW_UI = "Aetherhaven/ToolHudHotkeyRow.ui";
    private static final String MODIFIER_ROW_UI = "Aetherhaven/ToolHudModifierHotkeyRow.ui";

    private ToolHudHotkeyRows() {}

    public static void appendRow(
        @Nonnull UICommandBuilder builder,
        @Nonnull String containerSelector,
        int index,
        @Nonnull ToolKeybindSlot slot,
        @Nonnull Message description,
        @Nullable PlayerRef playerRef
    ) {
        String base = containerSelector + "[" + index + "]";
        builder.append(containerSelector, ROW_UI);
        builder.set(base + " #KeyLabel.TextSpans", Message.raw(ToolKeybindDisplay.labelFor(playerRef, slot)));
        builder.set(base + " #DescLabel.TextSpans", description);
    }

    public static void appendRow(
        @Nonnull UICommandBuilder builder,
        @Nonnull String containerSelector,
        int index,
        @Nonnull ToolKeybindSlot slot,
        @Nonnull String descriptionLangKey,
        @Nullable PlayerRef playerRef
    ) {
        appendRow(builder, containerSelector, index, slot, Message.translation(descriptionLangKey), playerRef);
    }

    public static void appendModifierRow(
        @Nonnull UICommandBuilder builder,
        @Nonnull String containerSelector,
        int index,
        @Nonnull String modifierLabel,
        @Nonnull ToolKeybindSlot slot,
        @Nonnull Message description,
        @Nullable PlayerRef playerRef
    ) {
        String base = containerSelector + "[" + index + "]";
        builder.append(containerSelector, MODIFIER_ROW_UI);
        builder.set(base + " #ModifierLabel.TextSpans", Message.raw(modifierLabel + " +"));
        builder.set(base + " #KeyLabel.TextSpans", Message.raw(ToolKeybindDisplay.labelFor(playerRef, slot)));
        builder.set(base + " #DescLabel.TextSpans", description);
    }

    public static void appendModifierRow(
        @Nonnull UICommandBuilder builder,
        @Nonnull String containerSelector,
        int index,
        @Nonnull String modifierLabel,
        @Nonnull ToolKeybindSlot slot,
        @Nonnull String descriptionLangKey,
        @Nullable PlayerRef playerRef
    ) {
        appendModifierRow(
            builder,
            containerSelector,
            index,
            modifierLabel,
            slot,
            Message.translation(descriptionLangKey),
            playerRef
        );
    }

    public static void appendInfoRow(
        @Nonnull UICommandBuilder builder,
        @Nonnull String containerSelector,
        int index,
        @Nonnull Message description
    ) {
        String base = containerSelector + "[" + index + "]";
        builder.append(containerSelector, "Aetherhaven/ToolHudInfoRow.ui");
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

    public static int appendHotkeyRow(
        @Nonnull UICommandBuilder builder,
        @Nonnull String containerSelector,
        int index,
        @Nonnull ToolKeybindSlot slot,
        @Nonnull String descriptionLangKey,
        @Nullable PlayerRef playerRef
    ) {
        appendRow(builder, containerSelector, index, slot, descriptionLangKey, playerRef);
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
