package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.festival.hallowseve.HallowsEveIds;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

/** Countdown then remaining-time HUD for a Hallow's Eve maze run. */
public final class HallowsEveMazeHud extends CustomUIHud {

    public HallowsEveMazeHud(@Nonnull PlayerRef playerRef) {
        super(playerRef, HallowsEveIds.HUD_KEY, 0);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.append("Aetherhaven/HallowsEveMazeHud.ui");
    }

    public void refreshCountdown(@Nonnull String numberText) {
        UICommandBuilder b = new UICommandBuilder();
        b.set("#HallowsEveCountdownPanel.Visible", true);
        b.set("#HallowsEveRacePanel.Visible", false);
        b.set("#HallowsEveCountdownText.TextSpans", com.hypixel.hytale.server.core.Message.raw(numberText));
        this.update(false, b);
    }

    public void refreshRace(@Nonnull String timeText, @Nonnull String orbsText) {
        UICommandBuilder b = new UICommandBuilder();
        b.set("#HallowsEveCountdownPanel.Visible", false);
        b.set("#HallowsEveRacePanel.Visible", true);
        b.set(
            "#HallowsEveTimerLabel.TextSpans",
            com.hypixel.hytale.server.core.Message.translation(
                "aetherhaven_festivals.aetherhaven.festival.hallows_eve.hud.timerLabel"
            )
        );
        b.set("#HallowsEveTimerText.TextSpans", com.hypixel.hytale.server.core.Message.raw(timeText));
        b.set("#HallowsEveOrbsText.TextSpans", com.hypixel.hytale.server.core.Message.raw(orbsText));
        this.update(false, b);
    }
}
