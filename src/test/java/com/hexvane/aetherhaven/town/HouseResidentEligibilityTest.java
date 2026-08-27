package com.hexvane.aetherhaven.town;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.tourist.TouristRecord;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class HouseResidentEligibilityTest {

    @Test
    void activeTouristWithoutQuestIsExcluded() {
        TownRecord town = new TownRecord();
        UUID touristUuid = UUID.randomUUID();
        town
            .getTouristRecords()
            .add(new TouristRecord("char_a", touristUuid, UUID.randomUUID(), false, false, 1L, 20));

        assertTrue(TownResidentEligibility.excludeFromHouseAssignmentPicker(town, touristUuid));
    }

    @Test
    void activeTouristWithQuestForDifferentTargetIsExcluded() {
        TownRecord town = new TownRecord();
        UUID touristUuid = UUID.randomUUID();
        UUID otherUuid = UUID.randomUUID();
        town
            .getTouristRecords()
            .add(new TouristRecord("char_a", touristUuid, UUID.randomUUID(), false, false, 1L, 20));
        town.addActiveQuest(AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK);
        town.setQuestTargetEntityUuid(AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK, otherUuid);

        assertTrue(TownResidentEligibility.excludeFromHouseAssignmentPicker(town, touristUuid));
    }

    @Test
    void activeTouristWithQuestAsTargetIsAllowed() {
        TownRecord town = new TownRecord();
        UUID touristUuid = UUID.randomUUID();
        town
            .getTouristRecords()
            .add(new TouristRecord("char_a", touristUuid, UUID.randomUUID(), false, false, 1L, 20));
        town.addActiveQuest(AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK);
        town.setQuestTargetEntityUuid(AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK, touristUuid);

        assertFalse(TownResidentEligibility.excludeFromHouseAssignmentPicker(town, touristUuid));
    }

    @Test
    void unhousedGuardWithoutQuestIsExcluded() {
        TownRecord town = new TownRecord();
        UUID guardUuid = UUID.randomUUID();
        town.getHiredGuardRecords().add(new HiredGuardRecord("guard_a", guardUuid, "profile", false));

        assertTrue(TownResidentEligibility.excludeFromHouseAssignmentPicker(town, guardUuid));
    }

    @Test
    void unhousedGuardWithQuestAsTargetIsAllowed() {
        TownRecord town = new TownRecord();
        UUID guardUuid = UUID.randomUUID();
        town.getHiredGuardRecords().add(new HiredGuardRecord("guard_a", guardUuid, "profile", false));
        town.addActiveQuest(AetherhavenConstants.QUEST_HOUSE_GUARD);
        town.setQuestTargetEntityUuid(AetherhavenConstants.QUEST_HOUSE_GUARD, guardUuid);

        assertFalse(TownResidentEligibility.excludeFromHouseAssignmentPicker(town, guardUuid));
    }

    @Test
    void randomTownsfolkUuidIsAllowed() {
        TownRecord town = new TownRecord();
        UUID uuid = UUID.randomUUID();

        assertFalse(TownResidentEligibility.excludeFromHouseAssignmentPicker(town, uuid));
    }

    @Test
    void activeTouristWithStaleQuestTargetUuidIsAllowedWhenInvited() {
        TownRecord town = new TownRecord();
        UUID staleTargetUuid = UUID.randomUUID();
        UUID liveTouristUuid = UUID.randomUUID();
        town
            .getTouristRecords()
            .add(
                new TouristRecord(
                    "char_a",
                    liveTouristUuid,
                    UUID.randomUUID(),
                    true,
                    false,
                    1L,
                    20
                )
            );
        town.addActiveQuest(AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK);
        town.setQuestTargetEntityUuid(AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK, staleTargetUuid);

        assertFalse(TownResidentEligibility.excludeFromHouseAssignmentPicker(town, liveTouristUuid));
    }

    @Test
    void unhousedGuardWithStaleQuestTargetUuidIsAllowedWhenOnlyGuard() {
        TownRecord town = new TownRecord();
        UUID staleTargetUuid = UUID.randomUUID();
        UUID liveGuardUuid = UUID.randomUUID();
        town.getHiredGuardRecords().add(new HiredGuardRecord("guard_a", liveGuardUuid, "profile", false));
        town.addActiveQuest(AetherhavenConstants.QUEST_HOUSE_GUARD);
        town.setQuestTargetEntityUuid(AetherhavenConstants.QUEST_HOUSE_GUARD, staleTargetUuid);

        assertFalse(TownResidentEligibility.excludeFromHouseAssignmentPicker(town, liveGuardUuid));
    }

    @Test
    void questTargetUuidSyncAllowsAssignmentAfterReconcile() {
        TownRecord town = new TownRecord();
        UUID staleTargetUuid = UUID.randomUUID();
        UUID liveTouristUuid = UUID.randomUUID();
        town
            .getTouristRecords()
            .add(new TouristRecord("char_a", liveTouristUuid, UUID.randomUUID(), false, false, 1L, 20));
        town.addActiveQuest(AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK);
        town.setQuestTargetEntityUuid(AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK, staleTargetUuid);

        assertTrue(TownResidentEligibility.excludeFromHouseAssignmentPicker(town, liveTouristUuid));

        town.replaceEntityUuidInQuestTargets(staleTargetUuid, liveTouristUuid);

        assertFalse(TownResidentEligibility.excludeFromHouseAssignmentPicker(town, liveTouristUuid));
    }
}
