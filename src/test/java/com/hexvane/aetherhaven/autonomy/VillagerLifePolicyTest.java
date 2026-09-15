package com.hexvane.aetherhaven.autonomy;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Tag("autonomy")
class VillagerLifePolicyTest {
    @Test void idleNeverUsesWorkRecordingsButWorkCanUseEither() {
        assertEquals("Idle", VillagerLifePolicy.ambientMood(false, false));
        assertEquals("Idle", VillagerLifePolicy.ambientMood(false, true));
        assertEquals("Idle", VillagerLifePolicy.ambientMood(true, false));
        assertEquals("Work", VillagerLifePolicy.ambientMood(true, true));
    }

    @Test void rainThoughtsOnlyMatchWetWeather() {
        assertTrue(VillagerLifeContext.rainWeatherId("Zone1_Rain"));
        assertTrue(VillagerLifeContext.rainWeatherId("Thunderstorm"));
        assertFalse(VillagerLifeContext.rainWeatherId("Snow_Storm"));
        assertFalse(VillagerLifeContext.rainWeatherId("Sandstorm"));
        assertFalse(VillagerLifeContext.rainWeatherId("Zone1_Sunny"));
        assertFalse(VillagerLifeContext.rainWeatherId(null));
    }
    @Test void recreationChoosesConversationSlightlyMoreOftenThanBuildings() {
        int conversations = 0;
        for (int i = 0; i < 100; i++) if (VillagerLifePolicy.chooseConversation(i / 100.0, true)) conversations++;
        assertEquals(65, conversations);
        assertTrue(VillagerLifePolicy.chooseConversation(.3, false));
        assertFalse(VillagerLifePolicy.chooseConversation(.5, false));
    }

    @Test void partnerSearchTimerDoesNotReserveTheVillager() {
        var life = new VillagerLifeState();
        life.nextSearchMs = 100_000;
        assertFalse(life.ownsActivity(1000));
        life.socialCooldownMs = 10_000;
        assertEquals(life.socialCooldownMs, life.clone().socialCooldownMs);
    }
    @Test void hungerAndRestTakePriorityOverCompany() {
        assertTrue(VillagerLifePolicy.wantsCompany(80, 80, 10));
        assertFalse(VillagerLifePolicy.wantsCompany(49, 80, 10));
        assertFalse(VillagerLifePolicy.wantsCompany(80, 29, 10));
        assertFalse(VillagerLifePolicy.wantsCompany(80, 80, 40));
    }

    @Test void conversationRequiresNearbySeparateFeetOnTheSameFloor() {
        assertTrue(VillagerLifePolicy.inTalkingRange(4, 0));
        assertFalse(VillagerLifePolicy.inTalkingRange(.1, 0));
        assertFalse(VillagerLifePolicy.inTalkingRange(9, 0));
        assertFalse(VillagerLifePolicy.inTalkingRange(4, 2));
    }

    @Test void funAccumulatesOverTicksButNeverOfflineOrAboveFull() {
        assertEquals(40.75f, VillagerLifePolicy.refill(40, .25));
        assertEquals(41.5f, VillagerLifePolicy.refill(40, 600));
        assertEquals(100, VillagerLifePolicy.refill(99.9f, .25));
        assertEquals(40, VillagerLifePolicy.refill(40, -1));
        assertEquals(40, VillagerLifePolicy.refill(40, Double.NaN));
        float fun = 5;
        for (int i = 0; i < 96; i++) fun = VillagerLifePolicy.refill(fun, .25);
        assertEquals(77, fun);
    }

    @Test void needActingReflectsTheLowestUnsatisfiedNeed() {
        assertEquals("Hungry", VillagerLifePolicy.needEmote(20, 80, 80));
        assertEquals("Sleepy", VillagerLifePolicy.needEmote(70, 20, 80));
        assertEquals("Bored", VillagerLifePolicy.needEmote(80, 80, 10));
        assertEquals("Sleepy", VillagerLifePolicy.needEmote(40, 10, 20));
        assertNull(VillagerLifePolicy.needEmote(80, 80, 80));
    }

    @Test void unknownVoicesStayTheSameAcrossDifferentWorldSpawns() {
        var voices = new HashSet<String>();
        for (int i = 0; i < 100; i++) {
            UUID id = new UUID(0, i);
            String voice = VillagerLifePolicy.voice(id);
            assertEquals(voice, VillagerLifePolicy.voice(UUID.fromString(id.toString())));
            voices.add(voice);
        }
        assertEquals(java.util.Set.of("WarmMale"), voices);
    }
}
