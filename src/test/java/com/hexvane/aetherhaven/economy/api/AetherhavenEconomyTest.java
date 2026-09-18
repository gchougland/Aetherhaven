package com.hexvane.aetherhaven.economy.api;

import static org.junit.jupiter.api.Assertions.*;

import com.hexvane.aetherhaven.economy.ItemCoinEconomy;
import com.hexvane.aetherhaven.economy.api.GoldSource;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("economy")
class AetherhavenEconomyTest {
    /** A balance in a long, refusing deposits on demand (a full coin inventory). */
    static final class MemoryAccount implements GoldAccount {
        long balance;
        boolean refuseDeposits;
        MemoryAccount(long balance) { this.balance = balance; }
        @Override public long balance() { return balance; }
        @Override public boolean withdraw(long amount) {
            if (amount > balance) { return false; }
            balance -= amount;
            return true;
        }
        @Override public boolean deposit(long amount) {
            if (refuseDeposits) { return false; }
            balance += amount;
            return true;
        }
    }

    /** Ledgers in memory, one account per town and per safe, so that the migration can be watched. */
    private static class FakeProvider implements EconomyProvider {
        private final String id;
        final Map<Object, MemoryAccount> ledgers = new HashMap<>();
        FakeProvider(String id) { this.id = id; }
        @Override public String id() { return id; }
        final MemoryAccount player = new MemoryAccount(0);
        @Override public GoldAccount account(Ref<EntityStore> ref, Store<EntityStore> store) { return player; }
        @Override public GoldAccount townAccount(TownRecord town) {
            return ledgers.computeIfAbsent(town.getTownId(), k -> new MemoryAccount(0));
        }
        @Override public GoldAccount shopSafe(TownRecord town, UUID player) {
            return ledgers.computeIfAbsent(List.of(town.getTownId(), player), k -> new MemoryAccount(0));
        }
        @Override public List<ItemStack> goldItems(GoldSource source, String itemId, long amount) { return List.of(); }
        @Override public Message amount(long amount) { return Message.raw(String.valueOf(amount)); }
        @Override public void show(UICommandBuilder builder, String selector, long amount, int fontSize) {}
    }

    private final FakeProvider first = new FakeProvider("test:first");
    private final FakeProvider second = new FakeProvider("test:second");

    @AfterEach void unregisterAll() {
        AetherhavenEconomy.unregister(first);
        AetherhavenEconomy.unregister(second);
    }

    @Test void transferMovesWholeCoinsByDefault() {
        MemoryAccount from = new MemoryAccount(20);
        MemoryAccount to = new MemoryAccount(5);
        Transfer moved = first.transfer(from, to, " 12 ");
        assertEquals(Transfer.Outcome.MOVED, moved.outcome());
        assertEquals("12", moved.moved().getRawText());
        assertEquals(8, from.balance());
        assertEquals(17, to.balance());
    }

    @Test void transferOfBlankTextMovesEverything() {
        MemoryAccount from = new MemoryAccount(20);
        MemoryAccount to = new MemoryAccount(0);
        assertEquals(Transfer.Outcome.MOVED, first.transfer(from, to, "").outcome());
        assertEquals(0, from.balance());
        assertEquals(20, to.balance());
        assertEquals(Transfer.Outcome.NOT_AVAILABLE, first.transfer(from, to, null).outcome());
    }

    @Test void transferRefusesTextThatIsNotAnAmount() {
        MemoryAccount from = new MemoryAccount(20);
        MemoryAccount to = new MemoryAccount(0);
        for (String text : new String[] {"0", "-1", "1.5", "ten", "12 gold"}) {
            assertEquals(Transfer.Outcome.NOT_AN_AMOUNT, first.transfer(from, to, text).outcome(), text);
        }
        assertEquals(20, from.balance());
    }

    @Test void transferRefusesMoreThanTheSourceHolds() {
        MemoryAccount from = new MemoryAccount(20);
        MemoryAccount to = new MemoryAccount(0);
        assertEquals(Transfer.Outcome.NOT_AVAILABLE, first.transfer(from, to, "21").outcome());
        assertEquals(20, from.balance());
        assertEquals(0, to.balance());
    }

    @Test void transferUndoesTheWithdrawalWhenTheDepositIsRefused() {
        MemoryAccount from = new MemoryAccount(20);
        MemoryAccount to = new MemoryAccount(0);
        to.refuseDeposits = true;
        assertEquals(Transfer.Outcome.NO_ROOM, first.transfer(from, to, "12").outcome());
        assertEquals(20, from.balance());
    }

    @Test void balanceIsTheSumOfTheAccountsInWholeCoinsByDefault() {
        Balance balance = first.balance(new MemoryAccount(7), new MemoryAccount(5));
        assertEquals(new Balance.Whole(first, 12), balance);
        assertEquals("12", balance.message().getRawText());
        assertEquals(new Balance.Whole(first, 0), first.balance());
        assertNotEquals(balance, first.balance(new MemoryAccount(12), new MemoryAccount(1)));
        assertEquals(new Balance.Whole(first, 5), first.balance(new MemoryAccount(-10), new MemoryAccount(5)));
        assertEquals(
            new Balance.Whole(first, Long.MAX_VALUE),
            first.balance(new MemoryAccount(Long.MAX_VALUE - 2L), new MemoryAccount(10))
        );
    }

    @Test void coinItemKeepsTheTreasuryInTheTownRecord() {
        TownRecord town = new TownRecord();
        town.setTreasuryGoldCoinCount(8);
        GoldAccount treasury = AetherhavenEconomy.townAccount(town);
        assertEquals(8, treasury.balance());
        assertTrue(treasury.deposit(4));
        assertEquals(12, town.getTreasuryGoldCoinCount());
        assertFalse(treasury.withdraw(13));
        assertTrue(treasury.withdraw(12));
        assertEquals(0, town.getTreasuryGoldCoinCount());
    }

    @Test void coinItemKeepsTheShopSafeInTheTownRecord() {
        TownRecord town = new TownRecord();
        UUID player = UUID.randomUUID();
        GoldAccount safe = AetherhavenEconomy.shopSafe(town, player);
        assertTrue(safe.deposit(30));
        assertEquals(30, town.getPlayerShopSafeGold(player));
        assertFalse(safe.withdraw(31));
        assertTrue(safe.withdraw(30));
        assertEquals(0, town.getPlayerShopSafeGold(player));
    }

    @Test void registeredProviderTakesOverTheTownRecordCountsOnce() {
        AetherhavenEconomy.register(first);
        TownRecord town = new TownRecord();
        town.setTownId(UUID.randomUUID());
        UUID player = UUID.randomUUID();
        town.setTreasuryGoldCoinCount(8);
        town.addPlayerShopSafeGold(player, 30);

        GoldAccount treasury = AetherhavenEconomy.townAccount(town);
        assertEquals(8, treasury.balance());
        assertEquals(0, town.getTreasuryGoldCoinCount());
        assertEquals(8, AetherhavenEconomy.townAccount(town).balance());

        GoldAccount safe = AetherhavenEconomy.shopSafe(town, player);
        assertEquals(30, safe.balance());
        assertEquals(0, town.getPlayerShopSafeGold(player));
        assertEquals(30, AetherhavenEconomy.shopSafe(town, player).balance());
    }

    /** Each command as "Type selector data" on one line. */
    private static String commands(UICommandBuilder builder) {
        return Stream.of(builder.getCommands())
            .map(c -> c.type + " " + c.selector + " " + (c.data != null ? c.data : c.text))
            .reduce("", (a, b) -> a + b + "\n");
    }

    @Test void coinItemDrawsTheIconAndTheGroupedNumberAtTheSizeOfTheLine() {
        UICommandBuilder builder = new UICommandBuilder();
        AetherhavenEconomy.show(builder, "#Row #Gold", 1234L, 16);
        String queued = commands(builder);
        assertTrue(queued.contains(" #Row #Gold Aetherhaven/GoldAmount.ui\n"), queued);
        assertTrue(queued.contains("#Row #Gold #Amount.Text {\"0\": \"1,234\"}\n"), queued);
        assertFalse(queued.contains("FontSize"), queued);

        builder = new UICommandBuilder();
        AetherhavenEconomy.show(builder, "#Row #Gold", 5L, 13);
        queued = commands(builder);
        assertTrue(queued.contains("#Row #Gold #Amount.Style.FontSize {\"0\": 13}\n"), queued);
        assertTrue(queued.contains("#Row #Gold #Icon.Anchor"), queued);
    }

    @Test void coinItemPutsTheIconAfterTheNumberOnRequestAndAProviderMayNot() {
        UICommandBuilder builder = new UICommandBuilder();
        AetherhavenEconomy.show(builder, "#GoldRight", ItemCoinEconomy.INSTANCE.balance(new MemoryAccount(7)), 16, true);
        String queued = commands(builder);
        assertTrue(queued.contains(" #GoldRight Aetherhaven/GoldAmountRight.ui\n"), queued);
        assertTrue(queued.contains("#GoldRight #Amount.Text {\"0\": \"7\"}\n"), queued);

        // A provider that draws one way only is asked through its four-parameter show.
        List<String> calls = new ArrayList<>();
        EconomyProvider oneWay = new FakeProvider("test:oneway") {
            @Override public void show(UICommandBuilder b, String selector, long amount, int fontSize) {
                calls.add(selector + " " + amount + " " + fontSize);
            }
        };
        oneWay.balance(new MemoryAccount(7)).show(new UICommandBuilder(), "#GoldRight", 16, true);
        assertEquals(List.of("#GoldRight 7 16"), calls);
    }

    @Test void coinItemIsTheDefault() {
        assertSame(ItemCoinEconomy.INSTANCE, AetherhavenEconomy.provider());
        assertTrue(AetherhavenEconomy.usesCoinItem());
    }

    @Test void registeredProviderReplacesTheCoinItem() {
        AetherhavenEconomy.register(first);
        assertSame(first, AetherhavenEconomy.provider());
        assertFalse(AetherhavenEconomy.usesCoinItem());
    }

    @Test void registeredProviderIgnoresTheContainerASiteChose() {
        AetherhavenEconomy.register(first);
        // No entity in a unit test: the provider is handed the ref and store as they are.
        assertSame(first.player, AetherhavenEconomy.account(null, null, null));
        assertSame(first.player, AetherhavenEconomy.account(null, null, new CombinedItemContainer(new SimpleItemContainer((short) 1))));
    }

    @Test void secondRegistrationIsRefusedAndFirstStays() {
        AetherhavenEconomy.register(first);
        assertThrows(IllegalStateException.class, () -> AetherhavenEconomy.register(second));
        assertSame(first, AetherhavenEconomy.provider());
    }

    @Test void registeringTheSameProviderTwiceIsHarmless() {
        AetherhavenEconomy.register(first);
        assertDoesNotThrow(() -> AetherhavenEconomy.register(first));
        assertSame(first, AetherhavenEconomy.provider());
    }

    @Test void unregisterRestoresTheDefaultAndIgnoresOthers() {
        AetherhavenEconomy.register(first);
        AetherhavenEconomy.unregister(second);
        assertSame(first, AetherhavenEconomy.provider());
        AetherhavenEconomy.unregister(first);
        assertSame(ItemCoinEconomy.INSTANCE, AetherhavenEconomy.provider());
    }
}
