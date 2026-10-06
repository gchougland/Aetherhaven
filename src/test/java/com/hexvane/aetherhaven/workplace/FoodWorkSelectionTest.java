package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.townsfolk.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class FoodWorkSelectionTest {
    private static ApprenticeWorkplace site(String name,TownsfolkWorkType type,int x,long age) {
        var site=new ApprenticeWorkplace(name,type,new WorkplaceArea(x,100,0,x+5,5));
        for(int i=0;i<48;i++)site.food().work(.25,true,true);
        assertTrue(site.food().completeCare(()->true));
        site.food().advance(age,true,true,true);
        return site;
    }
    private static TownsfolkProgress worker(int farming,int ranching) {
        var worker=new TownsfolkProgress();worker.setEmployed(true);
        worker.setPriority(TownsfolkWorkType.FARMING,farming);worker.setPriority(TownsfolkWorkType.RANCHING,ranching);
        return worker;
    }
    @Test void explicitProfessionPriorityWinsOverUrgency() {
        var pasture=site("Near pasture",TownsfolkWorkType.RANCHING,0,360);
        var garden=site("Neglected garden",TownsfolkWorkType.FARMING,20,1440);
        assertSame(pasture,List.of(garden,pasture).stream().min(FoodWorkSelection.order(worker(2,1),0,100,0)).orElseThrow());
    }
    @Test void equallyPrioritizedNeglectedGardenWinsOverNearbyRoutineFeeding() {
        var pasture=site("Near pasture",TownsfolkWorkType.RANCHING,0,360);
        var garden=site("Neglected garden",TownsfolkWorkType.FARMING,40,1440);
        assertSame(garden,List.of(pasture,garden).stream().min(FoodWorkSelection.order(worker(1,1),0,100,0)).orElseThrow());
    }
    @Test void distanceBreaksEqualUrgencyTies() {
        var near=site("Near",TownsfolkWorkType.FARMING,5,720);
        var far=site("Far",TownsfolkWorkType.FARMING,40,720);
        assertSame(near,List.of(far,near).stream().min(FoodWorkSelection.order(worker(1,1),0,100,0)).orElseThrow());
    }
    @Test void blockedUrgentSiteDoesNotPreventAnotherSiteBeingTended() {
        var blocked=site("No entrance",TownsfolkWorkType.FARMING,5,1440);
        var available=site("Open gate",TownsfolkWorkType.RANCHING,20,720);
        var retries=new WorkplaceRouteRetries();retries.failed(blocked.id(),1000);
        var sites=List.of(blocked,available);
        assertSame(available,sites.stream().filter(s->retries.canTry(s.id(),2000))
            .min(FoodWorkSelection.order(worker(1,1),0,100,0)).orElseThrow());
        assertSame(blocked,sites.stream().filter(s->retries.canTry(s.id(),16_000))
            .min(FoodWorkSelection.order(worker(1,1),0,100,0)).orElseThrow());
    }
    @Test void failedRoutesAreLocalAndRetryOtherApproachesWithoutPermanentBlacklisting() {
        var site=UUID.randomUUID();var other=UUID.randomUUID();var retries=new WorkplaceRouteRetries();
        retries.failed(site,1000);
        assertFalse(retries.canTry(site,15_999));assertTrue(retries.canTry(site,16_000));assertTrue(retries.canTry(other,1000));
        assertEquals(1,retries.approachAttempt(site,16_000));assertEquals(0,retries.approachAttempt(other,16_000));
        var copy=retries.copy();copy.failed(site,16_000);
        assertFalse(copy.canTry(site,45_999));assertTrue(copy.canTry(site,46_000));
        assertTrue(retries.canTry(site,16_000));
        copy.succeeded(site);assertTrue(copy.canTry(site,16_001));assertEquals(0,copy.approachAttempt(site,16_001));
        assertEquals(0,retries.approachAttempt(site,400_000));
    }
    @Test void repeatedFailuresHaveABoundedWait() {
        var site=UUID.randomUUID();var retries=new WorkplaceRouteRetries();long now=1000;
        for(int i=0;i<20;i++) {
            retries.failed(site,now);assertTrue(retries.canTry(site,now+120_000));now+=120_000;
        }
        assertFalse(retries.canTry(site,now-1));assertTrue(retries.canTry(site,now));
    }
}
