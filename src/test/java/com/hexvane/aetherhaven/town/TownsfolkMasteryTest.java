package com.hexvane.aetherhaven.town;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.townsfolk.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class TownsfolkMasteryTest {
    private final Gson gson = new Gson();

    @Test void pointAndItemTrainingRequireTownMembershipManagementPermissionAndResidentCitizenship() {
        var owner = java.util.UUID.randomUUID();
        var resident = java.util.UUID.randomUUID();
        var outsider = java.util.UUID.randomUUID();
        var town = new TownRecord();
        town.setOwner(owner, "Owner");
        var house = gson.fromJson("{\"plotId\":\"" + java.util.UUID.randomUUID() + "\",\"constructionId\":\"plot_house\",\"state\":\"COMPLETE\"}", PlotInstance.class);
        house.setHomeResidentAt(0, resident);
        town.getPlotInstances().add(house);
        assertTrue(TownsfolkProgressService.canTrain(town, owner, resident));
        assertFalse(TownsfolkProgressService.canTrain(town, outsider, resident));
        assertFalse(TownsfolkProgressService.canTrain(town, owner, java.util.UUID.randomUUID()));
        assertFalse(TownsfolkProgressService.canTrain(town, null, resident));
        town.getMemberRolesRaw().put(outsider.toString(), "QUEST");
        assertFalse(TownsfolkProgressService.canTrain(town, outsider, resident));
    }

    @Test void oldResidentsStartAtLevelOneWithoutChangingTheirExistingStatsOrPriorities() {
        var progress = gson.fromJson("{\"attributes\":{\"strength\":8},\"skills\":{\"farming\":0,\"cooking\":5},\"workPriorities\":{\"cooking\":2}}", TownsfolkProgress.class);
        assertEquals(1, progress.masteryLevel());
        assertEquals(0, progress.masteryXp());
        assertEquals(100, progress.masteryXpToNextLevel());
        assertEquals(0, progress.masterySkillPoints());
        assertEquals(0, progress.masteryAttributePoints());
        assertEquals(8, progress.attribute(TownsfolkAttribute.STRENGTH));
        assertEquals(0, progress.skill(TownsfolkSkill.FARMING));
        assertEquals(5, progress.skill(TownsfolkSkill.COOKING));
        assertEquals(2, progress.priority(TownsfolkWorkType.COOKING));
    }

    @Test void masteryThresholdsRiseAndCrossedLevelsGrantTheirPointsExactlyOnce() {
        var progress = new TownsfolkProgress();
        assertEquals(0, progress.earnMasteryXp(0));
        assertEquals(0, progress.earnMasteryXp(-100));
        progress.earnMasteryXp(99);
        assertEquals(1, progress.masteryLevel());
        progress.earnMasteryXp(1);
        assertEquals(2, progress.masteryLevel());
        assertEquals(1, progress.masterySkillPoints());
        assertEquals(0, progress.masteryAttributePoints());
        assertEquals(0, progress.masteryXpInLevel());
        assertEquals(150, progress.masteryXpToNextLevel());
        progress.earnMasteryXp(TownsfolkProgress.masteryXpForLevel(5) - progress.masteryXp());
        assertEquals(5, progress.masteryLevel());
        assertEquals(4, progress.masterySkillPoints());
        assertEquals(2, progress.masteryAttributePoints());
        assertTrue(progress.spendMasteryPoint(TownsfolkSkill.COOKING));
        assertTrue(progress.spendMasteryPoint(TownsfolkAttribute.STRENGTH));
        var town = new TownRecord();
        town.getTownsfolkProgress().put("resident", progress);
        var restored = gson.fromJson(gson.toJson(town), TownRecord.class).getTownsfolkProgress().get("resident");
        assertEquals(5, restored.masteryLevel());
        assertEquals(3, restored.masterySkillPoints());
        assertEquals(1, restored.masteryAttributePoints());
        assertEquals(2, restored.skill(TownsfolkSkill.COOKING));
        assertEquals(6, restored.attribute(TownsfolkAttribute.STRENGTH));
        assertEquals(0, restored.earnMasteryXp(-1));
        assertEquals(3, restored.masterySkillPoints());
    }

    @Test void capAndOverflowCannotGenerateEndlessPoints() {
        var progress = new TownsfolkProgress();
        assertEquals(10450, progress.earnMasteryXp(Long.MAX_VALUE));
        assertEquals(20, progress.masteryLevel());
        assertEquals(19, progress.masterySkillPoints());
        assertEquals(8, progress.masteryAttributePoints());
        assertEquals(0, progress.masteryXpToNextLevel());
        assertEquals(0, progress.earnMasteryXp(Long.MAX_VALUE));
        assertEquals(19, progress.masterySkillPoints());
        var corrupt = gson.fromJson("{\"masteryXp\":-1,\"spentMasterySkillPoints\":-999,\"spentMasteryAttributePoints\":-999}", TownsfolkProgress.class);
        assertEquals(1, corrupt.masteryLevel());
        assertEquals(0, corrupt.masterySkillPoints());
        assertFalse(corrupt.spendMasteryPoint(TownsfolkSkill.COOKING));
        assertFalse(corrupt.spendMasteryPoint(TownsfolkAttribute.STRENGTH));
    }

    @Test void pointSpendingPreservesIncapabilityAndCapsAndNeverConsumesUnavailablePoints() {
        var progress = new TownsfolkProgress();
        assertFalse(progress.spendMasteryPoint(TownsfolkSkill.COOKING));
        progress.earnMasteryXp(100);
        progress.setSkill(TownsfolkSkill.FARMING, 0);
        progress.setSkill(TownsfolkSkill.MINING, 10);
        assertFalse(progress.spendMasteryPoint(TownsfolkSkill.FARMING));
        assertFalse(progress.spendMasteryPoint(TownsfolkSkill.MINING));
        assertEquals(1, progress.masterySkillPoints());
        assertTrue(progress.spendMasteryPoint(TownsfolkSkill.COOKING));
        assertFalse(progress.spendMasteryPoint(TownsfolkSkill.COOKING));
        assertEquals(2, progress.skill(TownsfolkSkill.COOKING));
        progress.earnMasteryXp(TownsfolkProgress.masteryXpForLevel(5) - progress.masteryXp());
        progress.setAttribute(TownsfolkAttribute.STRENGTH, 20);
        assertFalse(progress.spendMasteryPoint(TownsfolkAttribute.STRENGTH));
        assertEquals(2, progress.masteryAttributePoints());
        assertTrue(progress.spendMasteryPoint(TownsfolkAttribute.VITALITY));
        assertTrue(progress.spendMasteryPoint(TownsfolkAttribute.VITALITY));
        assertFalse(progress.spendMasteryPoint(TownsfolkAttribute.VITALITY));
        assertEquals(7, progress.attribute(TownsfolkAttribute.VITALITY));
    }

    @Test void everyShardOrCatalystRaisesExactlyOneAndNeverUsesMasteryPoints() {
        var progress = new TownsfolkProgress();
        progress.earnMasteryXp(700);
        for (int level = 1; level < 10; level++) {
            progress.setSkill(TownsfolkSkill.COOKING, level);
            assertEquals(1, progress.skillCost(TownsfolkSkill.COOKING));
            assertTrue(progress.train(TownsfolkSkill.COOKING));
            assertEquals(level + 1, progress.skill(TownsfolkSkill.COOKING));
        }
        for (int level = 1; level < 20; level++) {
            progress.setAttribute(TownsfolkAttribute.STRENGTH, level);
            assertEquals(1, progress.attributeCost(TownsfolkAttribute.STRENGTH));
            assertTrue(progress.train(TownsfolkAttribute.STRENGTH));
            assertEquals(level + 1, progress.attribute(TownsfolkAttribute.STRENGTH));
        }
        assertEquals(0, progress.skillCost(TownsfolkSkill.COOKING));
        assertEquals(0, progress.attributeCost(TownsfolkAttribute.STRENGTH));
        assertEquals(4, progress.masterySkillPoints());
        assertEquals(2, progress.masteryAttributePoints());
    }

    @Test void failedWorkAndStatusChecksDoNotAwardMastery() {
        var progress = new TownsfolkProgress();
        for (var work : TownsfolkMastery.Work.values()) {
            assertEquals(0, TownsfolkMastery.completed(progress, work, false));
        }
        assertEquals(0, progress.masteryXp());
        assertEquals(10, TownsfolkMastery.completed(progress, TownsfolkMastery.Work.CRAFT, true));
        assertEquals(5, TownsfolkMastery.completed(progress, TownsfolkMastery.Work.DELIVERY, true));
        assertEquals(15, progress.masteryXp());
    }
}
