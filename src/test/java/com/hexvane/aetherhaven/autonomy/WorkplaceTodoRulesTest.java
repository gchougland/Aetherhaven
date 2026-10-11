package com.hexvane.aetherhaven.autonomy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

class WorkplaceTodoRulesTest {
    @Test
    void aWorkerOnlyWaitsBrieflyForSomeoneBetter() {
        UUID site = UUID.randomUUID();
        String worker = "patience_" + UUID.randomUUID();
        long start = 1_000_000L;
        assertTrue(ApprenticeWorkService.stillPatient(worker, site, start));
        assertTrue(ApprenticeWorkService.stillPatient(worker, site, start + ApprenticeWorkService.DEFER_PATIENCE_MS));
        assertFalse(ApprenticeWorkService.stillPatient(worker, site, start + ApprenticeWorkService.DEFER_PATIENCE_MS + 1),
            "After waiting, the job is theirs");
        assertFalse(ApprenticeWorkService.stillPatient(worker, site, start + 60_000L));
        assertTrue(ApprenticeWorkService.stillPatient(worker, site, start + 200_000L), "A later job starts a new wait");
        assertTrue(ApprenticeWorkService.stillPatient(worker, UUID.randomUUID(), start + 60_000L), "Each site is waited on separately");
    }

    @Test
    void aClerkStandsAcrossTheCounterFromTheCustomers() {
        // Counter at 7.5, -6.5 with customers browsing in front of it at z -4.5.
        Vector3d customers = new Vector3d(7.0, 64, -4.5);
        assertTrue(ShopClerkWorkService.behind(new Vector3d(7.5, 64, -8.5), 7.5, -6.5, customers));
        assertFalse(ShopClerkWorkService.behind(new Vector3d(7.5, 64, -5.5), 7.5, -6.5, customers));
        assertFalse(ShopClerkWorkService.behind(new Vector3d(7.5, 64, -6.5), 7.5, -6.5, customers), "On the counter is not behind it");
        // The same shop turned a quarter: customers to the east.
        Vector3d east = new Vector3d(10.5, 64, 3.0);
        assertTrue(ShopClerkWorkService.behind(new Vector3d(6.5, 64, 3.5), 8.5, 3.5, east));
        assertEquals(false, ShopClerkWorkService.behind(new Vector3d(9.5, 64, 3.5), 8.5, 3.5, east));
    }
    @Test
    void theCommercePriorityDecidesWhenAClerkStaffsTheCounter() {
        assertFalse(ShopClerkWorkService.shopComesFirst(0, 0), "Commerce switched off never staffs the shop");
        assertTrue(ShopClerkWorkService.shopComesFirst(1, 0), "With nothing else to do the clerk is at the counter");
        assertTrue(ShopClerkWorkService.shopComesFirst(2, 3), "Lower numbers come first");
        assertTrue(ShopClerkWorkService.shopComesFirst(2, 2), "A tie goes to the shop");
        assertFalse(ShopClerkWorkService.shopComesFirst(3, 1), "Work ranked above Commerce is done first");
        // Every kind of work has a column, and the two that share Blacksmithing's skill have pictures of their own.
        assertEquals("furnace.png", com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.SMELTING.icon);
        assertEquals("armor.png", com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.ARMOR_CRAFTING.icon);
        assertEquals("forge.png", com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.BLACKSMITHING.icon);
        assertFalse(com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.COMMERCE.requiresWorkplace());
        assertEquals(com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.COMMERCE,
            com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.byId("commerce"));
    }
    @Test
    void theMinerDoesNotStandOnTheRockOrWaitForeverForOre() {
        var veins = java.util.Set.of(new org.joml.Vector3i(6, 100, 12), new org.joml.Vector3i(6, 100, 13));
        assertTrue(MiningWorkService.onAVein(veins, 6, 101, 13), "Feet on top of the next vein");
        assertFalse(MiningWorkService.onAVein(veins, 5, 100, 12), "Beside the vein, on the mine floor");
        assertFalse(MiningWorkService.onAVein(veins, 6, 102, 13));
        assertFalse(MiningWorkService.oreOverdue(0, 99_999), "No wait has started");
        assertFalse(MiningWorkService.oreOverdue(1000, 1000 + MiningWorkService.ORE_WAIT_MS - 1));
        assertTrue(MiningWorkService.oreOverdue(1000, 1000 + MiningWorkService.ORE_WAIT_MS));
        assertEquals(null, MiningWorkService.waitingReason(null));
        assertEquals(null, MiningWorkService.waitingReason(UUID.randomUUID()), "Nothing is claimed about a mine that has not been seen");
        // General stores are collected at the warehouse's own desk when it is there, and at any desk only when it is not.
        var desks = java.util.List.of("beside the barn", "in the warehouse", "by the gate");
        assertEquals(java.util.List.of("in the warehouse"), CourierWorkService.preferWarehouse(desks, "in the warehouse"::equals));
        assertEquals(desks, CourierWorkService.preferWarehouse(desks, desk -> false));
        assertTrue(CourierWorkService.preferWarehouse(java.util.List.<String>of(), desk -> true).isEmpty());
    }
}
