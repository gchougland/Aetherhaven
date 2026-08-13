package com.hexvane.aetherhaven.guild;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.AetherhavenConstants;
import com.hexvane.aetherhaven.town.HiredGuardRecord;
import com.hexvane.aetherhaven.town.PlotInstance;
import com.hexvane.aetherhaven.town.PlotInstanceState;
import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class GuardHireServiceTest {

    @Test
    void removeHiredGuardFromTown_freesHouseSlotAndResidentRow() {
        TownRecord town = new TownRecord();
        UUID keep = UUID.randomUUID();
        UUID dismiss = UUID.randomUUID();
        town.getHiredGuardRecords().add(new HiredGuardRecord("keep", keep, "guard_knight", false));
        town.getHiredGuardRecords().add(new HiredGuardRecord("gone", dismiss, "guard_mage", true));
        town.getResidentNpcRecords()
            .add(new ResidentNpcRecord(AetherhavenConstants.NPC_GUARD_KNIGHT, TownVillagerBinding.KIND_GUARD, null, dismiss));

        PlotInstance house = new PlotInstance();
        house.setPlotId(UUID.randomUUID());
        house.setConstructionId(AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE);
        house.setState(PlotInstanceState.COMPLETE);
        house.setHomeResidentEntityUuid(dismiss);
        town.addPlotInstance(house);

        String characterId = GuardHireService.removeHiredGuardFromTown(town, dismiss, "gone");

        assertEquals("gone", characterId);
        assertEquals(1, town.getHiredGuardRecords().size());
        assertTrue(GuardHireService.isHiredGuard(town, keep));
        assertFalse(GuardHireService.isHiredGuard(town, dismiss));
        assertFalse(house.hasHomeResident(dismiss));
        assertTrue(town.getResidentNpcRecords().isEmpty());
    }

    @Test
    void removeHiredGuardFromTown_clearsHouseQuestWhenThisGuardWasTheTarget() {
        TownRecord town = new TownRecord();
        UUID dismiss = UUID.randomUUID();
        town.getHiredGuardRecords().add(new HiredGuardRecord("gone", dismiss, "guard_knight", false));
        town.addActiveQuest(AetherhavenConstants.QUEST_HOUSE_GUARD);
        town.setQuestTargetEntityUuid(AetherhavenConstants.QUEST_HOUSE_GUARD, dismiss);

        GuardHireService.removeHiredGuardFromTown(town, dismiss, null);

        assertFalse(town.hasQuestActive(AetherhavenConstants.QUEST_HOUSE_GUARD));
        assertNull(town.getQuestTargetEntityUuid(AetherhavenConstants.QUEST_HOUSE_GUARD));
    }

    @Test
    void removeHiredGuardFromTown_leavesOtherGuardsAndHousesAlone() {
        TownRecord town = new TownRecord();
        UUID keep = UUID.randomUUID();
        UUID dismiss = UUID.randomUUID();
        town.getHiredGuardRecords().add(new HiredGuardRecord("keep", keep, "guard_knight", true));
        town.getHiredGuardRecords().add(new HiredGuardRecord("gone", dismiss, "guard_rogue", false));

        PlotInstance keepHouse = new PlotInstance();
        keepHouse.setPlotId(UUID.randomUUID());
        keepHouse.setConstructionId(AetherhavenConstants.CONSTRUCTION_PLOT_HOUSE);
        keepHouse.setState(PlotInstanceState.COMPLETE);
        keepHouse.setHomeResidentEntityUuid(keep);
        town.addPlotInstance(keepHouse);

        GuardHireService.removeHiredGuardFromTown(town, dismiss, "gone");

        assertTrue(GuardHireService.isHiredGuard(town, keep));
        assertTrue(keepHouse.hasHomeResident(keep));
        assertEquals(1, town.getHiredGuardRecords().size());
    }
}
