package com.hexvane.aetherhaven.alchemy;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import com.hexvane.aetherhaven.alchemy.AlchemyBrew.Move;
import com.hexvane.aetherhaven.alchemy.AlchemyBrew.Quality;
import com.hypixel.hytale.assetstore.*;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.event.*;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.asset.type.item.config.metadata.ItemDisplayMetadata;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import org.bson.BsonDocument;
import org.junit.jupiter.api.*;

/**
 * Hold the brew steady. The owner's promises: ingredients are never lost, a normal potion can always be bottled
 * without playing, a better brew lasts longer or restores more, and someone paying attention usually reaches Great.
 */
@Tag("town")
class AlchemyBrewTest {
    private static final Path EFFECTS=Path.of("src/main/resources/Server/Entity/Effects/Aetherhaven/Alchemy");
    private static final AlchemyBrew.Rules RULES=AlchemyBrew.load(AlchemyBrewTest.class.getClassLoader());
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
    /** Someone paying attention: aims at where the page says the band is heading, using only what the page shows. */
    private static Move attentive(AlchemyBrew brew) {
        Move best=Move.WAIT;
        for(var move:Move.values())if(Math.abs(brew.after(move)-brew.expectedCentre())<Math.abs(brew.after(best)-brew.expectedCentre()))best=move;
        return best;
    }
    private static AlchemyBrew play(long seed,int ingredients,int mastery,Function<AlchemyBrew,Move> player) {
        var brew=new AlchemyBrew(RULES,seed,ingredients,mastery);
        while(!brew.finished())brew.play(player.apply(brew));
        return brew;
    }
    /** Shares of Good, Great and Perfect over many brews. */
    private static double[] shares(int ingredients,int mastery,Function<AlchemyBrew,Move> player) {
        var counts=new double[3];int brews=3000;
        for(long seed=1;seed<=brews;seed++)counts[play(seed,ingredients,mastery,player).quality().ordinal()]++;
        for(int i=0;i<3;i++)counts[i]/=brews;
        return counts;
    }

    @Test void theBundledRulesAreTheOnesThePageAndTheGuideQuote() {
        assertTrue(RULES.valid());
        assertEquals(10,RULES.beats());assertEquals(6,RULES.greatBeats());assertEquals(9,RULES.perfectBeats());
        assertEquals(1.25f,RULES.duration(Quality.GREAT));assertEquals(1.5f,RULES.duration(Quality.PERFECT));assertEquals(1f,RULES.duration(Quality.GOOD));
        assertEquals(1.1f,RULES.restore(Quality.GREAT));assertEquals(1.2f,RULES.restore(Quality.PERFECT));assertEquals(1f,RULES.restore(Quality.GOOD));
        assertEquals(25,AlchemyBrew.Rules.percent(RULES.duration(Quality.GREAT)));assertEquals(50,AlchemyBrew.Rules.percent(RULES.duration(Quality.PERFECT)));
        assertEquals(10,AlchemyBrew.Rules.percent(RULES.restore(Quality.GREAT)));assertEquals(20,AlchemyBrew.Rules.percent(RULES.restore(Quality.PERFECT)));
    }
    @Test void theGuideAndThePageNoLongerKnowTheOldGrindStirSimmerOrder() throws Exception {
        String guide=Files.readString(Path.of("src/main/resources/Server/Aetherhaven/GuideTopics/en-US/villager_alchemist.md"));
        // The guide quotes the rules in words. If the data file changes, this is the reminder to reword it.
        assertTrue(guide.contains("A brew lasts "+RULES.beats()+" beats"));
        assertTrue(guide.contains("| "+RULES.greatBeats()+" to "+(RULES.perfectBeats()-1)+" | Great | 25% longer | 10% more |"));
        assertTrue(guide.contains("| "+RULES.perfectBeats()+" or "+RULES.beats()+" | Perfect | 50% longer | 20% more |"));
        assertTrue(guide.contains("| "+(RULES.greatBeats()-1)+" or fewer | Good |"));
        String page=Files.readString(Path.of("src/main/java/com/hexvane/aetherhaven/alchemy/AlchemyCauldronPage.java"));
        String document=Files.readString(Path.of("src/main/resources/Common/UI/Custom/Aetherhaven/AlchemyCauldronPage.ui"));
        String lang=Files.readString(Path.of("src/main/resources/Server/Languages/en-US/aetherhaven_alchemy.lang"));
        String table=Files.readString(Path.of("src/main/java/com/hexvane/aetherhaven/alchemy/AlchemyIngredients.java"));
        for(String gone:List.of("Grind","Simmer","Prepare","preparation","#Clue","#Progress","sequence(","clue("))
            for(String text:List.of(guide,page,document,lang,table))assertFalse(text.contains(gone),gone);
        // The page plays in beats and never on a clock: a timed redraw would make the game drop button presses.
        for(String timer:List.of("scheduleOnWorld","ScheduledExecutor","EntityTickingSystem","Timer"))assertFalse(page.contains(timer),timer);
        for(String id:List.of("#Start ","#Stir ","#Wait ","#Stoke ","#Gauge ","#Band ","#Ghost ","#Marker ","#BrewRule ","#FillNote "))assertTrue(document.contains(id),id);
    }
    @Test void theShareOfBeatsInsideTheBandDecidesTheQuality() {
        for(int inside=0;inside<=RULES.beats();inside++)
            assertEquals(inside>=9?Quality.PERFECT:inside>=6?Quality.GREAT:Quality.GOOD,RULES.quality(inside),inside+" beats");
        // Counted against an independent tally: a beat counts exactly when the heat ends inside the band.
        for(long seed=1;seed<=200;seed++) {
            var brew=new AlchemyBrew(RULES,seed,2,3);int tally=0;
            while(!brew.finished()) {
                boolean counted=brew.play(attentive(brew));
                assertEquals(brew.counts(brew.heat(),brew.centre()),counted);assertEquals(counted,brew.lastCounted());
                if(counted)tally++;
                assertEquals(tally,brew.inside());
            }
            assertEquals(RULES.beats(),brew.beat());assertEquals(RULES.quality(tally),brew.quality());
        }
    }
    @Test void theEdgeOfTheBandIsForgiving() {
        var brew=new AlchemyBrew(RULES,7,1,1);
        assertEquals(12,brew.width());assertEquals(1,RULES.forgiveness());
        // Half the band is 6. One more is forgiven, so a marker that only touches the edge still counts.
        for(int away=0;away<=7;away++){assertTrue(brew.counts(50+away,50),"+"+away);assertTrue(brew.counts(50-away,50),"-"+away);}
        assertFalse(brew.counts(58,50));assertFalse(brew.counts(42,50));
    }
    @Test void moreIngredientsNarrowAndLivenTheBandAndMasteryWidensIt() {
        assertEquals(12,RULES.width(1,1));assertEquals(10,RULES.width(2,1));assertEquals(8,RULES.width(3,1));
        assertEquals(21,RULES.width(1,10));assertEquals(13,RULES.width(3,6),"Three ingredients need Mastery 6, which nearly buys the width back");
        for(int ingredients=1;ingredients<=3;ingredients++)for(int mastery=1;mastery<10;mastery++) {
            assertTrue(RULES.width(ingredients,mastery+1)>=RULES.width(ingredients,mastery));
            assertTrue(RULES.width(ingredients,mastery+1)>RULES.width(ingredients,1));
            if(ingredients<3)assertTrue(RULES.width(ingredients+1,mastery)<RULES.width(ingredients,mastery));
        }
        assertTrue(RULES.drift(1)<RULES.drift(2)&&RULES.drift(2)<RULES.drift(3));
        // It shows in play: Mastery makes Perfect more common, a third ingredient makes it rarer.
        double novice=shares(1,1,AlchemyBrewTest::attentive)[2],master=shares(1,10,AlchemyBrewTest::attentive)[2];
        assertTrue(master>novice+.2,novice+" then "+master);
        assertTrue(shares(3,6,AlchemyBrewTest::attentive)[2]<shares(1,6,AlchemyBrewTest::attentive)[2]);
    }
    @Test void someonePayingAttentionUsuallyReachesGreatAndLuckAloneDoesNot() {
        var first=shares(1,1,AlchemyBrewTest::attentive);
        assertTrue(first[1]+first[2]>=.85,"Great or better on a first flask: "+(first[1]+first[2]));
        assertTrue(first[2]>=.2&&first[2]<=.75,"Perfect is reachable but not given: "+first[2]);
        var third=shares(3,6,AlchemyBrewTest::attentive);
        assertTrue(third[1]+third[2]>=.8,"Great or better with three ingredients at the Mastery that unlocks them: "+(third[1]+third[2]));
        assertTrue(shares(1,10,AlchemyBrewTest::attentive)[2]>=.85,"A master's band makes Perfect the usual result");
        var random=new Random(11);var pressing=shares(1,1,brew->Move.values()[random.nextInt(3)]);
        assertTrue(pressing[1]+pressing[2]<=.15,"Pressing anything: "+(pressing[1]+pressing[2]));
        var waiting=shares(1,1,brew->Move.WAIT);
        assertTrue(waiting[1]+waiting[2]<=.3&&waiting[2]<=.02,"Only ever letting it rise: "+(waiting[1]+waiting[2]));
        for(var move:Move.values())assertTrue(shares(2,3,brew->move)[2]<=.02,"One button alone is never Perfect: "+move);
    }
    @Test void theHeatMovesAsTheButtonsSayAndTheBandGoesWhereThePageSaid() {
        for(long seed=1;seed<=300;seed++) {
            var brew=new AlchemyBrew(RULES,seed,(int)(seed%3)+1,(int)(seed%10)+1);
            assertEquals(30,brew.heat());assertEquals(0,brew.beat());
            while(!brew.finished()) {
                int heat=brew.heat(),centre=brew.centre(),direction=brew.direction();
                assertEquals(Math.min(100,heat+6),brew.after(Move.WAIT));assertEquals(Math.min(100,heat+18),brew.after(Move.STOKE));assertEquals(Math.max(0,heat-12),brew.after(Move.STIR));
                assertTrue(direction==1||direction==-1);
                assertTrue(Math.abs(brew.expectedCentre()-centre)<=RULES.drift(3));
                var move=Move.values()[(int)((seed+brew.beat())%3)];int landing=brew.after(move);
                brew.play(move);
                assertEquals(landing,brew.heat());
                // The direction on the page before the press is the direction the band then moved.
                assertTrue((brew.centre()-centre)*direction>0,"seed "+seed+" beat "+brew.beat());
                assertTrue(Math.abs(brew.centre()-centre)<=RULES.drift(3)+RULES.jitter());
                assertTrue(brew.centre()-brew.width()/2>=0&&brew.centre()+brew.width()/2<=AlchemyBrew.GAUGE);
            }
        }
    }
    @Test void theSameSeedIsTheSameBrewAndAFinishedBrewIgnoresPresses() {
        var one=play(42,2,4,AlchemyBrewTest::attentive);var two=play(42,2,4,AlchemyBrewTest::attentive);
        assertEquals(one.inside(),two.inside());assertEquals(one.heat(),two.heat());assertEquals(one.centre(),two.centre());
        int inside=one.inside(),heat=one.heat();
        assertFalse(one.play(Move.STOKE));assertFalse(one.play(null));
        assertEquals(inside,one.inside());assertEquals(heat,one.heat());assertEquals(RULES.beats(),one.beat());
    }
    @Test void notPlayingOrStoppingEarlyIsAGoodPotionAndNeverCostsAnything() {
        var untouched=new AlchemyBrew(RULES,3,1,1);
        assertEquals(Quality.GOOD,untouched.quality());
        // Nine beats inside the band and then bottled: still Good, because the brew was not finished.
        AlchemyBrew early=null;
        for(long seed=1;seed<500&&early==null;seed++) {
            var brew=new AlchemyBrew(RULES,seed,1,10);
            for(int beat=0;beat<RULES.beats()-1;beat++)brew.play(attentive(brew));
            if(brew.inside()==RULES.beats()-1)early=brew;
        }
        assertNotNull(early);assertFalse(early.finished());assertEquals(Quality.GOOD,early.quality());
        // Bottling without a brew is the ordinary fill, and what the flask gets is the ordinary potion.
        var bag=new SimpleItemContainer((short)2);var flask=new ItemStack(AlchemyFlask.ITEM,1);
        bag.setItemStackForSlot((short)0,flask,false);bag.setItemStackForSlot((short)1,new ItemStack("Plant_Crop_Health1",2),false);
        assertTrue(AlchemyBottling.fill(bag,(short)0,flask,List.of("Plant_Crop_Health1"),null,early.quality()).filled());
        assertEquals(Quality.GOOD,AlchemyFlask.quality(bag.getItemStack((short)0)));
        assertEquals(1,bag.getItemStack((short)1).getQuantity(),"One per charge, as always");
    }
    @Test void theQualityTravelsWithThePotionInsideTheFlask() {
        var mix=List.of("Plant_Crop_Mushroom_Cap_Red","Plant_Crop_Health1");
        var flask=new ItemStack(AlchemyFlask.ITEM,1);for(int i=0;i<2;i++)flask=AlchemyFlask.upgrade(flask);
        var bag=new SimpleItemContainer((short)3);
        bag.setItemStackForSlot((short)0,flask,false);bag.setItemStackForSlot((short)1,new ItemStack(mix.get(0),3),false);bag.setItemStackForSlot((short)2,new ItemStack(mix.get(1),3),false);
        assertTrue(AlchemyBottling.fill(bag,(short)0,flask,mix,null,Quality.PERFECT).filled());
        var filled=bag.getItemStack((short)0);
        assertEquals(Quality.PERFECT,AlchemyFlask.quality(filled));assertEquals(3,AlchemyFlask.charges(filled));
        // Saved and loaded, dropped and picked up, moved to another slot: the item's own data is all there is.
        var loaded=new ItemStack(filled.getItemId(),1,filled.getDurability(),filled.getMaxDurability(),BsonDocument.parse(com.hexvane.aetherhaven.inventory.ItemStackData.metadata(filled).toJson()));
        assertEquals(Quality.PERFECT,AlchemyFlask.quality(loaded));assertEquals(mix,AlchemyFlask.ingredients(loaded));
        // Partly drunk, upgraded and given a fresh tooltip: still the same potion.
        var sipped=AlchemyFlask.consume(loaded);
        assertEquals(2,AlchemyFlask.charges(sipped));assertEquals(Quality.PERFECT,AlchemyFlask.quality(sipped));
        assertEquals(Quality.PERFECT,AlchemyFlask.quality(AlchemyFlask.upgrade(sipped)));assertEquals(Quality.PERFECT,AlchemyFlask.quality(AlchemyFlask.refreshDisplay(sipped)));
        String tooltip=com.hypixel.hytale.server.core.util.MessageUtil.formatMessageToPlainString(
            sipped.getFromMetadataOrNull(ItemDisplayMetadata.KEYED_CODEC).getDescription().getFormattedMessage());
        assertTrue(tooltip.contains("Charges: 2 / 3"),tooltip);
        var children=sipped.getFromMetadataOrNull(ItemDisplayMetadata.KEYED_CODEC).getDescription().getFormattedMessage().children;
        assertTrue(Arrays.stream(children).anyMatch(child->"aetherhaven_alchemy.aetherhaven.flask.quality.perfect".equals(child.messageId)),"The tooltip names the quality");
        // The last drink takes the quality with it, and so does pouring the potion away.
        var last=AlchemyFlask.consume(AlchemyFlask.consume(sipped));
        assertEquals(0,AlchemyFlask.charges(last));assertEquals(Quality.GOOD,AlchemyFlask.quality(last));
        assertEquals(Quality.GOOD,AlchemyFlask.quality(AlchemyFlask.empty(sipped)));
        assertEquals(Quality.GREAT,AlchemyFlask.quality(AlchemyFlask.fill(last,mix,Quality.GREAT)));
        // A flask filled before quality existed is a Good one, and its tooltip is unchanged.
        var old=AlchemyFlask.fill(new ItemStack(AlchemyFlask.ITEM,1),mix);
        assertEquals(Quality.GOOD,AlchemyFlask.quality(old));
        assertFalse(com.hexvane.aetherhaven.inventory.ItemStackData.metadata(old).getDocument("AetherhavenAlchemyFlask").containsKey("quality"));
        assertEquals(2,old.getFromMetadataOrNull(ItemDisplayMetadata.KEYED_CODEC).getDescription().getFormattedMessage().children.length);
        assertEquals(Quality.GOOD,Quality.of(-1));assertEquals(Quality.GOOD,Quality.of(7));
    }
    @Test void aBetterBrewLastsLongerOrRestoresMoreForEveryEffectInThePack() throws Exception {
        int lasting=0,instant=0;
        try(var files=Files.list(EFFECTS)) {
            for(var file:files.toList()) {
                float seconds=JsonParser.parseString(Files.readString(file)).getAsJsonObject().get("Duration").getAsFloat();
                assertEquals(seconds,AlchemyFlaskService.lastingSeconds(seconds,RULES,Quality.GOOD),file.toString());
                assertEquals(0f,AlchemyFlaskService.restoreBonus(seconds,RULES,Quality.GOOD),file.toString());
                if(AlchemyFlaskService.instant(seconds)) {
                    instant++;
                    assertEquals(seconds,AlchemyFlaskService.lastingSeconds(seconds,RULES,Quality.PERFECT),"A restore does not start lasting: "+file);
                    assertEquals(.1f,AlchemyFlaskService.restoreBonus(seconds,RULES,Quality.GREAT),1e-6f);assertEquals(.2f,AlchemyFlaskService.restoreBonus(seconds,RULES,Quality.PERFECT),1e-6f);
                } else {
                    lasting++;
                    assertEquals(seconds*1.25f,AlchemyFlaskService.lastingSeconds(seconds,RULES,Quality.GREAT),1e-4f);assertEquals(seconds*1.5f,AlchemyFlaskService.lastingSeconds(seconds,RULES,Quality.PERFECT),1e-4f);
                    assertEquals(0f,AlchemyFlaskService.restoreBonus(seconds,RULES,Quality.PERFECT),"A lasting effect is lengthened, not doubled up: "+file);
                }
            }
        }
        assertEquals(9,instant,"Health, Mana and Stamina in three strengths");assertEquals(11,lasting);
        // In plain numbers: a minute of Fire Ward, two minutes of Health Boost, and Regeneration's heals every 2 seconds.
        assertEquals(75f,AlchemyFlaskService.lastingSeconds(60f,RULES,Quality.GREAT));assertEquals(90f,AlchemyFlaskService.lastingSeconds(60f,RULES,Quality.PERFECT));
        assertEquals(150f,AlchemyFlaskService.lastingSeconds(120f,RULES,Quality.GREAT));assertEquals(180f,AlchemyFlaskService.lastingSeconds(120f,RULES,Quality.PERFECT));
        assertEquals(15,(int)Math.floor(AlchemyFlaskService.lastingSeconds(30.1f,RULES,Quality.GOOD)/2));
        assertEquals(18,(int)Math.floor(AlchemyFlaskService.lastingSeconds(30.1f,RULES,Quality.GREAT)/2));
        assertEquals(22,(int)Math.floor(AlchemyFlaskService.lastingSeconds(30.1f,RULES,Quality.PERFECT)/2));
    }
    @Test void rulesThatCouldNotBePlayedAreRefusedSoABrokenPackFileCannotStopABrew() {
        String good="{\"Beats\":10,\"StartHeat\":30,\"Rise\":6,\"Stoke\":12,\"Stir\":18,\"BandWidth\":12,\"BandWidthPerExtraIngredient\":-2,\"BandWidthPerMasteryLevel\":1,"
            +"\"BandMinWidth\":8,\"EdgeForgiveness\":1,\"Drift\":[8,10,12],\"DriftJitter\":5,\"TurnChance\":0.35,\"GreatShare\":0.6,\"PerfectShare\":0.9,"
            +"\"GreatDuration\":1.25,\"PerfectDuration\":1.5,\"GreatRestore\":1.1,\"PerfectRestore\":1.2}";
        assertTrue(AlchemyBrew.Rules.parse(JsonParser.parseString(good).getAsJsonObject()).valid());
        for(String[] change:new String[][]{{"\"Stir\":18","\"Stir\":6"},{"\"Beats\":10","\"Beats\":0"},{"\"Drift\":[8,10,12]","\"Drift\":[8,10]"},{"\"DriftJitter\":5","\"DriftJitter\":9"},
            {"\"GreatShare\":0.6","\"GreatShare\":0.95"},{"\"PerfectDuration\":1.5","\"PerfectDuration\":1.1"},{"\"GreatRestore\":1.1","\"GreatRestore\":0.5"},{"\"BandMinWidth\":8","\"BandMinWidth\":40"}})
            assertFalse(AlchemyBrew.Rules.parse(JsonParser.parseString(good.replace(change[0],change[1])).getAsJsonObject()).valid(),change[1]);
    }
}
