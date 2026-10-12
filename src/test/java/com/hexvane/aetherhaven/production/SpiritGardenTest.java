package com.hexvane.aetherhaven.production;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.production.GardenUpgrades.Track;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import com.hexvane.aetherhaven.workplace.AlchemicalCrop;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.hexvane.aetherhaven.production.SpiritGardenState.*;

@Tag("town")
class SpiritGardenTest {
    private static final String ROSE="Plant_Crop_Health1",PETAL="Plant_Petals_Blood";
    private static final Point BLOCK=new Point(4,100,7);
    private static final long REST=4*3600;
    private final Gson gson=new Gson();

    private static PlotProductionState conservatory() {
        var production=new PlotProductionState();assertTrue(GardenUpgrades.mapLegacy(production));return production;
    }
    private static ProductionCatalog.Entry catalog() {
        return ProductionCatalog.loadFromClasspath(SpiritGardenTest.class.getClassLoader()).get(SpiritGarden.CONSTRUCTION);
    }
    /** Binds planter 0, lets Rowan plant it and perform every rite, each after a full rest. Returns the game second it ripened. */
    private static long ripen(SpiritGardenState garden,String plant) {
        if(!garden.planters().containsKey(0))assertNull(garden.bind(0,2,garden.revision(),BLOCK,plant));
        var id=garden.planters().get(0).id();long now=1000;
        if(garden.planters().get(0).stage()==WAITING)assertTrue(garden.plant(0,id,now,REST,()->true));
        while(!garden.planters().get(0).ripe()){now=garden.planters().get(0).restUntil();assertTrue(garden.rite(0,id,now,REST,stage->true));}
        return now;
    }

    @Test void choosingASeedIsTheOnlyStepNeededToStartTheGarden() {
        var garden=new SpiritGardenState();assertFalse(garden.adopted());assertEquals(0,garden.freeSlot(2));
        assertEquals(-1,garden.planterAt(BLOCK));
        assertNull(garden.bind(0,2,0,BLOCK,ROSE));
        assertTrue(garden.adopted());assertEquals(0,garden.planterAt(BLOCK));
        var planter=garden.planters().get(0);
        assertEquals(WAITING,planter.stage());assertEquals(new Point(4,101,7),planter.cell());
        assertEquals(1,garden.freeSlot(2));assertEquals(-1,garden.freeSlot(1));
        assertEquals("changed",garden.bind(1,2,0,new Point(9,100,9),ROSE),"A stale screen cannot bind");
        assertEquals("taken",garden.bind(1,2,garden.revision(),BLOCK,ROSE),"One block is one planter");
        assertEquals("taken",garden.bind(0,2,garden.revision(),new Point(9,100,9),ROSE));
        assertEquals("full",garden.bind(2,2,garden.revision(),new Point(9,100,9),ROSE),"A planter beyond the allowance stays asleep");
        assertEquals("full",garden.bind(1,MAX_PLANTERS+1,garden.revision(),new Point(9,100,9),ROSE));
        assertEquals(1,garden.planters().size());
    }

    /**
     * The game's clock starts on the first day of year 1, which Java counts as about sixty two billion seconds
     * below zero. Every real world hands the garden such a time, so planting, rites and harvests must accept it.
     */
    @Test void theGardenRunsOnTheGamesOwnClockWhichStartsFarBelowZero() {
        long dawn=java.time.Instant.parse("0001-01-01T05:30:00Z").getEpochSecond();assertTrue(dawn<0);
        var production=conservatory();var garden=production.garden();
        assertNull(garden.bind(0,2,0,BLOCK,ROSE));var id=garden.planters().get(0).id();
        assertTrue(garden.plant(0,id,dawn,REST,()->true),"Rowan could not plant on the first morning of a new world");
        assertEquals(dawn+REST,garden.planters().get(0).restUntil());
        assertFalse(garden.rested(0,dawn+REST-1,REST));assertEquals(0.5/RIPE,garden.growth(0,dawn+REST/2,REST),1e-9);
        long now=dawn;
        for(int rite=1;rite<=RIPE;rite++) {
            now+=REST;assertFalse(garden.rite(0,id,now-1,REST,stage->fail("Still resting")));
            assertTrue(garden.rite(0,id,now,REST,stage->true),"Rite "+rite+" was refused");
        }
        assertTrue(now<0,"Three rites fit long before the clock reaches zero");assertEquals(1.0,garden.growth(0,now,REST),1e-9);
        assertTrue(garden.harvest(0,id,Map.of(ROSE,2),now,REST,()->true),"The harvest was refused");
        assertEquals(now+REST,garden.planters().get(0).restUntil());assertEquals(2,garden.carried());
        production=gson.fromJson(gson.toJson(production),PlotProductionState.class);
        assertEquals(now+REST,production.garden().planters().get(0).restUntil(),"The time of the next rite survives a save");
        assertTrue(production.garden().rested(0,now+REST,REST));
        // Only a rest that makes no sense is refused.
        var other=new SpiritGardenState();assertNull(other.bind(0,2,0,BLOCK,ROSE));
        assertFalse(other.plant(0,other.planters().get(0).id(),dawn,-1,()->fail("A negative rest is no rest")));
    }

    @Test void threeRitesRipenAPlantAndTheClockAloneNeverDoes() {
        var garden=new SpiritGardenState();assertNull(garden.bind(0,2,0,BLOCK,ROSE));
        var id=garden.planters().get(0).id();
        assertFalse(garden.rite(0,id,5000,REST,stage->fail("Nothing is planted yet")));
        assertFalse(garden.plant(0,id,1000,REST,()->false));assertEquals(WAITING,garden.planters().get(0).stage());
        assertFalse(garden.plant(0,UUID.randomUUID(),1000,REST,()->fail("A stale visit plants nothing")));
        assertTrue(garden.plant(0,id,1000,REST,()->true));
        assertFalse(garden.plant(0,id,1000,REST,()->fail("Planted once")));
        assertEquals(0,garden.planters().get(0).stage());assertEquals(0.0,garden.growth(0,1000,REST),1e-9);
        assertFalse(garden.rested(0,1000+REST-1,REST));
        assertFalse(garden.rite(0,id,1000+REST-1,REST,stage->fail("The plant is still resting")));
        // A week on the clock with no Druid at the planter changes nothing but its readiness.
        long later=1000+7*86400;
        assertEquals(0,garden.planters().get(0).stage());assertTrue(garden.rested(0,later,REST));
        assertEquals(1.0/3,garden.growth(0,later,REST),1e-9);
        assertFalse(garden.rite(0,id,later,REST,stage->false));assertEquals(0,garden.planters().get(0).stage(),"A plant that could not be shown does not grow");
        var shown=new ArrayList<Integer>();long now=later;
        for(int rite=1;rite<=RIPE;rite++) {
            assertTrue(garden.rite(0,id,now,REST,shown::add));
            assertEquals(rite,garden.planters().get(0).stage());
            assertFalse(garden.rite(0,id,now,REST,stage->fail("One rite, one stage")));
            if(rite<RIPE)assertEquals((rite+0.5)/RIPE,garden.growth(0,now+REST/2,REST),1e-9,"The bar fills while the plant rests");
            now+=REST;
        }
        assertEquals(List.of(1,2,3),shown);assertTrue(garden.planters().get(0).ripe());assertEquals(1.0,garden.growth(0,now,REST),1e-9);
        assertFalse(garden.rested(0,now,REST),"A ripe plant needs a harvest, not another rite");
        // A stronger rite or a clock set back never leaves a plant waiting longer than one rest.
        var other=new SpiritGardenState();assertNull(other.bind(0,2,0,BLOCK,ROSE));var oid=other.planters().get(0).id();
        assertTrue(other.plant(0,oid,1000,REST,()->true));
        assertTrue(other.rested(0,1000,REST/2));assertTrue(other.rested(0,10,REST));
    }

    @Test void everyPlantRegrowsForFreeAndItsHarvestReachesTheStoresExactlyOnceAcrossReload() {
        var entry=catalog();
        for(var plant:SpiritGardenRules.get().plants()) {
            var production=conservatory();var garden=production.garden();
            long ripe=ripen(garden,plant.plant());var id=garden.planters().get(0).id();
            var yields=new LinkedHashMap<String,Integer>();yields.put(plant.plant(),2);yields.put(plant.petal(),6);
            assertFalse(garden.harvest(0,id,yields,ripe,REST,()->false));assertTrue(garden.planters().get(0).ripe());
            assertFalse(garden.harvest(0,UUID.randomUUID(),yields,ripe,REST,()->fail("A stale visit harvests nothing")));
            assertFalse(garden.harvest(0,id,Map.of(),ripe,REST,()->fail("An empty harvest is no harvest")));
            assertTrue(garden.harvest(0,id,yields,ripe,REST,()->true));
            assertEquals(0,production.getAmount(plant.plant()),"Nothing is stock until Rowan puts it away");
            assertEquals(8,garden.carried());assertEquals(0,garden.planters().get(0).stage(),"The plant starts again without a new seed");
            assertFalse(garden.harvest(0,id,yields,ripe,REST,()->fail("A sprout cannot be harvested")));
            production=gson.fromJson(gson.toJson(production),PlotProductionState.class);garden=production.garden();
            assertEquals(8,garden.carried());assertTrue(production.usesSpiritGarden());
            var saved=production;
            var delivered=garden.deliverAll(production,item->GardenUpgrades.capacity(saved,entry,item));
            assertEquals(Map.of(plant.plant(),2,plant.petal(),6),delivered);
            assertTrue(garden.deliverAll(production,item->1000).isEmpty(),"A harvest is delivered once");
            production=gson.fromJson(gson.toJson(production),PlotProductionState.class);
            assertTrue(production.garden().deliverAll(production,item->1000).isEmpty());
            assertEquals(2,production.getAmount(plant.plant()));assertEquals(6,production.getAmount(plant.petal()));
            assertEquals(id,production.garden().planters().get(0).id());
            // The same planter ripens and gives again with no seed and no player.
            long again=ripen(production.garden(),plant.plant());
            assertTrue(production.garden().harvest(0,id,yields,again,REST,()->true));
            assertEquals(8,production.garden().carried());
        }
    }

    @Test void oneFullStoreNeverHoldsUpTheOthers() {
        var production=conservatory();var garden=production.garden();var entry=catalog();
        long ripe=ripen(garden,ROSE);var id=garden.planters().get(0).id();
        production.addAmount(ROSE,24,25);production.addAmount(PETAL,50,50);
        assertEquals(25,GardenUpgrades.capacity(production,entry,ROSE));assertEquals(50,GardenUpgrades.capacity(production,entry,PETAL));
        var yields=new LinkedHashMap<String,Integer>();yields.put(ROSE,2);yields.put(PETAL,6);
        assertTrue(garden.harvest(0,id,yields,ripe,REST,()->true));
        assertEquals(Map.of(ROSE,1),garden.deliverAll(production,item->GardenUpgrades.capacity(production,entry,item)));
        assertEquals(25,production.getAmount(ROSE));assertEquals(50,production.getAmount(PETAL));
        assertFalse(garden.carrying(),"What does not fit is left behind so Rowan can carry on");
        long again=ripen(garden,ROSE);
        assertTrue(garden.harvest(0,id,yields,again,REST,()->true),"A full store does not stop the next harvest");
    }

    @Test void aBlessingDoublesOneHarvestOnly() {
        var production=conservatory();var garden=production.garden();
        assertEquals("empty",garden.blessingIssue(0));assertFalse(garden.bless(0));
        assertNull(garden.bind(0,2,0,BLOCK,ROSE));
        assertNull(garden.blessingIssue(0));assertTrue(garden.bless(0));assertTrue(garden.blessed(0));
        assertEquals("blessed",garden.blessingIssue(0));assertFalse(garden.bless(0),"A second blessing would only waste Life Essence");
        production=gson.fromJson(gson.toJson(production),PlotProductionState.class);garden=production.garden();assertTrue(garden.blessed(0));
        long ripe=ripen(garden,ROSE);var id=garden.planters().get(0).id();
        var yields=new LinkedHashMap<String,Integer>();yields.put(ROSE,2);yields.put(PETAL,6);
        assertFalse(garden.harvest(0,id,yields,ripe,REST,()->false));assertTrue(garden.blessed(0),"A failed harvest keeps the blessing");
        assertTrue(garden.harvest(0,id,yields,ripe,REST,()->true));
        assertEquals(List.of(new Cargo(ROSE,4),new Cargo(PETAL,12)),garden.cargo());assertFalse(garden.blessed(0));
        garden.deliverAll(production,item->1000);
        long again=ripen(garden,ROSE);
        assertTrue(garden.harvest(0,id,yields,again,REST,()->true));assertEquals(8,garden.carried());
    }

    @Test void uprootingOrBreakingAPlanterForgetsItsPlant() {
        var production=conservatory();var garden=production.garden();
        long ripe=ripen(garden,ROSE);var id=garden.planters().get(0).id();long revision=garden.revision();
        assertTrue(garden.bless(0));
        assertTrue(garden.uproot(0));assertTrue(garden.planters().isEmpty());assertFalse(garden.blessed(0));
        assertTrue(garden.revision()>revision);assertEquals(-1,garden.planterAt(BLOCK));assertEquals(0,garden.freeSlot(2));
        assertFalse(garden.harvest(0,id,Map.of(ROSE,2),ripe,REST,()->fail("An uprooted plant gives nothing")));
        assertFalse(garden.uproot(0));assertTrue(garden.adopted(),"The Conservatory stays a garden");
        assertNull(garden.bind(0,2,garden.revision(),BLOCK,ROSE));
        assertFalse(production.uprootPlanterAt(new Point(80,100,80)));
        assertFalse(production.uprootPlanterAt(garden.planters().get(0).cell()),"Only the planter itself counts, not the plant above it");
        assertTrue(production.uprootPlanterAt(BLOCK));
        var restored=gson.fromJson(gson.toJson(production),PlotProductionState.class);
        assertTrue(restored.garden().planters().isEmpty());assertTrue(restored.usesSpiritGarden());
        assertFalse(new PlotProductionState().uprootPlanterAt(BLOCK));
    }

    @Test void aPlantThatVanishedIsPlantedAgainForFree() {
        var garden=new SpiritGardenState();ripen(garden,ROSE);var id=garden.planters().get(0).id();assertTrue(garden.bless(0));
        assertFalse(garden.lost(0,UUID.randomUUID()));assertFalse(garden.lost(1,id));
        assertTrue(garden.lost(0,id));assertEquals(WAITING,garden.planters().get(0).stage());assertTrue(garden.blessed(0));
        assertFalse(garden.lost(0,id),"There is nothing left to lose");
        assertEquals(ROSE,garden.planters().get(0).plant());assertEquals(0,garden.growth(0,5000,REST));
        assertTrue(garden.plant(0,id,5000,REST,()->true));assertFalse(garden.carrying());
    }

    @Test void gardenUpgradesArePaidInFullOrNotAtAll() {
        var production=conservatory();var entry=catalog();
        assertEquals(2,GardenUpgrades.planters(production));assertEquals(REST,GardenUpgrades.restSeconds(production));
        assertEquals(2,GardenUpgrades.plantsPerHarvest(production));assertEquals(6,GardenUpgrades.petalRolls(production));
        assertEquals(25,GardenUpgrades.capacity(production,entry,ROSE));
        var pack=new HashMap<String,Integer>();
        var purse=new GardenUpgrades.Purse() {
            public long available(String item){return pack.getOrDefault(item,0);}
            public boolean take(Map<String,Integer> cost){cost.forEach((item,count)->pack.merge(item,-count,Integer::sum));return true;}
        };
        assertEquals("order",GardenUpgrades.purchase(production,Track.PLANTERS,2,1,purse));
        assertEquals("short",GardenUpgrades.purchase(production,Track.PLANTERS,1,1,purse));
        pack.put("Ingredient_Life_Essence",10);pack.put(ROSE,4);
        assertEquals("short",GardenUpgrades.purchase(production,Track.PLANTERS,1,1,purse));
        assertEquals(10,pack.get("Ingredient_Life_Essence"),"A purchase that fails costs nothing");
        pack.put(ROSE,5);
        assertNull(GardenUpgrades.purchase(production,Track.PLANTERS,1,1,purse));
        assertEquals(0,pack.get("Ingredient_Life_Essence"));assertEquals(0,pack.get(ROSE));
        assertEquals(3,GardenUpgrades.planters(production));assertEquals("order",GardenUpgrades.purchase(production,Track.PLANTERS,1,1,purse));
        // Every level of every track can be bought in order, costs are five based and never fall.
        var rich=new GardenUpgrades.Purse() {
            public long available(String item){return 1_000_000;}
            public boolean take(Map<String,Integer> cost){return true;}
        };
        for(var track:Track.values()) {
            int before=0;assertTrue(GardenUpgrades.maxLevel(track)>=3&&GardenUpgrades.maxLevel(track)<=4,track+" has three or four levels");
            for(int level=GardenUpgrades.level(production,track)+1;level<=GardenUpgrades.maxLevel(track);level++)
                assertNull(GardenUpgrades.purchase(production,track,level,1,rich));
            for(int level=1;level<=GardenUpgrades.maxLevel(track);level++) {
                var cost=GardenUpgrades.cost(track,level,1);
                assertTrue(cost.containsKey("Ingredient_Life_Essence"),track+" is paid with Life Essence");
                assertTrue(cost.size()>=2,track+" is also paid with what the garden grows");
                int total=0;for(var part:cost.entrySet()) {
                    assertEquals(0,part.getValue()%5,track+" "+level+" "+part.getKey());total+=part.getValue();
                    assertTrue("Ingredient_Life_Essence".equals(part.getKey())||entry.containsItemId(part.getKey()),part.getKey()+" is something Rowan grows");
                }
                assertTrue(total>=before,track+" costs rise");before=total;
            }
            assertEquals("maxed",GardenUpgrades.purchase(production,track,GardenUpgrades.maxLevel(track)+1,1,rich));
        }
        assertEquals(MAX_PLANTERS,GardenUpgrades.planters(production));assertEquals(2*3600,GardenUpgrades.restSeconds(production));
        assertEquals(5,GardenUpgrades.plantsPerHarvest(production));assertEquals(15,GardenUpgrades.petalRolls(production));
        assertEquals(100,GardenUpgrades.capacity(production,entry,ROSE));assertEquals(200,GardenUpgrades.capacity(production,entry,PETAL));
        assertEquals(100,WorkplaceProductionUpgrades.effectiveMaxStorage(production,entry,ROSE),"The stores track is the Conservatory's only capacity rule");
        var saved=gson.fromJson(gson.toJson(production),PlotProductionState.class);
        assertEquals(MAX_PLANTERS,GardenUpgrades.planters(saved));assertEquals(3,GardenUpgrades.level(saved,Track.RITE));
        assertEquals(Map.of("Ingredient_Life_Essence",20,ROSE,10),GardenUpgrades.cost(Track.PLANTERS,1,2),"Costs follow the town's upgrade cost setting");
    }

    /** Rowan's working day from his schedule: 6:00 to 16:30 and 18:00 to 20:00. He visits a planter whenever it has something for him. */
    private static double harvestsPerDay(PlotProductionState production,int days) {
        var garden=production.garden();long rest=GardenUpgrades.restSeconds(production);
        assertNull(garden.bind(0,2,garden.revision(),BLOCK,ROSE));var id=garden.planters().get(0).id();
        long start=6*3600;assertTrue(garden.plant(0,id,start,rest,()->true));int harvests=0;
        for(long now=start;now<(days+1)*86400L;now+=300) {
            long time=now%86400;
            if(!(time>=6*3600&&time<16*3600+1800||time>=18*3600&&time<20*3600))continue;
            if(garden.planters().get(0).ripe()) {
                assertTrue(garden.harvest(0,id,Map.of(ROSE,2),now,rest,()->true));garden.deliverAll(production,item->1000);
                if(now>=86400)harvests++;
            }else if(garden.rested(0,now,rest))assertTrue(garden.rite(0,id,now,rest,stage->true));
        }
        return harvests/(double)days;
    }
    @Test void aPlanterGivesAboutOneHarvestADayAndTheStrongestRiteAboutTwo() {
        double base=harvestsPerDay(conservatory(),20);
        assertTrue(base>=1.0&&base<=1.5,"Base pace was "+base+" harvests a day");
        var strong=conservatory();strong.garden().setLevel(Track.RITE.name(),GardenUpgrades.maxLevel(Track.RITE));
        double best=harvestsPerDay(strong,20);
        assertTrue(best>=1.8&&best<=2.6,"Strongest rite pace was "+best+" harvests a day");
        double before=base;
        for(int level=1;level<=GardenUpgrades.maxLevel(Track.RITE);level++) {
            var production=conservatory();production.garden().setLevel(Track.RITE.name(),level);
            double pace=harvestsPerDay(production,20);assertTrue(pace>before,"Every rite level is worth buying");before=pace;
        }
    }

    @Test void upgradesArePaidFromThePackThenTheStoresThenTheWarehouse() {
        var cost=new LinkedHashMap<String,Integer>();cost.put("Ingredient_Life_Essence",10);cost.put(ROSE,10);
        assertTrue(GardenUpgrades.fromStores(cost,item->99,item->50).isEmpty(),"A full pack pays alone");
        assertEquals(Map.of(ROSE,4L),GardenUpgrades.fromStores(cost,item->item.equals(ROSE)?6:10,item->50));
        assertEquals(Map.of(ROSE,3L),GardenUpgrades.fromStores(cost,item->item.equals(ROSE)?6:10,item->item.equals(ROSE)?3:0),
            "What the stores cannot cover is left for the warehouse");
        assertEquals(Map.of("Ingredient_Life_Essence",10L,ROSE,10L),GardenUpgrades.fromStores(cost,item->0,item->50));
        assertTrue(GardenUpgrades.fromStores(cost,item->0,item->-5).isEmpty(),"Reserved stock is never spent");
    }

    @Test void theUpgradeTreeShowsEachTrackAsARowBoughtFromLeftToRight() {
        var nodes=com.hexvane.aetherhaven.workplace.BuildingUpgradeTree.garden();
        assertEquals(12,nodes.size());
        var ids=new HashSet<String>();
        for(var node:nodes) {
            assertTrue(ids.add(node.id()));
            assertEquals(com.hexvane.aetherhaven.workplace.BuildingUpgradeTree.Kind.GARDEN,node.kind());
            var track=Track.valueOf(node.branch());
            assertEquals(track.name()+":"+node.tier(),node.id());
            assertEquals(38+track.ordinal()*124,node.y(),"One row for each track");
            assertTrue(node.y()+140<=555,"All four rows show without scrolling");
            assertEquals(32+(node.tier()-1)*201,node.x());assertTrue(node.x()+174<=640,"The node fits the tree");
            assertEquals(node.tier()==1?List.of():List.of(track.name()+":"+(node.tier()-1)),node.parents());
            assertNotNull(node.item());assertNull(node.icon());assertEquals(1,node.level(),"The garden asks for no town level of its own");
            assertFalse(node.title().isBlank());assertFalse(node.description().contains(" - "));
        }
        assertEquals(8,com.hexvane.aetherhaven.workplace.BuildingUpgradeConnections.build(nodes,node->0).size(),"Two links in each of four rows");
        assertTrue(com.hexvane.aetherhaven.workplace.WorkplaceUpgradesPage.supports(SpiritGarden.KIND));
        assertTrue(com.hexvane.aetherhaven.workplace.BuildingUpgradeTree.production().stream()
            .noneMatch(node->node.kind()==com.hexvane.aetherhaven.workplace.BuildingUpgradeTree.Kind.GARDEN),"Other buildings keep the ingot tree");
    }

    @Test void upgradesBoughtWithIngotsCarryOverOnceAndAreNeverLost() {
        var production=new PlotProductionState();production.migrateIfNeeded();
        production.setIronUpgrade(1);production.setCobaltLevel(3);production.setThoriumLevel(2);production.setAdamantineLevel(1);
        assertFalse(production.usesSpiritGarden());
        assertTrue(GardenUpgrades.mapLegacy(production));
        assertTrue(production.usesSpiritGarden(),"A Conservatory is a garden from the moment it is looked at");
        assertEquals(3,GardenUpgrades.level(production,Track.PLANTERS));assertEquals(6,GardenUpgrades.planters(production));
        assertEquals(1,GardenUpgrades.level(production,Track.BOUNTY),"The fourth output slot becomes a richer harvest");
        assertEquals(2,GardenUpgrades.level(production,Track.RITE));assertEquals(1,GardenUpgrades.level(production,Track.STORES));
        assertEquals(50,GardenUpgrades.capacity(production,catalog(),ROSE),"Doubled storage stays doubled");
        production.garden().setLevel(Track.RITE.name(),3);
        assertFalse(GardenUpgrades.mapLegacy(production));assertEquals(3,GardenUpgrades.level(production,Track.RITE));
        var saved=gson.fromJson(gson.toJson(production),PlotProductionState.class);
        assertFalse(GardenUpgrades.mapLegacy(saved),"The carry over happens once");
        assertEquals(3,GardenUpgrades.level(saved,Track.PLANTERS));assertEquals(1,saved.getIronUpgrade());
        var partly=new PlotProductionState();partly.migrateIfNeeded();partly.setIronUpgrade(1);partly.setCobaltLevel(1);
        assertTrue(GardenUpgrades.mapLegacy(partly));
        assertEquals(2,GardenUpgrades.level(partly,Track.PLANTERS));assertEquals(0,GardenUpgrades.level(partly,Track.BOUNTY));
        var fresh=conservatory();
        for(var track:Track.values())assertEquals(0,GardenUpgrades.level(fresh,track));
    }

    @Test void savesFromBeforeTheGardenLoadWithoutOne() {
        var empty=gson.fromJson("{}",PlotProductionState.class);
        assertFalse(empty.usesSpiritGarden());assertTrue(empty.garden().planters().isEmpty());assertFalse(empty.garden().carrying());
        // A Conservatory that grew beds the old way keeps its stock and ingot levels. Its old beds are simply ordinary plants.
        var old=gson.fromJson("{\"field\":{\"adopted\":true,\"revision\":3,\"beds\":[{\"point\":{\"x\":1,\"y\":100,\"z\":1},"
            +"\"crop\":\"Plant_Crop_Health1_Block\",\"output\":\"Plant_Crop_Health1\"}]},\"amounts\":{\"Plant_Crop_Health1\":7},"
            +"\"thoriumLevel\":1,\"legacySlotUpgradesApplied\":true}",PlotProductionState.class);
        assertFalse(old.usesSpiritGarden());assertEquals(7,old.getAmount(ROSE));
        assertTrue(GardenUpgrades.mapLegacy(old));
        assertEquals(1,GardenUpgrades.level(old,Track.RITE));assertEquals(7,old.getAmount(ROSE));assertEquals(2,GardenUpgrades.planters(old));
        var bare=gson.fromJson("{\"garden\":{\"planters\":null,\"cargo\":null,\"blessed\":null,\"upgrades\":null}}",PlotProductionState.class);
        assertFalse(bare.usesSpiritGarden());assertEquals(0,bare.garden().freeSlot(2));assertEquals(0,bare.garden().level("RITE"));
        assertNull(bare.garden().bind(0,2,0,BLOCK,ROSE));assertTrue(bare.garden().bless(0));
    }

    @Test void theNinePlantsKeepTheirMasteryGatesAndTheirOwnPetals() {
        var rules=SpiritGardenRules.get();var entry=catalog();
        assertEquals(9,rules.plants().size());
        for(String family:List.of("Health","Stamina","Mana"))for(int tier=1;tier<=3;tier++) {
            var crop=new AlchemicalCrop(family,tier);var plant=rules.plant(crop.item());
            assertNotNull(plant,crop.item());assertEquals(crop.seed(),plant.seed());assertEquals(plant,rules.bySeed(crop.seed()));
            assertEquals(crop.skill(),plant.mastery());assertEquals(tier,plant.tier());
            assertEquals("Aetherhaven_Spirit_"+family+tier,plant.block());
            assertEquals(switch(family){case "Health"->"Plant_Petals_Blood";case "Stamina"->"Plant_Petals_Storm";default->"Plant_Petals_Azure";},plant.petal());
            assertEquals(crop.item()+"_Harvest",plant.plantDrops());assertEquals(crop.item(),plant.petalDrops());
            assertTrue(entry.containsItemId(plant.plant()));assertTrue(entry.containsItemId(plant.petal()));
            assertEquals(crop.skill(),com.hexvane.aetherhaven.mastery.CoreProductionRequirements.requirement("druid",plant.plant()).mastery());
        }
        assertEquals(1,List.of(1,4,7).indexOf(rules.plant("Plant_Crop_Mana2").mastery()));
        assertNull(rules.plant("Plant_Crop_Wheat_Item"));assertNull(rules.bySeed(null));
        assertEquals(5,rules.blessing().cost());assertEquals("Ingredient_Life_Essence",rules.blessing().item());
        assertTrue(rules.xpFor(ROSE)>rules.xpFor(PETAL));
        assertEquals(12,rules.outputs().size());
    }
}
