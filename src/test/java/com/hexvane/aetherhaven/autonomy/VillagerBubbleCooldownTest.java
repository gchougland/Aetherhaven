package com.hexvane.aetherhaven.autonomy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
@org.junit.jupiter.api.Tag("town")
class VillagerBubbleCooldownTest {
    @Test void differentSourcesShareOneVisibleSlotAndCloneRetainsCooldown(){
        var state=new VillagerLifeState();assertTrue(state.beginBubble(1000));assertFalse(state.beginBubble(1000));assertFalse(state.beginBubble(3999));
        assertFalse(((VillagerLifeState)state.clone()).beginBubble(3999));assertTrue(state.beginBubble(4000));
    }
}
