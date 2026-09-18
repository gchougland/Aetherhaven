package com.hexvane.aetherhaven.town;

import static org.junit.jupiter.api.Assertions.*;

import com.hexvane.aetherhaven.economy.ItemCoinEconomy;
import com.hexvane.aetherhaven.economy.api.AetherhavenEconomy;
import com.hexvane.aetherhaven.economy.api.EconomyProvider;
import com.hexvane.aetherhaven.economy.api.GoldAccount;
import com.hexvane.aetherhaven.economy.api.GoldSource;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.event.IEventBus;
import com.hypixel.hytale.protocol.StringParamValue;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** A town log line stores the coins as a number and shows them as the economy provider of the day writes them. */
@Tag("town")
class TownLogMessageTest {
    private static ItemStore itemStore;
    private static Object previousItemStore;

    // Supply an asset map so the item name of a sale line resolves without starting a server.
    private static class ItemStore extends AssetStore<String, Item, DefaultAssetMap<String, Item>> {
        private final EventBus events = new EventBus(false);
        ItemStore(Config config) { super(config); }
        @Override protected IEventBus getEventBus() { return events; }
        @Override public void addFileMonitor(String pack, Path path) {}
        @Override public void removeFileMonitor(Path path) {}
        @Override protected void handleRemoveOrUpdate(Set<String> removed, Map<String, Item> updated, AssetUpdateQuery query) {}
        private static class Config extends Builder<String, Item, DefaultAssetMap<String, Item>, Config> {
            Config() {
                super(String.class, Item.class, new DefaultAssetMap<>());
                setPath("Items"); setCodec(Item.CODEC); setKeyFunction(Item::getId);
            }
            @Override public ItemStore build() { return new ItemStore(this); }
        }
    }

    @BeforeAll static void setUpAssets() throws Exception {
        var field = Item.class.getDeclaredField("ASSET_STORE");
        field.setAccessible(true);
        previousItemStore = field.get(null);
        itemStore = new ItemStore.Config().build();
        field.set(null, itemStore);
    }

    @AfterAll static void restoreAssets() throws Exception {
        var field = Item.class.getDeclaredField("ASSET_STORE");
        field.setAccessible(true);
        field.set(null, previousItemStore);
    }

    /** Writes an amount as "{n} marks", to tell its text from the built-in one. */
    private static final class MarksProvider implements EconomyProvider {
        @Override public String id() { return "test:marks"; }
        @Override public GoldAccount account(Ref<EntityStore> ref, Store<EntityStore> store) { return null; }
        @Override public GoldAccount townAccount(TownRecord town) { throw new UnsupportedOperationException(); }
        @Override public GoldAccount shopSafe(TownRecord town, UUID player) { throw new UnsupportedOperationException(); }
        @Override public List<ItemStack> goldItems(GoldSource source, String itemId, long amount) { return List.of(); }
        @Override public Message amount(long amount) { return Message.raw(amount + " marks"); }
        @Override public void show(UICommandBuilder builder, String selector, long amount, int fontSize) {}
    }

    private final MarksProvider marks = new MarksProvider();

    @AfterEach void unregister() {
        AetherhavenEconomy.unregister(marks);
    }

    private static Message param(Message message, String name) {
        return new Message(message.getFormattedMessage().messageParams.get(name));
    }

    private static String text(Message message, String name) {
        return ((StringParamValue) message.getFormattedMessage().params.get(name)).value;
    }

    @Test void storedGoldIsWrittenByTheProviderOfTheDay() {
        TownLogEntry tax = new TownLogEntry(3L, TownLogService.KEY_TAX, TownLogMessage.taxParams("1200", "Dawnmere"));
        TownLogEntry sale = new TownLogEntry(
            3L, TownLogService.KEY_SHOP_SALE, TownLogMessage.shopSaleParams("Ann", "Ingredient_Bar_Iron", "2", "7")
        );

        Message builtIn = param(TownLogMessage.render(tax), "amount");
        assertEquals(ItemCoinEconomy.AMOUNT_KEY, builtIn.getMessageId());
        assertEquals("1,200", ((StringParamValue) builtIn.getFormattedMessage().params.get("count")).value);

        AetherhavenEconomy.register(marks);
        assertEquals("1200 marks", param(TownLogMessage.render(tax), "amount").getRawText());
        Message rendered = TownLogMessage.render(sale);
        assertEquals("7 marks", param(rendered, "gold").getRawText());
        assertEquals("2", text(rendered, "count"));
        assertEquals("Ann", text(rendered, "buyer"));
    }

    @Test void aStoredAmountThatIsNotANumberIsShownAsIs() {
        AetherhavenEconomy.register(marks);
        TownLogEntry tax = new TownLogEntry(3L, TownLogService.KEY_TAX, TownLogMessage.taxParams("a few", "Dawnmere"));
        assertEquals("a few", param(TownLogMessage.render(tax), "amount").getRawText());
    }

    @Test void otherParamsStayText() {
        AetherhavenEconomy.register(marks);
        TownLogEntry other = new TownLogEntry(3L, "some.other.key", Map.of("amount", "12"));
        assertNull(TownLogMessage.render(other).getFormattedMessage().messageParams);
        assertEquals("12", text(TownLogMessage.render(other), "amount"));
    }
}
