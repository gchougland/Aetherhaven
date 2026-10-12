package com.hexvane.aetherhaven.hunter;

import com.google.gson.Gson;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class TownHuntsTest {
    @Test void retiredFireFamilyKeepsItsUnlockAndDoesNotDuplicateNewWaves() {
        var s=new Gson().fromJson("{\"unlocked\":[\"incandescent\",\"cinder\"]}",TownHunts.class);
        assertTrue(s.unlocked("cinder"));
        assertFalse(s.unlock("cinder"));
        assertEquals(List.of("cinder"),s.unlocked());
        var run=s.begin();assertEquals("cinder",s.next().id());
        assertTrue(s.collect(run,0,s.next()));assertEquals("cinder",s.next().id());
        assertTrue(HuntFamily.all().stream().noneMatch(f->f.id().equals("incandescent")));
        assertNotNull(HuntFamily.get("incandescent"));
    }
    @Test void everyRunRequiresPlayerStartAndAtLeastOneUnlockedCreature() {
        var state=new TownHunts();assertNull(state.begin());assertFalse(state.unlock("missing"));
        assertTrue(state.unlock("bone"));var id=state.begin();assertNotNull(id);assertNull(state.begin());
        state.end("Defeated");assertNull(state.run());assertEquals(1,state.runs());
        assertNotEquals(id,state.begin());assertEquals(2,state.runs());
    }
    @Test void wavesCycleAcrossOnlyUnlockedCreaturesAndKeepSpoilsAfterDefeat() {
        var s=new TownHunts();s.unlock("bone");s.unlock("web");var id=s.begin();
        assertEquals("bone",s.next().id());assertTrue(s.collect(id,0,s.next()));
        assertEquals("web",s.next().id());assertTrue(s.collect(id,1,s.next()));
        assertEquals("bone",s.next().id());s.end("Defeated");
        assertEquals(2,s.amount("Ingredient_Bone_Fragment"));assertEquals(1,s.amount("Ingredient_Sac_Venom"));
        assertEquals(2,s.best());assertEquals(2,s.lastDefeated());assertNull(s.next());
    }
    @Test void onlyTickedCreaturesEverEnterTheArena() {
        var s=new TownHunts();for(String id:List.of("bone","web","goblin","swarm"))assertTrue(s.unlock(id));
        assertEquals(List.of("bone","web","goblin","swarm"),s.chosen(),"a newly unlocked creature is in until the player says otherwise");
        assertTrue(s.choose("web",false));assertTrue(s.choose("swarm",false));
        assertFalse(s.choose("web",false),"already out");assertFalse(s.choose("bone",true),"already in");
        assertEquals(List.of("bone","goblin"),s.chosen());assertTrue(s.chosen("goblin"));assertFalse(s.chosen("web"));
        var run=s.begin();var seen=new ArrayList<String>();
        for(int wave=0;wave<12;wave++){var f=s.next();seen.add(f.id());assertTrue(s.collect(run,wave,f,Map.of()));}
        assertEquals(Set.of("bone","goblin"),new HashSet<>(seen),"twelve waves, never a creature that was left out");
        assertEquals(List.of("bone","goblin","bone","goblin"),seen.subList(0,4),"they still take turns in unlock order");
        assertFalse(s.choose("web",true),"the line-up is fixed while a run is under way");
        s.end("Retired");assertTrue(s.choose("web",true));assertEquals(List.of("bone","web","goblin"),s.chosen());
        var next=s.begin();assertEquals("bone",s.next().id());assertTrue(s.collect(next,0,s.next(),Map.of()));assertEquals("web",s.next().id());
    }
    @Test void anEmptyLineUpCannotBeSaved() {
        var one=new TownHunts();one.unlock("bone");long before=one.revision();
        assertFalse(one.choose("bone",false),"the only unlocked creature cannot be taken out");
        assertEquals(before,one.revision(),"a refused change saves nothing");assertEquals(List.of("bone"),one.chosen());
        var s=new TownHunts();s.unlock("bone");s.unlock("web");s.unlock("goblin");
        assertTrue(s.choose("bone",false));assertTrue(s.choose("goblin",false));
        assertFalse(s.choose("web",false),"the last creature in cannot be taken out");
        assertEquals(List.of("web"),s.chosen());
        var copy=new Gson().fromJson(new Gson().toJson(s),TownHunts.class);
        assertEquals(List.of("web"),copy.chosen(),"the choice is saved with the town");
        assertNotNull(copy.begin());assertEquals("web",copy.next().id());
    }
    @Test void lockedAndUnknownCreaturesCannotBeTicked() {
        var s=new TownHunts();s.unlock("bone");s.unlock("web");
        assertFalse(s.choose("goblin",true),"locked");assertFalse(s.choose("goblin",false));assertFalse(s.chosen("goblin"));
        assertFalse(s.choose("missing",true));assertFalse(s.choose(null,false));
        assertEquals(List.of("bone","web"),s.chosen());
        assertTrue(s.choose("web",false));assertTrue(s.unlock("goblin"));
        assertEquals(List.of("bone","goblin"),s.chosen(),"unlocking one creature does not bring back another that was left out");
    }
    @Test void savesFromBeforeTheChoiceExistedLoadWithEveryUnlockedCreatureIn() {
        var old=new Gson().fromJson("{\"unlocked\":[\"bone\",\"web\",\"goblin\"],\"upgrades\":{\"power\":2}}",TownHunts.class);
        assertEquals(List.of("bone","web","goblin"),old.chosen());
        var run=old.begin();var seen=new ArrayList<String>();
        for(int wave=0;wave<6;wave++){seen.add(old.next().id());assertTrue(old.collect(run,wave,old.next(),Map.of()));}
        assertEquals(List.of("bone","web","goblin","bone","web","goblin"),seen,"exactly the order such a save had before");
        var blank=new Gson().fromJson("{\"unlocked\":[\"bone\",\"web\"],\"benched\":null}",TownHunts.class);
        assertEquals(List.of("bone","web"),blank.chosen());assertTrue(blank.choose("bone",false));assertEquals(List.of("web"),blank.chosen());
        // A save that somehow leaves everything out is read as everything in, and can be changed from there.
        var damaged=new Gson().fromJson("{\"unlocked\":[\"bone\",\"web\"],\"benched\":[\"bone\",\"web\"]}",TownHunts.class);
        assertEquals(List.of("bone","web"),damaged.chosen());assertNotNull(damaged.begin());damaged.end("Retired");
        assertTrue(damaged.choose("web",false));assertEquals(List.of("bone"),damaged.chosen());
        // The retired fire family still stands for its replacement.
        var fire=new Gson().fromJson("{\"unlocked\":[\"incandescent\",\"bone\"],\"benched\":[\"incandescent\"]}",TownHunts.class);
        assertEquals(List.of("bone"),fire.chosen());assertTrue(fire.choose("cinder",true));assertEquals(List.of("cinder","bone"),fire.chosen());
    }
    @Test void theRunTabSaysWhoIsInTheArena() {
        var s=new TownHunts();
        assertTrue(HuntPage.lineup(s).contains("Unlock a creature"));
        s.unlock("bone");assertEquals("In the arena: Restless Bones.",HuntPage.lineup(s));
        for(var f:HuntFamily.all())s.unlock(f.id());
        assertEquals("In the arena: all 9 unlocked creatures.",HuntPage.lineup(s));
        s.choose("web",false);assertEquals("In the arena: 8 of 9 unlocked creatures. Sitting out: Silken Hollows.",HuntPage.lineup(s));
        for(String id:List.of("goblin","swarm","frost","cinder","outlander","void"))s.choose(id,false);
        assertEquals("In the arena: Restless Bones, Greater Voidspawn.",HuntPage.lineup(s));
    }
    @Test void staleAndDuplicateKillsCannotGrantOutput() {
        var s=new TownHunts();s.unlock("bone");var id=s.begin();var f=s.next();
        assertFalse(s.collect(UUID.randomUUID(),0,f));assertFalse(s.collect(id,1,f));
        assertTrue(s.collect(id,0,f));assertFalse(s.collect(id,0,f));
        s.end("Retired");assertFalse(s.collect(id,1,f));assertEquals(2,s.amount(f.icon()));
    }
    @Test void fullStoragePausesWaveWithoutConsumingKillOrSpilling() {
        var s=new TownHunts();s.unlock("bone");var id=s.begin();var f=s.next();
        for(int i=0;i<17;i++)assertTrue(s.collect(id,i,f));
        assertFalse(s.collect(id,17,f));assertEquals(17,s.defeated());assertEquals(34,s.amount(f.icon()));
        assertFalse(s.withdraw(f.icon(),35));assertFalse(s.withdraw(f.icon(),-1));assertTrue(s.withdraw(f.icon(),2));assertTrue(s.withdraw("Ingredient_Fabric_Scrap_Linen",2));
        assertTrue(s.collect(id,17,f));assertEquals(34,s.amount(f.icon()));
    }
    @Test void rolledSpoilsAreCollectedExactlyOnceAndAnEmptyRollStillCompletesTheWave() {
        var s=new TownHunts();s.unlock("bone");var run=s.begin();var family=s.next();
        assertTrue(s.collect(run,0,family,Map.of("Ingredient_Bone_Fragment",1,"Ingredient_Fabric_Scrap_Linen",2)));
        assertEquals(1,s.amount("Ingredient_Bone_Fragment"));assertEquals(2,s.amount("Ingredient_Fabric_Scrap_Linen"));
        assertFalse(s.collect(run,0,family,Map.of("Ingredient_Bone_Fragment",1)));
        assertTrue(s.collect(run,1,family,Map.of()));assertEquals(2,s.defeated());
        assertEquals(1,s.amount("Ingredient_Bone_Fragment"));
    }
    @Test void spoilsFilterRejectsNativeEquipmentIdsAndKeepsMaterials() {
        assertTrue(HuntSpoils.equipment("Weapon_Sword_Scrap"));
        assertTrue(HuntSpoils.equipment("Armor_Steel_Ancient_Head"));
        assertTrue(HuntSpoils.equipment("Tool_Pickaxe_Iron"));
        assertFalse(HuntSpoils.equipment("Ingredient_Fabric_Scrap_Cindercloth"));
        assertFalse(HuntSpoils.equipment("Ingredient_Fire_Essence"));
    }
    @Test void trainingCannotChangeDuringARunAndCapsAreEnforced() {
        var s=new TownHunts();assertFalse(s.upgrade("unknown"));assertEquals(260,s.maxHealth());
        for(int i=0;i<5;i++)assertTrue(s.upgrade("vitality"));assertFalse(s.upgrade("vitality"));assertEquals(560,s.maxHealth());
        s.unlock("bone");s.begin();assertFalse(s.upgrade("power"));assertFalse(s.unlock("web"));
        s.end("Defeated");assertTrue(s.upgrade("power"));assertEquals(1.2,s.damageMultiplier(),.001);
        assertTrue(s.upgrade("second_wind"));assertFalse(s.upgrade("second_wind"));
    }
    @Test void saveRoundTripPreservesRunIdentityButDoesNotInventKills() {
        var s=new TownHunts();s.unlock("swarm");s.upgrade("dressing");var id=s.begin();s.collect(id,0,s.next());
        var copy=new Gson().fromJson(new Gson().toJson(s),TownHunts.class);
        assertEquals(id,copy.run());assertEquals(1,copy.defeated());assertEquals(5,copy.recoveryPerWave());
        copy.end("Restarted");assertNull(copy.run());assertEquals(1,copy.amount("Ingredient_Chitin_Sturdy"));
        assertFalse(copy.collect(id,1,HuntFamily.get("swarm")));
    }
    @Test void scalingIsMonotonicBoundedAndUsesCompletedWaves() {
        assertEquals(1,TownHunts.enemyHealthScale(0));assertEquals(1.15,TownHunts.enemyHealthScale(1),.001);
        assertEquals(1.15,TownHunts.enemyDamageScale(1),.001);assertEquals(1,TownHunts.enemyDamageScale(-1));
        assertEquals(20,TownHunts.enemyHealthScale(Integer.MAX_VALUE));assertEquals(20,TownHunts.enemyDamageScale(Integer.MAX_VALUE));
    }
    @Test void integrationRejectsInvalidOrDuplicateFamilies() {
        assertThrows(IllegalArgumentException.class,()->HuntFamily.register(HuntFamily.get("bone")));
        assertThrows(IllegalArgumentException.class,()->new HuntFamily("bad","Bad","Bad","Role","Item",1,1,Map.of("Item",1000)));
        assertThrows(UnsupportedOperationException.class,()->HuntFamily.get("bone").drops().put("x",1));
    }
}
