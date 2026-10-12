package com.hexvane.aetherhaven.hunter;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The hunt's revision moves with every wave won, so the Hunter's page must never refuse a press because "the run
 * changed". Each press names what it asks for and is judged by the hunt as it is now.
 */
@Tag("town")
class HuntPagePressTest {
    @Test void trainingNamesTheLevelItBuysSoOneOfferIsNeverBoughtTwiceOrAtAnotherPrice() {
        var state = new TownHunts();
        // Drawn at level 0: the button offers level 1.
        assertNull(HuntPage.tierIssue(state.level("vitality"), "1", "Vitality"));
        assertTrue(state.upgrade("vitality"));
        // The same press again, or one from a page drawn before another player trained it.
        assertEquals("Vitality level 1 is already trained. Nothing was spent.", HuntPage.tierIssue(state.level("vitality"), "1", "Vitality"));
        assertNull(HuntPage.tierIssue(state.level("vitality"), "2", "Vitality"));
        assertEquals("Train Vitality level 2 first.", HuntPage.tierIssue(state.level("vitality"), "3", "Vitality"));
        assertEquals("Choose the training again.", HuntPage.tierIssue(0, null, "Vitality"));
        assertEquals("Choose the training again.", HuntPage.tierIssue(0, "x", "Vitality"));
    }

    @Test void aWaveWonSinceThePageWasDrawnDoesNotStandInTheWayOfAPress() {
        var state = new TownHunts();
        state.unlock("bone");
        long drawn = state.revision();
        var run = state.begin();
        assertNotNull(run);
        assertNotEquals(drawn, state.revision(), "Starting a run and winning waves moves the revision the old check compared");
        // Spoils are collected by what is in stock now.
        assertEquals(0, state.amount("Ingredient_Bone_Fragment"));
        state.end("Defeated");
        // Training is offered again after the run, at the level the page drew.
        assertNull(HuntPage.tierIssue(state.level("power"), "1", "Weapon mastery"));
    }

    @Test void healthIsShownInTwentiethsSoAFightDoesNotSendAnUpdateForEveryScratch() {
        assertEquals(1, HuntPage.shownShare(260, 260), 1e-9);
        assertEquals(0, HuntPage.shownShare(0, 260), 1e-9);
        assertEquals(.95, HuntPage.shownShare(259, 260), 1e-9, "Hurt never reads as full");
        assertEquals(.05, HuntPage.shownShare(1, 260), 1e-9, "Alive never reads as empty");
        assertEquals(.5, HuntPage.shownShare(131, 260), 1e-9);
        assertEquals(0, HuntPage.shownShare(10, 0), 1e-9);
        int changes = 0;
        double last = -1;
        for (int health = 260; health >= 0; health--) {
            double shown = HuntPage.shownShare(health, 260);
            if (shown != last) changes++;
            last = shown;
        }
        assertTrue(changes <= 22, "260 points of damage move the bar " + changes + " times");
    }

    @Test void thePageCarriesNoRevisionAndItsButtonsNameWhatTheyAskFor() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/hexvane/aetherhaven/hunter/HuntPage.java"));
        assertFalse(source.contains("\"Revision\""));
        assertFalse(source.contains("The run changed"));
        // Start and Retire are two presses. One button that did either would start, and charge for, a new run when
        // the old one ended a moment before a press meant to retire it.
        assertTrue(source.contains("event(state.run()==null?\"Start\":\"Retire\",\"\")"));
        assertTrue(source.contains("That run has already ended. Nothing was spent."));
        assertTrue(source.contains("sendChanges(c,e)"), "The timer sends only what differs from what the player sees");
    }
}
