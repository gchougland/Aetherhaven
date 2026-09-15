package com.hexvane.aetherhaven.autonomy;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Tag("autonomy")
class VillagerRomanceTest {
    @Test void romanceIsOccasionalAndBothResponsesAreReachable() {
        int romantic = 0, loving = 0, rejecting = 0;
        for (int i = 0; i < 100; i++) {
            var beats = VillagerRomance.choose(i / 100.0, .2);
            if (!beats.isEmpty()) romantic++;
            var response = VillagerRomance.choose(0, i / 100.0).get(1);
            if (response.hearts()) loving++; else rejecting++;
        }
        assertEquals(15, romantic);
        assertEquals(60, loving);
        assertEquals(40, rejecting);
        assertTrue(VillagerRomance.choose(Double.NaN, 0).isEmpty());
    }

    @Test void affectionIsAnsweredBeforeTheInitiatorReacts() {
        for (double outcome : new double[]{0, .99}) {
            var beats = VillagerRomance.choose(0, outcome);
            assertEquals(3, beats.size());
            assertTrue(beats.get(0).firstSpeaks());
            assertTrue(beats.get(0).hearts());
            assertEquals("Love", beats.get(0).bubble());
            assertFalse(beats.get(1).firstSpeaks());
            assertTrue(beats.get(2).firstSpeaks());
            if (outcome == 0) {
                assertTrue(beats.get(1).hearts());
                assertTrue(beats.get(2).hearts() && beats.get(2).listenerHearts());
            } else {
                assertEquals("Disagree", beats.get(1).gesture());
                assertEquals("Groan", beats.get(2).voice());
                assertEquals("Bored", beats.get(2).gesture());
                assertFalse(beats.get(2).speechBubble());
                for (var beat : beats.subList(1, 3)) assertFalse(beat.hearts() || beat.listenerHearts());
            }
        }
    }

    @Test void finalReactionFinishesEvenWhenTheNormalChatTimeoutHasExpired() {
        var session = new VillagerLifeState.Session(UUID.randomUUID(), UUID.randomUUID(), 1000, "Home");
        session.talkingSinceMs = 1000;
        session.romance = VillagerRomance.choose(0, .99);
        session.beat = 2;
        session.nextBeatMs = 30_000;
        assertFalse(session.finishedTalking(30_000), "Rejection still needs its sad response");
        session.beat = 3;
        session.nextBeatMs = 36_000;
        assertFalse(session.finishedTalking(35_999), "Do not cut off the groan or body animation");
        assertTrue(session.finishedTalking(36_000));
        session.romance = java.util.List.of();
        assertTrue(session.finishedTalking(36_000), "Ordinary conversations retain their timer");
    }

    @Test void allRomanticBeatsHaveVoicedBodyAndFaceTracksAtEveryPitch() {
        for (double outcome : new double[]{0, .99}) {
            for (var beat : VillagerRomance.choose(0, outcome)) {
                for (String voice : VillagerLifePolicy.VOICES) {
                    for (String pitch : new String[]{"", "Lower", "Higher"}) {
                        var clip = VillagerLifeSpeech.select(voice + pitch, beat.voice(), 0);
                        assertNotNull(clip);
                        var face = clip.faces().get(beat.gesture());
                        assertNotNull(face, voice + pitch + ":" + beat.gesture());
                        assertTrue(face.durationMs() >= clip.audioMs());
                    }
                }
            }
        }
    }
}
