package com.hexvane.aetherhaven.warehouse;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
import com.hexvane.aetherhaven.economy.TownPayroll;
import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.*;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class CourierAssignmentsTest {
    private final UUID owner = UUID.randomUUID();
    private TownRecord town() {
        var town = new TownRecord(); town.setOwner(owner, "Test");
        town.getResidentNpcRecords().add(new ResidentNpcRecord("Aetherhaven_Warehouse_Manager", WarehouseAccess.MANAGER_KIND, null, UUID.randomUUID()));
        return town;
    }
    private TownsfolkWorkRoster.Worker worker(boolean guard, boolean alive) {
        return new TownsfolkWorkRoster.Worker("test", "Test Resident", UUID.randomUUID(), guard, alive, new TownsfolkProgress());
    }

    @Test void explicitPriorityEnablesLogisticsWithoutExcludingOtherProfessions() {
        var town = town(); var worker = worker(false, true); var progress = worker.progress();
        progress.setPriority(TownsfolkWorkType.CRAFTING, 2);
        CourierAssignments.setPriority(town, owner, worker, 1);
        CourierAssignments.setPriority(town, owner, worker, 1);
        assertTrue(progress.priority(TownsfolkWorkType.LOGISTICS) > 0); assertTrue(progress.isEmployed());
        assertEquals(1, town.getPayroll().bills().size());
        assertEquals(TownPayroll.WEEKLY_WAGE, town.getPayroll().bills().getFirst().amount());
        assertEquals(2, progress.priority(TownsfolkWorkType.CRAFTING));
        assertTrue(progress.canPerform(TownsfolkWorkType.CRAFTING, false, false, true));
        assertTrue(CourierAssignments.ready(town, worker));
        CourierAssignments.setPriority(town, owner, worker, 0);
        assertTrue(progress.canPerform(TownsfolkWorkType.CRAFTING, false, false, true));
        assertEquals(1, town.getPayroll().bills().size());
    }

    @Test void guardDeathIncapabilityAndOverdueWagesPreventAssignment() {
        for (int condition = 0; condition < 4; condition++) {
            var town = town(); var worker = worker(condition == 0, condition != 1);
            if (condition == 2) worker.progress().setSkill(TownsfolkSkill.LOGISTICS, 0);
            if (condition == 3) {
                town.getPayroll().ensureCurrentBill("test");
                town.getPayroll().advance(TownPayroll.WEEK_MINUTES, true, Set.of("test"));
            }
            CourierAssignments.setPriority(town, owner, worker, 1);
            assertFalse(worker.progress().priority(TownsfolkWorkType.LOGISTICS) > 0);
        }
    }

    @Test void existingCourierStopsForGuardDutyDeathUnemploymentOrDebt() {
        var town = town(); var original = worker(false, true);
        CourierAssignments.setPriority(town, owner, original, 1);
        var guard = new TownsfolkWorkRoster.Worker("test", "Test", original.entityId(), true, true, original.progress());
        var dead = new TownsfolkWorkRoster.Worker("test", "Test", original.entityId(), false, false, original.progress());
        assertFalse(CourierAssignments.ready(town, guard)); assertFalse(CourierAssignments.ready(town, dead));
        original.progress().setEmployed(false); assertFalse(CourierAssignments.ready(town, original));
        original.progress().setEmployed(true);
        town.getPayroll().advance(TownPayroll.WEEK_MINUTES, true, Set.of("test"));
        assertFalse(CourierAssignments.ready(town, original));
        assertTrue(original.progress().priority(TownsfolkWorkType.LOGISTICS) > 0);
    }

    @Test void managerDeathDoesNotRemoveAccessButNonmemberCannotAssign() {
        var town = town(); var worker = worker(false, true);
        town.getResidentNpcRecords().getFirst().setPendingDawnRevival(true);
        assertTrue(WarehouseAccess.hasManager(town)); assertTrue(WarehouseAccess.canBrowse(town, owner));
        CourierAssignments.setPriority(town, UUID.randomUUID(), worker, 1);
        assertFalse(worker.progress().priority(TownsfolkWorkType.LOGISTICS) > 0);
        CourierAssignments.setPriority(town, owner, worker, 1);
        assertTrue(worker.progress().priority(TownsfolkWorkType.LOGISTICS) > 0);
        assertFalse(WarehouseAccess.canBrowse(town, UUID.randomUUID()));
    }

    @Test void noManagerCannotAssignButStoredGoodsRemainAccessible() {
        var town = town(); town.getResidentNpcRecords().clear();
        var worker = worker(false, true);
        CourierAssignments.setPriority(town, owner, worker, 1);
        assertFalse(worker.progress().priority(TownsfolkWorkType.LOGISTICS) > 0); assertTrue(town.getPayroll().bills().isEmpty());
        assertTrue(WarehouseAccess.canBrowse(town, owner));
    }

    @Test void capacityCountsStacksAndSavedRoleSurvivesReload() {
        assertEquals(0, CourierAssignments.carryingStacks(0));
        assertEquals(1, CourierAssignments.carryingStacks(1));
        assertEquals(2, CourierAssignments.carryingStacks(3));
        assertEquals(5, CourierAssignments.carryingStacks(10));
        assertEquals(5, CourierAssignments.carryingStacks(999));
        var progress = new TownsfolkProgress(); progress.setPriority(TownsfolkWorkType.LOGISTICS, 1); progress.setEmployed(true);
        var reloaded = new Gson().fromJson(new Gson().toJson(progress), TownsfolkProgress.class);
        assertTrue(reloaded.priority(TownsfolkWorkType.LOGISTICS) > 0); assertTrue(reloaded.isEmployed());
    }

    @Test void legacyCourierSaveMigratesWithoutCallingTheObsoleteRoleApi() {
        var progress = new Gson().fromJson("{\"courier\":true,\"employed\":true}", TownsfolkProgress.class);
        assertEquals(1, progress.priority(TownsfolkWorkType.LOGISTICS));
        progress.setPriority(TownsfolkWorkType.LOGISTICS, 0);
        assertEquals(0, progress.priority(TownsfolkWorkType.LOGISTICS));
    }

    @Test void explicitPriorityChangesDoNotIssueAdditionalWages() {
        var town=town();var worker=worker(false,true);
        CourierAssignments.setPriority(town,owner,worker,1);
        CourierAssignments.setPriority(town,owner,worker,4);
        assertEquals(4,worker.progress().priority(TownsfolkWorkType.LOGISTICS));
        assertEquals(1,town.getPayroll().bills().size());
        CourierAssignments.setPriority(town,owner,worker,5);
        assertEquals(4,worker.progress().priority(TownsfolkWorkType.LOGISTICS));
    }
}
