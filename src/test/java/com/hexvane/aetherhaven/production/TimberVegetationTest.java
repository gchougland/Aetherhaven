package com.hexvane.aetherhaven.production;

import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static com.hexvane.aetherhaven.production.TimberHarvestPlan.*;
import static com.hexvane.aetherhaven.production.TimberProductionState.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class TimberVegetationTest {
    private static final Position ROOT=new Position(0,100,0), LEAF=new Position(1,100,0);
    private static final Ground AIR=new Ground(true,"Empty",0,0,true,false,false,false);
    private static final Ground SOIL=new Ground(true,"Soil_Grass",0,0,false,true,true,false);
    private static final Ground GRASS=new Ground(true,"Plant_Grass",0,0,false,false,false,false,true);
    private static TimberPlantingReceipt tree(){return new TimberPlantingReceipt(UUID.randomUUID(),"Plant_Sapling_Beech","Wood_Beech_Trunk",ROOT,0,List.of(
        new TimberPlantingReceipt.Stage(0,"seed",List.of(new Cell(ROOT,"Plant_Sapling_Beech",0,Kind.FOLIAGE))),
        new TimberPlantingReceipt.Stage(1,"grown",List.of(new Cell(ROOT,"Wood_Beech_Trunk",0,Kind.TIMBER),new Cell(LEAF,"Plant_Leaves_Beech",0,Kind.FOLIAGE)))));}
    @Test void onlySoftGrassBushesAndFernsCanBeCleared() {
        for(String id:List.of("Plant_Grass","Plant_Bush_Bramble","Plant_Fern"))assertTrue(TimberVegetation.replaceable(id,"Model",true,false));
        for(String id:List.of("Soil_Grass","Wood_Beech_Trunk","Plant_Crop_Wheat","Plant_Sapling_Beech","Furniture_Chest"))
            assertFalse(TimberVegetation.replaceable(id,"Model",true,false));
        assertFalse(TimberVegetation.replaceable("Plant_Bush","Cube",true,false));
        assertFalse(TimberVegetation.replaceable("Plant_Bush","Model",true,true));

    }
    @Test void grassAllowsBindingAndIsClearedOnlyWhenTheLoggerPlants() {
        var world=new HashMap<Position,Ground>();world.put(ROOT,GRASS);world.put(LEAF,GRASS);world.put(new Position(0,99,0),SOIL);
        var timber=new TimberProductionState();var tree=tree();
        assertNull(timber.plan(0,1,0,tree,p->true,world::get));assertEquals(GRASS,world.get(ROOT));
        assertTrue(TimberGrowingSpace.obstructions(tree,false,p->true,world::get,Set.of()).isEmpty());
        var prepared=timber.prepare(0,1,tree,p->true,world::get);assertTrue(prepared.ready());
        assertTrue(prepared.planting().originals().contains(new Original(ROOT,"Empty",0)),"Cleared grass is not replanted after felling");
        assertNull(timber.plant(0,1,prepared,p->true,world::get,()->TimberVegetation.clearAndPlant(TimberGrowingSpace.cells(tree),world::get,
            (p,target)->{world.put(p,AIR);return true;},()->{assertEquals(AIR,world.get(ROOT));assertEquals(AIR,world.get(LEAF));return true;})));
        assertEquals(SOIL,world.get(new Position(0,99,0)));assertEquals(1,timber.trees().size());
    }
    @Test void fullGrassTerrainAboveTheStoneStillBlocksTheTree() {
        var prepared=new TimberProductionState().prepare(0,1,tree(),p->true,p->SOIL);
        assertFalse(prepared.ready());
    }
    @Test void failedPlantingRestoresGrassButNeverOverwritesFreshEdits() {
        var world=new HashMap<Position,Ground>();world.put(ROOT,GRASS);
        java.util.function.BiPredicate<Position,Observed> write=(p,target)->{world.put(p,"Empty".equals(target.blockId())?AIR:GRASS);return true;};
        assertFalse(TimberVegetation.clearAndPlant(Set.of(ROOT),world::get,write,()->false));assertEquals(GRASS,world.get(ROOT));
        var chest=new Ground(true,"Furniture_Chest",0,0,false,false,false,true);
        assertFalse(TimberVegetation.clearAndPlant(Set.of(ROOT),world::get,write,()->{world.put(ROOT,chest);return false;}));assertEquals(chest,world.get(ROOT));
    }
    @Test void tallGrassAndBushesClearTheirWholeHitboxWithoutLeavingFillers() {
        var upper=new Position(0,101,0);var occupied=Set.of(ROOT,upper);
        int filler=com.hypixel.hytale.server.core.util.FillerBlockUtil.pack(0,1,0);
        for(String id:List.of("Plant_Grass_Dry_Tall","Plant_Bush_Dead")) {
            var world=new HashMap<Position,Ground>();
            world.put(ROOT,new Ground(true,id,0,0,false,false,false,false,true,occupied));
            world.put(upper,new Ground(true,id,0,filler,false,false,false,false,true,occupied));
            world.put(new Position(0,99,0),SOIL);
            var old=tree();var stages=new ArrayList<>(old.stages());
            stages.set(1,new TimberPlantingReceipt.Stage(1,"grown",List.of(new Cell(ROOT,"Wood_Beech_Trunk",0,Kind.TIMBER),new Cell(upper,"Plant_Leaves_Beech",0,Kind.FOLIAGE))));
            var tree=new TimberPlantingReceipt(old.id(),old.sapling(),old.output(),ROOT,0,stages);
            var prepared=new TimberProductionState().prepare(0,1,tree,p->true,world::get);
            assertTrue(prepared.ready(),prepared.issue());
            assertTrue(TimberGrowingSpace.obstructions(tree,false,p->true,world::get,Set.of()).isEmpty());
            var writes=new ArrayList<Position>();
            assertTrue(TimberVegetation.clearAndPlant(occupied,world::get,(p,target)->{writes.add(p);world.put(p,AIR);return true;},
                ()->{assertEquals(AIR,world.get(ROOT));assertEquals(AIR,world.get(upper));return true;}));
            assertEquals(List.of(upper,ROOT),writes,"Clear upper filler first, then its anchor");
        }
    }
    @Test void aTallPlantsProtectedOrOutOfBoundsPartsPreventAnyClearing() {
        var upper=new Position(0,101,0);var occupied=Set.of(ROOT,upper);
        var anchor=new Ground(true,"Plant_Bush_Dead",0,0,false,false,false,false,true,occupied);
        assertFalse(TimberVegetation.clearAndPlant(Set.of(ROOT),p->!p.equals(upper),p->anchor,(p,target)->fail("Do not reach outside the town"),()->fail()));
        var chest=new Ground(true,"Furniture_Chest",0,0,false,false,false,true);
        // The ordinary whole-tree preflight still rejects protected cells inside a tall plant's footprint.
        var old=tree();var stages=new ArrayList<>(old.stages());
        stages.set(1,new TimberPlantingReceipt.Stage(1,"grown",List.of(new Cell(ROOT,"Wood_Beech_Trunk",0,Kind.TIMBER),new Cell(upper,"Plant_Leaves_Beech",0,Kind.FOLIAGE))));
        var tree=new TimberPlantingReceipt(old.id(),old.sapling(),old.output(),ROOT,0,stages);
        assertFalse(new TimberProductionState().prepare(0,1,tree,p->true,p->p.equals(ROOT)?anchor:p.equals(upper)?chest:SOIL).ready());
        assertFalse(new TimberProductionState().prepare(0,1,tree,p->!p.equals(upper),p->anchor).ready());
    }
    @Test void anEdgePlantIsClearedWholeEvenWhenOnlyOneCellTouchesTheTree() {
        var upper=new Position(0,101,0);var occupied=Set.of(ROOT,upper);
        int filler=com.hypixel.hytale.server.core.util.FillerBlockUtil.pack(0,1,0);
        var anchor=new Ground(true,"Plant_Grass_Dry_Tall",0,0,false,false,false,false,true,occupied);
        var top=new Ground(true,"Plant_Grass_Dry_Tall",0,filler,false,false,false,false,true,occupied);
        var world=new HashMap<Position,Ground>();world.put(ROOT,anchor);world.put(upper,top);world.put(LEAF,AIR);world.put(new Position(0,99,0),SOIL);
        var tree=tree();var footprint=TimberGrowingSpace.cells(tree);
        assertFalse(footprint.contains(upper),"Exercise an edge plant extending beyond the reserved tree cells");
        assertTrue(new TimberProductionState().prepare(0,1,tree,p->true,world::get).ready());
        assertTrue(TimberGrowingSpace.obstructions(tree,false,p->true,world::get,Set.of()).isEmpty());
        assertFalse(new TimberProductionState().prepare(0,1,tree,p->!p.equals(upper),world::get).ready());
        assertEquals(Set.of(ROOT),TimberGrowingSpace.obstructions(tree,false,p->!p.equals(upper),world::get,Set.of()));
        var cleared=new HashSet<Position>();
        assertTrue(TimberVegetation.clearAndPlant(footprint,p->true,world::get,(p,target)->{cleared.add(p);world.put(p,AIR);return true;},()->true));
        assertEquals(occupied,cleared);assertEquals(SOIL,world.get(new Position(0,99,0)));
    }
}
