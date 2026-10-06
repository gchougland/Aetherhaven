package com.hexvane.aetherhaven.town;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.townsfolk.*;
import com.hexvane.aetherhaven.workplace.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class LogisticsPriorityTest {
    @Test void oldCourierSavesMigrateOnceWithoutErasingOtherWorkOrRestoringAnExplicitOff() {
        var gson=new Gson();
        var worker=gson.fromJson("{courier:true,employed:true,workPriorities:{crafting:2}}",TownsfolkProgress.class);
        assertEquals(1,worker.priority(TownsfolkWorkType.LOGISTICS));
        assertEquals(2,worker.priority(TownsfolkWorkType.CRAFTING));
        assertTrue(worker.canPerform(TownsfolkWorkType.CRAFTING,false,false,true));
        assertTrue(worker.canPerform(TownsfolkWorkType.LOGISTICS,false,false,true));
        worker.setPriority(TownsfolkWorkType.LOGISTICS,0);
        var restored=gson.fromJson(gson.toJson(worker),TownsfolkProgress.class);
        assertEquals(0,restored.priority(TownsfolkWorkType.LOGISTICS));assertEquals(2,restored.priority(TownsfolkWorkType.CRAFTING));
        assertEquals(0,gson.fromJson("{courier:true,workPriorities:{logistics:0}}",TownsfolkProgress.class).priority(TownsfolkWorkType.LOGISTICS));
    }
    @Test void logisticsHasTheSameGuardDebtAndSkillGatesAndNeedsNoSite() {
        var worker=new TownsfolkProgress();worker.setEmployed(true);worker.setPriority(TownsfolkWorkType.LOGISTICS,2);
        assertTrue(worker.canPerform(TownsfolkWorkType.LOGISTICS,false,false,true));
        assertFalse(worker.canPerform(TownsfolkWorkType.LOGISTICS,true,false,true));
        assertFalse(worker.canPerform(TownsfolkWorkType.LOGISTICS,false,true,true));
        assertFalse(worker.canPerform(TownsfolkWorkType.LOGISTICS,false,false,false));
        worker.setSkill(TownsfolkSkill.LOGISTICS,0);assertFalse(worker.canPerform(TownsfolkWorkType.LOGISTICS,false,false,true));
        assertFalse(TownsfolkWorkType.LOGISTICS.requiresWorkplace());
        var town=new TownRecord();
        assertNotNull(town.getApprenticeWorkplaces().unlock(town,TownsfolkWorkType.LOGISTICS));
        assertNotNull(town.getApprenticeWorkplaces().save(town,null,TownsfolkWorkType.LOGISTICS,new WorkplaceArea(0,1,0,5,5),"No site needed"));
        assertTrue(town.getApprenticeWorkplaces().sites().isEmpty());
    }
    @Test void availableWorkPrioritiesDecideBetweenDeliveriesAndBenches() {
        assertTrue(WorkSelection.chooseDelivery(1,2,false,false,false));
        assertFalse(WorkSelection.chooseDelivery(3,1,false,false,false));
        assertFalse(WorkSelection.chooseDelivery(2,2,false,false,false));
        assertTrue(WorkSelection.chooseDelivery(4,0,false,false,false));
        assertFalse(WorkSelection.chooseDelivery(0,0,false,false,false));
        assertFalse(WorkSelection.chooseDelivery(1,4,false,false,true));
        assertTrue(WorkSelection.chooseDelivery(4,1,false,true,false));
        assertTrue(WorkSelection.chooseDelivery(0,1,true,false,false));
    }
}
