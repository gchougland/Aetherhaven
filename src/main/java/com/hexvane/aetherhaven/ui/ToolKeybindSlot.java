package com.hexvane.aetherhaven.ui;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Vanilla {@code InputBindingKey} ids and default key labels for dynamically appended tool HUD rows. */
public enum ToolKeybindSlot {
    PRIMARY("PrimaryItemAction", "LMB"),
    SECONDARY("SecondaryItemAction", "RMB"),
    USE("BlockInteractAction", "F"),
    ABILITY1("Ability1ItemAction", "Q"),
    ABILITY2("Ability2ItemAction", "E"),
    ABILITY3("Ability3ItemAction", "R"),
    ESCAPE("UiCancel", "Esc"),
    SHIFT("Sprint", "Shift"),
    CTRL("FlyDown", "Ctrl"),
    SPACE("Jump", "Space"),
    MOVEMENT("MoveForwards", "WASD");

    private final String inputBindingKey;
    private final String defaultLabel;

    ToolKeybindSlot(@Nonnull String inputBindingKey, @Nonnull String defaultLabel) {
        this.inputBindingKey = inputBindingKey;
        this.defaultLabel = defaultLabel;
    }

    @Nonnull
    public String inputBindingKey() {
        return inputBindingKey;
    }

    @Nonnull
    public String defaultLabel() {
        return defaultLabel;
    }

    /** Shared append row template for server-built tool HUD hint lists. */
    @Nonnull
    public String rowUiPath() {
        return "Aetherhaven/ToolHudHotkeyRow.ui";
    }

    @Nullable
    public static ToolKeybindSlot fromAbstractKey(@Nullable String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return switch (key.trim()) {
            case "Primary" -> PRIMARY;
            case "Secondary" -> SECONDARY;
            case "F" -> USE;
            case "Q" -> ABILITY1;
            case "E" -> ABILITY2;
            case "R" -> ABILITY3;
            case "Escape" -> ESCAPE;
            case "Shift" -> SHIFT;
            case "Ctrl" -> CTRL;
            case "Space" -> SPACE;
            case "WASD" -> MOVEMENT;
            default -> null;
        };
    }
}
