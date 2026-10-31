package com.hexvane.aetherhaven.equipment;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.equipment.data.*;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkProgress;
import com.hypixel.hytale.assetstore.*;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.codec.*;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.event.*;
import com.hypixel.hytale.protocol.ItemArmorSlot;
import com.hypixel.hytale.server.core.asset.type.item.config.*;
import com.hypixel.hytale.server.core.inventory.*;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.inventory.container.EmptyItemContainer;
import com.hypixel.hytale.server.core.inventory.container.filter.FilterType;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.lang.reflect.Proxy;
import java.nio.file.*;
import java.util.*;
import org.bson.BsonDocument;
import org.junit.jupiter.api.*;

@Tag("town")
class GuardEquipmentRestoreTest {
    private static final Path ROOT = Path.of("src/main/resources/Server");
    private static final Gson GSON = new Gson();
    private static final ItemMap ITEMS = new ItemMap();
    private static Object previousItemStore;
    private static boolean registeredSimple;
    private static boolean registeredEmpty;
    private static EquipmentProfileCatalog catalog;
    private final Ref<EntityStore> ref = new Ref<>(null);
    private final List<Object> events = new ArrayList<>();

    private static class ItemMap extends DefaultAssetMap<String, Item> {
        void add(Item item) { putAll("guard-restore-test", Item.CODEC, Map.of(item.getId(), item), Map.of(), Map.of()); }
    }
    private static class TestItem extends Item {
        TestItem(String id, ItemArmorSlot slot) {
            super(id);
            maxStack = 1;
            if (slot != null) armor = new ItemArmor(slot, 0, null, new com.hypixel.hytale.protocol.Cosmetic[0]);
            utility = ItemUtility.CODEC.decode(BsonDocument.parse("{\"Usable\":true}"), new ExtraInfo());
        }
    }
    private static class ItemStore extends AssetStore<String, Item, DefaultAssetMap<String, Item>> {
        private final EventBus events = new EventBus(false);
        ItemStore(Config config) { super(config); }
        @Override protected IEventBus getEventBus() { return events; }
        @Override public void addFileMonitor(String pack, Path path) {}
        @Override public void removeFileMonitor(Path path) {}
        @Override protected void handleRemoveOrUpdate(Set<String> removed, Map<String, Item> updated, AssetUpdateQuery query) {}
        private static class Config extends Builder<String, Item, DefaultAssetMap<String, Item>, Config> {
            Config() { super(String.class, Item.class, ITEMS); setPath("Items"); setCodec(Item.CODEC); setKeyFunction(Item::getId); }
            @Override public ItemStore build() { return new ItemStore(this); }
        }
    }

    @BeforeAll static void assets() throws Exception {
        var field = Item.class.getDeclaredField("ASSET_STORE");
        field.setAccessible(true);
        previousItemStore = field.get(null);
        field.set(null, new ItemStore.Config().build());
        registeredSimple = ItemContainer.CODEC.getIdFor(SimpleItemContainer.class) == null;
        registeredEmpty = ItemContainer.CODEC.getIdFor(EmptyItemContainer.class) == null;
        if (registeredSimple) ItemContainer.CODEC.register("Simple", SimpleItemContainer.class, SimpleItemContainer.CODEC);
        if (registeredEmpty) ItemContainer.CODEC.register("Empty", EmptyItemContainer.class, EmptyItemContainer.CODEC);
        catalog = EquipmentProfileCatalog.loadFromAssetPacksOrClasspath(GuardEquipmentRestoreTest.class.getClassLoader());
        // Exercise native inventory filters using each shipped profile's actual armor slot definitions.
        try (var files = Files.list(ROOT.resolve("Aetherhaven/Equipment"))) {
            for (var path : files.filter(p -> p.getFileName().toString().startsWith("guard_")).toList()) {
                var profile = GSON.fromJson(Files.readString(path), EquipmentProfileDefinition.class);
                for (String id : profile.getArmorItemIds()) {
                    var json = JsonParser.parseString(Files.readString(ROOT.resolve("Item/Items/Aetherhaven/Guard_Equipment/" + id + ".json"))).getAsJsonObject();
                    ITEMS.add(new TestItem(id, ItemArmorSlot.valueOf(json.getAsJsonObject("Armor").get("ArmorSlot").getAsString())));
                }
                profile.getHotbarSlots().forEach(s -> ITEMS.add(new TestItem(s.getItemId(), null)));
                if (profile.getOffhandItemId() != null) ITEMS.add(new TestItem(profile.getOffhandItemId(), null));
            }
        }
        ITEMS.add(new TestItem("Aetherhaven_Adamantite_Knight_Kit", null));
        ITEMS.add(new TestItem("Food_Salad_Caesar", null));
    }

    @AfterAll static void restoreAssets() throws Exception {
        var field = Item.class.getDeclaredField("ASSET_STORE");
        field.setAccessible(true);
        field.set(null, previousItemStore);
        if (registeredSimple) ItemContainer.CODEC.remove(SimpleItemContainer.class);
        if (registeredEmpty) ItemContainer.CODEC.remove(EmptyItemContainer.class);
    }

    @SuppressWarnings("unchecked")
    private ComponentAccessor<EntityStore> accessor() {
        return (ComponentAccessor<EntityStore>) Proxy.newProxyInstance(ComponentAccessor.class.getClassLoader(),
            new Class<?>[]{ComponentAccessor.class}, (proxy, method, args) -> {
                // Any health, town-save or kit-inventory operation would fail this test.
                if (method.getName().equals("invoke")) { events.add(args[1]); return null; }
                throw new AssertionError("Unexpected operation: " + method);
            });
    }

    @Test void everyClassAndTierRecoversEmptyEquipmentAndIsIdempotentAfterNativeSaveLoad() {
        for (String cls : List.of("knight", "archer", "rogue", "mage")) {
            for (String tier : List.of("", "_copper", "_iron", "_thorium", "_cobalt", "_adamantite")) {
                var profile = catalog.byId("guard_" + cls + tier);
                assertNotNull(profile);
                var armor = new InventoryComponent.Armor((short) 4);
                var hotbar = new InventoryComponent.Hotbar((short) 3);
                var utility = new InventoryComponent.Utility((short) 0);
                GuardEquipmentRestore.restore(profile, armor, hotbar, utility, ref, accessor());
                assertEquipment(profile, armor, hotbar, utility);
                armor = InventoryComponent.Armor.CODEC.decode(InventoryComponent.Armor.CODEC.encode(armor, new ExtraInfo()), new ExtraInfo());
                hotbar = InventoryComponent.Hotbar.CODEC.decode(InventoryComponent.Hotbar.CODEC.encode(hotbar, new ExtraInfo()), new ExtraInfo());
                utility = InventoryComponent.Utility.CODEC.decode(InventoryComponent.Utility.CODEC.encode(utility, new ExtraInfo()), new ExtraInfo());
                var before = InventoryComponent.Hotbar.CODEC.encode(hotbar, new ExtraInfo());
                events.clear(); armor.consumeOutdatedEquipment(); hotbar.consumeOutdatedEquipment(); utility.consumeOutdatedEquipment();
                GuardEquipmentRestore.restore(profile, armor, hotbar, utility, ref, accessor());
                assertEquipment(profile, armor, hotbar, utility);
                assertEquals(before, InventoryComponent.Hotbar.CODEC.encode(hotbar, new ExtraInfo()));
                assertTrue(events.isEmpty(), profile.getId());
                assertFalse(armor.consumeOutdatedEquipment(), profile.getId());
                assertFalse(hotbar.consumeOutdatedEquipment(), profile.getId());
                assertFalse(utility.consumeOutdatedEquipment(), profile.getId());
            }
        }
    }

    @Test void savedUpgradedKitSurvivesRecoveryAndStaleOrCivilianEntitiesAreIgnored() {
        var town = new TownRecord();
        var uuid = UUID.randomUUID();
        town.getHiredGuardRecords().add(new HiredGuardRecord("guard", uuid, "guard_knight_adamantite", true));
        var progress = new TownsfolkProgress();
        progress.setGuardKitData(TownsfolkProgress.encodeProfessionKit(new ItemStack("Aetherhaven_Adamantite_Knight_Kit", 1)));
        town.getTownsfolkProgress().put("guard", progress);
        town = GSON.fromJson(GSON.toJson(town), TownRecord.class);
        String before = GSON.toJson(town);
        var profile = GuardEquipmentRestore.savedProfile(town, uuid, "guard", "Aetherhaven_Guard_Knight", catalog);
        assertNotNull(profile); assertEquals("guard_knight_adamantite", profile.getId());
        GuardEquipmentRestore.restore(profile, new InventoryComponent.Armor((short) 4), new InventoryComponent.Hotbar((short) 3),
            new InventoryComponent.Utility((short) 0), ref, accessor());
        assertEquals(before, GSON.toJson(town));
        assertEquals("Aetherhaven_Adamantite_Knight_Kit", town.getTownsfolkProgress().get("guard").guardKit().getItemId());
        assertNull(GuardEquipmentRestore.savedProfile(town, uuid, "townsfolk", "Aetherhaven_Guard_Knight", catalog));
        assertNull(GuardEquipmentRestore.savedProfile(town, UUID.randomUUID(), "guard", "Aetherhaven_Guard_Knight", catalog));
        assertNull(GuardEquipmentRestore.savedProfile(town, uuid, "guard", "Aetherhaven_Guard_Mage", catalog));
        town.getHiredGuardRecords().getFirst().setEquipmentProfileId("unavailable_addon_kit");
        assertNull(GuardEquipmentRestore.savedProfile(town, uuid, "guard", "Aetherhaven_Guard_Knight", catalog));
    }

    @Test void partialRecoveryPreservesWearAndRetriesRejectedSlots() {
        var profile = catalog.byId("guard_knight_iron");
        var armor = new InventoryComponent.Armor((short) 4);
        var hotbar = new InventoryComponent.Hotbar((short) 3);
        var utility = new InventoryComponent.Utility((short) 2);
        var worn = new ItemStack(profile.getHotbarSlots().getFirst().getItemId(), 1, 12, 100,
            BsonDocument.parse("{\"engraved\":\"Keep me\"}"));
        hotbar.getInventory().setItemStackForSlot((short) 0, worn);
        armor.getInventory().setGlobalFilter(FilterType.DENY_ALL);
        GuardEquipmentRestore.restore(profile, armor, hotbar, utility, ref, accessor());
        assertSame(worn, hotbar.getInventory().getItemStack((short) 0));
        assertTrue(armor.getInventory().isEmpty());
        armor.getInventory().setGlobalFilter(FilterType.ALLOW_ALL);
        GuardEquipmentRestore.restore(profile, armor, hotbar, utility, ref, accessor());
        assertEquipment(profile, armor, hotbar, utility);
        assertSame(worn, hotbar.getInventory().getItemStack((short) 0));
    }

    @Test void temporaryPropsAreLeftAloneAndMissingWeaponsReturnAfterTheActivity() {
        var profile = catalog.byId("guard_knight");
        var armor = new InventoryComponent.Armor((short) 4);
        var hotbar = new InventoryComponent.Hotbar((short) 3);
        var utility = new InventoryComponent.Utility((short) 2);
        var food = new ItemStack("Food_Salad_Caesar", 1).withMetadata("AetherhavenLifeTemporary", Codec.BOOLEAN, true);
        hotbar.getInventory().setItemStackForSlot((short) 0, food);
        utility.getInventory().setItemStackForSlot((short) 0, food);
        GuardEquipmentRestore.restore(profile, armor, hotbar, utility, ref, accessor());
        assertSame(food, hotbar.getInventory().getItemStack((short) 0));
        assertSame(food, utility.getInventory().getItemStack((short) 0));
        assertTrue(events.isEmpty());
        hotbar.getInventory().clear(); utility.getInventory().clear();
        GuardEquipmentRestore.restore(profile, armor, hotbar, utility, ref, accessor());
        assertEquipment(profile, armor, hotbar, utility);
    }

    @Test void existingEquipmentBecomesVisibleAgainWithoutReplacingTheStacks() {
        var profile = catalog.byId("guard_knight");
        var armor = new InventoryComponent.Armor((short) 4);
        var hotbar = new InventoryComponent.Hotbar((short) 3);
        var utility = new InventoryComponent.Utility((short) 2);
        GuardEquipmentRestore.restore(profile, armor, hotbar, utility, ref, accessor());
        var weapon = hotbar.getActiveItem();
        var shield = utility.getActiveItem();
        hotbar.setActiveSlot((byte) 1, ref, accessor());
        utility.setActiveSlot((byte) -1, ref, accessor());
        GuardEquipmentRestore.restore(profile, armor, hotbar, utility, ref, accessor());
        assertSame(weapon, hotbar.getActiveItem());
        assertSame(shield, utility.getActiveItem());
        assertEquipment(profile, armor, hotbar, utility);
    }

    @Test void unavailableItemsDoNotEraseEquipmentAndAreRetriedWhenAssetsArrive() {
        var profile = GSON.fromJson("""
            {"id":"addon_guard","hotbar":[{"slot":0,"itemId":"late_addon_sword"}]}
            """, EquipmentProfileDefinition.class);
        var armor = new InventoryComponent.Armor((short) 4);
        var hotbar = new InventoryComponent.Hotbar((short) 3);
        var utility = new InventoryComponent.Utility((short) 2);
        var previous = new ItemStack("Weapon_Sword_Copper", 1);
        hotbar.getInventory().setItemStackForSlot((short) 0, previous);
        GuardEquipmentRestore.restore(profile, armor, hotbar, utility, ref, accessor());
        assertSame(previous, hotbar.getActiveItem());
        assertTrue(events.isEmpty());
        ITEMS.add(new TestItem("late_addon_sword", null));
        GuardEquipmentRestore.restore(profile, armor, hotbar, utility, ref, accessor());
        assertEquals("late_addon_sword", hotbar.getActiveItem().getItemId());
    }

    private static void assertEquipment(EquipmentProfileDefinition profile, InventoryComponent.Armor armor,
            InventoryComponent.Hotbar hotbar, InventoryComponent.Utility utility) {
        for (String id : profile.getArmorItemIds()) {
            short slot = (short) Item.getAssetMap().getAsset(id).getArmor().getArmorSlot().ordinal();
            assertEquals(id, armor.getInventory().getItemStack(slot).getItemId(), profile.getId());
        }
        for (var slot : profile.getHotbarSlots()) assertEquals(slot.getItemId(), hotbar.getInventory().getItemStack((short) slot.getSlot()).getItemId());
        if (profile.getOffhandItemId() != null) assertEquals(profile.getOffhandItemId(), utility.getActiveItem().getItemId());
    }
}
