package com.hexvane.aetherhaven.guild;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class GuardRecoveryProgressTest {
    @Test void civilianRecoveryLeavesEnoughTimeForAnAutomaticClinicVisit() {
        var rest=new GuardRecoveryProgress();
        float health=120;
        for(int i=0;i<120;i++)health+=rest.advance(.25,health,200,true);
        assertEquals(120,health);
        assertTrue(com.hexvane.aetherhaven.clinic.ClinicVisitPolicy.needsVisit(health,200,false,30));
        for(int i=0;i<40;i++)health+=rest.advance(.25,health,200,true);
        assertEquals(122,health);
    }
    @Test void quietRestIsSlowAndCannotBeAwardedByATimeJump() {
        var rest = new GuardRecoveryProgress();
        assertEquals(0, rest.advance(86400, 100, 240, true));
        for (int i = 0; i < 158; i++) assertEquals(0, rest.advance(.25, 100, 240, true));
        assertEquals(2.4, rest.advance(.25, 100, 240, true), .001);
    }
    @Test void DamageAndOfflineTimeRestartRestWithoutHealing() {
        var rest = new GuardRecoveryProgress();
        for (int i = 0; i < 150; i++) rest.advance(.25, 100, 240, true);
        assertEquals(0, rest.advance(.25, 80, 240, true));
        for (int i = 0; i < 159; i++) assertEquals(0, rest.advance(.25, 80, 240, true));
        assertEquals(0, rest.advance(.25, 80, 240, false));
        assertEquals(0, rest.advance(.25, 80, 240, true));
    }
    @Test void restNeverRevivesOrOverheals() {
        var rest = new GuardRecoveryProgress();
        for (int i = 0; i < 500; i++) assertEquals(0, rest.advance(.25, 0, 240, true));
        for (int i = 0; i < 159; i++) assertEquals(0, rest.advance(.25, 239, 240, true));
        assertEquals(1, rest.advance(.25, 239, 240, true));
        assertEquals(0, rest.advance(.25, Float.NaN, 240, true));
    }
}
