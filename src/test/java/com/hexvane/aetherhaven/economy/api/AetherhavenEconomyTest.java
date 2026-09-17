package com.hexvane.aetherhaven.economy.api;

import static org.junit.jupiter.api.Assertions.*;

import com.hexvane.aetherhaven.economy.ItemCoinEconomy;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("economy")
class AetherhavenEconomyTest {
    private static final class FakeProvider implements EconomyProvider {
        private final String id;
        FakeProvider(String id) { this.id = id; }
        @Override public String id() { return id; }
        @Override public GoldAccount account(Ref<EntityStore> ref, Store<EntityStore> store) { return null; }
        @Override public List<ItemStack> lootItems(String itemId, long amount) { return List.of(); }
        @Override public Message amount(long amount) { return Message.raw(String.valueOf(amount)); }
        @Override public void show(UICommandBuilder builder, String selector, long amount) {}
    }

    private final FakeProvider first = new FakeProvider("test:first");
    private final FakeProvider second = new FakeProvider("test:second");

    @AfterEach void unregisterAll() {
        AetherhavenEconomy.unregister(first);
        AetherhavenEconomy.unregister(second);
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
