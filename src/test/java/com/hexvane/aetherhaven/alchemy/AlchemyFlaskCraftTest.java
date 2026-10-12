package com.hexvane.aetherhaven.alchemy;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.warehouse.TownWarehouse;
import com.hypixel.hytale.assetstore.*;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.event.*;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.MaterialQuantity;
import com.hypixel.hytale.server.core.inventory.container.*;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.*;

/**
 * The owner's request: the flask "should just be obtainable immediately upon building the laboratory" and "crafted at
 * the laboratory like how backpacks are crafted at the leatherworker". No recipe is learned and no bench makes it.
 */
@Tag("town")
class AlchemyFlaskCraftTest {
    private static final String IRON="Ingredient_Bar_Iron",LEATHER="Ingredient_Leather_Light",SAP="Ingredient_Tree_Sap";
    private static final Path RESOURCES=Path.of("src/main/resources"),JAVA=Path.of("src/main/java/com/hexvane/aetherhaven");
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
    private static final Map<String,Integer> COST=AlchemyFlaskCraft.load(AlchemyFlaskCraftTest.class.getClassLoader());
    private static ItemStack stack(String item,int count){return new ItemStack(item,count);}
    private static void put(ItemContainer bag,int slot,ItemStack stack){bag.setItemStackForSlot((short)slot,stack,false);}
    private static int count(ItemContainer bag,String item){return bag.countItemStacks(s->item.equals(s.getItemId()));}
    private static short flaskSlot(ItemContainer bag){return AlchemyBottling.locate(bag,(short)-1,null);}

    @Test void theCostIsReadFromTheBundledDataFileInItsOrder() {
        assertEquals(List.of(IRON,LEATHER,SAP),List.copyOf(COST.keySet()));
        assertEquals(List.of(5,5,5),List.copyOf(COST.values()));
        assertEquals(Map.of("a",3),AlchemyFlaskCraft.parse(new StringReader("{\"CraftCost\":[{\"ItemId\":\"a\",\"Quantity\":1},{\"ItemId\":\"a\",\"Quantity\":2}]}")));
        // A typo must never make the flask free.
        for(String broken:List.of("{}","{\"CraftCost\":[]}","{\"CraftCost\":[{\"ItemId\":\"a\",\"Quantity\":0}]}","{\"CraftCost\":[{\"Quantity\":2}]}",
            "{\"CraftCost\":[{\"ItemId\":\"a\",\"Quantity\":1},{\"ItemId\":\"\",\"Quantity\":1}]}")) {
            var cost=AlchemyFlaskCraft.parse(new StringReader(broken));assertTrue(cost.isEmpty(),broken);
            assertEquals(AlchemyFlaskCraft.Outcome.REFUSED,AlchemyFlaskCraft.craft(new SimpleItemContainer((short)2),null,cost).outcome(),broken);
        }
    }
    @Test void aNewFlaskIsMadeFromCarriedMaterialsAndIsEmptyWithOneCharge() {
        var bag=new CombinedItemContainer(new SimpleItemContainer((short)3),new SimpleItemContainer((short)3));
        put(bag,0,stack(IRON,3));put(bag,4,stack(IRON,4));put(bag,1,stack(LEATHER,5));put(bag,2,stack(SAP,6));
        var result=AlchemyFlaskCraft.craft(bag,null,COST);
        assertEquals(AlchemyFlaskCraft.Outcome.CRAFTED,result.outcome());
        assertEquals(2,count(bag,IRON));assertEquals(0,count(bag,LEATHER));assertEquals(1,count(bag,SAP));
        short slot=flaskSlot(bag);assertTrue(slot>=0);var flask=bag.getItemStack(slot);
        assertTrue(AlchemyFlask.initialized(flask));assertFalse(AlchemyFlask.needsDisplayRefresh(flask));
        assertEquals(1,AlchemyFlask.capacity(flask));assertEquals(0,AlchemyFlask.charges(flask));
        // Straight to the cauldron: the flask just made can be filled and upgraded like any other.
        put(bag,5,stack("Plant_Crop_Health1",1));
        assertTrue(AlchemyBottling.fill(bag,slot,flask,List.of("Plant_Crop_Health1"),null).filled());
        assertEquals(2,AlchemyFlask.capacity(AlchemyFlask.upgrade(bag.getItemStack(slot))));
    }
    @Test void townStorageCoversWhatThePlayerDoesNotCarryButNeverACouriersSupplies() {
        var warehouse=new TownWarehouse();
        assertTrue(warehouse.acceptGuardSpoils(stack(IRON,3)));assertTrue(warehouse.acceptGuardSpoils(stack(LEATHER,5)));assertTrue(warehouse.acceptCompletedOutput(stack(SAP,5)));
        var batch=warehouse.reserve(UUID.randomUUID(),UUID.randomUUID(),"recipe",List.of(new MaterialQuantity(LEATHER,null,null,2,null)),List.of(stack("kit",1)));
        assertNotNull(batch);
        var bag=new SimpleItemContainer((short)3);put(bag,0,stack(IRON,2));
        var held=AlchemyFlaskCraft.craft(bag,warehouse,COST);
        assertEquals(AlchemyFlaskCraft.Outcome.MISSING,held.outcome());
        assertEquals(List.of(new AlchemyBottling.Missing(LEATHER,2)),held.missing(),"Two of the five leathers are promised to a workplace");
        assertEquals(2,count(bag,IRON));assertEquals(3,warehouse.count(TownWarehouse.Page.GENERAL,IRON));assertEquals(5,warehouse.count(TownWarehouse.Page.OUTPUT,SAP));
        put(bag,1,stack(LEATHER,2));
        assertEquals(AlchemyFlaskCraft.Outcome.CRAFTED,AlchemyFlaskCraft.craft(bag,warehouse,COST).outcome());
        assertEquals(0,count(bag,IRON));assertEquals(0,count(bag,LEATHER));assertTrue(flaskSlot(bag)>=0);
        assertEquals(0,warehouse.count(TownWarehouse.Page.GENERAL,IRON));assertEquals(0,warehouse.count(TownWarehouse.Page.OUTPUT,SAP));
        assertEquals(2,warehouse.count(TownWarehouse.Page.GENERAL,LEATHER),"The courier's two are still there");
        assertEquals(2,warehouse.pickup(batch.id(),"courier",1));
    }
    @Test void everyMissingMaterialIsNamedWithItsCountAndNothingIsSpent() {
        var bag=new SimpleItemContainer((short)4);put(bag,0,stack(IRON,5));put(bag,1,stack(SAP,1));
        var result=AlchemyFlaskCraft.craft(bag,null,COST);
        assertEquals(AlchemyFlaskCraft.Outcome.MISSING,result.outcome());
        assertEquals(List.of(new AlchemyBottling.Missing(LEATHER,5),new AlchemyBottling.Missing(SAP,4)),result.missing());
        assertEquals(5,count(bag,IRON));assertEquals(1,count(bag,SAP));assertEquals(-1,flaskSlot(bag));
    }
    @Test void aFullInventoryIsToldSoBeforeAnythingIsSpent() {
        var bag=new SimpleItemContainer((short)4);put(bag,0,stack(IRON,6));put(bag,1,stack(LEATHER,6));put(bag,2,stack(SAP,6));put(bag,3,stack("Rock_Stone",1));
        assertEquals(AlchemyFlaskCraft.Outcome.NO_ROOM,AlchemyFlaskCraft.craft(bag,null,COST).outcome());
        assertEquals(6,count(bag,IRON));assertEquals(6,count(bag,LEATHER));assertEquals(6,count(bag,SAP));
        put(bag,3,null);
        assertEquals(AlchemyFlaskCraft.Outcome.CRAFTED,AlchemyFlaskCraft.craft(bag,null,COST).outcome());
        assertEquals(3,flaskSlot(bag));
    }
    @Test void noBenchRecipeAndNoDialogueStepIsLeftBetweenThePlayerAndTheFlask() throws Exception {
        var item=JsonParser.parseString(Files.readString(RESOURCES.resolve("Server/Item/Items/Aetherhaven/Aetherhaven_Alchemy_Flask.json"))).getAsJsonObject();
        assertFalse(item.has("Recipe"),"The flask is made at the cauldron, not at a bench that needs a learned recipe");
        assertEquals("aetherhaven_alchemy.flask.name",item.getAsJsonObject("TranslationProperties").get("Name").getAsString());
        for(String file:List.of("ui/DialoguePage.java","dialogue/DialogueActionExecutor.java","alchemy/AlchemyCauldronPage.java")) {
            String source=Files.readString(JAVA.resolve(file));
            assertFalse(source.contains("learn_alchemy_flask"),file);assertFalse(source.contains("alchemy_flask_recipe"),file);
        }
        assertFalse(Files.readString(JAVA.resolve("alchemy/AlchemyCauldronPage.java")).contains("learnRecipe"),"The cauldron no longer teaches a recipe");
        String page=Files.readString(RESOURCES.resolve("Common/UI/Custom/Aetherhaven/AlchemyCauldronPage.ui"));
        assertTrue(page.contains("#Craft "));assertTrue(page.contains("#CraftCost "));assertFalse(page.contains("#Learn"));
        assertFalse(Files.readString(RESOURCES.resolve("Server/Languages/en-US/aetherhaven_mastery.lang")).contains("flaskRecipe"));
        String lang=Files.readString(RESOURCES.resolve("Server/Languages/en-US/aetherhaven_alchemy.lang"));
        String cauldron=Files.readString(JAVA.resolve("alchemy/AlchemyCauldronPage.java"));
        // The cauldron's lines in the lang file and the keys the page can build are the same set: none missing, none dead.
        var defined=new TreeSet<String>();
        for(String line:lang.split("\\R"))if(line.startsWith("aetherhaven.cauldron."))defined.add(line.substring("aetherhaven.cauldron.".length(),line.indexOf(" = ")));
        var literals=new TreeSet<String>();
        var matcher=java.util.regex.Pattern.compile("\"([A-Za-z]+(?:\\.[A-Za-z]+)?)\"").matcher(cauldron);
        while(matcher.find())literals.add(matcher.group(1));
        var built=new TreeSet<String>();
        var stems=new TreeSet<String>();
        for(String stem:List.of("brew.title","quality","fillNote"))for(String quality:List.of("Good","Great","Perfect")){stems.add(stem);built.add(stem+quality);}
        for(String stem:List.of("brew.move","brew.pip"))for(String button:List.of("Stir","Wait","Stoke")){stems.add(stem);built.add(stem+button);}
        for(String stem:stems)assertTrue(literals.contains(stem),"The page no longer builds keys from "+stem);
        for(String literal:literals) {
            if(defined.contains(literal))built.add(literal);
            else if(!stems.contains(literal)&&literal.matches("(brew\\.[A-Za-z]+|hint[A-Z][A-Za-z]*|fill[A-Z][A-Za-z]*|craft[A-Z][A-Za-z]*|noFlask|filled|crafted)"))
                fail("The page sends "+literal+" but the lang file has no such line");
        }
        assertEquals(defined,built);
        assertTrue(defined.size()>=40);
        for(String quality:List.of("great","perfect"))assertTrue(lang.contains("aetherhaven.flask.quality."+quality+" = "),quality);
        for(String line:lang.split("\\R")) {
            if(line.startsWith("aetherhaven.cauldron.fillMissing")||line.startsWith("aetherhaven.cauldron.craftMissing"))assertTrue(line.contains("{list}"),line);
            if(line.startsWith("aetherhaven.cauldron.craftCost"))assertTrue(line.contains("{materials}"),line);
            assertFalse(line.contains(" - ")||line.contains("—")||line.contains("–"),line);
        }
        String guide=Files.readString(RESOURCES.resolve("Server/Aetherhaven/GuideTopics/en-US/villager_alchemist.md"));
        assertFalse(guide.contains("Town Planning Desk"),"The guide must not send the player to the planning desk for a flask");
        assertFalse(guide.contains("teach you the"),"The guide must not send the player to Orin for a recipe");
        assertTrue(guide.contains("Craft flask"));
        // The guide quotes the cost in words. If the data file changes, this is the reminder to reword it.
        assertEquals(Map.of(IRON,5,LEATHER,5,SAP,5),COST);
        assertTrue(guide.contains("5 Iron Ingots, 5 Light Leather and 5 Tree Sap"));
    }
}
