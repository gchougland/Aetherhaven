package com.hexvane.aetherhaven.rts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.rts.RtsFieldCommand.Outcome;
import com.hexvane.aetherhaven.rts.RtsFieldFlagHudSystem.Panel;
import com.hexvane.aetherhaven.rts.ui.RtsCommandStatusHud;
import com.hexvane.aetherhaven.rts.ui.RtsCommandStatusHud.FieldGuards;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The Command Flag carried in hand, away from the Guard Command Post.
 *
 * <p>The flag used to work only inside a command post session: each of its four inputs returned early without a word
 * when no session was open, and an order given anyway was dropped on the next tick because its commander had no
 * session. These tests hold the rules that replaced that: what each press comes to, that every outcome has words, what
 * an order writes onto a guard, and how long it lasts.
 */
@Tag("entity")
class RtsFieldCommandTest {
    private static final Path LANG = Path.of("src/main/resources/Server/Languages/en-US/aetherhaven_rts.lang");
    private static final String LANG_PREFIX = "aetherhaven_rts.";

    private static Map<String, String> lang() throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        for (String line : Files.readAllLines(LANG)) {
            int eq = line.indexOf('=');
            if (eq > 0) {
                out.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
        }
        return out;
    }

    private static String text(Map<String, String> lang, String runtimeKey) {
        assertTrue(runtimeKey.startsWith(LANG_PREFIX), runtimeKey + " is not in the guard command lang file");
        String text = lang.get(runtimeKey.substring(LANG_PREFIX.length()));
        assertTrue(text != null && !text.isBlank(), "no English text for " + runtimeKey);
        assertFalse(text.contains(" - ") || text.contains("—") || text.contains("–"), runtimeKey + " uses a dash as a pause: " + text);
        return text;
    }

    @Test
    void theFlagInHandWithGuardsHiredAndGroundInViewSendsThem() {
        // The owner's press: flag in hand, no command post session, guards on duty, looking at the ground.
        assertEquals(Outcome.SENT, RtsFieldCommand.decideSend(true, 3, true));
        assertTrue(Outcome.SENT.acted());
        assertEquals(Outcome.STOPPED, RtsFieldCommand.decideStop(true, 3));
        assertEquals(Outcome.READY, RtsFieldCommand.decideReady(true, 3));
        assertEquals(Outcome.RELEASED, RtsFieldCommand.decideRelease(true, 3, 2));
        assertEquals(Outcome.ORDER_MOVE, RtsFieldCommand.orderModeOutcome(RtsOrderMode.ATTACK_MOVE.next()));
        assertEquals(Outcome.ORDER_ATTACK_MOVE, RtsFieldCommand.orderModeOutcome(RtsOrderMode.MOVE.next()));
    }

    @Test
    void aPressThatCannotActSaysWhy() {
        assertEquals(Outcome.NO_TOWN, RtsFieldCommand.decideSend(false, 0, true));
        assertEquals(Outcome.NO_TOWN, RtsFieldCommand.decideSend(false, 5, false));
        assertEquals(Outcome.NO_GUARDS, RtsFieldCommand.decideSend(true, 0, true));
        assertEquals(Outcome.NO_GUARDS, RtsFieldCommand.decideSend(true, 0, false), "no guards is the first thing to fix");
        assertEquals(Outcome.NO_GROUND, RtsFieldCommand.decideSend(true, 4, false));
        assertEquals(Outcome.NO_TOWN, RtsFieldCommand.decideStop(false, 0));
        assertEquals(Outcome.NO_GUARDS, RtsFieldCommand.decideStop(true, 0));
        assertEquals(Outcome.NO_TOWN, RtsFieldCommand.decideReady(false, 0));
        assertEquals(Outcome.NO_GUARDS, RtsFieldCommand.decideReady(true, 0));
        assertEquals(Outcome.NO_TOWN, RtsFieldCommand.decideRelease(false, 0, 0));
        assertEquals(Outcome.NO_GUARDS, RtsFieldCommand.decideRelease(true, 0, 0));
        assertEquals(Outcome.NONE_UNDER_ORDERS, RtsFieldCommand.decideRelease(true, 4, 0));
        for (Outcome refusal : new Outcome[] {Outcome.NO_TOWN, Outcome.NO_GUARDS, Outcome.NO_GROUND, Outcome.NONE_UNDER_ORDERS}) {
            assertFalse(refusal.acted(), refusal + " is a refusal");
        }
    }

    @Test
    void everyOutcomeHasItsOwnEnglishWords() throws IOException {
        Map<String, String> lang = lang();
        Set<String> seen = new HashSet<>();
        for (Outcome outcome : Outcome.values()) {
            String text = text(lang, outcome.messageKey());
            assertTrue(seen.add(text), outcome + " says the same as another outcome: " + text);
        }
        assertEquals("Look at the ground to send guards there.", text(lang, Outcome.NO_GROUND.messageKey()));
        assertTrue(text(lang, Outcome.NO_GUARDS.messageKey()).startsWith("No guards to command."));
        for (Outcome counted : new Outcome[] {Outcome.SENT, Outcome.STOPPED, Outcome.RELEASED, Outcome.READY}) {
            assertTrue(text(lang, counted.messageKey()).contains("{n}"), counted + " should say how many guards");
        }
        // The command post's own refusals for the flag, and the other tools held with no session.
        for (String key : new String[] {"errorNoSelection", "errorNeedsCommandPost"}) {
            text(lang, LANG_PREFIX + "aetherhaven.rts." + key);
        }
    }

    @Test
    void aSendWritesATravellingFlagOrderOntoTheGuard() {
        UUID commander = UUID.randomUUID();
        GuardRtsCommandState order = new GuardRtsCommandState();
        // Leftovers from an earlier focus fire order must not survive.
        order.setFocusFire(true);
        order.setTargetEntityUuid(UUID.randomUUID());
        order.setPhase(RtsCommandPhase.ENGAGING);
        RtsOrderService.applyMoveOrder(order, RtsOrderMode.MOVE, RtsCombatStance.DEFENSIVE, 24.5, 128, 24.5, commander, true);
        assertEquals(RtsCommandPhase.TRAVELING, order.getPhase());
        assertEquals(RtsOrderMode.MOVE, order.getOrderMode());
        assertEquals(RtsCombatStance.DEFENSIVE, order.getCombatStance());
        assertEquals(24.5, order.getHoldX());
        assertEquals(128, order.getHoldY());
        assertEquals(24.5, order.getHoldZ());
        assertEquals(commander, order.getCommanderPlayerUuid());
        assertTrue(order.isFieldOrder());
        assertFalse(order.isFocusFire());
        assertNull(order.getTargetEntityUuid());
        assertTrue(((GuardRtsCommandState) order.clone()).isFieldOrder(), "the flag mark is copied with the order");
        // The same call from the command post leaves no flag mark, so that order still ends with the session.
        RtsOrderService.applyMoveOrder(order, RtsOrderMode.ATTACK_MOVE, RtsCombatStance.AGGRESSIVE, 1, 2, 3, commander, false);
        assertFalse(order.isFieldOrder());
        assertFalse(new GuardRtsCommandState().isFieldOrder(), "an order loaded from an older save has no flag mark");
    }

    @Test
    void aStopHoldsTheGuardWhereItStands() {
        UUID commander = UUID.randomUUID();
        GuardRtsCommandState order = new GuardRtsCommandState();
        RtsOrderService.applyMoveOrder(order, RtsOrderMode.ATTACK_MOVE, RtsCombatStance.DEFENSIVE, 24.5, 128, 24.5, commander, true);
        RtsOrderService.applyStopOrder(order, RtsOrderMode.ATTACK_MOVE, 10.25, 128, 9.75, commander, true);
        assertEquals(RtsCommandPhase.HOLDING, order.getPhase());
        assertEquals(10.25, order.getHoldX());
        assertEquals(9.75, order.getHoldZ());
        assertTrue(order.isFieldOrder());
        assertEquals(commander, order.getCommanderPlayerUuid());
    }

    @Test
    void aFlagOrderLastsWhileItsCommanderIsInTheWorld() {
        // The fault: the commander of a flag order has no command post session, so the order was dropped next tick.
        assertTrue(RtsOrphanedGuardRecoverySystem.keepsOrder(true, true, false), "a flag order needs no session");
        assertFalse(RtsOrphanedGuardRecoverySystem.keepsOrder(true, false, false), "it ends when the commander leaves");
        // A command post order is unchanged: it lasts exactly as long as the session.
        assertTrue(RtsOrphanedGuardRecoverySystem.keepsOrder(false, true, true));
        assertFalse(RtsOrphanedGuardRecoverySystem.keepsOrder(false, true, false));
        assertFalse(RtsOrphanedGuardRecoverySystem.keepsOrder(false, false, true));
    }

    @Test
    void aHeldButtonIsOnePress() {
        assertTrue(RtsFieldCommand.repeats("send 1 2 3", 1000, "send 1 2 3", 1000 + RtsFieldCommand.REPEAT_MS - 1));
        assertFalse(RtsFieldCommand.repeats("send 1 2 3", 1000, "send 1 2 3", 1000 + RtsFieldCommand.REPEAT_MS), "a second press a moment later counts");
        assertFalse(RtsFieldCommand.repeats("send 1 2 3", 1000, "send 1 2 4", 1001), "another block is another order");
        assertFalse(RtsFieldCommand.repeats("send 1 2 3", 1000, "stop", 1001));
        assertFalse(RtsFieldCommand.repeats(null, 0, "ready", 5));
        assertFalse(RtsFieldCommand.repeats("ready", 1000, "ready", 900), "a clock that went back never swallows a press");
    }

    @Test
    void thePanelIsUpWhileTheFlagIsHeldAndTheCommandPostKeepsItsOwn() {
        assertEquals(Panel.FIELD, RtsFieldFlagHudSystem.panelFor(false, true, false));
        assertEquals(Panel.NONE, RtsFieldFlagHudSystem.panelFor(false, false, false), "put the flag away and the panel goes");
        assertEquals(Panel.NONE, RtsFieldFlagHudSystem.panelFor(false, true, true), "an open page hides it");
        assertEquals(Panel.COMMAND_POST, RtsFieldFlagHudSystem.panelFor(true, true, false));
        assertEquals(Panel.COMMAND_POST, RtsFieldFlagHudSystem.panelFor(true, false, false), "the session panel is never taken down from here");
        assertEquals(FieldGuards.NO_TOWN, RtsFieldFlagHudSystem.guardsState(false, 0));
        assertEquals(FieldGuards.NONE, RtsFieldFlagHudSystem.guardsState(true, 0));
        assertEquals(FieldGuards.LISTENING, RtsFieldFlagHudSystem.guardsState(true, 2));
        assertFalse(RtsFieldFlagHudSystem.holdsFlag(null));
    }

    @Test
    void thePanelIsSentAgainWheneverWhatItShowsChanges() {
        UUID town = UUID.randomUUID();
        long base = RtsFieldFlagHudSystem.signature(RtsOrderMode.ATTACK_MOVE, town, 3, 0);
        assertEquals(base, RtsFieldFlagHudSystem.signature(RtsOrderMode.ATTACK_MOVE, town, 3, 0));
        assertNotEquals(base, RtsFieldFlagHudSystem.signature(RtsOrderMode.MOVE, town, 3, 0), "order mode");
        assertNotEquals(base, RtsFieldFlagHudSystem.signature(RtsOrderMode.ATTACK_MOVE, town, 4, 0), "a guard was hired");
        assertNotEquals(base, RtsFieldFlagHudSystem.signature(RtsOrderMode.ATTACK_MOVE, town, 3, 3), "guards were sent");
        assertNotEquals(base, RtsFieldFlagHudSystem.signature(RtsOrderMode.ATTACK_MOVE, null, 3, 0), "left the town");
        assertNotEquals(
            RtsFieldFlagHudSystem.signature(RtsOrderMode.ATTACK_MOVE, null, 0, 0),
            RtsFieldFlagHudSystem.signature(RtsOrderMode.ATTACK_MOVE, town, 0, 0),
            "no town and no guards are different lines"
        );
    }

    @Test
    void theGuardsLineHasWordsForEveryState() throws IOException {
        Map<String, String> lang = lang();
        assertEquals("Found or join a town to command guards.", text(lang, RtsCommandStatusHud.fieldGuardsKey(FieldGuards.NO_TOWN, 0)));
        assertTrue(text(lang, RtsCommandStatusHud.fieldGuardsKey(FieldGuards.NONE, 0)).startsWith("No guards yet."));
        String listening = text(lang, RtsCommandStatusHud.fieldGuardsKey(FieldGuards.LISTENING, 0));
        assertTrue(listening.contains("{n}") && !listening.contains("{m}"), listening);
        String ordered = text(lang, RtsCommandStatusHud.fieldGuardsKey(FieldGuards.LISTENING, 2));
        assertTrue(ordered.contains("{n}") && ordered.contains("{m}"), ordered);
        assertTrue(text(lang, RtsCommandStatusHud.orderModeKey(RtsOrderMode.MOVE)).startsWith("Order:"));
        assertTrue(text(lang, RtsCommandStatusHud.orderModeKey(RtsOrderMode.ATTACK_MOVE)).startsWith("Order:"));
    }
}
