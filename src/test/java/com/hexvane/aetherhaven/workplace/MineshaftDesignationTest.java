package com.hexvane.aetherhaven.workplace;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import com.hexvane.aetherhaven.town.ResidentNpcRecord;
import com.hexvane.aetherhaven.town.TownRecord;
import com.hexvane.aetherhaven.townsfolk.TownsfolkWorkType;
import com.hexvane.aetherhaven.warehouse.WarehouseBatch.Point;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("town")
class MineshaftDesignationTest {
    private static final Gson GSON = new Gson();
    private static final WorkplaceArea INITIAL = new WorkplaceArea(8, 100, 8, 15, 17);

    private TownRecord town() {
        var town = new TownRecord(UUID.randomUUID(), UUID.randomUUID(), "test", 16, 100, 16, 1, 1, 0);
        town.getResidentNpcRecords().add(new ResidentNpcRecord("Aetherhaven_Miner", "miner", null, UUID.randomUUID()));
        assertNull(town.getApprenticeWorkplaces().unlock(town, TownsfolkWorkType.MINING));
        return town;
    }

    private ApprenticeWorkplace site(TownRecord town) {
        assertNull(town.getApprenticeWorkplaces().save(town, null, TownsfolkWorkType.MINING, INITIAL, "Shaft"));
        return town.getApprenticeWorkplaces().sites().getFirst();
    }

    private ExcavationPlan start(TownRecord town, ApprenticeWorkplace site,
            ExcavationPlan.Entrance entrance, ExcavationPlan.Layout layout) {
        var plan = site.excavation();
        var cells = ExcavationPlan.tunnel(site.area(), entrance, layout).stream().limit(2)
            .map(p -> new ExcavationPlan.Cell(p, "Rock_Stone", 0, 1)).toList();
        assertEquals(2, cells.size());
        assertNull(plan.start(0, town.getOwnerUuid(), UUID.randomUUID(), site.area(), entrance, layout, cells));
        return plan;
    }

    private void beginFirstCell(ExcavationPlan plan) {
        assertTrue(plan.prepare(Map.of("Rock_Stone_Cobble", 1)));
        for (int i = 0; i < 12; i++) plan.attend(.5, true, 1);
        var next = plan.next().point();
        assertTrue(plan.begin(plan.id(), 6, new Point(next.x(), next.y(), next.z() - 1), () -> true));
        assertTrue(plan.pending());
    }

    @Test
    void defaultGettersAndValidationDoNotAllocateAnExcavationPlan() {
        var town = town();
        var site = site(town);
        var before = GSON.toJsonTree(site);
        for (int i = 0; i < 3; i++) {
            assertEquals(ExcavationPlan.Entrance.NORTH, site.mineshaftEntrance());
            assertEquals(ExcavationPlan.Layout.STRAIGHT, site.mineshaftLayout());
            assertNull(site.existingExcavation());
            assertNull(town.getApprenticeWorkplaces().validate(town, site.id(), TownsfolkWorkType.MINING, INITIAL, site.name()));
        }
        assertEquals(before, GSON.toJsonTree(site));
        assertFalse(GSON.toJsonTree(site).getAsJsonObject().has("excavation"));
    }

    @Test
    void designationChoicesSurviveSaveBeforeTheFirstDigWithoutCreatingAnOrder() {
        var town = town();
        var site = site(town);
        site.setMineshaftConfiguration(ExcavationPlan.Entrance.WEST, ExcavationPlan.Layout.STAIRS_DOWN);
        var saved = GSON.toJsonTree(town.getApprenticeWorkplaces());
        var restored = GSON.fromJson(saved, TownWorkplaces.class).find(site.id());
        assertNotNull(restored);
        assertEquals(site.id(), restored.id());
        assertEquals(INITIAL, restored.area());
        assertEquals(ExcavationPlan.Entrance.WEST, restored.mineshaftEntrance());
        assertEquals(ExcavationPlan.Layout.STAIRS_DOWN, restored.mineshaftLayout());
        assertNull(restored.existingExcavation());
        assertFalse(GSON.toJsonTree(restored).getAsJsonObject().has("excavation"));
    }

    @Test
    void legacyDesignationsFallBackToTheirSavedPlanWithoutRewritingIt() {
        var town = town();
        var site = site(town);
        start(town, site, ExcavationPlan.Entrance.EAST, ExcavationPlan.Layout.STAIRS_DOWN);
        var legacy = GSON.toJsonTree(site).getAsJsonObject();
        legacy.remove("mineshaftEntrance");
        legacy.remove("mineshaftLayout");
        var restored = GSON.fromJson(legacy, ApprenticeWorkplace.class);
        var plan = restored.existingExcavation();
        var before = GSON.toJsonTree(restored);
        assertEquals(ExcavationPlan.Entrance.EAST, restored.mineshaftEntrance());
        assertEquals(ExcavationPlan.Layout.STAIRS_DOWN, restored.mineshaftLayout());
        assertSame(plan, restored.existingExcavation());
        assertEquals(before, GSON.toJsonTree(restored));
    }

    @Test
    void changingDesignationKeepsTheExistingPlanAndItsCompletedProgress() {
        var town = town();
        var site = site(town);
        var plan = start(town, site, ExcavationPlan.Entrance.NORTH, ExcavationPlan.Layout.STRAIGHT);
        beginFirstCell(plan);
        assertTrue(plan.settle(plan.id(), true));
        assertTrue(plan.delivered(plan.id(), () -> true));
        assertEquals(1, plan.completed());
        var before = GSON.toJsonTree(plan);
        var resized = MineshaftSelection.area(12, 99, 12, MineshaftSelection.Direction.EAST, 8, ExcavationPlan.Layout.STRAIGHT);
        assertNull(town.getApprenticeWorkplaces().save(town, site.id(), TownsfolkWorkType.MINING, resized, "New direction"));
        site.setMineshaftConfiguration(ExcavationPlan.Entrance.WEST, ExcavationPlan.Layout.STRAIGHT);
        assertEquals(resized, site.area());
        assertEquals(ExcavationPlan.Entrance.WEST, site.mineshaftEntrance());
        assertSame(plan, site.existingExcavation());
        assertEquals(before, GSON.toJsonTree(plan), "Saving a designation must not replace, restart or discard the old order");
        assertEquals(INITIAL, plan.area(), "The existing order retains its own reviewed bounds until explicitly replaced");
        assertEquals(1, plan.completed());
    }

    @Test
    void pendingAndCarriedMaterialsRejectResizeWithoutChangingTheSavedWorkplace() {
        for (boolean collected : List.of(false, true)) {
            var town = town();
            var site = site(town);
            var plan = start(town, site, ExcavationPlan.Entrance.NORTH, ExcavationPlan.Layout.STRAIGHT);
            beginFirstCell(plan);
            if (collected) {
                assertTrue(plan.settle(plan.id(), true));
                assertTrue(plan.pickup(plan.id(), "worker", plan.cargoPoint()));
            }
            var before = GSON.toJsonTree(site);
            var resized = MineshaftSelection.area(12, 99, 12, MineshaftSelection.Direction.EAST, 8, ExcavationPlan.Layout.STRAIGHT);
            assertNotNull(town.getApprenticeWorkplaces().save(town, site.id(), TownsfolkWorkType.MINING, resized, "Changed"));
            assertSame(plan, site.existingExcavation());
            assertEquals(before, GSON.toJsonTree(site));
            assertTrue(plan.held());
            if (collected) {
                assertEquals(1, plan.completed());
                assertEquals(Map.of("Rock_Stone_Cobble", 1), plan.cargo());
                assertEquals("worker", plan.carrier());
            } else {
                assertTrue(plan.pending());
                assertEquals(0, plan.completed());
            }
        }
    }

    @Test
    void readingAndDiscardingNewOptionsCannotMutateEitherAnUnstartedOrActiveOrder() {
        for (boolean active : List.of(false, true)) {
            var town = town();
            var site = site(town);
            if (active) start(town, site, ExcavationPlan.Entrance.NORTH, ExcavationPlan.Layout.STAIRS_UP);
            var plan = site.existingExcavation();
            var before = GSON.toJsonTree(site);
            var ground = MineshaftSelection.ground(site.area(), site.mineshaftEntrance(), site.mineshaftLayout());
            var proposed = MineshaftSelection.area(ground.x(), ground.y(), ground.z(),
                MineshaftSelection.Direction.EAST, 8, ExcavationPlan.Layout.STAIRS_DOWN);
            assertTrue(proposed.valid());
            assertNull(town.getApprenticeWorkplaces().validate(town, site.id(), TownsfolkWorkType.MINING, proposed, "Unsaved options"));
            // Cancel discards these local choices without calling save or the configuration setter.
            assertSame(plan, site.existingExcavation());
            assertEquals(before, GSON.toJsonTree(site));
        }
    }
}
