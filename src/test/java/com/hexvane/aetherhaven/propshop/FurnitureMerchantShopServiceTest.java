package com.hexvane.aetherhaven.propshop;

import static org.junit.jupiter.api.Assertions.*;

import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.economy.ItemCoinEconomy;
import com.hexvane.aetherhaven.economy.api.GoldAccount;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.event.IEventBus;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.*;

@Tag("prop")
class FurnitureMerchantShopServiceTest {
    private static final List<String> PROPS = List.of("a", "b", "c", "d", "e", "f", "g");
    private static final List<String> PALETTES = List.of("p1", "p2", "p3", "p4", "p5", "p6", "p7");
    private static ItemStore itemStore;
    private static Object previousItemStore;

    // Supply an asset map so real ItemStacks and inventory transactions work without starting a server.
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
        // Avoid changing the global registry used by other test fixtures.
        field.set(null, itemStore);
    }

    @AfterAll static void restoreAssets() throws Exception {
        var field = Item.class.getDeclaredField("ASSET_STORE");
        field.setAccessible(true);
        field.set(null, previousItemStore);
    }

    private static TownRecord town(long gold) {
        TownRecord town = new TownRecord();
        town.setTreasuryGoldCoinCount(gold);
        town.setFurnitureMerchantShopLastRerollEpochDay(10L);
        town.getFurnitureMerchantShopSlots().add(new FurnitureMerchantShopSlotRecord("oldProp", 2));
        town.getFurnitureMerchantPaletteShopSlots().add(new FurnitureMerchantPaletteShopSlotRecord("oldPalette", 3));
        return town;
    }

    private static GoldAccount inventory(int... stacks) {
        var container = new SimpleItemContainer((short) 9);
        for (short i = 0; i < stacks.length; i++) {
            container.setItemStackForSlot(i, new ItemStack(GoldCoinPayment.coinItemId(), stacks[i]));
        }
        return new ItemCoinEconomy.ItemCoinAccount(new CombinedItemContainer(container));
    }

    private static FurnitureMerchantShopService.BuyResult reroll(TownRecord shop, TownRecord payer,
        GoldAccount inventory, boolean allowTreasury) {
        return FurnitureMerchantShopService.tryReroll(shop, payer, inventory, allowTreasury, 11L,
            FurnitureMerchantShopService.inventoryToken(shop), PROPS, PALETTES);
    }

    @Test void chargesExactlyTwentyFromBuyerTownAndRefreshesBothTabs() {
        var shop = town(100);
        var payer = town(27);
        var inv = inventory(30);
        assertTrue(reroll(shop, payer, inv, true).ok());
        assertEquals(7, payer.getTreasuryGoldCoinCount());
        assertEquals(100, shop.getTreasuryGoldCoinCount());
        assertEquals(30, inv.balance());
        assertEquals(11L, shop.getFurnitureMerchantShopLastRerollEpochDay());
        assertValidStock(shop);
    }

    @Test void inventoryOnlyPaymentWorksWithoutTown() {
        var inv = inventory(12, 15);
        assertTrue(reroll(town(0), null, inv, false).ok());
        assertEquals(7, inv.balance());
    }

    @Test void mixedPaymentUsesTreasuryFirst() {
        var payer = town(8);
        var inv = inventory(5, 10);
        assertTrue(reroll(town(0), payer, inv, true).ok());
        assertEquals(0, payer.getTreasuryGoldCoinCount());
        assertEquals(3, inv.balance());
    }

    @Test void treasuryPermissionIsRespectedEvenWithEnoughTownGold() {
        var shop = town(100);
        var inv = inventory(19);
        String before = FurnitureMerchantShopService.inventoryToken(shop);
        assertFalse(reroll(shop, shop, inv, false).ok());
        assertEquals(100, shop.getTreasuryGoldCoinCount());
        assertEquals(19, inv.balance());
        assertEquals(before, FurnitureMerchantShopService.inventoryToken(shop));
    }

    @Test void unauthorizedTreasuryStillAllowsPayingWithInventory() {
        var shop = town(100);
        var inv = inventory(20);
        assertTrue(reroll(shop, shop, inv, false).ok());
        assertEquals(100, shop.getTreasuryGoldCoinCount());
        assertEquals(0, inv.balance());
    }

    @Test void insufficientCombinedFundsPreservesCoinsAndStock() {
        var shop = town(8);
        var inv = inventory(11);
        String before = FurnitureMerchantShopService.inventoryToken(shop);
        assertFalse(reroll(shop, shop, inv, true).ok());
        assertEquals(8, shop.getTreasuryGoldCoinCount());
        assertEquals(11, inv.balance());
        assertEquals(before, FurnitureMerchantShopService.inventoryToken(shop));
    }

    @Test void staleClickDoesNotChargeOrReplaceStock() {
        var shop = town(100);
        String token = FurnitureMerchantShopService.inventoryToken(shop);
        assertTrue(reroll(shop, shop, inventory(), true).ok());
        String after = FurnitureMerchantShopService.inventoryToken(shop);
        var result = FurnitureMerchantShopService.tryReroll(shop, shop, inventory(), true, 11L, token, PROPS, PALETTES);
        assertFalse(result.ok());
        assertTrue(result.failLangKey().endsWith("stockChanged"));
        assertEquals(80, shop.getTreasuryGoldCoinCount());
        assertEquals(after, FurnitureMerchantShopService.inventoryToken(shop));
    }

    @Test void emptyCatalogDoesNotChargeOrChangeStock() {
        var shop = town(100);
        String before = FurnitureMerchantShopService.inventoryToken(shop);
        assertFalse(FurnitureMerchantShopService.tryReroll(shop, shop, inventory(), true, 11L,
            before, List.of(), List.of()).ok());
        assertEquals(100, shop.getTreasuryGoldCoinCount());
        assertEquals(before, FurnitureMerchantShopService.inventoryToken(shop));
    }

    @Test void paletteOnlyCatalogStillRestocksAndPadsSlots() {
        var shop = town(20);
        assertTrue(FurnitureMerchantShopService.tryReroll(shop, shop, inventory(), true, 11L,
            FurnitureMerchantShopService.inventoryToken(shop), List.of(), List.of("p1", "p1", "p2")).ok());
        assertEquals(6, shop.getFurnitureMerchantShopSlots().size());
        assertTrue(shop.getFurnitureMerchantShopSlots().stream().noneMatch(FurnitureMerchantShopSlotRecord::hasStock));
        assertEquals(6, shop.getFurnitureMerchantPaletteShopSlots().size());
        assertEquals(2, shop.getFurnitureMerchantPaletteShopSlots().stream().filter(FurnitureMerchantPaletteShopSlotRecord::hasStock).count());
    }

    @Test void propOnlyCatalogStillRestocksAndPadsSlots() {
        var shop = town(20);
        assertTrue(FurnitureMerchantShopService.tryReroll(shop, shop, inventory(), true, 11L,
            FurnitureMerchantShopService.inventoryToken(shop), List.of("a", "a"), List.of()).ok());
        assertEquals(6, shop.getFurnitureMerchantShopSlots().size());
        assertEquals(1, shop.getFurnitureMerchantShopSlots().stream().filter(FurnitureMerchantShopSlotRecord::hasStock).count());
        assertTrue(shop.getFurnitureMerchantPaletteShopSlots().stream().noneMatch(FurnitureMerchantPaletteShopSlotRecord::hasStock));
    }

    @Test void repeatedRerollsChangeAssortmentsAndMaintainUniqueStock() {
        var shop = town(20_000);
        for (int i = 0; i < 500; i++) {
            var previousProps = props(shop);
            var previousPalettes = palettes(shop);
            assertTrue(reroll(shop, shop, inventory(), true).ok());
            assertValidStock(shop);
            assertNotEquals(previousProps, props(shop));
            assertNotEquals(previousPalettes, palettes(shop));
        }
        assertEquals(10_000, shop.getTreasuryGoldCoinCount());
    }

    @Test void soldOutOffersDoNotRerollForFreeOnSameDay() {
        var shop = town(0);
        shop.getFurnitureMerchantShopSlots().forEach(slot -> slot.setStock(0));
        shop.getFurnitureMerchantPaletteShopSlots().forEach(slot -> slot.setStock(0));
        shop.ensureFurnitureMerchantShopSlotCount(6);
        shop.ensureFurnitureMerchantPaletteShopSlotCount(6);
        String before = FurnitureMerchantShopService.inventoryToken(shop);
        FurnitureMerchantShopService.ensureInventory(null, shop, null, 10L);
        assertEquals(before, FurnitureMerchantShopService.inventoryToken(shop));
    }

    @Test void tokenTracksPurchasesPaletteChangesAndDay() {
        var shop = town(0);
        String before = FurnitureMerchantShopService.inventoryToken(shop);
        shop.getFurnitureMerchantShopSlots().getFirst().setStock(1);
        assertNotEquals(before, FurnitureMerchantShopService.inventoryToken(shop));
        before = FurnitureMerchantShopService.inventoryToken(shop);
        shop.getFurnitureMerchantPaletteShopSlots().getFirst().setPaletteId("changed");
        assertNotEquals(before, FurnitureMerchantShopService.inventoryToken(shop));
        before = FurnitureMerchantShopService.inventoryToken(shop);
        shop.setFurnitureMerchantShopLastRerollEpochDay(11L);
        assertNotEquals(before, FurnitureMerchantShopService.inventoryToken(shop));
    }

    private static Set<String> props(TownRecord shop) {
        return shop.getFurnitureMerchantShopSlots().stream().map(FurnitureMerchantShopSlotRecord::getPropId).collect(Collectors.toSet());
    }
    private static Set<String> palettes(TownRecord shop) {
        return shop.getFurnitureMerchantPaletteShopSlots().stream().map(FurnitureMerchantPaletteShopSlotRecord::getPaletteId).collect(Collectors.toSet());
    }
    private static void assertValidStock(TownRecord shop) {
        assertEquals(6, shop.getFurnitureMerchantShopSlots().size());
        assertEquals(6, props(shop).size());
        assertTrue(PROPS.containsAll(props(shop)));
        assertTrue(shop.getFurnitureMerchantShopSlots().stream().allMatch(slot -> slot.getStock() >= 5 && slot.getStock() <= 10));
        assertEquals(6, shop.getFurnitureMerchantPaletteShopSlots().size());
        assertEquals(6, palettes(shop).size());
        assertTrue(PALETTES.containsAll(palettes(shop)));
        assertTrue(shop.getFurnitureMerchantPaletteShopSlots().stream().allMatch(slot -> slot.getStock() >= 5 && slot.getStock() <= 10));
    }
}
