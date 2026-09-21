package com.hexvane.aetherhaven.ui;

import static org.junit.jupiter.api.Assertions.*;
import com.hypixel.hytale.codec.ExtraInfo;
import org.bson.BsonDocument;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Tag("town")
class PlayerTownJournalSpeechTest {
    @Test void ambientPreferencesPersistAndOldSavesGetDefaults() {
        var state = PlayerTownJournalState.CODEC.decode(BsonDocument.parse("{}"), new ExtraInfo());
        assertEquals(70, state.getVillagerSpeechVolumePercent());
        assertEquals(100, state.getVillagerChatterFrequencyPercent());
        state.setVillagerSpeechVolumePercent(23);
        state.setVillagerChatterFrequencyPercent(170);
        state.setDialogueSpeechVolumePercent(81);
        state.setLastDialogueClip("MellowMale_Talk_1");
        var encoded = PlayerTownJournalState.CODEC.encode(state, new ExtraInfo());
        var restored = PlayerTownJournalState.CODEC.decode(encoded, new ExtraInfo());
        assertEquals(23, restored.getVillagerSpeechVolumePercent());
        assertEquals(170, restored.getVillagerChatterFrequencyPercent());
        assertEquals(81, restored.getDialogueSpeechVolumePercent());
        assertNull(restored.getLastDialogueClip());
        var clone = (PlayerTownJournalState)state.clone();
        assertEquals(23, clone.getVillagerSpeechVolumePercent());
        assertEquals(170, clone.getVillagerChatterFrequencyPercent());
        assertEquals(state.getLastDialogueClip(), clone.getLastDialogueClip());
    }

    @Test void chatterFrequencyIsPerListenerAndCanBeTurnedOff() {
        UUID npc = new UUID(1,2);
        var normal = new PlayerTownJournalState();
        var frequent = new PlayerTownJournalState(); frequent.setVillagerChatterFrequencyPercent(200);
        var rare = new PlayerTownJournalState(); rare.setVillagerChatterFrequencyPercent(25);
        for (var prefs : java.util.List.of(normal,frequent,rare)) assertTrue(prefs.tryAmbientSpeech(npc,1000));
        assertTrue(frequent.tryAmbientSpeech(npc,7000));
        assertFalse(normal.tryAmbientSpeech(npc,7000));
        assertTrue(normal.tryAmbientSpeech(npc,13000));
        assertFalse(rare.tryAmbientSpeech(npc,13000));
        assertTrue(rare.tryAmbientSpeech(npc,49000));
        normal.setVillagerChatterFrequencyPercent(0);
        assertFalse(normal.tryAmbientSpeech(npc,999999));
        assertTrue(normal.isDialogueSpeechEnabled());
        assertEquals(70, normal.getDialogueSpeechVolumePercent());
        rare.setVillagerChatterFrequencyPercent(1);
        assertTrue(rare.tryAmbientSpeech(new UUID(2,3), 400_000));
        assertFalse(rare.tryAmbientSpeech(npc, 500_000));
    }

    @Test void preferenceLimitsAndResetAreSafe() {
        var prefs = new PlayerTownJournalState();
        prefs.setVillagerSpeechVolumePercent(-30); assertEquals(0, prefs.getVillagerSpeechVolumePercent());
        prefs.setVillagerSpeechVolumePercent(900); assertEquals(100, prefs.getVillagerSpeechVolumePercent());
        prefs.setVillagerChatterFrequencyPercent(-10); assertEquals(0, prefs.getVillagerChatterFrequencyPercent());
        prefs.setVillagerChatterFrequencyPercent(900); assertEquals(200, prefs.getVillagerChatterFrequencyPercent());
        prefs.resetHudPreferences();
        assertEquals(70, prefs.getVillagerSpeechVolumePercent());
        assertEquals(100, prefs.getVillagerChatterFrequencyPercent());
    }

    @Test void nearbyVillagersShareOneAmbientAudioSlotPerListener() {
        var listener = new PlayerTownJournalState();
        UUID first = new UUID(1, 1), second = new UUID(1, 2);
        assertTrue(listener.tryAmbientSpeech(first, 1000, 5000));
        for (int i = 2; i < 102; i++) {
            assertFalse(listener.tryAmbientSpeech(new UUID(1, i), 1000, 5000));
        }
        assertFalse(listener.tryAmbientSpeech(second, 5999, 5000));
        // Rejected candidates do not consume their per-villager cooldown.
        assertTrue(listener.tryAmbientSpeech(second, 6000, 5000));
        assertFalse(listener.tryAmbientSpeech(first, 11_000, 1000));
        assertTrue(listener.tryAmbientSpeech(first, 13_000, 1000));
        // A different listener's sound budget is independent.
        assertTrue(new PlayerTownJournalState().tryAmbientSpeech(first, 1000, 5000));
    }

    @Test void densityBudgetScalesWithFrequencyAndMutedAudioDoesNotReserveIt() {
        var listener = new PlayerTownJournalState();
        UUID first = new UUID(1, 1), second = new UUID(1, 2);
        listener.setVillagerSpeechVolumePercent(0);
        assertFalse(listener.tryAmbientSpeech(first, 1000, 5000));
        listener.setVillagerSpeechVolumePercent(70);
        listener.setVillagerChatterFrequencyPercent(25);
        assertTrue(listener.tryAmbientSpeech(first, 1000, 500));
        assertFalse(listener.tryAmbientSpeech(second, 12_999, 500));
        assertTrue(listener.tryAmbientSpeech(second, 13_000, 500));
        listener.resetHudPreferences();
        assertTrue(listener.tryAmbientSpeech(first, 13_000, 500));
    }

    @Test void cooldownHistorySurvivesALaterFrequencyReduction() {
        var listener = new PlayerTownJournalState();
        UUID first = new UUID(1, 1);
        assertTrue(listener.tryAmbientSpeech(first, 1000, 500));
        assertTrue(listener.tryAmbientSpeech(new UUID(1, 2), 400_000, 500));
        listener.setVillagerChatterFrequencyPercent(1);
        assertFalse(listener.tryAmbientSpeech(first, 500_000, 500));
        assertTrue(listener.tryAmbientSpeech(first, 1_201_000, 500));
    }

    @Test void cloningKeepsTheActiveAudioBudgetButPersistenceDoesNotResumePlayback() {
        var listener = new PlayerTownJournalState();
        assertTrue(listener.tryAmbientSpeech(new UUID(1, 1), 1000, 5000));
        var clone = (PlayerTownJournalState) listener.clone();
        assertFalse(clone.tryAmbientSpeech(new UUID(1, 2), 2000, 5000));
        var restored = PlayerTownJournalState.CODEC.decode(PlayerTownJournalState.CODEC.encode(listener, new ExtraInfo()), new ExtraInfo());
        assertTrue(restored.tryAmbientSpeech(new UUID(1, 2), 2000, 5000));
    }
}
