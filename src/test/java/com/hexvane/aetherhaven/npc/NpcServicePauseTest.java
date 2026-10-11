package com.hexvane.aetherhaven.npc;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class NpcServicePauseTest {
    @Test void teleportExpiresButAnOngoingConversationKeepsTheResidentStill() {
        assertTrue(NpcServicePause.keepHolding(1000, 6000, false));
        assertFalse(NpcServicePause.keepHolding(6000, 6000, false));
        assertTrue(NpcServicePause.keepHolding(9000, 6000, true));
        assertFalse(NpcServicePause.keepHolding(9000, 6000, false));
        assertTrue(NpcServicePause.keepHolding(2000, 6000, false));
    }
}
