package com.hexvane.aetherhaven.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.dialogue.data.DialogueChoiceDefinition;
import com.hexvane.aetherhaven.dialogue.data.DialogueNodeDefinition;
import com.hexvane.aetherhaven.dialogue.data.DialogueTreeDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every dialogue choice that opens a shop shows a shop icon.
 *
 * <p>The row icon comes from {@link DialoguePage#choiceIconPath}: an authored {@code icon} wins, otherwise the choice's
 * actions decide. A shop action the page does not know falls through to the speech bubble, which is how the Rancher's
 * animal shop lost its icon.
 */
class DialogueShopChoiceIconTest {
    private static final Path CORE_DIALOGUE = Path.of("src/main/resources/Server/Aetherhaven/Dialogue");
    private static final Path SUBPLUGINS = Path.of("subplugin-assets");
    private static final String SHOP_ICON = "UI/Custom/market.png";
    private static final String SPEECH_BUBBLE = "UI/Custom/dialogue.png";

    /** Actions that open a page where the player buys, sells or trades. */
    private static final Set<String> SHOP_ACTIONS = Set.of("open_barn_shop", "open_barter_shop", "open_prop_shop");

    @Test
    void theAnimalShopChoiceShowsTheShopIcon() throws IOException {
        DialogueTreeDefinition rancher = new Gson()
            .fromJson(Files.readString(CORE_DIALOGUE.resolve("aetherhaven_rancher.json")), DialogueTreeDefinition.class);
        List<DialogueChoiceDefinition> shop = new ArrayList<>();
        for (DialogueNodeDefinition node : rancher.getNodes().values()) {
            for (DialogueChoiceDefinition choice : node.getChoices()) {
                if (choice.hasAction("open_barn_shop")) {
                    shop.add(choice);
                }
            }
        }
        assertEquals(1, shop.size(), "the Rancher offers the animal shop once");
        assertEquals("aetherhaven_barn.barn.animalShop", shop.getFirst().getText());
        assertEquals(SHOP_ICON, DialoguePage.choiceIconPath(shop.getFirst()));
    }

    @Test
    void everyChoiceThatOpensAShopShowsAShopIcon() throws IOException {
        List<String> problems = new ArrayList<>();
        Set<String> seen = new TreeSet<>();
        int shopChoices = 0;
        for (Path file : dialogueFiles()) {
            DialogueTreeDefinition tree = new Gson().fromJson(Files.readString(file), DialogueTreeDefinition.class);
            if (tree == null || tree.getNodes() == null) {
                continue;
            }
            for (var node : tree.getNodes().entrySet()) {
                for (DialogueChoiceDefinition choice : node.getValue().getChoices()) {
                    String action = SHOP_ACTIONS.stream().filter(choice::hasAction).findFirst().orElse(null);
                    if (action == null) {
                        continue;
                    }
                    shopChoices++;
                    seen.add(action);
                    String icon = DialoguePage.choiceIconPath(choice);
                    String where = file.getFileName() + " node " + node.getKey() + " (" + action + ")";
                    if (icon == null || icon.isBlank() || SPEECH_BUBBLE.equals(icon)) {
                        problems.add(where + " shows " + icon + " instead of a shop icon");
                    } else if (choice.getIcon() == null && !SHOP_ICON.equals(icon)) {
                        // An authored icon may be a festival's own picture; an unauthored one must be the shop icon.
                        problems.add(where + " resolves to " + icon + " without an authored icon");
                    }
                }
            }
        }
        assertTrue(problems.isEmpty(), String.join("\n", problems));
        assertEquals(SHOP_ACTIONS, seen, "a shop action no dialogue uses any more should leave this list");
        assertTrue(shopChoices >= 9, "expected the festival, furniture and animal shops, found " + shopChoices);
    }

    @Test
    void anUnknownActionStillFallsBackToTheSpeechBubble() {
        // Guards the test above: it only means something while an unlisted action really does show the bubble.
        DialogueChoiceDefinition unknown = new Gson()
            .fromJson("{\"text\":\"x\",\"actions\":[{\"type\":\"open_something_new\"}]}", DialogueChoiceDefinition.class);
        assertEquals(SPEECH_BUBBLE, DialoguePage.choiceIconPath(unknown));
        DialogueChoiceDefinition barn = new Gson()
            .fromJson("{\"text\":\"x\",\"actions\":[{\"type\":\"open_barn_shop\"}]}", DialogueChoiceDefinition.class);
        assertNotEquals(SPEECH_BUBBLE, DialoguePage.choiceIconPath(barn));
        assertFalse(barn.getActions().isEmpty());
    }

    private static List<Path> dialogueFiles() throws IOException {
        List<Path> roots = new ArrayList<>();
        roots.add(CORE_DIALOGUE);
        try (Stream<Path> packs = Files.list(SUBPLUGINS)) {
            for (Path pack : packs.toList()) {
                Path dialogue = pack.resolve("Server/Aetherhaven/Dialogue");
                if (Files.isDirectory(dialogue)) {
                    roots.add(dialogue);
                }
            }
        }
        List<Path> out = new ArrayList<>();
        for (Path root : roots) {
            try (Stream<Path> files = Files.walk(root)) {
                out.addAll(files.filter(p -> p.getFileName().toString().endsWith(".json")).toList());
            }
        }
        assertTrue(out.size() >= 40, "expected the mod's dialogue trees, found " + out.size());
        return out;
    }
}
