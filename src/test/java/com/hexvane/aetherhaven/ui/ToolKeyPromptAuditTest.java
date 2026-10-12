package com.hexvane.aetherhaven.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.protocol.InteractionType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The player's own keys must be what every tool shows, on any server.
 *
 * <p>Only the client knows the bindings. In Hytale 0.7.0 the client puts the player's key beside a root interaction that
 * sets {@code HudInputBindingEntry}: for one input of the held item, and for the others through the player's own
 * interaction overrides ({@link ToolKeyPromptOverrides}). These tests hold the data to that: every input a tool binds
 * names an action there and falls under one of the client's two lists, the name exists in English, and no tool text
 * names a key, because any key it named would be wrong for a player who rebound it.
 */
class ToolKeyPromptAuditTest {
    private static final Path CORE = Path.of("src/main/resources");
    private static final Path SUBPLUGINS = Path.of("subplugin-assets");
    private static final Path LANG = CORE.resolve("Server/Languages/en-US");
    private static final String FIELD = "HudInputBindingEntry";

    /** The inputs the client lists a key prompt for and a tool can bind. */
    private static final Set<InteractionType> TOOL_INPUTS = EnumSet.of(
        InteractionType.Primary,
        InteractionType.Secondary,
        InteractionType.Use,
        InteractionType.Ability1,
        InteractionType.Ability4,
        InteractionType.Pick
    );

    private record Tool(ToolKeyPrompts prompts, Set<InteractionType> unprompted) {
        Tool(ToolKeyPrompts prompts) {
            this(prompts, EnumSet.noneOf(InteractionType.class));
        }
    }

    /** Every held tool with an overlay, and the inputs it binds that do nothing worth a prompt. */
    private static final Map<String, Tool> TOOLS = tools();

    private static Map<String, Tool> tools() {
        Map<String, Tool> t = new LinkedHashMap<>();
        t.put("Aetherhaven_Path_Tool", new Tool(ToolKeyPrompts.PATH_TOOL));
        t.put("Aetherhaven_Plot_Creator_Staff", new Tool(ToolKeyPrompts.PLOT_CREATOR));
        t.put("Aetherhaven_Plot_Creator_Staff_Bounds", new Tool(ToolKeyPrompts.PLOT_CREATOR));
        t.put("Aetherhaven_Building_Editor_Staff", new Tool(ToolKeyPrompts.PLOT_CREATOR));
        t.put("Aetherhaven_Patrol_Wand", new Tool(ToolKeyPrompts.PATROL_WAND));
        t.put("Aetherhaven_Building_Staff", new Tool(ToolKeyPrompts.BUILDING_STAFF));
        t.put("Aetherhaven_Building_Staff_Iron", new Tool(ToolKeyPrompts.BUILDING_STAFF));
        t.put("Aetherhaven_Building_Staff_Cobalt", new Tool(ToolKeyPrompts.BUILDING_STAFF));
        t.put("Aetherhaven_Building_Staff_Thorium", new Tool(ToolKeyPrompts.BUILDING_STAFF));
        t.put("Aetherhaven_Building_Staff_Adamantite", new Tool(ToolKeyPrompts.BUILDING_STAFF));
        t.put("Aetherhaven_Poi_Tool", new Tool(ToolKeyPrompts.POI_TOOL));
        t.put("Aetherhaven_Rts_Flag", new Tool(ToolKeyPrompts.RTS_FLAG));
        t.put("Aetherhaven_Rts_Sword", new Tool(ToolKeyPrompts.RTS_SWORD));
        t.put("Aetherhaven_Rts_Free", new Tool(ToolKeyPrompts.RTS_FREE));
        // These command tools bind the shared secondary interaction but give it nothing to do.
        Set<InteractionType> noSecondary = EnumSet.of(InteractionType.Secondary);
        t.put("Aetherhaven_Rts_Stance_Banner", new Tool(ToolKeyPrompts.RTS_STANCE, noSecondary));
        t.put("Aetherhaven_Rts_Select_All", new Tool(ToolKeyPrompts.RTS_SELECT, noSecondary));
        t.put("Aetherhaven_Rts_Select_Knight", new Tool(ToolKeyPrompts.RTS_SELECT, noSecondary));
        t.put("Aetherhaven_Rts_Select_Archer", new Tool(ToolKeyPrompts.RTS_SELECT, noSecondary));
        t.put("Aetherhaven_Rts_Select_Mage", new Tool(ToolKeyPrompts.RTS_SELECT, noSecondary));
        t.put("Aetherhaven_Rts_Exit", new Tool(ToolKeyPrompts.RTS_EXIT));
        return t;
    }

    /** Key names a player could have rebound. A hit means the text would be wrong for that player. */
    private static final Pattern KEY_NAME = Pattern.compile(
        "\\b(LMB|RMB|MMB|WASD)\\b"
            + "|[Ll]eft[- ]click|[Rr]ight[- ]click|[Mm]iddle[- ]click|[Mm]iddle mouse|[Mm]ouse wheel"
            + "|\\b[Pp]ress(es|ing)? (F|Q|E|R|H)\\b"
            + "|\\((F|Q|E|R|H)\\)|\\[(F|Q|E|R|H)\\]"
            + "|^(F|Q|E|R|H)\\b|\\b(F|Q|E|R|H)( to | opens| names| starts| clears| saves| switches| cycles| key\\b|:)"
            + "|\\b(Ctrl|CTRL|Esc|ESC|Escape|Spacebar|Tab key|[Nn]umber keys?)\\b"
            + "|\\b(Shift|SHIFT)( \\+|-click| click| key)"
    );

    @Test
    void everyInputAToolBindsNamesItsActionForTheGamesKeyList() throws IOException {
        Map<String, JsonObject> items = jsonById("Server/Item/Items");
        Map<String, JsonObject> roots = jsonById("Server/Item/RootInteractions");
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, Tool> tool : TOOLS.entrySet()) {
            JsonObject item = items.get(tool.getKey());
            assertNotNull(item, tool.getKey() + " not found");
            JsonObject interactions = item.getAsJsonObject("Interactions");
            for (InteractionType type : TOOL_INPUTS) {
                JsonElement bound = interactions.get(type.name());
                if (bound == null) {
                    continue;
                }
                JsonObject root = bound.isJsonObject() ? bound.getAsJsonObject() : roots.get(bound.getAsString());
                if (root == null) {
                    problems.add(tool.getKey() + " " + type + ": root interaction " + bound + " not found");
                    continue;
                }
                JsonElement entry = root.get(FIELD);
                if (tool.getValue().unprompted().contains(type)) {
                    if (entry != null) {
                        problems.add(tool.getKey() + " " + type + ": listed as doing nothing, but has a prompt");
                    }
                    continue;
                }
                String expected = tool.getValue().prompts().promptKey(type);
                if (entry == null) {
                    problems.add(tool.getKey() + " binds " + type + " without " + FIELD
                        + ", so the game shows no key for it. Expected \"" + expected + "\".");
                } else if (!expected.equals(entry.getAsString())) {
                    problems.add(tool.getKey() + " " + type + ": " + FIELD + " is \"" + entry.getAsString()
                        + "\" but the overlay rows use \"" + expected + "\"");
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void theClientHasAListForEveryPromptOfEveryTool() throws IOException {
        // The client lists one input from the held item itself and the rest from the player's overrides. A prompt on
        // any other input would never be drawn, which is how five of the shovel's six prompts went missing.
        assertEquals(
            TOOL_INPUTS,
            EnumSet.copyOf(Stream.concat(
                ToolKeyPromptOverrides.MIRRORED.stream(),
                Stream.of(ToolKeyPromptOverrides.HELD_ITEM_LISTED)
            ).toList())
        );
        Map<String, JsonObject> items = jsonById("Server/Item/Items");
        List<String> problems = new ArrayList<>();
        int besideAbilitySlots = 0;
        int throughOverrides = 0;
        for (Map.Entry<String, Tool> tool : TOOLS.entrySet()) {
            JsonObject interactions = items.get(tool.getKey()).getAsJsonObject("Interactions");
            for (String bound : interactions.keySet()) {
                InteractionType type = InteractionType.valueOf(bound);
                boolean prompted = TOOL_INPUTS.contains(type) && !tool.getValue().unprompted().contains(type);
                ToolKeyPromptOverrides.Listing listing = ToolKeyPromptOverrides.listingOf(type);
                if (prompted && listing == ToolKeyPromptOverrides.Listing.HELD_ITEM) {
                    besideAbilitySlots++;
                } else if (prompted && listing == ToolKeyPromptOverrides.Listing.PLAYER_OVERRIDE) {
                    throughOverrides++;
                } else if (ToolKeyPromptOverrides.CLIENT_OVERRIDE_LIST.contains(type) && !tool.getValue().unprompted().contains(type)) {
                    problems.add(tool.getKey() + " binds " + type + ", an input a player presses, and the game will list no key for it");
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
        assertTrue(besideAbilitySlots == 0 && throughOverrides > 0, besideAbilitySlots + " / " + throughOverrides);
        // While a weapon is held the client draws only the item's own prompt and hides the whole key list, so a tool
        // filed under Items.Weapons shows one line. Seen in game with the Command Flag.
        for (String tool : TOOLS.keySet()) {
            JsonElement categories = items.get(tool).get("Categories");
            assertFalse(categories != null && categories.toString().contains("Items.Weapons"), tool + " is filed as a weapon");
        }
    }

    @Test
    void theOverridesAreKeptInStepBySystemsThatAreRegistered() throws IOException {
        String bootstrap = Files.readString(
            Path.of("src/main/java/com/hexvane/aetherhaven/plugin/AetherhavenCoreBootstrap.java"), StandardCharsets.UTF_8);
        assertTrue(bootstrap.contains("ToolKeyPromptOverrides.register(plugin)"), "nothing would mirror a tool's inputs");
        String overrides = Files.readString(
            Path.of("src/main/java/com/hexvane/aetherhaven/ui/ToolKeyPromptOverrides.java"), StandardCharsets.UTF_8);
        for (String system : List.of("Tick", "SlotChange", "StripOnWorldChange", "UseEntityGuard")) {
            assertTrue(overrides.contains("new ToolKeyPromptOverrideSystems." + system + "()"), system + " is not registered");
        }
    }

    @Test
    void aPlayerCanPrintWhatTheServerHoldsForTheirKeyList() throws IOException {
        // "ah test mykeys": the one way to see, for a real player, which link of the key list is broken.
        String tests = Files.readString(
            Path.of("src/main/java/com/hexvane/aetherhaven/command/AetherhavenTestCommand.java"), StandardCharsets.UTF_8);
        assertTrue(tests.contains("new AetherhavenMyKeysTestCommand()"), "ah test mykeys is not registered");
        String command = Files.readString(
            Path.of("src/main/java/com/hexvane/aetherhaven/command/AetherhavenMyKeysTestCommand.java"), StandardCharsets.UTF_8);
        assertTrue(command.contains("super(\"mykeys\""), "the command the owner was told to run was renamed");
        for (String shown : List.of("desired(ref, store)", "pending(ref, store)", "visibleTo.containsKey(ref)", "isUsingToolsItem()")) {
            assertTrue(command.contains(shown), "the printout no longer shows " + shown);
        }
    }

    @Test
    void theInServerCheckCoversTheSameTools() throws IOException {
        // "ah test toolkeys" walks ToolKeyPrompts.items() on a running server. It must know every tool audited here.
        Map<String, JsonObject> items = jsonById("Server/Item/Items");
        for (Map.Entry<String, Tool> tool : TOOLS.entrySet()) {
            assertEquals(tool.getValue().prompts(), ToolKeyPrompts.items().get(tool.getKey()), tool.getKey());
        }
        for (Map.Entry<String, ToolKeyPrompts> known : ToolKeyPrompts.items().entrySet()) {
            if (TOOLS.containsKey(known.getKey())) {
                continue;
            }
            // Anything else must be a tool that inherits every binding from an audited tool of the same key set.
            JsonObject item = items.get(known.getKey());
            assertNotNull(item, known.getKey() + " not found");
            assertFalse(item.has("Interactions"), known.getKey() + " binds its own inputs and must be audited");
            Tool parent = TOOLS.get(item.get("Parent").getAsString());
            assertNotNull(parent, known.getKey() + " inherits from a tool that is not audited");
            assertEquals(parent.prompts(), known.getValue(), known.getKey());
        }
    }

    @Test
    void everyToolItemWithAnAbilityKeyIsAudited() throws IOException {
        // A new tool that binds an extra key must be added to TOOLS, so it gets prompts too.
        List<String> unaudited = new ArrayList<>();
        for (Map.Entry<String, JsonObject> item : jsonById("Server/Item/Items").entrySet()) {
            JsonElement interactions = item.getValue().get("Interactions");
            if (interactions == null || !interactions.isJsonObject()) {
                continue;
            }
            boolean extraKey = interactions.getAsJsonObject().has(ToolAbilityKeys.FIRST.name())
                || interactions.getAsJsonObject().has(ToolAbilityKeys.SECOND.name())
                || interactions.getAsJsonObject().has(InteractionType.Pick.name());
            if (extraKey && !TOOLS.containsKey(item.getKey())) {
                unaudited.add(item.getKey());
            }
        }
        assertTrue(unaudited.isEmpty(), "tools with extra keys but no key prompts: " + unaudited);
    }

    @Test
    void everyActionNameExistsInEnglishAndIsUsed() throws IOException {
        Map<String, String> names = langFile(ToolKeyPrompts.LANG_FILE + ".lang");
        Set<String> used = new TreeSet<>();
        for (Tool tool : TOOLS.values()) {
            for (InteractionType type : TOOL_INPUTS) {
                used.add(tool.prompts().promptKey(type));
            }
        }
        Map<String, JsonObject> items = jsonById("Server/Item/Items");
        Map<String, JsonObject> roots = jsonById("Server/Item/RootInteractions");
        Set<String> referenced = new TreeSet<>();
        collectEntries(items.values(), referenced);
        collectEntries(roots.values(), referenced);
        String prefix = ToolKeyPrompts.LANG_FILE + ".";
        for (String key : referenced) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            assertTrue(names.containsKey(key.substring(prefix.length())), key + " has no English text");
            assertTrue(used.contains(key), key + " is not the key of any audited tool input");
        }
        // Every slot, plain game inputs included, has the game's own name for it as a fallback label.
        for (ToolKeybindSlot slot : ToolKeybindSlot.values()) {
            String generic = ToolKeyPrompts.GENERIC.promptKey(slot);
            assertTrue(names.containsKey(generic.substring(prefix.length())), generic + " has no English text");
        }
        assertTrue(names.containsKey(ToolKeyPrompts.WHERE_KEYS_KEY.substring(prefix.length())));
        assertNotNull(names.get(ToolKeyPrompts.CROUCH_MODIFIER_KEY.substring(prefix.length())), "the chord row has no English text");
        // Every tool action name in the file belongs to an input some tool binds.
        for (String key : names.keySet()) {
            String full = prefix + key;
            boolean shared = full.equals(ToolKeyPrompts.WHERE_KEYS_KEY)
                || full.equals(ToolKeyPrompts.CROUCH_MODIFIER_KEY)
                || full.startsWith(prefix + "aetherhaven.toolKeys." + ToolKeyPrompts.GENERIC.id() + ".");
            assertTrue(shared || referenced.contains(full), full + " is not set on any root interaction");
        }
    }

    @Test
    void noToolTextNamesAKey() throws IOException {
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> line : langFile(ToolKeyPrompts.LANG_FILE + ".lang").entrySet()) {
            if (KEY_NAME.matcher(line.getValue()).find()) {
                problems.add(ToolKeyPrompts.LANG_FILE + ".lang " + line.getKey() + " names a key: " + line.getValue());
            }
        }
        // The text of every overlay hint row, and the item descriptions and chat lines of the tools.
        Map<String, List<String>> hintKeys = new LinkedHashMap<>();
        hintKeys.put("aetherhaven_items.lang", List.of(
            "aetherhaven.pathTool.", "aetherhaven.patrolWand.", "aetherhaven.poiTool.",
            "items.Aetherhaven_Path_Tool.", "items.Aetherhaven_Patrol_Wand.", "items.Aetherhaven_Poi_Tool.",
            "items.Aetherhaven_Plot_Creator_Staff.", "items.Aetherhaven_Building_Editor_Staff."));
        hintKeys.put("aetherhaven_plot_creator.lang", List.of("aetherhaven.plotcreator."));
        hintKeys.put("aetherhaven_rts.lang", List.of("aetherhaven.rts."));
        hintKeys.put("aetherhaven_shop.lang", List.of("aetherhaven.shop."));
        hintKeys.put("aetherhaven_plot_move.lang", List.of("aetherhaven.ui.plotplacement.tips", "aetherhaven.ui.charterrelocation.tips"));
        hintKeys.put("aetherhaven_misc.lang", List.of("items.Aetherhaven_Building_Staff"));
        for (Map.Entry<String, List<String>> file : hintKeys.entrySet()) {
            for (Map.Entry<String, String> line : langFile(file.getKey()).entrySet()) {
                boolean hint = file.getValue().stream().anyMatch(line.getKey()::startsWith);
                if (hint && KEY_NAME.matcher(line.getValue()).find()) {
                    problems.add(file.getKey() + " " + line.getKey() + " names a key: " + line.getValue());
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
    }

    @Test
    void theKeyPatternCatchesWhatItShould() {
        for (String wrong : List.of(
            "Press Q to change mode", "LMB", "Right click a point", "F opens the guard list", "Use (F) to extend",
            "Crouch and press Q", "Shift + click", "hold Ctrl", "Q: switch mode", "H starts fresh", "Esc closes")) {
            assertTrue(KEY_NAME.matcher(wrong).find(), wrong);
        }
        for (String fine : List.of(
            "Next mode", "Crouch + Next mode", "Use the stall to buy", "Hold Select guards and drag a box",
            "Shift view one step toward -X", "Fly up and fly down to zoom", "Sprint while selecting", "Open or confirm")) {
            assertFalse(KEY_NAME.matcher(fine).find(), fine);
        }
    }

    @Test
    void overlaysNeverUseAnElementOnlyTheBuilderLegendFillsIn() throws IOException {
        // The client resolves a HotkeyLabel only for labels inside a builder tool's legend. In a custom HUD or page it
        // stays empty, and an ActionButton's KeyBindingLabel is fixed text. Neither can show a player's key here.
        List<String> problems = new ArrayList<>();
        for (Path ui : uiFiles()) {
            String text = Files.readString(ui, StandardCharsets.UTF_8);
            for (String element : List.of("HotkeyLabel", "InputBindingKey", "KeyBindingLabel")) {
                if (text.contains(element)) {
                    problems.add(ui + " uses " + element);
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
        Path custom = CORE.resolve("Common/UI/Custom");
        String row = Files.readString(custom.resolve(ToolHudHotkeyRows.ROW_UI), StandardCharsets.UTF_8);
        assertTrue(row.contains("Label #KeyLabel") && row.contains("Label #DescLabel"), "the hint row lost an id Java addresses");
        String chordRow = Files.readString(custom.resolve(ToolHudHotkeyRows.MODIFIER_ROW_UI), StandardCharsets.UTF_8);
        assertTrue(
            chordRow.contains("Label #ModifierLabel") && chordRow.contains("Label #KeyLabel") && chordRow.contains("Label #DescLabel"),
            "the chord row lost an id Java addresses");
        assertTrue(Files.isRegularFile(custom.resolve(ToolHudHotkeyRows.INFO_ROW_UI)));
    }

    @Test
    void noServerCodeReadsAPlayersSettingsFile() throws IOException {
        // The file on the machine that runs the server belongs to whoever runs it, never to the player being shown a key.
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java/com/hexvane/aetherhaven/ui"))) {
            for (Path file : files.filter(p -> p.getFileName().toString().startsWith("Tool")).toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                if (text.contains("Settings.json") || text.contains("getenv(") || text.contains("Scancode")) {
                    problems.add(file.toString());
                }
            }
        }
        assertEquals(List.of(), problems);
    }

    private static void collectEntries(Iterable<JsonObject> assets, Set<String> out) {
        for (JsonObject asset : assets) {
            collectEntries(asset, out);
        }
    }

    private static void collectEntries(JsonElement element, Set<String> out) {
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> field : element.getAsJsonObject().entrySet()) {
                if (field.getKey().equals(FIELD) && field.getValue().isJsonPrimitive()) {
                    out.add(field.getValue().getAsString());
                } else {
                    collectEntries(field.getValue(), out);
                }
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectEntries(child, out);
            }
        }
    }

    private static Map<String, String> langFile(String name) throws IOException {
        Map<String, String> out = new LinkedHashMap<>();
        for (String line : Files.readAllLines(LANG.resolve(name), StandardCharsets.UTF_8)) {
            String trimmed = line.strip();
            int eq = trimmed.indexOf('=');
            if (trimmed.isEmpty() || trimmed.startsWith("#") || eq <= 0) {
                continue;
            }
            out.put(trimmed.substring(0, eq).strip(), trimmed.substring(eq + 1).strip());
        }
        return out;
    }

    private static Map<String, JsonObject> jsonById(String relative) throws IOException {
        Map<String, JsonObject> out = new TreeMap<>();
        for (Path root : packRoots(relative)) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                    JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                    if (parsed.isJsonObject()) {
                        out.put(file.getFileName().toString().replace(".json", ""), parsed.getAsJsonObject());
                    }
                }
            }
        }
        return out;
    }

    private static List<Path> packRoots(String relative) throws IOException {
        List<Path> roots = new ArrayList<>();
        if (Files.isDirectory(CORE.resolve(relative))) {
            roots.add(CORE.resolve(relative));
        }
        try (Stream<Path> packs = Files.list(SUBPLUGINS)) {
            for (Path pack : packs.toList()) {
                if (Files.isDirectory(pack.resolve(relative))) {
                    roots.add(pack.resolve(relative));
                }
            }
        }
        return roots;
    }

    private static List<Path> uiFiles() throws IOException {
        List<Path> out = new ArrayList<>();
        for (Path root : packRoots("Common/UI/Custom")) {
            try (Stream<Path> files = Files.walk(root)) {
                out.addAll(files.filter(p -> p.getFileName().toString().endsWith(".ui")).toList());
            }
        }
        return out;
    }
}
