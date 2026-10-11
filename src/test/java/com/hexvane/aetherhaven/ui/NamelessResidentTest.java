package com.hexvane.aetherhaven.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.villager.AetherhavenRoleLabels;
import org.junit.jupiter.api.Test;

class NamelessResidentTest {
    @Test
    void aTownsfolkNobodyCanNameIsLeftOutOfLists() {
        assertTrue(TownVillagerDirectory.namelessTownsfolk(true, false), "No character known: no name or portrait to show");
        assertFalse(TownVillagerDirectory.namelessTownsfolk(true, true));
        assertFalse(TownVillagerDirectory.namelessTownsfolk(false, false), "Core villagers are named by their role");
    }

    @Test
    void aRoleIsNeverShownAsItsId() {
        String shown = AetherhavenRoleLabels.displayNameForRoleId("Aetherhaven_Townsfolk");
        assertFalse(shown.contains("_"), shown);
        assertEquals("Townsfolk", shown);
        assertFalse(AetherhavenRoleLabels.displayNameForRoleId("Some_Pack_Villager").contains("_"));
    }
}
