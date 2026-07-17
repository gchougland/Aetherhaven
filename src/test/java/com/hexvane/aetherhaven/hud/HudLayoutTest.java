package com.hexvane.aetherhaven.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
final class HudLayoutTest {
    @Test
    void resolvesCornerOffsetsInward() {
        HudLayout.ResolvedAnchor topLeft =
            HudLayout.resolve(new HudPanelPlacement(HudPlacement.TOP_LEFT, 7, 9), 300, 100);
        assertEquals(27, topLeft.left());
        assertEquals(29, topLeft.top());
        assertNull(topLeft.right());
        assertNull(topLeft.bottom());

        HudLayout.ResolvedAnchor bottomRight =
            HudLayout.resolve(new HudPanelPlacement(HudPlacement.BOTTOM_RIGHT, 7, 9), 390, 278);
        assertNull(bottomRight.left());
        assertNull(bottomRight.top());
        assertEquals(27, bottomRight.right());
        assertEquals(29, bottomRight.bottom());
    }

    @Test
    void resolvesOtherCornersAndPreservesDimensions() {
        HudLayout.ResolvedAnchor topRight = HudLayout.resolve(HudPanelPlacement.topRight(), 390, 278);
        assertEquals(20, topRight.top());
        assertEquals(20, topRight.right());
        assertEquals(390, topRight.width());
        assertEquals(278, topRight.height());

        HudLayout.ResolvedAnchor bottomLeft = HudLayout.resolve(HudPanelPlacement.bottomLeft(), 300, 112);
        assertEquals(20, bottomLeft.left());
        assertEquals(20, bottomLeft.bottom());
    }

    @Test
    void customOffsetsAreAbsoluteLeftAndTop() {
        HudLayout.ResolvedAnchor custom = HudLayout.resolve(HudPanelPlacement.custom(123, 456), 300, 112);

        assertEquals(123, custom.left());
        assertEquals(456, custom.top());
        assertNull(custom.right());
        assertNull(custom.bottom());
    }
}
