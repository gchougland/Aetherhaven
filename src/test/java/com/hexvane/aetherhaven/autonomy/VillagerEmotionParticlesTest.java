package com.hexvane.aetherhaven.autonomy;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
@org.junit.jupiter.api.Tag("autonomy")
class VillagerEmotionParticlesTest {
    @Test void accentsMatchTheActingAndLeaveNeutralActivitiesQuiet() {
        assertEquals("Question", VillagerEmotionParticles.effect("Question", "Question", .5));
        assertEquals("Confusion", VillagerEmotionParticles.effect("Ponder", "Thinking", .5));
        assertEquals("Gloom", VillagerEmotionParticles.effect("Bored", "Groan", .5));
        assertEquals("Surprise", VillagerEmotionParticles.effect("Surprise", null, .1));
        assertEquals("Shock", VillagerEmotionParticles.effect("Surprise", "Gasp", .1));
        assertEquals("Surprise", VillagerEmotionParticles.effect("Surprise", "Gasp", .9));
        assertNull(VillagerEmotionParticles.effect("ReadLoop", "Thinking", .5));
        assertEquals("Confusion", VillagerEmotionParticles.effect("ReadLoop", "Thinking", .2));
        assertEquals("Question", VillagerEmotionParticles.effect("Explain", "Question", .5));
        assertEquals("Shock", VillagerEmotionParticles.effect(null, "Gasp", .1));
        assertNull(VillagerEmotionParticles.effect("Mix", "Work", .5));
        assertNull(VillagerEmotionParticles.effect(null, "Talk", .5));
    }
}
