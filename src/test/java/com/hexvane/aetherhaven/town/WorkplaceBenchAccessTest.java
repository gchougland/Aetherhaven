package com.hexvane.aetherhaven.town;

import com.hexvane.aetherhaven.workplace.WorkplaceBenchAccess;
import com.hexvane.aetherhaven.workplace.WorkplaceEquipment;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class WorkplaceBenchAccessTest {
    @Test void builderUsesItsOwnBenchWhileCraftingAndMiningKeepTheWorkbench() {
        assertTrue(WorkplaceBenchAccess.matches(TownsfolkWorkType.BUILDING,"Builders","Bench_Builders"));
        assertFalse(WorkplaceBenchAccess.matches(TownsfolkWorkType.BUILDING,"Workbench","Bench_Workbench"));
        for(var type:java.util.List.of(TownsfolkWorkType.CRAFTING,TownsfolkWorkType.MINING)) {
            assertTrue(WorkplaceBenchAccess.matches(type,"Workbench","Bench_Workbench"));
            assertFalse(WorkplaceBenchAccess.matches(type,"Builders","Bench_Builders"));
        }
    }
    @Test void upgradedWorkbenchCannotSubstituteForBuildersBench() {
        var ordinary=new WorkplaceEquipment.Bench("Workbench",3,0,0,0);
        var missing=new WorkplaceEquipment.Snapshot(true,java.util.List.of(ordinary));
        assertNotNull(missing.requirement(WorkplaceEquipment.BUILDERS_BENCH,1));
        var equipped=new WorkplaceEquipment.Snapshot(true,java.util.List.of(ordinary,
            new WorkplaceEquipment.Bench("Builders",1,1,0,0)));
        assertNull(equipped.requirement(WorkplaceEquipment.BUILDERS_BENCH,1));
        assertEquals(1,equipped.tier(WorkplaceEquipment.BUILDERS_BENCH));
        assertEquals(3,equipped.tier("Workbench"));
    }
    @Test void onlyPrimaryProfessionBenchesOpenTasks() {
        assertTrue(WorkplaceBenchAccess.matches(TownsfolkWorkType.ARMOR_CRAFTING,"Armor_Bench","Bench_Armour"));
        assertFalse(WorkplaceBenchAccess.matches(TownsfolkWorkType.BLACKSMITHING,"Armor_Bench","Bench_Armour"));
        assertTrue(WorkplaceBenchAccess.matches(TownsfolkWorkType.JEWELRY,WorkplaceEquipment.JEWELRY_BENCH,WorkplaceEquipment.JEWELRY_BENCH));
        assertFalse(WorkplaceBenchAccess.matches(TownsfolkWorkType.JEWELRY,WorkplaceEquipment.APPRAISAL_BENCH,WorkplaceEquipment.APPRAISAL_BENCH));
        assertFalse(WorkplaceBenchAccess.matches(TownsfolkWorkType.CRAFTING,null,"Furniture_Crate"));
        assertFalse(WorkplaceBenchAccess.matches(TownsfolkWorkType.LOGISTICS,"Workbench","Bench_Workbench"));
    }
    @Test void sharedNativeBenchIdDoesNotConfuseTroughAndFarmerBench() {
        assertTrue(WorkplaceBenchAccess.matches(TownsfolkWorkType.RANCHING,"Farmingbench","Bench_Trough"));
        assertFalse(WorkplaceBenchAccess.matches(TownsfolkWorkType.FARMING,"Farmingbench","Bench_Trough"));
        assertFalse(WorkplaceBenchAccess.matches(TownsfolkWorkType.RANCHING,"Farmingbench","Bench_Farming"));
        assertTrue(WorkplaceBenchAccess.matches(TownsfolkWorkType.CULTIVATION,"Farmingbench","Bench_Farming"));
    }
}
