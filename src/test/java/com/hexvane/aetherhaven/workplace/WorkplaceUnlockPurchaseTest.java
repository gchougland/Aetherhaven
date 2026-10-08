package com.hexvane.aetherhaven.workplace;
import org.junit.jupiter.api.Test;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
@org.junit.jupiter.api.Tag("town")
class WorkplaceUnlockPurchaseTest {
    private final com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer inventory =
        new com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer(new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short) 1));
    @Test void requiresSupervisorPermissionAndGoldAndCannotChargeTwice(){
        var town=new TownRecord();var owner=UUID.randomUUID();town.setOwner(owner,"Owner");town.setTreasuryGoldCoinCount(200);
        var sites=town.getApprenticeWorkplaces();var type=TownsfolkWorkType.SMELTING;
        assertNotNull(sites.buyUnlock(town,owner,type, inventory));assertEquals(200,town.getTreasuryGoldCoinCount());
        town.getResidentNpcRecords().add(new ResidentNpcRecord("Smith","blacksmith",null,UUID.randomUUID()));
        assertNotNull(sites.buyUnlock(town,UUID.randomUUID(),type, inventory));assertEquals(200,town.getTreasuryGoldCoinCount());
        town.setTreasuryGoldCoinCount(99);assertNotNull(sites.buyUnlock(town,owner,type, inventory));assertFalse(sites.unlocked(type));
        town.setTreasuryGoldCoinCount(200);assertNull(sites.buyUnlock(town,owner,type, inventory));assertEquals(100,town.getTreasuryGoldCoinCount());
        assertNotNull(sites.buyUnlock(town,owner,type, inventory));assertEquals(100,town.getTreasuryGoldCoinCount());
    }
}
