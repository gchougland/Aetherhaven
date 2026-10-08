package com.hexvane.aetherhaven.town;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.workplace.*;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType.*;

@Tag("town")
class ApprenticeWorkplacesTest {
    private final com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer inventory =
        new com.hypixel.hytale.server.core.inventory.container.CombinedItemContainer(new com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer((short) 1));
    @Test void removalWaitsForExcavatedCargoAndKeepsItsDeliveryRoute() {
        var town=town();var sites=town.getApprenticeWorkplaces();
        assertNull(sites.save(town,null,SMELTING,area(0,0),"Cargo fixture"));
        var site=sites.sites().getFirst();var plan=site.excavation();
        var point=new com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point(1,101,1);
        assertNull(plan.start(plan.revision(),town.getOwnerUuid(),UUID.randomUUID(),site.area(),ExcavationPlan.Entrance.NORTH,
            java.util.List.of(new ExcavationPlan.Cell(point,"Rock_Stone",0,1))));
        assertTrue(plan.prepare(java.util.Map.of("Rock_Stone_Cobble",2)));
        plan.attend(.5,true,1);assertTrue(plan.begin(plan.id(),.5,point,()->true));assertTrue(plan.settle(plan.id(),true));
        assertNotNull(sites.remove(town,town.getOwnerUuid(),site.id()));
        assertSame(site,sites.find(site.id()));assertTrue(plan.paused());assertEquals(2,plan.cargo().get("Rock_Stone_Cobble"));
        assertTrue(plan.delivered(plan.id(),()->true));
        assertNull(sites.remove(town,town.getOwnerUuid(),site.id()));assertNull(sites.find(site.id()));
    }
    @Test void removingWorkplaceRequiresPermissionAndFreesItsSupervisorSlot() {
        var town=town();var sites=town.getApprenticeWorkplaces();
        assertNull(sites.save(town,null,SMELTING,area(0,0),"Smeltery"));
        var site=sites.sites().getFirst();
        assertNull(site.orders().add(site.orders().revision(),"test",WorkplaceOrders.Mode.COUNT,1));
        assertNotNull(sites.remove(town,UUID.randomUUID(),site.id()));
        assertEquals(1,site.orders().list().size());
        assertEquals(1,sites.used("blacksmith"));
        assertNull(sites.remove(town,town.getOwnerUuid(),site.id()));
        assertNull(sites.find(site.id()));assertEquals(0,sites.used("blacksmith"));
    }
    private TownRecord town() {
        var town = new TownRecord(); town.setTownId(UUID.randomUUID()); town.setOwner(UUID.randomUUID(), "Owner");
        town.getClaimedTerritoryChunksMutable().add(ClaimedTerritoryChunkRecord.of(0, 0));
        town.getResidentNpcRecords().add(new ResidentNpcRecord("Smith", "blacksmith", null, UUID.randomUUID()));
        for (var type : new TownsfolkWorkType[]{SMELTING, BLACKSMITHING, ARMOR_CRAFTING}) assertNull(town.getApprenticeWorkplaces().unlock(town, type));
        return town;
    }
    private static WorkplaceArea area(int x, int z) { return WorkplaceArea.corners(x, 100, z, x+3, z+3); }

    @Test void cornerOrderIsSymmetricAndHeightComesOnlyFromFirstGroundClick() {
        assertEquals(new WorkplaceArea(2, 101, 4, 7, 10), WorkplaceArea.corners(6, 100, 9, 2, 4));
        assertFalse(WorkplaceArea.corners(0, 100, 0, 0, 3).valid());
        assertFalse(WorkplaceArea.corners(0, 100, 0, 64, 3).valid());
        assertFalse(new WorkplaceArea(Integer.MIN_VALUE, 100, 0, Integer.MAX_VALUE, 4).valid());
    }
    @Test void touchingBordersAndSeparateFloorsAreAllowedButNestingIsNot() {
        var a = area(0,0);
        assertFalse(a.overlaps(area(4,0)));
        assertTrue(a.overlaps(area(3,0)));
        assertTrue(a.overlaps(new WorkplaceArea(1,102,1,3,3)));
        assertFalse(a.overlaps(new WorkplaceArea(0,106,0,4,4)));
        assertTrue(a.overlaps(new WorkplaceArea(0,105,0,4,4)));
        assertTrue(a.contains(0,101,0)); assertFalse(a.contains(4,101,0)); assertFalse(a.contains(0,106,0));
    }
    @Test void clickingTheUpperFloorChoosesItsSiteRatherThanTheOlderSiteBelow() {
        var town=town();var sites=town.getApprenticeWorkplaces();
        assertNull(sites.save(town,null,SMELTING,area(0,0),"Below"));
        assertNull(sites.save(town,null,SMELTING,new WorkplaceArea(0,106,0,4,4),"Above"));
        assertEquals("Above",sites.atGroundOrBlock(1,105,1).name());
        assertEquals("Below",sites.atGroundOrBlock(1,100,1).name());
        assertNull(sites.atGroundOrBlock(4,105,1));
    }
    @Test void everyCoveredChunkMustBeClaimedEvenWhenBothCornersAreInsideTown() {
        var town=town(); town.getClaimedTerritoryChunksMutable().add(ClaimedTerritoryChunkRecord.of(1,1));
        var selection=WorkplaceArea.corners(30,100,30,34,34);
        assertTrue(TownTerritoryClaims.containsBlock(town,30,30)); assertTrue(TownTerritoryClaims.containsBlock(town,34,34));
        assertNotNull(town.getApprenticeWorkplaces().save(town,null,SMELTING,selection,"Crossing"));
        assertTrue(town.getApprenticeWorkplaces().sites().isEmpty());
    }
    @Test void negativeCoordinatesAndExactClaimEdgesWork() {
        var town=town(); town.getClaimedTerritoryChunksMutable().clear(); town.getClaimedTerritoryChunksMutable().add(ClaimedTerritoryChunkRecord.of(-1,-1));
        assertNull(town.getApprenticeWorkplaces().save(town,null,SMELTING,WorkplaceArea.corners(-4,100,-4,-1,-1),"Edge"));
        assertNotNull(town.getApprenticeWorkplaces().save(town,null,SMELTING,WorkplaceArea.corners(-8,100,-4,0,-1),"Outside"));
    }
    @Test void allThreeBlacksmithTypesShareExactlyTwoStartingSlots() {
        var town=town(); var sites=town.getApprenticeWorkplaces();
        assertNull(sites.save(town,null,SMELTING,area(0,0),"Furnaces"));
        assertNull(sites.save(town,null,BLACKSMITHING,area(4,0),"Forge"));
        assertNotNull(sites.save(town,null,ARMOR_CRAFTING,area(8,0),"Armor"));
        assertEquals(2, sites.used("blacksmith"));
        var id=sites.sites().getFirst().id();
        assertNull(sites.save(town,id,ARMOR_CRAFTING,area(0,0),"Armor"));
        assertEquals(2,sites.sites().size()); assertEquals(ARMOR_CRAFTING,sites.find(id).type());
    }
    @Test void failedResizeLeavesExistingSiteIntactAndSuccessfulRenameKeepsIdentity() {
        var town=town();var sites=town.getApprenticeWorkplaces();
        sites.save(town,null,SMELTING,area(0,0),"Original"); sites.save(town,null,SMELTING,area(4,0),"Neighbour");
        var id=sites.sites().getFirst().id();
        assertNotNull(sites.save(town,id,SMELTING,area(3,0),"Invalid"));
        assertEquals("Original",sites.find(id).name()); assertEquals(area(0,0),sites.find(id).area());
        assertNull(sites.save(town,id,SMELTING,area(0,0),"New name"));
        assertEquals(id,sites.sites().getFirst().id());
    }
    @Test void noResidentMeansNoUnlockButDeadOrUnloadedSupervisorsKeepTheirSites() {
        var town=town(); var sites=town.getApprenticeWorkplaces();
        assertNotNull(sites.unlock(town,FARMING)); assertFalse(sites.unlocked(FARMING));
        town.getResidentNpcRecords().getFirst().setPendingDawnRevival(true);
        assertNull(sites.save(town,null,SMELTING,area(0,0),"Still available"));
    }
    @Test void purchasesEnforcePermissionsLevelFundsAndRejectRepeatedStaleClicks() {
        var town=town();var sites=town.getApprenticeWorkplaces();town.setTreasuryGoldCoinCount(1000);
        assertNotNull(sites.buySlot(town,town.getOwnerUuid(),"blacksmith",0, inventory));
        for(int i=0;i<3;i++)town.getResidentNpcRecords().add(new ResidentNpcRecord("Core"+i,"core"+i,null,UUID.randomUUID()));
        assertNotNull(sites.buySlot(town,UUID.randomUUID(),"blacksmith",0, inventory));
        assertNull(sites.buySlot(town,town.getOwnerUuid(),"blacksmith",0, inventory));
        assertEquals(900,town.getTreasuryGoldCoinCount());assertEquals(3,sites.slots("blacksmith"));
        assertNotNull(sites.buySlot(town,town.getOwnerUuid(),"blacksmith",0, inventory));
        assertEquals(900,town.getTreasuryGoldCoinCount());
    }
    @Test void saveReloadKeepsGeometryNameUnlocksAndSharedCapacity() {
        var town=town();var sites=town.getApprenticeWorkplaces();
        sites.save(town,null,ARMOR_CRAFTING,area(0,0),"Armorer");
        var copy=new Gson().fromJson(new Gson().toJson(town),TownRecord.class);
        assertEquals(sites.sites().getFirst().id(),copy.getApprenticeWorkplaces().sites().getFirst().id());
        assertEquals(area(0,0),copy.getApprenticeWorkplaces().sites().getFirst().area());
        assertTrue(copy.getApprenticeWorkplaces().unlocked(BLACKSMITHING));
        assertEquals(2,copy.getApprenticeWorkplaces().slots("blacksmith"));
        assertNotNull(copy.getApprenticeWorkplaces().save(copy,null,SMELTING,area(0,0),"Duplicate"));
    }
}
