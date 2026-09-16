package com.hexvane.aetherhaven.autonomy;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.JsonParser;
import java.util.List;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Tag("autonomy")
class VillagerMouthPlaybackTest {
    @Test void mouthOverlayCannotRestartFaceOrTakeOverAnUnrelatedTriggeredAnimation() {
        assertEquals(com.hypixel.hytale.protocol.AnimationSlot.ServerAction, VillagerMouthPlayback.SLOT);
        assertNotEquals(com.hypixel.hytale.protocol.AnimationSlot.Face, VillagerMouthPlayback.SLOT);
        assertNotEquals(VillagerLifeVisuals.BODY_SLOT, VillagerMouthPlayback.SLOT);
        assertTrue(VillagerMouthPlayback.canAcquire(null));
        assertTrue(VillagerMouthPlayback.canAcquire("Aetherhaven_Life_Mouth_D"));
        assertFalse(VillagerMouthPlayback.canAcquire("Wave"));
        assertFalse(VillagerMouthPlayback.owns(null));
        assertFalse(VillagerMouthPlayback.owns("Wave"));
    }
    @Test void cueCursorHonorsSilencesBoundariesAndSkipsMissedShapes() {
        var cues = List.of(new VillagerLifeSpeech.MouthCue(0,"A"),
            new VillagerLifeSpeech.MouthCue(100,"D"), new VillagerLifeSpeech.MouthCue(250,"B"),
            new VillagerLifeSpeech.MouthCue(400,"A"), new VillagerLifeSpeech.MouthCue(600,"F"),
            new VillagerLifeSpeech.MouthCue(900,"A"));
        for (long t : new long[]{-1,0,99,400,599,900,1000,5000})
            assertEquals("A", VillagerMouthPlayback.shapeAt(cues,t,1000));
        assertEquals("D", VillagerMouthPlayback.shapeAt(cues,100,1000));
        assertEquals("B", VillagerMouthPlayback.shapeAt(cues,350,1000));
        assertEquals("F", VillagerMouthPlayback.shapeAt(cues,800,1000));
        assertEquals("A", VillagerMouthPlayback.shapeAt(List.of(),200,1000));
    }

    @Test void addonCueValidationRejectsInvalidTimingAndShapes() {
        for (String cues : new String[]{"[[1,\"A\"]]", "[[0,\"A\"],[0,\"B\"]]",
            "[[0,\"A\"],[1001,\"A\"]]", "[[0,\"Z\"]]", "[[0,\"A\"],[999,\"B\"]]"})
            assertThrows(IllegalArgumentException.class, () -> parse(cues));
        var clip = parse("[[0,\"D\"],[400,\"A\"]]").get("Example_Talk").getFirst();
        assertEquals("D", VillagerMouthPlayback.shapeAt(clip.mouthCues(),0,clip.audioMs()));
        assertThrows(UnsupportedOperationException.class, () -> clip.mouthCues().clear());
    }

    private static java.util.Map<String,List<VillagerLifeSpeech.Clip>> parse(String cues) {
        return VillagerLifeSpeech.parse(JsonParser.parseString("{\"Example_Talk\":[{\"clip\":\"Example_Talk_1\",\"audioMs\":1000,\"mouthCues\":"+cues+"}]}").getAsJsonObject());
    }
}
