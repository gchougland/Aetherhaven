package com.hexvane.aetherhaven.production;

import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static com.hexvane.aetherhaven.production.TimberHarvestPlan.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class TimberGrowingSpaceTest {
    private static final Position ROOT = new Position(10, 100, 20), STONE = new Position(10,99,20),
        EARLY = new Position(7,102,20), DEEP = new Position(11,97,20), TOP = new Position(10,110,22), CLEARANCE = new Position(14,106,20);
    private static TimberPlantingReceipt tree() {
        return new TimberPlantingReceipt(UUID.randomUUID(),"Plant_Sapling_Beech","Wood_Beech_Trunk",ROOT,0,List.of(
            new TimberPlantingReceipt.Stage(0,"seed",List.of(new Cell(ROOT,"Plant_Sapling_Beech",0,Kind.FOLIAGE))),
            new TimberPlantingReceipt.Stage(1,"young",List.of(new Cell(ROOT,"Wood_Beech_Trunk",0,Kind.TIMBER),new Cell(EARLY,"Plant_Leaves_Beech",0,Kind.FOLIAGE),new Cell(DEEP,"Wood_Beech_Roots",0,Kind.FOLIAGE))),
            new TimberPlantingReceipt.Stage(2,"grown",List.of(new Cell(ROOT,"Wood_Beech_Trunk",0,Kind.TIMBER),new Cell(TOP,"Plant_Leaves_Beech",0,Kind.FOLIAGE)))),Set.of(CLEARANCE));
    }
    private static TimberProductionState.Ground clear(Position p) {
        boolean soil = p.y()<ROOT.y();
        return new TimberProductionState.Ground(true, p.equals(STONE)?Rootstone.BLOCK:soil?"Soil_Dirt":"Empty",0,0,!soil,soil,soil,false);
    }
    @Test void outlineIncludesIntermediateBranchesUndergroundRootsClearanceAndStone() {
        assertEquals(new TimberGrowingSpace.Box(7,97,20,15,111,23),TimberGrowingSpace.bounds(tree()));
        assertTrue(TimberGrowingSpace.cells(tree()).containsAll(Set.of(EARLY,DEEP,TOP,CLEARANCE,STONE)));
    }
    @Test void obstructionMarkersAreOutsideEveryOpaqueBlockFace() {
        var b=TimberGrowingSpace.marker(EARLY);
        assertTrue(b.minX()<EARLY.x()&&b.maxX()>EARLY.x()+1);
        assertTrue(b.minY()<EARLY.y()&&b.maxY()>EARLY.y()+1);
        assertTrue(b.minZ()<EARLY.z()&&b.maxZ()>EARLY.z()+1);
    }
    @Test void previewUsesPlantingRulesAndUpdatesAfterPlayerClearsBlocks() {
        var blocks=new HashMap<Position,TimberProductionState.Ground>();
        blocks.put(EARLY,new TimberProductionState.Ground(true,"Furniture_Chest",0,0,false,false,false,true));
        blocks.put(DEEP,new TimberProductionState.Ground(true,"Wood_Planks",0,0,false,false,false,false));
        var blocked=TimberGrowingSpace.obstructions(tree(),false,p->true,p->blocks.getOrDefault(p,clear(p)),Set.of());
        assertEquals(Set.of(EARLY,DEEP),blocked);
        blocks.clear();
        assertTrue(TimberGrowingSpace.obstructions(tree(),false,p->true,TimberGrowingSpaceTest::clear,Set.of()).isEmpty());
        assertTrue(new TimberProductionState().prepare(0,1,tree(),p->true,TimberGrowingSpaceTest::clear).ready());
    }
    @Test void ownGrowingTreeIsNotABlockerButOtherGrovesAndTownBordersAre() {
        var read=(java.util.function.Function<Position,TimberProductionState.Ground>)p -> p.equals(ROOT)
            ?new TimberProductionState.Ground(true,"Plant_Sapling_Beech",0,0,false,false,false,true):clear(p);
        assertEquals(Set.of(ROOT),TimberGrowingSpace.obstructions(tree(),false,p->true,read,Set.of()));
        assertTrue(TimberGrowingSpace.obstructions(tree(),true,p->true,read,Set.of()).isEmpty());
        assertEquals(Set.of(EARLY,TOP),TimberGrowingSpace.obstructions(tree(),true,p->!p.equals(TOP),read,Set.of(EARLY)));
    }
    @Test void nativeTreeHitboxFillersDoNotTurnRedAroundTheirOwnTree() {
        var tree=tree();
        int filler=com.hypixel.hytale.server.core.util.FillerBlockUtil.pack(CLEARANCE.x()-ROOT.x(),CLEARANCE.y()-ROOT.y(),CLEARANCE.z()-ROOT.z());
        var read=(java.util.function.Function<Position,TimberProductionState.Ground>)p -> p.equals(CLEARANCE)
            ?new TimberProductionState.Ground(true,"Wood_Beech_Trunk",0,filler,false,false,false,false):clear(p);
        assertTrue(TimberGrowingSpace.obstructions(tree,true,p->true,read,Set.of()).isEmpty());
        assertEquals(Set.of(CLEARANCE),TimberGrowingSpace.obstructions(tree,false,p->true,read,Set.of()));
    }
}
