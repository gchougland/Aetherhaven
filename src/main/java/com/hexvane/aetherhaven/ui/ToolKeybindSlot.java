package com.hexvane.aetherhaven.ui;

import com.hypixel.hytale.protocol.InteractionType;
import java.util.Locale;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * The inputs an Aetherhaven tool hint can refer to.
 *
 * <p>A slot never carries a key name. Only the player's client knows which key is bound to an input, and the server
 * cannot see it (see {@link ToolKeyPrompts}). For a slot that is an item interaction, the client shows the player's
 * real key beside the action name of the root interaction bound to {@link #interactionType()}.
 *
 * <p>{@link #ABILITY1}, {@link #ABILITY2} and {@link #ABILITY3} are a tool's first, second and third extra actions,
 * not the game's ability inputs of the same number. Since Hytale 0.7.0-pre.5 the game's Ability 2 and Ability 3 inputs
 * belong to rune abilities while an item is held, so the second action sits on the game's Ability 4 input and the
 * third is the first input used while crouching. See {@link ToolAbilityKeys}.
 *
 * <p>{@link #ESCAPE}, {@link #SHIFT}, {@link #CTRL}, {@link #SPACE}, {@link #MOVEMENT} and {@link #CROUCH} are named
 * after the keys they had by default, and are kept so existing callers still compile. They are game inputs that are
 * not item interactions, so the game has no key prompt for them: a hint row shows the game's own name for the input
 * ("Sprint", "Fly down"), which stays true whatever key the player bound.
 */
public enum ToolKeybindSlot {
    PRIMARY(InteractionType.Primary),
    SECONDARY(InteractionType.Secondary),
    USE(InteractionType.Use),
    ABILITY1(ToolAbilityKeys.FIRST),
    ABILITY2(ToolAbilityKeys.SECOND),
    ABILITY3(ToolAbilityKeys.THIRD, true),
    PICK(InteractionType.Pick),
    /** The game's cancel input. */
    ESCAPE("cancel"),
    /** The game's sprint input. */
    SHIFT("sprint"),
    /** The game's fly down input. */
    CTRL("flyDown"),
    /** The game's jump input, which is also fly up. */
    SPACE("jump"),
    /** The game's movement inputs. */
    MOVEMENT("move"),
    /** The game's crouch input. Held with {@link #ABILITY1} it is a tool's third action. */
    CROUCH("crouch");

    @Nullable
    private final InteractionType interactionType;
    private final boolean crouchChord;
    private final String langSuffix;

    ToolKeybindSlot(@Nonnull InteractionType interactionType) {
        this(interactionType, false);
    }

    ToolKeybindSlot(@Nonnull InteractionType interactionType, boolean crouchChord) {
        this.interactionType = interactionType;
        this.crouchChord = crouchChord;
        this.langSuffix = langSuffix(interactionType);
    }

    ToolKeybindSlot(@Nonnull String gameInputName) {
        this.interactionType = null;
        this.crouchChord = false;
        this.langSuffix = gameInputName;
    }

    /**
     * The item interaction this slot is bound to, or null for a game input that is not an item interaction.
     * A tool's item JSON binds a root interaction to the same type.
     */
    @Nullable
    public InteractionType interactionType() {
        return interactionType;
    }

    /** True when the game lists a key for this slot beside the held tool's action name. */
    public boolean hasKeyPrompt() {
        return interactionType != null;
    }

    /** True when the slot is {@link #chordBase()} used while crouching. The game has no key prompt for a chord. */
    public boolean isCrouchChord() {
        return crouchChord;
    }

    /** The plain slot whose input this chord uses, or null for a plain slot. */
    @Nullable
    public ToolKeybindSlot chordBase() {
        if (!crouchChord) {
            return null;
        }
        for (ToolKeybindSlot slot : values()) {
            if (!slot.crouchChord && slot.interactionType == interactionType) {
                return slot;
            }
        }
        return null;
    }

    /** The input that must be held with this one, or null for a plain slot. */
    @Nullable
    public ToolKeybindSlot chordModifier() {
        return crouchChord ? CROUCH : null;
    }

    /** Last segment of the action name's translation key, for example {@code ability4} or {@code sprint}. */
    @Nonnull
    public String langSuffix() {
        return langSuffix;
    }

    /** Shared append row template for server-built tool HUD hint lists. */
    @Nonnull
    public String rowUiPath() {
        return ToolHudHotkeyRows.ROW_UI;
    }

    @Nonnull
    public String modifierRowUiPath() {
        return ToolHudHotkeyRows.MODIFIER_ROW_UI;
    }

    @Nonnull
    static String langSuffix(@Nonnull InteractionType type) {
        String name = type.name();
        return name.substring(0, 1).toLowerCase(Locale.ROOT) + name.substring(1);
    }
}
