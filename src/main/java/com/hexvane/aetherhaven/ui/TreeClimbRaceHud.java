package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.festival.treeclimb.TreeClimbIds;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

/** Counting-up timer HUD while a tree climb race is running. */
public final class TreeClimbRaceHud extends CustomUIHud {

    public TreeClimbRaceHud(@Nonnull PlayerRef playerRef) {
        super(playerRef, TreeClimbIds.HUD_KEY, 0);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.append("Aetherhaven/TreeClimbRaceHud.ui");
    }

    public void refresh(@Nonnull String timeText) {
        UICommandBuilder b = new UICommandBuilder();
        b.set(
            "#TreeClimbTimerLabel.TextSpans",
            com.hypixel.hytale.server.core.Message.translation(
                "aetherhaven_festivals.aetherhaven.festival.tree_climbing.hud.timerLabel"
            )
        );
        b.set("#TreeClimbTimerText.TextSpans", com.hypixel.hytale.server.core.Message.raw(timeText));
        this.update(false, b);
    }
}
