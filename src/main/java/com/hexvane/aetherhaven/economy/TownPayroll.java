package com.hexvane.aetherhaven.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Manual weekly invoices. The caller advances this clock only during member-online game time. */
public final class TownPayroll {
    public static final long WEEK_MINUTES = 7 * 24 * 60;
    public static final long WEEKLY_WAGE = 20;
    private long onlineMinutes;
    private List<Bill> bills = new ArrayList<>();

    public static final class Bill {
        private String id = UUID.randomUUID().toString();
        private String characterId;
        private long amount;
        private long dueMinute;
        private boolean paid;
        private boolean warned;
        public String id() { return id; }
        public String characterId() { return characterId; }
        public long amount() { return Math.max(0, amount); }
        public long dueMinute() { return dueMinute; }
        public boolean paid() { return paid; }
    }

    public long onlineMinutes() { return Math.max(0, onlineMinutes); }
    public List<Bill> bills() {
        if (bills == null) bills = new ArrayList<>();
        return java.util.Collections.unmodifiableList(bills);
    }
    public boolean overdue(String characterId) {
        return bills().stream().anyMatch(b -> !b.paid && characterId.equals(b.characterId) && b.dueMinute <= onlineMinutes());
    }
    public void ensureCurrentBill(String characterId) {
        if (characterId == null || characterId.isBlank()) return;
        Bill last = null;
        for (var bill : bills()) if (characterId.equals(bill.characterId)) last = bill;
        if (last != null && (!last.paid || last.dueMinute > onlineMinutes())) return;
        var bill = new Bill();
        bill.characterId = characterId;
        bill.amount = WEEKLY_WAGE;
        bill.dueMinute = Math.addExact(onlineMinutes(), WEEK_MINUTES);
        bills.add(bill);
        // Retain all unpaid invoices and a bounded receipt history.
        while (bills.size() > 128) {
            Bill removable = bills.stream().filter(b -> b.paid && b.dueMinute <= onlineMinutes()).findFirst().orElse(null);
            if (removable == null) break;
            bills.remove(removable);
        }
    }
    public void advance(long minutes, boolean memberOnline, Set<String> employedLivingCivilians) {
        if (!memberOnline || minutes <= 0) return;
        onlineMinutes = Math.addExact(onlineMinutes(), minutes);
        for (String character : employedLivingCivilians) ensureCurrentBill(character);
    }
    public List<String> takeDueSoonWarnings() {
        List<String> warnings = new ArrayList<>();
        for (var bill : bills()) if (!bill.paid && !bill.warned && bill.dueMinute - onlineMinutes() <= 24 * 60) {
            bill.warned = true;
            warnings.add(bill.characterId);
        }
        return warnings;
    }
    /** Returns the amount to debit, or -1 if already paid, stale, or unaffordable. Run on the owning town thread. */
    public long settle(String billId, long treasuryBalance) {
        for (var bill : bills()) if (bill.id.equals(billId)) {
            if (bill.paid || bill.amount() > treasuryBalance) return -1;
            bill.paid = true;
            return bill.amount();
        }
        return -1;
    }
}
