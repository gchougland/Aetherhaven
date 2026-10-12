package com.hexvane.aetherhaven.rts.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.rts.RtsCommandPlayerComponent;
import com.hexvane.aetherhaven.rts.RtsOrderMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class RtsCommandStatusHud extends CustomUIHud {
    private static final String TOOL_HELP_ROWS = "#ToolHelpRows";
    private static final String CONTROLS_ROWS = "#ControlsRows";
    private static final String P = "aetherhaven_rts.aetherhaven.rts";

    /** What the guards line says while the Command Flag is carried away from the command post. */
    public enum FieldGuards {
        /** The holder belongs to no town here. */
        NO_TOWN,
        /** The town has no guard in the world. */
        NONE,
        /** Guards are listening. */
        LISTENING
    }

    public RtsCommandStatusHud(@Nonnull PlayerRef playerRef) {
        super(playerRef, AetherhavenConstants.RTS_COMMAND_HUD_KEY, 0);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.append("Aetherhaven/RtsCommandStatusHud.ui");
    }

    public void refresh(@Nonnull RtsCommandPlayerComponent session, @Nonnull String toolHelpKey) {
        UICommandBuilder b = new UICommandBuilder();
        b.set("#HudTitle.TextSpans", Message.translation(P + ".hudTitle"));
        b.set("#OrderModeLine.TextSpans", Message.translation(orderModeKey(session.getOrderMode())));
        // The same panel serves the flag carried in hand, which hides these two.
        b.set("#StanceLine.Visible", true);
        b.set("#ControlsRows.Visible", true);
        b.set("#StanceLine.TextSpans", Message.translation(stanceKey(session, P)));
        RtsHudHotkeyHints.appendToolHelpRows(b, TOOL_HELP_ROWS, toolHelpKey, getPlayerRef());
        b.set(
            "#SelectedLine.TextSpans",
            Message.translation(P + ".hudSelected").param("n", String.valueOf(session.getSelectedGuardUuids().size()))
        );
        RtsHudHotkeyHints.appendControlsRows(b, CONTROLS_ROWS, getPlayerRef());
        this.update(false, b);
    }

    /**
     * The panel while the Command Flag is carried in hand, away from the command post: the order the next send will
     * carry, who is listening, and what each key does. There is no camera to explain and no stance tool, so those
     * parts are hidden.
     *
     * @param guards living guards of the holder's town that are in the world
     * @param underOrders how many of them are following an order now
     */
    public void refreshField(@Nonnull RtsOrderMode orderMode, @Nonnull FieldGuards state, int guards, int underOrders) {
        this.update(false, fieldCommands(orderMode, state, guards, underOrders, getPlayerRef()));
    }

    /** What {@link #refreshField} sends. Apart from the panel so a test can read every command. */
    @Nonnull
    static UICommandBuilder fieldCommands(
        @Nonnull RtsOrderMode orderMode,
        @Nonnull FieldGuards state,
        int guards,
        int underOrders,
        @Nullable PlayerRef playerRef
    ) {
        UICommandBuilder b = new UICommandBuilder();
        b.set("#HudTitle.TextSpans", Message.translation(P + ".hudTitle"));
        b.set("#OrderModeLine.TextSpans", Message.translation(orderModeKey(orderMode)));
        b.set("#StanceLine.Visible", false);
        b.set("#SelectedLine.TextSpans", fieldGuardsLine(state, guards, underOrders));
        RtsHudHotkeyHints.appendFieldFlagRows(b, TOOL_HELP_ROWS, playerRef);
        b.clear(CONTROLS_ROWS);
        b.set("#ControlsRows.Visible", false);
        return b;
    }

    @Nonnull
    static Message fieldGuardsLine(@Nonnull FieldGuards state, int guards, int underOrders) {
        return Message
            .translation(fieldGuardsKey(state, underOrders))
            .param("n", String.valueOf(guards))
            .param("m", String.valueOf(underOrders));
    }

    @Nonnull
    public static String fieldGuardsKey(@Nonnull FieldGuards state, int underOrders) {
        return switch (state) {
            case NO_TOWN -> P + ".hudFieldNoTown";
            case NONE -> P + ".hudFieldNoGuards";
            case LISTENING -> underOrders > 0 ? P + ".hudFieldUnderOrders" : P + ".hudFieldListening";
        };
    }

    @Nonnull
    public static String orderModeKey(@Nonnull RtsOrderMode orderMode) {
        return switch (orderMode) {
            case ATTACK_MOVE -> P + ".orderAttackMove";
            case MOVE -> P + ".orderMove";
        };
    }

    @Nonnull
    private static String stanceKey(@Nonnull RtsCommandPlayerComponent session, @Nonnull String p) {
        return switch (session.getStanceMode()) {
            case DEFENSIVE -> p + ".stanceDefensive";
            case AGGRESSIVE -> p + ".stanceAggressive";
            case STAND_GROUND -> p + ".stanceStandGround";
            case HOLD_FIRE -> p + ".stanceHoldFire";
        };
    }
}
