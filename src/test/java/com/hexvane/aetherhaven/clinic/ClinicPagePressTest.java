package com.hexvane.aetherhaven.clinic;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The clinic's medicine drops every time the Doctor treats someone, so "the supply changed" can never be a reason to
 * refuse a press on the clinic page. The page also redraws by itself; what it shows moves in steps so that it has
 * little to send.
 */
@Tag("town")
class ClinicPagePressTest {
    @Test void stockingIsJudgedByTheCabinetAndThePackAsTheyAreNow() {
        var state = new TownClinic();
        long drawn = state.revision();
        assertTrue(state.supply(2));
        // The Doctor treats a patient after the page was drawn. The old check compared this number and refused.
        assertTrue(state.treat(10, 100) > 0);
        assertNotEquals(drawn, state.revision());
        assertNull(ClinicPage.supplyIssue(state, 1, 1));
        assertNull(ClinicPage.supplyIssue(state, 5, 9));
    }

    @Test void aRefusalSaysExactlyWhatIsShortAndThatNothingWasTaken() {
        var state = new TownClinic();
        assertEquals("You need 5 Lesser Health Potions and carry 2. Nothing was taken.", ClinicPage.supplyIssue(state, 5, 2));
        assertEquals("You need 1 Lesser Health Potion and carry 0. Nothing was taken.", ClinicPage.supplyIssue(state, 1, 0));
        assertTrue(state.supply(8));
        assertEquals(TownClinic.CAPACITY - 2 * TownClinic.SUPPLY_VALUE, state.medicine());
        assertEquals("The medicine cabinet only has room for 2 more. Nothing was taken.", ClinicPage.supplyIssue(state, 5, 9));
        assertNull(ClinicPage.supplyIssue(state, 1, 9));
        assertTrue(state.supply(2));
        assertEquals("The medicine cabinet is full. Nothing was taken.", ClinicPage.supplyIssue(state, 1, 9));
        // A patient is treated: there is room again and the same press now works.
        assertTrue(state.treat(1, 100) > 0);
        assertTrue(state.treat(1, 100) > 0);
        assertNull(ClinicPage.supplyIssue(state, 1, 9));
    }

    @Test void aCourseOfCareMovesFourTimesNotEverySecond() {
        int changes = 0;
        String last = null;
        // Looked at once a second for a whole treatment, as the page's timer does.
        for (int second = 0; second <= (int) TownClinic.TREATMENT_SECONDS; second++) {
            double progress = second / TownClinic.TREATMENT_SECONDS;
            String shown = ClinicPage.courseShown(progress) + "|" + ClinicPage.remaining(progress);
            if (!shown.equals(last)) changes++;
            last = shown;
        }
        assertEquals(ClinicPage.COURSE_STEPS + 1, changes, "The start, three steps and the end");
        assertEquals(12, ClinicPage.remaining(0.01));
        assertEquals(9, ClinicPage.remaining(0.30));
        assertEquals(3, ClinicPage.remaining(0.99));
        assertEquals(0, ClinicPage.remaining(1));
        assertEquals(.5, ClinicPage.steps(.51, 20), 1e-9);
        assertEquals(1, ClinicPage.steps(7, 20), 1e-9);
        assertEquals(0, ClinicPage.steps(-1, 20), 1e-9);
    }

    @Test void thePageNoLongerComparesTheSupplyRevisionAndItsTimerSendsOnlyChanges() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/hexvane/aetherhaven/clinic/ClinicPage.java"));
        assertFalse(source.contains("seenRevision"));
        assertFalse(source.contains("The medicine supply changed"));
        assertTrue(source.contains("sendChanges(commands, events)"), "The timer sends only what differs from what the player sees");
        // The pause button asks for a state. A toggle seen late would undo itself.
        assertTrue(source.contains("state.paused() ? \"Resume\" : \"Pause\""));
    }
}
