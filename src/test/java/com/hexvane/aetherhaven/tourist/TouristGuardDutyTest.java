package com.hexvane.aetherhaven.tourist;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.*;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class TouristGuardDutyTest {
    @Test void citizenshipNeverRewritesGuardDutyEvenAfterSaving() {
        for (boolean citizen : new boolean[]{false,true}) {
            var town = new TownRecord(); town.setTownId(UUID.randomUUID());
            var uuid = UUID.randomUUID();
            town.getHiredGuardRecords().add(new HiredGuardRecord("guard",uuid,"guard_knight_iron",true));
            town.getTouristRecords().add(new TouristRecord("guard",uuid,UUID.randomUUID(),true,citizen));
            var pool = new TownsfolkPoolState();
            var tourist = town.getTouristRecords().getFirst();
            assertTrue(TouristReconcileService.syncPoolCheckout(pool,town,tourist,null));
            assertEquals(TownsfolkAssignmentKinds.GUARD,pool.checkoutForCharacter(town.getTownId(),"guard").getAssignmentKind());
            assertFalse(TouristReconcileService.syncPoolCheckout(pool,town,tourist,null));
            town = new Gson().fromJson(new Gson().toJson(town),TownRecord.class);
            pool = new Gson().fromJson(new Gson().toJson(pool),TownsfolkPoolState.class);
            tourist = town.getTouristRecords().getFirst();
            assertFalse(TouristReconcileService.syncPoolCheckout(pool,town,tourist,null));
            assertEquals("guard_knight_iron",town.getHiredGuardRecords().getFirst().getEquipmentProfileId());
            assertEquals(TownsfolkAssignmentKinds.GUARD,pool.checkoutForCharacter(town.getTownId(),"guard").getAssignmentKind());
            // A deliberate return to civilian duty removes the guard record; normal tourist bookkeeping resumes.
            town.getHiredGuardRecords().clear();
            assertTrue(TouristReconcileService.syncPoolCheckout(pool,town,tourist,null));
            assertEquals(TownsfolkAssignmentKinds.TOURIST,pool.checkoutForCharacter(town.getTownId(),"guard").getAssignmentKind());
        }
    }

    @Test void repairsAnOldCivilianCheckoutWithoutFollowingAStaleTouristUuid() {
        var town = new TownRecord(); town.setTownId(UUID.randomUUID());
        var guardUuid = UUID.randomUUID(); var oldUuid = UUID.randomUUID();
        town.getHiredGuardRecords().add(new HiredGuardRecord("guard",guardUuid,"guard_archer",true));
        var tourist = new TouristRecord("guard",oldUuid,UUID.randomUUID(),true,true);
        var pool = new TownsfolkPoolState();
        pool.checkout(new TownsfolkPoolCheckoutRecord("guard",town.getTownId().toString(),oldUuid.toString(),TownsfolkAssignmentKinds.TOURIST,"grumpy"));
        assertTrue(TouristReconcileService.syncPoolCheckout(pool,town,tourist,null));
        var repaired = pool.checkoutForCharacter(town.getTownId(),"guard");
        assertEquals(guardUuid.toString(),repaired.getEntityUuid());
        assertEquals(TownsfolkAssignmentKinds.GUARD,repaired.getAssignmentKind());
        assertEquals("grumpy",repaired.getActivePersonalityId());
        assertFalse(TouristReconcileService.syncPoolCheckout(pool,town,tourist,null));
    }
}
