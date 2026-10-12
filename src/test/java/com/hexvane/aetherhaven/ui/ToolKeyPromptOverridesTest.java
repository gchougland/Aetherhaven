package com.hexvane.aetherhaven.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.ui.ToolKeyPromptOverrides.Change;
import com.hexvane.aetherhaven.ui.ToolKeyPromptOverrides.Listing;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.modules.interaction.Interactions;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

/**
 * The rule that gets every tool input into the game's key list.
 *
 * <p>The client lists one input of a held item by itself and the others only from the player's own interaction
 * overrides. These tests hold the two lists to what was read from the client, and the override edits to the one
 * promise that makes them safe: an entry is only ever the root the tool in use already binds, someone else's entry is
 * never touched, and nothing of ours is left behind.
 */
class ToolKeyPromptOverridesTest {
    /** Roots that carry a tool prompt in these tests. Everything else belongs to someone else. */
    private static final Predicate<String> OURS = id -> id.startsWith("tool:");

    private static Map<InteractionType, String> entries(Object... typeThenRoot) {
        Map<InteractionType, String> map = new EnumMap<>(InteractionType.class);
        for (int i = 0; i < typeThenRoot.length; i += 2) {
            map.put((InteractionType) typeThenRoot[i], (String) typeThenRoot[i + 1]);
        }
        return map;
    }

    private static List<Change> plan(Map<InteractionType, String> current, Map<InteractionType, String> desired) {
        return ToolKeyPromptOverrides.plan(current, false, desired, OURS);
    }

    private static Map<InteractionType, String> after(Map<InteractionType, String> current, List<Change> changes) {
        Map<InteractionType, String> out = new EnumMap<>(InteractionType.class);
        out.putAll(current);
        for (Change change : changes) {
            if (change.rootId() == null) {
                out.remove(change.type());
            } else {
                out.put(change.type(), change.rootId());
            }
        }
        return out;
    }

    @Test
    void theTwoListsAreTheOnesReadFromTheClient() {
        // HeldItemInputBindings walks a table of one input type, value 5.
        assertEquals(InteractionType.Ability4, ToolKeyPromptOverrides.HELD_ITEM_LISTED);
        assertEquals(5, ToolKeyPromptOverrides.HELD_ITEM_LISTED.getValue());
        // The input binding list walks a table of eight, values 0 to 7, over the player's overrides.
        List<InteractionType> list = ToolKeyPromptOverrides.CLIENT_OVERRIDE_LIST;
        assertEquals(8, list.size());
        for (int i = 0; i < list.size(); i++) {
            assertEquals(i, list.get(i).getValue(), list.get(i).name());
        }
        assertEquals(
            List.of(
                InteractionType.Primary,
                InteractionType.Secondary,
                InteractionType.Ability1,
                InteractionType.Ability2,
                InteractionType.Ability3,
                InteractionType.Ability4,
                InteractionType.Use,
                InteractionType.Pick
            ),
            list
        );
    }

    @Test
    void everyInputAToolCanBindIsListedExactlyOnce() {
        Set<InteractionType> mirrored = ToolKeyPromptOverrides.MIRRORED;
        assertEquals(
            EnumSet.of(
                InteractionType.Primary,
                InteractionType.Secondary,
                InteractionType.Ability1,
                InteractionType.Ability4,
                InteractionType.Use,
                InteractionType.Pick
            ),
            mirrored
        );
        assertTrue(ToolKeyPromptOverrides.CLIENT_OVERRIDE_LIST.containsAll(mirrored), "an override the client would not list");
        // The held item's own prompt is drawn for weapons only, and no tool is one, so this input is mirrored too.
        assertTrue(mirrored.contains(ToolKeyPromptOverrides.HELD_ITEM_LISTED));
        // The rune ability inputs are read from the ability slots, not from the item in hand.
        assertFalse(mirrored.contains(InteractionType.Ability2));
        assertFalse(mirrored.contains(InteractionType.Ability3));

        for (InteractionType type : mirrored) {
            assertEquals(Listing.PLAYER_OVERRIDE, ToolKeyPromptOverrides.listingOf(type), type.name());
        }
        assertNull(ToolKeyPromptOverrides.listingOf(InteractionType.Ability2));
        assertNull(ToolKeyPromptOverrides.listingOf(InteractionType.Held));
        // Every input a tool row can stand for has a list.
        for (ToolKeybindSlot slot : ToolKeybindSlot.values()) {
            if (slot.hasKeyPrompt()) {
                assertTrue(ToolKeyPromptOverrides.listingOf(slot.interactionType()) != null, slot.name());
            }
        }
    }

    @Test
    void aToolMirrorsItsOwnPromptedRootsAndNothingElse() {
        Map<InteractionType, String> item = entries(
            InteractionType.Primary, "tool:primary",
            InteractionType.Secondary, "shared:does-nothing",
            InteractionType.Ability1, "tool:ability1",
            InteractionType.Ability4, "tool:ability4",
            InteractionType.Use, "tool:use",
            InteractionType.Pick, "tool:pick",
            InteractionType.Held, "tool:held"
        );
        assertEquals(
            entries(
                InteractionType.Primary, "tool:primary",
                InteractionType.Ability1, "tool:ability1",
                InteractionType.Ability4, "tool:ability4",
                InteractionType.Use, "tool:use",
                InteractionType.Pick, "tool:pick"
            ),
            ToolKeyPromptOverrides.mirrored(item, OURS)
        );
        assertTrue(ToolKeyPromptOverrides.mirrored(Map.of(), OURS).isEmpty());
    }

    @Test
    void takingAToolInHandAddsItsRootsAndASecondPassChangesNothing() {
        Map<InteractionType, String> desired = entries(InteractionType.Primary, "tool:p", InteractionType.Use, "tool:u");
        List<Change> changes = plan(Map.of(), desired);
        assertEquals(desired, after(Map.of(), changes));
        assertTrue(plan(desired, desired).isEmpty());
    }

    @Test
    void puttingAToolAwayTakesEveryEntryOff() {
        Map<InteractionType, String> current = entries(InteractionType.Primary, "tool:p", InteractionType.Pick, "tool:k");
        assertTrue(after(current, plan(current, Map.of())).isEmpty());
    }

    @Test
    void switchingToAnotherToolNeverLeavesTheOldToolsRoots() {
        Map<InteractionType, String> current = entries(
            InteractionType.Primary, "tool:shovel-p",
            InteractionType.Use, "tool:shovel-u",
            InteractionType.Pick, "tool:shovel-k"
        );
        Map<InteractionType, String> desired = entries(InteractionType.Primary, "tool:flag-p", InteractionType.Secondary, "tool:flag-s");
        assertEquals(desired, after(current, plan(current, desired)));
    }

    @Test
    void someoneElsesOverrideIsNeverReplacedOrRemoved() {
        // A game mode or the creative eraser owns Primary. It won over the held item before, and it still must.
        Map<InteractionType, String> current = entries(InteractionType.Primary, "vanilla:eraser", InteractionType.Use, "tool:old-u");
        Map<InteractionType, String> desired = entries(InteractionType.Primary, "tool:p", InteractionType.Use, "tool:u");
        Map<InteractionType, String> result = after(current, plan(current, desired));
        assertEquals("vanilla:eraser", result.get(InteractionType.Primary));
        assertEquals("tool:u", result.get(InteractionType.Use));
        // Put away: only the other owner's entry remains.
        assertEquals(entries(InteractionType.Primary, "vanilla:eraser"), after(result, plan(result, Map.of())));
        // An entry on an input this class never writes is also left alone.
        Map<InteractionType, String> other = entries(InteractionType.Ability2, "vanilla:rune");
        assertTrue(plan(other, desired).stream().noneMatch(c -> c.type() == InteractionType.Ability2));
    }

    @Test
    void nothingIsAddedWhileEveryInputIsOverridden() {
        // Spectators: the held item does nothing, so the tool must not start working through an override.
        Map<InteractionType, String> current = entries(InteractionType.Primary, "vanilla:spectate-prev", InteractionType.Use, "tool:u");
        Map<InteractionType, String> desired = entries(InteractionType.Secondary, "tool:s", InteractionType.Use, "tool:u");
        Map<InteractionType, String> result = after(current, ToolKeyPromptOverrides.plan(current, true, desired, OURS));
        assertEquals(entries(InteractionType.Primary, "vanilla:spectate-prev"), result);
    }

    @Test
    void onlyMirroredInputsAreEverWritten() {
        Map<InteractionType, String> desired = entries(
            InteractionType.Ability4, "tool:a4",
            InteractionType.Ability2, "tool:a2",
            InteractionType.Held, "tool:h",
            InteractionType.Primary, "tool:p"
        );
        assertEquals(
            entries(InteractionType.Primary, "tool:p", InteractionType.Ability4, "tool:a4"),
            after(Map.of(), plan(Map.of(), desired)));
        // One of our roots found on an input we never write is taken off, wherever it came from.
        Map<InteractionType, String> stray = entries(InteractionType.Ability2, "tool:a2");
        assertTrue(after(stray, plan(stray, desired)).get(InteractionType.Ability2) == null);
    }

    @Test
    void theEditsLandOnTheEngineComponentAndFlagItForSending() {
        Interactions interactions = new Interactions();
        interactions.consumeNetworkOutdated();
        Map<InteractionType, String> desired = entries(InteractionType.Primary, "tool:p", InteractionType.Use, "tool:u");
        assertTrue(ToolKeyPromptOverrides.apply(interactions, plan(interactions.getInteractions(), desired)));
        assertEquals(desired, interactions.getInteractions());
        assertTrue(interactions.consumeNetworkOutdated(), "the client is only told when the component is flagged");
        assertFalse(ToolKeyPromptOverrides.apply(interactions, plan(interactions.getInteractions(), desired)));
        assertFalse(interactions.consumeNetworkOutdated());
        ToolKeyPromptOverrides.apply(interactions, plan(interactions.getInteractions(), Map.of()));
        assertTrue(interactions.isEmpty(), "an empty component is removed, as the engine's own override code does");
        assertTrue(interactions.consumeNetworkOutdated());
    }
}
