package com.hexvane.aetherhaven.town;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.economy.TownPayroll;
import com.hexvane.aetherhaven.townsfolk.*;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkCharacterDefinition;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class TownPayrollAndWorkTest {
    private final com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer inventory =
        new com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer(new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short) 1));
    private static final String WORKER = "apprentice";

    @Test void someoneOutsideTownCannotPayItsBillsThroughAStalePage() {
        var town = new TownRecord(); town.setOwner(java.util.UUID.randomUUID(), "Owner");
        town.setTreasuryGoldCoinCount(100);
        town.getPayroll().ensureCurrentBill(WORKER);
        assertFalse(com.hexvane.aetherhaven.economy.TownPayrollService.pay(town, null, java.util.UUID.randomUUID(), town.getPayroll().bills().getFirst().id(), inventory));
        assertEquals(100, town.getTreasuryGoldCoinCount());
    }

    @Test void offlineTimeDoesNotAdvanceOrIssueBills() {
        var payroll = new TownPayroll();
        payroll.advance(TownPayroll.WEEK_MINUTES * 4, false, Set.of(WORKER));
        assertEquals(0, payroll.onlineMinutes());
        assertTrue(payroll.bills().isEmpty());
    }

    @Test void billsGiveOneWeekToPayAndNeverAutomaticallyDebitTheTreasury() {
        var town = new TownRecord();
        town.setTreasuryGoldCoinCount(100);
        var payroll = town.getPayroll();
        payroll.ensureCurrentBill(WORKER);
        payroll.advance(TownPayroll.WEEK_MINUTES - 1, true, Set.of(WORKER));
        assertFalse(payroll.overdue(WORKER));
        payroll.advance(1, true, Set.of(WORKER));
        assertTrue(payroll.overdue(WORKER));
        assertEquals(100, town.getTreasuryGoldCoinCount());
        payroll.advance(TownPayroll.WEEK_MINUTES * 9, true, Set.of(WORKER));
        assertEquals(1, payroll.bills().size(), "Stopped work must not accumulate more wage debt");
    }

    @Test void invoicesCannotBePaidTwiceOrWithoutEnoughMoney() {
        var payroll = new TownPayroll();
        payroll.ensureCurrentBill(WORKER);
        var bill = payroll.bills().getFirst();
        assertEquals(-1, payroll.settle(bill.id(), TownPayroll.WEEKLY_WAGE - 1));
        assertFalse(bill.paid());
        assertEquals(TownPayroll.WEEKLY_WAGE, payroll.settle(bill.id(), TownPayroll.WEEKLY_WAGE));
        assertEquals(-1, payroll.settle(bill.id(), 999));
        assertEquals(-1, payroll.settle("old window", 999));
    }

    @Test void changingPrioritiesTrainingAndReemploymentDoesNotChangeOrResetWages() {
        var payroll = new TownPayroll();
        var progress = new TownsfolkProgress();
        progress.setEmployed(true);
        payroll.ensureCurrentBill(WORKER);
        var original = payroll.bills().getFirst();
        progress.setPriority(TownsfolkWorkType.SMELTING, 1);
        progress.setPriority(TownsfolkWorkType.ARMOR_CRAFTING, 4);
        progress.setSkill(TownsfolkSkill.BLACKSMITHING, 10);
        progress.setEmployed(false);
        payroll.advance(1000, true, Set.of());
        progress.setEmployed(true);
        payroll.ensureCurrentBill(WORKER);
        assertSame(original, payroll.bills().getFirst());
        assertEquals(TownPayroll.WEEKLY_WAGE, original.amount());
        assertEquals(TownPayroll.WEEK_MINUTES, original.dueMinute());
    }

    @Test void earlyPaymentCoversTheRestOfTheWeekThenCreatesOneNewBill() {
        var payroll = new TownPayroll();
        payroll.ensureCurrentBill(WORKER);
        payroll.settle(payroll.bills().getFirst().id(), 100);
        payroll.advance(TownPayroll.WEEK_MINUTES - 1, true, Set.of(WORKER));
        assertEquals(1, payroll.bills().size());
        payroll.advance(1, true, Set.of(WORKER));
        assertEquals(2, payroll.bills().size());
        assertEquals(2 * TownPayroll.WEEK_MINUTES, payroll.bills().getLast().dueMinute());
        assertFalse(payroll.overdue(WORKER));
    }

    @Test void warningOccursOnceAndSurvivesSavingTogetherWithDebt() {
        var payroll = new TownPayroll();
        payroll.ensureCurrentBill(WORKER);
        payroll.advance(6 * 1440, true, Set.of(WORKER));
        assertEquals(Set.of(WORKER), Set.copyOf(payroll.takeDueSoonWarnings()));
        var gson = new Gson();
        payroll = gson.fromJson(gson.toJson(payroll), TownPayroll.class);
        assertTrue(payroll.takeDueSoonWarnings().isEmpty());
        payroll.advance(1440, true, Set.of(WORKER));
        assertTrue(payroll.overdue(WORKER));
        payroll.settle(payroll.bills().getFirst().id(), 100);
        assertFalse(payroll.overdue(WORKER));
    }

    @Test void guardDeathUnemploymentIncapabilityAndDebtBlockWorkWithoutErasingPriorities() {
        var progress = new TownsfolkProgress();
        progress.setEmployed(true);
        progress.setPriority(TownsfolkWorkType.SMELTING, 1);
        progress.setPriority(TownsfolkWorkType.ARMOR_CRAFTING, 3);
        assertTrue(progress.canPerform(TownsfolkWorkType.SMELTING, false, false, true));
        assertFalse(progress.canPerform(TownsfolkWorkType.SMELTING, true, false, true));
        assertFalse(progress.canPerform(TownsfolkWorkType.SMELTING, false, true, true));
        assertFalse(progress.canPerform(TownsfolkWorkType.SMELTING, false, false, false));
        progress.setEmployed(false);
        assertFalse(progress.canPerform(TownsfolkWorkType.SMELTING, false, false, true));
        assertEquals(1, progress.priority(TownsfolkWorkType.SMELTING));
        assertEquals(3, progress.priority(TownsfolkWorkType.ARMOR_CRAFTING));
        progress.setSkill(TownsfolkSkill.BLACKSMITHING, 0);
        progress.setPriority(TownsfolkWorkType.SMELTING, 1);
        assertEquals(0, progress.priority(TownsfolkWorkType.SMELTING));
    }

    @Test void crossModGuardProfilesUseTheirActualNpcClass() {
        var definition = new Gson().fromJson("{\"id\":\"example\",\"equipmentProfileId\":\"lotr_guard_hobbit\"}", TownsfolkCharacterDefinition.class);
        var progress = TownsfolkProgressService.startingProgress(definition, "Aetherhaven_Guard_Knight");
        assertEquals(8, progress.attribute(TownsfolkAttribute.STRENGTH));
        assertEquals(7, progress.attribute(TownsfolkAttribute.VITALITY));
    }
}
