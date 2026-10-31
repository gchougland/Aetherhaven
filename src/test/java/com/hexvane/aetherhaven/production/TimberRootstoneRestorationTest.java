package com.hexvane.aetherhaven.production;

import com.google.gson.Gson;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static com.hexvane.aetherhaven.production.TimberHarvestPlan.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class TimberRootstoneRestorationTest {
    private static final Position ROOT = new Position(2, 100, 3), STONE = new Position(2, 99, 3);
    private static final String SAPLING = "Plant_Sapling_Beech", WOOD = "Wood_Beech_Trunk", ROOTS = "Wood_Beech_Roots";
    private static TimberProductionState.Planting planting() {
        var tree = new TimberPlantingReceipt(UUID.randomUUID(), SAPLING, WOOD, ROOT, 0, List.of(
            new TimberPlantingReceipt.Stage(0, SAPLING, List.of(new Cell(ROOT, SAPLING, 0, Kind.FOLIAGE))),
            new TimberPlantingReceipt.Stage(1, "young", List.of(new Cell(ROOT, WOOD, 0, Kind.TIMBER), new Cell(STONE, ROOTS, 2, Kind.FOLIAGE))),
            new TimberPlantingReceipt.Stage(2, "mature", List.of(new Cell(ROOT, WOOD, 0, Kind.TIMBER)))));
        return new TimberProductionState.Planting(tree, List.of(new TimberProductionState.Original(ROOT, "Empty", 0),
            new TimberProductionState.Original(STONE, Rootstone.BLOCK, 3)));
    }
    @Test void partiallyGrownRootsAndLaterClearedRootsRestoreThePaidStoneAfterReload() {
        var saved = new Gson().fromJson(new Gson().toJson(planting()), TimberProductionState.Planting.class);
        for (var old : List.of(new Observed(true, ROOTS, 2, false), new Observed(true, "Empty", 0, false))) {
            var world = new HashMap<Position, Observed>(); world.put(STONE, old); world.put(ROOT, new Observed(true, WOOD, 0, false));
            assertNull(TimberRootstoneRestoration.restore(saved, world::get, (p, target) -> {world.put(p,target);return true;}));
            assertEquals(new Observed(true, Rootstone.BLOCK, 3, false), world.get(STONE));
            assertEquals(WOOD, world.get(ROOT).blockId(), "Uprooting preserves the tree for the player to chop");
            assertNull(TimberRootstoneRestoration.restore(saved, world::get, (p, target) -> fail("Already restored: do not create another stone")));
        }
    }
    @Test void playerStructuresAndProtectedOrUnloadedCellsCannotBeOverwritten() {
        for (var current : List.of(new Observed(true, "Furniture_Chest", 0, false), new Observed(true, ROOTS, 2, true),
                new Observed(false, null, 0, false), new Observed(true, ROOTS, 1, false))) {
            assertNotNull(TimberRootstoneRestoration.restore(planting(), p -> current, (p, target) -> fail("Unsafe write")));
        }
        assertNotNull(TimberRootstoneRestoration.restore(planting(), p -> new Observed(true, ROOTS, 2, false), (p, target) -> false));
    }
    @Test void anUnplantedOrLegacyGroveDoesNotCreateFreeRootstones() {
        assertNull(TimberRootstoneRestoration.restore(null, p -> fail(), (p,target) -> fail()));
        var legacy = new TimberProductionState.Planting(planting().tree(), List.of(new TimberProductionState.Original(STONE,"Soil_Dirt",0)));
        assertNull(TimberRootstoneRestoration.restore(legacy, p -> fail(), (p,target) -> fail()));
    }
    @Test void nativeRootHitboxCanGiveTheStoneBackWithoutRemovingItsAnchor() {
        var old=planting();var receipt=old.tree();
        var tree=new TimberPlantingReceipt(receipt.id(),receipt.sapling(),receipt.output(),receipt.root(),0,receipt.stages(),Set.of(STONE));
        int filler=com.hypixel.hytale.server.core.util.FillerBlockUtil.pack(0,-1,0);
        var saved=new TimberProductionState.Planting(tree,old.originals());
        var writes=new HashMap<Position,Observed>();
        assertNull(TimberRootstoneRestoration.restore(saved,p->new Observed(true,WOOD,0,filler,false),(p,target)->{writes.put(p,target);return true;}));
        assertEquals(Set.of(STONE),writes.keySet());
        assertEquals(Rootstone.BLOCK,writes.get(STONE).blockId());
    }
}
