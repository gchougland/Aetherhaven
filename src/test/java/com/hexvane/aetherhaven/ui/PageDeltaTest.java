package com.hexvane.aetherhaven.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.hypixel.hytale.protocol.packets.interface_.CustomUICommand;
import com.hypixel.hytale.protocol.packets.interface_.CustomUICommandType;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBinding;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A page that refreshes by itself must send nothing while it looks the same and only the moved values when it does
 * not: every update sent makes the server ignore the player's presses until the client answers it.
 */
@Tag("town")
class PageDeltaTest {
    private static CustomUICommand set(String selector, String value) {
        return new CustomUICommand(CustomUICommandType.Set, selector, "{\"0\":\"" + value + "\"}", null);
    }
    private static CustomUICommand clear(String selector) {
        return new CustomUICommand(CustomUICommandType.Clear, selector, null, null);
    }
    private static CustomUICommand append(String selector, String document) {
        return new CustomUICommand(CustomUICommandType.Append, selector, null, document);
    }
    private static CustomUIEventBinding press(String selector, String data) {
        return new CustomUIEventBinding(CustomUIEventBindingType.Activating, selector, data, false);
    }
    private static CustomUICommand[] commands(CustomUICommand... commands) { return commands; }
    private static CustomUIEventBinding[] events(CustomUIEventBinding... events) { return events; }

    /** A clinic like page: a document, a supply line and one row per patient. */
    private static CustomUICommand[] page(boolean withDocument, String supply, String... patients) {
        var list = new ArrayList<CustomUICommand>();
        if (withDocument) list.add(append(null, "Aetherhaven/ClinicPage.ui"));
        list.add(set("#Supply.Text", supply));
        list.add(clear("#Patients"));
        for (int i = 0; i < patients.length; i++) {
            list.add(append("#Patients", "Aetherhaven/ClinicPatient.ui"));
            list.add(set("#Patients[" + i + "] #Health.Text", patients[i]));
        }
        return list.toArray(new CustomUICommand[0]);
    }
    private static final CustomUIEventBinding[] BUTTONS = events(press("#SupplyOne", "{\"Action\":\"SupplyOne\"}"));

    @Test void aRefreshThatLooksTheSameSendsNothing() {
        var delta = new PageDelta();
        delta.sent(page(true, "40 / 100", "12 / 20"), BUTTONS);
        // The timer builds the page again a second later. The document is not appended a second time.
        for (int second = 0; second < 600; second++) assertNull(delta.next(page(false, "40 / 100", "12 / 20"), BUTTONS));
    }

    @Test void onlyTheValueThatMovedIsSentAndTheRowsAreKept() {
        var delta = new PageDelta();
        delta.sent(page(true, "40 / 100", "12 / 20", "3 / 20"), BUTTONS);
        var update = delta.next(page(false, "40 / 100", "13 / 20", "3 / 20"), BUTTONS);
        assertNotNull(update);
        assertFalse(update.whole());
        assertEquals(List.of(set("#Patients[0] #Health.Text", "13 / 20")), Arrays.asList(update.commands()));
        assertEquals(0, update.events().length, "the buttons are already bound");
        // What was sent is remembered: the same picture again sends nothing.
        assertNull(delta.next(page(false, "40 / 100", "13 / 20", "3 / 20"), BUTTONS));
    }

    @Test void aChangedListIsSentWhole() {
        var delta = new PageDelta();
        delta.sent(page(true, "40 / 100", "12 / 20"), BUTTONS);
        var arrived = page(false, "40 / 100", "12 / 20", "5 / 20");
        var update = delta.next(arrived, BUTTONS);
        assertNotNull(update);
        assertTrue(update.whole());
        assertArrayEquals(arrived, update.commands());
        assertNull(delta.next(arrived, BUTTONS));
        // The patient leaves again: rows are remade, so the list goes whole once more.
        var left = page(false, "40 / 100", "12 / 20");
        assertTrue(delta.next(left, BUTTONS).whole());
        assertNull(delta.next(left, BUTTONS));
    }

    @Test void aValueARemadeRowNoLongerSetsForcesAWholeSend() {
        var delta = new PageDelta();
        var withNote = commands(clear("#Rows"), append("#Rows", "Row.ui"), set("#Rows[0] #Name.Text", "Oak"), set("#Rows[0] #Note.Text", "Reserved"));
        delta.sent(withNote, events());
        // The same row without its note. Keeping the old row would leave "Reserved" on screen.
        var withoutNote = commands(clear("#Rows"), append("#Rows", "Row.ui"), set("#Rows[0] #Name.Text", "Oak"));
        var update = delta.next(withoutNote, events());
        assertNotNull(update);
        assertTrue(update.whole());
        assertNull(delta.next(withoutNote, events()));
    }

    @Test void aValueSetOnceOnAFixedElementDoesNotForceAWholeSend() {
        var delta = new PageDelta();
        // Set when the page opens and never again, as a search box is.
        delta.sent(commands(append(null, "Page.ui"), set("#Search.Value", ""), set("#Hint.Text", "a"), clear("#Rows"), append("#Rows", "Row.ui"),
            set("#Rows[0] #Name.Text", "Oak")), events());
        var later = commands(set("#Hint.Text", "a"), clear("#Rows"), append("#Rows", "Row.ui"), set("#Rows[0] #Name.Text", "Oak"));
        assertNull(delta.next(later, events()));
    }

    @Test void aListOfValuesAloneIsComparedValueByValue() {
        var delta = new PageDelta();
        delta.sent(page(true, "40 / 100", "12 / 20"), BUTTONS);
        // A progress bar tick: no rows are made, one value is set.
        assertNull(delta.next(commands(set("#Supply.Text", "40 / 100")), events()));
        var update = delta.next(commands(set("#Supply.Text", "39 / 100")), events());
        assertNotNull(update);
        assertFalse(update.whole());
        assertEquals(List.of(set("#Supply.Text", "39 / 100")), Arrays.asList(update.commands()));
        // The rows the client already has are still known: a full build with the same rows only moves values.
        assertNull(delta.next(page(false, "39 / 100", "12 / 20"), BUTTONS));
    }

    @Test void aChangedOrNewBindingIsSentAndAnUnchangedOneIsNot() {
        var delta = new PageDelta();
        delta.sent(page(true, "40 / 100"), BUTTONS);
        var rebound = events(press("#SupplyOne", "{\"Action\":\"SupplyOne\",\"Amount\":\"2\"}"), press("#Toggle", "{\"Action\":\"Toggle\"}"));
        var update = delta.next(page(false, "40 / 100"), rebound);
        assertNotNull(update);
        assertFalse(update.whole());
        assertEquals(0, update.commands().length);
        assertArrayEquals(rebound, update.events());
        assertNull(delta.next(page(false, "40 / 100"), rebound));
    }

    @Test void theLaterOfTwoValuesForOneSelectorCounts() {
        var delta = new PageDelta();
        delta.sent(commands(set("#Start.Disabled", "true"), set("#Start.Disabled", "false")), events());
        assertNull(delta.next(commands(set("#Start.Disabled", "false")), events()));
        var update = delta.next(commands(set("#Start.Disabled", "false"), set("#Start.Disabled", "true")), events());
        assertEquals(List.of(set("#Start.Disabled", "true")), Arrays.asList(update.commands()));
    }

    @Test void rowsAddedWithoutEmptyingTheirListAreNeverSkipped() {
        var delta = new PageDelta();
        // Sending this twice makes two rows, so it must go whole every time it is built.
        var adds = commands(append("#Log", "Line.ui"), set("#Log[0] #Text.Text", "hello"));
        delta.sent(adds, events());
        var update = delta.next(adds, events());
        assertNotNull(update);
        assertTrue(update.whole());
    }

    @Test void nothingIsKnownBeforeTheFirstSendSoTheFirstRefreshGoesWhole() {
        var delta = new PageDelta();
        assertFalse(delta.known());
        var first = page(false, "40 / 100", "12 / 20");
        assertTrue(delta.next(first, BUTTONS).whole());
        assertTrue(delta.known());
        assertNull(delta.next(first, BUTTONS));
        delta.reset();
        assertTrue(delta.next(first, BUTTONS).whole());
    }

    @Test void aSimilarlyNamedElementIsNotMistakenForARow() {
        var delta = new PageDelta();
        delta.sent(commands(set("#StockEmpty.Visible", "true"), set("#Stock.Visible", "false"), clear("#Stock")), events());
        // "#StockEmpty" and "#Stock.Visible" are not inside "#Stock"; leaving them out forces nothing.
        assertNull(delta.next(commands(clear("#Stock")), events()));
    }

    /** The quiet town case in numbers: ten minutes of a one second timer with nothing happening. */
    @Test void tenQuietMinutesSendNoUpdateAndABusyMinuteSendsOnlyWhatMoved() {
        var delta = new PageDelta();
        delta.sent(page(true, "40 / 100", "12 / 20"), BUTTONS);
        int sent = 0;
        for (int second = 0; second < 600; second++) if (delta.next(page(false, "40 / 100", "12 / 20"), BUTTONS) != null) sent++;
        assertEquals(0, sent);
        // A minute in which the patient gains one health every five seconds.
        int commandsSent = 0;
        for (int second = 1; second <= 60; second++) {
            var update = delta.next(page(false, "40 / 100", (12 + second / 5) + " / 20"), BUTTONS);
            if (update == null) continue;
            sent++; commandsSent += update.commands().length;
            assertFalse(update.whole());
        }
        assertEquals(12, sent);
        assertEquals(12, commandsSent);
    }
}
