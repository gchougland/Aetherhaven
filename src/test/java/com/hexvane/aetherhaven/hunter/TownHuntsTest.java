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
    @Test void staleAndDuplicateKillsCannotGrantOutput() {
        var s=new TownHunts();s.unlock("bone");var id=s.begin();var f=s.next();
        assertFalse(s.collect(UUID.randomUUID(),0,f));assertFalse(s.collect(id,1,f));
        assertTrue(s.collect(id,0,f));assertFalse(s.collect(id,0,f));
        s.end("Retired");assertFalse(s.collect(id,1,f));assertEquals(2,s.amount(f.icon()));
    }
    @Test void fullStoragePausesWaveWithoutConsumingKillOrSpilling() {
        var s=new TownHunts();s.unlock("bone");var id=s.begin();var f=s.next();
        for(int i=0;i<16;i++)assertTrue(s.collect(id,i,f));
        assertFalse(s.collect(id,16,f));assertEquals(16,s.defeated());assertEquals(32,s.amount(f.icon()));
        assertFalse(s.withdraw(f.icon(),33));assertFalse(s.withdraw(f.icon(),-1));assertTrue(s.withdraw(f.icon(),2));assertTrue(s.withdraw("Ingredient_Fabric_Scrap_Linen",2));
        assertTrue(s.collect(id,16,f));assertEquals(32,s.amount(f.icon()));
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
        assertEquals(id,copy.run());assertEquals(1,copy.defeated());assertEquals(4,copy.recoveryPerWave());
        copy.end("Restarted");assertNull(copy.run());assertEquals(1,copy.amount("Ingredient_Chitin_Sturdy"));
        assertFalse(copy.collect(id,1,HuntFamily.get("swarm")));
    }
    @Test void scalingIsMonotonicBoundedAndUsesCompletedWaves() {
        assertEquals(1,TownHunts.enemyHealthScale(0));assertEquals(1.15,TownHunts.enemyHealthScale(1),.001);
        assertEquals(1.12,TownHunts.enemyDamageScale(1),.001);assertEquals(1,TownHunts.enemyDamageScale(-1));
        assertEquals(20,TownHunts.enemyHealthScale(Integer.MAX_VALUE));assertEquals(20,TownHunts.enemyDamageScale(Integer.MAX_VALUE));
    }
    @Test void integrationRejectsInvalidOrDuplicateFamilies() {
        assertThrows(IllegalArgumentException.class,()->HuntFamily.register(HuntFamily.get("bone")));
        assertThrows(IllegalArgumentException.class,()->new HuntFamily("bad","Bad","Bad","Role","Item",1,1,Map.of("Item",1000)));
        assertThrows(UnsupportedOperationException.class,()->HuntFamily.get("bone").drops().put("x",1));
    }
}
