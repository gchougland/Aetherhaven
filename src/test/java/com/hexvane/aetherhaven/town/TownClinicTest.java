package com.hexvane.aetherhaven.town;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.clinic.TownClinic;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class TownClinicTest {
    @Test void boundedSuppliesCannotOverflowOrAcceptPartialBottles() {
        var clinic = new TownClinic();
        assertFalse(clinic.supply(-1)); assertFalse(clinic.supply(Integer.MAX_VALUE));
        assertTrue(clinic.supply(10)); assertFalse(clinic.supply(1));
        assertEquals(800, clinic.medicine());
        assertEquals(40, clinic.treat(20, 100)); assertFalse(clinic.supply(1));
        assertEquals(40, clinic.treat(20, 100)); assertTrue(clinic.supply(1));
    }
    @Test void actualHealingIsChargedWithoutOverhealOrResurrection() {
        var clinic = new TownClinic(); clinic.supply(1);
        assertEquals(5, clinic.treat(95, 100)); assertEquals(75, clinic.medicine());
        assertEquals(0, clinic.treat(0, 100)); assertEquals(0, clinic.treat(100, 100));
        assertEquals(0, clinic.treat(Float.NaN, 100)); assertEquals(0, clinic.treat(10, Float.POSITIVE_INFINITY));
        assertEquals(40, clinic.treat(1, 100)); assertEquals(35, clinic.treat(1, 100));
        assertEquals(0, clinic.treat(1, 100)); assertEquals(0, clinic.medicine());
    }
    @Test void pauseAndTownSaveKeepMedicineWithoutOfflineWork() {
        var town = new TownRecord(); town.getClinic().supply(2); town.getClinic().toggle();
        var saved = new Gson().fromJson(new Gson().toJson(town), TownRecord.class).getClinic();
        assertTrue(saved.paused()); assertEquals(160, saved.medicine());
        assertEquals(0, saved.treat(10, 100)); assertEquals(160, saved.medicine());
        saved.toggle(); assertEquals(40, saved.treat(10, 100));
    }
}
