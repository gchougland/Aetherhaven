package com.hexvane.aetherhaven.villager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hexvane.aetherhaven.dialogue.DialogueNpcConditionUtil;
import com.hexvane.aetherhaven.dialogue.DialogueNpcConditionUtil.LowNeedKind;
import com.hexvane.aetherhaven.dialogue.DialogueNpcConditionUtil.VillagerNeedsSnapshot;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("crossmod")
class VillagerNeedsDialoguePickerTest {

    @Test
    void hungerLowestWhenOnlyHungerBelowThreshold() {
        VillagerNeedsSnapshot snapshot = new VillagerNeedsSnapshot(45f, 80f, 80f);
        assertEquals(LowNeedKind.HUNGER, DialogueNpcConditionUtil.resolveLowestLowNeed(snapshot));
    }

    @Test
    void energySelectedWhenLowestBelowThreshold() {
        VillagerNeedsSnapshot snapshot = new VillagerNeedsSnapshot(60f, 30f, 80f);
        assertEquals(LowNeedKind.ENERGY, DialogueNpcConditionUtil.resolveLowestLowNeed(snapshot));
    }

    @Test
    void funSelectedWhenLowestBelowThreshold() {
        VillagerNeedsSnapshot snapshot = new VillagerNeedsSnapshot(60f, 60f, 25f);
        assertEquals(LowNeedKind.FUN, DialogueNpcConditionUtil.resolveLowestLowNeed(snapshot));
    }

    @Test
    void noLineWhenAllNeedsAboveThreshold() {
        VillagerNeedsSnapshot snapshot = new VillagerNeedsSnapshot(55f, 50f, 45f);
        assertNull(DialogueNpcConditionUtil.resolveLowestLowNeed(snapshot));
    }

    @Test
    void hungerWinsTieAtSameLowValue() {
        VillagerNeedsSnapshot snapshot = new VillagerNeedsSnapshot(30f, 30f, 80f);
        assertEquals(LowNeedKind.HUNGER, DialogueNpcConditionUtil.resolveLowestLowNeed(snapshot));
    }

    @Test
    void hungerAtThresholdIsNotLow() {
        VillagerNeedsSnapshot snapshot = new VillagerNeedsSnapshot(50f, 80f, 80f);
        assertNull(DialogueNpcConditionUtil.resolveLowestLowNeed(snapshot));
    }

    @Test
    void nullSnapshotReturnsNull() {
        assertNull(DialogueNpcConditionUtil.resolveLowestLowNeed(null));
    }
}
