package com.hexvane.aetherhaven.plotcreator;

import com.hexvane.aetherhaven.town.TownRecord;
import com.hypixel.hytale.protocol.GameMode;
import java.util.UUID;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("construction")
class PlacedMineUpgradeAccessTest {
    @Test void conversionKeepsTheCreativeEditorAndTownOwnershipRequirements() {
        var town=new TownRecord();var owner=UUID.randomUUID();town.setOwner(owner,"owner");
        assertTrue(PlacedMineUpgradePage.allowed(town,owner,GameMode.Creative));
        assertFalse(PlacedMineUpgradePage.allowed(town,owner,GameMode.Adventure));
        assertFalse(PlacedMineUpgradePage.allowed(town,UUID.randomUUID(),GameMode.Creative));
    }
}
