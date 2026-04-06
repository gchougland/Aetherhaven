package com.hexvane.aetherhaven.ui;

import com.hypixel.hytale.server.core.ui.Anchor;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import javax.annotation.Nonnull;

/** Ten-slot heart bar: grey backing, filled/partial pink hearts for 0–100 reputation. */
public final class ReputationHeartUi {
    /** Foreground heart pixel size when full (must match {@code HeartSlot.ui} default). */
    private static final int HEART_PX = 20;

    private ReputationHeartUi() {}

    public static void applyHearts(@Nonnull UICommandBuilder cmd, @Nonnull String heartSlotsPrefix, int reputation) {
        int rep = Math.max(0, Math.min(100, reputation));
        int full = rep / 10;
        int rem = rep % 10;
        for (int i = 0; i < 10; i++) {
            String slot = heartSlotsPrefix + "[" + i + "]";
            if (i < full) {
                cmd.set(slot + " #Fg.Visible", true);
                cmd.setObject(slot + " #Fg.Anchor", anchorHeart(HEART_PX));
            } else if (i == full && rem > 0) {
                cmd.set(slot + " #Fg.Visible", true);
                int px = Math.max(1, Math.round(HEART_PX * (rem / 10f)));
                cmd.setObject(slot + " #Fg.Anchor", anchorHeart(px));
            } else {
                cmd.set(slot + " #Fg.Visible", false);
            }
        }
    }

    @Nonnull
    private static Anchor anchorHeart(int px) {
        Anchor a = new Anchor();
        a.setWidth(Value.of(px));
        a.setHeight(Value.of(px));
        return a;
    }
}
