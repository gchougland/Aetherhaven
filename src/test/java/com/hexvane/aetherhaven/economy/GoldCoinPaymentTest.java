package com.hexvane.aetherhaven.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.economy.GoldCoinPayment.SpendBreakdown;
import com.hexvane.aetherhaven.economy.api.GoldAccount;
import com.hexvane.aetherhaven.town.TownRecord;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("economy")
class GoldCoinPaymentTest {

    /** A balance in a long, with optional refusals (a full coin inventory, a provider that fails). */
    private static final class MemoryAccount implements GoldAccount {
        long balance;
        boolean refuseDeposits;
        boolean refuseWithdrawals;

        MemoryAccount(long balance) {
            this.balance = balance;
        }

        @Override
        public long balance() {
            return balance;
        }

        @Override
        public boolean withdraw(long amount) {
            if (refuseWithdrawals || amount > balance) {
                return false;
            }
            balance -= amount;
            return true;
        }

        @Override
        public boolean deposit(long amount) {
            if (refuseDeposits) {
                return false;
            }
            balance += amount;
            return true;
        }
    }

    private static TownRecord town(long gold) {
        TownRecord town = new TownRecord();
        town.setTreasuryGoldCoinCount(gold);
        return town;
    }

    @Test
    void treasuryFirstThenPlayer() {
        TownRecord town = town(8);
        MemoryAccount account = new MemoryAccount(15);
        SpendBreakdown paid = GoldCoinPayment.trySpendReturningBreakdown(town, account, 20, true);
        assertNotNull(paid);
        assertEquals(8, paid.fromTreasury());
        assertEquals(12, paid.fromPlayer());
        assertEquals(0, town.getTreasuryGoldCoinCount());
        assertEquals(3, account.balance());
    }

    @Test
    void treasuryCoversAllWhenItCan() {
        TownRecord town = town(50);
        MemoryAccount account = new MemoryAccount(5);
        SpendBreakdown paid = GoldCoinPayment.trySpendReturningBreakdown(town, account, 20, true);
        assertEquals(new SpendBreakdown(20, 0), paid);
        assertEquals(30, town.getTreasuryGoldCoinCount());
        assertEquals(5, account.balance());
    }

    @Test
    void refusesWhenTheSumDoesNotCover() {
        TownRecord town = town(8);
        MemoryAccount account = new MemoryAccount(11);
        assertFalse(GoldCoinPayment.canAfford(town, account, 20, true));
        assertNull(GoldCoinPayment.trySpendReturningBreakdown(town, account, 20, true));
        assertEquals(8, town.getTreasuryGoldCoinCount());
        assertEquals(11, account.balance());
    }

    @Test
    void treasuryRestoredWhenTheAccountRefuses() {
        TownRecord town = town(8);
        MemoryAccount account = new MemoryAccount(15);
        account.refuseWithdrawals = true;
        assertNull(GoldCoinPayment.trySpendReturningBreakdown(town, account, 20, true));
        assertEquals(8, town.getTreasuryGoldCoinCount());
        assertEquals(15, account.balance());
    }

    @Test
    void treasuryIgnoredWhenNotAllowed() {
        TownRecord town = town(100);
        MemoryAccount account = new MemoryAccount(15);
        assertEquals(15, GoldCoinPayment.totalAvailable(town, account, false));
        assertFalse(GoldCoinPayment.canAfford(town, account, 20, false));
        assertNull(GoldCoinPayment.trySpendReturningBreakdown(town, account, 20, false));
        assertEquals(new SpendBreakdown(0, 10), GoldCoinPayment.trySpendReturningBreakdown(town, account, 10, false));
        assertEquals(100, town.getTreasuryGoldCoinCount());
        assertEquals(5, account.balance());
    }

    @Test
    void noTownMeansPlayerOnly() {
        MemoryAccount account = new MemoryAccount(15);
        assertTrue(GoldCoinPayment.trySpend(null, account, 15, true));
        assertEquals(0, account.balance());
        assertFalse(GoldCoinPayment.trySpend(null, account, 1, true));
    }

    @Test
    void zeroCostIsFree() {
        MemoryAccount account = new MemoryAccount(0);
        assertTrue(GoldCoinPayment.canAfford(null, account, 0, true));
        assertEquals(new SpendBreakdown(0, 0), GoldCoinPayment.trySpendReturningBreakdown(town(0), account, 0, true));
    }

    @Test
    void refundReturnsBothParts() {
        TownRecord town = town(0);
        MemoryAccount account = new MemoryAccount(3);
        GoldCoinPayment.refund(town, account, new SpendBreakdown(8, 12));
        assertEquals(8, town.getTreasuryGoldCoinCount());
        assertEquals(15, account.balance());
    }

    @Test
    void refundCreditsTreasuryWhenTheAccountRefuses() {
        TownRecord town = town(0);
        MemoryAccount account = new MemoryAccount(3);
        account.refuseDeposits = true;
        GoldCoinPayment.refund(town, account, new SpendBreakdown(8, 12));
        assertEquals(20, town.getTreasuryGoldCoinCount());
        assertEquals(3, account.balance());
    }

    @Test
    void giveDeposits() {
        MemoryAccount account = new MemoryAccount(3);
        assertTrue(GoldCoinPayment.give(account, 5));
        assertEquals(8, account.balance());
        assertTrue(GoldCoinPayment.give(account, 0));
        account.refuseDeposits = true;
        assertFalse(GoldCoinPayment.give(account, 1));
        assertEquals(8, account.balance());
    }
}
