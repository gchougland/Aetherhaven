package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.festival.snowball.SnowballIds;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import javax.annotation.Nonnull;

/** Timer bar and snowflake lives while a snowball fight is running. */
public final class SnowballFightHud extends CustomUIHud {
    private static final String TRACK = "#SnowballHudPanel #SnowballBarFillTrack";
    private static final int FLEX_SCALE = 1000;

    public SnowballFightHud(@Nonnull PlayerRef playerRef) {
        super(playerRef, SnowballIds.HUD_KEY, 0);
    }

    @Override
    protected void build(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.append("Aetherhaven/SnowballFightHud.ui");
    }

    public void refresh(float remainingFraction, int lives) {
        UICommandBuilder b = new UICommandBuilder();
        b.set(
            "#SnowballTitle.TextSpans",
            Message.translation("aetherhaven_festivals.aetherhaven.festival.snowball.hud.title")
        );
        applyFill(b, remainingFraction);
        int shown = Math.max(0, Math.min(SnowballIds.LIVES, lives));
        applyLife(b, 0, shown >= 1);
        applyLife(b, 1, shown >= 2);
        applyLife(b, 2, shown >= 3);
        this.update(false, b);
    }

    private static void applyFill(@Nonnull UICommandBuilder cmd, float remainingFraction) {
        float progress = Math.max(0f, Math.min(1f, remainingFraction));
        int fillWeight = Math.max(0, Math.round(progress * FLEX_SCALE));
        int remainWeight = Math.max(1, FLEX_SCALE - fillWeight);
        if (fillWeight == 0) {
            remainWeight = FLEX_SCALE;
        }
        cmd.set(TRACK + " #SnowballBarFill.FlexWeight", fillWeight);
        cmd.set(TRACK + " #SnowballBarFillRemainder.FlexWeight", remainWeight);
        cmd.set(TRACK + " #SnowballBarFill.Visible", fillWeight > 0);
    }

    private static void applyLife(@Nonnull UICommandBuilder cmd, int index, boolean full) {
        cmd.set("#SnowballLife" + index + ".Visible", true);
        cmd.set("#SnowballLife" + index + "Fg.Visible", full);
    }
}
