package com.hexvane.aetherhaven.town;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.hexvane.aetherhaven.villager.TownVillagerBinding;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class TownResidentReconcileServiceTest {

    @Test
    void reconcileReportTracksChanges() {
        TownResidentReconcileService.ReconcileReport report = new TownResidentReconcileService.ReconcileReport();
        assertFalse(report.anyChanges());
        report.addSyncedRole();
        assertTrue(report.anyChanges());
        assertEquals(1, report.getSyncedRoles());
    }

    @Test
    void removeDuplicateRegistryRows_keepsOneStoryRowPerRole() {
        TownRecord town = sampleTown();
        UUID uuidA = UUID.randomUUID();
        UUID uuidB = UUID.randomUUID();
        town.getResidentNpcRecords().add(new ResidentNpcRecord("Aetherhaven_Chef", TownVillagerBinding.KIND_CHEF, null, uuidA));
        town.getResidentNpcRecords().add(new ResidentNpcRecord("Aetherhaven_Chef", TownVillagerBinding.KIND_CHEF, null, uuidB));
        TownResidentReconcileService.ReconcileReport report = new TownResidentReconcileService.ReconcileReport();

        invokeRemoveDuplicateRegistryRows(town, null, report);

        assertEquals(1, town.getResidentNpcRecords().size());
        assertEquals(1, report.getRemovedStaleRegistryRows());
    }

    private static TownRecord sampleTown() {
        return new TownRecord(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "default",
            0,
            0,
            0,
            1,
            1,
            System.currentTimeMillis()
        );
    }

    private static void invokeRemoveDuplicateRegistryRows(
        TownRecord town,
        TownManager tm,
        TownResidentReconcileService.ReconcileReport report
    ) {
        try {
            var method =
                TownResidentReconcileService.class.getDeclaredMethod(
                    "removeDuplicateRegistryRows",
                    TownRecord.class,
                    TownManager.class,
                    TownResidentReconcileService.ReconcileReport.class
                );
            method.setAccessible(true);
            method.invoke(null, town, tm, report);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
