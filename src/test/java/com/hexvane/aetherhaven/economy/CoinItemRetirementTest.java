package com.hexvane.aetherhaven.economy;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.economy.api.AetherhavenEconomy;
import com.hexvane.aetherhaven.economy.api.EconomyProvider;
import com.hexvane.aetherhaven.economy.api.GoldAccount;
import com.hexvane.aetherhaven.economy.api.GoldSource;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.data.TownsfolkPersonalityDefinition;
import com.hexvane.aetherhaven.villager.data.VillagerDefinition;
import com.hypixel.hytale.assetstore.AssetStore;
import com.hypixel.hytale.assetstore.AssetUpdateQuery;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.event.EventBus;
import com.hypixel.hytale.event.IEventBus;
import com.hypixel.hytale.protocol.BenchRequirement;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.CraftingRecipe;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.*;

/** Under a provider that is not the coin, the coin item is retired: deposited when held, rewritten out of recipes, no villager wants it. */
@Tag("economy")
class CoinItemRetirementTest {
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
        field.set(null, itemStore);
    }

    @AfterAll static void restoreAssets() throws Exception {
        var field = Item.class.getDeclaredField("ASSET_STORE");
        field.setAccessible(true);
        field.set(null, previousItemStore);
    }

    /** A balance in a long, refusing deposits on demand. */
    static final class MemoryAccount implements GoldAccount {
        long balance;
        boolean refuseDeposits;
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

    /** Hands out one token per recipe, or nothing. */
    private static final class TokenProvider implements EconomyProvider {
        String token = "Test_Token";
        long tokensFor = -1;
        @Override public String id() { return "test:tokens"; }
        @Override public GoldAccount account(Ref<EntityStore> ref, Store<EntityStore> store) { return null; }
        @Override public GoldAccount townAccount(TownRecord town) { return new MemoryAccount(); }
        @Override public GoldAccount shopSafe(TownRecord town, UUID player) { return new MemoryAccount(); }
        @Override public List<ItemStack> goldItems(GoldSource source, String itemId, long amount) {
            tokensFor = amount;
            return token == null ? List.of() : List.of(new ItemStack(token, 1));
        }
        @Override public Message amount(long amount) { return Message.raw(String.valueOf(amount)); }
        @Override public void show(UICommandBuilder builder, String selector, long amount, int fontSize) {}
    }

    private final TokenProvider tokens = new TokenProvider();
    private final Gson gson = new Gson();
    private static final String COIN = ItemCoinEconomy.coinItemId();

    @AfterEach void unregister() {
        AetherhavenEconomy.unregister(tokens);
    }

    private static CraftingRecipe salvage(int coins) {
        MaterialQuantity token = new MaterialQuantity("Aetherhaven_Plot_Token_Barn", null, null, 1, null);
        MaterialQuantity out = new MaterialQuantity(COIN, null, null, coins, null);
        MaterialQuantity scrap = new MaterialQuantity("Ingredient_Fabric_Scrap", null, null, 1, null);
        return new CraftingRecipe(new MaterialQuantity[] {token}, out, new MaterialQuantity[] {out, scrap}, 1,
            new BenchRequirement[0], 4f, false, 0);
    }

    @Test void aCoinRecipeGivesTheProvidersItemsInstead() {
        CraftingRecipe recipe = salvage(5);
        assertEquals(CoinRecipeRewriter.Outcome.REWRITTEN, CoinRecipeRewriter.rewrite(recipe, tokens, COIN));
        assertEquals(5, tokens.tokensFor);
        assertEquals("Ingredient_Fabric_Scrap", recipe.getOutputs()[0].getItemId());
        assertEquals("Test_Token", recipe.getOutputs()[1].getItemId());
        assertEquals("Ingredient_Fabric_Scrap", recipe.getPrimaryOutput().getItemId());
        assertFalse(recipe.isKnowledgeRequired());
        assertEquals(CoinRecipeRewriter.Outcome.UNTOUCHED, CoinRecipeRewriter.rewrite(recipe, tokens, COIN), "no coin left to rewrite");
    }

    @Test void aCoinRecipeIsHiddenWhenTheProviderHandsOutNothing() {
        tokens.token = null;
        CraftingRecipe recipe = new CraftingRecipe(
            new MaterialQuantity[] {new MaterialQuantity("Aetherhaven_Plot_Token_Barn", null, null, 1, null)},
            new MaterialQuantity(COIN, null, null, 5, null), null, 1, new BenchRequirement[0], 4f, false, 0);
        assertEquals(CoinRecipeRewriter.Outcome.HIDDEN, CoinRecipeRewriter.rewrite(recipe, tokens, COIN));
        assertTrue(recipe.isKnowledgeRequired());
    }

    @Test void aRecipeWithoutCoinsIsLeftAlone() {
        CraftingRecipe recipe = new CraftingRecipe(
            new MaterialQuantity[] {new MaterialQuantity("Ore_Iron", null, null, 1, null)},
            new MaterialQuantity("Ingredient_Bar_Iron", null, null, 1, null), null, 1, new BenchRequirement[0], 4f, false, 0);
        assertEquals(CoinRecipeRewriter.Outcome.UNTOUCHED, CoinRecipeRewriter.rewrite(recipe, tokens, COIN));
        assertEquals(-1, tokens.tokensFor);
    }

    @Test void coinsHeldAreDepositedAndTakenAway() {
        var inv = new SimpleItemContainer((short) 4);
        inv.setItemStackForSlot((short) 0, new ItemStack(COIN, 3));
        inv.setItemStackForSlot((short) 1, new ItemStack("Ore_Iron", 2));
        inv.setItemStackForSlot((short) 3, new ItemStack(COIN, 4));
        MemoryAccount account = new MemoryAccount();
        assertEquals(7, CoinItemDepositSystem.deposit(inv, account));
        assertEquals(7, account.balance());
        assertEquals(0, inv.countItemStacks(s -> COIN.equals(s.getItemId())));
        assertEquals(2, inv.countItemStacks(s -> "Ore_Iron".equals(s.getItemId())));
        assertEquals(0, CoinItemDepositSystem.deposit(inv, account), "nothing left");
    }

    @Test void coinsGoBackWhenTheDepositIsRefused() {
        var inv = new SimpleItemContainer((short) 4);
        inv.setItemStackForSlot((short) 2, new ItemStack(COIN, 6));
        MemoryAccount account = new MemoryAccount();
        account.refuseDeposits = true;
        assertEquals(-6, CoinItemDepositSystem.deposit(inv, account));
        assertEquals(0, account.balance());
        assertEquals(6, inv.getItemStack((short) 2).getQuantity());
    }

    @Test void villagersForgetTheCoinUnderAProvider() {
        VillagerDefinition villager = gson.fromJson(
            "{\"giftLoves\":[\"Food_Apple\",\"" + COIN + "\"],\"giftLikes\":[\"" + COIN + "\"],\"giftDislikes\":[\"Food_Beef_Raw\"]}",
            VillagerDefinition.class);
        TownsfolkPersonalityDefinition trait = gson.fromJson(
            "{\"id\":\"stingy\",\"thoughtItemWeights\":{\"" + COIN + "\":2,\"Rock_Gem_Diamond\":1}}",
            TownsfolkPersonalityDefinition.class);
        assertEquals(List.of("Food_Apple", COIN), villager.getGiftLoves());
        assertEquals(Map.of(COIN, 2.0, "Rock_Gem_Diamond", 1.0), trait.getThoughtItemWeights());

        AetherhavenEconomy.register(tokens);
        assertEquals(List.of("Food_Apple"), villager.getGiftLoves());
        assertEquals(List.of(), villager.getGiftLikes());
        assertEquals(List.of("Food_Beef_Raw"), villager.getGiftDislikes());
        assertEquals(Map.of("Rock_Gem_Diamond", 1.0), trait.getThoughtItemWeights());
    }
}
