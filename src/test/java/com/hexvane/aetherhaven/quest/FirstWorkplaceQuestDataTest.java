package com.hexvane.aetherhaven.quest;

import static com.hexvane.aetherhaven.quest.WorkplaceQuestFixture.NO_BUILDINGS;
import static com.hexvane.aetherhaven.quest.WorkplaceQuestFixture.QUEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.quest.data.QuestDefinition;
import com.hexvane.aetherhaven.quest.data.QuestObjective;
import com.hexvane.aetherhaven.quest.data.QuestReward;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.workplace.WorkplaceGuide;
import com.hexvane.aetherhaven.workplace.WorkplaceStations;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The shipped quest, its English text and Elder Lyren's part in it, held together: every wording the game can pick
 * exists, every gap in a sentence is one the game fills for that wording, the dialogue points at steps that exist,
 * and nothing a player reads names a key or uses a dash as a pause.
 */
@Tag("town")
class FirstWorkplaceQuestDataTest {
    private static final Path LANG = Path.of("src/main/resources/Server/Languages/en-US");
    private static final String FILE = "aetherhaven_workplace_quest";
    private static final Pattern GAP = Pattern.compile("\\{([A-Za-z]+)}");
    /** The key names the tool prompt audit refuses, so quest text can never be wrong for a player who rebound a key. */
    private static final Pattern KEY_NAME = Pattern.compile(
        "\\b(LMB|RMB|MMB|WASD)\\b|[Ll]eft[- ]click|[Rr]ight[- ]click|[Mm]iddle[- ]click|[Mm]ouse wheel"
            + "|\\b[Pp]ress(es|ing)? (F|Q|E|R|H|G|B|V|Tab)\\b|\\b(Ctrl|CTRL|Esc|ESC|Escape|Spacebar|Tab key)\\b|\\bShift\\b|\\bAlt\\b");

    private final QuestDefinition quest = WorkplaceQuestFixture.shipped();

    private static Map<String, String> lang(String file) throws IOException {
        var lines = new LinkedHashMap<String, String>();
        for (String line : Files.readAllLines(LANG.resolve(file + ".lang"))) {
            int at = line.indexOf('=');
            if (at <= 0 || line.startsWith("#")) continue;
            // The game's key is the file name and the line's key.
            assertNull(lines.put(file + "." + line.substring(0, at).strip(), line.substring(at + 1).strip()), "Twice in " + file + ": " + line);
        }
        return lines;
    }

    private static Set<String> gaps(String text) {
        var gaps = new TreeSet<String>();
        Matcher m = GAP.matcher(text);
        while (m.find()) gaps.add(m.group(1));
        return gaps;
    }

    private static void assertPlain(String key, String text) {
        assertFalse(text.isBlank(), key);
        assertFalse(text.contains(" - ") || text.contains("—") || text.contains("–"), "No dashes as pauses: " + key);
        assertFalse(KEY_NAME.matcher(text).find(), "Names a key, which is wrong for a player who rebound it: " + key + " = " + text);
        assertFalse(text.contains("  "), "Double space: " + key);
    }

    @Test
    void theQuestIsTheFourStepsThenLyren() {
        assertEquals(QUEST, quest.idOrEmpty());
        assertEquals(QuestDefinition.SUPPORTED_SCHEMA_VERSION, quest.schemaVersion());
        assertEquals(List.of("unlock", "mark", "choose", "bench", "talk"), quest.objectivesOrEmpty().stream().map(QuestObjective::id).toList());
        assertEquals(
            List.of(QuestProgressionService.WORKPLACE_TYPE_UNLOCKED, QuestProgressionService.WORKPLACE_MARKED,
                QuestProgressionService.WORKPLACE_TYPE_CHOSEN, QuestProgressionService.WORKPLACE_BENCH_PLACED,
                QuestProgressionService.DIALOGUE_TURN_IN),
            quest.objectivesOrEmpty().stream().map(QuestObjective::kind).toList());
        for (var objective : quest.objectivesOrEmpty()) {
            assertNull(objective.workType(), "Any workplace type must do: " + objective.id());
            assertTrue(QuestDefinition.isTrackableObjective(objective));
        }
        // Nobody is there to be handed a token when a quest starts by itself.
        assertNull(quest.grantPlotTokenConstructionId());
        assertNull(quest.grantPlotBlueprintConstructionId());
        assertNull(quest.assignNpcRoleId(), "A quest tied to a villager's role would open Lyren's dialogue on an offer");
    }

    @Test
    void theRewardIsLyrensUsualKindAndPaysTheFirstWeekOfWages() {
        long gold = 0;
        int reputation = 0;
        for (QuestReward reward : quest.rewardsOrEmpty()) {
            if ("item".equals(reward.kind())) {
                assertEquals("Aetherhaven_Gold_Coin", reward.itemId());
                gold += reward.count();
            }
            if ("reputation".equals(reward.kind())) {
                assertEquals("Aetherhaven_Elder_Lyren", reward.npcRoleId());
                reputation += reward.amount();
            }
        }
        assertEquals(com.hexvane.aetherhaven.economy.TownPayroll.WEEKLY_WAGE, gold, "One week's wage for the townsfolk hired next");
        assertEquals(0, gold % 5, "Aetherhaven's own prices and payments are multiples of five");
        assertEquals(10, reputation);
    }

    @Test
    void everyWordingTheGameCanPickIsWrittenAndPlain() throws IOException {
        var text = lang(FILE);
        for (var line : text.entrySet()) assertPlain(line.getKey(), line.getValue());
        assertTrue(text.containsKey(quest.titleLangKey()), quest.titleLangKey());
        assertTrue(text.containsKey(quest.descriptionLangKey()), quest.descriptionLangKey());
        assertNotNull(quest.autoStart());
        assertTrue(text.get(quest.autoStart().announceLangKey()).startsWith("Elder Lyren: "), "The chat line says who is speaking");
        var used = new HashSet<>(List.of(quest.titleLangKey(), quest.descriptionLangKey(), quest.autoStart().announceLangKey()));
        for (var objective : quest.objectivesOrEmpty()) {
            assertNotNull(objective.textLangKey(), objective.id());
            assertNotNull(objective.hudTextLangKey(), objective.id());
            assertFalse(objective.text() == null || objective.text().isBlank(), "A plain line for tools and logs: " + objective.id());
            if (!QuestProgressionService.isWorkplaceKind(objective)) {
                for (String key : List.of(objective.textLangKey(), objective.hudTextLangKey())) {
                    assertTrue(text.containsKey(key), key);
                    assertTrue(gaps(text.get(key)).isEmpty(), "Nothing fills a gap in this line: " + key);
                    used.add(key);
                }
                continue;
            }
            var journal = new ArrayList<>(WorkplaceObjectiveText.JOURNAL_VARIANTS.get(objective.kind()));
            var hud = new ArrayList<>(WorkplaceObjectiveText.HUD_VARIANTS.get(objective.kind()));
            journal.add(WorkplaceObjectiveText.DONE);
            hud.add(WorkplaceObjectiveText.DONE);
            for (String variant : journal) {
                assertTrue(text.containsKey(objective.textLangKey() + "." + variant), objective.textLangKey() + "." + variant);
                used.add(objective.textLangKey() + "." + variant);
            }
            for (String variant : hud) {
                String key = objective.hudTextLangKey() + "." + variant;
                assertTrue(text.containsKey(key), key);
                assertTrue(text.get(key).length() <= 96, "The tracker has room for two short lines: " + key);
                used.add(key);
            }
            assertTrue(gaps(text.get(objective.textLangKey() + ".done")).isEmpty(), "A finished step has no names to fill in");
            assertTrue(gaps(text.get(objective.hudTextLangKey() + ".done")).isEmpty());
        }
        assertEquals(used, text.keySet(), "A line nothing shows, or a line the game shows that is not written");
    }

    /** Every wording of every step, produced from towns in each state, with the gaps the game fills for it. */
    private Map<String, List<WorkplaceGuide.Step>> stepsInEveryState() {
        var steps = new HashMap<String, List<WorkplaceGuide.Step>>();
        var unlock = new ArrayList<WorkplaceGuide.Step>();
        var choose = new ArrayList<WorkplaceGuide.Step>();
        var bench = new ArrayList<WorkplaceGuide.Step>();
        var empty = WorkplaceQuestFixture.emptyTown();
        unlock.add(WorkplaceGuide.unlockStep(empty, NO_BUILDINGS, null, role -> null));
        choose.add(WorkplaceGuide.chooseStep(empty, null));
        bench.add(WorkplaceGuide.benchStep(empty, null, type -> null));
        for (boolean named : new boolean[]{true, false}) {
            var town = WorkplaceQuestFixture.townFor(TownsfolkWorkType.SMELTING);
            unlock.add(WorkplaceGuide.unlockStep(town, NO_BUILDINGS, null, role -> named ? "Garren Vale" : null));
            WorkplaceQuestFixture.moveIn(town, "farmer");
            unlock.add(WorkplaceGuide.unlockStep(town, NO_BUILDINGS, null, role -> named ? "Garren Vale" : null));
        }
        var town = WorkplaceQuestFixture.townFor(TownsfolkWorkType.SMELTING);
        assertNull(town.getApprenticeWorkplaces().unlock(town, TownsfolkWorkType.SMELTING));
        choose.add(WorkplaceGuide.chooseStep(town, null));
        WorkplaceQuestFixture.save(town, TownsfolkWorkType.SMELTING, "Smeltery");
        bench.add(WorkplaceGuide.benchStep(town, null, type -> null));
        bench.add(WorkplaceGuide.benchStep(town, null, type -> new WorkplaceStations.Station("Bench_Furnace", null, null)));
        bench.add(WorkplaceGuide.benchStep(town, null, type -> new WorkplaceStations.Station("Bench_Furnace",
            new WorkplaceStations.CraftedAt(WorkplaceStations.POCKET, 1), null)));
        bench.add(WorkplaceGuide.benchStep(town, null, type -> new WorkplaceStations.Station("Bench_Furnace",
            new WorkplaceStations.CraftedAt("Workbench", 1), "Bench_WorkBench")));
        bench.add(WorkplaceGuide.benchStep(town, null, type -> new WorkplaceStations.Station("Bench_Furnace",
            new WorkplaceStations.CraftedAt("Workbench", 2), "Bench_WorkBench")));
        steps.put(QuestProgressionService.WORKPLACE_TYPE_UNLOCKED, unlock);
        steps.put(QuestProgressionService.WORKPLACE_MARKED, List.of(WorkplaceGuide.markStep()));
        steps.put(QuestProgressionService.WORKPLACE_TYPE_CHOSEN, choose);
        steps.put(QuestProgressionService.WORKPLACE_BENCH_PLACED, bench);
        return steps;
    }

    @Test
    void everyGapInASentenceIsFilledForThatWordingAndNoWordingIsLeftOut() throws IOException {
        var text = lang(FILE);
        var steps = stepsInEveryState();
        for (var objective : quest.objectivesOrEmpty()) {
            if (!QuestProgressionService.isWorkplaceKind(objective)) continue;
            var seenJournal = new TreeSet<String>();
            var seenHud = new TreeSet<String>();
            for (var step : steps.get(objective.kind())) {
                seenJournal.add(step.variant());
                seenHud.add(step.hudVariant());
                for (String key : List.of(objective.textLangKey() + "." + step.variant(), objective.hudTextLangKey() + "." + step.hudVariant())) {
                    assertNotNull(text.get(key), "The game would show a bare key: " + key);
                    var unfilled = gaps(text.get(key));
                    unfilled.removeAll(step.words().keySet());
                    assertTrue(unfilled.isEmpty(), key + " has gaps the game does not fill: " + unfilled);
                }
            }
            // The lists the text is checked against are the wordings the guide really produces. Neither can drift.
            assertEquals(new TreeSet<>(WorkplaceObjectiveText.JOURNAL_VARIANTS.get(objective.kind())), seenJournal, objective.id());
            assertEquals(new TreeSet<>(WorkplaceObjectiveText.HUD_VARIANTS.get(objective.kind())), seenHud, objective.id());
        }
    }

    @Test
    void theStepThatNamesABenchNamesItForEveryType() {
        // Each type's bench line carries the workplace's own name and its type, whatever the type is.
        for (var type : WorkplaceQuestFixture.everyType().toList()) {
            var town = WorkplaceQuestFixture.townFor(type);
            assertNull(town.getApprenticeWorkplaces().unlock(town, type));
            WorkplaceQuestFixture.save(town, type, "My " + type.id);
            var step = WorkplaceGuide.benchStep(town, null, asked -> new WorkplaceStations.Station("Item_" + asked.id, null, null));
            assertEquals(WorkplaceGuide.Word.item("Item_" + type.id), step.words().get("bench"), type.id);
            assertEquals(WorkplaceGuide.Word.raw("My " + type.id), step.words().get("site"), type.id);
            assertEquals("Item_" + type.id, step.itemId());
        }
    }

    @Test
    void lyrenIntroducesItClosesItAndOffersItAgain() throws IOException {
        var elder = JsonParser.parseString(Files.readString(Path.of("src/main/resources/Server/Aetherhaven/Dialogue/aetherhaven_elder.json"))).getAsJsonObject();
        var nodes = elder.getAsJsonObject("nodes");
        var text = lang("aetherhaven_dialogue_elder");
        var objectiveIds = quest.objectivesOrEmpty().stream().map(QuestObjective::id).toList();
        JsonObject done = null, remind = null, offer = null;
        for (JsonElement choice : nodes.getAsJsonObject("main_hub").getAsJsonArray("choices")) {
            var c = choice.getAsJsonObject();
            String id = c.has("id") ? c.get("id").getAsString() : "";
            if (id.equals("first_workplace_done")) done = c;
            if (id.equals("first_workplace_remind")) remind = c;
            if (id.equals("first_workplace_offer")) offer = c;
        }
        assertNotNull(done);
        assertNotNull(remind);
        assertNotNull(offer);
        // Every condition about this quest names the quest and a step it really has.
        for (JsonObject choice : List.of(done, remind, offer)) {
            assertEquals("hide", choice.get("whenFalse").getAsString());
            var conditions = new ArrayList<JsonObject>();
            collect(choice.get("condition"), conditions);
            assertFalse(conditions.isEmpty());
            for (var condition : conditions) {
                if (condition.has("questId")) assertEquals(QUEST, condition.get("questId").getAsString());
                if (condition.has("objectiveId")) assertTrue(objectiveIds.contains(condition.get("objectiveId").getAsString()), condition.toString());
            }
        }
        // He closes it only when all four steps are done, and that is the one choice that completes the quest.
        var gates = new ArrayList<JsonObject>();
        collect(done.get("condition"), gates);
        var gated = gates.stream().filter(c -> "town_quest_objective_complete".equals(c.get("type").getAsString()))
            .map(c -> c.get("objectiveId").getAsString()).toList();
        assertEquals(List.of("unlock", "mark", "choose", "bench"), gated);
        assertEquals("complete_quest", done.getAsJsonArray("actions").get(0).getAsJsonObject().get("type").getAsString());
        assertEquals(QUEST, done.getAsJsonArray("actions").get(0).getAsJsonObject().get("id").getAsString());
        assertEquals("workplace_done", done.get("next").getAsString());
        assertFalse(remind.has("actions"), "Asking again changes nothing");
        assertEquals("workplace_intro", remind.get("next").getAsString());
        assertEquals("workplace_offer", offer.get("next").getAsString());
        var accept = nodes.getAsJsonObject("workplace_offer").getAsJsonArray("choices").get(0).getAsJsonObject();
        assertEquals("start_quest", accept.getAsJsonArray("actions").get(0).getAsJsonObject().get("type").getAsString());
        assertEquals(QUEST, accept.getAsJsonArray("actions").get(0).getAsJsonObject().get("id").getAsString());
        assertEquals("workplace_intro", accept.get("next").getAsString());
        // Every line he says for it is written, in his name, and plain.
        for (String node : List.of("workplace_intro", "workplace_done", "workplace_offer")) {
            var n = nodes.getAsJsonObject(node);
            assertEquals("Elder Lyren", text.get(n.get("speaker").getAsString()), node);
            assertPlain(node, text.get(n.get("text").getAsString()));
            for (JsonElement choice : n.getAsJsonArray("choices")) assertPlain(node, text.get(choice.getAsJsonObject().get("text").getAsString()));
        }
        for (JsonObject choice : List.of(done, remind, offer)) assertPlain(choice.get("id").getAsString(), text.get(choice.get("text").getAsString()));
        String closing = text.get(nodes.getAsJsonObject("workplace_done").get("text").getAsString());
        assertTrue(closing.contains("Work tab") && closing.contains("priority"), "His closing line points at the next thing to do");
    }

    private static void collect(JsonElement condition, List<JsonObject> into) {
        if (condition == null || !condition.isJsonObject()) return;
        var c = condition.getAsJsonObject();
        into.add(c);
        if (c.has("condition")) collect(c.get("condition"), into);
        if (c.has("conditions")) for (JsonElement child : c.getAsJsonArray("conditions")) collect(child, into);
    }

    @Test
    void theShippedQuestLoadsWithNoUnknownKind() {
        // The catalog warns about a kind it does not know and the quest would then never tick: hold the list to the data.
        var known = Set.of("workplace_type_unlocked", "workplace_marked", "workplace_type_chosen", "workplace_bench_placed", "dialogue_turn_in");
        for (var objective : quest.objectivesOrEmpty()) assertTrue(known.contains(objective.kind()), objective.kind());
        var catalog = WorkplaceQuestFixture.catalog(quest);
        assertTrue(catalog.hasObjectives(QUEST));
        assertEquals(5, quest.trackableObjectiveIds().size());
    }
}
