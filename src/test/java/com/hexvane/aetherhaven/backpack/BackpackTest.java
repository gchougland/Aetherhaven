package com.hexvane.aetherhaven.backpack;
import static org.junit.jupiter.api.Assertions.*;
import com.hypixel.hytale.assetstore.*;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.event.*;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.*;
import java.nio.file.Path;
import java.util.*;
import org.bson.*;
import org.junit.jupiter.api.*;
@Tag("town")
class BackpackTest {
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
        var field=Item.class.getDeclaredField("ASSET_STORE");field.setAccessible(true);previousStore=field.get(null);field.set(null,new Items.Config().build());
    }
    @AfterAll static void restore() throws Exception {
        var field=Item.class.getDeclaredField("ASSET_STORE");field.setAccessible(true);field.set(null,previousStore);
    }

    static void put(ItemContainer inv,int slot,ItemStack item){inv.setItemStackForSlot((short)slot,item,false);}
    @Test void namingPersistsThroughUpgradeAndRejectsStaleChanges(){
        var inv=new SimpleItemContainer((short)2);var bag=Backpack.create();put(inv,0,bag);
        assertTrue(BackpackExchange.change(inv,(short)0,bag,Map.of(),p->p.rename("  Ore supplies  ")));
        assertFalse(BackpackExchange.change(inv,(short)0,bag,Map.of(),p->p.rename("Stale")));
        var saved=inv.getItemStack((short)0);var pack=Backpack.read(saved);assertEquals("Ore supplies",pack.displayName());
        var id=pack.id;pack.insert(new ItemStack("test",1));assertTrue(BackpackUpgrades.apply(pack,"IRON"));
        pack=Backpack.read(pack.write(saved));assertEquals("Ore supplies",pack.displayName());assertEquals(id,pack.id);assertEquals(1,pack.used());
        assertFalse(pack.rename("x".repeat(31)));assertFalse(pack.rename("bad\nname"));assertEquals("Ore supplies",pack.name);
        assertTrue(pack.rename(""));assertEquals("Iron Backpack",pack.displayName());
    }
    @Test void tiersBranchThenCombineWithoutDowngrade(){
        var pack=Backpack.read(Backpack.create());assertEquals(15,pack.tier.slots);assertEquals(1,pack.multiplier());
        assertFalse(BackpackUpgrades.apply(pack,"ADAMANTITE"));assertTrue(BackpackUpgrades.apply(pack,"IRON"));
        assertEquals(List.of("COBALT","THORIUM","Compression","Magnet","Jukebox"),BackpackUpgrades.available(pack));
        assertTrue(BackpackUpgrades.apply(pack,"THORIUM"));assertEquals(20,pack.tier.slots);assertEquals(2,pack.multiplier());
        assertFalse(BackpackUpgrades.apply(pack,"COBALT"));assertTrue(BackpackUpgrades.apply(pack,"ADAMANTITE"));assertEquals(40,pack.tier.slots);assertEquals(3,pack.multiplier());
        for(int i=0;i<3;i++)assertTrue(BackpackUpgrades.apply(pack,"Compression"));assertFalse(BackpackUpgrades.apply(pack,"Compression"));assertEquals(6,pack.multiplier());
    }
    @Test void contentsAndOptionsRoundTripWithoutLosingItemMetadata(){
        var original=Backpack.create();var pack=Backpack.read(original);pack.tier=Backpack.Tier.ADAMANTITE;pack.compression=3;pack.magnet=true;pack.magnetOn=true;pack.jukebox=true;pack.song="test";
        var item=new ItemStack("test",1).withMetadata("engraved",new BsonString("Mertie"));assertEquals(1,pack.insert(item));
        var saved=pack.write(original);var again=Backpack.read(ItemStack.CODEC.decode(ItemStack.CODEC.encode(saved,new com.hypixel.hytale.codec.ExtraInfo()),new com.hypixel.hytale.codec.ExtraInfo()));
        assertNotNull(again);assertEquals(pack.id,again.id);assertEquals(item,again.items[0]);assertEquals(6,again.multiplier());assertTrue(again.magnetOn);assertEquals("test",again.song);
        assertEquals(0,again.insert(Backpack.create()));
    }
    @Test void upgradeChangesAppearanceWithoutChangingIdentityOrStoredItems(){
        var bag=Backpack.create().withMetadata("engraved",new BsonString("Sera"));
        var pack=Backpack.read(bag);var item=new ItemStack("test",1).withMetadata("special",BsonBoolean.TRUE);
        pack.insert(item);var id=pack.id;
        for(var tier:Backpack.Tier.values()){
            pack.tier=tier;bag=pack.write(bag);pack=Backpack.read(bag);
            assertEquals(Backpack.itemId(tier),bag.getItemId());assertEquals(id,pack.id);assertEquals(item,pack.items[0]);
            assertEquals(new BsonString("Sera"),com.hexvane.aetherhaven.inventory.ItemStackData.metadata(bag).get("engraved"));
            assertEquals(0,pack.insert(new ItemStack(Backpack.itemId(tier),1)));
        }
    }
    @Test void depositWithdrawAndStaleReplayConserveItemsAcrossCombinedInventory(){
        var inv=new CombinedItemContainer(new SimpleItemContainer((short)2),new SimpleItemContainer((short)3));
        var bag=Backpack.create();var item=new ItemStack("test",5);put(inv,3,bag);put(inv,1,item);
        assertTrue(BackpackExchange.transfer(inv,(short)3,bag,1,item,true,true));
        assertEquals(4,inv.getItemStack((short)1).getQuantity());var updated=inv.getItemStack((short)3);
        assertFalse(BackpackExchange.transfer(inv,(short)3,bag,1,item,true,true));
        assertTrue(BackpackExchange.transfer(inv,(short)3,updated,0,Backpack.read(updated).items[0],false,true));
        assertEquals(5,inv.countItemStacks(s->s.getItemId().equals("test")));assertEquals(0,Backpack.read(inv.getItemStack((short)3)).used());
    }
    @Test void craftingAndFailedUpgradeAreAtomic(){
        var inv=new SimpleItemContainer((short)4);put(inv,1,new ItemStack("Ingredient_Leather_Light",10));
        assertTrue(BackpackExchange.craft(inv));var bag=inv.getItemStack((short)0);assertTrue(Backpack.isBackpack(bag));assertTrue(ItemStack.isEmpty(inv.getItemStack((short)1)));
        put(inv,1,new ItemStack("Ingredient_Leather_Light",10));var p=Backpack.read(bag);
        assertFalse(BackpackExchange.change(inv,(short)0,bag,BackpackUpgrades.cost(p,"IRON"),x->BackpackUpgrades.apply(x,"IRON")));
        assertEquals(bag,inv.getItemStack((short)0));assertEquals(10,inv.getItemStack((short)1).getQuantity());
        put(inv,2,new ItemStack("Ingredient_Bar_Iron",10));
        assertTrue(BackpackExchange.change(inv,(short)0,bag,BackpackUpgrades.cost(p,"IRON"),x->BackpackUpgrades.apply(x,"IRON")));
        assertEquals(Backpack.Tier.IRON,Backpack.read(inv.getItemStack((short)0)).tier);assertTrue(ItemStack.isEmpty(inv.getItemStack((short)2)));
    }
    @Test void withdrawalIntoEmptySlotMustPublishTheNewStack(){
        var inv=new SimpleItemContainer((short)2);var bag=Backpack.create();var pack=Backpack.read(bag);pack.insert(new ItemStack("test",1));bag=pack.write(bag);put(inv,1,bag);
        assertTrue(BackpackExchange.transfer(inv,(short)1,bag,0,pack.items[0],false,true));assertEquals(1,inv.getItemStack((short)0).getQuantity());assertEquals(0,Backpack.read(inv.getItemStack((short)1)).used());
    }
    @Test void fullInventoryCannotLoseWithdrawnContents(){
        var inv=new SimpleItemContainer((short)1);var bag=Backpack.create();var pack=Backpack.read(bag);pack.insert(new ItemStack("test",1));bag=pack.write(bag);put(inv,0,bag);
        assertFalse(BackpackExchange.transfer(inv,(short)0,bag,0,pack.items[0],false,true));assertEquals(bag,inv.getItemStack((short)0));
    }
    @Test void taggedGuardDropsCannotMergeWithUnrelatedItemsAndCleanKeepsOtherMetadata(){
        var source=new ItemStack("test",1).withMetadata("engraved",new BsonString("Ada"));
        var marked=com.hexvane.aetherhaven.patrol.GuardSpoils.mark(source,"guardA");assertFalse(marked.isStackableWith(source));
        assertFalse(marked.isStackableWith(com.hexvane.aetherhaven.patrol.GuardSpoils.mark(source,"guardB")));
        assertEquals(source,com.hexvane.aetherhaven.patrol.GuardSpoils.clean(marked));
        var plain=new ItemStack("test",1);assertEquals(plain,com.hexvane.aetherhaven.patrol.GuardSpoils.clean(com.hexvane.aetherhaven.patrol.GuardSpoils.mark(plain,"guardA")));
    }
}
