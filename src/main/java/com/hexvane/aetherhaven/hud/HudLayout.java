package com.hexvane.aetherhaven.hud;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Pure placement math shared by the HUD and unit tests. */
public final class HudLayout {
    private HudLayout() {}

    @Nonnull
    public static ResolvedAnchor resolve(@Nonnull HudPanelPlacement placement, int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Panel dimensions must be positive");
        }
        int x = placement.xOffset();
        int y = placement.yOffset();
        int margin = HudPanelPlacement.DEFAULT_MARGIN;
        return switch (placement.placement()) {
            case TOP_LEFT -> new ResolvedAnchor(margin + x, margin + y, null, null, width, height);
            case TOP_RIGHT -> new ResolvedAnchor(null, margin + y, margin + x, null, width, height);
            case BOTTOM_LEFT -> new ResolvedAnchor(margin + x, null, null, margin + y, width, height);
            case BOTTOM_RIGHT -> new ResolvedAnchor(null, null, margin + x, margin + y, width, height);
            case CUSTOM -> new ResolvedAnchor(x, y, null, null, width, height);
        };
    }

    public record ResolvedAnchor(
        @Nullable Integer left,
        @Nullable Integer top,
        @Nullable Integer right,
        @Nullable Integer bottom,
        int width,
        int height
    ) {}
}
