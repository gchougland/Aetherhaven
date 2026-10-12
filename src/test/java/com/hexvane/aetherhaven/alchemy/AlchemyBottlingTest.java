package com.hexvane.aetherhaven.alchemy;

import static org.junit.jupiter.api.Assertions.*;
import com.hexvane.aetherhaven.mastery.CoreVillagerMasteryService;
import com.hexvane.aetherhaven.production.PlotProductionState;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.warehouse.TownWarehouse;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch;
import com.hexvane.aetherhaven.workplace.*;
import com.hypixel.hytale.assetstore.*;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.event.*;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.*;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;

/**
 * The owner's report: "The alchemy cauldron keeps saying my inventory changed so I can't fill the flask". A fill is
 * decided only by what can be taken when the button is pressed. Each test opens the page's view of the town first,
 * changes something, and then fills.
 */
@Tag("town")
class AlchemyBottlingTest {
    private static final String HEALTH="Plant_Crop_Health1",MANA="Plant_Crop_Mana1",RED_CAP="Plant_Crop_Mushroom_Cap_Red";
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

    private final UUID owner=UUID.randomUUID();
    /** A town whose laboratory has bought its second ingredient slot. */
    private TownRecord townWithTwoSlots() {
        var town=town();assertTrue(town.getAlchemyLaboratory().upgrade(0));return town;
    }
    private TownRecord town() {
        var town=new TownRecord();town.setTownId(UUID.randomUUID());town.setOwner(owner,"Owner");
        town.getClaimedTerritoryChunks().add(ClaimedTerritoryChunkRecord.of(0,0));
        town.getResidentNpcRecords().add(new ResidentNpcRecord("druid","druid",null,UUID.randomUUID()));
        town.getResidentNpcRecords().add(new ResidentNpcRecord("alchemist","alchemist",null,UUID.randomUUID()));
        return town;
    }
    /** What AlchemyTownStock.sources finds for grower sites: a new view of each site's store on every call. */
    private static Supplier<List<AlchemyTownStock.Source>> growers(TownRecord town) {
        return ()->{
            var sources=new ArrayList<AlchemyTownStock.Source>();
            for(var site:town.getApprenticeWorkplaces().sites()) {
                var harvest=GrowerOutput.find(town,site.id());
                if(harvest!=null)sources.add(new AlchemyTownStock.Source(site.id(),harvest));
            }
            return sources;
        };
    }
    private static ItemStack flask(int capacity) {
        var flask=new ItemStack(AlchemyFlask.ITEM,1);
        for(int n=1;n<capacity;n++)flask=AlchemyFlask.upgrade(flask);
        return flask;
    }
    private static ItemStack stack(String item,int count){return new ItemStack(item,count);}
    private static void put(ItemContainer bag,int slot,ItemStack stack){bag.setItemStackForSlot((short)slot,stack,false);}
    private static int count(ItemContainer bag,String item){return bag.countItemStacks(s->item.equals(s.getItemId()));}
    /** A player's inventory the way the page sees it: several native sections behind one combined container. */
    private static CombinedItemContainer inventory(){return new CombinedItemContainer(new SimpleItemContainer((short)4),new SimpleItemContainer((short)4));}
    private static void store(TownWarehouse warehouse,ItemStack stack){assertTrue(warehouse.acceptGuardSpoils(stack),"General storage refused "+stack.getItemId());}
    private GrowerSite mushroomYard(TownRecord town) {
        var sites=town.getApprenticeWorkplaces();
        assertNull(sites.unlock(town,TownsfolkWorkType.MYCOLOGY));
        assertNull(sites.save(town,null,TownsfolkWorkType.MYCOLOGY,new WorkplaceArea(0,100,0,4,4),"Log yard"));
        return sites.sites().getFirst().grower();
    }

    @Test void aTownWithAWorkedGrowerSiteStillFillsAndNothingNeedsToHaveChanged() {
        var town=town();mushroomYard(town);
        var sources=growers(town);assertEquals(1,sources.get().size());
        // The cause of the report: each look at the town builds a new view of a grower's store, so two looks never compared equal.
        assertNotEquals(AlchemyTownStock.snapshot(town.getWarehouse(),sources.get()),AlchemyTownStock.snapshot(town.getWarehouse(),sources.get()));
        var bag=inventory();var flask=flask(1);put(bag,0,flask);put(bag,1,stack(HEALTH,3));
        var result=AlchemyBottling.bottle(town,owner,sources,bag,(short)0,flask,List.of(HEALTH));
        assertEquals(AlchemyBottling.Outcome.FILLED,result.outcome());
        assertEquals(1,AlchemyFlask.charges(bag.getItemStack((short)0)));assertEquals(2,count(bag,HEALTH));
    }
    @Test void warehouseContentsChangingBetweenOpeningThePageAndFillingDoNotMatter() {
        var town=townWithTwoSlots();var warehouse=town.getWarehouse();store(warehouse,stack(MANA,2));
        var plot=UUID.randomUUID();var crops=new PlotProductionState();crops.addAmount(HEALTH,2,100);
        Supplier<List<AlchemyTownStock.Source>> sources=()->List.of(new AlchemyTownStock.Source(plot,crops));
        var bag=inventory();var flask=flask(2);put(bag,5,flask);put(bag,0,stack(HEALTH,1));put(bag,1,stack(MANA,1));
        var opened=AlchemyTownStock.snapshot(warehouse,sources.get());
        // While the page is open: a craft is finished, a courier is promised supplies, a harvest lands, a player stores something.
        assertTrue(warehouse.acceptCompletedOutput(stack("Ingredient_Bar_Iron",3)));
        store(warehouse,stack("Ingredient_Fibre",9));
        assertNotNull(warehouse.reserve(UUID.randomUUID(),UUID.randomUUID(),"recipe",List.of(new MaterialQuantity("Ingredient_Fibre",null,null,4,null)),List.of(stack("Ingredient_Bandage",1))));
        crops.addAmount(HEALTH,5,100);warehouse.markProductionWithdrawal();
        assertNotEquals(opened,AlchemyTownStock.snapshot(warehouse,sources.get()),"The town really did change");
        var mix=List.of(HEALTH,MANA);
        var result=AlchemyBottling.bottle(town,owner,sources,bag,(short)5,flask,mix);
        assertEquals(AlchemyBottling.Outcome.FILLED,result.outcome());
        assertEquals(2,AlchemyFlask.charges(bag.getItemStack((short)5)));assertEquals(mix,AlchemyFlask.ingredients(bag.getItemStack((short)5)));
        // The bag is used first, then storage, then what a production building holds.
        assertEquals(0,count(bag,HEALTH));assertEquals(0,count(bag,MANA));
        assertEquals(1,warehouse.count(TownWarehouse.Page.GENERAL,MANA));assertEquals(6,crops.getAmount(HEALTH));
        assertEquals(9,warehouse.count(TownWarehouse.Page.GENERAL,"Ingredient_Fibre"),"Nothing else is touched");
    }
    @Test void aFlaskMovedToAnotherSlotIsFilledWhereItNowSits() {
        var town=town();var bag=inventory();var flask=flask(1);put(bag,0,flask);put(bag,1,stack(HEALTH,1));
        // The page showed it in slot 0. It is in slot 6 when the button is pressed.
        put(bag,0,null);put(bag,6,flask);
        var result=AlchemyBottling.bottle(town,owner,List::of,bag,(short)0,flask,List.of(HEALTH));
        assertEquals(AlchemyBottling.Outcome.FILLED,result.outcome());assertEquals(6,result.flaskSlot());
        assertEquals(1,AlchemyFlask.charges(bag.getItemStack((short)6)));assertEquals(0,count(bag,HEALTH));
    }
    @Test void aFlaskWhoseDetailsChangedInPlaceIsStillTheFlaskToFill() {
        var town=town();var bag=inventory();var shown=new ItemStack(AlchemyFlask.ITEM,1);put(bag,2,shown);put(bag,0,stack(HEALTH,1));
        // The inventory tidy up rewrites a new flask's tooltip after the page drew it, so the stack no longer equals what was shown.
        var refreshed=AlchemyFlask.refreshDisplay(shown);assertNotEquals(shown,refreshed);put(bag,2,refreshed);
        assertEquals(AlchemyBottling.Outcome.FILLED,AlchemyBottling.bottle(town,owner,List::of,bag,(short)2,shown,List.of(HEALTH)).outcome());
        assertEquals(1,AlchemyFlask.charges(bag.getItemStack((short)2)));
    }
    @Test void ingredientsSplitAcrossStacksAndPlacesAreCountedTogether() {
        var town=townWithTwoSlots();var warehouse=town.getWarehouse();var yard=mushroomYard(town);
        var plan=GrowerCatalog.loadFromClasspath(getClass().getClassLoader()).find(TownsfolkWorkType.MYCOLOGY);
        yard.sync(plan,List.of(new WarehouseBatch.Point(0,100,0)),null);
        assertNull(yard.choose(plan,"cap_red",10,yard.revision()));
        for(int i=0;i<40&&yard.stored()==0;i++) {
            var task=yard.next(plan,yard.chosen(plan),bed->true,null);
            if(task==null){yard.advance(plan,plan.stageMinutes(),true,true);continue;}
            for(int tick=0;tick<200&&!yard.work(plan,task.bed(),task.job(),.25,true,1);tick++){}
            yard.complete(plan,yard.chosen(plan),task.bed(),task.job(),false,()->true);
        }
        assertEquals(2,yard.amount(RED_CAP),"The Mycologist holds two Red Caps");
        store(warehouse,stack(RED_CAP,1));assertTrue(warehouse.acceptCompletedOutput(stack(RED_CAP,1)));
        var bag=inventory();var flask=flask(5);put(bag,7,flask);put(bag,0,stack(RED_CAP,1));put(bag,3,stack(RED_CAP,1));put(bag,5,stack(HEALTH,2));put(bag,6,stack(HEALTH,3));
        // Five of each: Red Caps are one and one in the bag, one in storage, one in finished goods and one of the Mycologist's two.
        var result=AlchemyBottling.bottle(town,owner,growers(town),bag,(short)7,flask,List.of(RED_CAP,HEALTH));
        assertEquals(AlchemyBottling.Outcome.FILLED,result.outcome());assertEquals(5,result.charges());
        assertEquals(0,count(bag,RED_CAP));assertEquals(0,count(bag,HEALTH));
        assertEquals(0,warehouse.count(TownWarehouse.Page.GENERAL,RED_CAP));assertEquals(0,warehouse.count(TownWarehouse.Page.OUTPUT,RED_CAP));
        assertEquals(1,yard.amount(RED_CAP));
    }
    @Test void oneIngredientShortNamesItAndHowManyAndSpendsNothing() {
        var town=townWithTwoSlots();var warehouse=town.getWarehouse();store(warehouse,stack(MANA,1));
        var bag=inventory();var flask=flask(3);put(bag,0,flask);put(bag,1,stack(HEALTH,3));put(bag,2,stack(MANA,1));
        var result=AlchemyBottling.bottle(town,owner,List::of,bag,(short)0,flask,List.of(HEALTH,MANA));
        assertEquals(AlchemyBottling.Outcome.MISSING,result.outcome());
        assertEquals(List.of(new AlchemyBottling.Missing(MANA,1)),result.missing(),"Three are needed, one is carried and one is stored");
        assertEquals(flask,bag.getItemStack((short)0));assertEquals(3,count(bag,HEALTH));assertEquals(1,count(bag,MANA));
        assertEquals(1,warehouse.count(TownWarehouse.Page.GENERAL,MANA));
        assertFalse(town.getAlchemyLaboratory().discovered(HEALTH));
        assertEquals(1,CoreVillagerMasteryService.level(town,"alchemist"));
        // Bringing the one that was missing is all it takes.
        put(bag,3,stack(MANA,1));
        assertTrue(AlchemyBottling.bottle(town,owner,List::of,bag,(short)0,flask,List.of(HEALTH,MANA)).filled());
    }
    @Test void suppliesPromisedToACourierAreNotTakenAndAreReportedAsMissing() {
        var town=town();var warehouse=town.getWarehouse();store(warehouse,stack(HEALTH,5));
        assertNotNull(warehouse.reserve(UUID.randomUUID(),UUID.randomUUID(),"recipe",List.of(new MaterialQuantity(HEALTH,null,null,4,null)),List.of(stack("Potion_Health_Lesser",1))));
        var bag=inventory();var flask=flask(3);put(bag,0,flask);put(bag,1,stack(HEALTH,1));
        var result=AlchemyBottling.bottle(town,owner,List::of,bag,(short)0,flask,List.of(HEALTH));
        assertEquals(List.of(new AlchemyBottling.Missing(HEALTH,1)),result.missing(),"One carried and one unreserved in storage leave one short");
        assertEquals(5,warehouse.count(TownWarehouse.Page.GENERAL,HEALTH));assertEquals(1,count(bag,HEALTH));
    }
    @Test void aPlayerWhoMayNotOpenTownStoresFillsFromTheirOwnInventoryOnly() {
        var town=town();store(town.getWarehouse(),stack(HEALTH,5));var visitor=UUID.randomUUID();
        var bag=inventory();var flask=flask(2);put(bag,0,flask);put(bag,1,stack(HEALTH,1));
        var result=AlchemyBottling.bottle(town,visitor,List::of,bag,(short)0,flask,List.of(HEALTH));
        assertEquals(List.of(new AlchemyBottling.Missing(HEALTH,1)),result.missing());
        put(bag,2,stack(HEALTH,1));
        assertTrue(AlchemyBottling.bottle(town,visitor,List::of,bag,(short)0,flask,List.of(HEALTH)).filled());
        assertEquals(5,town.getWarehouse().count(TownWarehouse.Page.GENERAL,HEALTH));
    }
    @Test void aFilledFlaskTeachesTheTownAndOrinAndCannotBeFilledTwice() {
        var town=town();var bag=inventory();var flask=flask(2);put(bag,0,flask);put(bag,1,stack(HEALTH,4));
        var first=AlchemyBottling.bottle(town,owner,List::of,bag,(short)0,flask,List.of(HEALTH));
        assertTrue(first.filled());assertTrue(town.getAlchemyLaboratory().discovered(HEALTH));
        long earned=CoreVillagerMasteryService.progress(town,"alchemist").xpInLevel();assertEquals(AlchemyFlask.bottlingXp(1,2),earned);
        // A second press of the same button, with the page still showing the empty flask.
        var again=AlchemyBottling.bottle(town,owner,List::of,bag,(short)0,flask,List.of(HEALTH));
        assertEquals(AlchemyBottling.Outcome.FLASK_HOLDS_POTION,again.outcome());
        assertEquals(2,count(bag,HEALTH));assertEquals(earned,CoreVillagerMasteryService.progress(town,"alchemist").xpInLevel());
    }
    @Test void noFlaskAndMixturesBeyondTheLaboratorysSlotsAreRefusedWithoutSpending() {
        var town=town();var bag=inventory();put(bag,1,stack(HEALTH,4));put(bag,2,stack(MANA,4));
        assertEquals(AlchemyBottling.Outcome.NO_FLASK,AlchemyBottling.bottle(town,owner,List::of,bag,(short)0,null,List.of(HEALTH)).outcome());
        put(bag,0,flask(1));
        assertEquals(1,town.getAlchemyLaboratory().ingredientSlots());
        assertEquals(AlchemyBottling.Outcome.INVALID_MIXTURE,AlchemyBottling.bottle(town,owner,List::of,bag,(short)0,bag.getItemStack((short)0),List.of(HEALTH,MANA)).outcome());
        assertEquals(4,count(bag,HEALTH));assertEquals(4,count(bag,MANA));
    }
    @Test void aFlaskFromAnOlderSaveFillsLikeAnyOther() {
        var town=town();var bag=inventory();
        // Crafted at the Town Planning Desk before the cauldron made flasks: no flask data at all.
        var plain=new ItemStack(AlchemyFlask.ITEM,1);put(bag,0,plain);put(bag,1,stack(HEALTH,2));
        assertFalse(AlchemyFlask.initialized(plain));
        assertTrue(AlchemyBottling.bottle(town,owner,List::of,bag,(short)0,plain,List.of(HEALTH)).filled());
        assertEquals(1,AlchemyFlask.charges(bag.getItemStack((short)0)));
        // Upgraded and half drunk: capacity and the potion inside are kept, and it is not refilled over.
        var used=AlchemyFlask.consume(AlchemyFlask.fill(flask(3),List.of(MANA)));put(bag,3,used);
        assertEquals(3,AlchemyFlask.capacity(bag.getItemStack((short)3)));assertEquals(2,AlchemyFlask.charges(bag.getItemStack((short)3)));
        assertEquals(AlchemyBottling.Outcome.FLASK_HOLDS_POTION,AlchemyBottling.bottle(town,owner,List::of,bag,(short)3,used,List.of(HEALTH)).outcome());
        assertEquals(used,bag.getItemStack((short)3));
    }
}
