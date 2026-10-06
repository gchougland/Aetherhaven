package com.hexvane.aetherhaven.town;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.economy.TownEconomyPresence;
import com.hexvane.aetherhaven.economy.TownPayroll;
import com.hexvane.aetherhaven.economy.TownPayrollService;
import com.hexvane.aetherhaven.tourist.TouristRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkCharacterBinding;
import com.hexvane.aetherhaven.townsfolk.TownsfolkProgress;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class TownPayrollBudgetTest {
    @Test void onlyAnOwnerOrMemberPresentInTheTownWorldAdvancesEconomy() {
        var town = new TownRecord();
        var owner = UUID.randomUUID();
        var member = UUID.randomUUID();
        var outsider = UUID.randomUUID();
        town.setOwner(owner, "Owner");
        town.getMemberRolesRaw().put(member.toString(), "BOTH");
        var payroll = town.getPayroll();
        payroll.ensureCurrentBill("worker");
        payroll.advance(1440, TownEconomyPresence.hasMember(town, Set.of(outsider)), Set.of("worker"));
        assertEquals(0, payroll.onlineMinutes(), "An online member in another world is absent from this world's roster");
        payroll.advance(1440, TownEconomyPresence.hasMember(town, Set.of(owner, outsider)), Set.of("worker"));
        payroll.advance(1440, TownEconomyPresence.hasMember(town, Set.of(member)), Set.of("worker"));
        assertEquals(2880, payroll.onlineMinutes());
        assertFalse(TownEconomyPresence.hasMember(town, Set.of()));
    }

    @Test void weeklyBudgetCountsEachLivingEmployedCivilianOnceRegardlessOfPriority() {
        var town = new TownRecord();
        var worker = resident(town, "worker", true);
        town.getResidentNpcRecords().add(worker); // Old duplicate roster rows must not inflate salary.
        resident(town, "courier", true);
        resident(town, "guard", true).setKind(TownVillagerBinding.KIND_GUARD);
        resident(town, "dead", true).setPendingDawnRevival(true);
        resident(town, "unemployed", true);
        town.getTownsfolkProgress().get("unemployed").setEmployed(false);
        resident(town, "visitor", false);
        var budget = TownPayrollService.budget(town);
        assertEquals(Set.of("worker", "courier"), TownPayrollService.employedCharacters(town));
        assertEquals(2, budget.employees());
        assertEquals(2 * TownPayroll.WEEKLY_WAGE, budget.weeklyGold());
        assertEquals(0, budget.unpaidGold());
        assertTrue(town.getPayroll().bills().isEmpty(), "Opening Bills must not issue invoices");
        town.getTownsfolkProgress().get("worker").setPriority(TownsfolkWorkType.SMELTING, 1);
        assertEquals(budget, TownPayrollService.budget(town));
    }

    @Test void summaryKeepsFormerEmployeeDebtAndSeparatesPaidCurrentAndOverdueBillsAfterReload() {
        var town = new TownRecord();
        resident(town, "worker", true);
        var payroll = town.getPayroll();
        payroll.ensureCurrentBill("former");
        payroll.ensureCurrentBill("paid");
        payroll.settle(payroll.bills().getLast().id(), 100);
        payroll.advance(TownPayroll.WEEK_MINUTES, true, Set.of());
        payroll.ensureCurrentBill("worker");
        var restored = new Gson().fromJson(new Gson().toJson(town), TownRecord.class);
        var summary = TownPayrollService.budget(restored);
        assertEquals(1, summary.employees());
        assertEquals(20, summary.weeklyGold());
        assertEquals(40, summary.unpaidGold());
        assertEquals(20, summary.overdueGold());
        assertEquals(3, restored.getPayroll().bills().size());
        restored.getPayroll().settle(restored.getPayroll().bills().getFirst().id(), 100);
        assertEquals(0, TownPayrollService.budget(restored).overdueGold());
        assertEquals(20, TownPayrollService.budget(restored).unpaidGold());
    }

    private static ResidentNpcRecord resident(TownRecord town, String id, boolean citizen) {
        var uuid = UUID.randomUUID();
        var row = new ResidentNpcRecord("Aetherhaven_Townsfolk", TownVillagerBinding.KIND_TOWNSFOLK, null, uuid);
        row.setTownsfolkIdentity(new TownsfolkCharacterBinding(id, "", "", "", List.of()));
        town.getResidentNpcRecords().add(row);
        town.getTouristRecords().add(new TouristRecord(id, uuid, UUID.randomUUID(), citizen, citizen));
        var progress = new TownsfolkProgress();
        progress.setEmployed(true);
        town.getTownsfolkProgress().put(id, progress);
        return row;
    }
}
