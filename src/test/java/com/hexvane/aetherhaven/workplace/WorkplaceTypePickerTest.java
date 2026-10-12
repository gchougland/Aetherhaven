package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkSkill;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** The cards of the workplace page: which types are offered, in what order, and what each one says stands in the way. */
@Tag("town")
class WorkplaceTypePickerTest {
    private static TownRecord town(String... villagers) {
        var town = new TownRecord(UUID.randomUUID(), UUID.randomUUID(), "test", 16, 100, 16, 1, 1, 0);
        for (String villager : villagers) town.getResidentNpcRecords().add(new ResidentNpcRecord(villager, villager, null, UUID.randomUUID()));
        return town;
    }
    private static List<TownsfolkWorkType> types(List<WorkplaceTypePicker.Card> cards) {
        return cards.stream().map(WorkplaceTypePicker.Card::type).toList();
    }

    @Test void everyTypeAWorkplaceCanBeMadeForHasACardAndWorkWithoutAWorkplaceHasNone() {
        var cards = WorkplaceTypePicker.cards(town(), null, null, "");
        assertEquals(16, cards.size());
        var shown = new HashSet<>(types(cards));
        assertFalse(shown.contains(TownsfolkWorkType.LOGISTICS));
        assertFalse(shown.contains(TownsfolkWorkType.COMMERCE));
        assertFalse(shown.contains(TownsfolkWorkType.CULTIVATION), "Herb gardens are no longer created");
        for (var type : TownsfolkWorkType.values())
            if (type.requiresWorkplace() && type.availableForNewWork()) assertTrue(shown.contains(type), type.id);
    }

    @Test void aNewTownSeesEveryTypeLockedWithItsReason() {
        var town = town("blacksmith");
        for (var card : WorkplaceTypePicker.cards(town, null, null, "")) {
            boolean smith = card.villager().equals("blacksmith");
            assertEquals(smith ? WorkplaceTypePicker.State.LOCKED : WorkplaceTypePicker.State.NO_VILLAGER, card.state(), card.type().id);
            assertFalse(card.state().choosable());
        }
    }

    @Test void typesThatCanBeChosenComeFirstAndEachRunKeepsTheSkillsTogether() {
        var town = town("blacksmith", "crafter", "farmer");
        var sites = town.getApprenticeWorkplaces();
        assertNull(sites.unlock(town, TownsfolkWorkType.SALVAGING));
        assertNull(sites.unlock(town, TownsfolkWorkType.SMELTING));
        assertNull(sites.unlock(town, TownsfolkWorkType.CRAFTING));
        var cards = WorkplaceTypePicker.cards(town, null, null, "");
        assertEquals(3, WorkplaceTypePicker.choosable(cards));
        // Smelting (Blacksmithing) leads, then the two Crafting skill types side by side although Salvaging is declared last.
        assertEquals(List.of(TownsfolkWorkType.SMELTING, TownsfolkWorkType.CRAFTING, TownsfolkWorkType.SALVAGING), types(cards).subList(0, 3));
        // The locked run starts again from the first skill.
        assertEquals(TownsfolkWorkType.BLACKSMITHING, cards.get(3).type());
        assertEquals(TownsfolkWorkType.ARMOR_CRAFTING, cards.get(4).type());
        for (int i = 3; i < cards.size(); i++) assertFalse(cards.get(i).state().choosable(), cards.get(i).type().id);
    }

    @Test void aVillagerWithNoSiteToSpareIsSaidSoButTheWorkplaceBeingEditedDoesNotCountAgainstItself() {
        var town = town("farmer");
        var sites = town.getApprenticeWorkplaces();
        assertNull(sites.unlock(town, TownsfolkWorkType.FARMING));
        assertEquals(WorkplaceTypePicker.State.READY, WorkplaceTypePicker.state(town, TownsfolkWorkType.FARMING, null));
        assertNull(sites.save(town, null, TownsfolkWorkType.FARMING, new WorkplaceArea(8, 100, 8, 15, 14), "Field"), "The fixture site must save");
        var field = sites.sites().getFirst().id();
        // The Farmer starts with one site. A second workplace has no room, but editing the first one is fine.
        assertEquals(WorkplaceTypePicker.State.FULL, WorkplaceTypePicker.state(town, TownsfolkWorkType.FARMING, null));
        assertEquals(WorkplaceTypePicker.State.READY, WorkplaceTypePicker.state(town, TownsfolkWorkType.FARMING, field));
        var card = WorkplaceTypePicker.cards(town, null, TownsfolkSkill.FARMING, "").getFirst();
        assertEquals(1, card.used());
        assertEquals(1, card.slots());
        assertTrue(card.state().choosable(), "A full type can still be looked at and saved over an existing site");
    }

    @Test void theSkillFilterKeepsOnlyThatSkillsTypes() {
        var town = town();
        assertEquals(List.of(TownsfolkWorkType.FARMING, TownsfolkWorkType.MYCOLOGY, TownsfolkWorkType.BERRIES),
            types(WorkplaceTypePicker.cards(town, null, TownsfolkSkill.FARMING, "")));
        assertEquals(List.of(TownsfolkWorkType.SMELTING, TownsfolkWorkType.BLACKSMITHING, TownsfolkWorkType.ARMOR_CRAFTING),
            types(WorkplaceTypePicker.cards(town, null, TownsfolkSkill.BLACKSMITHING, "")));
        // A filter exists for every skill with a workplace, and none for hauling or shop work.
        var skills = WorkplaceTypePicker.skills();
        assertEquals(10, skills.size());
        assertFalse(skills.contains(TownsfolkSkill.LOGISTICS));
        assertFalse(skills.contains(TownsfolkSkill.COMMERCE));
        for (var skill : skills) assertFalse(WorkplaceTypePicker.cards(town, null, skill, "").isEmpty(), skill.id);
    }

    @Test void searchFindsATypeByItsNameItsBenchItsVillagerOrWhatItDoes() {
        var town = town();
        assertEquals(List.of(TownsfolkWorkType.MYCOLOGY), types(WorkplaceTypePicker.cards(town, null, null, "mushroom")));
        assertEquals(List.of(TownsfolkWorkType.LEATHERWORKING), types(WorkplaceTypePicker.cards(town, null, null, "TANNING rack")));
        assertEquals(List.of(TownsfolkWorkType.SMELTING), types(WorkplaceTypePicker.cards(town, null, null, "  furnace ")));
        assertTrue(types(WorkplaceTypePicker.cards(town, null, null, "druid")).containsAll(List.of(TownsfolkWorkType.MYCOLOGY, TownsfolkWorkType.BERRIES)));
        assertEquals(List.of(TownsfolkWorkType.COOKING), types(WorkplaceTypePicker.cards(town, null, null, "stove cook")));
        assertTrue(WorkplaceTypePicker.cards(town, null, null, "dragon").isEmpty());
        // Search and filter narrow together.
        assertTrue(WorkplaceTypePicker.cards(town, null, TownsfolkSkill.MINING, "furnace").isEmpty());
        assertEquals(16, WorkplaceTypePicker.cards(town, null, null, null).size());
    }

    @Test void everyCardHasItsLineAndEveryVillagerItsNameInTheEnglishText() throws Exception {
        var keys = new HashSet<String>();
        for (String line : Files.readAllLines(Path.of("src/main/resources/Server/Languages/en-US/aetherhaven_workplace.lang"))) {
            int at = line.indexOf(" = ");
            if (at > 0 && !line.startsWith("#")) {
                keys.add(line.substring(0, at));
                assertFalse(line.contains(" - ") || line.contains("—") || line.contains("–"), "No dashes as pauses: " + line);
            }
        }
        for (var type : WorkplaceTypePicker.creatable()) {
            assertTrue(keys.contains("aetherhaven.workplace.line." + type.id), type.id);
            assertTrue(keys.contains("aetherhaven.workplace.villager." + WorkplaceRules.supervisor(type)), WorkplaceRules.supervisor(type));
        }
        for (String key : List.of("typesReady", "typesLocked", "filterAll", "filterAllHint", "filterHint", "shown", "noMatch", "chosen",
                "choosePrompt", "chooseHelp", "defaultHint", "stateFree", "stateFull", "stateLocked", "stateNoVillager", "lockedHint", "noVillagerHint"))
            assertTrue(keys.contains("aetherhaven.workplace." + key), key);
    }
}
