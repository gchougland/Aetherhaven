package com.hexvane.aetherhaven.shopspot;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.town.*;
import com.hexvane.aetherhaven.townsfolk.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class ShopStaffingTest {
    @Test void unpaidDeadGuardAndIncapableResidentsCannotServe() {
        var p=new TownsfolkProgress();p.setEmployed(true);
        assertTrue(ShopStaffing.canWork(p,true,true,false,false));
        assertFalse(ShopStaffing.canWork(p,false,true,false,false));
        assertFalse(ShopStaffing.canWork(p,true,false,false,false));
        assertFalse(ShopStaffing.canWork(p,true,true,true,false));
        assertFalse(ShopStaffing.canWork(p,true,true,false,true));
        p.setEmployed(false);assertFalse(ShopStaffing.canWork(p,true,true,false,false));
        assertNull(ShopStaffing.unavailable(p,true,true,false,false),"Assignment can start employment");
        p.setEmployed(true);p.setSkill(TownsfolkSkill.COMMERCE,0);
        assertFalse(ShopStaffing.canWork(p,true,true,false,false));
    }
    @Test void oneShopPerResidentSurvivesSaveAndDoesNotEraseOtherWork() {
        var town=new TownRecord();var a=new PlotInstance();a.setPlotId(UUID.randomUUID());
        var b=new PlotInstance();b.setPlotId(UUID.randomUUID());town.getPlotInstances().add(a);town.getPlotInstances().add(b);
        var p=new TownsfolkProgress();p.setEmployed(true);p.setPriority(TownsfolkWorkType.LOGISTICS,2);town.getTownsfolkProgress().put("mertie",p);
        ShopStaffing.assign(town,a,"mertie");ShopStaffing.assign(town,b," Mertie ");assertNull(a.getShopClerkId());
        var loaded=new Gson().fromJson(new Gson().toJson(town),TownRecord.class);
        assertEquals("mertie",loaded.findPlotById(b.getPlotId()).getShopClerkId());
        ShopStaffing.assign(loaded,loaded.findPlotById(b.getPlotId()),null);
        assertTrue(loaded.getTownsfolkProgress().get("mertie").isEmployed());
        assertEquals(2,loaded.getTownsfolkProgress().get("mertie").priority(TownsfolkWorkType.LOGISTICS));
    }
    @Test void purchaseCurveIsBoundedAndDoesNotGuaranteeSales() {
        assertEquals(0,ShopStaffing.purchaseChance(0));assertEquals(.35,ShopStaffing.purchaseChance(1),1e-10);
        assertEquals(.8,ShopStaffing.purchaseChance(10),1e-10);assertEquals(.8,ShopStaffing.purchaseChance(100),1e-10);
        for(int skill=1;skill<=10;skill++) {
            assertTrue(ShopStaffing.makesPurchase(skill,ShopStaffing.purchaseChance(skill)-.00001));
            assertFalse(ShopStaffing.makesPurchase(skill,ShopStaffing.purchaseChance(skill)));
            assertFalse(ShopStaffing.makesPurchase(skill,Double.NaN));
        }
        assertFalse(ShopStaffing.makesPurchase(0,0));assertFalse(ShopStaffing.makesPurchase(10,-1));
    }
    @Test void declinedVisitsCannotRerollButUnattendedShopsCanBeRevisited() {
        assertTrue(NpcShopSpotBuyerService.Result.DECLINED.visitComplete());
        assertTrue(NpcShopSpotBuyerService.Result.PURCHASED.visitComplete());
        assertFalse(NpcShopSpotBuyerService.Result.UNAVAILABLE.visitComplete());
    }
}
