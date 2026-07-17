package com.hexvane.aetherhaven.hud;

import javax.annotation.Nonnull;

/**
 * Panel placement with pixel offsets. Corner offsets move inward; custom offsets are absolute left/top coordinates.
 */
public record HudPanelPlacement(@Nonnull HudPlacement placement, int xOffset, int yOffset) {
    public static final int DEFAULT_MARGIN = 20;

    @Nonnull
    public static HudPanelPlacement topLeft() {
        return new HudPanelPlacement(HudPlacement.TOP_LEFT, 0, 0);
    }

    @Nonnull
    public static HudPanelPlacement topRight() {
        return new HudPanelPlacement(HudPlacement.TOP_RIGHT, 0, 0);
    }

    @Nonnull
    public static HudPanelPlacement bottomLeft() {
        return new HudPanelPlacement(HudPlacement.BOTTOM_LEFT, 0, 0);
    }

    @Nonnull
    public static HudPanelPlacement bottomRight() {
        return new HudPanelPlacement(HudPlacement.BOTTOM_RIGHT, 0, 0);
    }

    @Nonnull
    public static HudPanelPlacement custom(int left, int top) {
        return new HudPanelPlacement(HudPlacement.CUSTOM, left, top);
    }
}
