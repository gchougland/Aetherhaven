package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.villager.VillagerNeeds;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class ResidentNeedsUiTest {
    @Test void liveMetersTakePrecedenceWithoutChangingTheSavedSnapshot() {
        var saved = new ResidentNpcRecord(); saved.setLastKnownNeedsIfChanged(10, 20, 30, 0);
        var live = VillagerNeeds.full(); live.setHunger(75); live.setEnergy(60); live.setFun(40);
        var view = ResidentNeedsUi.snapshot(true, live, saved);
        assertTrue(view.available()); assertEquals("Current needs", view.status());
        assertEquals(.75f, view.hunger()); assertEquals(.6f, view.energy()); assertEquals(.4f, view.fun());
        assertEquals(10, saved.getLastKnownHunger());
    }

    @Test void unloadedResidentsUseExplicitlyLabeledSavedValues() {
        var saved = new ResidentNpcRecord(); saved.setLastKnownNeedsIfChanged(20, 45, 80, 0);
        var view = ResidentNeedsUi.snapshot(true, null, saved);
        assertTrue(view.available()); assertEquals("Last known needs", view.status());
        assertEquals(.2f, view.hunger()); assertEquals(.45f, view.energy()); assertEquals(.8f, view.fun());
    }

    @Test void MissingOrInapplicableNeedsDoNotDisplayInventedMeters() {
        assertFalse(ResidentNeedsUi.snapshot(true, null, new ResidentNpcRecord()).available());
        var saved = new ResidentNpcRecord(); saved.setLastKnownNeedsIfChanged(20, 45, 80, 0);
        var unsupported = ResidentNeedsUi.snapshot(false, VillagerNeeds.full(), saved);
        assertFalse(unsupported.available()); assertEquals(0, unsupported.hunger());
    }
}
