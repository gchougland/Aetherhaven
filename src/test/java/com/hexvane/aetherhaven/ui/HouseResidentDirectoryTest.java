package com.hexvane.aetherhaven.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.construction.ConstructionCatalog;
import com.hexvane.aetherhaven.town.HiredGuardRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.tourist.TouristRecord;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class HouseResidentDirectoryTest {

    @Test
    void offlineCandidatesIncludeTouristWithActiveHousingQuest() {
        TownRecord town = new TownRecord();
        UUID touristUuid = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        town
            .getTouristRecords()
            .add(new TouristRecord("char_a", touristUuid, UUID.randomUUID(), false, false, 1L, 20));
        town.addActiveQuest(AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK);
        town.setQuestTargetEntityUuid(AetherhavenConstants.QUEST_HOUSE_TOWNSFOLK, touristUuid);

        Map<UUID, HouseResidentDirectory.HouseResidentRow> rows =
            HouseResidentDirectory.collectOfflineAssignableCandidates(
                town,
                ConstructionCatalog.empty(),
                plotId,
                false
            );

        assertTrue(rows.containsKey(touristUuid));
    }

    @Test
    void offlineCandidatesExcludeTouristWithoutHousingQuest() {
        TownRecord town = new TownRecord();
        UUID touristUuid = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        town
            .getTouristRecords()
            .add(new TouristRecord("char_a", touristUuid, UUID.randomUUID(), false, false, 1L, 20));

        Map<UUID, HouseResidentDirectory.HouseResidentRow> rows =
            HouseResidentDirectory.collectOfflineAssignableCandidates(
                town,
                ConstructionCatalog.empty(),
                plotId,
                false
            );

        assertFalse(rows.containsKey(touristUuid));
    }

    @Test
    void offlineCandidatesIncludeGuardWithActiveHousingQuest() {
        TownRecord town = new TownRecord();
        UUID guardUuid = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        town.getHiredGuardRecords().add(new HiredGuardRecord("guard_a", guardUuid, "guard_knight", false));
        town.addActiveQuest(AetherhavenConstants.QUEST_HOUSE_GUARD);
        town.setQuestTargetEntityUuid(AetherhavenConstants.QUEST_HOUSE_GUARD, guardUuid);

        Map<UUID, HouseResidentDirectory.HouseResidentRow> rows =
            HouseResidentDirectory.collectOfflineAssignableCandidates(
                town,
                ConstructionCatalog.empty(),
                plotId,
                false
            );

        assertTrue(rows.containsKey(guardUuid));
    }

    @Test
    void offlineCandidatesExcludeUnhousedGuardWithoutHousingQuest() {
        TownRecord town = new TownRecord();
        UUID guardUuid = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
        town.getHiredGuardRecords().add(new HiredGuardRecord("guard_a", guardUuid, "guard_knight", false));

        Map<UUID, HouseResidentDirectory.HouseResidentRow> rows =
            HouseResidentDirectory.collectOfflineAssignableCandidates(
                town,
                ConstructionCatalog.empty(),
                plotId,
                false
            );

        assertFalse(rows.containsKey(guardUuid));
    }

    @Test
    void offlineCandidatesIncludeInvitedTouristWhenQuestTargetUuidIsStale() {
        TownRecord town = new TownRecord();
        UUID staleTargetUuid = UUID.randomUUID();
        UUID liveTouristUuid = UUID.randomUUID();
        UUID plotId = UUID.randomUUID();
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

        Map<UUID, HouseResidentDirectory.HouseResidentRow> rows =
            HouseResidentDirectory.collectOfflineAssignableCandidates(
                town,
                ConstructionCatalog.empty(),
                plotId,
                false
            );

        assertTrue(rows.containsKey(liveTouristUuid));
    }
}
