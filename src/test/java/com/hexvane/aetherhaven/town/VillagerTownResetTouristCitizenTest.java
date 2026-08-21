package com.hexvane.aetherhaven.town;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.tourist.TouristRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("tourist")
class VillagerTownResetTouristCitizenTest {

    @Test
    void genericTownsfolkResidentRecordMatchesErroneousSyncRow() {
        ResidentNpcRecord record =
            new ResidentNpcRecord(
                AetherhavenConstants.NPC_TOWNSFOLK,
                TownVillagerBinding.KIND_TOWNSFOLK,
                null,
                UUID.randomUUID()
            );

        assertTrue(VillagerTownResetService.isGenericTownsfolkResidentRecord(record));
    }

    @Test
    void storyResidentRecordIsNotGenericTownsfolkRow() {
        ResidentNpcRecord record =
            new ResidentNpcRecord(
                AetherhavenConstants.ELDER_NPC_ROLE_ID,
                TownVillagerBinding.KIND_ELDER,
                null,
                UUID.randomUUID()
            );

        assertFalse(VillagerTownResetService.isGenericTownsfolkResidentRecord(record));
    }

    @Test
    void invitedTouristCountsAsSettlerForReset() {
        TouristRecord invited =
            new TouristRecord("char_a", UUID.randomUUID(), UUID.randomUUID(), true, false, 1L);
        TouristRecord visiting =
            new TouristRecord("char_b", UUID.randomUUID(), UUID.randomUUID(), false, false, 1L);
        TouristRecord citizen =
            new TouristRecord("char_c", UUID.randomUUID(), UUID.randomUUID(), true, true, 1L);

        assertTrue(VillagerTownResetService.isTouristSettlerForTest(invited));
        assertFalse(VillagerTownResetService.isTouristSettlerForTest(visiting));
        assertTrue(VillagerTownResetService.isTouristSettlerForTest(citizen));
    }
}
