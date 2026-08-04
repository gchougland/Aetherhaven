package com.hexvane.aetherhaven.dialogue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.villager.VillagerNeeds;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
class DialogueNpcConditionUtilTest {

    @Test
    void reputationHeartsMapsTenPointSteps() {
        assertEquals(0, DialogueNpcConditionUtil.reputationHearts(0));
        assertEquals(0, DialogueNpcConditionUtil.reputationHearts(9));
        assertEquals(1, DialogueNpcConditionUtil.reputationHearts(10));
        assertEquals(5, DialogueNpcConditionUtil.reputationHearts(50));
        assertEquals(10, DialogueNpcConditionUtil.reputationHearts(100));
        assertEquals(10, DialogueNpcConditionUtil.reputationHearts(150));
    }

    @Test
    void reputationForHeartsClamps() {
        assertEquals(0, DialogueNpcConditionUtil.reputationForHearts(0));
        assertEquals(50, DialogueNpcConditionUtil.reputationForHearts(5));
        assertEquals(100, DialogueNpcConditionUtil.reputationForHearts(10));
        assertEquals(100, DialogueNpcConditionUtil.reputationForHearts(20));
    }

    @Test
    void needPercentUsesFullScale() {
        assertEquals(0, DialogueNpcConditionUtil.needPercent(0f));
        assertEquals(50, DialogueNpcConditionUtil.needPercent(50f));
        assertEquals(100, DialogueNpcConditionUtil.needPercent(VillagerNeeds.MAX));
    }

    @Test
    void minNeedPercentUsesLowestMeter() {
        VillagerNeeds needs = new VillagerNeeds();
        needs.setHunger(80f);
        needs.setEnergy(30f);
        needs.setFun(60f);
        assertEquals(30, DialogueNpcConditionUtil.minNeedPercent(needs));
    }

    @Test
    void hungerPercentReadsHungerOnly() {
        VillagerNeeds needs = new VillagerNeeds();
        needs.setHunger(75f);
        needs.setEnergy(10f);
        needs.setFun(10f);
        assertEquals(75, DialogueNpcConditionUtil.hungerPercent(needs));
    }

    @Test
    void dawnDayWithinUsesHalfOpenWindow() {
        assertTrue(DialogueNpcConditionUtil.dawnDayWithin(100L, 100L, 3));
        assertTrue(DialogueNpcConditionUtil.dawnDayWithin(100L, 102L, 3));
        assertFalse(DialogueNpcConditionUtil.dawnDayWithin(100L, 103L, 3));
        assertFalse(DialogueNpcConditionUtil.dawnDayWithin(105L, 100L, 3));
        assertFalse(DialogueNpcConditionUtil.dawnDayWithin(100L, 100L, 0));
    }
}
