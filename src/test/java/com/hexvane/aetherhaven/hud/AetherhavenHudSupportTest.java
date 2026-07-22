package com.hexvane.aetherhaven.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
final class AetherhavenHudSupportTest {
    @Test
    void combinedGoldUsesOnlyTheSumAndSaturates() {
        assertEquals(125L, AetherhavenHudSnapshotService.combinedGold(25L, 100L));
        assertEquals(5L, AetherhavenHudSnapshotService.combinedGold(-10L, 5L));
        assertEquals(
            Long.MAX_VALUE,
            AetherhavenHudSnapshotService.combinedGold(Long.MAX_VALUE - 2L, 10L)
        );
    }

    @Test
    void parsesAndClampsSavedPanelPlacement() {
        assertEquals(
            new HudPanelPlacement(HudPlacement.BOTTOM_RIGHT, 4000, 0),
            AetherhavenHudRefreshSystem.placement("bottom_right", 9999, -4, HudPlacement.TOP_LEFT)
        );
        assertEquals(
            new HudPanelPlacement(HudPlacement.TOP_LEFT, 4, 5),
            AetherhavenHudRefreshSystem.placement("unknown", 4, 5, HudPlacement.TOP_LEFT)
        );
    }

    @Test
    void panelsShrinkToTheirVisibleContent() {
        int fullStatus = AetherhavenHud.statusHeight(true, true, true);
        assertEquals(117, fullStatus);
        assertEquals(46, AetherhavenHud.statusHeight(true, false, false));
        assertEquals(51, AetherhavenHud.statusHeight(false, false, true));

        assertEquals(33, AetherhavenHud.questHeight(0));
        assertEquals(124, AetherhavenHud.questHeight(1));
        assertEquals(299, AetherhavenHud.questHeight(3));
    }

    @Test
    void questRowsGrowForWrappedGoalText() {
        HudQuestEntry shortQuest = new HudQuestEntry(
            HudQuestEntry.Source.STORY,
            "short",
            com.hypixel.hytale.server.core.Message.raw("A short title"),
            com.hypixel.hytale.server.core.Message.raw("One goal"),
            "short:open"
        );
        HudQuestEntry longQuest = new HudQuestEntry(
            HudQuestEntry.Source.STORY,
            "long",
            com.hypixel.hytale.server.core.Message.raw("A long quest title that needs enough room to wrap cleanly"),
            com.hypixel.hytale.server.core.Message.raw(
                "First detailed goal with useful progress\nSecond detailed goal with useful progress\nThird detailed goal"
            ),
            "long:open"
        );

        assertEquals(84, AetherhavenHud.questRowHeight(shortQuest));
        org.junit.jupiter.api.Assertions.assertTrue(
            AetherhavenHud.questRowHeight(longQuest) >= AetherhavenHud.questRowHeight(shortQuest)
        );
    }
}
