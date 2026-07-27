package com.hexvane.aetherhaven.ui;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Display label slots for Aetherhaven tool HUD key hints (visual only). */
public enum ToolKeybindSlot {
    PRIMARY("Primary"),
    SECONDARY("Secondary"),
    USE("F"),
    ABILITY1("Q"),
    ABILITY2("E"),
    ABILITY3("R"),
    ESCAPE("Escape"),
    SHIFT("Shift"),
    CTRL("Ctrl"),
    SPACE("Space"),
    MOVEMENT("WASD");

    private final String defaultLabel;

    ToolKeybindSlot(@Nonnull String defaultLabel) {
        this.defaultLabel = defaultLabel;
    }

    @Nonnull
    public String defaultLabel() {
        return defaultLabel;
    }

    /** Lang suffix under {@code aetherhaven.ui.toolKeybinds.slot.*}. */
    @Nonnull
    public String langSuffix() {
        return name().toLowerCase();
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
