package com.hexvane.aetherhaven.inn;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("town")
class InnOutreachTest {
    private final com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer inventory =
        new com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer(new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short) 1));
    @Test void invitationsChargeOnceAndCannotOverwritePendingGuestsOrSpendWithoutPermission() {
        var t=town(7,200);assertNull(t.getOutreach().unlock(t,owner, inventory));
        assertNotNull(t.getOutreach().invite(t,UUID.randomUUID(),"guest", inventory));assertEquals(100,t.getTreasuryGoldCoinCount());
        assertNull(t.getOutreach().invite(t,owner,"guest", inventory));assertEquals(50,t.getTreasuryGoldCoinCount());
        assertNotNull(t.getOutreach().invite(t,owner,"other", inventory));assertEquals(50,t.getTreasuryGoldCoinCount());
        assertEquals("guest",t.getOutreach().requestedRole());
        t.getOutreach().arrived("guest");assertNull(t.getOutreach().invite(t,owner,"other", inventory));assertEquals(0,t.getTreasuryGoldCoinCount());
        t.getOutreach().arrived("other");assertNotNull(t.getOutreach().invite(t,owner,"poor", inventory));assertEquals("",t.getOutreach().requestedRole());
    }
    private final Gson gson = new Gson();
    private final UUID owner = UUID.randomUUID();
    private TownRecord town(int residents, long gold) {
        var t = gson.fromJson("{\"ownerUuid\":\"" + owner + "\",\"innActive\":true}", TownRecord.class);
        for (int i = 1; i < residents; i++) t.getResidentNpcRecords().add(new ResidentNpcRecord("Core_" + i, "worker", null, UUID.randomUUID()));
        t.setTreasuryGoldCoinCount(gold);
        return t;
    }

    @Test void unlockRequiresLevelAndPermissionAndOnlyChargesOnce() {
        var low = town(6, 500);
        assertNotNull(low.getOutreach().unlock(low, owner, inventory));
        assertEquals(500, low.getTreasuryGoldCoinCount());
        var t = town(7, 500);
        assertNotNull(t.getOutreach().unlock(t, UUID.randomUUID(), inventory));
        assertNull(t.getOutreach().unlock(t, owner, inventory));
        assertEquals(400, t.getTreasuryGoldCoinCount());
        assertNotNull(t.getOutreach().unlock(t, owner, inventory));
        assertEquals(400, t.getTreasuryGoldCoinCount());
    }

    @Test void invitationSurvivesSaveAndOnlyMatchingArrivalFulfillsIt() {
        var t = town(7, 100);
        assertNull(t.getOutreach().unlock(t, owner, inventory));
        t.getOutreach().request("Addon_Warehouse_Manager");
        var restored = gson.fromJson(gson.toJson(t), TownRecord.class);
        restored.getOutreach().arrived("Someone_Else");
        assertEquals("Addon_Warehouse_Manager", restored.getOutreach().requestedRole());
        restored.getOutreach().arrived("Addon_Warehouse_Manager");
        assertEquals("", restored.getOutreach().requestedRole());
        assertTrue(restored.getOutreach().unlocked());
    }

    @Test void unavailableGuestKeepsRequestWithoutBypassingEligibilityOrDuplicatingPriority() {
        var t = town(7, 100); t.getOutreach().unlock(t, owner, inventory);
        t.getOutreach().request("requested");
        var original = List.of("quest_owner", "requested", "farmer");
        assertEquals(original, t.getOutreach().prioritize(original, false));
        assertEquals(List.of("requested", "quest_owner", "farmer"), t.getOutreach().prioritize(original, true));
        assertEquals(List.of("quest_owner", "requested", "farmer"), original);
        t.getOutreach().request(null);
        assertEquals(original, t.getOutreach().prioritize(original, true));
    }

    @Test void oldSaveCannotRequestBeforeUnlockAndInsufficientFundsArePreserved() {
        var t = town(7, 99);
        assertThrows(IllegalStateException.class, () -> t.getOutreach().request("guest"));
        assertNotNull(t.getOutreach().unlock(t, owner, inventory));
        assertFalse(t.getOutreach().unlocked());
        assertEquals(99, t.getTreasuryGoldCoinCount());
        assertEquals(List.of("ordinary"), t.getOutreach().prioritize(List.of("ordinary"), true));
    }
}
