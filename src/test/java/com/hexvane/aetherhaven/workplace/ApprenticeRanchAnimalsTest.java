package com.hexvane.aetherhaven.workplace;

import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import com.hypixel.hytale.math.shape.Box;
import org.joml.Vector3d;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("town")
class ApprenticeRanchAnimalsTest {
    @Test void theWholeAnimalMustFitInsideTheWorkplaceAndCloseToItsTrough() {
        var area = new WorkplaceArea(0, 100, 0, 10, 10);
        var trough = new Point(5, 100, 5);
        var cow = new Box(-.8, 0, -.8, .8, 2, .8);
        assertTrue(ApprenticeRanchAnimals.inside(area, trough, new Vector3d(6.5, 100, 5.5), cow));
        assertFalse(ApprenticeRanchAnimals.inside(area, trough, new Vector3d(.5, 100, 5.5), cow));
        assertFalse(ApprenticeRanchAnimals.inside(area, trough, new Vector3d(9.5, 100, 5.5), cow));
        assertFalse(ApprenticeRanchAnimals.inside(area, trough, new Vector3d(5.5, 104, 5.5), cow));
        assertFalse(ApprenticeRanchAnimals.inside(area, trough, new Vector3d(5.5, 99, 5.5), cow));
        assertFalse(ApprenticeRanchAnimals.inside(new WorkplaceArea(-20, 100, -20, 20, 20), trough,
            new Vector3d(9.5, 100, 5.5), cow), "Even a large workplace keeps animals close to the trough");
    }

    @Test void apprenticeTendingNeverTargetsAnOldOffspringForSlaughter() {
        var site = new ApprenticeWorkplace("Ranch", TownsfolkWorkType.RANCHING, new WorkplaceArea(0, 100, 0, 10, 10));
        var herd = site.food().ranch();
        var old = java.util.UUID.randomUUID();
        herd.born(old, "Cow"); herd.matured(old); herd.advance(RanchCare.GROW_MINUTES);
        var trough = new Point(5, 100, 5);
        var job = RanchWorkService.job(null, site, trough);
        assertEquals(trough, job.target());
        assertNull(job.animal());
        assertFalse(job.slaughter());
    }

    @Test void foodSupportRequiresCompletedAttendanceAndNoInventoryProduction() {
        var food = new WorkplaceFood();
        assertEquals(0, food.foodPoints());
        for (int i = 0; i < 48; i++) food.work(.25, true, true);
        assertFalse(food.completeCare(() -> false));
        assertEquals(0, food.foodPoints(), "Care that did not complete must not grant support");
        assertTrue(food.completeCare(() -> true, "Workplace animals fed."));
        assertEquals(10, food.foodPoints());
        assertNull(food.ranch().offspring());
    }
}
