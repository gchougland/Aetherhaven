package com.hexvane.aetherhaven.restaurant;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.economy.GoldCoinPayment;
import com.hexvane.aetherhaven.inventory.InventoryMaterials;
import com.hexvane.aetherhaven.restaurant.RestaurantUpgrades.Branch;
import com.hexvane.aetherhaven.restaurant.RestaurantUpgrades.IngredientCost;
import com.hexvane.aetherhaven.restaurant.RestaurantUpgrades.PurchaseResult;
import com.hexvane.aetherhaven.town.TownMemberPermissions;
import com.hexvane.aetherhaven.town.TownMemberRole;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.workplace.BuildingUpgradeTree;
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
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Levels, costs, purchases and old saves for the restaurant's four upgrade branches. */
@Tag("town")
class RestaurantUpgradesTest {
    private static final Gson GSON = new Gson();
    private static Object previousItemStore;
    private final UUID owner = UUID.randomUUID();

    private static class ItemStore extends AssetStore<String, Item, DefaultAssetMap<String, Item>> {
        private final EventBus events = new EventBus(false);
        ItemStore(Config config) { super(config); }
        @Override protected IEventBus getEventBus() { return events; }
        @Override public void addFileMonitor(String pack, Path path) {}
        @Override public void removeFileMonitor(Path path) {}
        @Override protected void handleRemoveOrUpdate(Set<String> removed, Map<String, Item> updated, AssetUpdateQuery query) {}
        private static class Config extends Builder<String, Item, DefaultAssetMap<String, Item>, Config> {
            Config() { super(String.class, Item.class, new DefaultAssetMap<>()); setPath("Items"); setCodec(Item.CODEC); setKeyFunction(Item::getId); }
            @Override public ItemStore build() { return new ItemStore(this); }
        }
    }

    @BeforeAll static void assets() throws Exception {
        var field = Item.class.getDeclaredField("ASSET_STORE"); field.setAccessible(true);
        previousItemStore = field.get(null); field.set(null, new ItemStore.Config().build());
    }

    @AfterAll static void restore() throws Exception {
        var field = Item.class.getDeclaredField("ASSET_STORE"); field.setAccessible(true); field.set(null, previousItemStore);
    }

    private TownRecord town(long treasury) {
        var town = new TownRecord(); town.setOwner(owner, "Owner"); town.setTreasuryGoldCoinCount(treasury); return town;
    }

    private static CombinedItemContainer inventory(Object... idThenCount) {
        var bag = new SimpleItemContainer((short) 12);
        for (int i = 0; i < idThenCount.length; i += 2) {
            bag.setItemStackForSlot((short) (i / 2), new ItemStack((String) idThenCount[i], (Integer) idThenCount[i + 1]), false);
        }
        return new CombinedItemContainer(bag);
    }

    // --- levels ---------------------------------------------------------------------------------------------------

    @Test void everyBranchStartsAtZeroAndClimbsThreeLevelsOnItsOwn() {
        var state = PlotRestaurantState.empty();
        for (Branch branch : Branch.values()) {
            assertEquals(0, RestaurantUpgrades.currentLevel(state, branch));
            assertEquals(1, RestaurantUpgrades.nextTier(state, branch));
            assertFalse(RestaurantUpgrades.isMaxed(state, branch));
            assertEquals(3, RestaurantUpgrades.maxLevel(branch));
        }
        state.setMenuLevel(2);
        assertEquals(2, RestaurantUpgrades.currentLevel(state, Branch.MENU));
        assertEquals(3, RestaurantUpgrades.nextTier(state, Branch.MENU));
        assertEquals(0, RestaurantUpgrades.currentLevel(state, Branch.PROVISIONS), "Menu levels must not leak into Provisions");
        assertEquals(0, state.getSatietyLevel()); assertEquals(0, state.getServiceLevel());
        state.setProvisionsLevel(3);
        assertTrue(RestaurantUpgrades.isMaxed(state, Branch.PROVISIONS));
        assertEquals(0, RestaurantUpgrades.nextTier(state, Branch.PROVISIONS));
        assertFalse(RestaurantUpgrades.prerequisitesMet(state, Branch.PROVISIONS));
        state.setMenuLevel(99); state.setProvisionsLevel(-4);
        assertEquals(3, state.getMenuLevel()); assertEquals(0, state.getProvisionsLevel());
    }

    @Test void branchIdsResolveForAllFourBranches() {
        assertEquals(Branch.MENU, RestaurantUpgrades.branchFromId(" Menu "));
        assertEquals(Branch.PROVISIONS, RestaurantUpgrades.branchFromId("provisions"));
        assertEquals(Branch.SATIETY, RestaurantUpgrades.branchFromId("satiety"));
        assertEquals(Branch.SERVICE, RestaurantUpgrades.branchFromId("service"));
        assertNull(RestaurantUpgrades.branchFromId("dessert")); assertNull(RestaurantUpgrades.branchFromId(null));
    }

    // --- costs ----------------------------------------------------------------------------------------------------

    @Test void newBranchesCostFiveBasedCropsAndCookedFoodWithGoldRisingByLevel() {
        long previousGold = 0;
        for (int tier = 1; tier <= 3; tier++) {
            long gold = RestaurantUpgrades.goldCost(Branch.MENU, tier);
            assertEquals(gold, RestaurantUpgrades.goldCost(Branch.PROVISIONS, tier));
            assertEquals(gold, RestaurantUpgrades.goldCost(Branch.SATIETY, tier), "New branches follow the existing gold ladder");
            assertEquals(0, gold % 5); assertTrue(gold > previousGold); previousGold = gold;
            for (Branch branch : List.of(Branch.MENU, Branch.PROVISIONS)) {
                var costs = RestaurantUpgrades.ingredientCosts(branch, tier);
                assertFalse(costs.isEmpty());
                assertTrue(costs.size() <= 4, "The details panel shows four cost cells");
                for (IngredientCost cost : costs) {
                    assertTrue(cost.count() > 0 && cost.count() % 5 == 0, branch + " " + tier + " " + cost);
                    assertTrue(cost.itemId().startsWith("Food_") || cost.itemId().startsWith("Plant_Crop_"), cost.itemId());
                }
            }
        }
        assertTrue(RestaurantUpgrades.ingredientCosts(Branch.MENU, 0).isEmpty());
        assertTrue(RestaurantUpgrades.ingredientCosts(Branch.PROVISIONS, 4).isEmpty());
        int previousItems = 0;
        for (int tier = 1; tier <= 3; tier++) {
            int items = RestaurantUpgrades.ingredientCosts(Branch.PROVISIONS, tier).stream().mapToInt(IngredientCost::count).sum();
            assertTrue(items > previousItems, "A bigger pantry takes more stock each level"); previousItems = items;
        }
    }

    @Test void satietyAndServiceNumbersAreUntouched() {
        assertEquals(List.of(new IngredientCost("Plant_Crop_Carrot_Item", 12), new IngredientCost("Plant_Crop_Corn_Item", 12)), RestaurantUpgrades.ingredientCosts(Branch.SATIETY, 1));
        assertEquals(List.of(new IngredientCost("Plant_Crop_Cauliflower_Item", 10), new IngredientCost("Plant_Crop_Aubergine_Item", 10)), RestaurantUpgrades.ingredientCosts(Branch.SATIETY, 2));
        assertEquals(List.of(new IngredientCost("Plant_Crop_Onion_Item", 8), new IngredientCost("Plant_Crop_Potato_Item", 8)), RestaurantUpgrades.ingredientCosts(Branch.SATIETY, 3));
        assertEquals(List.of(new IngredientCost("Food_Bread", 10), new IngredientCost("Food_Cheese", 10)), RestaurantUpgrades.ingredientCosts(Branch.SERVICE, 1));
        assertEquals(List.of(new IngredientCost("Food_Salad_Berry", 6), new IngredientCost("Food_Salad_Mushroom", 6)), RestaurantUpgrades.ingredientCosts(Branch.SERVICE, 2));
        assertEquals(List.of(new IngredientCost("Food_Pie_Apple", 4), new IngredientCost("Food_Pie_Meat", 4), new IngredientCost("Food_Pie_Pumpkin", 4)), RestaurantUpgrades.ingredientCosts(Branch.SERVICE, 3));
        assertEquals(List.of(30L, 55L, 85L), List.of(RestaurantUpgrades.goldCost(Branch.SERVICE, 1), RestaurantUpgrades.goldCost(Branch.SERVICE, 2), RestaurantUpgrades.goldCost(Branch.SERVICE, 3)));
        assertArrayEquals(new float[]{1f, .92f, .85f, .78f}, new float[]{RestaurantUpgrades.satietyDecayMultiplier(0), RestaurantUpgrades.satietyDecayMultiplier(1), RestaurantUpgrades.satietyDecayMultiplier(2), RestaurantUpgrades.satietyDecayMultiplier(3)});
        assertArrayEquals(new float[]{10f, 9f, 8f, 6f}, new float[]{RestaurantUpgrades.serviceEatDurationSeconds(0), RestaurantUpgrades.serviceEatDurationSeconds(1), RestaurantUpgrades.serviceEatDurationSeconds(2), RestaurantUpgrades.serviceEatDurationSeconds(3)});
        assertArrayEquals(new float[]{30f, 34f, 38f, 44f}, new float[]{RestaurantUpgrades.serviceHungerRestore(0), RestaurantUpgrades.serviceHungerRestore(1), RestaurantUpgrades.serviceHungerRestore(2), RestaurantUpgrades.serviceHungerRestore(3)});
    }

    // --- purchases through the shared gold helper -------------------------------------------------------------------

    @Test void menuPurchaseTakesTheDishesAndSplitsGoldBetweenTreasuryAndPockets() {
        var town = town(20); var state = PlotRestaurantState.empty();
        var inv = inventory("Food_Kebab_Vegetable", 7, "Food_Kebab_Mushroom", 5, GoldCoinPayment.coinItemId(), 40);
        assertTrue(RestaurantUpgrades.canAfford(state, Branch.MENU, town, inv, true));
        assertEquals(PurchaseResult.OK, RestaurantUpgrades.tryPurchase(state, Branch.MENU, town, inv, true));
        assertEquals(1, state.getMenuLevel());
        assertEquals(2, InventoryMaterials.count(inv, "Food_Kebab_Vegetable"));
        assertEquals(0, InventoryMaterials.count(inv, "Food_Kebab_Mushroom"));
        assertEquals(0, town.getTreasuryGoldCoinCount(), "The treasury pays first, as for every shared purchase");
        assertEquals(30, InventoryMaterials.count(inv, GoldCoinPayment.coinItemId()));
        assertEquals(0, state.getProvisionsLevel()); assertEquals(0, state.getSatietyLevel()); assertEquals(0, state.getServiceLevel());
    }

    @Test void provisionsPurchaseWithoutTreasuryPermissionUsesOnlyTheBuyersCoins() {
        var town = town(500); var member = UUID.randomUUID(); town.putMember(member, TownMemberRole.BOTH);
        town.putMemberPermissions(member, GSON.fromJson("{\"manageConstructions\":true,\"spendTreasuryGold\":false}", TownMemberPermissions.class));
        boolean treasury = town.playerCanSpendTreasuryGold(member); assertFalse(treasury);
        var state = PlotRestaurantState.empty();
        var inv = inventory("Plant_Crop_Wheat_Item", 20, "Food_Bread", 10, GoldCoinPayment.coinItemId(), 25);
        assertFalse(RestaurantUpgrades.canAfford(state, Branch.PROVISIONS, town, inv, treasury));
        assertEquals(PurchaseResult.NEED_GOLD, RestaurantUpgrades.tryPurchase(state, Branch.PROVISIONS, town, inv, treasury));
        assertEquals(20, InventoryMaterials.count(inv, "Plant_Crop_Wheat_Item"), "A refused purchase takes nothing");
        assertEquals(10, InventoryMaterials.count(inv, "Food_Bread"));
        assertEquals(25, InventoryMaterials.count(inv, GoldCoinPayment.coinItemId())); assertEquals(500, town.getTreasuryGoldCoinCount());
        assertEquals(0, state.getProvisionsLevel());
        inv.setItemStackForSlot((short) 5, new ItemStack(GoldCoinPayment.coinItemId(), 5), false);
        assertEquals(PurchaseResult.OK, RestaurantUpgrades.tryPurchase(state, Branch.PROVISIONS, town, inv, treasury));
        assertEquals(1, state.getProvisionsLevel()); assertEquals(500, town.getTreasuryGoldCoinCount());
        assertEquals(0, InventoryMaterials.count(inv, GoldCoinPayment.coinItemId()));
        assertEquals(0, InventoryMaterials.count(inv, "Plant_Crop_Wheat_Item")); assertEquals(0, InventoryMaterials.count(inv, "Food_Bread"));
    }

    @Test void missingIngredientsRefuseWithoutChargingAndAFullBranchCannotBeBoughtAgain() {
        var town = town(1000); var state = PlotRestaurantState.empty();
        var inv = inventory("Food_Kebab_Vegetable", 5, "Food_Kebab_Mushroom", 4);
        assertEquals(PurchaseResult.NEED_INGREDIENT, RestaurantUpgrades.tryPurchase(state, Branch.MENU, town, inv, true));
        assertEquals(1000, town.getTreasuryGoldCoinCount()); assertEquals(5, InventoryMaterials.count(inv, "Food_Kebab_Vegetable"));
        assertEquals(0, state.getMenuLevel());
        var stocked = inventory("Food_Kebab_Vegetable", 5, "Food_Kebab_Mushroom", 5, "Food_Salad_Berry", 5, "Food_Salad_Mushroom", 5, "Food_Pie_Apple", 5, "Food_Pie_Pumpkin", 5);
        for (int tier = 1; tier <= 3; tier++) assertEquals(PurchaseResult.OK, RestaurantUpgrades.tryPurchase(state, Branch.MENU, town, stocked, true));
        assertEquals(3, state.getMenuLevel());
        assertEquals(1000 - 30 - 55 - 85, town.getTreasuryGoldCoinCount());
        assertEquals(PurchaseResult.MAXED, RestaurantUpgrades.tryPurchase(state, Branch.MENU, town, stocked, true));
        assertFalse(RestaurantUpgrades.canAfford(state, Branch.MENU, town, stocked, true));
        assertEquals(1000 - 170, town.getTreasuryGoldCoinCount());
    }

    // --- old saves ------------------------------------------------------------------------------------------------

    @Test void restaurantsSavedBeforeTheNewBranchesLoadUnchangedWithBothAtZero() {
        var old = GSON.fromJson("{\"satietyLevel\":2,\"serviceLevel\":3}", PlotRestaurantState.class);
        old.migrateIfNeeded();
        assertEquals(2, old.getSatietyLevel()); assertEquals(3, old.getServiceLevel());
        assertEquals(0, old.getMenuLevel()); assertEquals(0, old.getProvisionsLevel());
        UUID plotId = UUID.randomUUID();
        var town = GSON.fromJson("{\"plotRestaurantByPlotId\":{\"" + plotId + "\":{\"satietyLevel\":1,\"serviceLevel\":2}}}", TownRecord.class);
        var loaded = town.getOrCreatePlotRestaurant(plotId);
        assertEquals(1, loaded.getSatietyLevel()); assertEquals(2, loaded.getServiceLevel());
        assertEquals(0, loaded.getMenuLevel()); assertEquals(0, loaded.getProvisionsLevel());
        loaded.setMenuLevel(2); loaded.setProvisionsLevel(1);
        var again = GSON.fromJson(GSON.toJson(town), TownRecord.class).getOrCreatePlotRestaurant(plotId);
        assertEquals(1, again.getSatietyLevel()); assertEquals(2, again.getServiceLevel());
        assertEquals(2, again.getMenuLevel()); assertEquals(1, again.getProvisionsLevel());
        var corrupt = GSON.fromJson("{\"menuLevel\":40,\"provisionsLevel\":-7}", PlotRestaurantState.class);
        assertEquals(3, corrupt.getMenuLevel()); assertEquals(0, corrupt.getProvisionsLevel());
    }

    // --- the upgrade tree the screen draws ----------------------------------------------------------------------------

    @Test void theUpgradeTreeOffersEveryLevelOfAllFourBranchesInOrder() {
        var tree = BuildingUpgradeTree.restaurant();
        assertEquals(13, tree.size());
        for (Branch branch : Branch.values()) for (int tier = 1; tier <= 3; tier++) {
            String id = branch.name() + ":" + tier;
            var node = tree.stream().filter(n -> n.id().equals(id)).findFirst().orElseThrow();
            assertEquals(BuildingUpgradeTree.Kind.RESTAURANT, node.kind()); assertEquals(branch.name(), node.branch()); assertEquals(tier, node.tier());
            assertEquals(List.of(tier == 1 ? "home" : branch.name() + ":" + (tier - 1)), node.parents());
            assertEquals(RestaurantUpgrades.ingredientCosts(branch, tier).getFirst().itemId(), node.item());
            assertFalse(node.title().isBlank()); assertFalse(node.description().isBlank());
            assertFalse(node.description().contains(" - ") || node.description().contains("—") || node.description().contains("–"));
            if (branch == Branch.SATIETY || branch == Branch.SERVICE) assertEquals(tier + 1, node.level(), "Existing branches keep their Town Levels");
        }
        assertEquals("Hearty meals 2", tree.stream().filter(n -> n.id().equals("SATIETY:2")).findFirst().orElseThrow().title());
        assertEquals("Table service 3", tree.stream().filter(n -> n.id().equals("SERVICE:3")).findFirst().orElseThrow().title());
    }
}
