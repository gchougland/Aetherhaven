package com.hexvane.aetherhaven.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.MovementStates;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Data audit for the keys Aetherhaven's held tools bind.
 *
 * <p>The engine decodes an item's {@code Interactions} as a map keyed by {@link InteractionType}, so a key is only
 * accepted when it names a constant of the engine on the classpath. Since Hytale 0.7.0-pre.5 the engine also takes
 * {@code Ability2} and {@code Ability3} away from held items (they run the rune ability slots), so an item that binds
 * them declares an action no key can reach.
 */
class ToolItemKeyBindingAuditTest {
    private static final Path CORE_ITEMS = Path.of("src/main/resources/Server/Item/Items");
    private static final Path CORE_UI = Path.of("src/main/resources/Common/UI/Custom");
    private static final Path SUBPLUGINS = Path.of("subplugin-assets");

    /** Tools that carry a second extra action, and the interaction that action must reach. */
    private static final Map<String, String> SECOND_ACTION = Map.of(
        "Aetherhaven_Path_Tool", "AetherhavenPathToolWidthCycle",
        "Aetherhaven_Plot_Creator_Staff", "AetherhavenPlotCreatorStepForward",
        "Aetherhaven_Plot_Creator_Staff_Bounds", "AetherhavenPlotCreatorStepForward",
        "Aetherhaven_Building_Editor_Staff", "AetherhavenBuildingEditorStepForward",
        "Aetherhaven_Patrol_Wand", "AetherhavenPatrolWandNewRoute",
        "Aetherhaven_Rts_Flag", "AetherhavenRtsFlagStop"
    );

    @Test
    void everyItemInteractionKeyIsAnEngineInteractionType() throws IOException {
        Set<String> engineTypes = new TreeSet<>();
        for (InteractionType type : InteractionType.values()) {
            engineTypes.add(type.name());
        }
        List<String> problems = new ArrayList<>();
        Map<String, JsonObject> items = itemsWithInteractions();
        assertTrue(items.size() >= 10, "expected to find the mod's items, found " + items.size());
        for (Map.Entry<String, JsonObject> item : items.entrySet()) {
            for (String key : item.getValue().getAsJsonObject("Interactions").keySet()) {
                if (!engineTypes.contains(key)) {
                    problems.add(item.getKey() + " binds unknown interaction type " + key);
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void noItemBindsAnInputTheGameGivesToRuneAbilities() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, JsonObject> item : itemsWithInteractions().entrySet()) {
            for (InteractionType owned : ToolAbilityKeys.RUNE_OWNED) {
                if (item.getValue().getAsJsonObject("Interactions").has(owned.name())) {
                    problems.add(item.getKey() + " binds " + owned.name()
                        + ", which runs a rune ability slot instead of the held item. Use "
                        + ToolAbilityKeys.FIRST.name() + " or " + ToolAbilityKeys.SECOND.name() + ".");
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void toolsWithASecondActionBindItToTheSecondToolKey() throws IOException {
        assertEquals(InteractionType.Ability4, ToolAbilityKeys.SECOND);
        assertEquals(InteractionType.Ability1, ToolAbilityKeys.FIRST);
        assertFalse(ToolAbilityKeys.RUNE_OWNED.contains(ToolAbilityKeys.FIRST));
        assertFalse(ToolAbilityKeys.RUNE_OWNED.contains(ToolAbilityKeys.SECOND));
        assertFalse(ToolAbilityKeys.RUNE_OWNED.contains(ToolAbilityKeys.THIRD));
        Map<String, JsonObject> items = itemsWithInteractions();
        for (Map.Entry<String, String> expected : SECOND_ACTION.entrySet()) {
            JsonObject item = items.get(expected.getKey());
            assertNotNull(item, expected.getKey() + " not found");
            JsonObject interactions = item.getAsJsonObject("Interactions");
            assertTrue(interactions.has(ToolAbilityKeys.FIRST.name()), expected.getKey() + " lost its first extra key");
            JsonElement second = interactions.get(ToolAbilityKeys.SECOND.name());
            assertNotNull(second, expected.getKey() + " has no " + ToolAbilityKeys.SECOND.name() + " binding");
            assertEquals(
                expected.getValue(),
                second.getAsJsonObject().getAsJsonArray("Interactions").get(0).getAsJsonObject().get("Type").getAsString(),
                expected.getKey()
            );
        }
    }

    @Test
    void hudKeySlotsPointAtBindingsAHeldItemStillOwns() throws IOException {
        for (ToolKeybindSlot slot : ToolKeybindSlot.values()) {
            assertFalse(slot.interactionType() == InteractionType.Ability2, slot + " is the rune ability 1 input");
            assertFalse(slot.interactionType() == InteractionType.Ability3, slot + " is the rune ability 2 input");
        }
        assertEquals(InteractionType.Ability1, ToolKeybindSlot.ABILITY1.interactionType());
        assertEquals(InteractionType.Ability4, ToolKeybindSlot.ABILITY2.interactionType());
        // The third action is the first input used while crouching.
        assertEquals(InteractionType.Ability1, ToolKeybindSlot.ABILITY3.interactionType());
        assertEquals(ToolKeybindSlot.ABILITY1, ToolKeybindSlot.ABILITY3.chordBase());
        for (ToolKeybindSlot slot : ToolKeybindSlot.values()) {
            if (slot != ToolKeybindSlot.ABILITY3) {
                assertEquals(null, slot.chordBase(), slot.name());
            }
        }
        List<String> problems = new ArrayList<>();
        for (Path ui : uiFiles()) {
            String text = Files.readString(ui);
            if (text.contains("\"Ability2ItemAction\"") || text.contains("\"Ability3ItemAction\"")) {
                problems.add(ui + " shows a rune ability key as a tool key");
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void crouchingTurnsTheFirstKeyIntoTheThirdAction() {
        assertFalse(ToolAbilityKeys.thirdActionHeld((MovementStates) null));
        MovementStates states = new MovementStates();
        assertFalse(ToolAbilityKeys.thirdActionHeld(states));
        states.crouching = true;
        assertTrue(ToolAbilityKeys.thirdActionHeld(states));
        states.crouching = false;
        states.forcedCrouching = true;
        assertTrue(ToolAbilityKeys.thirdActionHeld(states));
    }

    private static Map<String, JsonObject> itemsWithInteractions() throws IOException {
        Map<String, JsonObject> out = new TreeMap<>();
        for (Path root : itemRoots()) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                    JsonElement parsed = JsonParser.parseString(Files.readString(file));
                    if (!parsed.isJsonObject()) {
                        continue;
                    }
                    JsonElement interactions = parsed.getAsJsonObject().get("Interactions");
                    if (interactions != null && interactions.isJsonObject()) {
                        String id = file.getFileName().toString().replace(".json", "");
                        assertEquals(null, out.put(id, parsed.getAsJsonObject()), "two item files named " + id);
                    }
                }
            }
        }
        return out;
    }

    private static List<Path> itemRoots() throws IOException {
        List<Path> roots = new ArrayList<>();
        roots.add(CORE_ITEMS);
        try (Stream<Path> packs = Files.list(SUBPLUGINS)) {
            for (Path pack : packs.toList()) {
                Path items = pack.resolve("Server/Item/Items");
                if (Files.isDirectory(items)) {
                    roots.add(items);
                }
            }
        }
        return roots;
    }

    private static List<Path> uiFiles() throws IOException {
        List<Path> roots = new ArrayList<>();
        roots.add(CORE_UI);
        try (Stream<Path> packs = Files.list(SUBPLUGINS)) {
            for (Path pack : packs.toList()) {
                Path ui = pack.resolve("Common/UI/Custom");
                if (Files.isDirectory(ui)) {
                    roots.add(ui);
                }
            }
        }
        List<Path> out = new ArrayList<>();
        for (Path root : roots) {
            try (Stream<Path> files = Files.walk(root)) {
                out.addAll(files.filter(p -> p.getFileName().toString().endsWith(".ui")).toList());
            }
        }
        return out;
    }
}
