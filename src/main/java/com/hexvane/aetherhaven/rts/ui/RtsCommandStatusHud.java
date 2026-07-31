package com.hexvane.aetherhaven.rts.ui;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.rts.RtsCommandPlayerComponent;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

public final class RtsCommandStatusHud extends CustomUIHud {
    private static final String TOOL_HELP_ROWS = "#ToolHelpRows";
    private static final String CONTROLS_ROWS = "#ControlsRows";

    public RtsCommandStatusHud(@Nonnull PlayerRef playerRef) {
        super(playerRef, AetherhavenConstants.RTS_COMMAND_HUD_KEY, 0);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.append("Aetherhaven/RtsCommandStatusHud.ui");
    }

    public void refresh(@Nonnull RtsCommandPlayerComponent session, @Nonnull String toolHelpKey) {
        UICommandBuilder b = new UICommandBuilder();
        String p = "aetherhaven_rts.aetherhaven.rts";
        b.set("#HudTitle.TextSpans", Message.translation(p + ".hudTitle"));
        b.set("#OrderModeLine.TextSpans", Message.translation(orderModeKey(session, p)));
        b.set("#StanceLine.TextSpans", Message.translation(stanceKey(session, p)));
        RtsHudHotkeyHints.appendToolHelpRows(b, TOOL_HELP_ROWS, toolHelpKey, getPlayerRef());
        b.set(
            "#SelectedLine.TextSpans",
            Message.translation(p + ".hudSelected").param("n", String.valueOf(session.getSelectedGuardUuids().size()))
        );
        RtsHudHotkeyHints.appendControlsRows(b, CONTROLS_ROWS, getPlayerRef());
        this.update(false, b);
    }

    @Nonnull
    private static String orderModeKey(@Nonnull RtsCommandPlayerComponent session, @Nonnull String p) {
        return switch (session.getOrderMode()) {
            case ATTACK_MOVE -> p + ".orderAttackMove";
            case MOVE -> p + ".orderMove";
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
