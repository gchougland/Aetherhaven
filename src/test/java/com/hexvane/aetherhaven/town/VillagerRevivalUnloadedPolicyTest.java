package com.hexvane.aetherhaven.town;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class VillagerRevivalUnloadedPolicyTest {

    @Test
    void confirmedDawnDeathSkipsUnloadedRevivalGuard() {
        ResidentNpcRecord record =
            new ResidentNpcRecord(
                AetherhavenConstants.ELDER_NPC_ROLE_ID,
                TownVillagerBinding.KIND_ELDER,
                null,
                UUID.randomUUID()
            );
        record.setPendingDawnRevival(true);

        assertFalse(VillagerRevivalService.appliesUnloadedRevivalGuard(record));
    }

    @Test
    void gaiaRevivalAppliesUnloadedGuardWhenDeathNotConfirmed() {
        ResidentNpcRecord record =
            new ResidentNpcRecord(
                AetherhavenConstants.ELDER_NPC_ROLE_ID,
                TownVillagerBinding.KIND_ELDER,
                null,
                UUID.randomUUID()
            );

        assertTrue(VillagerRevivalService.appliesUnloadedRevivalGuard(record));
    }

    @Test
    void townStillTracksElderUuidForUnloadedCheck() {
        UUID elderUuid = UUID.randomUUID();
        TownRecord town = new TownRecord();
        town.setElderEntityUuid(elderUuid);

        assertTrue(town.getElderEntityUuid().equals(elderUuid));
    }
}
