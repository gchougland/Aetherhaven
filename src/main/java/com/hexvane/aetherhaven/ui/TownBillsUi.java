package com.hexvane.aetherhaven.ui;

import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hexvane.aetherhaven.AetherhavenPlugin;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.ui.builder.*;
import java.util.Comparator;
import java.util.UUID;

/** The journal and treasury render the same authoritative invoices. */
final class TownBillsUi {
    private TownBillsUi() {}
    static void build(UICommandBuilder c, UIEventBuilder e, TownRecord town, AetherhavenPlugin plugin, UUID player,
            com.hypixel.hytale.component.Store<com.hypixel.hytale.server.core.universe.world.storage.EntityStore> store, CombinedItemContainer inventory) {
        c.clear("#BillsPage");
        c.append("#BillsPage", "Aetherhaven/TownBills.ui");
        c.set("#BillsHelp.TextSpans", t("billsHelp"));
        c.set("#BillsTreasuryTitle.TextSpans", t("budgetAvailable"));
        c.set("#BillsUnpaidTitle.TextSpans", t("budgetUnpaid"));
        c.set("#BillsWeeklyTitle.TextSpans", t("budgetWeekly"));
        var budget = town == null ? new com.hexvane.aetherhaven.economy.TownPayrollService.Budget(0, 0, 0, 0)
            : com.hexvane.aetherhaven.economy.TownPayrollService.budget(town);
        long balance = town == null || inventory == null || player == null ? 0 : GoldCoinPayment.totalAvailable(town, inventory, town.playerCanSpendTreasuryGold(player));
        c.set("#BillsBalance.TextSpans", t("budgetGold").param("gold", balance));
        c.set("#BillsUnpaid.TextSpans", t("budgetGold").param("gold", budget.unpaidGold()));
        c.set("#BillsWeekly.TextSpans", t("budgetGold").param("gold", budget.weeklyGold()));
        c.set("#BillsEmployees.TextSpans", t("budgetEmployees").param("count", budget.employees()));
        var tax = town == null || plugin == null ? null : com.hexvane.aetherhaven.economy.TownTaxService
            .computeTaxMorningBreakdown(town, store, plugin.getConfig().get(), plugin.getConstructionCatalog());
        c.set("#BillsTax.TextSpans", t("budgetGold").param("gold", tax == null || !tax.townHallComplete() ? 0 : tax.finalTotal()));
        c.set("#BillsOverdue.TextSpans", t("budgetOverdue").param("gold", budget.overdueGold()));
        c.set("#BillsCoverage.TextSpans", t(balance >= budget.unpaidGold() ? "budgetCovered" : "budgetShort")
            .param("gold", Math.max(0, budget.unpaidGold() - balance)));
        c.set("#BillsEmpty.TextSpans", t(town == null ? "noTown" : "noBills"));
        if (town == null || plugin == null) return;
        var payroll = town.getPayroll();
        var bills = payroll.bills().stream().filter(bill -> !bill.paid())
            .sorted(Comparator.comparingLong(com.hexvane.aetherhaven.economy.TownPayroll.Bill::dueMinute)).toList();
        c.set("#BillsEmpty.Visible", bills.isEmpty());
        int i = 0;
        for (var bill : bills) {
            c.append("#BillsRows", "Aetherhaven/TownBillRow.ui");
            String row = "#BillsRows[" + i++ + "]";
            var definition = plugin.getTownsfolkCharacterCatalog().byId(bill.characterId());
            String name = definition != null && definition.getDisplayName() != null ? definition.getDisplayName() : bill.characterId();
            c.set(row + " #Name.Text", name);
            long remaining = bill.dueMinute() - payroll.onlineMinutes();
            var due = remaining <= 0 ? t("overdue")
                : t("due").param("days", Math.max(1, (remaining + 1439) / 1440));
            c.set(row + " #Due.TextSpans", due);
            c.set(row + " #Pay.TextSpans", t("pay").param("gold", bill.amount()));
            c.set(row + " #Pay.Disabled", player == null || !town.hasMemberOrOwner(player) || inventory == null
                || balance < bill.amount());
            c.set(row + " #Pay.TooltipTextSpans", t(balance < bill.amount() ? "notEnoughGold" : "payHint"));
            e.addEventBinding(CustomUIEventBindingType.Activating, row + " #Pay",
                new EventData().append("Action", "PayBill").append("BillId", bill.id()), false);
        }
    }
    private static Message t(String key) { return Message.translation("aetherhaven_workforce." + key); }
}
