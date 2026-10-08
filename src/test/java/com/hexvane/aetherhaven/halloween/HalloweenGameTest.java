package com.hexvane.aetherhaven.halloween;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.town.*;
import com.hypixel.hytale.assetstore.*;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.event.*;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class HalloweenGameTest {
    private static Object previousStore;
    private static class Items extends AssetStore<String,Item,DefaultAssetMap<String,Item>> {
        private final EventBus events=new EventBus(false);
        Items(Config config){super(config);}
        @Override protected IEventBus getEventBus(){return events;}
        @Override public void addFileMonitor(String pack,Path path){}
        @Override public void removeFileMonitor(Path path){}
        @Override protected void handleRemoveOrUpdate(Set<String> removed,Map<String,Item> updated,AssetUpdateQuery query){}
        private static class Config extends Builder<String,Item,DefaultAssetMap<String,Item>,Config> {
            Config(){super(String.class,Item.class,new DefaultAssetMap<>());setPath("Items");setCodec(Item.CODEC);setKeyFunction(Item::getId);}
            @Override public Items build(){return new Items(this);}
        }
    }
    @BeforeAll static void assets() throws Exception {
        var f=Item.class.getDeclaredField("ASSET_STORE");f.setAccessible(true);previousStore=f.get(null);f.set(null,new Items.Config().build());
    }
    @AfterAll static void restore() throws Exception {var f=Item.class.getDeclaredField("ASSET_STORE");f.setAccessible(true);f.set(null,previousStore);}
    private final UUID player=UUID.randomUUID(), townId=UUID.randomUUID();
    private final HalloweenGameState.Spot spot=new HalloweenGameState.Spot(UUID.randomUUID(),10,30,20,"position");
    private static void put(ItemContainer bag,int slot,ItemStack item){bag.setItemStackForSlot((short)slot,item,false);}
    @Test void rewardIsOncePerPlayerPerGameDayAndSurvivesTownSave() {
        var town=new TownRecord();var state=town.halloweenGame();var game=state.start(player,80,spot);
        assertFalse(state.reward(player,game.id(),80));
        assertTrue(state.pickup(player,game.id()));assertTrue(state.reward(player,game.id(),80));
        assertFalse(state.reward(player,game.id(),80));
        var restored=new Gson().fromJson(new Gson().toJson(town),TownRecord.class).halloweenGame();
        assertFalse(restored.canStart(player,80));assertFalse(restored.canStart(player,79));
        assertTrue(restored.canStart(player,81));assertTrue(restored.canStart(UUID.randomUUID(),80));
    }
    @Test void otherPlayersCannotPickUpReturnOrReplaceAnActiveGame() {
        var state=new HalloweenGameState();var game=state.start(player,4,spot);var stranger=UUID.randomUUID();
        assertNull(state.start(stranger,4,spot));assertFalse(state.pickup(stranger,game.id()));
        assertFalse(state.pickup(player,UUID.randomUUID()));assertFalse(state.reward(stranger,game.id(),4));
        assertEquals(game,state.active());
    }
    @Test void cancellationLeavesAnOwnedCleanupReceiptWithoutSpendingDailyReward() {
        var state=new HalloweenGameState();var game=state.start(player,4,spot);state.cancel();
        assertTrue(state.canStart(player,4));assertEquals(List.of(spot),state.cleanup());
        assertFalse(state.pickup(player,game.id()));state.cleaned(spot);assertTrue(state.cleanup().isEmpty());
        var replacement=state.start(player,4,spot);assertNotEquals(game.id(),replacement.id());
        assertTrue(state.pickup(player,replacement.id()));state.cancel();assertTrue(state.cleanup().isEmpty());
    }
    @Test void activeAndFoundGamesSurviveReloadWithTheSameItemReceipt() {
        var state=new HalloweenGameState();var game=state.start(player,4,spot);
        var gson=new Gson();state=gson.fromJson(gson.toJson(state),HalloweenGameState.class);
        assertEquals(game,state.active());state.pickup(player,game.id());
        state=gson.fromJson(gson.toJson(state),HalloweenGameState.class);assertTrue(state.active().found());
        assertTrue(HalloweenGameInventory.matches(HalloweenGameInventory.head(townId,game),townId,state.active()));
        assertFalse(HalloweenGameInventory.matches(new ItemStack(HalloweenGameInventory.HEAD,1),townId,game));
        assertFalse(HalloweenGameInventory.matches(HalloweenGameInventory.head(townId,game),UUID.randomUUID(),game));
    }
    @Test void fullInventoryLeavesPickupAndWorldCommitUntouched() {
        var bag=new SimpleItemContainer((short)1);var existing=new ItemStack("Ingredient_Stick",2);put(bag,0,existing);
        var state=new HalloweenGameState();var game=state.start(player,4,spot);var commit=new AtomicBoolean();
        assertFalse(HalloweenGameInventory.pickup(bag,HalloweenGameInventory.head(townId,game),()->{commit.set(true);return true;}));
        assertFalse(commit.get());assertEquals(existing,bag.getItemStack((short)0));
    }
    @Test void completelyEmptyCombinedInventoryCanReceiveTheHead() {
        var bag=new CombinedItemContainer(new SimpleItemContainer((short)2),new SimpleItemContainer((short)2));
        var state=new HalloweenGameState();var game=state.start(player,4,spot);
        assertTrue(HalloweenGameInventory.pickup(bag,HalloweenGameInventory.head(townId,game),()->state.pickup(player,game.id())));
        assertTrue(HalloweenGameInventory.matches(bag.getItemStack((short)0),townId,game));assertTrue(state.active().found());
    }
    @Test void insufficientRewardSpaceCannotConsumeHeadOrPayHalfAReward() {
        var bag=new SimpleItemContainer((short)1);var state=new HalloweenGameState();var game=state.start(player,4,spot);
        var head=HalloweenGameInventory.head(townId,game);put(bag,0,head);state.pickup(player,game.id());
        assertFalse(HalloweenGameInventory.reward(bag,townId,game,()->state.reward(player,game.id(),4)));
        assertEquals(head,bag.getItemStack((short)0));assertTrue(state.active().found());assertFalse(state.rewarded(player,4));
    }
    @Test void combinedInventoryReturnPaysExactRewardAndRejectsDuplicateReceipts() {
        var state=new HalloweenGameState();var game=state.start(player,4,spot);state.pickup(player,game.id());
        var bag=new CombinedItemContainer(new SimpleItemContainer((short)1),new SimpleItemContainer((short)2));
        var head=HalloweenGameInventory.head(townId,game);put(bag,0,head);put(bag,2,head);
        assertTrue(HalloweenGameInventory.reward(bag,townId,game,()->state.reward(player,game.id(),4)));
        assertEquals(5,bag.getItemStack((short)0).getQuantity());assertEquals("Aetherhaven_Spooky_Candy",bag.getItemStack((short)0).getItemId());
        assertEquals(5,bag.getItemStack((short)1).getQuantity());assertEquals("Aetherhaven_Gold_Coin",bag.getItemStack((short)1).getItemId());
        assertEquals(head,bag.getItemStack((short)2));
        assertFalse(HalloweenGameInventory.reward(bag,townId,game,()->state.reward(player,game.id(),4)));
        assertEquals(5,bag.getItemStack((short)0).getQuantity());assertEquals(5,bag.getItemStack((short)1).getQuantity());
    }
    @Test void movedOrRemovedBuildingsInvalidateHidingPlaces() {
        var town=new TownRecord();var id=UUID.randomUUID();
        var plot=new PlotInstance(id,"plot_house",PlotInstanceState.COMPLETE,new PlotFootprintRecord(0,20,0,20,40,20),0,20,0,0);
        town.getPlotInstances().add(plot);
        var location=new HalloweenGameState.Spot(id,10,30,10,HalloweenGameService.buildingPosition(plot));
        assertTrue(HalloweenGameService.validBuilding(town,location));town.getPlotInstances().clear();
        assertFalse(HalloweenGameService.validBuilding(town,location));
        town.getPlotInstances().add(new PlotInstance(id,"plot_house",PlotInstanceState.COMPLETE,new PlotFootprintRecord(1,20,0,21,40,20),1,20,0,0));
        assertFalse(HalloweenGameService.validBuilding(town,location));
    }
}
