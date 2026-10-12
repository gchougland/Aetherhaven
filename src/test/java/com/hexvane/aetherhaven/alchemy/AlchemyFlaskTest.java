package com.hexvane.aetherhaven.alchemy;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
import com.hypixel.hytale.assetstore.*;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.event.*;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.*;
import java.nio.file.Path;
import java.util.*;
import org.bson.BsonDocument;
import org.junit.jupiter.api.*;

@Tag("town")
class AlchemyFlaskTest {
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
    private static ItemStack fresh() { return new ItemStack(AlchemyFlask.ITEM,1); }
    private static void put(ItemContainer bag,int slot,ItemStack stack) { bag.setItemStackForSlot((short)slot,stack,false); }
    @Test void craftedAndCreativeFlasksStartEmptyAndUpgradesNeverRefill() {
        var flask=fresh();assertEquals(1,AlchemyFlask.capacity(flask));assertEquals(0,AlchemyFlask.charges(flask));
        flask=AlchemyFlask.fill(flask,List.of("Plant_Crop_Health1"));
        for(int i=0;i<9;i++)flask=AlchemyFlask.upgrade(flask);
        assertEquals(5,AlchemyFlask.capacity(flask));assertEquals(1,AlchemyFlask.charges(flask));
        flask=AlchemyFlask.consume(flask);assertEquals(0,AlchemyFlask.charges(flask));
        assertEquals(0,AlchemyFlask.charges(AlchemyFlask.consume(flask)));
    }
    @Test void metadataRoundTripRetainsMixtureCapacityChargesAndOtherMetadata() {
        var flask=AlchemyFlask.upgrade(fresh().withMetadata("engraving",new BsonDocument("text",new org.bson.BsonString("Orin"))));
        var ingredients=List.of("Plant_Crop_Health2","Plant_Crop_Mana3","Plant_Crop_Mushroom_Boomshroom_Small");
        flask=AlchemyFlask.consume(AlchemyFlask.fill(flask,ingredients));
        var restored=new ItemStack(flask.getItemId(),1,flask.getDurability(),flask.getMaxDurability(),BsonDocument.parse(com.hexvane.aetherhaven.inventory.ItemStackData.metadata(flask).toJson()));
        assertEquals(2,AlchemyFlask.capacity(restored));assertEquals(1,AlchemyFlask.charges(restored));
        assertEquals(ingredients,AlchemyFlask.ingredients(restored));assertTrue(com.hexvane.aetherhaven.inventory.ItemStackData.metadata(restored).containsKey("engraving"));
    }
    @Test void nativeTooltipRetainsEveryCombinedEffectAndItsColor() {
        var ingredients=List.of("Plant_Crop_Health2","Plant_Crop_Mana3","Plant_Crop_Mushroom_Boomshroom_Small");
        var flask=AlchemyFlask.fill(fresh(),ingredients);
        var display=flask.getFromMetadataOrNull(com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata.KEYED_CODEC);
        var description=display.getDescription().getFormattedMessage();
        String plain=com.hypixel.hytale.server.core.util.MessageUtil.formatMessageToPlainString(description);
        for(var ingredient:AlchemyIngredients.effects(ingredients))assertTrue(plain.contains(ingredient.description()));
        assertEquals(3,description.children.length);
        assertEquals(3,Arrays.stream(description.children).map(m->m.color).distinct().count());
        assertTrue(Arrays.stream(description.children).allMatch(m->m.color!=null));
        var empty=AlchemyFlask.empty(flask).getFromMetadataOrNull(com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata.KEYED_CODEC);
        assertFalse(com.hypixel.hytale.server.core.util.MessageUtil.formatMessageToPlainString(empty.getDescription().getFormattedMessage()).contains("Explosion immunity"));
    }
    @Test void regenerationFireWardAndRepellentBottleLikeAnyOtherMixture() {
        var ingredients=List.of("Plant_Crop_Mushroom_Glowing_Green","Plant_Flower_Flax_Orange","Plant_Flower_Tall_Purple");
        assertTrue(AlchemyIngredients.validMix(ingredients,3));
        var flask=AlchemyFlask.consume(AlchemyFlask.fill(AlchemyFlask.upgrade(fresh()),ingredients));
        assertEquals(1,AlchemyFlask.charges(flask));assertEquals(ingredients,AlchemyFlask.ingredients(flask));
        var display=flask.getFromMetadataOrNull(com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata.KEYED_CODEC);
        var description=display.getDescription().getFormattedMessage();
        String plain=com.hypixel.hytale.server.core.util.MessageUtil.formatMessageToPlainString(description);
        assertTrue(plain.contains("Regeneration 3"));assertTrue(plain.contains("Fire and lava immunity"));assertTrue(plain.contains("Bug repellent"));
        var colours=Arrays.stream(description.children).map(m->m.color).toList();
        assertEquals(3,new HashSet<>(colours).size(),"Each new property has its own tooltip colour");
        assertFalse(colours.contains("#D4C7ED"),"A new property fell back to the default colour");
        // The weaker aloe in the same mixture is hidden by the stronger mushroom, as with every other family.
        var mixed=AlchemyIngredients.effects(List.of("Plant_Flower_Common_Blue2","Plant_Crop_Mushroom_Cap_Green"));
        assertEquals(1,mixed.size());assertEquals("Aetherhaven_Alchemy_Regen2",mixed.getFirst().effect());
        assertEquals(Map.of("Plant_Flower_Flax_Orange",4),AlchemyInventory.brewCosts(List.of("Plant_Flower_Flax_Orange"),4));
    }
    @Test void upgradingLegacyTooltipPreservesPaidCapacityAndRemainingMixture() {
        var flask=AlchemyFlask.consume(AlchemyFlask.fill(AlchemyFlask.upgrade(AlchemyFlask.upgrade(fresh())),List.of("Plant_Crop_Mana2")));
        var metadata=BsonDocument.parse(com.hexvane.aetherhaven.inventory.ItemStackData.metadata(flask).toJson());metadata.getDocument("AetherhavenAlchemyFlask").remove("displayVersion");
        flask=new ItemStack(flask.getItemId(),1,flask.getDurability(),flask.getMaxDurability(),metadata);
        assertTrue(AlchemyFlask.needsDisplayRefresh(flask));var updated=AlchemyFlask.refreshDisplay(flask);
        assertFalse(AlchemyFlask.needsDisplayRefresh(updated));assertEquals(3,AlchemyFlask.capacity(updated));assertEquals(2,AlchemyFlask.charges(updated));
        assertEquals(List.of("Plant_Crop_Mana2"),AlchemyFlask.ingredients(updated));
    }
    @Test void missingLaterIngredientLeavesFlaskAndEarlierIngredientsUntouched() {
        var bag=new SimpleItemContainer((short)3);var flask=AlchemyFlask.upgrade(fresh());put(bag,0,flask);
        var health=new ItemStack("Plant_Crop_Health1",4);put(bag,1,health);
        var mix=List.of("Plant_Crop_Health1","Plant_Crop_Mana1");
        assertFalse(AlchemyInventory.exchange(bag,(short)0,flask,AlchemyFlask.fill(flask,mix),AlchemyInventory.brewCosts(mix,2)));
        assertEquals(flask,bag.getItemStack((short)0));assertEquals(health,bag.getItemStack((short)1));
    }
    @Test void combinedInventoryConsumesAcrossStacksAndReplayCannotDuplicateCharges() {
        var backpack=new SimpleItemContainer((short)3);var hotbar=new SimpleItemContainer((short)2);var bag=new CombinedItemContainer(backpack,hotbar);
        var flask=AlchemyFlask.upgrade(fresh());put(bag,3,flask);
        put(bag,0,new ItemStack("Plant_Crop_Health1",1));put(bag,1,new ItemStack("Plant_Crop_Health1",3));
        var mix=List.of("Plant_Crop_Health1");var filled=AlchemyFlask.fill(flask,mix);var costs=AlchemyInventory.brewCosts(mix,2);
        assertTrue(AlchemyInventory.exchange(bag,(short)3,flask,filled,costs));
        assertEquals(2,AlchemyFlask.charges(bag.getItemStack((short)3)));assertEquals(2,bag.countItemStacks(s->s.getItemId().equals("Plant_Crop_Health1")));
        assertFalse(AlchemyInventory.exchange(bag,(short)3,flask,filled,costs));
        assertEquals(2,bag.countItemStacks(s->s.getItemId().equals("Plant_Crop_Health1")));
    }
    @Test void movedOrReplacedFlaskCannotSpendIngredients() {
        var bag=new SimpleItemContainer((short)3);var flask=fresh();put(bag,1,flask);put(bag,2,new ItemStack("Plant_Crop_Health1",3));
        assertFalse(AlchemyInventory.exchange(bag,(short)0,flask,AlchemyFlask.fill(flask,List.of("Plant_Crop_Health1")),Map.of("Plant_Crop_Health1",1)));
        assertEquals(3,bag.getItemStack((short)2).getQuantity());
    }
    @Test void failedUpgradeCannotCreateFreeCapacityAndSuccessDoesNotCreateCharges() {
        var bag=new SimpleItemContainer((short)2);var flask=fresh();put(bag,0,flask);
        var cost=Map.of(AlchemyFlask.upgradeLeather(1),AlchemyFlask.upgradeLeatherCount(1));
        assertFalse(AlchemyInventory.exchange(bag,(short)0,flask,AlchemyFlask.upgrade(flask),cost));
        put(bag,1,new ItemStack(AlchemyFlask.upgradeLeather(1),5));
        assertTrue(AlchemyInventory.exchange(bag,(short)0,flask,AlchemyFlask.upgrade(flask),cost));
        assertEquals(2,AlchemyFlask.capacity(bag.getItemStack((short)0)));assertEquals(0,AlchemyFlask.charges(bag.getItemStack((short)0)));
        assertNull(bag.getItemStack((short)1));
    }
    @Test void sameFamilyUsesStrongestAndSlotAllowanceCannotBeBypassed() {
        var ingredients=List.of("Plant_Crop_Health1","Plant_Crop_Health3","Plant_Crop_Mana1");
        var effects=AlchemyIngredients.effects(ingredients);assertEquals(2,effects.size());assertEquals(3,effects.getFirst().strength());
        assertFalse(AlchemyIngredients.validMix(ingredients,2));assertTrue(AlchemyIngredients.validMix(ingredients,3));
        assertFalse(AlchemyIngredients.validMix(List.of("Plant_Crop_Health1","Plant_Crop_Health1"),3));
        assertFalse(AlchemyIngredients.validMix(List.of("fake"),1));
    }
    @Test void townResearchPersistsAndPurchasesAreSequential() {
        var lab=new AlchemyLaboratoryState();assertEquals(1,lab.ingredientSlots());
        assertFalse(lab.upgrade(1));assertTrue(lab.upgrade(0));assertFalse(lab.upgrade(0));
        lab.discover(List.of("Plant_Crop_Health1"));var gson=new Gson();
        lab=gson.fromJson(gson.toJson(lab),AlchemyLaboratoryState.class);
        assertEquals(2,lab.ingredientSlots());assertTrue(lab.discovered("Plant_Crop_Health1"));
        assertTrue(lab.upgrade(1));assertFalse(lab.upgrade(2));assertEquals(3,lab.ingredientSlots());
    }
    @Test void aMasteryLevelTakesAboutEightFilledFlasksAllTheWayUp() {
        for(int mastery=1;mastery<10;mastery++) {
            // The best flask and mixture a town can have at this Mastery: capacity grows at 2, 4, 6 and 8, slots at 3 and 6.
            int capacity=1;while(capacity<5&&AlchemyFlask.upgradeMastery(capacity)<=mastery)capacity++;
            int ingredients=mastery>=6?3:mastery>=3?2:1;
            long cost=100L+50L*(mastery-1),fills=Math.ceilDiv(cost,AlchemyFlask.bottlingXp(ingredients,capacity));
            assertTrue(fills>=7&&fills<=9,"Mastery "+mastery+" takes "+fills+" fills");
        }
        assertEquals(8,Math.ceilDiv(100L,AlchemyFlask.bottlingXp(1,1)),"The first level with a new flask and one ingredient");
    }
}
