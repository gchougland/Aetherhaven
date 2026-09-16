package com.hexvane.aetherhaven.ui;

import static org.junit.jupiter.api.Assertions.*;
import com.hexvane.aetherhaven.town.TownRecord;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("prop")
class PropShopPageTest {
    @Test void unlockStatusUsesTheGivenTownAndPalette() {
        var home = new TownRecord();
        var other = new TownRecord();
        home.unlockBlockPalette("stone");
        assertTrue(PropShopPage.paletteUnlockKey(home, "stone").endsWith(".unlocked"));
        assertTrue(PropShopPage.paletteUnlockKey(home, "wood").endsWith(".locked"));
        assertTrue(PropShopPage.paletteUnlockKey(other, "stone").endsWith(".locked"));
    }

    @Test void playerWithoutTownGetsNoTownStatus() {
        assertTrue(PropShopPage.paletteUnlockKey(null, "stone").endsWith(".noTown"));
    }
}
