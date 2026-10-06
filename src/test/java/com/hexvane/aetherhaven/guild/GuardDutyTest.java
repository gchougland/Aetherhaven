package com.hexvane.aetherhaven.guild;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.*;
import com.hexvane.aetherhaven.tourist.TouristRecord;
import org.junit.jupiter.api.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class GuardDutyTest {
    @Test void civilianWorkStateInitializesOnlyForLivingResidentsWithoutNeeds() {
        var town=new TownRecord();var id=UUID.randomUUID();
        var kind=com.hexvane.aetherhaven.villager.TownVillagerBinding.KIND_TOWNSFOLK;
        assertFalse(TownsfolkWorkRoster.initializeResidentNeeds(town,id,kind,false,false));
        GuildResidentInvitation.admit(town,id,"visitor");
        assertTrue(TownsfolkWorkRoster.initializeResidentNeeds(town,id,kind,false,false));
        assertFalse(TownsfolkWorkRoster.initializeResidentNeeds(town,id,kind,true,false));
        assertFalse(TownsfolkWorkRoster.initializeResidentNeeds(town,id,kind,false,true));
        assertFalse(TownsfolkWorkRoster.initializeResidentNeeds(town,id,"guard",false,false));
        GuardDutyService.recordDuty(town,id,"visitor","guard_knight");
        GuardDutyService.recordDuty(town,id,"visitor",null);
        assertTrue(TownsfolkWorkRoster.initializeResidentNeeds(town,id,kind,false,false));
    }
    @Test void dutyRoundTripPreservesPopulationIdentityPrioritiesAndTrainingThroughSave() {
        var town=new TownRecord();var id=UUID.randomUUID();
        town.getTouristRecords().add(new TouristRecord("briar",id,UUID.randomUUID(),true,true));
        var progress=new TownsfolkProgress();progress.setEmployed(true);progress.setSkill(TownsfolkSkill.FARMING,7);
        progress.setPriority(TownsfolkWorkType.LOGISTICS,2);town.getTownsfolkProgress().put("briar",progress);
        GuardDutyService.recordDuty(town,id,"briar","guard_knight");
        assertEquals(1,TownPopulation.count(town));assertEquals(id,town.getHiredGuardRecords().getFirst().getEntityUuid());
        var loaded=new Gson().fromJson(new Gson().toJson(town),TownRecord.class);
        GuardDutyService.recordDuty(loaded,id,"briar",null);
        assertTrue(TownAdmission.isMember(loaded,id));assertEquals(1,TownPopulation.count(loaded));
        assertTrue(loaded.getHiredGuardRecords().isEmpty());
        assertEquals(7,loaded.getTownsfolkProgress().get("briar").skill(TownsfolkSkill.FARMING));
        assertEquals(2,loaded.getTownsfolkProgress().get("briar").priority(TownsfolkWorkType.LOGISTICS));
        assertTrue(loaded.getTownsfolkProgress().get("briar").isEmployed());
    }
    @Test void oldGuardWithoutHomeRemainsResidentWhenReturningToCivilianLife() {
        var town=new TownRecord();var id=UUID.randomUUID();
        town.getHiredGuardRecords().add(new HiredGuardRecord("old_guard",id,"guard_archer",false));
        GuardDutyService.recordDuty(town,id,"old_guard",null);
        assertTrue(TownAdmission.isMember(town,id));assertEquals(1,TownPopulation.count(town));
    }
    @Test void rankLimitDoesNotBlockReturningOrChangingClassAndWorkHazardsBlockDutyChanges() {
        var town=new TownRecord();var id=UUID.randomUUID();
        town.getTouristRecords().add(new TouristRecord("worker",id,UUID.randomUUID(),true,true));
        assertNotNull(GuardDutyService.blocked(town,id,"worker",true,true,false,false,true,0));
        assertNull(GuardDutyService.blocked(town,id,"worker",true,true,false,false,false,0));
        GuardDutyService.recordDuty(town,id,"worker","guard_knight");
        assertNull(GuardDutyService.blocked(town,id,"worker",true,true,false,false,true,1));
        assertNotNull(GuardDutyService.blocked(town,id,"worker",false,true,false,false,true,1));
        assertNotNull(GuardDutyService.blocked(town,id,"worker",true,false,false,false,true,1));
        assertNotNull(GuardDutyService.blocked(town,id,"worker",true,true,true,false,true,1));
        assertNotNull(GuardDutyService.blocked(town,id,"worker",true,true,false,true,true,1));
        assertNotNull(GuardDutyService.blocked(town,UUID.randomUUID(),"outsider",true,true,false,false,true,10));
    }
    @Test void guildInvitationFreesDisplaySlotWithoutConsumingGuardCapacityOrDuplicatingCitizen() {
        var town=new TownRecord();var id=UUID.randomUUID();String key=id.toString();
        town.getGuildHallAdventurerNpcIds().add(key);town.getGuildHallAdventurerSlotByNpcId().put(key,1);town.getGuildHallAdventurerFilledSlots().add(1);
        GuildResidentInvitation.admit(town,id,"visitor");GuildResidentInvitation.admit(town,id,"visitor");
        assertTrue(town.getGuildHallAdventurerNpcIds().isEmpty());assertTrue(town.getGuildHallAdventurerSlotByNpcId().isEmpty());
        assertFalse(town.getGuildHallAdventurerFilledSlots().contains(1));assertTrue(town.getHiredGuardRecords().isEmpty());
        assertEquals(1,town.getTouristRecords().size());assertTrue(TownAdmission.isMember(town,id));assertEquals(1,TownPopulation.count(town));
    }
}
