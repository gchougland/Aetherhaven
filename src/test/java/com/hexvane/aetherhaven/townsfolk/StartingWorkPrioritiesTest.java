package com.hexvane.aetherhaven.townsfolk;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.tourist.TouristRecord;
import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterDefinition;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** A new resident starts with priorities for the work they are good at. Once, and never over a player's own choice. */
@Tag("town")
class StartingWorkPrioritiesTest {
    private static final Gson GSON = new Gson();

    private static TownsfolkProgress skilled(Map<TownsfolkSkill, Integer> skills) {
        var progress = new TownsfolkProgress();
        skills.forEach(progress::setSkill);
        return progress;
    }

    @Test void theBestSkillComesFirstAndUntrainedWorkStaysOff() {
        var progress = skilled(Map.of(TownsfolkSkill.FARMING, 5, TownsfolkSkill.RANCHING, 3, TownsfolkSkill.CRAFTING, 2));
        assertTrue(progress.applyStartingPriorities());
        // Farming 5 is the best skill, and the three kinds of work that use it share first place.
        assertEquals(1, progress.priority(TownsfolkWorkType.FARMING));
        assertEquals(1, progress.priority(TownsfolkWorkType.MYCOLOGY));
        assertEquals(1, progress.priority(TownsfolkWorkType.BERRIES));
        assertEquals(2, progress.priority(TownsfolkWorkType.RANCHING));
        assertEquals(3, progress.priority(TownsfolkWorkType.CRAFTING));
        assertEquals(3, progress.priority(TownsfolkWorkType.SALVAGING));
        // Everything at the untrained level of 1 is left off.
        for (var type : List.of(TownsfolkWorkType.SMELTING, TownsfolkWorkType.BLACKSMITHING, TownsfolkWorkType.ARMOR_CRAFTING,
                TownsfolkWorkType.MINING, TownsfolkWorkType.BUILDING, TownsfolkWorkType.BLOCK_WORKSHOP, TownsfolkWorkType.ALCHEMY,
                TownsfolkWorkType.LEATHERWORKING, TownsfolkWorkType.COOKING, TownsfolkWorkType.JEWELRY, TownsfolkWorkType.LOGISTICS))
            assertEquals(0, progress.priority(type), type.id);
    }

    @Test void aFourthAndFifthSkillShareTheLastPriorityAndEqualSkillsShareOne() {
        var progress = skilled(Map.of(TownsfolkSkill.BLACKSMITHING, 9, TownsfolkSkill.MINING, 7, TownsfolkSkill.BUILDING, 7,
            TownsfolkSkill.COOKING, 5, TownsfolkSkill.ALCHEMY, 3, TownsfolkSkill.LOGISTICS, 2));
        progress.applyStartingPriorities();
        assertEquals(1, progress.priority(TownsfolkWorkType.SMELTING));
        assertEquals(2, progress.priority(TownsfolkWorkType.MINING));
        assertEquals(2, progress.priority(TownsfolkWorkType.BUILDING));
        assertEquals(3, progress.priority(TownsfolkWorkType.COOKING));
        assertEquals(4, progress.priority(TownsfolkWorkType.ALCHEMY));
        assertEquals(4, progress.priority(TownsfolkWorkType.LOGISTICS));
    }

    @Test void workTheyCannotDoAndRetiredWorkGetNoPriority() {
        var progress = skilled(Map.of(TownsfolkSkill.FARMING, 6, TownsfolkSkill.ALCHEMY, 0));
        progress.applyStartingPriorities();
        assertEquals(0, progress.priority(TownsfolkWorkType.ALCHEMY));
        assertFalse(progress.startingPriorities().containsKey(TownsfolkWorkType.ALCHEMY));
        // Herb cultivation shares the Farming skill but is no longer offered, so it is not switched on.
        assertFalse(progress.startingPriorities().containsKey(TownsfolkWorkType.CULTIVATION));
        assertEquals(0, progress.priority(TownsfolkWorkType.CULTIVATION));
    }

    @Test void commerceIsLeftToTheShopSoAClerkStillServesFirst() {
        var progress = skilled(Map.of(TownsfolkSkill.COMMERCE, 6, TownsfolkSkill.COOKING, 4));
        progress.applyStartingPriorities();
        assertFalse(progress.startingPriorities().containsKey(TownsfolkWorkType.COMMERCE));
        // Unchosen Commerce reads as first, as it did before. Commerce skill takes no place in the ranking, so Cooking
        // is this resident's best ranked work. A clerk still serves first: a tie with other work goes to the shop.
        assertEquals(1, progress.priority(TownsfolkWorkType.COMMERCE));
        assertEquals(1, progress.priority(TownsfolkWorkType.COOKING));
    }

    @Test void aPriorityThePlayerSetIsNeverReplaced() {
        var progress = skilled(Map.of(TownsfolkSkill.FARMING, 7, TownsfolkSkill.MINING, 4));
        progress.setPriority(TownsfolkWorkType.MINING, 3);
        assertTrue(progress.applyStartingPriorities());
        assertEquals(3, progress.priority(TownsfolkWorkType.MINING));
        assertEquals(0, progress.priority(TownsfolkWorkType.FARMING), "Nothing is added beside a choice the player made");
    }

    @Test void switchingEverythingOffIsAChoiceToo() {
        var progress = skilled(Map.of(TownsfolkSkill.FARMING, 7));
        progress.setPriority(TownsfolkWorkType.FARMING, 0);
        progress.applyStartingPriorities();
        assertEquals(0, progress.priority(TownsfolkWorkType.FARMING));
    }

    @Test void itActsOnceSoLaterTrainingOrASwitchedOffCellIsNotUndone() {
        var progress = skilled(Map.of(TownsfolkSkill.FARMING, 5, TownsfolkSkill.COOKING, 3));
        assertTrue(progress.applyStartingPriorities());
        progress.setPriority(TownsfolkWorkType.FARMING, 0);
        progress.setPriority(TownsfolkWorkType.MYCOLOGY, 0);
        progress.setPriority(TownsfolkWorkType.BERRIES, 0);
        progress.setPriority(TownsfolkWorkType.COOKING, 0);
        progress.setSkill(TownsfolkSkill.MINING, 8);
        assertFalse(progress.applyStartingPriorities());
        assertEquals(0, progress.priority(TownsfolkWorkType.FARMING));
        assertEquals(0, progress.priority(TownsfolkWorkType.MINING));
    }

    @Test void theOneTimeMarkSurvivesASaveSoALoadDoesNotApplyAgain() {
        var town = new TownRecord();
        var progress = skilled(Map.of(TownsfolkSkill.FARMING, 5));
        town.getTownsfolkProgress().put("briar", progress);
        progress.applyStartingPriorities();
        progress.setPriority(TownsfolkWorkType.FARMING, 0);
        progress.setPriority(TownsfolkWorkType.MYCOLOGY, 0);
        progress.setPriority(TownsfolkWorkType.BERRIES, 0);
        var loaded = GSON.fromJson(GSON.toJson(town), TownRecord.class).getTownsfolkProgress().get("briar");
        assertTrue(loaded.startingPrioritiesSet());
        assertFalse(loaded.applyStartingPriorities());
        assertEquals(0, loaded.priority(TownsfolkWorkType.FARMING));
    }

    @Test void anOldCourierKeepsOnlyTheirDeliveries() {
        // Saves from before Logistics was a priority marked couriers with a flag. That is a choice the player made.
        var progress = GSON.fromJson("{\"skills\":{\"farming\":6},\"courier\":true}", TownsfolkProgress.class);
        progress.applyStartingPriorities();
        assertEquals(1, progress.priority(TownsfolkWorkType.LOGISTICS));
        assertEquals(0, progress.priority(TownsfolkWorkType.FARMING));
    }

    @Test void residentsOfAnOlderSaveWithNoPrioritiesGetThemOnceWhenTheTownLoads() {
        var town = GSON.fromJson("{\"townsfolkProgress\":{"
            + "\"untouched\":{\"skills\":{\"farming\":5,\"cooking\":3},\"employed\":true},"
            + "\"chosen\":{\"skills\":{\"farming\":5,\"cooking\":3},\"workPriorities\":{\"cooking\":2}},"
            + "\"visitor\":{\"skills\":{\"mining\":6}}}}", TownRecord.class);
        resident(town, "untouched", true);
        resident(town, "chosen", true);
        resident(town, "visitor", false);
        assertTrue(TownsfolkWorkRoster.applyStartingPriorities(town));
        var untouched = town.getTownsfolkProgress().get("untouched");
        assertEquals(1, untouched.priority(TownsfolkWorkType.FARMING));
        assertEquals(2, untouched.priority(TownsfolkWorkType.COOKING));
        assertTrue(untouched.isEmployed());
        var chosen = town.getTownsfolkProgress().get("chosen");
        assertEquals(2, chosen.priority(TownsfolkWorkType.COOKING));
        assertEquals(0, chosen.priority(TownsfolkWorkType.FARMING));
        // Someone who only visits is not a resident, so nothing is decided for them yet.
        var visitor = town.getTownsfolkProgress().get("visitor");
        assertFalse(visitor.startingPrioritiesSet());
        assertEquals(0, visitor.priority(TownsfolkWorkType.MINING));

        // The next load finds the mark and changes nothing, even after the player has switched the work off.
        untouched.setPriority(TownsfolkWorkType.FARMING, 0);
        var again = GSON.fromJson(GSON.toJson(town), TownRecord.class);
        assertFalse(TownsfolkWorkRoster.applyStartingPriorities(again));
        assertEquals(0, again.getTownsfolkProgress().get("untouched").priority(TownsfolkWorkType.FARMING));
        assertEquals(2, again.getTownsfolkProgress().get("untouched").priority(TownsfolkWorkType.COOKING));
    }

    @Test void everyAuthoredTownsfolkStartsWithTheirBestWorkFirst() throws Exception {
        int count = 0;
        try (var paths = Files.list(Path.of("src/main/resources/Server/Aetherhaven/Townsfolk"))) {
            for (var path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                var character = GSON.fromJson(Files.readString(path), TownsfolkCharacterDefinition.class);
                var progress = TownsfolkProgressService.startingProgress(character);
                progress.applyStartingPriorities();
                int best = 0;
                for (var type : TownsfolkWorkType.values())
                    if (type.availableForNewWork() && type != TownsfolkWorkType.COMMERCE) best = Math.max(best, progress.skill(type.skill));
                for (var type : TownsfolkWorkType.values()) {
                    if (!type.availableForNewWork() || type == TownsfolkWorkType.COMMERCE) continue;
                    int skill = progress.skill(type.skill), priority = progress.priority(type);
                    String who = character.getId() + ": " + type.id;
                    if (skill <= 1) assertEquals(0, priority, who);
                    else assertTrue(priority >= 1 && priority <= 4, who);
                    if (skill > 1 && skill == best) assertEquals(1, priority, who);
                    for (var other : TownsfolkWorkType.values())
                        if (other.availableForNewWork() && other != TownsfolkWorkType.COMMERCE && progress.skill(other.skill) > 1
                                && progress.skill(other.skill) < skill)
                            assertTrue(progress.priority(other) >= priority, who + " should not come after " + other.id);
                }
                count++;
            }
        }
        assertTrue(count >= 60, "Read " + count + " townsfolk");
    }

    private static void resident(TownRecord town, String id, boolean citizen) {
        var uuid = UUID.randomUUID();
        var row = new ResidentNpcRecord("Aetherhaven_Townsfolk", TownVillagerBinding.KIND_TOWNSFOLK, null, uuid);
        row.setTownsfolkIdentity(new TownsfolkCharacterBinding(id, "", "", "", List.of()));
        town.getResidentNpcRecords().add(row);
        town.getTouristRecords().add(new TouristRecord(id, uuid, UUID.randomUUID(), citizen, citizen));
    }
}
