package com.hexvane.aetherhaven.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hexvane.aetherhaven.town.PlotFootprintRecord;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("construction")
class PrefabFootprintClearUtilTest {

    @Test
    void footprintAabbBlockCount_isFullBoxVolume() {
        PlotFootprintRecord fp = new PlotFootprintRecord(0, 0, 0, 9, 4, 9);
        assertEquals(500, PrefabFootprintClearUtil.footprintAabbBlockCount(fp));
    }

    @Test
    void sparseClearCanTargetFewerCellsThanAabbClear() {
        PlotFootprintRecord fp = new PlotFootprintRecord(0, 0, 0, 10, 10, 10);
        int aabbCells = PrefabFootprintClearUtil.footprintAabbBlockCount(fp);
        int sparsePrefabListedCells = 8;
        assertTrue(sparsePrefabListedCells < aabbCells);
    }

    // npc, ownNpc, pastedWithPrefab, blockEntity, prop, displayItem, modEntity

    @Test
    void clearingASiteNeverTakesACreatureThatIsNotOurs() {
        // A pet, a mount or wildlife standing inside a plot when its building is moved or a festival changes the square.
        assertFalse(PrefabFootprintClearUtil.belongsToBuilding(true, false, false, false, false, false, false));
        // The released rule removed every entity in the box that was not a player or a town villager.
    }

    @Test
    void clearingASiteNeverTakesLootOrOtherModsThings() {
        // Not a creature and nothing a building prefab places: an item a player dropped (it can still be destroyed, so
        // it is not one of the invulnerable display items), a boat, a projectile, another mod's helper entity.
        assertFalse(PrefabFootprintClearUtil.belongsToBuilding(false, false, false, false, false, false, false));
    }

    @Test
    void clearingASiteStillTakesWhatTheBuildingPlaced() {
        assertTrue(PrefabFootprintClearUtil.belongsToBuilding(false, false, false, true, false, false, false), "block decoration");
        assertTrue(PrefabFootprintClearUtil.belongsToBuilding(false, false, false, false, true, false, false), "a prop");
        assertTrue(PrefabFootprintClearUtil.belongsToBuilding(false, false, false, false, false, true, false), "a display item");
        assertTrue(PrefabFootprintClearUtil.belongsToBuilding(false, false, false, false, false, false, true), "one of the mod's own markers or festival pieces");
        assertTrue(PrefabFootprintClearUtil.belongsToBuilding(false, false, true, false, false, false, false), "anything still marked as pasted");
        assertTrue(PrefabFootprintClearUtil.belongsToBuilding(true, false, true, false, false, false, false), "a creature the prefab itself placed");
    }

    @Test
    void clearingASiteStillTakesTheModsOwnCreatures() {
        assertTrue(PrefabFootprintClearUtil.belongsToBuilding(true, true, false, false, false, false, false));
    }
}
