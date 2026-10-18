package com.hexvane.aetherhaven.workplace;

import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.bench.CraftingBench;
import com.hypixel.hytale.server.core.util.FillerBlockUtil;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import org.joml.Vector3i;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorkplaceDetectionTest {
    @Test void transientBenchStatesUseBaseStationMetadata() {
        var configuredBench=new CraftingBench(){ @Override public String getId(){return "Farmingbench";} };
        var base=new BlockType("Bench_Farming") { @Override public CraftingBench getBench(){return configuredBench;} };
        var active=new BlockType("*Bench_Farming_State_Definitions_CraftCompleted") {
            @Override public String getDefaultStateKey(){return "Bench_Farming";}
        };
        assertEquals("Farmingbench",WorkplaceEquipment.benchId(base,key->{fail("Base bench needs no lookup");return null;}));
        assertEquals("Farmingbench",WorkplaceEquipment.benchId(active,key->"Bench_Farming".equals(key)?base:null));
        assertNull(WorkplaceEquipment.benchId(active,key->null),"Unavailable assets must not invent a station");
        assertNull(WorkplaceEquipment.benchId(new BlockType("Rock_Stone"),key->base));
        var standing=new WorkplaceQuestSteps.Standing(WorkplaceEquipment.benchId(active,key->base),"Bench_Farming",null);
        assertTrue(WorkplaceQuestSteps.isStation(TownsfolkWorkType.FARMING,null,standing));
        assertFalse(WorkplaceQuestSteps.isStation(TownsfolkWorkType.FARMING,null,
            new WorkplaceQuestSteps.Standing("Farmingbench","Bench_Trough",null)));
    }
    @Test void registeredFarmingBenchUsesTheSameRulesAsQuestAndWorkplaceAccess() {
        WorkplaceBenchAccess.registerPrimaryBench(TownsfolkWorkType.FARMING,"Regression_Farming_Station");
        assertTrue(WorkplaceQuestSteps.isStation(TownsfolkWorkType.FARMING,null,
            new WorkplaceQuestSteps.Standing("Regression_Farming_Station","Regression_Bench",null)));
    }
    @Test void cropOrBenchCornerIncludesItsBaseAndKeepsTheChosenHorizontalFootprint() {
        for(int[] offset:new int[][]{{0,0,0},{0,1,0},{1,2,-1},{-1,1,1}}) {
            var origin=new Vector3i(12,116,-5);
            var hit=new Vector3i(origin).add(offset[0],offset[1],offset[2]);
            var ground=WorkplaceSelectionGround.ground(hit,new WorkplaceSelectionGround.Blocks() {
                public int filler(int x,int y,int z){return FillerBlockUtil.pack(offset[0],offset[1],offset[2]);}
                public boolean equipmentOrCrop(int x,int y,int z){assertEquals(origin,new Vector3i(x,y,z));return true;}
            });
            assertEquals(new Vector3i(hit.x,115,hit.z),ground);
            var area=WorkplaceArea.corners(ground.x,ground.y,ground.z,ground.x+4,ground.z+4);
            assertTrue(area.contains(hit.x,116,hit.z),"Clicking a crop must not raise the floor above its root");
        }
    }
    @Test void ordinaryTerrainCornerKeepsItsOriginalGroundHeight() {
        var hit=new Vector3i(-8,115,31);
        assertEquals(hit,WorkplaceSelectionGround.ground(hit,new WorkplaceSelectionGround.Blocks() {
            public int filler(int x,int y,int z){return 0;}
            public boolean equipmentOrCrop(int x,int y,int z){return false;}
        }));
    }
}
