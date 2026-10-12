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
    @Test void bothBuilderWorkplacesUseTheBuildersBenchAndAreToldApartByTheSite() {
        for(var type:java.util.List.of(TownsfolkWorkType.BUILDING,TownsfolkWorkType.BLOCK_WORKSHOP)) {
            assertTrue(WorkplaceBenchAccess.matches(type,"Builders","Bench_Builders"));
            assertFalse(WorkplaceBenchAccess.matches(type,"Workbench","Bench_Workbench"));
            assertEquals("builder",com.hexvane.aetherhaven.workplace.WorkplaceRules.supervisor(type));
            assertEquals("Main workplace bench: Builder's Workbench",com.hexvane.aetherhaven.workplace.WorkplaceRules.mainBench(type));
        }
        assertEquals("Builder's Yard",com.hexvane.aetherhaven.workplace.WorkplaceRules.label(TownsfolkWorkType.BUILDING));
        assertEquals("Block Workshop",com.hexvane.aetherhaven.workplace.WorkplaceRules.label(TownsfolkWorkType.BLOCK_WORKSHOP));
        // The bench opens the page of the site it stands in: construction for the yard, orders for the workshop.
        assertEquals(WorkplaceBenchAccess.Screen.BUILDING_PROJECTS,WorkplaceBenchAccess.screen(TownsfolkWorkType.BUILDING));
        assertEquals(WorkplaceBenchAccess.Screen.ORDERS,WorkplaceBenchAccess.screen(TownsfolkWorkType.BLOCK_WORKSHOP));
        assertEquals(WorkplaceBenchAccess.Screen.MINESHAFT,WorkplaceBenchAccess.screen(TownsfolkWorkType.MINING));
        assertEquals(WorkplaceBenchAccess.Screen.FOOD,WorkplaceBenchAccess.screen(TownsfolkWorkType.FARMING));
        assertEquals(WorkplaceBenchAccess.Screen.ORDERS,WorkplaceBenchAccess.screen(TownsfolkWorkType.CRAFTING));
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
    @Test void everyKindOfBenchWithItsOwnScreenGetsAButtonThatSaysWhatItOpens() {
        // Using a workplace's main bench opens its tasks, so the pages it opens offer the bench's own screen.
        for(var type:java.util.List.of(com.hypixel.hytale.protocol.BenchType.Crafting,
                com.hypixel.hytale.protocol.BenchType.DiagramCrafting,com.hypixel.hytale.protocol.BenchType.StructuralCrafting))
            assertEquals("Craft at this bench",com.hexvane.aetherhaven.workplace.WorkplaceNativeBench.label(type));
        assertEquals("Use this bench",com.hexvane.aetherhaven.workplace.WorkplaceNativeBench.label(com.hypixel.hytale.protocol.BenchType.Processing));
        for(var type:com.hypixel.hytale.protocol.BenchType.values())
            assertNotNull(com.hexvane.aetherhaven.workplace.WorkplaceNativeBench.label(type),type.name());
        // A block with no bench of its own, such as the jewelry bench, shows no button.
        assertNull(com.hexvane.aetherhaven.workplace.WorkplaceNativeBench.label((com.hypixel.hytale.protocol.BenchType)null));
        assertNull(com.hexvane.aetherhaven.workplace.WorkplaceNativeBench.label(null,new org.joml.Vector3i()));
        assertFalse(com.hexvane.aetherhaven.workplace.WorkplaceNativeBench.UNAVAILABLE.contains(" - "));
    }
    @Test void sharedNativeBenchIdDoesNotConfuseTroughAndFarmerBench() {
        assertTrue(WorkplaceBenchAccess.matches(TownsfolkWorkType.RANCHING,"Farmingbench","Bench_Trough"));
        assertFalse(WorkplaceBenchAccess.matches(TownsfolkWorkType.FARMING,"Farmingbench","Bench_Trough"));
        assertFalse(WorkplaceBenchAccess.matches(TownsfolkWorkType.RANCHING,"Farmingbench","Bench_Farming"));
        assertTrue(WorkplaceBenchAccess.matches(TownsfolkWorkType.CULTIVATION,"Farmingbench","Bench_Farming"));
    }
}
